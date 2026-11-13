package com.omp.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.omp.chat.data.ChatMessage
import com.omp.chat.data.ToolCallInfo
import com.omp.chat.data.str
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayError
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimaySidebar
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import com.omp.chat.ui.theme.NimayUserBubble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    streaming: Boolean,
    showThinking: Boolean,
    listState: LazyListState,
    onRegenerate: (String) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onTogglePin: (String) -> Unit = {},
    pinnedIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
) {
    val visible = remember(messages) { foldToolResults(messages) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(visible, key = { index, message -> "${message.id}-$index" }) { index, message ->
            val isLast = index == visible.lastIndex
            val isPinned = pinnedIds.contains(message.id)
            MessageItem(
                message = message,
                isLast = isLast,
                streaming = streaming,
                showThinking = showThinking,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onTogglePin = onTogglePin,
                isPinned = isPinned
            )
        }
        if (streaming && (visible.lastOrNull()?.isAssistant != true || visible.last().text.isBlank())) {
            item(key = "typing") { TypingRow() }
        }
        item(key = "tail") { Spacer(Modifier.size(8.dp)) }
    }
}

/**
 * OMP stores a tool call in the assistant entry and its outcome in a separate
 * `tool` entry. Rendering both produces one card per tool call plus a duplicate
 * "result" card (and the duplicate reported the wrong state after a denial), so
 * fold each result into the card it belongs to.
 */
internal fun foldToolResults(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.none { it.role == "tool" && it.toolCallId.isNotBlank() }) return messages
    val folded = messages.toMutableList()
    val consumed = HashSet<Int>()
    for (index in folded.indices) {
        val message = folded[index]
        if (message.role != "tool" || message.toolCallId.isBlank()) continue
        val holder = folded.indexOfFirst { candidate ->
            candidate.toolCalls.any { it.id == message.toolCallId }
        }
        if (holder < 0) continue
        val target = folded[holder]
        val tools = target.toolCalls.map { call ->
            if (call.id != message.toolCallId) call
            else call.copy(
                result = message.text.ifBlank { call.result },
                state = if (message.isError) "error" else "done",
                isError = message.isError
            )
        }
        folded[holder] = target.copy(toolCalls = tools)
        consumed.add(index)
    }
    if (consumed.isEmpty()) return messages
    return folded.filterIndexed { index, _ -> index !in consumed }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    isLast: Boolean,
    streaming: Boolean,
    showThinking: Boolean,
    onRegenerate: (String) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onTogglePin: (String) -> Unit,
    isPinned: Boolean
) {
    when {
        message.isUser -> UserMessage(message, onEdit, onTogglePin, isPinned)
        message.isAssistant -> AssistantMessage(message, isLast, streaming, showThinking, onRegenerate, onTogglePin, isPinned)
        message.isTool -> ToolResultMessage(message)
        message.isTerminal -> TerminalMessage(message)
        else -> SystemMessage(message)
    }
}

// ---------------------------------------------------------------------------
// User
// ---------------------------------------------------------------------------

@Composable
private fun UserMessage(
    message: ChatMessage,
    onEdit: (ChatMessage) -> Unit,
    onTogglePin: (String) -> Unit,
    isPinned: Boolean
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = NimayUserBubble,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = MarkdownParser.inline(
                                message.text,
                                NimayUserBubble,
                                NimayAccent
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NimayTextPrimary
                        )
                    }
                }
                if (message.images > 0) {
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            OmpIcons.Image,
                            contentDescription = null,
                            tint = NimayTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${message.images}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 3.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (message.status) {
                "sending" -> Text(
                    strings.sending,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayTextSecondary
                )
                "queued" -> Text(
                    "I kø",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayAccent
                )
                "error" -> Text(
                    strings.errorGeneric,
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayError
                )
                else -> Text(
                    relativeTime(message.timestamp, strings.lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayTextSecondary.copy(alpha = 0.7f)
                )
            }
            if (message.status != "sending") {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onEdit(message) }
                        .padding(4.dp)
                ) {
                    Icon(
                        OmpIcons.Pencil,
                        contentDescription = strings.editAndResend,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onTogglePin(message.id) }
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (isPinned) strings.unpinMessage else strings.pinMessage,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else NimayTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Assistant
// ---------------------------------------------------------------------------

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isLast: Boolean,
    streaming: Boolean,
    showThinking: Boolean,
    onRegenerate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    isPinned: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember(message.id) { mutableStateOf(false) }
    var thinkingOpen by remember(message.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            if (message.thinking.isNotBlank()) {
                ThinkingBlock(
                    text = message.thinking,
                    open = thinkingOpen,
                    onToggle = { thinkingOpen = !thinkingOpen }
                )
                Spacer(Modifier.size(8.dp))
            }

            if (message.text.isNotBlank()) {
                SelectionContainer {
                    MarkdownText(text = message.text)
                }
            }

            if (message.toolCalls.isNotEmpty()) {
                Spacer(Modifier.size(if (message.text.isBlank()) 0.dp else 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.toolCalls.forEach { tool ->
                        ToolCallActivityCard(tool)
                    }
                }
            }

            if (message.streaming) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypingDots(NimayAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.working,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (message.error.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Surface(
                    color = NimayError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, NimayError.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = NimayError,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            strings.errorText(message.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = NimayTextSecondary
                        )
                    }
                }
            }

            if (!message.streaming && (message.text.isNotBlank() || message.toolCalls.isNotEmpty())) {
                Spacer(Modifier.size(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SmallAction(
                        icon = if (copied) Icons.Default.Check else OmpIcons.Copy,
                        label = if (copied) strings.copied else strings.copy,
                        tint = if (copied) NimayAccent else NimayTextSecondary,
                        onClick = {
                            clipboard.setText(AnnotatedString(message.text))
                            copied = true
                            scope.launch {
                                delay(1500)
                                copied = false
                            }
                        }
                    )
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable { onTogglePin(message.id) }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (isPinned) strings.unpinMessage else strings.pinMessage,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else NimayTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (isLast) {
                        SmallAction(
                            icon = OmpIcons.Refresh,
                            label = strings.regenerate,
                            tint = NimayTextSecondary,
                            onClick = { onRegenerate(message.id) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val meta = buildList {
                        if (message.usage != null) add("${shortNumber(message.usage.total)} tok")
                        if (message.status == "stopped") add(strings.stopped)
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = NimayTextSecondary,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ThinkingBlock(text: String, open: Boolean, onToggle: () -> Unit) {
    val strings = LocalStrings.current
    Surface(
        color = NimaySidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.reasoning,
                    style = MaterialTheme.typography.labelMedium,
                    color = NimayTextSecondary
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    OmpIcons.ChevronDown,
                    contentDescription = null,
                    tint = NimayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = NimayTextSecondary,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------

@Composable
fun ToolCallCard(tool: ToolCallInfo) {
    val scheme = MaterialTheme.colorScheme
    val strings = LocalStrings.current
    var open by remember(tool.id) { mutableStateOf(false) }
    val running = tool.state == "running"
    val error = tool.isError || tool.state == "error"
    val unparsed = tool.state == "unparsed"

    Surface(
        color = NimaySidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (error) NimayError.copy(alpha = 0.5f) else NimayDivider,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = toolIcon(tool.name),
                    contentDescription = null,
                    tint = if (error) NimayError else if (unparsed) NimayTextSecondary else NimayAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = toolLabel(tool),
                    style = MaterialTheme.typography.labelMedium,
                    color = NimayTextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                if (running) {
                    TypingDots(NimayAccent)
                } else {
                    Text(
                        text = when {
                            error -> strings.toolError
                            unparsed -> strings.toolUnparsed
                            else -> strings.toolDone
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (error) scheme.error else scheme.onSurfaceVariant
                    )
                }
                Icon(
                    OmpIcons.ChevronDown,
                    contentDescription = null,
                    tint = NimayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            val summary = toolSummary(tool)
            if (summary.isNotBlank() && !open) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = summary,
                    style = MonoStyle.copy(fontSize = 11.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 23.dp)
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(top = 8.dp, start = 2.dp)) {
                    if (tool.args.isNotBlank()) {
                        SectionLabel(strings.args)
                        SelectionContainer {
                            Text(
                                text = pretty(tool.args),
                                style = MonoStyle,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NimayInput)
                                    .border(1.dp, NimayDivider, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                    if (tool.result.isNotBlank()) {
                        Spacer(Modifier.size(8.dp))
                        SectionLabel(strings.output)
                        SelectionContainer {
                            Text(
                                text = clamp(tool.result),
                                style = MonoStyle,
                                color = if (error) scheme.error else scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NimayInput)
                                    .border(1.dp, NimayDivider, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun toolIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    name.contains("bash") || name.contains("shell") || name.contains("terminal") -> OmpIcons.Terminal
    name.contains("write") || name.contains("edit") || name.contains("patch") -> OmpIcons.Pencil
    name.contains("read") || name.contains("grep") || name.contains("search") -> Icons.Default.Search
    name.contains("list") || name.contains("todo") -> OmpIcons.Checklist
    else -> OmpIcons.Sparkle
}

@Composable
private fun toolLabel(tool: ToolCallInfo): String {
    val name = tool.name.ifBlank { "verktøy" }
    if (tool.intent.isNotBlank()) return "$name · ${tool.intent.take(60)}"
    return name
}

private fun toolSummary(tool: ToolCallInfo): String {
    if (tool.args.isBlank()) return ""
    return try {
        val json = JsonParser.parseString(tool.args)
        if (!json.isJsonObject) return tool.args.take(120)
        val obj = json.asJsonObject
        when {
            obj.has("command") -> obj.str("command")
            obj.has("path") -> obj.str("path")
            obj.has("file_path") -> obj.str("file_path")
            obj.has("query") -> obj.str("query")
            obj.has("url") -> obj.str("url")
            obj.has("pattern") -> obj.str("pattern")
            else -> tool.args.take(120)
        }
    } catch (error: Exception) {
        tool.args.take(120)
    }
}

private fun pretty(raw: String): String = try {
    val element = JsonParser.parseString(raw)
    if (element.isJsonObject || element.isJsonArray) {
        GsonBuilder().setPrettyPrinting().create().toJson(element)
    } else raw
} catch (error: Exception) {
    raw
}

private fun clamp(text: String): String =
    if (text.length > 6000) text.take(6000) + "\n… (avkortet)" else text

// ---------------------------------------------------------------------------
// Other transcript roles
// ---------------------------------------------------------------------------

@Composable
private fun ToolResultMessage(message: ChatMessage) {
    ToolCallActivityCard(
        ToolCallInfo(
            id = message.id,
            name = message.toolName.ifBlank { "verktøy" },
            state = if (message.isError) "error" else "done",
            result = message.text,
            isError = message.isError
        )
    )
}

@Composable
private fun TerminalMessage(message: ChatMessage) {
    Surface(
        color = NimaySidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
    ) {
        SelectionContainer {
            Text(
                text = message.text,
                style = MonoStyle,
                color = if (message.isError) NimayError else NimayTextPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun SystemMessage(message: ChatMessage) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            message.text,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

// ---------------------------------------------------------------------------
// Indicators
// ---------------------------------------------------------------------------

@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 620, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun TypingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(NimaySidebar)
                .border(1.dp, NimayDivider, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypingDots(NimayAccent)
            Text(
                LocalStrings.current.working,
                style = MaterialTheme.typography.labelSmall,
                color = NimayTextSecondary
            )
        }
    }
}

private fun Color.isLight(): Boolean = 0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.5f
