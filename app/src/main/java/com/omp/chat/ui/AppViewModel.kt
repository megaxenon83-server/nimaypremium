package com.omp.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omp.chat.OmpApp
import com.omp.chat.data.AppSettings
import com.omp.chat.data.ChatRepository
import com.omp.chat.data.FileManagerRepository
import com.omp.chat.data.PcProfile
import com.omp.chat.data.PcProfileStore
import com.omp.chat.data.TaskStore
import com.omp.chat.data.TerminalTab
import com.omp.chat.data.WolSender
import com.omp.chat.data.SettingsStore
import com.omp.chat.data.VoiceCallEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.omp.chat.ui.components.Attachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OmpApp
    val repository: ChatRepository = app.repository
    val files: FileManagerRepository = app.files
    val settings: SettingsStore = app.settings
    val pcProfiles: PcProfileStore = app.pcProfiles
    val tasks: TaskStore = app.tasks

    val state = repository.state
    val settingsState: StateFlow<AppSettings> = settings.state

    private val _pcProfiles = MutableStateFlow<List<PcProfile>>(pcProfiles.load())
    val pcProfileList: StateFlow<List<PcProfile>> = _pcProfiles.asStateFlow()

    private val _activePcId = MutableStateFlow(pcProfiles.activeId())
    val activePcId: StateFlow<String> = _activePcId.asStateFlow()

    private val _favoriteSessions = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSessions: StateFlow<Set<String>> = _favoriteSessions.asStateFlow()

    private val _pcStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val pcStatus: StateFlow<Map<String, String>> = _pcStatus.asStateFlow()

    /** Full spoken-conversation pipeline (mic -> STT -> OMP -> TTS -> speaker). */
    val voice = VoiceCallEngine(application, settings, repository)
    val voiceState = voice.state

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    private val _speech = MutableStateFlow<String?>(null)
    val speech: StateFlow<String?> = _speech.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    init {
        // Migrer eksisterende enkelt-PC til profil-listen.
        val existing = pcProfiles.load()
        if (existing.isEmpty() && settings.baseUrl().isNotBlank()) {
            val profile = PcProfile(
                name = "Min PC",
                baseUrl = settings.baseUrl(),
                token = settings.token().orEmpty(),
                lastCwd = settings.state.value.lastCwd,
                lastSession = settings.state.value.lastSession
            )
            pcProfiles.save(listOf(profile))
            pcProfiles.setActiveId(profile.id)
            _pcProfiles.value = listOf(profile)
            _activePcId.value = profile.id
        }
        // Seed the active profile from the old global position, but only when
        // the global address actually belongs to that PC.
        seedActiveProfilePosition()
        refreshFavorites()
        if (settings.baseUrl().isNotBlank() && settings.token() != null) {
            repository.connect()
        }
        // Per-PC position: the repository persists every confirmed cwd/session
        // to the profile that is active right now, never to another PC.
        repository.onRememberLast = { cwd, session -> savePositionToActive(cwd, session) }
        repository.modelUsageProvider = { activePc()?.modelUsageCounts ?: emptyMap() }
        repository.onModelSelected = { key -> recordModelUsage(key) }
        // Live oppgaveovervåking (Etappe 3): koble ChatRepository-hendelser til TaskStore.
        repository.taskListener = { event ->
            val pcId = pcProfiles.activeId().ifBlank { "default" }
            tasks.handle(pcId, event)
        }
        // Automatisk failover mellom alle kjente adresser til aktiv PC.
        repository.candidatesProvider = { activePc()?.candidateUrls().orEmpty() }
        repository.onEndpointResolved = { url -> promoteEndpoint(url) }
        repository.onHostsDiscovered = { hosts -> mergePcEndpoints(_activePcId.value, hosts) }
        // Automatisk MAC-henting ved tilkobling for PC-er uten MAC
        viewModelScope.launch {
            state.collect { st ->
                if (st.connected && st.loadedOnce) {
                    val pc = activePc()
                    if (pc != null && pc.mac.isBlank()) {
                        try {
                            val interfaces = repository.bridge.networkInterfaces()
                            val preferred = interfaces.firstOrNull { it.wolCapable } ?: interfaces.firstOrNull()
                            if (preferred != null && preferred.mac.isNotBlank()) {
                                savePcProfile(pc.copy(mac = preferred.mac))
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        // Periodisk oppdateringssjekk (Etappe 8)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                com.omp.chat.data.UpdateChecker.fetch(application)?.let { info ->
                    if (info.androidVersionCode > 0) {
                        // notify via toast if newer (simple check: version name differs)
                        _toast.value = "Ny versjon tilgjengelig: ${info.androidVersion}"
                    }
                }
            }
        }
    }

    /** Copy the legacy global position into the active profile once. */
    private fun seedActiveProfilePosition() {
        val activeId = pcProfiles.activeId()
        if (activeId.isBlank()) return
        val list = pcProfiles.load()
        val idx = list.indexOfFirst { it.id == activeId }
        if (idx < 0) return
        val profile = list[idx]
        if (profile.lastCwd.isNotBlank() || profile.lastSession.isNotBlank()) return
        val saved = settings.state.value
        if (saved.lastCwd.isBlank() && saved.lastSession.isBlank()) return
        val base = SettingsStore.normalizeBaseUrl(settings.baseUrl())
        if (base.isBlank() || profile.candidateUrls().none { it == base }) return
        list[idx] = profile.copy(lastCwd = saved.lastCwd, lastSession = saved.lastSession)
        pcProfiles.save(list)
        _pcProfiles.value = list
    }

    /** Persist the position to the profile that is active right now. */
    private fun savePositionToActive(cwd: String, session: String) {
        savePositionTo(pcProfiles.activeId(), cwd, session)
    }

    private fun savePositionTo(id: String, cwd: String, session: String) {
        if (id.isBlank()) return
        val list = pcProfiles.load()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val profile = list[idx]
        if (profile.lastCwd == cwd && profile.lastSession == session) return
        list[idx] = profile.copy(lastCwd = cwd, lastSession = session)
        pcProfiles.save(list)
        _pcProfiles.value = list
    }

    // -----------------------------------------------------------------------
    // Pairing
    // -----------------------------------------------------------------------

    /**
     * Accepts a QR/deep-link payload (`omp2://host:port?token=…`) or a plain
     * address plus token. Returns a user-facing error, or null when saved.
     */
    fun pair(raw: String, tokenOverride: String? = null, name: String? = null): String? {
        val input = raw.trim()
        if (input.isBlank()) return copy("Mangler serveradresse", "Missing server address")
        var token = tokenOverride?.trim().orEmpty()
        if (token.isBlank()) token = SettingsStore.tokenFrom(input) ?: ""
        val base = SettingsStore.normalizeBaseUrl(input)
        if (base.isBlank()) return copy("Mangler serveradresse", "Missing server address")
        if (token.isBlank()) return copy("Mangler tilgangstoken", "Missing access token")

        // Finn eller opprett en PC-profil for denne adressen (LAN/Tailscale)
        val existingProfiles = pcProfiles.load()
        val existing = existingProfiles.firstOrNull {
            SettingsStore.normalizeBaseUrl(it.baseUrl) == base ||
            it.lanUrl == base ||
            it.tailscaleUrl == base ||
            it.endpoints.any { ep -> SettingsStore.normalizeBaseUrl(ep) == base }
        }

        val profile = if (existing != null) {
            // Oppdater token hvis endret
            existing.copy(token = token)
        } else {
            // Opprett ny PC-profil
            PcProfile(
                name = name?.takeIf { it.isNotBlank() } ?: "PC ${existingProfiles.size + 1}",
                baseUrl = base,
                token = token,
                autoConnect = true
            )
        }

        savePcProfile(profile)
        switchPc(profile.id)
        // Hent MAC-adresse automatisk fra Bridge
        viewModelScope.launch {
            try {
                val interfaces = repository.bridge.networkInterfaces()
                val preferred = interfaces.firstOrNull { it.wolCapable } ?: interfaces.firstOrNull()
                if (preferred != null && preferred.mac.isNotBlank()) {
                    val updated = profile.copy(mac = preferred.mac)
                    savePcProfile(updated)
                }
            } catch (_: Exception) {
                // MAC-henting feilet; brukeren kan sette den manuelt
            }
        }
        return null
    }

    private fun copy(nb: String, en: String): String =
        if (settings.state.value.language == "en") en else nb

    fun disconnect() {
        // Keep the position for this PC before the in-memory state is dropped.
        savePositionTo(pcProfiles.activeId(), settings.state.value.lastCwd, settings.state.value.lastSession)
        repository.disconnect()
        settings.clearConnection()
        settings.rememberLast("", "")
    }

    /** Activity ON_START: rebuild stream + resync state/messages. */
    fun onAppForeground() {
        repository.onAppForeground()
    }

    /** Activity ON_STOP: park the stream only when no OMP task is streaming. */
    fun onAppBackground() {
        repository.onAppBackground()
    }

    fun testConnection() {
        viewModelScope.launch {
            val result = repository.testConnection()
            _toast.value = result
        }
    }

    fun diagnostics(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.diagnostics()
            onResult(
                if (json == null) {
                    copy("Kunne ikke hente logg fra bridgen.", "Could not load the bridge log.")
                } else {
                    buildString {
                        appendLine("bridge: ${json.get("version")?.asString ?: "?"}")
                        appendLine("node: ${json.get("node")?.asString ?: "?"}")
                        appendLine("platform: ${json.get("platform")?.asString ?: "?"}")
                        appendLine("approval: ${json.get("approvalMode")?.asString ?: "?"}")
                        appendLine("sessions: ${json.get("sessionsRoot")?.asString ?: "?"}")
                        appendLine()
                        json.getAsJsonArray("log")?.forEach { line ->
                            appendLine(line.asString)
                        }
                    }
                }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Attachments & speech
    // -----------------------------------------------------------------------

    fun addAttachment(attachment: Attachment): String? {
        val current = _attachments.value
        if (current.size >= 6) return "Maks 6 vedlegg"
        if (current.any { it.name == attachment.name && it.base64.length == attachment.base64.length }) {
            return null
        }
        _attachments.value = current + attachment
        return null
    }

    fun removeAttachment(index: Int) {
        _attachments.value = _attachments.value.filterIndexed { i, _ -> i != index }
    }

    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    fun setSpeech(text: String) {
        _speech.value = text
    }

    fun consumeSpeech() {
        _speech.value = null
    }

    fun toast(text: String) {
        _toast.value = text
    }

    fun clearError() {
        repository.clearError()
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun reloadModels() {
        _modelsLoading.value = true
        repository.loadModels()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200)
            _modelsLoading.value = false
        }
    }

    fun loadVoiceEngines() {
        repository.loadVoiceEngines()
    }

    fun setApprovalMode(mode: String) {
        repository.setApprovalMode(mode)
    }

    fun setSttEngine(engineId: String) {
        repository.setSttEngine(engineId)
    }

    fun setTtsEngine(engineId: String) {
        repository.setTtsEngine(engineId)
    }

    fun setTtsVoice(engineId: String, voiceId: String) {
        repository.setTtsVoice(engineId, voiceId)
    }

    fun installVoiceEngine(kind: String, engineId: String) {
        repository.installVoiceEngine(kind, engineId)
    }

    fun clearTerminal() {
        repository.clearTerminal()
    }

    // -----------------------------------------------------------------------
    // Voice conversation
    // -----------------------------------------------------------------------

    fun startVoiceCall() {
        viewModelScope.launch {
            // A voice turn needs an active OMP session; create one on the fly
            // instead of failing with "open a conversation first".
            if (repository.ensureSession()) {
                voice.start(settings.state.value.voiceHandsFree)
            } else {
                _toast.value = copy(
                    "Velg en prosjektmappe først",
                    "Choose a project folder first"
                )
            }
        }
    }

    fun stopVoiceCall() {
        voice.stop()
    }

    fun setVoiceHandsFree(enabled: Boolean) {
        voice.setHandsFree(enabled)
    }

    fun setVoiceMuted(muted: Boolean) {
        voice.setMuted(muted)
    }

    /** Avbryt pågående OMP-runde uten å avslutte samtalen. */
    fun interruptVoiceTurn() {
        voice.interruptTurn()
    }

    // -----------------------------------------------------------------------
    // Multi-PC
    // -----------------------------------------------------------------------

    fun refreshPcProfiles() {
        _pcProfiles.value = pcProfiles.load()
        _activePcId.value = pcProfiles.activeId()
    }

    fun activePc(): PcProfile? = _pcProfiles.value.firstOrNull { it.id == _activePcId.value }

    private fun recordModelUsage(key: String) {
        if (key.isBlank()) return
        val id = pcProfiles.activeId()
        if (id.isBlank()) return
        val list = pcProfiles.load().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val profile = list[idx]
        val counts = profile.modelUsageCounts.toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        val updated = profile.copy(modelUsageCounts = counts)
        list[idx] = updated
        pcProfiles.save(list)
        _pcProfiles.value = list
        repository.resortModels()
    }

    fun isFavorite(sessionFile: String): Boolean = _favoriteSessions.value.contains(sessionFile)

    fun toggleFavorite(sessionFile: String) {
        if (sessionFile.isBlank()) return
        val id = pcProfiles.activeId()
        if (id.isBlank()) return
        val next = if (_favoriteSessions.value.contains(sessionFile)) {
            _favoriteSessions.value - sessionFile
        } else {
            _favoriteSessions.value + sessionFile
        }
        _favoriteSessions.value = next
        val list = pcProfiles.load()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(favoriteSessions = next.toList())
            pcProfiles.save(list)
            _pcProfiles.value = list
        }
    }

    private fun refreshFavorites() {
        _favoriteSessions.value = activePc()?.favoriteSessions?.toSet() ?: emptySet()
    }

    /**
     * En alternativ adresse svarte. Gjør den til primær (raskere kall neste
     * gang) og behold de andre som reserveløsninger.
     */
    fun promoteEndpoint(url: String, pcId: String = _activePcId.value) {
        val normalized = com.omp.chat.data.SettingsStore.normalizeBaseUrl(url)
        if (normalized.isBlank()) return
        if (pcId.isBlank()) return
        val list = pcProfiles.load()
        val idx = list.indexOfFirst { it.id == pcId }
        if (idx < 0) return
        val profile = list[idx]
        // Only promote an address that belongs to this PC. A late callback from
        // the previous PC must not rewrite the new PC's primary address.
        if (profile.candidateUrls().none { it == normalized }) return
        if (pcId == _activePcId.value) settings.updateBaseUrl(normalized)
        if (com.omp.chat.data.SettingsStore.normalizeBaseUrl(profile.baseUrl) == normalized) return
        val endpoints = (profile.endpoints + profile.baseUrl)
            .map { com.omp.chat.data.SettingsStore.normalizeBaseUrl(it) }
            .filter { it.isNotBlank() && it != normalized }
            .distinct()
        list[idx] = profile.copy(baseUrl = normalized, endpoints = endpoints)
        pcProfiles.save(list)
        _pcProfiles.value = list
        android.util.Log.d("OmpChat", "pc: ${profile.name} bruker nå ${com.omp.chat.data.EndpointResolver.hostLabel(normalized)}")
    }

    /** Lærer nye adresser fra `/api/ping` -> `hosts` (LAN, Tailscale, VPN). */
    fun mergePcEndpoints(id: String, hosts: List<String>) {
        if (id.isBlank() || hosts.isEmpty()) return
        val list = pcProfiles.load()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val merged = com.omp.chat.data.EndpointResolver.mergeHosts(list[idx], hosts)
        if (merged == list[idx]) return
        list[idx] = merged
        pcProfiles.save(list)
        _pcProfiles.value = list
        android.util.Log.d("OmpChat", "pc: lærte ${merged.endpoints.size} ekstra adresse(r)")
    }

    fun savePcProfile(profile: PcProfile) {
        val list = pcProfiles.load().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        pcProfiles.save(list)
        if (pcProfiles.activeId().isBlank()) pcProfiles.setActiveId(profile.id)
        refreshPcProfiles()
    }

    fun deletePcProfile(id: String) {
        val list = pcProfiles.load().filterNot { it.id == id }
        pcProfiles.save(list)
        if (pcProfiles.activeId() == id) {
            pcProfiles.setActiveId(list.firstOrNull()?.id ?: "")
        }
        refreshPcProfiles()
    }

    fun disconnectPc(id: String = pcProfiles.activeId()) {
        if (id.isBlank()) return
        if (id == pcProfiles.activeId()) {
            // Lagre gjeldende sesjon/cwd til den aktive profilen før frakobling.
            savePositionTo(id, settings.state.value.lastCwd, settings.state.value.lastSession)
        }
        pcProfiles.setActiveId("")
        repository.disconnect()
        settings.clearConnection()
        settings.rememberLast("", "")
        _activePcId.value = ""
        refreshPcProfiles()
        _favoriteSessions.value = emptySet()
        _pcStatus.value = _pcStatus.value + (id to "offline")
    }

    fun switchPc(id: String) {
        val profile = pcProfiles.load().firstOrNull { it.id == id } ?: return
        // Lagre gjeldende sesjon/cwd til forrige profil.
        val currentId = pcProfiles.activeId()
        if (currentId.isNotBlank() && currentId != id) {
            savePositionTo(currentId, settings.state.value.lastCwd, settings.state.value.lastSession)
        }
        // Stop everything from the old PC *before* the active id changes, so
        // no late response can be attributed to the new profile.
        repository.disconnect()
        pcProfiles.setActiveId(id)
        settings.saveConnection(profile.baseUrl, profile.token)
        repository.fallbackModelKey = profile.fallbackModel
        // Always replace the global position. A blank value is meaningful:
        // the new PC must not resume the previous PC's conversation.
        settings.rememberLast(profile.lastCwd, profile.lastSession)
        _activePcId.value = id
        refreshPcProfiles()
        refreshFavorites()
        if (profile.autoConnect) repository.connect()
        // Gotify config is best-effort and pushed separately via Settings screen.
        // Do NOT call it here to avoid "Gotify-konfig feilet" toasts during pairing.
    }

    fun wakePc(profile: PcProfile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = if (profile.useDockerWol && profile.dockerWolUrl.isNotBlank() || settings.state.value.wolServiceUrl.isNotBlank()) {
                wakeViaDocker(profile)
            } else {
                WolSender.send(profile.mac, profile.wolBroadcast, profile.wolPort)
            }
            _pcStatus.value = _pcStatus.value + (profile.id to if (ok) "starting" else "offline")
            onResult(ok)
            if (ok) {
                _pcStatus.value = _pcStatus.value + (profile.id to "wol_sent")
                // Prøv automatisk tilkobling når bridgen er klar.
                viewModelScope.launch {
                    kotlinx.coroutines.delay(5000)
                    var attempts = 0
                    while (attempts < 24) {
                        attempts++
                        // 1) Sjekk om PC-en svarer på TCP (PC tilgjengelig)
                        val tcpOk = withContext(Dispatchers.IO) {
                            runCatching {
                                val sock = java.net.Socket()
                                sock.connect(java.net.InetSocketAddress(profile.baseUrl.substringAfter("://").substringBefore(":").ifBlank { profile.baseUrl }, 7777), 2000)
                                sock.close()
                                true
                            }.getOrDefault(false)
                        }
                        if (!tcpOk) {
                            _pcStatus.value = _pcStatus.value + (profile.id to "wol_sent")
                            kotlinx.coroutines.delay(5000)
                            continue
                        }
                        _pcStatus.value = _pcStatus.value + (profile.id to "pc_available")
                        // 2) Verifiser faktisk bridge-tilkobling (ikke bare åpen port)
                        val result = repository.testConnection()
                        if (result != null && result.contains("OK", true)) {
                            _pcStatus.value = _pcStatus.value + (profile.id to "connected")
                            if (pcProfiles.activeId() == profile.id) repository.connect()
                            return@launch
                        }
                        kotlinx.coroutines.delay(5000)
                    }
                    _pcStatus.value = _pcStatus.value + (profile.id to "offline")
                }
            }
        }
    }

    private suspend fun wakeViaDocker(profile: PcProfile): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val serviceUrl = profile.dockerWolUrl.ifBlank { settings.state.value.wolServiceUrl }
            val serviceToken = profile.dockerWolToken.ifBlank { settings.state.value.wolServiceToken }
            if (serviceUrl.isBlank()) return@withContext false
            val base = serviceUrl.trimEnd('/')
            val id = profile.id
            // Prøv wake først.
            var conn = java.net.URL("$base/pcs/$id/wake").openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (serviceToken.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + serviceToken)
            }
            var code = conn.responseCode
            conn.disconnect()
            if (code == 404) {
                // PC-en er ikke registrert i tjenesten. Registrer den automatisk
                // med profilen vi allerede har, slik at brukeren slipper manuell ID.
                val host = profile.baseUrl.substringAfter("://").substringBefore(":").ifBlank { profile.baseUrl }
                val body = org.json.JSONObject().apply {
                    put("id", id)
                    put("name", profile.name.ifBlank { "PC" })
                    put("mac", profile.mac)
                    put("ip", host)
                    put("host", host)
                    put("port", 7777)
                    put("broadcast", profile.wolBroadcast.ifBlank { "255.255.255.255" })
                    put("wolPort", profile.wolPort)
                }
                conn = java.net.URL("$base/pcs").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/json")
                if (serviceToken.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer " + serviceToken)
                }
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode // consume
                conn.disconnect()
                // Prøv wake på nytt.
                conn = java.net.URL("$base/pcs/$id/wake").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (serviceToken.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer " + serviceToken)
                }
                code = conn.responseCode
                conn.disconnect()
            }
            code in 200..299
        } catch (e: Throwable) {
            false
        }
    }

    fun powerOffPc(profile: PcProfile, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = profile.baseUrl.trimEnd('/') + "/api/poweroff"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Content-Type", "application/json")
                    if (profile.token.isNotBlank()) {
                        conn.setRequestProperty("Authorization", "Bearer " + profile.token)
                    }
                    conn.doOutput = true
                    conn.outputStream.use { it.write("""{"confirm":true}""".toByteArray()) }
                    val code = conn.responseCode
                    val body = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { "" }
                    conn.disconnect()
                    val ok = code in 200..299
                    ok to (if (ok) body.ifBlank { "Slår av PC-en." } else body.ifBlank { "Kunne ikke slå av PC-en" })
                } catch (e: Throwable) {
                    false to (e.message ?: "Kunne ikke slå av PC-en")
                }
            }
            onResult(result.first, result.second)
        }
    }

    fun updatePcStatus(id: String, status: String) {
        _pcStatus.value = _pcStatus.value + (id to status)
    }
    private val _terminalTabs = MutableStateFlow<List<TerminalTab>>(listOf(TerminalTab(title = "Terminal 1")))
    val terminalTabs: StateFlow<List<TerminalTab>> = _terminalTabs.asStateFlow()
    private val _activeTerminalTab = MutableStateFlow(0)
    val activeTerminalTab: StateFlow<Int> = _activeTerminalTab.asStateFlow()

    fun setTerminalTabs(tabs: List<TerminalTab>) { _terminalTabs.value = tabs }
    fun setActiveTerminalTab(idx: Int) { _activeTerminalTab.value = idx }
    fun testGotify(): Boolean = repository.testGotify()

    fun saveGotifyConfig(enabled: Boolean, server: String, token: String) {
        settings.setGotifyEnabled(enabled)
        settings.setGotifyServer(server)
        if (token.isNotBlank()) settings.setGotifyToken(token)
        val effectiveToken = token.ifBlank { settings.state.value.gotifyToken }
        val events = mapOf(
            "task_done" to true,
            "task_failed" to true,
            "approval" to true,
            "agent_waiting" to true,
            "build" to true,
            "omp_error" to true,
            "bridge_disconnect" to true
        )
        // distribute to all registered PCs (best-effort, async)
        viewModelScope.launch {
            val profiles = pcProfiles.load()
            for (p in profiles) {
                try {
                    val conn = java.net.URL(p.baseUrl.trimEnd('/') + "/api/gotify/config").openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("Authorization", "Bearer " + p.token)
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val body = org.json.JSONObject().apply {
                        put("enabled", enabled)
                        put("server", server)
                        if (effectiveToken.isNotBlank()) put("token", effectiveToken)
                        val ev = org.json.JSONObject()
                        events.forEach { (k, v) -> ev.put(k, v) }
                        put("events", ev)
                    }
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    // ignore response
                    conn.inputStream.close()
                } catch (e: Exception) {
                    // PC offline; will sync on next connect
                }
            }
            // also try active bridge via repository (fallback)
            repository.saveGotifyConfig(enabled, server, effectiveToken, events)
        }
    }

    override fun onCleared() {
        voice.stop()
        super.onCleared()
    }
}
