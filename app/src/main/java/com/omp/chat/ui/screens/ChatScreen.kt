package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.components.ChatGptDrawerContent
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.online
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import com.omp.chat.ui.theme.VariantColors.userBubble
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.ChatMessage
import com.omp.chat.data.ChatRepository
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.ChatRepository.Upload
import com.omp.chat.data.RepoState
import com.omp.chat.data.TodoPhase
import com.omp.chat.data.UiRequest
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.TokenIndicator
import com.omp.chat.ui.components.ResourceMonitorChip
import com.omp.chat.ui.components.ExportChatChip
import com.omp.chat.ui.components.PinMessageChip
import com.omp.chat.ui.components.ApprovalSheet
import com.omp.chat.ui.components.Attachment
import com.omp.chat.ui.components.AttachSheet
import com.omp.chat.ui.components.Composer
import com.omp.chat.ui.components.DeleteDialog
import com.omp.chat.ui.components.EditMessageDialog
import com.omp.chat.ui.components.MessageList
import com.omp.chat.ui.components.ModelSheet
import com.omp.chat.ui.components.OmpAvatar
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.components.RenameDialog
import com.omp.chat.ui.components.StatusDot
import com.omp.chat.ui.components.TerminalSheet
import com.omp.chat.ui.components.TypingDots
import com.omp.chat.ui.components.fileName
import com.omp.chat.ui.components.shortModel
import com.omp.chat.ui.components.shortNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: RepoState,
    attachments: List<Attachment>,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    showThinking: Boolean,
    speechText: String?,
    onSpeechConsumed: () -> Unit,
    onBack: () -> Unit,
    onSend: (String, List<String>, List<ChatRepository.Upload>) -> Unit,
    onQueue: (String, List<String>, List<ChatRepository.Upload>) -> Unit = { _, _, _ -> },
    onStop: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEditResend: (String, String) -> Unit,
    onAnswer: (UiRequest, String?, Boolean?, Boolean) -> Unit,
    onAnswers: (UiRequest, List<Pair<String, List<String>>>) -> Unit,
    onModel: (ModelInfo) -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onOpenPcs: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    onOpenGit: () -> Unit = {},
    resourceMonitorEnabled: Boolean = false,
    onLoadResources: () -> Unit = {},
    exportChatEnabled: Boolean = false,
    onExportChat: (Int) -> Unit = {},
    pinMessagesEnabled: Boolean = false,
    onTogglePinMessage: (String) -> Unit = {},
    onPinnedMessageClicked: (String) -> Unit = {},
    onOpenActiveChat: () -> Unit = {},
    onOpenCleanup: () -> Unit = {},
    pcName: String = "PC",
    loadActiveBridge: suspend () -> com.omp.chat.data.ActiveBridgeSession? = { null },
    animatedActive: Boolean = true,
    onOpenProjectHistory: (String) -> Unit = {},
    onReconnect: () -> Unit,
    onReloadModels: () -> Unit,
    onClearTerminal: () -> Unit,
    onTerminal: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit,
    onVoiceCall: () -> Unit,
    onToast: (String) -> Unit,
    onOpenSession: (SessionSummary) -> Unit = {},
    onRenameSession: (SessionSummary) -> Unit = {},
    onArchiveSession: (SessionSummary, Boolean) -> Unit = { _, _ -> },
    onDeleteSession: (SessionSummary) -> Unit = {},
    onSelectProject: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    isFavorite: (String) -> Boolean = { false }
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var todosOpen by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    LaunchedEffect(speechText) {
        val spoken = speechText?.trim().orEmpty()
        if (spoken.isNotEmpty()) {
            draft = if (draft.isBlank()) spoken else draft.trimEnd() + " " + spoken
        }
        onSpeechConsumed()
    }

    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }

    /** Meldinger sortert med pinnede øverst, ellers kronologisk. */
    val displayMessages = remember(state.messages, state.pinnedMessageIds) {
        state.messages.sortedWith(
            compareByDescending<ChatMessage> { it.id in state.pinnedMessageIds }
                .thenBy { it.timestamp }
        )
    }

    /** Scroller til en bestemt melding i listen. */
    val scrollToMessage: (String) -> Unit = { messageId ->
        val index = displayMessages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            scope.launch {
                listState.animateScrollToItem(index)
            }
        }
    }
    val canScroll by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // AutofÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸lg under streaming: fÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸lg alltid nyeste mens OMP svarer.
    LaunchedEffect(state.sessionFile) {
        autoFollow = true
    }

    // NÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥r brukeren scroller manuelt, slutt ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ autofÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸lg. Gjenoppta nÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥r bunnen nÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥s.
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling) {
                autoFollow = !listState.canScrollForward
            }
            wasScrolling = scrolling
        }
    }

    // Manuelt avstandssjekk: hvis brukeren er nederst, hold bunnen fÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸lges.
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { away ->
            if (!away) autoFollow = true
        }
    }

    // Scroll til siste nÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥r innholdet vokser. Storleken pÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ listene er ikke nok:
    // delta-tekst og verktÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ykall streaming oppdateres OG-standard i den siste
    // melding uten at listen vokser, sÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ fingerprint av det siste element.
    val tailFingerprint = remember(state.messages) {
        tailFingerprint(state.messages.lastOrNull())
    }
    LaunchedEffect(state.messages.size, state.streaming, tailFingerprint) {
        if (autoFollow && state.messages.isNotEmpty()) {
            listState.scrollToLatest(animate = false)
        }
    }

    val send: () -> Unit = {
        val text = draft.trim()
        if (text.isNotEmpty() || attachments.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val images = attachments.filter { it.isImage }
                .map { "data:${it.mime};base64,${it.base64}" }
            val files = attachments.filter { !it.isImage }
                .map { ChatRepository.Upload(it.name, it.mime, it.base64) }
            onSend(text, images, files)
            draft = ""
            onAttachmentsChange(emptyList())
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VariantColors.sidebar,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(310.dp)
            ) {
                ChatGptDrawerContent(
                    state = state,
                    currentSessionFile = state.sessionFile,
                    onOpenSession = onOpenSession,
                    onNewChat = { cwd ->
                        onNewChat()
                    },
                    onSelectProject = onSelectProject,
                    onOpenModelPicker = {
                        onReloadModels()
                        modelOpen = true
                    },
                    onRename = onRenameSession,
                    onArchive = onArchiveSession,
                    onDelete = onDeleteSession,
                    onOpenSettings = onOpenSettings,
                    onOpenFiles = onOpenFiles,
                    onOpenPcs = onOpenPcs,
                    onOpenTasks = onOpenTasks,
                    onOpenTerminal = onOpenTerminal,
                    onOpenGit = onOpenGit,
                    onReconnect = onReconnect,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onToggleFavorite = onToggleFavorite,
                    isFavorite = isFavorite,
                    onOpenActiveChat = onOpenActiveChat,
                    onOpenCleanup = onOpenCleanup,
                    pcName = pcName,
                    loadActiveBridge = loadActiveBridge,
                    animatedActive = animatedActive,
                    onOpenProjectHistory = onOpenProjectHistory
                )
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(VariantColors.background)
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
        // ------------------------------------------------------------- header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        OmpIcons.Menu,
                        contentDescription = strings.chats,
                        tint = VariantColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = strings.displayTitle(state.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = VariantColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onReloadModels()
                                modelOpen = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = shortModel(state.chat.model?.name ?: "").ifBlank { strings.model },
                            style = MaterialTheme.typography.labelSmall,
                            color = VariantColors.textSecondary,
                            maxLines = 1
                        )
                        Icon(
                            OmpIcons.ChevronDown,
                            contentDescription = null,
                            tint = VariantColors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        // Tokenindikator ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ integrert i modell-raden, ingen egen boks
                        if (state.chat.contextWindow > 0 || state.chat.contextTokens > 0) {
                            TokenIndicator(
                                used = state.chat.contextTokens,
                                max = state.chat.contextWindow,
                            )
                        }
                    }
                }

                // Ressursmonitor - diskret chip i header
                ResourceMonitorChip(
                    resources = state.resources,
                    enabled = resourceMonitorEnabled,
                    onRefresh = onLoadResources,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Eksport-chat - diskret chip i header
                ExportChatChip(
                    strings = strings,
                    onExport = onExportChat,
                    onToast = onToast,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Pinne-meldinger - diskret chip i header
                PinMessageChip(
                    strings = strings,
                    pinnedMessageIds = state.pinnedMessageIds,
                    messages = state.messages,
                    onPinToggled = onTogglePinMessage,
                    onPinnedMessageClicked = { messageId ->
                        scrollToMessage(messageId)
                        onPinnedMessageClicked(messageId)
                    },
                    onToast = onToast,
                    modifier = Modifier.padding(end = 4.dp)
                )


                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        OmpIcons.NewChat,
                        contentDescription = strings.newChat,
                        tint = VariantColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            OmpIcons.MoreVert,
                            contentDescription = strings.chatMenu,
                            tint = VariantColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(strings.rename) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; renameOpen = true }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.terminal) },
                            leadingIcon = { Icon(OmpIcons.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; terminalOpen = true }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.settings) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onOpenSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.archive) },
                            leadingIcon = { Icon(OmpIcons.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.delete, color = VariantColors.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = VariantColors.error, modifier = Modifier.size(18.dp))
                            },
                            onClick = { menuOpen = false; deleteOpen = true }
                        )
                    }
                }
            }

        // --------------------------------------------------------------- todos
        if (state.todos.isNotEmpty()) {
            TodoStrip(
                phases = state.todos,
                open = todosOpen,
                onToggle = { todosOpen = !todosOpen }
            )
        }

        if (!state.connected) {
            Surface(
                color = VariantColors.error.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, VariantColors.error.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = VariantColors.error, modifier = Modifier.size(15.dp))
                    Text(
                        strings.connectionLost,
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onReconnect) {
                        Text(strings.reconnect, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.readOnly) {
            Surface(
                color = VariantColors.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, VariantColors.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = VariantColors.accent, modifier = Modifier.size(15.dp))
                    Text(
                        text = state.readOnlyReason.ifBlank {
                            "Samtalen vises skrivebeskyttet."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onNewChat) {
                        Text(strings.newChat, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ------------------------------------------------------------ messages
        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyChat(
                    cwd = state.cwd,
                    streaming = state.streaming,
                    streamingStartedAt = state.streamingStartedAt,
                    onSuggestion = { draft = it }
                )
            } else {
                MessageList(
                    messages = displayMessages,
                    streaming = state.streaming,
                    showThinking = showThinking,
                    listState = listState,
                    onRegenerate = onRegenerate,
                    onEdit = { editTarget = it },
                    onTogglePin = onTogglePinMessage,
                    pinnedIds = state.pinnedMessageIds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (canScroll && state.messages.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 10.dp)
                ) {
                    Surface(
                        color = VariantColors.surfaceElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, VariantColors.divider, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { listState.scrollToLatest(animate = true) }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                OmpIcons.ChevronDown,
                                contentDescription = strings.showMore,
                                tint = VariantColors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------- composer
        Composer(
            value = draft,
            onValueChange = { draft = it },
            streaming = state.streaming,
            attachments = attachments,
            enabled = state.connected,
            onRemoveAttachment = { index ->
                onAttachmentsChange(attachments.filterIndexed { i, _ -> i != index })
            },
            onSend = send,
            onQueue = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val text = draft.trim()
                if (text.isNotEmpty() || attachments.isNotEmpty()) {
                    val images = attachments.filter { it.isImage }
                        .map { "data:${it.mime};base64,${it.base64}" }
                    val files = attachments.filter { !it.isImage }
                        .map { ChatRepository.Upload(it.name, it.mime, it.base64) }
                    onQueue(text, images, files)
                    draft = ""
                    onAttachmentsChange(emptyList())
                }
            },
            onStop = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onStop()
            },
            onAttachClick = { attachOpen = true },
            onVoiceClick = onVoice,
            onVoiceCallClick = onVoiceCall
        )
        }
    }

    if (modelOpen) {
        ModelSheet(
            models = state.models,
            current = state.chat.model,
            usageCounts = state.modelUsageCounts,
            onSelect = onModel,
            onDismiss = { modelOpen = false }
        )
    }

    if (attachOpen) {
        AttachSheet(
            onPhoto = onPickPhoto,
            onCamera = onPickCamera,
            onFile = onPickFile,
            onVoice = onVoice,
            onDismiss = { attachOpen = false }
        )
    }

    if (terminalOpen) {
        TerminalSheet(
            entries = state.terminal,
            terminalEnabled = state.terminalEnabled,
            onRun = { command -> onTerminal(command) },
            onClear = onClearTerminal,
            onDismiss = { terminalOpen = false }
        )
    }

    editTarget?.let { target ->
        EditMessageDialog(
            initial = target.text,
            onSave = { text ->
                onEditResend(target.id, text)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    if (renameOpen) {
        RenameDialog(
            initial = state.title,
            onSave = { name ->
                onRename(name)
                renameOpen = false
            },
            onDismiss = { renameOpen = false }
        )
    }

    if (deleteOpen) {
        DeleteDialog(
            title = state.title,
            onConfirm = {
                deleteOpen = false
                onDelete()
            },
            onDismiss = { deleteOpen = false }
        )
    }

    state.request?.let { request ->
        ApprovalSheet(
            request = request,
            onAnswer = { value, confirmed, cancelled -> onAnswer(request, value, confirmed, cancelled) },
            onAnswers = { answers -> onAnswers(request, answers) },
            onDismiss = {}
        )
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun WorkingStatusWithTimer(startTime: Long) {
    val strings = LocalStrings.current
    var elapsedSeconds by remember(startTime) { mutableStateOf(0) }

    LaunchedEffect(startTime) {
        if (startTime <= 0) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            delay(1000)
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = if (minutes > 0) "${minutes} min ${seconds} sek" else "${seconds} sek"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Arbeider · $timeText",
            style = MaterialTheme.typography.labelSmall,
            color = VariantColors.textSecondary
        )
    }
}

@Composable
private fun EmptyChat(cwd: String, streaming: Boolean, streamingStartedAt: Long = 0L, onSuggestion: (String) -> Unit) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OmpAvatar(size = 40.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            strings.emptyTitle,
            style = MaterialTheme.typography.titleLarge,
            color = VariantColors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            strings.emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = VariantColors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (cwd.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = VariantColors.surfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, VariantColors.divider, RoundedCornerShape(8.dp))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        OmpIcons.Folder,
                        contentDescription = null,
                        tint = VariantColors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        fileName(cwd).ifBlank { cwd },
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (streaming) {
            Spacer(Modifier.height(16.dp))
            WorkingStatusWithTimer(startTime = streamingStartedAt)
        }
    }
}

// ---------------------------------------------------------------------------
// Todo progress
// ---------------------------------------------------------------------------

@Composable
private fun TodoStrip(phases: List<TodoPhase>, open: Boolean, onToggle: () -> Unit) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val total = phases.sumOf { it.tasks.size }
    val done = phases.sumOf { phase -> phase.tasks.count { it.done } }
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done.toFloat() / total,
        label = "todo"
    )

    Surface(
        color = VariantColors.sidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, VariantColors.divider, RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(OmpIcons.Checklist, contentDescription = null, tint = VariantColors.accent, modifier = Modifier.size(16.dp))
                Text(
                    strings.todoProgress,
                    style = MaterialTheme.typography.labelMedium,
                    color = VariantColors.textPrimary
                )
                if (total > 0) {
                    Text(
                        "$done/$total",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    OmpIcons.ChevronDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (total > 0) {
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = VariantColors.accent,
                    trackColor = VariantColors.surfaceElevated
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    phases.forEach { phase ->
                        if (phase.name.isNotBlank()) {
                            Text(
                                phase.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        phase.tasks.forEach { task ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                val color = when {
                                    task.done -> scheme.primary
                                    task.status == "in_progress" -> scheme.secondary
                                    task.abandoned -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.16f))
                                        .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (task.done) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = scheme.primary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                                Text(
                                    task.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                                    textDecoration = if (task.abandoned) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scrolls to the true end of the transcript.
 */
private suspend fun LazyListState.scrollToLatest(animate: Boolean) {
    repeat(5) { attempt ->
        val count = layoutInfo.totalItemsCount
        if (count > 0) {
            if (animate && attempt == 0) {
                animateScrollToItem(count - 1)
                animateScrollBy(100_000f)
            } else {
                scrollToItem(count - 1)
                scrollBy(100_000f)
            }
        }
        delay(if (attempt == 0) 32 else 48)
        if (!canScrollForward) return
    }
}

/**
 * Cheap fingerprint of the newest message so a tail that grows *inside* the
 * last item (streamed deltas, tool call progress) triggers the auto-follow
 * scroll even though the message count never changes.
 */
private fun tailFingerprint(message: ChatMessage?): Long {
    if (message == null) return 0L
    return message.text.length.toLong() * 1_000_003L +
        message.thinking.length.toLong() * 17L +
        message.toolCalls.fold(0L) { acc, tool ->
            acc * 29L + tool.name.length + tool.result.length + (if (tool.state == "done") 7L else 1L)
        } +
        (if (message.streaming) 5L else 0L)
}
