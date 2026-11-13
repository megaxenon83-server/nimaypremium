package com.omp.chat.ui.chstgpt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.data.ChatMessage
import com.omp.chat.data.ChatRepository
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.RepoState
import com.omp.chat.data.UiRequest
import com.omp.chat.ui.components.Attachment
import com.omp.chat.ui.components.EditMessageDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The main, ChatGPT-inspired conversation screen. Everything it shows comes
 * from [RepoState]; nothing is simulated.
 */
@Composable
fun ChatGptChatScreen(
    state: RepoState,
    attachments: List<Attachment>,
    showThinking: Boolean,
    pcName: String,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: (String, List<String>, List<ChatRepository.Upload>) -> Unit,
    onQueue: (String, List<String>, List<ChatRepository.Upload>) -> Unit,
    onStop: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEditResend: (String, String) -> Unit,
    onAnswer: (UiRequest, String?, Boolean?, Boolean) -> Unit,
    onAnswers: (UiRequest, List<Pair<String, List<String>>>) -> Unit,
    onModel: (ModelInfo) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPcs: () -> Unit,
    onOpenActive: () -> Unit,
    onReloadModels: () -> Unit,
    onTogglePin: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit,
    onToast: (String) -> Unit
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    val scrollToBottomLabel = if (strings.lang == "en") "Go to newest" else "Gå til nyeste"
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var input by remember(state.sessionFile, state.cwd) { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var tokenDetailsOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var followsBottom by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }

    val lastAssistantStreaming = state.messages.lastOrNull { it.isAssistant }?.streaming == true
    val working = state.streaming || state.chat.isStreaming || lastAssistantStreaming
    val currentModel = state.chat.model ?: state.models.firstOrNull { it.key == state.modelUsageCounts.maxByOrNull { e -> e.value }?.key }

    val tail = state.messages.lastOrNull()
    val tailFingerprint = "${state.messages.size}:${tail?.text?.length ?: 0}:${tail?.id.orEmpty()}"

    // Smart autoscroll: follow the bottom only when the user is already there
    // (within a small tolerance). Stop following when the user scrolls up,
    // and re-engage when they scroll back to the bottom.
    LaunchedEffect(listState, working) {
        snapshotFlow { listState.isAtBottom() }
            .debounce(100)
            .collect { atBottom ->
                if (atBottom) followsBottom = true
            }
    }

    LaunchedEffect(tailFingerprint, working, followsBottom) {
        if (working && followsBottom) {
            // Keep the newest delta visible while OMP writes.
            delay(60)
            if (state.messages.isNotEmpty()) listState.scrollToBottom()
        }
    }

    LaunchedEffect(state.sessionFile) {
        // A conversation opens at its newest content, like ChatGPT.
        if (state.messages.isNotEmpty()) {
            delay(80)
            listState.scrollToBottom()
        }
    }

    LaunchedEffect(listState) {
        // Show the "jump to newest" button only when the user has scrolled
        // away from the bottom.
        snapshotFlow { !listState.isAtBottom() }
            .debounce(200)
            .distinctUntilChanged()
            .collect { away ->
                showJump = away
                if (away) followsBottom = false
            }
    }

    val send: (String, Boolean) -> Unit = { text, queue ->
        if (text.isNotBlank() || attachments.isNotEmpty()) {
            val images = attachments.filter { it.isImage }.map { "data:${it.mime};base64,${it.base64}" }
            val files = attachments.filter { !it.isImage }.map { ChatRepository.Upload(it.name, it.mime, it.base64) }
            if (queue) onQueue(text, images, files) else onSend(text, images, files)
            input = ""
            onAttachmentsChange(emptyList())
            focus.clearFocus()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .imePadding()
    ) {
        GptTopBar(
            title = state.title.ifBlank { strings.newChat },
            modelName = currentModel?.let { shortModelName(it.name) } ?: strings.models,
            working = working,
            pcName = pcName,
            contextTokens = state.chat.contextTokens,
            contextWindow = state.chat.contextWindow,
            contextPercent = state.chat.contextPercent,
            onMenu = onOpenDrawer,
            onModel = { modelOpen = true },
            onNewChat = onNewChat,
            onTokenClick = { tokenDetailsOpen = true }
        )

        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty() && !state.busy) {
                GptEmptyState(
                    pcName = pcName,
                    connected = state.connected,
                    streaming = working,
                    onSuggestion = { input = it }
                )
            } else {
                ChatGptMessageList(
                    messages = state.messages,
                    streaming = working,
                    listState = listState,
                    showThinking = showThinking,
                    pinnedIds = state.pinnedMessageIds,
                    onRegenerate = onRegenerate,
                    onEdit = { editTarget = it },
                    onTogglePin = onTogglePin,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showJump) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.surfaceElevated)
                        .border(1.dp, c.divider, CircleShape)
                        .clickable {
                            scope.launch {
                                followsBottom = true
                                if (state.messages.isNotEmpty()) {
                                    listState.scrollToBottom()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = scrollToBottomLabel,
                        tint = c.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.readOnly) {
            Text(
                state.readOnlyReason.ifBlank { "Denne samtalen er skrivebeskyttet." },
                style = MaterialTheme.typography.labelSmall,
                color = c.warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        ChatGptComposer(
            value = input,
            onValueChange = { input = it },
            streaming = working,
            working = working,
            attachments = attachments,
            enabled = !state.readOnly,
            tokenText = "",
            onRemoveAttachment = { index -> onAttachmentsChange(attachments.filterIndexed { i, _ -> i != index }) },
            onSend = { send(input, false) },
            onQueue = { send(input, true) },
            onStop = onStop,
            onAttachClick = { attachOpen = true },
            onVoice = onVoice
        )
    }

    if (attachOpen) {
        ChatGptAttachSheet(
            onPhoto = onPickPhoto,
            onCamera = onPickCamera,
            onFile = onPickFile,
            onVoice = onVoice,
            onDismiss = { attachOpen = false }
        )
    }

    if (modelOpen) {
        ChatGptModelSheet(
            models = state.models,
            current = state.chat.model,
            usageCounts = state.modelUsageCounts,
            modelsLoading = state.busy,
            onRefresh = onReloadModels,
            onSelect = { model ->
                onModel(model)
                modelOpen = false
            },
            onDismiss = { modelOpen = false }
        )
    }

    if (tokenDetailsOpen) {
        Dialog(onDismissRequest = { tokenDetailsOpen = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceElevated)
                    .border(1.dp, c.divider, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tokenbruk", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        TextButton(onClick = { tokenDetailsOpen = false }) { Text("Lukk", color = c.textSecondary) }
                    }
                    Text("Aktiv PC: $pcName", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Aktiv modell: ${state.chat.model?.name ?: "Ikke tilgjengelig"}", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Brukt kontekst: ${String.format("%,d", state.chat.contextTokens).replace(',', ' ')} / ${String.format("%,d", state.chat.contextWindow).replace(',', ' ')} tokens", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Kontekstprosent: ${String.format("%.1f", state.chat.contextPercent).replace('.', ',')} %", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Utvidet statistikk er ikke tilgjengelig fra OMP.", color = c.textTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    state.request?.let { request ->
        ChatGptApprovalSheet(
            request = request,
            onAnswer = { value, confirmed, cancelled -> onAnswer(request, value, confirmed, cancelled) },
            onAnswers = { answers -> onAnswers(request, answers) },
            onDismiss = { onAnswer(request, null, null, true) }
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
}

@Composable
private fun GptTopBar(
    title: String,
    modelName: String,
    working: Boolean,
    pcName: String,
    contextTokens: Int,
    contextWindow: Int,
    contextPercent: Double,
    onMenu: () -> Unit,
    onModel: () -> Unit,
    onNewChat: () -> Unit,
    onTokenClick: () -> Unit = {}
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GptIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = strings.chats,
                onClick = onMenu,
                size = 44,
                iconSize = 22
            )
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onModel() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (working) {
                        GptWorkingDots(c.online)
                    } else {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(c.online)
                        )
                    }
                    Text(
                        modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val ctxTokens = contextTokens
            val ctxWindow = contextWindow
            val ctxPercent = contextPercent
            val ctxLabel = when {
                ctxTokens >= 1_000_000 -> String.format("%.1fM", ctxTokens / 1_000_000f).replace('.', ',')
                ctxTokens >= 1000 -> String.format("%.1fk", ctxTokens / 1000f).replace('.', ',')
                else -> ctxTokens.toString()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onTokenClick() }
            ) {
                CircularProgressIndicator(
                    progress = { (ctxPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = c.textSecondary,
                    trackColor = c.divider
                )
                Text(
                    text = ctxLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1
                )
            }
            GptIconButton(
                icon = Icons.Filled.Add,
                contentDescription = strings.newChat,
                onClick = onNewChat,
                size = 44,
                iconSize = 23
            )
        }
        Text(
            pcName,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        GptDivider()
    }
}

@Composable
private fun GptEmptyState(
    pcName: String,
    connected: Boolean,
    streaming: Boolean,
    onSuggestion: (String) -> Unit
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    val english = strings.lang == "en"
    val suggestions = if (english) listOf(
        Triple(Icons.Outlined.EditNote, "Write or edit", "Help me improve a text I paste"),
        Triple(Icons.Outlined.Terminal, "Look at the code", "Show what changed in this project"),
        Triple(Icons.Outlined.Explore, "Find out", "Explain how this project is built"),
        Triple(Icons.Outlined.Lightbulb, "Think out loud", "Suggest the next step for this project")
    ) else listOf(
        Triple(Icons.Outlined.EditNote, "Skriv eller rediger", "Hjelp meg å forbedre en tekst jeg limer inn"),
        Triple(Icons.Outlined.Terminal, "Se på koden", "Vis hva som er endret i dette prosjektet"),
        Triple(Icons.Outlined.Explore, "Finn ut", "Forklar hvordan dette prosjektet er bygget"),
        Triple(Icons.Outlined.Lightbulb, "Tenk høyt", "Foreslå neste steg for dette prosjektet")
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        NimayMark(size = 46)
        Spacer(Modifier.height(16.dp))
        Text(
            if (english) "What can I help with?" else "Hva kan jeg hjelpe med?",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (connected) (if (english) "Connected to $pcName" else "Tilkoblet $pcName")
            else (if (english) "Not connected" else "Ikke tilkoblet"),
            style = MaterialTheme.typography.labelMedium,
            color = if (connected) c.textSecondary else c.warning
        )
        Spacer(Modifier.height(26.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(suggestions) { (icon, heading, body) ->
                GptSuggestionRow(icon, heading, body) {
                    if (!streaming) onSuggestion(body)
                }
            }
        }
    }
}

@Composable
private fun GptSuggestionRow(icon: ImageVector, heading: String, body: String, onClick: () -> Unit) {
    val c = LocalChatGptColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface.copy(alpha = 0.6f))
            .border(1.dp, c.divider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f)) {
            Text(heading, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
            Text(body, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Scrolls to the real bottom of the transcript, including when the newest
 * assistant message is taller than the viewport (plain scrollToItem would only
 * reveal its first line).
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottom() {
    if (layoutInfo.totalItemsCount == 0) return
    scrollBy(1_000_000f)
}

/**
 * Returns true when the lazy list is scrolled to (or very near) the bottom.
 * A small tolerance prevents the follow-mode from toggling on minor layout
 * changes.
 */
private fun androidx.compose.foundation.lazy.LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    // If the last item is visible, we're at the bottom.
    return lastVisible.index >= info.totalItemsCount - 1
}
