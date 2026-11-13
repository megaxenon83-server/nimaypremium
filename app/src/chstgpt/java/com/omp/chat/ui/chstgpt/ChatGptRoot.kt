package com.omp.chat.ui.chstgpt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
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
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.Strings
import com.omp.chat.ui.components.DeleteDialog
import com.omp.chat.ui.components.RenameDialog
import com.omp.chat.ui.screens.PairingScreen
import com.omp.chat.ui.screens.SettingsScreen
import com.omp.chat.ui.theme.OmpTheme
import com.omp.chat.util.Notifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GptScreen { Chat, Settings, Files, Pcs, Tasks, Terminal, Git, ProjectHistory, Cleanup, Archived, Trash }

@Composable
fun ChatGptRoot(
    viewModel: AppViewModel,
    speechText: String?,
    onSpeechConsumed: () -> Unit,
    onScan: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val pcProfiles by viewModel.pcProfileList.collectAsStateWithLifecycle()
    val activePcId by viewModel.activePcId.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSessions.collectAsStateWithLifecycle()

    val strings = remember(settings.language) { Strings(settings.language) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(GptScreen.Chat) }
    var skipPairing by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var projectHistoryPath by remember { mutableStateOf("") }
    var trashItems by remember { mutableStateOf(emptyList<com.omp.chat.data.TrashItem>()) }

    LaunchedEffect(settings.paired) {
        if (settings.paired) skipPairing = false
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    // Real working state: ask the bridge on open and then keep it fresh. The
    // cadence is faster while OMP is actually working.
    LaunchedEffect(screen, state.connected, state.streaming, state.sessionFile) {
        if (screen != GptScreen.Chat) return@LaunchedEffect
        while (true) {
            viewModel.repository.refreshLiveStatus()
            delay(if (state.streaming) 2500 else 5000)
        }
    }

    ChatGptTheme(theme = settings.theme, fontScale = settings.fontScale) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(
                Modifier.fillMaxSize(),
                color = LocalChatGptColors.current.background
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (!settings.paired && !skipPairing) {
                        GptPairingHost(
                            connecting = state.connecting,
                            error = state.error,
                            strings = strings,
                            settings = settings,
                            onScan = onScan,
                            onPair = { url, token -> viewModel.pair(url, token) },
                            onClearError = { viewModel.clearError() },
                            onToast = { viewModel.toast(it) },
                            onGoToMain = { skipPairing = true; screen = GptScreen.Chat }
                        )
                    } else {
                        when (screen) {
                            GptScreen.Chat -> ModalNavigationDrawer(
                                drawerState = drawerState,
                                gesturesEnabled = drawerState.isOpen,
                                drawerContent = {
                                    ChatGptDrawer(
                                        state = state,
                                        settings = settings,
                                        filesRepo = viewModel.files,
                                        pcProfiles = pcProfiles,
                                        activePcId = activePcId,
                                        favoriteIds = favorites,
                                        onClose = closeDrawer,
                                        onNewChat = { cwd ->
                                            viewModel.repository.newChat(cwd)
                                            screen = GptScreen.Chat
                                        },
                                        onCreateProject = { name, path -> viewModel.repository.createProject(name, path) },
                                        onHideProject = { id -> viewModel.repository.hideProject(id) },
                                        onRenameProject = { id, name -> viewModel.repository.renameProject(id, name) },
                                        onUnhideProject = { id -> viewModel.repository.unhideProject(id) },
                                        onDeleteProject = { id, physical -> viewModel.repository.deleteProject(id, physical) },
                                        onOpenSession = { session ->
                                            viewModel.repository.openSession(session)
                                            screen = GptScreen.Chat
                                        },
                                        onRename = { renameTarget = it },
                                        onArchive = { session, archived ->
                                            viewModel.repository.archive(session.file, archived)
                                        },
                                        onDelete = { deleteTarget = it },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onSwitchPc = { id -> viewModel.switchPc(id) },
                                        onOpenPcs = { screen = GptScreen.Pcs },
                                        onOpenSettings = { screen = GptScreen.Settings },
                                        onOpenFiles = { screen = GptScreen.Files },
                                        onOpenTasks = { screen = GptScreen.Tasks },
                                        onOpenTerminal = { screen = GptScreen.Terminal },
                                        onOpenGit = { screen = GptScreen.Git },
                                        onOpenCleanup = { screen = GptScreen.Cleanup },
                                        onOpenArchived = { screen = GptScreen.Archived }
                                    )
                                }
                            ) {
                                ChatGptChatScreen(
                                    state = state,
                                    attachments = attachments,
                                    showThinking = settings.showThinking || settings.activityDetailLevel == 1,
                                    pcName = viewModel.activePc()?.baseUrl?.let { url ->
                                        val host = url.substringAfter("//").substringBefore("/").substringBefore(":")
                                        if (host.isNotBlank()) host else viewModel.activePc()?.name
                                    } ?: viewModel.activePc()?.name ?: "PC",
                                    onAttachmentsChange = { list ->
                                        viewModel.clearAttachments()
                                        list.forEach { viewModel.addAttachment(it) }
                                    },
                                    onOpenDrawer = openDrawer,
                                    onSend = { text, images, files -> viewModel.repository.send(text, images, files) },
                                    onQueue = { text, images, files -> viewModel.repository.send(text, images, files, "followUp") },
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
                                    onNewChat = {
                                        viewModel.repository.newChat(state.cwd)
                                        screen = GptScreen.Chat
                                    },
                                    onOpenSettings = { screen = GptScreen.Settings },
                                    onOpenPcs = { screen = GptScreen.Pcs },
                                    onOpenActive = {
                                        screen = GptScreen.Chat
                                        viewModel.repository.openActiveBridgeSession()
                                    },
                                    onReloadModels = { viewModel.reloadModels() },
                                    onTogglePin = { id -> viewModel.repository.togglePinMessage(id) },
                                    onPickPhoto = onPickPhoto,
                                    onPickCamera = onPickCamera,
                                    onPickFile = onPickFile,
                                    onVoice = onVoice,
                                    onToast = { viewModel.toast(it) }
                                )
                            }

                            GptScreen.Files -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.FileManagerScreen(
                                    repo = viewModel.files,
                                    onBack = { screen = GptScreen.Chat }
                                )
                            }

                            GptScreen.Pcs -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.PcManagerScreen(
                                    viewModel = viewModel,
                                    onBack = { screen = GptScreen.Chat },
                                    onScan = { onScan() }
                                )
                            }

                            GptScreen.Tasks -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.TasksScreen(
                                    viewModel = viewModel,
                                    onBack = { screen = GptScreen.Chat },
                                    onOpenSession = { session ->
                                        viewModel.repository.openSessionByFile(session)
                                        screen = GptScreen.Chat
                                    }
                                )
                            }

                            GptScreen.Terminal -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.TerminalScreen(
                                    viewModel = viewModel,
                                    onBack = { screen = GptScreen.Chat }
                                )
                            }

                            GptScreen.Git -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.GitScreen(
                                    viewModel = viewModel,
                                    onBack = { screen = GptScreen.Chat }
                                )
                            }

                            GptScreen.ProjectHistory -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.ProjectHistoryScreen(
                                    projectPath = projectHistoryPath,
                                    state = state,
                                    onBack = { screen = GptScreen.Chat },
                                    onOpenSession = { session ->
                                        viewModel.repository.openSession(session)
                                        screen = GptScreen.Chat
                                    },
                                    onLoadPage = { offset ->
                                        runCatching {
                                            viewModel.repository.bridge.sessions(
                                                cwd = projectHistoryPath,
                                                limit = 20,
                                                offset = offset
                                            )
                                        }.getOrNull() ?: emptyList()
                                    }
                                )
                            }

                            GptScreen.Cleanup -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.CleanupScreen(
                                    state = state,
                                    onBack = { screen = GptScreen.Chat },
                                    onOpenSession = { session ->
                                        viewModel.repository.openSession(session)
                                        screen = GptScreen.Chat
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
                                    onOpenArchived = { screen = GptScreen.Archived },
                                    onOpenTrash = {
                                        scope.launch {
                                            trashItems = runCatching { viewModel.repository.bridge.trash() }.getOrNull() ?: emptyList()
                                        }
                                        screen = GptScreen.Trash
                                    }
                                )
                            }

                            GptScreen.Archived -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.ArchivedScreen(
                                    state = state,
                                    onBack = { screen = GptScreen.Chat },
                                    onOpenSession = { session ->
                                        viewModel.repository.openSession(session)
                                        screen = GptScreen.Chat
                                    },
                                    onRestore = { file ->
                                        viewModel.repository.archive(file, false)
                                        viewModel.repository.loadSessions()
                                        viewModel.repository.loadProjects()
                                    }
                                )
                            }

                            GptScreen.Trash -> LegacyHost(settings) {
                                com.omp.chat.ui.screens.TrashScreen(
                                    items = trashItems,
                                    onBack = { screen = GptScreen.Chat },
                                    onRestore = { file ->
                                        scope.launch {
                                            runCatching { viewModel.repository.bridge.restoreTrash(file) }
                                            viewModel.repository.loadSessions()
                                            viewModel.repository.loadProjects()
                                            trashItems = runCatching { viewModel.repository.bridge.trash() }.getOrNull() ?: emptyList()
                                        }
                                    },
                                    onOpen = { }
                                )
                            }

                            GptScreen.Settings -> LegacyHost(settings) {
                                SettingsScreen(
                                    state = state,
                                    settings = settings,
                                    onBack = { screen = GptScreen.Chat },
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
                                        screen = GptScreen.Chat
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
                                    onTestVoice = { viewModel.toast("Stemmetest er ikke tilknyttet en TTS-motor") },
                                    onLoadVoiceEngines = { viewModel.loadVoiceEngines() },
                                    onGotifySave = { e, u, t -> viewModel.saveGotifyConfig(e, u, t) },
                                    onGotifyTest = { viewModel.testGotify() },
                                    onOpenGotify = { },
                                    onResourceMonitorEnabled = { viewModel.settings.setResourceMonitorEnabled(it) },
                                    onPinMessagesEnabled = { viewModel.settings.setPinMessagesEnabled(it) },
                                    onWolServiceUrl = { viewModel.settings.setWolServiceUrl(it) },
                                    onActivityDetailLevel = { viewModel.settings.setActivityDetailLevel(it) }
                                )
                            }
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

    BackHandler(enabled = screen != GptScreen.Chat || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            screen = GptScreen.Chat
        }
    }

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
        if (screen != GptScreen.Chat) viewModel.consumeSpeech()
    }

    if (renameTarget != null || deleteTarget != null) {
        ChatGptTheme(theme = settings.theme, fontScale = settings.fontScale) {
            OmpTheme(theme = settings.theme, dynamicColor = false, fontScale = settings.fontScale) {
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
}

/** Legacy feature screens use the ChatGPT-inspired theme in this variant. */
@Composable
private fun LegacyHost(
    settings: com.omp.chat.data.AppSettings,
    content: @Composable () -> Unit
) {
    ChatGptTheme(theme = settings.theme, fontScale = settings.fontScale) {
        Surface(
            Modifier.fillMaxSize(),
            color = LocalChatGptColors.current.background
        ) {
            content()
        }
    }
}

/**
 * Pairing keeps the existing, verified QR/manual flow. It is hosted in the
 * classic palette because it is a complete, self-contained screen.
 */
@Composable
private fun GptPairingHost(
    connecting: Boolean,
    error: String,
    strings: Strings,
    settings: com.omp.chat.data.AppSettings,
    onScan: () -> Unit,
    onPair: (String, String) -> String?,
    onClearError: () -> Unit,
    onToast: (String) -> Unit,
    onGoToMain: () -> Unit
) {
    OmpTheme(theme = settings.theme, dynamicColor = false, fontScale = settings.fontScale) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                PairingScreen(
                    connecting = connecting,
                    error = error,
                    onScan = onScan,
                    onPair = onPair,
                    onClearError = onClearError,
                    onToast = onToast,
                    onGoToMain = onGoToMain
                )
            }
        }
    }
}
