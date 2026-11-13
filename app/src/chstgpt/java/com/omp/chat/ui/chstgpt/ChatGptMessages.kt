package com.omp.chat.ui.chstgpt

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.data.ChatMessage
import com.omp.chat.data.ToolCallInfo
import com.omp.chat.ui.components.MarkdownParser
import com.omp.chat.ui.components.MdBlock
import com.omp.chat.ui.components.foldToolResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatGptMessageList(
    messages: List<ChatMessage>,
    streaming: Boolean,
    listState: LazyListState,
    showThinking: Boolean,
    pinnedIds: Set<String>,
    onRegenerate: (String) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp)
) {
    val visible = remember(messages) { foldToolResults(messages) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(visible, key = { index, message -> "${message.id}-$index" }) { index, message ->
            val isLast = index == visible.lastIndex
            when {
                message.isUser -> GptUserMessage(message, onEdit)
                message.isAssistant -> GptAssistantMessage(
                    message = message,
                    isLast = isLast,
                    streaming = streaming,
                    showThinking = showThinking || message.thinking.isNotBlank(),
                    onRegenerate = onRegenerate,
                    onTogglePin = onTogglePin,
                    pinned = message.id in pinnedIds
                )
                message.isTool -> GptToolResultMessage(message)
                message.isTerminal -> GptTerminalMessage(message)
                else -> GptSystemMessage(message)
            }
        }
        if (streaming && (visible.lastOrNull()?.isAssistant != true || visible.last().text.isBlank())) {
            item(key = "gpt-typing") { GptTypingRow() }
        }
        item(key = "gpt-tail") { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun GptUserMessage(message: ChatMessage, onEdit: (ChatMessage) -> Unit) {
    val c = LocalChatGptColors.current
    val copy = rememberCopy()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.userBubble)
                .clickable { menu = !menu }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (message.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = MarkdownParser.inline(message.text, c.inlineCode, c.textPrimary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textPrimary
                    )
                }
            } else {
                Text("Vedlegg", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
        }
        AnimatedVisibility(visible = menu, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                GptSmallAction(Icons.Outlined.ContentCopy, "Kopier") { copy(message.text) }
                GptSmallAction(Icons.Outlined.Edit, "Rediger") { onEdit(message) }
                GptSmallAction(Icons.Outlined.Close, "Lukk") { menu = false }
            }
        }
    }
}

@Composable
private fun GptAssistantMessage(
    message: ChatMessage,
    isLast: Boolean,
    streaming: Boolean,
    showThinking: Boolean,
    onRegenerate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    pinned: Boolean
) {
    val c = LocalChatGptColors.current
    val copy = rememberCopy()
    var copied by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        if (message.thinking.isNotBlank() && showThinking) {
            GptThinkingBlock(message.thinking, message.streaming)
            Spacer(Modifier.height(8.dp))
        }

        if (message.text.isNotBlank()) {
            SelectionContainer {
                GptMarkdown(
                    text = if (message.streaming) message.text + " ▍" else message.text,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (message.streaming) {
            // The typing row below the list is enough; no invented text.
        }

        if (message.toolCalls.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                message.toolCalls.forEach { tool -> GptToolCard(tool) }
            }
        }

        if (message.error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.error.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = c.error, modifier = Modifier.size(16.dp))
                Text(message.error, color = c.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        val usage = message.usage
        if (usage != null && usage.total > 0) {
            Text(
                text = "${compactNumber(usage.total)} tokens" +
                    if (usage.cost > 0) " · $${String.format(java.util.Locale.US, "%.4f", usage.cost)}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            GptSmallAction(
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                label = if (copied) "Kopiert" else "Kopier"
            ) {
                copy(message.text)
                copied = true
                scope.launch { delay(1500); copied = false }
            }
            if (pinned) {
                GptSmallAction(Icons.Outlined.PushPin, "Løsne", tint = c.textPrimary) {
                    onTogglePin(message.id)
                }
            }
            if (isLast && !streaming) {
                GptSmallAction(Icons.Outlined.Refresh, "Prøv igjen") { onRegenerate(message.id) }
            }
            GptSmallAction(Icons.Outlined.Tune, "Mer") { actionsOpen = !actionsOpen }
        }
        AnimatedVisibility(visible = actionsOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Fest melding",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePin(message.id); actionsOpen = false }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Text(
                    "Skjul handlinger",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { actionsOpen = false }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GptSmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = LocalChatGptColors.current.textSecondary,
    onClick: () -> Unit
) {
    GptIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        size = 32,
        iconSize = 16,
        tint = tint
    )
}

@Composable
private fun GptThinkingBlock(text: String, streaming: Boolean) {
    val c = LocalChatGptColors.current
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface.copy(alpha = 0.55f))
            .border(1.dp, c.divider, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (streaming) GptWorkingDots(c.textSecondary) else
                Box(Modifier.size(5.dp).clip(CircleShape).background(c.textTertiary))
            Text(
                if (streaming) "Tenker …" else "Resonnering",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 21.sp
                ),
                color = c.textSecondary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
fun GptToolCard(tool: ToolCallInfo) {
    val c = LocalChatGptColors.current
    var open by remember(tool.id) { mutableStateOf(false) }
    val running = tool.state == "running" || tool.state == "queued"
    val label = toolLabel(tool)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface.copy(alpha = 0.55f))
            .border(1.dp, c.divider, RoundedCornerShape(12.dp))
            .clickable { open = !open }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = if (tool.isError) c.error else c.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tool.isError) c.error else c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        tool.isError -> "Feilet"
                        running -> "Kjører"
                        else -> "Fullført"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tool.isError) c.error else c.textTertiary
                )
            }
            if (running) {
                GptWorkingDots(c.textSecondary)
            } else {
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (tool.args.isNotBlank() && tool.args != "{}") {
                    GptCodeBlock("json", prettyJson(tool.args))
                    Spacer(Modifier.height(8.dp))
                }
                if (tool.result.isNotBlank()) {
                    GptCodeBlock("resultat", clamp(tool.result, 4000))
                }
            }
        }
    }
}

@Composable
private fun GptToolResultMessage(message: ChatMessage) {
    GptToolCard(
        ToolCallInfo(
            id = message.id,
            name = message.toolName.ifBlank { "verktøy" },
            result = message.text,
            state = if (message.isError) "error" else "done",
            isError = message.isError
        )
    )
}

@Composable
private fun GptTerminalMessage(message: ChatMessage) {
    val c = LocalChatGptColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.codeBackground)
            .border(1.dp, c.divider, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text("$ ${message.text}", color = c.textPrimary, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        if (message.error.isNotBlank()) {
            Text(message.error, color = c.error, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun GptSystemMessage(message: ChatMessage) {
    val c = LocalChatGptColors.current
    Text(
        message.text,
        style = MaterialTheme.typography.bodySmall,
        color = c.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface.copy(alpha = 0.4f))
            .padding(10.dp)
    )
}

@Composable
fun GptTypingRow() {
    val c = LocalChatGptColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GptWorkingDots(c.textSecondary)
        Text("Tenker …", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Markdown renderer with ChatGPT spacing and code treatment
// ---------------------------------------------------------------------------

@Composable
fun GptMarkdown(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = LocalChatGptColors.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val body = if (compact) MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
    else MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = MarkdownParser.inline(block.text, c.inlineCode, c.textPrimary),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = if (block.level == 1) 6.dp else 3.dp)
                )
                is MdBlock.Paragraph -> Text(
                    text = MarkdownParser.inline(block.text, c.inlineCode, c.textPrimary),
                    style = body,
                    color = c.textPrimary
                )
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = if (block.ordered) "${index + 1}." else "•",
                                style = body,
                                color = c.textSecondary,
                                modifier = Modifier.width(if (block.ordered) 24.dp else 16.dp)
                            )
                            Text(
                                text = MarkdownParser.inline(item, c.inlineCode, c.textPrimary),
                                style = body,
                                color = c.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.textTertiary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        MarkdownParser.inline(block.text, c.inlineCode, c.textPrimary),
                        style = body,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                is MdBlock.Code -> GptCodeBlock(block.lang, block.code)
                is MdBlock.Table -> GptTable(block)
                MdBlock.Divider -> HorizontalDivider(color = c.divider)
            }
        }
    }
}

@Composable
fun GptCodeBlock(lang: String, code: String) {
    val c = LocalChatGptColors.current
    val copy = rememberCopy()
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.codeBackground)
            .border(1.dp, c.divider, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.codeHeader)
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lang.ifBlank { "kode" },
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )
            GptIconButton(
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = "Kopier kode",
                onClick = {
                    copy(code)
                    copied = true
                    scope.launch { delay(1500); copied = false }
                },
                size = 30,
                iconSize = 15,
                tint = c.textSecondary
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                ),
                color = c.textPrimary
            )
        }
    }
}

@Composable
private fun GptTable(table: MdBlock.Table) {
    val c = LocalChatGptColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.divider, RoundedCornerShape(10.dp))
    ) {
        Row(Modifier.background(c.surface)) {
            table.header.forEach { cell ->
                Text(
                    MarkdownParser.inline(cell, c.inlineCode, c.textPrimary),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    modifier = Modifier
                        .widthIn(min = 96.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        HorizontalDivider(color = c.divider)
        table.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        MarkdownParser.inline(cell, c.inlineCode, c.textPrimary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = c.divider.copy(alpha = 0.6f))
        }
    }
}

private fun toolLabel(tool: ToolCallInfo): String {
    val name = tool.name.ifBlank { "verktøy" }
    val intent = tool.intent
    return if (intent.isNotBlank()) "$name · $intent" else name
}

private fun prettyJson(raw: String): String = try {
    val parsed = com.google.gson.JsonParser.parseString(raw)
    com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(parsed)
} catch (_: Exception) {
    raw
}

private fun clamp(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max) + "\n…"
