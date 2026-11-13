package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.omp.chat.BuildConfig
import com.omp.chat.data.AppSettings
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.RepoState
import com.omp.chat.data.VoiceEngineList
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.ModelSheet
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.components.SectionLabel
import com.omp.chat.ui.components.StatusDot
import com.omp.chat.ui.components.shortModel
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import com.omp.chat.ui.theme.VariantColors.userBubble

@Composable
fun SettingsScreen(
    state: RepoState,
    settings: AppSettings,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    onLanguage: (String) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onShowThinking: (Boolean) -> Unit,
    onSpeechLanguage: (String) -> Unit,
    onModel: (ModelInfo) -> Unit,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    onLoadLog: (onResult: (String) -> Unit) -> Unit,
    onApprovalMode: (String) -> Unit = {},
    onSttEngine: (String) -> Unit = {},
    onTtsEngine: (String) -> Unit = {},
    onTtsVoice: (String, String) -> Unit = { _, _ -> },
    onInstallVoiceEngine: (String, String) -> Unit = { _, _ -> },
    onVoiceSpeed: (Float) -> Unit = {},
    onVoiceBargeIn: (Boolean) -> Unit = {},
    onVoiceAutoListen: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit = {},
    onLoadVoiceEngines: () -> Unit = {},
    onGotifySave: (Boolean, String, String) -> Unit = { _, _, _ -> },
    onGotifyTest: () -> Boolean = { false },
    onOpenGotify: () -> Unit = {},
    onResourceMonitorEnabled: (Boolean) -> Unit = {},
    onPinMessagesEnabled: (Boolean) -> Unit = {},
    onWolServiceUrl: (String) -> Unit = {},
    onActivityDetailLevel: (Int) -> Unit = {}
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var modelOpen by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var logOpen by remember { mutableStateOf(false) }
    var sttPickerOpen by remember { mutableStateOf(false) }
    var ttsPickerOpen by remember { mutableStateOf(false) }
    var showGotify by remember { mutableStateOf(false) }
    var gotifyEnabled by remember { mutableStateOf(settings.gotifyEnabled) }
    var gotifyServer by remember { mutableStateOf(settings.gotifyServer) }
    var gotifyToken by remember { mutableStateOf(settings.gotifyToken) }
    var gotifyShowToken by remember { mutableStateOf(false) }
    var gotifyStatus by remember { mutableStateOf("") }

    var updateStatus by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<com.omp.chat.data.UpdateInfo?>(null) }
    var showUpdate by remember { mutableStateOf(false) }

    // Vis stemmer fra bridge (oppdateres når brukeren laster dem inn)
    LaunchedEffect(state.voiceEngines) {
        // Når bridge har lastet motorene, vis åpen picker hvis brukeren har trykket
        // Velg-motor mens den lastet.
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = strings.chats, tint = VariantColors.textPrimary, modifier = Modifier.size(20.dp))
            }
            Text(
                strings.settings,
                style = MaterialTheme.typography.titleLarge,
                color = VariantColors.textPrimary
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ------------------------------------------------------ connection
            SectionLabel(strings.connection, Modifier.padding(top = 8.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online = state.connected, size = 9.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.connected) strings.connected else strings.disconnected,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface
                        )
                        Text(
                            text = settings.baseUrl.ifBlank { strings.unpairedHint },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onTest) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.testConnection, tint = scheme.primary)
                    }
                }
                if (state.connected) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MetaPill(strings.version, state.bridgeVersion.ifBlank { "—" })
                        MetaPill(strings.approvalMode, approvalModeLabel(settings.approvalMode))
                        MetaPill(strings.terminalAccess, if (state.terminalEnabled) strings.enabled else strings.disabled)
                    }
                    Spacer(Modifier.height(10.dp))
                    Column {
                        Text(
                            strings.approvalHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Segmented(
                            options = listOf(
                                "always-ask" to strings.approvalAlwaysAsk,
                                "write" to strings.approvalAskWhenNeeded,
                                "yolo" to strings.approvalAlways
                            ),
                            selected = legacyApprovalMode(settings.approvalMode),
                            onSelect = { mode ->
                                onApprovalMode(mode)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(strings.scanQr, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.disconnect, color = VariantColors.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ----------------------------------------------------------- WoL-tjeneste
            SectionLabel("Wake-on-LAN-tjeneste", Modifier.padding(top = 10.dp))
            SettingsCard {
                Text(
                    "Global adresse til WOL-tjenesten (brukes som fallback hvis PC-profilen ikke har egen adresse).",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = settings.wolServiceUrl,
                    onValueChange = onWolServiceUrl,
                    label = { Text("WOL-tjeneste URL") },
                    placeholder = { Text("http://192.168.50.222:8790") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ----------------------------------------------------------- model
            SectionLabel(strings.model, Modifier.padding(top = 10.dp))
            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { modelOpen = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(OmpIcons.Sparkle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            shortModel(state.chat.model?.name ?: settings.lastModelName)
                                .ifBlank { strings.selectModel },
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface
                        )
                        Text(
                            state.chat.model?.provider ?: settings.lastModelProvider,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Text(strings.selectModel, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }

            // ------------------------------------------------------- appearance
            SectionLabel(strings.appearance, Modifier.padding(top = 10.dp))
            SettingsCard {
                Text(strings.theme, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(
                        "system" to strings.themeSystem,
                        "dark" to strings.themeDark,
                        "light" to strings.themeLight
                    ),
                    selected = settings.theme,
                    onSelect = onTheme
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                SwitchRow(strings.dynamicColor, strings.dynamicColorHint, settings.dynamicColor, onDynamicColor)
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            strings.fontSize,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(settings.fontScale * 100).toInt()} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = settings.fontScale,
                        onValueChange = onFontScale,
                        valueRange = 0.85f..1.3f,
                        steps = 8
                    )
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Text(strings.language, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf("nb" to strings.norwegian, "en" to strings.english),
                    selected = settings.language,
                    onSelect = onLanguage
                )
            }

            // --------------------------------------------------------- behavior
            SectionLabel(strings.haptics, Modifier.padding(top = 10.dp))
            SettingsCard {
                SwitchRow(strings.haptics, "", settings.haptics, onHaptics)
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                SwitchRow(strings.notifications, strings.notificationsHint, settings.notifications, onNotifications)
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                // Gotify
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Gotify", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                        Text(
                            if (settings.gotifyEnabled) (settings.gotifyServer.ifBlank { "Aktiv" }) else "Deaktivert",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onOpenGotify() }) { Text("Konfigurer", color = VariantColors.accent) }
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                // Ressursmonitor
                SwitchRow("PC-ressursmonitor", "Vis CPU, RAM og GPU i chatten", settings.resourceMonitorEnabled, onResourceMonitorEnabled)
                // Heft meldinger
                SwitchRow("Heft meldinger", "Heft viktige meldinger øverst i chatten", settings.pinMessagesEnabled, onPinMessagesEnabled)
                // Aktivitetsdetaljer
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Vis aktivitetsdetaljer", color = VariantColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (settings.activityDetailLevel == 0) "Minimalt (standard)" else "Utvidet",
                            color = VariantColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = settings.activityDetailLevel == 1,
                        onCheckedChange = { onActivityDetailLevel(if (it) 1 else 0) }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Oppdateringer", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                        Text(
                            updateInfo?.let { "Nyeste: ${it.androidVersion} (kode ${it.androidVersionCode})" }
                                ?: "Installert: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                val info = withContext(Dispatchers.IO) {
                                    com.omp.chat.data.UpdateChecker.fetch(ctx)
                                }
                                updateInfo = info
                                if (info == null) {
                                    updateStatus = "Kunne ikke hente oppdateringer"
                                } else if (info.error.isNotBlank()) {
                                    updateStatus = "Kunne ikke hente oppdateringer: ${info.error}"
                                } else if (info.available) {
                                    updateStatus = "Ny versjon ${info.androidVersion} tilgjengelig"
                                } else {
                                    updateStatus = "Du har nyeste versjon"
                                }
                                showUpdate = true
                            }
                        }) { Text("Se etter oppdateringer", color = VariantColors.accent) }
                        val currentInfo = updateInfo
                        if (currentInfo?.available == true && currentInfo.androidUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        updateStatus = "Laster ned oppdatering…"
                                        val file = withContext(Dispatchers.IO) {
                                            com.omp.chat.data.UpdateChecker.downloadApk(ctx, currentInfo!!.androidUrl)
                                        }
                                        if (file != null) {
                                            com.omp.chat.data.UpdateChecker.installApk(ctx, file)
                                        } else {
                                            updateStatus = "Nedlasting mislyktes"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Installer", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (updateStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            updateStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface
                        )
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.speechLanguage, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                        Text(
                            settings.speechLanguage,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniChoice("nb-NO", settings.speechLanguage == "nb-NO") { onSpeechLanguage("nb-NO") }
                        MiniChoice("en-US", settings.speechLanguage == "en-US") { onSpeechLanguage("en-US") }
                    }
                }
            }

            if (com.omp.chat.data.FeatureFlags.VoiceEnabled) {
                // --------------------------------------------------------- tale og lyd
                SectionLabel(strings.taleOgLyd, Modifier.padding(top = 10.dp))
                SettingsCard {
                    Column {
                        // STT-motor
                        Text(
                            strings.sttSeksjon,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            strings.sttBeskrivelse,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val sttEngineObj = state.voiceEngines?.sttEngines?.firstOrNull { it.id == settings.sttEngine }
                        val sttName = sttEngineObj?.name?.ifBlank { settings.sttEngine }
                            ?: settings.sttEngine.ifBlank { "Ikke valgt" }
                        val sttInstalled = sttEngineObj?.installed == true
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings.velgSttMotor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface
                                )
                                Text(
                                    "$sttName (${if (sttInstalled) strings.motorInstallert else strings.motorMangler})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sttInstalled) scheme.primary else scheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    sttPickerOpen = true
                                    if (state.voiceEngines == null) onLoadVoiceEngines()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.velgSttMotor, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))

                        // TTS-motor
                        Text(
                            strings.ttsSeksjon,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            strings.ttsBeskrivelse,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val ttsEngineObj = state.voiceEngines?.ttsEngines?.firstOrNull { it.id == settings.ttsEngine }
                        val ttsName = ttsEngineObj?.name?.ifBlank { settings.ttsEngine }
                            ?: settings.ttsEngine.ifBlank { "Ikke valgt" }
                        val ttsInstalled = ttsEngineObj?.installed == true
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings.velgTtsMotor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface
                                )
                                val voicesList = ttsEngineObj?.voices.orEmpty()
                                val activeVoice = state.voiceEngines?.ttsVoice?.takeIf { it.isNotBlank() }
                                    ?: ttsEngineObj?.voice?.takeIf { it.isNotBlank() }
                                val voiceLabel = if (voicesList.isEmpty()) {
                                    activeVoice
                                } else {
                                    activeVoice?.let { v -> voicesList.firstOrNull { ve -> ve.id == v }?.name ?: v }
                                }
                                val status = if (ttsInstalled) strings.motorInstallert else strings.motorMangler
                                Text(
                                    voiceLabel?.let { "$ttsName · $it ($status)" } ?: "$ttsName ($status)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ttsInstalled) scheme.primary else scheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    ttsPickerOpen = true
                                    if (state.voiceEngines == null) onLoadVoiceEngines()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.velgTtsMotor, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))

                        // Samtaleinnstillinger
                        Text(
                            strings.samtaleSeksjon,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(strings.automatiskLytning, strings.automatiskLytningBeskrivelse, settings.voiceAutoListen, onVoiceAutoListen)
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        SwitchRow(strings.avbrytAiTale, strings.avbrytAiTaleBeskrivelse, settings.voiceBargeIn, onVoiceBargeIn)
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    strings.talehastighet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(settings.voiceSpeed * 100).toInt()} %",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = settings.voiceSpeed,
                                onValueChange = onVoiceSpeed,
                                valueRange = 0.5f..2.0f,
                                steps = 15
                            )
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onTestVoice,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.testStemme, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onLoadVoiceEngines,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    strings.installert,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

            }

            // ------------------------------------------------------ diagnostics
            SectionLabel(strings.diagnostics, Modifier.padding(top = 10.dp))
            SettingsCard {
                Text(
                    strings.diagnosticsHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onLoadLog { text ->
                                log = text
                                logOpen = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.loadLogs, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(log))
                        },
                        enabled = log.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.copyLogs, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (logOpen && log.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = scheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        SelectionContainer {
                            Text(
                                log,
                                style = MonoStyle,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp)
                            )
                        }
                    }
                    TextButton(onClick = { logOpen = false }) {
                        Text(strings.showLess)
                    }
                }
            }

            // ------------------------------------------------------------- about
            SectionLabel(strings.about, Modifier.padding(top = 10.dp))
            SettingsCard {
                InfoRow(strings.version, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                InfoRow(strings.serverStatus, state.bridgeVersion.ifBlank { "—" })
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    strings.conversationDataHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (modelOpen) {
                ModelSheet(
                    models = state.models,
                    current = state.chat.model,
                    onSelect = onModel,
                    onDismiss = { modelOpen = false }
                )
            }
        }
    }
}

/** Gammelt app-vokab ("default"/"ask") mappes til OMPs reelle moduser for visning. */
private fun legacyApprovalMode(mode: String): String = when (mode) {
    "write" -> "write"
    "yolo" -> "yolo"
    "default" -> "write"
    "ask" -> "yolo"
    else -> "always-ask"
}

/** Visningsnavn ("Alltid spør"/"Spør ved …"/"Tillat alt") i stedet for rå modusverdi. */
private fun approvalModeLabel(mode: String): String = when (legacyApprovalMode(mode)) {
    "write" -> "Spør ved sletting/store endringer"
    "yolo" -> "Tillat alt"
    else -> "Alltid spør"
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = VariantColors.sidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VariantColors.divider, RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFFFFF),
                checkedTrackColor = VariantColors.accent
            )
        )
    }
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VariantColors.surfaceElevated)
            .border(1.dp, VariantColors.divider, RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) VariantColors.accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) VariantColors.accent else VariantColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MiniChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) VariantColors.accent.copy(alpha = 0.2f) else VariantColors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) VariantColors.accent else VariantColors.divider,
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) VariantColors.accent else VariantColors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MetaPill(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
