package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.google.gson.JsonParser
import com.omp.chat.data.EndpointResolver
import com.omp.chat.data.PcProfile
import com.omp.chat.data.SettingsStore
import com.omp.chat.data.TerminalTab
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.online
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.surface
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TERMINAL_URL = "https://appassets.androidplatform.net/assets/terminal/index.html"
private const val TERMINAL_HOST = "appassets.androidplatform.net"
private const val TAG = "OmpChat"

/** UI-tilstand per terminalfane. [status] skal alltid forklare hva som faktisk skjer. */
private data class TabUiState(
    val status: String = "Ikke tilkoblet",
    val connected: Boolean = false,
    val webViewReady: Boolean = false,
    val xtermReady: Boolean = false,
    val ptyReady: Boolean = false,
    val endpoint: String = ""
)

private fun quoteJson(s: String): String = com.google.gson.Gson().toJson(s)

/**
 * Én WebSocket/PTY per terminalfane.
 *
 * Adressen til PC-en løses på nytt hver gang vi kobler til (LAN først, deretter
 * Tailscale/VPN), og alle feil rapporteres videre til UI med faktisk årsak.
 */
class TerminalSessionManager(
    private val scope: CoroutineScope,
    private val onOutput: (String, String) -> Unit,
    private val onStatus: (
        tabId: String,
        connected: Boolean,
        ptyReady: Boolean,
        status: String,
        endpoint: String
    ) -> Unit,
    private val onSession: (String, String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val httpClient = client.newBuilder()
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val sockets = mutableMapOf<String, WebSocket>()
    private val jobs = mutableMapOf<String, Job>()
    private val pcs = mutableMapOf<String, PcProfile>()

    /**
     * Generasjon per fane. Når en fane kobles på nytt, øker generasjonen, og
     * lyttere fra den gamle WebSocket-en ignoreres. Uten dette kunne en sen
     * `onClosed` fra forrige socket kansellere den nye tilkoblingen (og dermed
     * miste buffer-replay).
     */
    private val generations = mutableMapOf<String, Int>()

    private fun nextGeneration(tabId: String): Int {
        val gen = (generations[tabId] ?: 0) + 1
        generations[tabId] = gen
        return gen
    }

    private fun isCurrent(tabId: String, gen: Int): Boolean = generations[tabId] == gen

    fun isConnected(tabId: String): Boolean = sockets[tabId] != null

    /**
     * Kobler en fane til [pc]. Finnes [sessionId], gjenopptas den (bridgen
     * sender hele bufferen på nytt), ellers opprettes en ny PTY.
     * Alle feil rapporteres med faktisk årsak.
     */
    fun connect(tabId: String, pc: PcProfile, sessionId: String? = null) {
        val gen = nextGeneration(tabId)
        jobs.remove(tabId)?.cancel()
        sockets.remove(tabId)?.close(1000, "kobler på nytt")
        pcs[tabId] = pc
        Log.d(TAG, "terminal: connect fane=$tabId økt=${sessionId ?: "ny"} gen=$gen")
        jobs[tabId] = scope.launch {
            try {
                if (!sessionId.isNullOrBlank() && attach(tabId, pc, sessionId, gen)) return@launch
                retryLoop(tabId, pc, gen)
            } catch (error: CancellationException) {
                Log.d(TAG, "terminal: jobb for fane=$tabId kansellert")
                throw error
            } catch (error: Exception) {
                Log.d(TAG, "terminal: jobb for fane=$tabId feilet: ${error.javaClass.simpleName}: ${error.message}")
                onStatus(tabId, false, false, "Feil: ${error.javaClass.simpleName}: ${error.message}", "")
            }
        }
    }

    /** Gjenopptar en eksisterende PTY (buffer replay). Returnerer false hvis den er borte. */
    private suspend fun attach(tabId: String, pc: PcProfile, sessionId: String, gen: Int): Boolean {
        Log.d(TAG, "terminal: attach starter fane=$tabId økt=$sessionId gen=$gen")
        val resolved = withContext(Dispatchers.IO) { EndpointResolver.firstReachable(pc) }
        Log.d(TAG, "terminal: attach adresse=${resolved?.url ?: "ingen"}")
        if (resolved == null || !isCurrent(tabId, gen)) return false
        onStatus(
            tabId, false, true,
            "Gjenopptar terminaløkt på ${EndpointResolver.hostLabel(resolved.url)}…",
            resolved.url
        )
        val wsUrl = wsUrlFor(resolved.url, sessionId)
        Log.d(TAG, "terminal: gjenopptar $sessionId via ${EndpointResolver.hostLabel(resolved.url)}")
        val result = openSocket(tabId, wsUrl, gen)
        if (!result.ok) {
            Log.d(TAG, "terminal: kunne ikke gjenoppta $sessionId: ${result.error}")
            return false
        }
        Log.d(TAG, "terminal: gjenopptok $sessionId (buffer replay)")
        onStatus(tabId, true, true, "Tilkoblet", resolved.url)
        return true
    }

    fun disconnect(tabId: String) {
        Log.d(TAG, "terminal: disconnect fane=$tabId")
        nextGeneration(tabId)
        jobs.remove(tabId)?.cancel()
        pcs.remove(tabId)
        sockets.remove(tabId)?.close(1000, "frakoblet")
        onStatus(tabId, false, false, "Frakoblet", "")
    }

    fun sendInput(tabId: String, text: String) {
        val socket = sockets[tabId] ?: return
        socket.send("""{"type":"input","data":${quoteJson(text)}}""")
    }

    fun sendResize(tabId: String, cols: Int, rows: Int) {
        val socket = sockets[tabId] ?: return
        if (cols > 0 && rows > 0) socket.send("""{"type":"resize","cols":$cols,"rows":$rows}""")
    }

    private suspend fun retryLoop(tabId: String, pc: PcProfile, gen: Int) {
        var attempt = 0
        while (currentCoroutineContext().isActive && isCurrent(tabId, gen)) {
            val opened = try {
                tryConnect(tabId, pc, gen)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onStatus(tabId, false, false, "Feil: ${error.javaClass.simpleName}: ${error.message}", "")
                false
            }
            if (opened || !isCurrent(tabId, gen)) return
            attempt++
            val waitMs = (1000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(30000L)
            onStatus(
                tabId, false, false,
                "Prøver igjen om ${waitMs / 1000}s (forsøk ${attempt + 1})…",
                ""
            )
            delay(waitMs)
        }
    }

    private suspend fun tryConnect(tabId: String, pc: PcProfile, gen: Int): Boolean {
        onStatus(tabId, false, false, "Finner adresse til ${pc.name}…", "")

        val resolved = withContext(Dispatchers.IO) { EndpointResolver.firstReachable(pc) }
        if (!isCurrent(tabId, gen)) return false
        if (resolved == null) {
            val probes = withContext(Dispatchers.IO) { EndpointResolver.probeAll(pc, 1200) }
            val detail = probes.joinToString(" · ") { probe ->
                "${EndpointResolver.hostLabel(probe.url)}: ${if (probe.ok) "OK" else probe.reason}"
            }
            onStatus(
                tabId, false, false,
                "Ingen adresse svarte ($detail). Sjekk at PC-en er på og at brannmuren tillater TCP 7777.",
                ""
            )
            return false
        }

        onStatus(
            tabId, false, false,
            "Oppretter terminal på ${EndpointResolver.hostLabel(resolved.url)}…",
            resolved.url
        )

        val created = withContext(Dispatchers.IO) { createSession(resolved.url, pc.token) }
        if (created.error != null) {
            onStatus(tabId, false, false, created.error, resolved.url)
            return false
        }

        onStatus(
            tabId, false, true,
            "PTY klar på ${EndpointResolver.hostLabel(resolved.url)}, kobler WebSocket…",
            resolved.url
        )
        onSession(tabId, created.id)

        val wsUrl = wsUrlFor(resolved.url, created.id)
        val result = openSocket(tabId, wsUrl, gen)
        if (!result.ok) {
            onStatus(
                tabId, false, true,
                "WebSocket feilet mot ${EndpointResolver.hostLabel(resolved.url)}: ${result.error}",
                resolved.url
            )
            return false
        }
        onStatus(tabId, true, true, "Tilkoblet", resolved.url)
        return true
    }

    private data class CreateResult(val id: String = "", val error: String? = null)

    private fun createSession(baseUrl: String, token: String): CreateResult {
        val body = """{"cwd":"","cols":80,"rows":24}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/terminal/create")
            .post(body)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return CreateResult(error = "Terminal-create avvist (HTTP ${response.code}): ${text.take(180)}")
                }
                val id = runCatching {
                    JsonParser.parseString(text).asJsonObject.get("id")?.asString.orEmpty()
                }.getOrDefault("")
                if (id.isBlank()) {
                    CreateResult(error = "Bridge svarte uten terminal-id: ${text.take(180)}")
                } else {
                    CreateResult(id = id)
                }
            }
        } catch (error: Exception) {
            CreateResult(error = "Terminal-create feilet: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private data class OpenResult(val ok: Boolean, val error: String = "")

    private suspend fun openSocket(tabId: String, wsUrl: String, gen: Int): OpenResult =
        suspendCancellableCoroutine { continuation ->
            var finished = false
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // En gammel socket (fra forrige generasjon) skal ikke røre
                    // tilstanden til den nye tilkoblingen.
                    if (!isCurrent(tabId, gen)) {
                        webSocket.close(1000, "utdatert")
                        return
                    }
                    sockets[tabId] = webSocket
                    if (!finished) {
                        finished = true
                        onStatus(tabId, true, true, "Tilkoblet", "")
                        continuation.resume(OpenResult(true))
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!isCurrent(tabId, gen)) return
                    onOutput(tabId, """{"type":"data","data":${quoteJson(text)}}""")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val detail = "kode $code" + if (reason.isNotBlank()) ": $reason" else ""
                    if (!isCurrent(tabId, gen)) {
                        Log.d(TAG, "terminal: ignorerer onClosed fra utdatert socket ($detail)")
                        return
                    }
                    sockets.remove(tabId)
                    onOutput(tabId, """{"type":"closed","reason":${quoteJson(detail)},"code":$code}""")
                    onStatus(tabId, false, true, "WebSocket lukket ($detail)", "")
                    if (!finished) {
                        finished = true
                        continuation.resume(OpenResult(false, "lukket ($detail)"))
                    } else {
                        scheduleReconnect(tabId, gen)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val detail = (if (response != null) "HTTP ${response.code}, " else "") +
                        (t.message ?: t.javaClass.simpleName)
                    if (!isCurrent(tabId, gen)) {
                        Log.d(TAG, "terminal: ignorerer onFailure fra utdatert socket ($detail)")
                        return
                    }
                    sockets.remove(tabId)
                    onOutput(tabId, """{"type":"failure","error":${quoteJson(detail)}}""")
                    onStatus(tabId, false, true, "WebSocket-feil: $detail", "")
                    if (!finished) {
                        finished = true
                        continuation.resume(OpenResult(false, detail))
                    } else {
                        scheduleReconnect(tabId, gen)
                    }
                }
            }

            val socket = client.newWebSocket(request, listener)
            continuation.invokeOnCancellation { socket.cancel() }
        }

    private fun scheduleReconnect(tabId: String, gen: Int) {
        if (!isCurrent(tabId, gen)) return
        val pc = pcs[tabId] ?: return
        jobs.remove(tabId)?.cancel()
        jobs[tabId] = scope.launch {
            delay(2000)
            if (isCurrent(tabId, gen)) retryLoop(tabId, pc, gen)
        }
    }
}

private fun wsUrlFor(baseUrl: String, sessionId: String): String =
    baseUrl
        .replaceFirst("http://", "ws://")
        .replaceFirst("https://", "wss://")
        .trimEnd('/') + "/ws/terminal?id=" + sessionId

@Composable
fun TerminalScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val profiles by viewModel.pcProfileList.collectAsState()
    val activePcId by viewModel.activePcId.collectAsState()
    val tabs by viewModel.terminalTabs.collectAsState()
    val activeTab by viewModel.activeTerminalTab.collectAsState()

    val settings by viewModel.settingsState.collectAsState()
    var fontSize by remember { mutableStateOf(settings.terminalFontSize) }

    val tabStates = remember { mutableStateMapOf<String, TabUiState>() }
    val webViews = remember { mutableMapOf<String, WebView>() }

    fun stateFor(tabId: String): TabUiState = tabStates[tabId] ?: TabUiState()
    fun updateState(tabId: String, block: (TabUiState) -> TabUiState) {
        mainHandler.post { tabStates[tabId] = block(tabStates[tabId] ?: TabUiState()) }
    }

    var showDiag by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf("") }

    val sessionManager = remember {
        TerminalSessionManager(
            scope = scope,
            onOutput = { tabId, json ->
                mainHandler.post {
                    webViews[tabId]?.evaluateJavascript(
                        "if (typeof window.terminalReceive === 'function') window.terminalReceive(${quoteJson(json)});" +
                            "else (window.__ompPending = window.__ompPending || []).push(${quoteJson(json)})",
                        null
                    )
                }
            },
            onStatus = { tabId, connected, ptyReady, status, endpoint ->
                mainHandler.post {
                    val current = tabStates[tabId] ?: TabUiState()
                    tabStates[tabId] = current.copy(
                        connected = connected,
                        ptyReady = ptyReady,
                        status = status,
                        endpoint = endpoint.ifBlank { current.endpoint }
                    )
                }
            },
            onSession = { tabId, sessionId ->
                mainHandler.post {
                    val list = viewModel.terminalTabs.value.toMutableList()
                    val idx = list.indexOfFirst { it.id == tabId }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(sessionId = sessionId)
                        viewModel.setTerminalTabs(list)
                    }
                }
            }
        )
    }

    val activeTabId = tabs.getOrNull(activeTab)?.id ?: ""
    val activeTabPcId = tabs.getOrNull(activeTab)?.pcId.orEmpty()
    var selectedPcId by remember { mutableStateOf(
        activeTabPcId.ifBlank { activePcId }.ifBlank { profiles.firstOrNull()?.id.orEmpty() }
    ) }

    val selectedPc: PcProfile? =
        profiles.firstOrNull { it.id == selectedPcId } ?: profiles.firstOrNull()

    fun pcForTab(tabId: String): PcProfile? {
        val id = tabs.firstOrNull { it.id == tabId }?.pcId.orEmpty()
        return profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
    }

    fun connectTab(tabId: String, fresh: Boolean = false) {
        val pc = pcForTab(tabId)
        if (pc == null) {
            updateState(tabId) { it.copy(status = "Ingen PC er registrert – legg til en PC først") }
            return
        }
        if (pc.token.isBlank()) {
            updateState(tabId) { it.copy(status = "Mangler token for ${pc.name} – rediger PC-en") }
            return
        }
        val tab = viewModel.terminalTabs.value.firstOrNull { it.id == tabId }
        val sessionId = if (fresh) null else tab?.sessionId
        if (fresh && tab?.sessionId != null) {
            val list = viewModel.terminalTabs.value.toMutableList()
            val idx = list.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(sessionId = null)
                viewModel.setTerminalTabs(list)
            }
        }
        updateState(tabId) { it.copy(status = "Kobler til ${pc.name}…", connected = false) }
        Log.d(TAG, "terminal: connectTab fane=$tabId fresh=$fresh økt=${sessionId ?: "ny"}")
        sessionManager.connect(tabId, pc, sessionId)
    }

    // Oppdater valgt PC når fanen byttes. Selve tilkoblingen starter når
    // WebView-en er ferdig lastet (onWebViewReady), slik at xterm.js finnes.
    LaunchedEffect(activeTabId, activeTabPcId) {
        if (activeTabId.isBlank()) return@LaunchedEffect
        selectedPcId = activeTabPcId.ifBlank { selectedPcId }
    }

    fun selectPcForActiveTab(pcId: String) {
        selectedPcId = pcId
        val list = viewModel.terminalTabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == activeTabId }
        if (idx >= 0 && list[idx].pcId != pcId) {
            list[idx] = list[idx].copy(pcId = pcId, sessionId = null, wsUrl = null)
            viewModel.setTerminalTabs(list)
        }
        sessionManager.disconnect(activeTabId)
        connectTab(activeTabId, fresh = true)
    }

    fun runDiagnostics() {
        val pc = selectedPc
        scope.launch {
            val sb = StringBuilder()
            sb.appendLine("=== Terminaldiagnostikk (xterm.js) ===")
            sb.appendLine("Fane: $activeTabId")
            sb.appendLine("PC: ${pc?.name ?: "ingen"}")
            sb.appendLine("Adresse: ${pc?.baseUrl ?: ""}")
            sb.appendLine("Token: ${if (pc?.token?.isNotBlank() == true) "satt (${pc.token.length} tegn)" else "MANGLER"}")
            val st = stateFor(activeTabId)
            sb.appendLine("Status: ${st.status}")
            sb.appendLine("WebView lastet: ${st.webViewReady}")
            sb.appendLine("xterm.js klar: ${st.xtermReady}")
            sb.appendLine("PTY klar: ${st.ptyReady}")
            sb.appendLine("WebSocket: ${st.connected}")
            sb.appendLine("Aktiv adresse: ${st.endpoint.ifBlank { "-" }}")
            sb.appendLine("Skriftstørrelse: ${fontSize}sp")
            sb.appendLine("WebView finnes: ${webViews[activeTabId] != null}")
            sb.appendLine()
            sb.appendLine("[Adressetest /api/ping]")
            if (pc != null) {
                val probes = withContext(Dispatchers.IO) { EndpointResolver.probeAll(pc, 2500) }
                probes.forEach { probe ->
                    sb.appendLine(
                        "  ${EndpointResolver.hostLabel(probe.url)}: " +
                            if (probe.ok) "OK (${probe.version.ifBlank { "ukjent versjon" }})" else probe.reason
                    )
                }
                if (probes.isEmpty()) sb.appendLine("  ingen adresser konfigurert")
            } else {
                sb.appendLine("  ingen PC valgt")
            }
            sb.appendLine()
            sb.appendLine("[WebView-konsoll]")
            sb.appendLine(lastConsoleLine.ifBlank { "  (ingen meldinger)" })
            sb.appendLine()
            sb.appendLine("[Terminalbuffer]")
            val bufferDump = withContext(Dispatchers.Main) {
                webViews[activeTabId]?.let { webView ->
                    evaluateJavascript(webView, "window.terminalDump ? window.terminalDump(120) : '(utilgjengelig)'")
                } ?: "(ingen WebView)"
            }
            sb.appendLine(bufferDump?.ifBlank { "  (tom)" } ?: "  (tom)")
            sb.appendLine()
            sb.appendLine("=== Slutt ===")
            withContext(Dispatchers.Main) {
                diagReport = sb.toString()
                showDiag = true
            }
        }
    }

    val statusState = stateFor(activeTabId)
    val isLive = statusState.connected
    val displayStatus = when {
        !statusState.webViewReady -> "Laster terminalmotor…"
        !statusState.xtermReady -> "Starter xterm.js…"
        !statusState.connected -> statusState.status
        else -> "Tilkoblet" + if (statusState.endpoint.isNotBlank()) {
            " · ${EndpointResolver.hostLabel(statusState.endpoint)}"
        } else ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VariantColors.background)
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VariantColors.textPrimary
                )
                Text(
                    displayStatus,
                    color = if (isLive) VariantColors.online else VariantColors.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
            }
            if (!isLive && statusState.webViewReady) {
                TextButton(onClick = { connectTab(activeTabId, fresh = true) }) {
                    Text("Prøv igjen", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = { runDiagnostics() }) {
                Text("Diag", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (profiles.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PC for denne fanen:",
                    color = VariantColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(6.dp))
                profiles.forEach { pc ->
                    val selected = pc.id == (selectedPcId)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) VariantColors.accent.copy(alpha = 0.2f) else VariantColors.surfaceElevated,
                        modifier = Modifier.padding(end = 4.dp).clickable { selectPcForActiveTab(pc.id) }
                    ) {
                        Text(
                            pc.name,
                            color = if (selected) VariantColors.accent else VariantColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, tab ->
                val tabPc = profiles.firstOrNull { it.id == tab.pcId }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (idx == activeTab) VariantColors.surface else VariantColors.surfaceElevated,
                    modifier = Modifier.padding(end = 4.dp).clickable { viewModel.setActiveTerminalTab(idx) }
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tab.title + if (tabPc != null && profiles.size > 1) " (${tabPc.name})" else "",
                            color = if (idx == activeTab) VariantColors.textPrimary else VariantColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val tabId = tab.id
                                sessionManager.disconnect(tabId)
                                tabStates.remove(tabId)
                                val updated = viewModel.terminalTabs.value.toMutableList()
                                    .also { list -> list.removeAll { it.id == tabId } }
                                if (updated.isEmpty()) {
                                    updated.add(TerminalTab(title = "Terminal 1", pcId = selectedPcId))
                                }
                                viewModel.setTerminalTabs(updated)
                                viewModel.setActiveTerminalTab(0.coerceAtMost(updated.size - 1))
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Lukk",
                                tint = VariantColors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = {
                    val updated = viewModel.terminalTabs.value.toMutableList()
                    updated.add(
                        TerminalTab(
                            title = "Terminal ${updated.size + 1}",
                            pcId = selectedPcId
                        )
                    )
                    viewModel.setTerminalTabs(updated)
                    viewModel.setActiveTerminalTab(updated.size - 1)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ny fane", tint = VariantColors.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                fontSize = (fontSize - 1).coerceIn(10, 28)
                viewModel.settings.setTerminalFontSize(fontSize)
            }) {
                Text("A-", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${fontSize}sp", color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = {
                fontSize = (fontSize + 1).coerceIn(10, 28)
                viewModel.settings.setTerminalFontSize(fontSize)
            }) {
                Text("A+", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        TerminalWebView(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
            context = context,
            tabId = activeTabId,
            fontSize = fontSize,
            onCreated = { tabId, webView -> webViews[tabId] = webView },
            onDisposed = { tabId, webView ->
                if (webViews[tabId] === webView) webViews.remove(tabId)
            },
            onWebViewReady = { tabId ->
                updateState(tabId) { it.copy(webViewReady = true) }
                // Ny WebView => xterm.js er tom. Gjenoppta økten slik at
                // bridgen spiller av bufferet på nytt.
                connectTab(tabId)
            },
            onJsReady = { tabId ->
                updateState(tabId) { it.copy(xtermReady = true) }
            },
            onJsInput = { tabId, text -> sessionManager.sendInput(tabId, text) },
            onJsResize = { tabId, cols, rows -> sessionManager.sendResize(tabId, cols, rows) },
            onJsError = { tabId, message ->
                updateState(tabId) { it.copy(status = "xterm.js-feil: $message") }
            },
            onPageError = { tabId, message ->
                updateState(tabId) { it.copy(status = "WebView-feil: $message", webViewReady = false) }
            }
        )

        Row(
            Modifier.fillMaxWidth().background(VariantColors.sidebar).horizontalScroll(rememberScrollState())
                .padding(4.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "ESC" to "\u001B",
                "TAB" to "\t",
                "CTRL+C" to "\u0003",
                "CTRL+D" to "\u0004",
                "CTRL+Z" to "\u001A",
                "⌫" to "\u0008",
                "←" to "\u001B[D",
                "→" to "\u001B[C",
                "↑" to "\u001B[A",
                "↓" to "\u001B[B",
                "/" to "/",
                "-" to "-",
                "|" to "|"
            ).forEach { (label, sequence) ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VariantColors.surfaceElevated,
                    modifier = Modifier.clickable {
                        if (!sessionManager.isConnected(activeTabId)) {
                            updateState(activeTabId) { it.copy(status = "Ikke tilkoblet – trykk «Prøv igjen»") }
                        } else {
                            sessionManager.sendInput(activeTabId, sequence)
                        }
                    }
                ) {
                    Text(
                        label,
                        color = VariantColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("Terminaldiagnostikk", color = VariantColors.textPrimary) },
            text = {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    diagReport.lines().forEach { line ->
                        Text(
                            line,
                            color = VariantColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiag = false }) { Text("OK", color = VariantColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDiag = false }) { Text("Lukk", color = VariantColors.textSecondary) }
            },
            containerColor = VariantColors.sidebar
        )
    }
}

/** Siste JS-konsollmelding, brukt i diagnostikken. */
private var lastConsoleLine: String = ""

/** Kjører JS i WebView og returnerer en JSON-dekodet streng. Må kalles på main-tråden. */
private suspend fun evaluateJavascript(webView: WebView, script: String): String? =
    suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { value ->
            val decoded = runCatching {
                if (value == null || value == "null") "" else JsonParser.parseString(value).asString
            }.getOrDefault(value.orEmpty())
            if (continuation.isActive) continuation.resume(decoded)
        }
    }

/** WebView som fanger backspace-tastetrykk fra Android-tastaturet og sender dem til xterm.js. */
private class TerminalWebViewImpl(context: Context) : WebView(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == 67 || event.keyCode == 112)) {
            Log.d(TAG, "WebView dispatchKeyEvent: backspace/del")
            evaluateJavascript(
                "if (typeof window.terminalBackspaceInput === 'function') window.terminalBackspaceInput();",
                null
            )
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** WebView som laster xterm.js fra appens egne assets via WebViewAssetLoader. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalWebView(
    modifier: Modifier,
    context: Context,
    tabId: String,
    fontSize: Int,
    onCreated: (String, WebView) -> Unit,
    onDisposed: (String, WebView) -> Unit,
    onWebViewReady: (String) -> Unit,
    onJsReady: (String) -> Unit,
    onJsInput: (String, String) -> Unit,
    onJsResize: (String, Int, Int) -> Unit,
    onJsError: (String, String) -> Unit,
    onPageError: (String, String) -> Unit
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val webView = remember(tabId) {
        // Asset-loader gjør at https://appassets.androidplatform.net/assets/...
        // serveres fra APK-ens assets – uten HTTP-server, CDN eller localhost.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        TerminalWebViewImpl(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF0A0F1E.toInt())
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                textZoom = 100
                useWideViewPort = false
                loadWithOverviewMode = false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    return assetLoader.shouldInterceptRequest(url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val host = request?.url?.host.orEmpty()
                    if (host != TERMINAL_HOST) {
                        Log.d(TAG, "terminal: blokkerte navigasjon til ${request?.url}")
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "terminal: WebView lastet $url")
                    view?.evaluateJavascript("typeof Terminal") { value ->
                        Log.d(TAG, "terminal: typeof Terminal = $value")
                        if (value == "undefined" || value == "\"undefined\"") {
                            mainHandler.post {
                                onJsError(tabId, "xterm.js ble ikke lastet (typeof Terminal = $value)")
                            }
                        }
                    }
                    view?.evaluateJavascript(
                        "if (typeof window.terminalSetFontSize === 'function') window.terminalSetFontSize($fontSize)",
                        null
                    )
                    view?.evaluateJavascript(
                        "if (typeof window.terminalReceive === 'function') " +
                            "window.terminalReceive(${quoteJson("""{"type":"ready"}""")})",
                        null
                    )
                    mainHandler.post { onWebViewReady(tabId) }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    val message = "${request?.url} -> ${error?.description} (kode ${error?.errorCode})"
                    Log.d(TAG, "terminal: WebView-feil $message")
                    // Feil på favicon o.l. skal ikke stoppe terminalen.
                    if (request?.isForMainFrame != false) {
                        mainHandler.post { onPageError(tabId, message) }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val message = "${request?.url} -> HTTP ${errorResponse?.statusCode}"
                    Log.d(TAG, "terminal: HTTP-feil $message")
                    if (request?.isForMainFrame != false) {
                        mainHandler.post { onPageError(tabId, message) }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    lastConsoleLine = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                    Log.d(TAG, "terminal JS: $lastConsoleLine")
                    return true
                }
            }

            addJavascriptInterface(object {
                @JavascriptInterface
                fun input(data: String) {
                    mainHandler.post { onJsInput(tabId, data) }
                }

                @JavascriptInterface
                fun resize(cols: Int, rows: Int) {
                    mainHandler.post { onJsResize(tabId, cols, rows) }
                }

                @JavascriptInterface
                fun ready() {
                    mainHandler.post { onJsReady(tabId) }
                }

                @JavascriptInterface
                fun error(message: String) {
                    mainHandler.post { onJsError(tabId, message) }
                }

                @JavascriptInterface
                fun log(message: String) {
                    lastConsoleLine = message
                    Log.d(TAG, "terminal JS: $message")
                }
            }, "android")

            loadUrl(TERMINAL_URL)
        }.also { created -> onCreated(tabId, created) }
    }

    DisposableEffect(tabId) {
        onDispose {
            onDisposed(tabId, webView)
            webView.removeJavascriptInterface("android")
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(fontSize) {
        webView.evaluateJavascript(
            "if (typeof window.terminalSetFontSize === 'function') window.terminalSetFontSize($fontSize)",
            null
        )
    }

    // Uten key(tabId) ville Compose gjenbrukt forrige fanes AndroidView-noder,
    // slik at factory ikke ble kalt på nytt og den nye fanen fikk feil WebView.
    key(tabId) {
        AndroidView(
            modifier = modifier,
            factory = { webView },
            update = { }
        )
    }
}
