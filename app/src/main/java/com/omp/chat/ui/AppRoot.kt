package com.omp.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.omp.chat.data.RepoState
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.components.DeleteDialog
import com.omp.chat.ui.components.RenameDialog
import com.omp.chat.ui.screens.ChatScreen
import com.omp.chat.ui.screens.ChatsScreen
import com.omp.chat.ui.screens.PairingScreen
import com.omp.chat.ui.screens.SettingsScreen
import com.omp.chat.ui.screens.VoiceCallScreen
import com.omp.chat.ui.theme.OmpTheme
import com.omp.chat.util.Notifier

enum class Screen { Chats, Chat, Settings, Voice, Files, Pcs, Tasks, Terminal, Git, Gotify, ProjectHistory, Cleanup, Archived, Trash }

@Composable
fun AppRoot(
    viewModel: AppViewModel,
    speechText: String?,
    onSpeechConsumed: () -> Unit,
    onScan: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit,
    onVoicePermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val voiceCall by viewModel.voiceState.collectAsStateWithLifecycle()
    val strings = remember(settings.language) { Strings(settings.language) }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    var skipPairing by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var projectHistoryPath by remember { mutableStateOf("") }
    var trashItems by remember { mutableStateOf(emptyList<com.omp.chat.data.TrashItem>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.paired) {
        if (settings.paired) skipPairing = false
    }

    val openVoiceCall: () -> Unit = {
        onVoicePermission { granted ->
            if (granted) {
                screen = Screen.Voice
                viewModel.startVoiceCall()
            } else {
                viewModel.toast(strings.talesamtaleMikrofon)
            }
        }
    }

    OmpTheme(
        theme = settings.theme,
        dynamicColor = settings.dynamicColor,
        fontScale = settings.fontScale
    ) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    if (!settings.paired && !skipPairing) {
                        PairingScreen(
                            connecting = state.connecting,
                            error = state.error,
                            onScan = onScan,
                            onPair = { url, token -> viewModel.pair(url, token) },
                            onClearError = { viewModel.clearError() },
                            onToast = { viewModel.toast(it) },
                            onGoToMain = { skipPairing = true; screen = Screen.Chats }
                        )
                    } else {
                        when (screen) {
                            Screen.Chats -> ChatsScreen(
                                state = state,
                                onOpenSession = { session ->
                                    viewModel.repository.openSession(session)
                                    screen = Screen.Chat
                                },
                                onNewChat = { cwd ->
                                    viewModel.repository.newChat(cwd)
                                    screen = Screen.Chat
                                },
                                onRename = { renameTarget = it },
                                onArchive = { session, archived ->
                                    viewModel.repository.archive(session.file, archived)
                                },
                                onDelete = { deleteTarget = it },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenFiles = { screen = Screen.Files },
                                onOpenPcs = { screen = Screen.Pcs },
                                onOpenTasks = { screen = Screen.Tasks },
                                onOpenTerminal = { screen = Screen.Terminal },
                                onOpenGit = { screen = Screen.Git },
                                onRefresh = {
                                    viewModel.repository.loadSessions()
                                    viewModel.repository.loadProjects()
                                    viewModel.repository.connect()
                                },
                                onListFolders = { path ->
                                    runCatching { viewModel.repository.bridge.folders(path) }.getOrNull()
                                }
                            )

                            Screen.Files -> com.omp.chat.ui.screens.FileManagerScreen(
                                repo = viewModel.files,
                                onBack = { screen = Screen.Chat }
                            )

                            Screen.Pcs -> com.omp.chat.ui.screens.PcManagerScreen(
                                viewModel = viewModel,
                                onBack = { screen = Screen.Chat },
                                onScan = { onScan() }
                            )

                            Screen.Tasks -> com.omp.chat.ui.screens.TasksScreen(
                                viewModel = viewModel,
                                onBack = { screen = Screen.Chat },
                                onOpenSession = { session ->
                                    viewModel.repository.openSessionByFile(session)
                                    screen = Screen.Chat
                                }
                            )

                            Screen.Terminal -> com.omp.chat.ui.screens.TerminalScreen(
                                viewModel = viewModel,
                                onBack = { screen = Screen.Chat }
                            )

                            Screen.Git -> com.omp.chat.ui.screens.GitScreen(
                                viewModel = viewModel,
                                onBack = { screen = Screen.Chat }
                            )

                            Screen.Gotify -> com.omp.chat.ui.screens.GotifyScreen(
                                viewModel = viewModel,
                                onBack = { screen = Screen.Settings }
                            )

                            Screen.Chat -> ChatScreen(
                                state = state,
                                attachments = attachments,
                                onAttachmentsChange = { list ->
                                    viewModel.clearAttachments()
                                    list.forEach { viewModel.addAttachment(it) }
                                },
                                showThinking = settings.showThinking || settings.activityDetailLevel == 1,
                                speechText = speechText,
                                onSpeechConsumed = onSpeechConsumed,
                                onBack = { screen = Screen.Chats },
                                onSend = { text, images, files ->
                                    viewModel.repository.send(text, images, files)
                                },
                                onQueue = { text, images, files ->
                                    viewModel.repository.send(text, images, files, "followUp")
                                },
                                onStop = { viewModel.repository.stop() },
                                onRegenerate = { id -> viewModel.repository.regenerate(id) },
                                onEditResend = { id, text -> viewModel.repository.editAndResend(id, text) },
                                onAnswer = { request, value, confirmed, cancelled ->
                                    viewModel.repository.answer(request, value, confirmed, cancelled)
                                },
                                onAnswers = { request, answers ->
                                    viewModel.repository.answer(request, answers = answers)
                                },
                                onModel = { model -> viewModel.repository.setModel(model) },
                                onRename = { name ->
                                    viewModel.repository.rename(state.sessionFile, name)
                                },
                                onArchive = {
                                    if (state.sessionFile.isNotBlank()) {
                                        viewModel.repository.archive(state.sessionFile, true)
                                    }
                                },
                                onDelete = {
                                    if (state.sessionFile.isNotBlank()) {
                                        viewModel.repository.delete(state.sessionFile)
                                    }
                                },
                                onNewChat = {
                                    viewModel.repository.newChat(state.cwd)
                                },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenFiles = { screen = Screen.Files },
                                onOpenPcs = { screen = Screen.Pcs },
                                onOpenTasks = { screen = Screen.Tasks },
                                onOpenTerminal = { screen = Screen.Terminal },
                                onOpenGit = { screen = Screen.Git },
                                resourceMonitorEnabled = settings.resourceMonitorEnabled,
                                onLoadResources = { viewModel.repository.loadResources() },
                                exportChatEnabled = settings.resourceMonitorEnabled,
                                onExportChat = { count ->
                                    viewModel.repository.exportLastNChats(count)
                                },
                                pinMessagesEnabled = settings.pinMessagesEnabled,
                                onTogglePinMessage = { messageId ->
                                    viewModel.repository.togglePinMessage(messageId)
                                },
                                onPinnedMessageClicked = { messageId ->
                                    // Scrolling håndteres internt i ChatScreen
                                },
                                onOpenActiveChat = {
                                    screen = Screen.Chat
                                    // The bridge is the only source that can prove
                                    // which session its runtime currently has open.
                                    viewModel.repository.openActiveBridgeSession()
                                },
                                onOpenCleanup = { screen = Screen.Cleanup },
                                pcName = viewModel.activePc()?.name ?: "PC",
                                loadActiveBridge = { runCatching { viewModel.repository.bridge.activeSession() }.getOrNull() },
                                animatedActive = settings.animatedActiveIndicator,
                                onOpenProjectHistory = { path ->
                                    projectHistoryPath = path
                                    screen = Screen.ProjectHistory
                                },
                                onReconnect = { viewModel.repository.connect() },
                                onReloadModels = { viewModel.reloadModels() },
                                onClearTerminal = { viewModel.clearTerminal() },
                                onTerminal = { command -> viewModel.repository.runTerminal(command) },
                                onPickPhoto = onPickPhoto,
                                onPickCamera = onPickCamera,
                                onPickFile = onPickFile,
                                onVoice = onVoice,
                                onVoiceCall = openVoiceCall,
                                onToast = { viewModel.toast(it) },
                                onOpenSession = { session ->
                                    viewModel.repository.openSession(session)
                                     screen = Screen.Chat
                                },
                                onRenameSession = { renameTarget = it },
                                onArchiveSession = { session, archived ->
                                    viewModel.repository.archive(session.file, archived)
                                },
                                onDeleteSession = { deleteTarget = it },
                                onSelectProject = { cwd ->
                                    viewModel.repository.newChat(cwd)
                                     screen = Screen.Chat
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                isFavorite = { viewModel.isFavorite(it) }
                            )

                            Screen.Settings -> SettingsScreen(
                                state = state,
                                settings = settings,
                                onBack = { screen = Screen.Chat },
                                onTheme = viewModel.settings::setTheme,
                                onDynamicColor = viewModel.settings::setDynamicColor,
                                onFontScale = viewModel.settings::setFontScale,
                                onLanguage = viewModel.settings::setLanguage,
                                onHaptics = viewModel.settings::setHaptics,
                                onNotifications = viewModel.settings::setNotifications,
                                onShowThinking = viewModel.settings::setShowThinking,
                                onSpeechLanguage = viewModel.settings::setSpeechLanguage,
                                onModel = { viewModel.repository.setModel(it) },
                                onTest = { viewModel.testConnection() },
                                onDisconnect = {
                                    viewModel.disconnect()
                                    screen = Screen.Chats
                                },
                                onScan = onScan,
                                onLoadLog = { onResult -> viewModel.diagnostics(onResult) },
                                onApprovalMode = { viewModel.setApprovalMode(it) },
                                onSttEngine = { viewModel.setSttEngine(it) },
                                onTtsEngine = { viewModel.setTtsEngine(it) },
                                onTtsVoice = { engineId, voiceId -> viewModel.setTtsVoice(engineId, voiceId) },
                                onInstallVoiceEngine = { kind, engineId -> viewModel.installVoiceEngine(kind, engineId) },
                                onVoiceSpeed = { viewModel.settings.setVoiceSpeed(it) },
                                onVoiceBargeIn = { viewModel.settings.setVoiceBargeIn(it) },
                                onVoiceAutoListen = { viewModel.settings.setVoiceAutoListen(it) },
                                onTestVoice = { viewModel.toast("Stemmetest ennÃƒÆ’Ã‚Â¥ ikke tilknyttet til TTS-motor") },
                                onLoadVoiceEngines = { viewModel.loadVoiceEngines() },
                                onGotifySave = { e, u, t -> viewModel.saveGotifyConfig(e, u, t) },
                                onGotifyTest = { viewModel.testGotify() },
                                onOpenGotify = { screen = Screen.Gotify },
                                onResourceMonitorEnabled = { viewModel.settings.setResourceMonitorEnabled(it) },
                                onPinMessagesEnabled = { viewModel.settings.setPinMessagesEnabled(it) },
                                onWolServiceUrl = { viewModel.settings.setWolServiceUrl(it) },
                                onActivityDetailLevel = { viewModel.settings.setActivityDetailLevel(it) }
                            )

                            Screen.ProjectHistory -> com.omp.chat.ui.screens.ProjectHistoryScreen(
                                projectPath = projectHistoryPath,
                                state = state,
                                onBack = { screen = Screen.Chat },
                                onOpenSession = { session ->
                                    viewModel.repository.openSession(session)
                                    screen = Screen.Chat
                                },
                                onLoadPage = { offset ->
                                    runCatching {
                                        viewModel.repository.bridge.sessions(cwd = projectHistoryPath, limit = 20, offset = offset)
                                    }.getOrNull() ?: emptyList()
                                }
                            )

                            Screen.Cleanup -> com.omp.chat.ui.screens.CleanupScreen(
                                state = state,
                                onBack = { screen = Screen.Chat },
                                onOpenSession = { session ->
                                    viewModel.repository.openSession(session)
                                    screen = Screen.Chat
                                },
                                onArchiveSelected = { files ->
                                    files.forEach { viewModel.repository.archive(it, true) }
                                    viewModel.repository.loadSessions()
                                    viewModel.repository.loadProjects()
                                },
                                onTrashSelected = { files ->
                                    files.forEach { viewModel.repository.delete(it) }
                                    viewModel.repository.loadSessions()
                                    viewModel.repository.loadProjects()
                                },
                                onOpenArchived = { screen = Screen.Archived },
                                onOpenTrash = {
                                    scope.launch {
                                        trashItems = runCatching { viewModel.repository.bridge.trash() }.getOrNull() ?: emptyList()
                                    }
                                    screen = Screen.Trash
                                }
                            )

                            Screen.Archived -> com.omp.chat.ui.screens.ArchivedScreen(
                                state = state,
                                onBack = { screen = Screen.Chat },
                                onOpenSession = { session ->
                                    viewModel.repository.openSession(session)
                                    screen = Screen.Chat
                                },
                                onRestore = { file ->
                                    viewModel.repository.archive(file, false)
                                    viewModel.repository.loadSessions()
                                    viewModel.repository.loadProjects()
                                }
                            )

                            Screen.Trash -> com.omp.chat.ui.screens.TrashScreen(
                                items = trashItems,
                                onBack = { screen = Screen.Chat },
                                onRestore = { file ->
                                    scope.launch {
                                        runCatching { viewModel.repository.bridge.restoreTrash(file) }
                                        viewModel.repository.loadSessions()
                                        viewModel.repository.loadProjects()
                                        trashItems = runCatching { viewModel.repository.bridge.trash() }.getOrNull() ?: emptyList()
                                    }
                                },
                                onOpen = { item ->
                                    // read-only view by file if still restorable? Just attempt open
                                }
                            )

                            Screen.Voice -> VoiceCallScreen(
                                state = state,
                                call = voiceCall,
                                onStart = { viewModel.startVoiceCall() },
                                onStop = { viewModel.stopVoiceCall() },
                                onHandsFree = { enabled -> viewModel.setVoiceHandsFree(enabled) },
                                onMute = { muted -> viewModel.setVoiceMuted(muted) },
                                onInterrupt = { viewModel.interruptVoiceTurn() },
                                onAnswer = { request, value, confirmed, cancelled ->
                                    viewModel.repository.answer(request, value, confirmed, cancelled)
                                },
                                onAnswers = { request, answers ->
                                    viewModel.repository.answer(request, answers = answers)
                                },
                                onBack = {
                                    viewModel.stopVoiceCall()
                                    screen = Screen.Chat
                                }
                            )
                        }

                    }

                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                    )
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.Chat) {
        if (screen == Screen.Voice) viewModel.stopVoiceCall()
        screen = Screen.Chat
    }

    // Transient messages from the repository or the view model.
    LaunchedEffect(state.notice?.id) {
        state.notice?.let {
            snackbar.showSnackbar(it.text)
            viewModel.repository.dismissNotice()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    // Local notification when a turn finishes while the app is backgrounded.
    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(state.streaming, state.messages.size) {
        if (wasStreaming && !state.streaming && settings.notifications) {
            val last = state.messages.lastOrNull { it.isAssistant }
            if (last != null && last.text.isNotBlank()) {
                Notifier.notify(
                    context = context,
                    title = state.title.ifBlank { strings.appName },
                    text = last.text.take(140)
                )
            }
        }
        wasStreaming = state.streaming
    }

    LaunchedEffect(screen) {
        if (screen != Screen.Chats) viewModel.consumeSpeech()
        if (screen != Screen.Voice) viewModel.stopVoiceCall()
    }

    if (renameTarget != null || deleteTarget != null) {
        // Dialogs open in their own window, so they need the app theme and copy
        // again; otherwise Material falls back to its light default palette.
        OmpTheme(
            theme = settings.theme,
            dynamicColor = settings.dynamicColor,
            fontScale = settings.fontScale
        ) {
            CompositionLocalProvider(LocalStrings provides strings) {
                val target = renameTarget
                if (target != null) {
                    RenameDialog(
                        initial = target.title,
                        onSave = { name ->
                            viewModel.repository.rename(target.file, name)
                            renameTarget = null
                        },
                        onDismiss = { renameTarget = null }
                    )
                }

                val deleting = deleteTarget
                if (deleting != null) {
                    DeleteDialog(
                        title = deleting.title,
                        onConfirm = {
                            viewModel.repository.delete(deleting.file)
                            deleteTarget = null
                        },
                        onDismiss = { deleteTarget = null }
                    )
                }
            }
        }
    }
}
