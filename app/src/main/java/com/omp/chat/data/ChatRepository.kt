package com.omp.chat.data

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

data class RepoState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val resources: PcResources? = null,
    val error: String = "",
    val projects: List<Project> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
    val archivedSessions: List<SessionSummary> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val modelUsageCounts: Map<String, Int> = emptyMap(),
    val messages: List<ChatMessage> = emptyList(),
    val chat: ChatState = ChatState(),
    val streaming: Boolean = false,
    val streamingStartedAt: Long = 0,
    val queuedMessageIds: Set<String> = emptySet(),
    val cwd: String = "",
    val sessionFile: String = "",
    val activeBridgeSession: com.omp.chat.data.ActiveBridgeSession? = null,
    val readOnly: Boolean = false,
    val readOnlyFile: String = "",
    val readOnlyReason: String = "",
    val title: String = "",
    val todos: List<TodoPhase> = emptyList(),
    val request: UiRequest? = null,
    val terminal: List<TerminalEntry> = emptyList(),
    val notice: Notice? = null,
    val busy: Boolean = false,
    val loadedOnce: Boolean = false,
    val backendVersion: String = "",
    val approvalMode: String = "",
    val terminalEnabled: Boolean = false,
    val bridgeVersion: String = "",
    val voiceEngines: VoiceEngineList? = null,
    val pinnedMessageIds: Set<String> = emptySet()
)

/**
 * The single client-side source of truth.
 *
 * OMP owns the conversation (stored as jsonl on the PC); this repository keeps an
 * in-memory working copy for smooth streaming and reloads the authoritative
 * transcript from the bridge when a turn settles.
 */
class ChatRepository(private val settings: SettingsStore) {

    private val client = BridgeClient(
        baseUrlProvider = { settings.baseUrl() },
        tokenProvider = { settings.token() },
        candidatesProvider = { candidatesProvider() },
        onEndpointResolved = { url -> onEndpointResolved(url) }
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state.asStateFlow()

    /** Alle kjente adresser til aktiv PC (primær + LAN/Tailscale/VPN). */
    var candidatesProvider: () -> List<String> = { emptyList() }

    var modelUsageProvider: () -> Map<String, Int> = { emptyMap() }

    var onModelSelected: (String) -> Unit = {}

    /** Kalles når en alternativ adresse svarte; brukes til å gjøre den primær. */
    var onEndpointResolved: (String) -> Unit = { }

    /** Kalles med `hosts` fra `/api/ping` slik at PC-profilen lærer nye adresser. */
    var onHostsDiscovered: (List<String>) -> Unit = { }

    /** Hook for live task monitoring (Etappe 3). */
    var taskListener: ((TaskEvent) -> Unit)? = null

    /** Shared HTTP client for features (voice) that live outside the chat turn logic. */
    val bridge: BridgeClient get() = client

    private val lock = Any()
    private val messages = mutableListOf<ChatMessage>()
    private val streamText = HashMap<String, StringBuilder>()
    private val streamThinking = HashMap<String, StringBuilder>()
    private val localUsers = ArrayDeque<String>()
    private var dirty = false
    private var activeId = ""
    private var eventJob: Job? = null
    private var connectJob: Job? = null
    private var activeBridgeJob: Job? = null
    private var noticeSeq = 0L
    private var terminalSeq = 0L
    private var autoTitleSession = false
    private var firstUserMessage = ""

    /**
     * Generation token for the currently connected PC. Every asynchronous
     * request captures the token it started under and refuses to write state
     * once the PC has been switched or disconnected. This is what keeps an
     * in-flight answer from PC A out of PC B's session list.
     */
    @Volatile
    private var epoch = 0

    /** Persists cwd/session to the active PC profile (set by AppViewModel). */
    var onRememberLast: (cwd: String, sessionFile: String) -> Unit = { _, _ -> }

    /**
     * Starts a new generation. Everything that was started under an older
     * token (connect probes, SSE events, resource polling, open/new chat) is
     * stale after this call and must not write state any more.
     */
    private fun bumpEpoch(): Int = synchronized(this) { ++epoch }

    /** True while [token] is still the generation of the connected PC. */
    private fun isCurrent(token: Int): Boolean = token == epoch

    /**
     * Serial number for "open/new conversation". Two taps on different rows
     * must not let the slower answer overwrite the newer one.
     */
    private var sessionOp = 0

    private fun beginSessionOp(): Int = synchronized(this) { ++sessionOp }

    private fun isSessionOpCurrent(op: Int, token: Int): Boolean =
        op == sessionOp && isCurrent(token)

    /** Persist the position for the active PC only (never for another PC). */
    private fun rememberLast(cwd: String, sessionFile: String) {
        settings.rememberLast(cwd, sessionFile)
        onRememberLast(cwd, sessionFile)
    }

    /** Favorittmarkerte sesjons-ID-er, lagret lokalt og overlever app-restart. */
    private val favoriteSessionIds = mutableSetOf<String>()

    init {
        // Last inn lagrede favoritter fra SettingsStore-konteksten
        val ctx = settings.context
        val prefsFav = ctx.getSharedPreferences("omp_favorites", android.content.Context.MODE_PRIVATE)
        val saved = prefsFav.getString("fav_sessions", "") ?: ""
        if (saved.isNotBlank()) {
            favoriteSessionIds.addAll(saved.split(",").filter { it.isNotBlank() })
        }
        scope.launch {
            while (isActive) {
                delay(45)
                flush()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Connection
    // -----------------------------------------------------------------------

    fun connect() {
        if (settings.baseUrl().isBlank() || settings.token().isNullOrBlank()) {
            _state.update { it.copy(connected = false, connecting = false) }
            return
        }
        val token = bumpEpoch()
        connectJob?.cancel()
        connectJob = scope.launch {
            if (!isCurrent(token)) return@launch
            _state.update { it.copy(connecting = true, error = "") }
            val candidates = (listOf(settings.baseUrl()) + candidatesProvider())
                .map { it.trimEnd('/') }
                .filter { it.isNotBlank() }
                .distinct()
            android.util.Log.d("OmpChat", "connect: prøver ${candidates.joinToString()}")
            // Probe først: /api/ping svarer uten token, så vi vet hvilken
            // adresse (LAN, Tailscale, VPN) som faktisk er oppe.
            val probes = candidates.map { EndpointResolver.probe(it, settings.token(), 1500) }
            probes.forEach {
                android.util.Log.d("OmpChat", "connect: ${EndpointResolver.hostLabel(it.url)} -> ${if (it.ok) "OK" else it.reason}")
            }

            val originalBaseUrl = settings.baseUrl()
            var connectedUrl: String? = null
            var pingResult: JsonObject? = null
            var healthResult: JsonObject? = null
            var tokenRejected = false

            // Prøv hver kandidat i rekkefølge, verifiser både ping OG health.
            // Ikke promoter en adresse til primær før både ping og health er bestått.
            for (probe in probes) {
                if (!isCurrent(token)) return@launch
                if (!probe.ok) continue
                try {
                    pingResult = client.ping(probe.url)
                    // Health is checked against the candidate explicitly; the
                    // shared baseUrl is only promoted after it has answered.
                    healthResult = client.health(probe.url)
                    connectedUrl = probe.url
                    android.util.Log.d("OmpChat", "connect: ping+health OK på ${probe.url}, versjon ${pingResult?.str("version")}")
                    break // Suksess, stopp å prøve
                } catch (error: Exception) {
                    if (error is BridgeException && error.status in listOf(401, 403)) {
                        tokenRejected = true
                    }
                    android.util.Log.d("OmpChat", "connect: ${probe.url} feilet: ${error.message}")
                    // Fortsett til neste kandidat
                    continue
                }
            }

            // A switch/disconnect may have happened while probing.
            if (!isCurrent(token)) return@launch

            if (connectedUrl == null) {
                // Alle kandidater feilet
                val reachable = probes.any { it.ok }
                val detail = probes.joinToString(" · ") { probe ->
                    "${EndpointResolver.hostLabel(probe.url)}: ${if (probe.ok) "OK" else probe.reason}"
                }
                if (tokenRejected) {
                    synchronized(this@ChatRepository) {
                        if (isCurrent(token)) settings.clearConnection()
                    }
                    android.util.Log.d("OmpChat", "connect: token avvist - tømmer tilkobling")
                } else {
                    // Reverter til original baseUrl
                    synchronized(this@ChatRepository) {
                        if (isCurrent(token)) settings.updateBaseUrl(originalBaseUrl)
                    }
                }
                if (!isCurrent(token)) return@launch
                val message = if (tokenRejected) {
                    "Tokenet ble avvist av bridgen. Skann QR-koden på nytt for å få et nytt token."
                } else if (reachable) {
                    "Ingen adresse svarte med gyldig token ($detail). Sjekk at PC-en er på, at Nimay OMP kjører, og at brannmuren tillater TCP 7777."
                } else {
                    "Ingen adresse svarte ($detail). Sjekk at PC-en er på, at Nimay OMP kjører, og at brannmuren tillater TCP 7777."
                }
                android.util.Log.d("OmpChat", "connect feil: $message")
                _state.update {
                    it.copy(
                        connected = false,
                        connecting = false,
                        error = message
                    )
                }
                return@launch
            }

            // Suksess - oppdater state
            val promoted = synchronized(this@ChatRepository) {
                if (!isCurrent(token)) false else {
                    settings.updateBaseUrl(connectedUrl)
                    true
                }
            }
            if (!promoted) return@launch
            val ping = pingResult!!
            val health = healthResult!!
            val hosts = ping.get("hosts")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                .orEmpty()
            if (hosts.isNotEmpty()) onHostsDiscovered(hosts)
            if (!isCurrent(token)) return@launch
            _state.update {
                it.copy(
                    connected = true,
                    connecting = false,
                    loadedOnce = true,
                    error = "",
                    bridgeVersion = ping.str("version"),
                    approvalMode = health.str("approvalMode"),
                    terminalEnabled = health.get("allowBash")?.asBoolean == true
                )
            }
            startEvents(token)
            refreshAll(token)
            scope.launch { validateRememberedFolder(token) }
            resumeLast(token)
            startResourcesPolling(token)
            startActiveBridgePolling(token)
        }
    }

    private var resourcesPollingJob: Job? = null

    /** Hent PC-ressurser (CPU, RAM, GPU) fra Bridge. */
    fun loadResources() {
        if (!state.value.connected) return
        val token = epoch
        scope.launch(Dispatchers.IO) {
            try {
                val json = client.resources()
                if (!isCurrent(token)) return@launch
                val resources = PcResources(
                    cpuPercent = json.get("cpu")?.asJsonObject?.get("percent")?.asInt ?: 0,
                    cpuCores = json.get("cpu")?.asJsonObject?.get("cores")?.asInt ?: 0,
                    ramPercent = json.get("ram")?.asJsonObject?.get("percent")?.asInt ?: 0,
                    ramUsedBytes = json.get("ram")?.asJsonObject?.get("usedBytes")?.asLong ?: 0,
                    ramTotalBytes = json.get("ram")?.asJsonObject?.get("totalBytes")?.asLong ?: 0,
                    gpuPercent = json.get("gpu")?.asJsonObject?.get("percent")?.asInt ?: 0,
                    gpuName = json.get("gpu")?.asJsonObject?.get("name")?.asString ?: "",
                    gpuMemoryUsedBytes = json.get("gpu")?.asJsonObject?.get("memoryUsedBytes")?.asLong ?: 0,
                    gpuMemoryTotalBytes = json.get("gpu")?.asJsonObject?.get("memoryTotalBytes")?.asLong ?: 0,
                    timestamp = json.get("timestamp")?.asLong ?: 0
                )
                _state.update { it.copy(resources = resources) }
            } catch (e: Exception) {
                // Stille feil
            }
        }
    }

    /** Kopierer siste [count] chat-meldinger til utklipp. */
    fun exportLastNChats(count: Int) {
        val messages = _state.value.messages
        if (messages.isEmpty()) return
        val lastN = messages.takeLast(count)
        val sb = StringBuilder()
        for (msg in lastN) {
            val role = if (msg.isUser) "Bruker" else "Assistent"
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
            sb.append("[$time] $role: ${msg.text}\n\n")
        }
        val text = sb.toString().trimEnd()
        val ctx = settings.context
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OmpChat-eksport", text)
        clipboard.setPrimaryClip(clip)
    }

    /** Pinner eller opp-pinner en melding ved messageId. */
    fun togglePinMessage(messageId: String) {
        val current = _state.value.pinnedMessageIds
        val newIds = if (current.contains(messageId)) {
            current - messageId
        } else {
            current + messageId
        }
        _state.update { it.copy(pinnedMessageIds = newIds) }
    }

    /** Start periodisk polling av PC-ressurser (hvert 5. sekund). */
    fun startResourcesPolling(token: Int = epoch) {
        resourcesPollingJob?.cancel()
        resourcesPollingJob = scope.launch {
            while (isActive && isCurrent(token)) {
                loadResources()
                delay(30000)
            }
        }
    }

    /** Stopp polling av PC-ressurser. */
    fun stopResourcesPolling() {
        resourcesPollingJob?.cancel()
        resourcesPollingJob = null
    }

    /** Start polling the bridge's active session (OMP TUI or Bridge runtime). */
    fun startActiveBridgePolling(token: Int = epoch) {
        activeBridgeJob?.cancel()
        activeBridgeJob = scope.launch {
            while (isActive && isCurrent(token)) {
                fetchActiveBridge(token)
                delay(10000)
            }
        }
    }

    private suspend fun fetchActiveBridge(token: Int) {
        if (!isCurrent(token)) return
        val active = try {
            client.activeSession()
        } catch (_: Exception) {
            null
        }
        if (!isCurrent(token)) return
        _state.update { it.copy(activeBridgeSession = active) }
    }

    /** Stopp polling av aktiv Bridge-samtale. */
    fun stopActiveBridgePolling() {
        activeBridgeJob?.cancel()
        activeBridgeJob = null
    }

    // -----------------------------------------------------------------------
    // Live status
    //
    // `/api/state` is authoritative for a conversation the bridge itself runs.
    // A conversation driven by the OMP terminal has no runtime state in the
    // bridge, so the only honest signal is the stored transcript: it grows
    // while OMP writes, and it ends with a user message while OMP still has to
    // answer. Nothing here invents progress; every flag comes from real data.
    // -----------------------------------------------------------------------

    private var liveSignature = ""
    private var liveStablePolls = 0
    private var liveSessionFile = ""

    /** Ask the bridge for the current, authoritative working state. */
    fun refreshLiveStatus() {
        val token = epoch
        scope.launch {
            if (!isCurrent(token)) return@launch
            val before = _state.value
            if (before.readOnly) return@launch
            val cwd = resolvedCwd()
            if (cwd.isBlank()) return@launch

            val rawState = try {
                client.stateRaw(cwd)
            } catch (_: Exception) {
                null
            }
            if (!isCurrent(token)) return@launch
            val active = try {
                client.activeSession()
            } catch (_: Exception) {
                null
            }
            if (!isCurrent(token)) return@launch

            val currentFile = before.sessionFile
            val runtimeFile = rawState?.str("sessionFile").orEmpty()
            val runtimeCoversCurrent = rawState != null && !before.readOnly &&
                (runtimeFile.isBlank() || currentFile.isBlank() || runtimeFile == currentFile)
            if (runtimeCoversCurrent) {
                applyState(rawState!!, token)
                _state.update { it.copy(activeBridgeSession = active) }
                liveSignature = ""
                liveStablePolls = 0
                liveSessionFile = currentFile
                return@launch
            }

            _state.update { it.copy(activeBridgeSession = active) }

            // Terminal-driven conversation: measure the real transcript.
            if (active == null || currentFile.isBlank() || active.sessionFile != currentFile) {
                if (liveSessionFile == currentFile) {
                    liveSignature = ""
                    liveStablePolls = 0
                }
                return@launch
            }
            if (liveSessionFile != currentFile) {
                liveSignature = ""
                liveStablePolls = 0
                liveSessionFile = currentFile
            }

            val messages = try {
                client.transcript(currentFile).second
            } catch (_: Exception) {
                return@launch
            }
            if (!isCurrent(token)) return@launch

            val last = messages.lastOrNull()
            val signature = buildString {
                append(messages.size)
                append(':')
                append(last?.id.orEmpty())
                append(':')
                append(last?.text?.length ?: 0)
            }
            val firstProbe = liveSignature.isBlank()
            val grew = !firstProbe && signature != liveSignature
            liveSignature = signature

            val working = when {
                active.streaming -> true
                last != null && last.isUser -> true
                grew -> {
                    liveStablePolls = 0
                    true
                }
                _state.value.streaming && liveStablePolls < 2 -> {
                    liveStablePolls++
                    true
                }
                else -> false
            }

            _state.update { st ->
                st.copy(
                    streaming = working,
                    streamingStartedAt = when {
                        working && st.streamingStartedAt > 0 -> st.streamingStartedAt
                        working -> System.currentTimeMillis()
                        else -> 0
                    }
                )
            }
        }
    }
    /** Adressen som skal brukes: vinneren fra probe, ellers primæradressen. */
    private fun winningUrl(probes: List<EndpointProbe>): String =
        probes.firstOrNull { it.ok }?.url ?: settings.baseUrl()

    fun disconnect() {
        bumpEpoch()
        connectJob?.cancel()
        connectJob = null
        backgroundMonitor?.cancel()
        backgroundMonitor = null
        stopResourcesPolling()
        eventJob?.cancel()
        eventJob = null
        activeBridgeJob?.cancel()
        activeBridgeJob = null
        synchronized(lock) {
            messages.clear()
            streamText.clear()
            streamThinking.clear()
            localUsers.clear()
            dirty = false
        }
        activeId = ""
        _state.value = RepoState()
    }

    // -----------------------------------------------------------------------
    // App foreground / background (Android lifecycle)
    // -----------------------------------------------------------------------

    private var backgroundMonitor: Job? = null

    /**
     * Called when the activity becomes STARTED again (app opened, screen
     * unlocked, returned from another app). Verifies the stream, rebuilds it
     * when dead and pulls everything that changed while away.
     */
    fun onAppForeground() {
        val configured = settings.baseUrl().isNotBlank() && settings.token() != null
        if (!configured) return
        val token = epoch
        android.util.Log.d("OmpChat", "onAppForeground: alive=${eventJob?.isActive == true && _state.value.connected}")
        val alive = eventJob?.isActive == true && _state.value.connected
        backgroundMonitor?.cancel()
        backgroundMonitor = null
        if (alive) {
            resyncAfterReconnect("foreground", token)
        } else {
            // Half-open sockets cannot be kept: cancel and rebuild the SSE
            // loop, then resync. No session is reopened or restarted.
            startEvents(token)
            resyncAfterReconnect("foreground-reconnect", token)
        }
    }

    /**
     * Called when the activity goes to STOP (home screen, screen off, app
     * switch). While an OMP task is streaming we keep the live stream so the
     * buffers stay warm; the moment it settles the stream is parked to save
     * battery and avoid dead-socket states.
     */
    fun onAppBackground() {
        val token = epoch
        backgroundMonitor?.cancel()
        backgroundMonitor = scope.launch {
            // While an OMP task is streaming we keep the live stream warm; the
            // moment it settles the stream is parked to save battery and avoid
            // dead-socket states. If the task still runs when the user comes
            // back, onAppForeground has cancelled this monitor.
            if (_state.value.streaming) {
                _state.first { !it.streaming && it.loadedOnce }
            }
            if (!isCurrent(token)) return@launch
            eventJob?.cancel()
            eventJob = null
        }
    }

    suspend fun testConnection(): String {
        return try {
            val ping = client.ping(settings.baseUrl())
            "OK · bridge ${ping.str("version")} · ${ping.arr("hosts").joinToString { it.asString }}"
        } catch (error: Exception) {
            error.message ?: "Ukjent feil"
        }
    }

    fun diagnostics(): JsonObject? = try {
        client.diagnostics()
    } catch (error: Exception) {
        null
    }

    private fun startEvents(token: Int = epoch) {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events(null).collect { event ->
                // A late event from the previous PC must never reach the new
                // PC's state, even if the stream was still draining.
                if (isCurrent(token)) handle(event, token)
            }
        }
    }

    /** Minimum gap between two automatic resyncs, so reconnect storms stay calm. */
    private var lastResync = 0L

    private fun resyncAfterReconnect(reason: String, token: Int = epoch) {
        val now = System.currentTimeMillis()
        if (now - lastResync < 2500) return
        lastResync = now
        scope.launch {
            if (!isCurrent(token)) return@launch
            // 1) Fresh runtime state first: this is authoritative for whether
            //    the agent is still working. Without this, a stale local
            //    streaming=true would skip the transcript reload below.
            val cwd = resolvedCwd()
            if (cwd.isNotBlank() && !_state.value.readOnly) {
                try {
                    client.stateRaw(cwd)?.let { if (isCurrent(token)) applyState(it) }
                } catch (_: Exception) {
                }
                if (!isCurrent(token)) return@launch
                runCatching { loadModels(cwd, token) }
            }
            if (!isCurrent(token)) return@launch
            // 2) Authoritative transcript fills everything missed while the
            //    stream was down. Only reload when the agent is not streaming.
            val streaming = _state.value.streaming
            if (!_state.value.readOnly && _state.value.sessionFile.isNotBlank() && !streaming) {
                runCatching { reloadTranscript() }
            }
            if (!isCurrent(token)) return@launch
            runCatching { loadSessions(token) }
            if (!isCurrent(token)) return@launch
            // 3) A finished turn while offline means the empty tail bubble must
            //    go; a still-running turn keeps its live buffers.
            if (!_state.value.streaming) {
                synchronized(lock) {
                    streamText.clear()
                    streamThinking.clear()
                }
            }
        }
    }

    private fun refreshAll(token: Int = epoch) {
        scope.launch { if (isCurrent(token)) runCatching { loadProjects(token) } }
        scope.launch { if (isCurrent(token)) runCatching { loadSessions(token) } }
        scope.launch {
            if (!isCurrent(token)) return@launch
            val cwd = resolvedCwd()
            if (cwd.isNotBlank()) {
                _state.update { it.copy(cwd = it.cwd.ifBlank { cwd }) }
                runCatching { loadModels(cwd, token) }
            }
        }
    }

    /**
     * The folder a new chat belongs to. Falls back from the active chat to the
     * last used folder and finally to the most recently active project, so the
     * app can start a conversation even on a fresh install.
     */
    private fun resolvedCwd(): String {
        val current = _state.value.cwd.trim()
        if (CwdPath.isUsable(current)) return current
        val last = settings.state.value.lastCwd.trim()
        if (CwdPath.isUsable(last)) return last
        return _state.value.projects.firstOrNull { it.exists && CwdPath.isUsable(it.path) }?.path
            ?: _state.value.projects.firstOrNull { CwdPath.isUsable(it.path) }?.path
            ?: ""
    }

    /** Validate a remembered folder against the bridge before using it. */
    private suspend fun validateRememberedFolder(token: Int) {
        if (!isCurrent(token)) return
        val remembered = settings.state.value.lastCwd
        if (!CwdPath.isUsable(remembered)) return
        val valid = try {
            client.validateFolder(remembered) != null
        } catch (_: Exception) {
            false
        }
        if (!isCurrent(token)) return
        if (!valid) {
            rememberLast("", settings.state.value.lastSession)
            _state.update { it.copy(cwd = "") }
        }
    }

    /** Reopen the conversation the user was in when the app last closed. */
    private fun resumeLast(token: Int = epoch) {
        val last = settings.state.value
        android.util.Log.d("OmpChat", "resumeLast: lastSession='${last.lastSession}'")
        if (last.lastSession.isBlank()) return
        scope.launch {
            if (!isCurrent(token)) return@launch
            try {
                val sessions = client.sessions()
                if (!isCurrent(token)) return@launch
                android.util.Log.d("OmpChat", "resumeLast: fetched ${sessions.size} sessions")
                val match = sessions.firstOrNull { it.file == last.lastSession }
                android.util.Log.d("OmpChat", "resumeLast: match=$match")
                if (match != null) {
                    openSession(match, silent = true)
                } else {
                    // The session may not be listed (archived, moved, or its
                    // working folder is gone). Open it by file so the stored
                    // transcript stays readable; the bridge decides whether it
                    // can resume.
                    openSessionByFile(last.lastSession, silent = true)
                }
            } catch (error: Exception) {
                android.util.Log.d("OmpChat", "resumeLast feil: ${error.message}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Catalogs
    // -----------------------------------------------------------------------

    fun loadProjects(token: Int = epoch) {
        scope.launch {
            if (!isCurrent(token)) return@launch
            runCatching {
                val projects = client.projectRegistry()
                if (!isCurrent(token)) return@launch
                _state.update { st ->
                    val current = st.cwd.takeIf { CwdPath.isUsable(it) }
                        ?: settings.state.value.lastCwd.takeIf { CwdPath.isUsable(it) }
                        ?: projects.firstOrNull { it.exists && CwdPath.isUsable(it.path) }?.path
                        ?: projects.firstOrNull { CwdPath.isUsable(it.path) }?.path
                        ?: ""
                    st.copy(projects = projects, cwd = current)
                }
                if (_state.value.cwd.isNotBlank() && _state.value.models.isEmpty()) {
                    loadModels(_state.value.cwd, token)
                }
            }.onFailure {
                if (isCurrent(token)) pushNotice(it.message ?: "Kunne ikke hente prosjekter", "error")
            }
        }
    }

    fun createProject(name: String, path: String? = null) {
        scope.launch {
            runCatching {
                val created = client.createProject(name, path = path)
                val projectPath = created.obj("project")?.str("path")?.ifBlank { path ?: "" } ?: (path ?: "")
                if (CwdPath.isUsable(projectPath)) {
                    _state.update { it.copy(cwd = projectPath) }
                    rememberLast(projectPath, _state.value.sessionFile)
                }
                loadProjects()
                loadSessions()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke opprette prosjekt", "error")
            }
        }
    }

    fun hideProject(id: String) {
        scope.launch {
            runCatching {
                client.hideProject(id)
                loadProjects()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke skjule prosjekt", "error")
            }
        }
    }

    fun renameProject(id: String, name: String) {
        scope.launch {
            runCatching {
                client.renameProject(id, name)
                loadProjects()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke gi nytt navn", "error")
            }
        }
    }

    fun unhideProject(id: String) {
        scope.launch {
            runCatching {
                client.unhideProject(id)
                loadProjects()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke vise prosjekt igjen", "error")
            }
        }
    }

    fun deleteProject(id: String, physical: Boolean = false) {
        scope.launch {
            runCatching {
                client.deleteProject(id, physical)
                loadProjects()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke fjerne prosjekt", "error")
            }
        }
    }

    fun moveSessionToProject(session: SessionSummary, project: Project) {
        scope.launch {
            runCatching {
                client.moveSessionToProject(session.file, project.id)
                loadSessions()
                loadProjects()
            }.onFailure {
                pushNotice(it.message ?: "Kunne ikke flytte samtale", "error")
            }
        }
    }

    fun loadSessions(token: Int = epoch) {
        scope.launch {
            if (!isCurrent(token)) return@launch
            runCatching {
                val active = client.sessions()
                if (!isCurrent(token)) return@launch
                val archived = client.sessions(archived = true).filter { it.archived }
                if (!isCurrent(token)) return@launch
                _state.update { it.copy(sessions = active, archivedSessions = archived) }
            }.onFailure {
                if (isCurrent(token)) pushNotice(it.message ?: "Kunne ikke hente samtaler", "error")
            }
        }
    }

    private fun sortModelsByUsage(models: List<ModelInfo>, counts: Map<String, Int>): List<ModelInfo> {
        return models.sortedWith(compareByDescending<ModelInfo> { counts[it.key] ?: 0 }
            .thenBy { it.name.ifBlank { it.id } })
    }

    fun resortModels() {
        val counts = modelUsageProvider()
        val sorted = sortModelsByUsage(_state.value.models, counts)
        _state.update { it.copy(models = sorted, modelUsageCounts = counts) }
    }

    fun loadModels(cwd: String = resolvedCwd(), token: Int = epoch) {
        if (!CwdPath.isUsable(cwd)) return
        scope.launch {
            if (!isCurrent(token)) return@launch
            runCatching {
                val models = client.models(cwd)
                if (!isCurrent(token)) return@launch
                val counts = modelUsageProvider()
                val sorted = sortModelsByUsage(models, counts)
                _state.update { it.copy(models = sorted, modelUsageCounts = counts) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Conversations
    // -----------------------------------------------------------------------

    /** Open a session by its file path (used by the Tasks screen). */
    fun openSessionByFile(file: String, silent: Boolean = false) {
        if (file.isBlank()) return
        val existing = _state.value.sessions.firstOrNull { it.file == file }
            ?: _state.value.archivedSessions.firstOrNull { it.file == file }
        // The working folder is never guessed from the current chat: the
        // bridge uses the folder recorded inside the session file.
        val summary = existing ?: SessionSummary(
            id = file, file = file, cwd = "",
            title = file.substringAfterLast('/').substringAfterLast('\\'),
            model = "", messageCount = 0, toolCount = 0, updatedAt = 0, archived = false
        )
        openSession(summary, silent)
    }

    /**
     * Open a stored conversation.
     *
     * When the bridge can resume it, the working folder and runtime come from
     * the bridge (the only proof of where OMP actually runs). When it cannot
     * (folder deleted/moved, OMP refused the switch) the transcript is shown
     * read-only, the previous valid working folder is kept, and nothing is
     * persisted for this PC.
     */
    fun openSession(session: SessionSummary, silent: Boolean = false) {
        val token = epoch
        val op = beginSessionOp()
        scope.launch {
            android.util.Log.d("OmpChat", "openSession: ${session.file} silent=$silent")
            _state.update { it.copy(busy = !silent, error = "") }
            try {
                val result = client.openSession(session.file)
                if (!isSessionOpCurrent(op, token)) return@launch
                synchronized(lock) {
                    messages.clear()
                    messages.addAll(result.messages)
                    streamText.clear()
                    streamThinking.clear()
                    dirty = true
                }
                if (result.readOnly) {
                    android.util.Log.d("OmpChat", "openSession read-only: ${result.reason}")
                    _state.update {
                        it.copy(
                            busy = false,
                            readOnly = true,
                            readOnlyFile = session.file,
                            readOnlyReason = result.reason.ifBlank {
                                "Samtalen kan vises, men ikke gjenopptas i denne arbeidsmappen."
                            },
                            sessionFile = "",
                            title = session.title.ifBlank { it.title },
                            messages = result.messages,
                            streaming = false,
                            todos = emptyList(),
                            request = null,
                            chat = it.chat.copy(
                                sessionFile = "",
                                isStreaming = false,
                                todoPhases = emptyList()
                            )
                        )
                    }
                    return@launch
                }
                val effectiveCwd = result.cwd.takeIf { CwdPath.isUsable(it) }
                if (effectiveCwd == null) {
                    // No proof of a working folder -> never persist a guess.
                    _state.update {
                        it.copy(
                            busy = false,
                            readOnly = true,
                            readOnlyFile = session.file,
                            readOnlyReason = "Bridgen oppgav ingen gyldig arbeidsmappe for samtalen.",
                            sessionFile = "",
                            title = session.title.ifBlank { it.title },
                            messages = result.messages,
                            streaming = false
                        )
                    }
                    return@launch
                }
                val chat = result.state ?: ChatState(sessionFile = result.session?.file ?: session.file)
                val file = chat.sessionFile.ifBlank { result.session?.file ?: session.file }
                android.util.Log.d("OmpChat", "openSession OK: ${result.messages.size} meldinger i $effectiveCwd")
                _state.update {
                    it.copy(
                        busy = false,
                        readOnly = false,
                        readOnlyFile = "",
                        readOnlyReason = "",
                        chat = chat,
                        cwd = effectiveCwd,
                        sessionFile = file,
                        title = session.title.ifBlank { it.title },
                        messages = result.messages,
                        streaming = chat.isStreaming,
                        todos = chat.todoPhases,
                        request = null,
                        models = it.models
                    )
                }
                rememberLast(effectiveCwd, file)
                loadModels(effectiveCwd, token)
                loadSessions(token)
            } catch (error: Exception) {
                if (!isSessionOpCurrent(op, token)) return@launch
                android.util.Log.d("OmpChat", "openSession FEIL: ${error.message}")
                if (silent) {
                    // A resume that cannot be completed must stay quiet; the
                    // user did not ask for this chat in this session.
                    _state.update { it.copy(busy = false) }
                } else {
                    _state.update {
                        it.copy(busy = false, error = error.message ?: "Kunne ikke åpne samtalen")
                    }
                }
            }
        }
    }

    /**
     * Start a new conversation in [cwd]. The bridge validates the folder and
     * returns the canonical spelling it actually runs in; only that value is
     * persisted. On failure nothing changes, so the previous working folder
     * stays selected.
     */
    fun newChat(cwd: String, title: String = "") {
        val token = epoch
        val op = beginSessionOp()
        scope.launch { openNewChat(cwd.ifBlank { resolvedCwd() }, title, token, op) }
    }

    /**
     * Makes sure a session exists before a prompt or a voice turn is started.
     * Returns false (after showing a notice) when there is no project folder.
     */
    suspend fun ensureSession(): Boolean {
        val current = _state.value
        if (current.readOnly) {
            pushNotice("Denne samtalen er skrivebeskyttet. Start en ny samtale i en gyldig mappe.", "warning")
            return false
        }
        if (current.sessionFile.isNotBlank() && CwdPath.isUsable(current.cwd)) return true
        return openNewChat(current.cwd.ifBlank { resolvedCwd() })
    }

    private suspend fun openNewChat(
        cwd: String,
        title: String = "",
        token: Int = epoch,
        op: Int = beginSessionOp()
    ): Boolean {
        if (!CwdPath.isUsable(cwd)) {
            pushNotice("Velg en prosjektmappe først", "warning")
            return false
        }
        if (!isSessionOpCurrent(op, token)) return false
        autoTitleSession = title.isBlank()
        firstUserMessage = ""
        _state.update { it.copy(busy = true, error = "") }
        return try {
            val result = client.newSession(cwd)
            if (!isSessionOpCurrent(op, token)) return false
            val canonical = result.cwd.takeIf { CwdPath.isUsable(it) } ?: cwd
            val chat = result.state
            val file = result.session?.file ?: chat.sessionFile
            synchronized(lock) {
                messages.clear()
                streamText.clear()
                streamThinking.clear()
                dirty = true
            }
            _state.update {
                it.copy(
                    busy = false,
                    readOnly = false,
                    readOnlyFile = "",
                    readOnlyReason = "",
                    cwd = canonical,
                    sessionFile = file,
                    title = title,
                    chat = chat,
                    messages = emptyList(),
                    streaming = false,
                    todos = emptyList(),
                    request = null
                )
            }
            rememberLast(canonical, file)
            val fallbackName = if (settings.state.value.language == "en") "New chat" else "Ny samtale"
            if (file.isNotBlank()) client.renameSession(file, title.ifBlank { fallbackName })
            if (!isSessionOpCurrent(op, token)) return false
            applyRememberedModel(canonical, token)
            loadModels(canonical, token)
            loadSessions(token)
            true
        } catch (error: Exception) {
            if (!isSessionOpCurrent(op, token)) return false
            val message = error.message ?: "Kunne ikke starte en ny samtale"
            // Keep the previous valid working folder; only report the failure.
            _state.update { it.copy(busy = false, error = message) }
            pushNotice(message, "error")
            false
        }
    }

    /**
     * Ask the bridge to validate a folder and then start a new chat there.
     * Used by the folder picker so an empty (but valid) folder is allowed,
     * while a deleted/invalid folder is rejected before anything persists.
     */
    fun switchFolder(cwd: String) {
        val token = epoch
        val op = beginSessionOp()
        scope.launch {
            val folder = try {
                client.validateFolder(cwd)
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke sjekke mappen", "error")
                null
            }
            if (!isSessionOpCurrent(op, token)) return@launch
            if (folder == null) {
                pushNotice("Mappen finnes ikke på PC-en: $cwd", "error")
                return@launch
            }
            openNewChat(folder.path, token = token, op = op)
        }
    }

    /**
     * Open the conversation the bridge itself has loaded most recently.
     * This is the only "active" chat the bridge can prove; the phone's own
     * last selection is not proof that OMP still has it open.
     */
    fun openActiveBridgeSession() {
        val token = epoch
        scope.launch {
            if (!isCurrent(token)) return@launch
            try {
                val active = client.activeSession()
                if (!isCurrent(token)) return@launch
                if (active == null) {
                    pushNotice("Ingen aktiv Bridge-samtale", "warning")
                    return@launch
                }
                val known = _state.value.sessions.firstOrNull { it.file == active.sessionFile }
                    ?: _state.value.archivedSessions.firstOrNull { it.file == active.sessionFile }
                val summary = known ?: SessionSummary(
                    id = active.sessionId,
                    file = active.sessionFile,
                    cwd = active.cwd,
                    title = "",
                    model = "",
                    messageCount = 0,
                    toolCount = 0,
                    updatedAt = active.lastUsed,
                    archived = false
                )
                openSession(summary)
            } catch (error: Exception) {
                if (isCurrent(token)) {
                    pushNotice(error.message ?: "Kunne ikke hente aktiv Bridge-samtale", "error")
                }
            }
        }
    }

    /**
     * OMP gives every new session the model of the runtime, which is often not
     * the one the user picked. Re-apply the remembered choice so the header and
     * the settings row keep showing the selected model.
     */
    private suspend fun applyRememberedModel(cwd: String, token: Int = epoch) {
        val saved = settings.state.value
        if (saved.lastModelProvider.isBlank() || saved.lastModelId.isBlank()) return
        try {
            val available = client.models(cwd)
            if (!isCurrent(token)) return
            val target = available.firstOrNull {
                it.provider == saved.lastModelProvider && it.id == saved.lastModelId
            } ?: return
            val applied = client.setModel(cwd, target) ?: target
            if (!isCurrent(token)) return
            _state.update { it.copy(chat = it.chat.copy(model = applied), models = available) }
        } catch (_: Exception) {
            // A missing model must never block a new conversation.
        }
    }

    fun rename(file: String, name: String) {
        if (file.isBlank() || name.isBlank()) return
        val token = epoch
        scope.launch {
            try {
                client.renameSession(file, name.trim())
                if (!isCurrent(token)) return@launch
                _state.update { st ->
                    if (st.sessionFile == file) st.copy(title = name.trim()) else st
                }
                loadSessions()
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke gi nytt navn", "error")
            }
        }
    }

    /** Returnerer om en sesjon er favorittmarkert. */
    fun isFavorite(sessionId: String): Boolean = favoriteSessionIds.contains(sessionId)

    /** Bytt favorittstatus for en sesjon og lagre varig. */
    fun toggleFavorite(sessionId: String) {
        if (sessionId.isBlank()) return
        val nowFavorite = !favoriteSessionIds.contains(sessionId)
        if (nowFavorite) {
            favoriteSessionIds.add(sessionId)
        } else {
            favoriteSessionIds.remove(sessionId)
        }
        // Lagre varig
        scope.launch {
            val ctx = settings.context
            val prefsFav = ctx.getSharedPreferences("omp_favorites", android.content.Context.MODE_PRIVATE)
            prefsFav.edit().putString("fav_sessions", favoriteSessionIds.joinToString(",")).apply()
        }
    }

    fun archive(file: String, archived: Boolean) {
        val token = epoch
        scope.launch {
            try {
                client.archiveSession(file, archived)
                if (!isCurrent(token)) return@launch
                loadSessions(token)
                if (archived && _state.value.sessionFile == file) {
                    _state.update { it.copy(sessionFile = "", title = "", messages = emptyList()) }
                }
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke arkivere", "error")
            }
        }
    }

    fun delete(file: String) {
        val token = epoch
        scope.launch {
            try {
                client.deleteSession(file)
                if (!isCurrent(token)) return@launch
                rememberLast(_state.value.cwd, "")
                if (_state.value.sessionFile == file) {
                    synchronized(lock) { messages.clear() }
                    _state.update { it.copy(sessionFile = "", title = "", messages = emptyList(), chat = ChatState()) }
                }
                loadSessions(token)
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke slette samtalen", "error")
            }
        }
    }

    private var fallbackModel: ModelInfo? = null
    var fallbackModelKey: String = ""

    fun setFallbackModel(model: ModelInfo?) {
        fallbackModel = model
    }

    fun setModel(model: ModelInfo) {
        val cwd = resolvedCwd()
        if (!CwdPath.isUsable(cwd) || _state.value.readOnly) {
            pushNotice("Velg en gyldig prosjektmappe først", "warning")
            return
        }
        val token = epoch
        // Show and keep the choice immediately. A slow or blocked OMP runtime
        // must not make the selection look like it was reset.
        settings.rememberModel(model.provider, model.id, model.name)
        _state.update {
            it.copy(
                cwd = it.cwd.ifBlank { cwd },
                chat = it.chat.copy(model = model)
            )
        }
        onModelSelected(model.key)
        resortModels()
        scope.launch {
            try {
                val applied = client.setModel(cwd, model) ?: model
                if (!isCurrent(token)) return@launch
                settings.rememberModel(applied.provider, applied.id, applied.name)
                _state.update { it.copy(chat = it.chat.copy(model = applied)) }
                if (applied.key != model.key) {
                    onModelSelected(applied.key)
                    resortModels()
                }
            } catch (error: Exception) {
                if (!isCurrent(token)) return@launch
                var fb = fallbackModel
                if (fb == null && fallbackModelKey.isNotBlank()) {
                    fb = _state.value.models.firstOrNull { it.key == fallbackModelKey }
                }
                if (fb != null && fb.key != model.key) {
                    try {
                        val applied = client.setModel(cwd, fb) ?: fb
                        if (!isCurrent(token)) return@launch
                        settings.rememberModel(applied.provider, applied.id, applied.name)
                        _state.update { it.copy(chat = it.chat.copy(model = applied)) }
                        pushNotice("Byttet til reservemodell: ${applied.name}", "warning")
                        return@launch
                    } catch (_: Exception) { }
                }
                pushNotice(
                    error.message ?: "Kunne ikke bytte modell",
                    "error"
                )
            }
        }
    }

    /** Endre OMPs godkjenningsmodus og synkroniser med serveren. */
    fun saveGotifyConfig(enabled: Boolean, server: String, token: String, events: Map<String, Boolean>): Boolean {
        return try {
            val body = com.google.gson.JsonObject().apply {
                addProperty("enabled", enabled)
                addProperty("server", server)
                if (token.isNotBlank()) addProperty("token", token)
                val ev = com.google.gson.JsonObject()
                events.forEach { (k, v) -> ev.addProperty(k, v) }
                add("events", ev)
            }
            client.callJson("POST", "/api/gotify/config", body, 15)
            true
        } catch (e: Exception) {
            pushNotice(e.message ?: "Gotify-konfig feilet", "error")
            false
        }
    }

    fun testGotify(): Boolean {
        return try {
            val json = client.callJson("POST", "/api/gotify/test", com.google.gson.JsonObject(), 15)
            json.bool("ok")
        } catch (e: Exception) {
            pushNotice(e.message ?: "Gotify-test feilet", "error")
            false
        }
    }

    fun getGotifyConfig(): Triple<Boolean, String, String> {
        return try {
            val json = client.callJson("GET", "/api/gotify/config", null, 15)
            Triple(json.bool("enabled"), json.str("server"), json.str("tokenMasked"))
        } catch (e: Exception) {
            Triple(false, "", "")
        }
    }

    fun setApprovalMode(mode: String) {
        val current = settings.state.value.approvalMode
        if (current == mode) return
        settings.setApprovalMode(mode)
        _state.update { it.copy(approvalMode = mode) }
        scope.launch {
            try {
                client.setApprovalMode(mode)
            } catch (error: Exception) {
                settings.setApprovalMode(current)
                _state.update { it.copy(approvalMode = current) }
                pushNotice("Kunne ikke endre godkjenningsmodus: ${error.message}", "error")
            }
        }
    }

    /** Velg aktiv STT-motor på PC-serveren. */
    fun setSttEngine(engineId: String) {
        settings.setSttEngine(engineId)
        scope.launch {
            try {
                client.selectVoiceEngine("stt", engineId)
            } catch (error: Exception) {
                settings.setSttEngine("")
                pushNotice("Kunne ikke velge STT-motor: ${error.message}", "error")
            }
        }
    }

    /** Velg aktiv TTS-motor på PC-serveren. */
    fun setTtsEngine(engineId: String) {
        settings.setTtsEngine(engineId)
        scope.launch {
            try {
                client.selectVoiceEngine("tts", engineId)
            } catch (error: Exception) {
                settings.setTtsEngine("")
                pushNotice("Kunne ikke velge TTS-motor: ${error.message}", "error")
            }
        }
    }

    /** Velg stemme (speaker per motor) på PC-serveren. */
    fun setTtsVoice(engineId: String, voiceId: String) {
        settings.setTtsEngine(engineId)
        scope.launch {
            try {
                client.selectVoiceEngine("tts", engineId, voiceId)
            } catch (error: Exception) {
                pushNotice("Kunne ikke velge stemme: ${error.message}", "error")
            }
        }
    }

    /** Starter bakgrunnsinstallasjon av en tale-motor. */
    fun installVoiceEngine(kind: String, engineId: String) {
        scope.launch {
            try {
                client.installVoiceEngine(kind, engineId)
                pushNotice("Installasjonen kjører i bakgrunnen på PC-en.", "info")
            } catch (error: Exception) {
                pushNotice("Kunne ikke starte installasjon: ${error.message}", "error")
            }
        }
    }

    /** Hent tale-motorlisten fra bridgen. */
    fun loadVoiceEngines() {
        val token = epoch
        scope.launch {
            try {
                val engines = client.voiceEngineList()
                if (!isCurrent(token)) return@launch
                _state.update { it.copy(voiceEngines = engines) }
            } catch (error: Exception) {
                if (isCurrent(token)) {
                    pushNotice("Kunne ikke hente tale-motorer: ${error.message}", "error")
                }
            }
        }
    }

    fun send(text: String, images: List<String> = emptyList(), attachments: List<Upload> = emptyList(), streamingBehavior: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val token = epoch
        scope.launch {
            if (!isCurrent(token)) return@launch
            if (_state.value.readOnly) {
                pushNotice("Denne samtalen er skrivebeskyttet. Start en ny samtale i en gyldig mappe.", "warning")
                return@launch
            }
            if (!ensureSession()) return@launch
            if (!isCurrent(token)) return@launch
            val current = _state.value
            val cwd = current.cwd.takeIf { CwdPath.isUsable(it) } ?: resolvedCwd()
            if (!CwdPath.isUsable(cwd)) {
                pushNotice("Velg en prosjektmappe først", "warning")
                return@launch
            }
            val file = current.sessionFile
            val localId = "local-${System.currentTimeMillis()}"
            synchronized(lock) {
                // Deduplisering: ikke legg til samme bruker melding to ganger
                val lastMsg = messages.lastOrNull()
                val isDuplicate = lastMsg?.role == "user" && lastMsg.text == trimmed && lastMsg.images == images.size
                if (!isDuplicate) {
                    messages += ChatMessage(
                        id = localId,
                        role = "user",
                        text = trimmed,
                        timestamp = System.currentTimeMillis(),
                        status = "sending",
                        images = images.size
                    )
                    localUsers.addLast(localId)
                    dirty = true
                }
                // Første brukernotering i en ny samtale lagres for automatisk tittelgenerering
                if (autoTitleSession && firstUserMessage.isBlank()) {
                    firstUserMessage = trimmed
                }
            }
            publish()
            if (streamingBehavior == "followUp") {
                _state.update { it.copy(queuedMessageIds = it.queuedMessageIds + localId) }
            }
            try {
                val note = if (attachments.isNotEmpty()) {
                    val saved = client.uploadFiles(cwd, attachments.map { Triple(it.name, it.mime, it.base64) })
                    if (!isCurrent(token)) return@launch
                    saved.joinToString("\n") { "- `$it`" }
                } else ""
                val body = if (note.isNotEmpty()) "$trimmed\n\nVedlagte filer (ligger i prosjektmappen):\n$note" else trimmed
                client.prompt(cwd, file.ifBlank { null }, body, images, streamingBehavior)
                if (!isCurrent(token)) return@launch
                if (streamingBehavior == "followUp") {
                    markLocalUser(localId, "queued")
                } else {
                    markLocalUser(localId, "done")
                }
            } catch (error: Exception) {
                if (!isCurrent(token)) return@launch
                markLocalUser(localId, "error")
                _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
                pushNotice(error.message ?: "Kunne ikke sende meldingen", "error")
            }
        }
    }

    fun stop() {
        val cwd = _state.value.cwd
        if (!CwdPath.isUsable(cwd) || _state.value.readOnly) return
        val token = epoch
        scope.launch {
            try {
                client.abort(cwd)
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke stoppe", "error")
            }
            if (!isCurrent(token)) return@launch
            _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
            synchronized(lock) {
                val lastUser = messages.indexOfLast { it.isUser }
                val lastAssistant = messages.indexOfLast { it.isAssistant }
                if (lastAssistant > lastUser) {
                    val target = messages[lastAssistant]
                    val empty = target.text.isBlank() && target.toolCalls.isEmpty()
                    messages[lastAssistant] = target.copy(
                        streaming = false,
                        status = "stopped",
                        // An empty bubble would otherwise look like a silent failure.
                        error = if (empty && target.error.isBlank()) "Interrupted by user" else target.error
                    )
                } else {
                    // Nothing streamed yet, so there is no bubble to mark. Add a
                    // short note instead of leaving the turn looking unanswered.
                    messages += ChatMessage(
                        id = "stopped-${System.currentTimeMillis()}",
                        role = "assistant",
                        text = "",
                        error = "Interrupted by user",
                        status = "stopped",
                        timestamp = System.currentTimeMillis()
                    )
                }
                dirty = true
            }
            publish()
        }
    }

    fun regenerate(assistantId: String) {
        val target = _state.value.messages.firstOrNull { it.id == assistantId } ?: return
        val index = _state.value.messages.indexOf(target)
        val user = _state.value.messages.subList(0, maxOf(0, index)).lastOrNull { it.isUser } ?: return
        branchAndPrompt(user.id, user.text)
    }

    fun editAndResend(userId: String, newText: String) {
        branchAndPrompt(userId, newText.trim())
    }

    private fun branchAndPrompt(entryId: String, text: String) {
        if (_state.value.readOnly) {
            pushNotice("Denne samtalen er skrivebeskyttet.", "warning")
            return
        }
        val cwd = _state.value.cwd
        val file = _state.value.sessionFile
        if (!CwdPath.isUsable(cwd) || file.isBlank() || entryId.isBlank() || text.isBlank()) return
        val token = epoch
        val index = _state.value.messages.indexOfFirst { it.id == entryId }
        if (index < 0) return
        synchronized(lock) {
            while (messages.size > index) messages.removeAt(messages.size - 1)
            streamText.clear()
            streamThinking.clear()
        }
        publish()
        val localId = "local-${System.currentTimeMillis()}"
        synchronized(lock) {
            messages += ChatMessage(
                id = localId,
                role = "user",
                text = text,
                timestamp = System.currentTimeMillis(),
                status = "sending"
            )
            localUsers.addLast(localId)
            dirty = true
        }
        publish()
        _state.update { it.copy(streaming = true, streamingStartedAt = System.currentTimeMillis()) }
        scope.launch {
            try {
                client.regenerate(cwd, file, entryId, text)
                if (!isCurrent(token)) return@launch
                markLocalUser(localId, "done")
            } catch (error: Exception) {
                if (!isCurrent(token)) return@launch
                markLocalUser(localId, "error")
                _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
                pushNotice(error.message ?: "Kunne ikke kjøre på nytt", "error")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Approvals / terminal
    // -----------------------------------------------------------------------

    fun answer(
        request: UiRequest,
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
        answers: List<Pair<String, List<String>>>? = null
    ) {
        _state.update { it.copy(request = null) }
        val token = epoch
        scope.launch {
            try {
                client.answer(request, value = value, confirmed = confirmed, cancelled = cancelled, answers = answers)
            } catch (error: Exception) {
                if (isCurrent(token)) pushNotice(error.message ?: "Kunne ikke svare OMP", "error")
            }
        }
    }

    fun runTerminal(command: String) {
        val cwd = _state.value.cwd
        val trimmed = command.trim()
        if (!CwdPath.isUsable(cwd) || trimmed.isEmpty() || _state.value.readOnly) return
        val token = epoch
        val id = ++terminalSeq
        _state.update {
            it.copy(terminal = it.terminal + TerminalEntry(id = id, command = trimmed, running = true))
        }
        scope.launch {
            try {
                val result = client.bash(cwd, trimmed)
                if (!isCurrent(token)) return@launch
                val output = result.str("output")
                _state.update { st ->
                    st.copy(
                        terminal = st.terminal.map { entry ->
                            if (entry.id == id) entry.copy(
                                output = output,
                                running = false,
                                exitCode = result.int("exitCode"),
                                isError = result.int("exitCode") != 0 || output.contains("error", ignoreCase = true) && result.int("exitCode") != 0
                            ) else entry
                        }
                    )
                }
            } catch (error: Exception) {
                if (!isCurrent(token)) return@launch
                _state.update { st ->
                    st.copy(
                        terminal = st.terminal.map { entry ->
                            if (entry.id == id) entry.copy(output = error.message ?: "Feil", running = false, isError = true)
                            else entry
                        }
                    )
                }
            }
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminal = emptyList()) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = "") }
    }

    // -----------------------------------------------------------------------
    // Event handling
    // -----------------------------------------------------------------------

    private fun detectFileChange(event: Event): String? {
        val args = event.json.get("args") ?: return null
        val text = args.toString()
        // best-effort: look for common path keys
        for (key in listOf("path", "file", "filename", "target", "filePath", "to")) {
            val v = try { event.json.obj("args")?.str(key) } catch (_: Throwable) { null }
            if (!v.isNullOrBlank()) return v
        }
        // fallback: regex for absolute paths in the args text
        val match = Regex("""[A-Za-z]:[\\/][^\s"'<>]+|/(?:home|tmp|opt|var|etc|usr)/[^\s"'<>]+""").find(text)
        return match?.value
    }

    private fun handle(event: Event, token: Int = epoch) {
        if (!isCurrent(token)) return
        when (event.type) {
            "connected" -> {
                _state.update { it.copy(connected = true, error = "") }
                taskListener?.invoke(TaskEvent.ConnectionRestored())
                resyncAfterReconnect("sse", token)
                return
            }
            "disconnected" -> {
                _state.update { it.copy(connected = false) }
                taskListener?.invoke(TaskEvent.ConnectionLost())
                return
            }
            "connection_error" -> {
                _state.update { it.copy(connected = false, error = event.json.str("error")) }
                taskListener?.invoke(TaskEvent.ConnectionLost())
                return
            }
            "hello" -> {
                val state = event.json.obj("state")
                if (state != null && sameProject(event)) applyState(state, token)
                return
            }
        }

        // While a read-only transcript is on screen no live event from another
        // runtime may change the message list or the header.
        if (_state.value.readOnly) return
        if (!sameProject(event)) return

        when (event.type) {
            "ready" -> _state.update { it.copy(connected = true) }
            "state" -> event.json.obj("state")?.let { applyState(it, token) }
            "models" -> {
                val models = event.json.arr("models").mapNotNull { it.asObjectOrNull()?.let(Parsers::model) }
                if (models.isNotEmpty()) _state.update { it.copy(models = models) }
            }
            "streaming" -> {
                val active = event.json.bool("active")
                _state.update { it.copy(streaming = active) }
                if (!active) synchronized(lock) {
                    val index = messages.indexOfLast { it.role == "assistant" }
                    if (index >= 0) messages[index] = messages[index].copy(streaming = false)
                }
            }
            "message_start" -> {
                if (event.json.str("role") == "assistant") {
                    taskListener?.invoke(TaskEvent.Started(
                        sessionId = _state.value.sessionFile,
                        title = _state.value.title.ifBlank { _state.value.cwd.substringAfterLast('/', "").substringAfterLast('\\', "") },
                        modelName = _state.value.chat.model?.name.orEmpty(),
                        cwd = _state.value.cwd
                    ))
                }
                onMessageStart(event)
            }
            "message_delta" -> {
                // no-op for tasks
                onDelta(event)
            }
            "message_end" -> {
                onMessageEnd(event)
                taskListener?.invoke(TaskEvent.Completed(_state.value.sessionFile))
            }
            "tool_start" -> {
                val toolName = event.json.str("toolName")
                val args = event.json.get("args")?.toString().orEmpty()
                taskListener?.invoke(TaskEvent.ToolStarted(_state.value.sessionFile, toolName, args))
                // detect file changes from tool args (best-effort)
                detectFileChange(event)?.let { path ->
                    taskListener?.invoke(TaskEvent.FileChanged(_state.value.sessionFile, path))
                }
                onToolStart(event)
            }
            "tool_update", "tool_stream" -> onToolUpdate(event)
            "tool_end" -> {
                val toolName = event.json.str("toolName")
                val isError = event.json.bool("isError")
                val output = toolOutput(event.json.get("result")).take(200)
                taskListener?.invoke(TaskEvent.ToolEnded(_state.value.sessionFile, toolName, output, isError))
                onToolEnd(event)
            }
            "prompt_result" -> onPromptResult(event)
            "session_settled" -> _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
            "ui_request" -> {
                val request = Parsers.uiRequest(event.json)
                if (request.id.isNotBlank()) {
                    _state.update { it.copy(request = request) }
                    if (request.isApproval) {
                        taskListener?.invoke(TaskEvent.ApprovalRequest(_state.value.sessionFile, request))
                    }
                }
            }
            "ui_cancel" -> {
                _state.update { it.copy(request = null) }
                taskListener?.invoke(TaskEvent.ApprovalResolved(_state.value.sessionFile))
            }
            "session_info" -> {
                val title = event.json.str("title")
                if (title.isNotBlank()) _state.update { it.copy(title = title) }
            }
            "notice" -> {
                val message = event.json.str("message").ifBlank {
                    event.json.obj("notice")?.str("message") ?: ""
                }
                if (message.isNotBlank()) pushNotice(message, event.json.str("level").ifBlank { "info" })
            }
            "backend" -> {
                if (event.json.str("status") == "offline") {
                    val reason = event.json.str("error")
                    _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
                    taskListener?.invoke(TaskEvent.Failed(_state.value.sessionFile, reason.ifBlank { "OMP-prosessen stoppet" }))
                    pushNotice(reason.ifBlank { "OMP-prosessen stoppet" }, "error")
                }
            }
            "rpc_error" -> pushNotice(event.json.str("error"), "error")
        }
    }

    private fun applyState(json: JsonObject, token: Int = epoch) {
        if (!isCurrent(token)) return
        if (_state.value.readOnly) return
        val parsed = Parsers.state(json)
        val title = parsed.sessionName.ifBlank { _state.value.title }
        _state.update { st ->
            // OMP reports `model: null` while a runtime is still starting or
            // blocked. Keep the model the user picked instead of blanking the
            // header and the settings row.
            val chat = if (parsed.model == null && st.chat.model != null) {
                parsed.copy(model = st.chat.model)
            } else parsed
            st.copy(
                chat = chat,
                streaming = chat.isStreaming,
                todos = chat.todoPhases,
                title = title,
                sessionFile = chat.sessionFile.ifBlank { st.sessionFile },
                models = if (chat.model != null) st.models else st.models
            )
        }
    }

    private fun onMessageStart(event: Event) {
        val id = event.json.str("messageId")
        if (id.isBlank() || event.json.str("role") != "assistant") return
        val streaming = event.json.bool("active", true)
        synchronized(lock) {
            if (messages.none { it.id == id }) {
                val index = messages.indexOfLast { it.role == "assistant" && it.streaming }
                val placeholder = ChatMessage(
                    id = id,
                    role = "assistant",
                    timestamp = System.currentTimeMillis(),
                    status = "streaming",
                    streaming = true
                )
                if (index >= 0) {
                    // The previous assistant message was only a placeholder for this turn.
                    val existing = messages[index]
                    if (existing.id.startsWith("pending-")) {
                        messages[index] = placeholder
                    } else {
                        messages += placeholder
                    }
                } else {
                    messages += placeholder
                }
                activeId = id
            } else {
                val index = messages.indexOfFirst { it.id == id }
                messages[index] = messages[index].copy(streaming = true, status = "streaming")
                activeId = id
            }
            dirty = true
        }
        publish()
        _state.update { it.copy(streaming = true, streamingStartedAt = System.currentTimeMillis()) }
    }

    private fun onDelta(event: Event) {
        val id = event.json.str("messageId").ifBlank { activeId }
        if (id.isBlank()) return
        val delta = event.json.str("delta")
        if (delta.isEmpty()) return
        synchronized(lock) {
            if (messages.none { it.id == id }) {
                messages += ChatMessage(
                    id = id,
                    role = "assistant",
                    timestamp = System.currentTimeMillis(),
                    status = "streaming",
                    streaming = true
                )
                activeId = id
            }
            if (event.json.str("kind") == "thinking") {
                streamThinking.getOrPut(id) { StringBuilder() }.append(delta)
            } else {
                streamText.getOrPut(id) { StringBuilder() }.append(delta)
            }
            dirty = true
        }
    }

    private fun onMessageEnd(event: Event) {
        val id = event.json.str("messageId")
        val message = event.json.obj("message") ?: return
        val role = message.str("role")
        if (role == "assistant") {
            val parsed = Parsers.message(message)
            synchronized(lock) {
                streamText.remove(id)
                streamThinking.remove(id)
                val index = messages.indexOfFirst { it.id == id }
                val merged = parsed.copy(
                    id = id.ifBlank { parsed.id },
                    text = parsed.text.ifBlank { messages.getOrNull(index)?.text ?: "" },
                    thinking = parsed.thinking.ifBlank { messages.getOrNull(index)?.thinking ?: "" },
                    toolCalls = mergeTools(messages.getOrNull(index)?.toolCalls ?: emptyList(), parsed.toolCalls),
                    streaming = false,
                    status = parsed.status
                )
                if (index >= 0) messages[index] = merged else messages += merged
                dirty = true
            }
            publish()
            // Automatisk tittel: første ferdige assistent-svar → generer tittel
            if (autoTitleSession && firstUserMessage.isNotBlank() && _state.value.sessionFile.isNotBlank()) {
                val generated = generateTitle(firstUserMessage)
                if (generated.isNotBlank()) {
                    autoTitleSession = false
                    firstUserMessage = ""
                    scope.launch {
                        try {
                            client.renameSession(_state.value.sessionFile, generated)
                            _state.update { it.copy(title = generated) }
                            loadSessions()
                        } catch (_: Exception) {
                            // Tittelgenerering må ikke blokkere samtalen
                        }
                    }
                }
            }
            return
        }

        val parsed = Parsers.message(message)
        synchronized(lock) {
            if (role == "user") {
                val localId = localUsers.removeFirstOrNull()
                val anchor = messages.indexOfFirst { it.id == localId }
                if (anchor >= 0) messages[anchor] = parsed else messages += parsed
            } else {
                messages += parsed
            }
            dirty = true
        }
        publish()
    }

    private fun onToolStart(event: Event) {
        val id = event.json.str("toolCallId")
        if (id.isBlank()) return
        val name = event.json.str("toolName")
        val args = event.json.get("args")?.toString() ?: ""
        val intent = event.json.str("intent")
        val target = activeAssistant()
        val tool = ToolCallInfo(id = id, name = name, args = args, state = "running", intent = intent)
        synchronized(lock) {
            val messageIndex = messages.indexOfFirst { it.id == target }
            if (messageIndex >= 0) {
                val msg = messages[messageIndex]
                val existing = msg.toolCalls.indexOfFirst { it.id == id }
                val tools = msg.toolCalls.toMutableList()
                if (existing >= 0) tools[existing] = tool else tools += tool
                messages[messageIndex] = msg.copy(toolCalls = tools)
                dirty = true
            } else {
                messages += ChatMessage(
                    id = "pending-$id",
                    role = "assistant",
                    toolCalls = listOf(tool),
                    timestamp = System.currentTimeMillis(),
                    status = "streaming",
                    streaming = true
                )
                dirty = true
            }
        }
        publish()
    }

    private fun onToolUpdate(event: Event) {
        val id = event.json.str("toolCallId")
        val partial = event.json.get("partial")
        if (id.isBlank() || partial == null) return
        val text = partial.toString()
        synchronized(lock) {
            for (index in messages.indices) {
                val msg = messages[index]
                val toolIndex = msg.toolCalls.indexOfFirst { it.id == id }
                if (toolIndex < 0) continue
                val tools = msg.toolCalls.toMutableList()
                tools[toolIndex] = tools[toolIndex].copy(result = text, state = "running")
                messages[index] = msg.copy(toolCalls = tools)
                dirty = true
                break
            }
        }
    }

    private fun onToolEnd(event: Event) {
        val id = event.json.str("toolCallId")
        if (id.isBlank()) return
        val isError = event.json.bool("isError")
        val output = toolOutput(event.json.get("result"))
        synchronized(lock) {
            for (index in messages.indices) {
                val msg = messages[index]
                val toolIndex = msg.toolCalls.indexOfFirst { it.id == id }
                if (toolIndex < 0) continue
                val tools = msg.toolCalls.toMutableList()
                tools[toolIndex] = tools[toolIndex].copy(
                    result = output.ifBlank { tools[toolIndex].result },
                    state = if (isError) "error" else "done",
                    isError = isError
                )
                messages[index] = msg.copy(toolCalls = tools)
                dirty = true
                break
            }
        }
        publish()
    }

    private fun onPromptResult(event: Event) {
        val status = event.json.str("status")
        val error = event.json.obj("error")
        _state.update { it.copy(streaming = false, streamingStartedAt = 0) }
        synchronized(lock) {
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                val msg = messages[index]
                messages[index] = msg.copy(
                    streaming = false,
                    status = if (status == "error") "error" else if (status == "aborted") "stopped" else "done",
                    error = error?.str("message") ?: ""
                )
            }
            dirty = true
        }
        publish()
        if (status == "error") {
            pushNotice(error?.str("message") ?: "OMP returnerte en feil", "error")
        }
        // The stream is a convenience; the jsonl transcript is authoritative.
        reloadTranscript()
        scope.launch { runCatching { loadSessions() } }
    }

    fun reloadTranscript() {
        val snapshot = _state.value
        // A read-only chat has no runtime session; its stored file is the only
        // source of truth.
        val file = if (snapshot.readOnly) snapshot.readOnlyFile else snapshot.sessionFile
        if (file.isBlank()) return
        val token = epoch
        scope.launch {
            try {
                val (session, transcript) = client.transcript(file)
                if (!isCurrent(token)) return@launch
                if (transcript.isEmpty()) return@launch
                val current = _state.value
                val stillSame = if (current.readOnly) {
                    current.readOnlyFile == file
                } else {
                    current.sessionFile == file
                }
                if (!stillSame) return@launch
                synchronized(lock) {
                    messages.clear()
                    messages.addAll(transcript)
                    streamText.clear()
                    streamThinking.clear()
                    dirty = true
                }
                publish()
                _state.update { it.copy(queuedMessageIds = emptySet()) }
                if (session != null && _state.value.title.isBlank()) {
                    _state.update { it.copy(title = session.title) }
                }
            } catch (_: Exception) {
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun activeAssistant(): String {
        synchronized(lock) {
            if (activeId.isNotBlank() && messages.any { it.id == activeId }) return activeId
            val last = messages.lastOrNull { it.role == "assistant" } ?: return ""
            return last.id
        }
    }

    private fun mergeTools(existing: List<ToolCallInfo>, incoming: List<ToolCallInfo>): List<ToolCallInfo> {
        if (incoming.isEmpty()) return existing
        val merged = existing.toMutableList()
        for (tool in incoming) {
            val index = merged.indexOfFirst { it.id == tool.id }
            if (index >= 0) {
                val current = merged[index]
                merged[index] = current.copy(
                    result = tool.result.ifBlank { current.result },
                    state = if (current.state == "running") current.state else tool.state,
                    isError = current.isError || tool.isError
                )
            } else {
                merged += tool
            }
        }
        return merged
    }

    private fun markLocalUser(id: String, status: String) {
        synchronized(lock) {
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) messages[index] = messages[index].copy(status = status)
            dirty = true
        }
        publish()
    }

    private fun publish() {
        val snapshot = synchronized(lock) { messages.toList() }
        _state.update { it.copy(messages = snapshot) }
    }

    /**
     * Generer en kort samtaletittel fra brukerens første melding.
     * Tar de første ~40 tegnene, avkortet ved ordgrense.
     * Returnerer tom streng hvis det ikke er noe meningsfullt å generere fra.
     */
    private fun generateTitle(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        // Fjern ledende "hva er", "kan du", osv. for mer relevante titler
        val start = trimmed.take(80)
        val endIndex = start.lastIndexOfAny(charArrayOf(' ', '.', '!', '?', ',', ':'))
        val cut = if (endIndex > 10 && endIndex < start.length - 1) start.take(endIndex) else start
        return cut.trim().capitalize(Locale.getDefault())
    }

    private fun flush() {
        val snapshot: List<ChatMessage>
        synchronized(lock) {
            if (!dirty) return
            dirty = false
            for (index in messages.indices) {
                val msg = messages[index]
                val text = streamText[msg.id]
                val thinking = streamThinking[msg.id]
                if (text == null && thinking == null) continue
                messages[index] = msg.copy(
                    text = text?.toString() ?: msg.text,
                    thinking = thinking?.toString() ?: msg.thinking,
                    streaming = true
                )
            }
            snapshot = messages.toList()
        }
        // Only the message list is republished here; the streaming flag is owned by
        // the event stream so a late flush can never re-arm the stop button.
        _state.update { it.copy(messages = snapshot) }
    }

    private fun sameProject(event: Event): Boolean {
        val eventCwd = event.json.str("cwd")
        val current = _state.value.cwd
        if (eventCwd.isBlank()) return true
        if (current.isBlank()) return true
        return CwdPath.same(eventCwd, current)
    }

    fun pushNotice(text: String, level: String = "info") {
        if (text.isBlank()) return
        _state.update { it.copy(notice = Notice(++noticeSeq, text, level)) }
    }

    private fun toolOutput(element: com.google.gson.JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        val json = element.asJsonObject
        val content = json.get("content")
        if (content != null && content.isJsonArray) {
            val text = content.asJsonArray.mapNotNull { block ->
                val obj = block.asObjectOrNull() ?: return@mapNotNull null
                if (obj.str("type") == "text") obj.str("text") else null
            }.joinToString("\n")
            if (text.isNotBlank()) return text
        }
        return json.str("output").ifBlank { json.toString() }
    }

    data class Upload(val name: String, val mime: String, val base64: String)
}
