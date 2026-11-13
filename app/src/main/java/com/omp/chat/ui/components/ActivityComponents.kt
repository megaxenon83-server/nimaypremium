package com.omp.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.omp.chat.data.str
import com.omp.chat.data.ToolCallInfo
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayActive
import com.omp.chat.ui.theme.NimayBackground
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayError
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimaySidebar
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Ekstra ikoner som ikke finnes i OmpIcons
// ---------------------------------------------------------------------------

private object LocalIcons {
    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black)
    ).build()

    val Stop = icon("Stop", "M7 7h10v10H7z")
    val Send = icon("Send", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8z")
    val Error = icon("Error", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z")
    val ExpandLess = icon("ExpandLess", "M12 16.5l-6-6 1.41-1.41L12 13.17l4.59-4.58L18 10.59z")
    val ExpandMore = icon("ExpandMore", "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14.41z")
    val Info = icon("Info", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z")
    val Search = icon("Search", "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z")
    val Play = icon("Play", "M8 5v14l11-7z")
    val Check = icon("Check", "M9 16.2l-3.5-3.5-0.7 0.7 4.2 4.2 8.3-8.3-0.7-0.7-7.6 7.5z")
}

// ---------------------------------------------------------------------------
// Live aktivitetskort
// ---------------------------------------------------------------------------

/** Status for en aktivitet. */
enum class ActivityStatus {
    Running, Done, Error
}

/** Viser en aktivitet som OMP for øyeblikket utføre (eller nettopp fullførte). */
@Composable
fun ActivityCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    status: ActivityStatus = ActivityStatus.Running,
    onDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val running = status == ActivityStatus.Running

    Surface(
        color = if (running) NimayAccent.copy(alpha = 0.08f) else NimayInput,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (running) NimayAccent.copy(alpha = 0.35f) else NimayDivider,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onDetails?.invoke() })
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (running) NimayAccent.copy(alpha = 0.2f) else NimayDivider)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (running) NimayAccent else NimayTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = NimayTextPrimary,
                        maxLines = 1
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                when (status) {
                    ActivityStatus.Running -> {
                        TypingDots(NimayAccent)
                    }
                    ActivityStatus.Done -> {
                        Icon(
                            imageVector = LocalIcons.Check,
                            contentDescription = null,
                            tint = NimayActive,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    ActivityStatus.Error -> {
                        Icon(
                            imageVector = LocalIcons.Error,
                            contentDescription = null,
                            tint = NimayError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (onDetails != null) {
                    IconButton(
                        onClick = { onDetails() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = OmpIcons.EditSquare,
                            contentDescription = strings.copy,
                            tint = NimayTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Detalj-panel for en aktivitet (utvides når brukeren trykker). */
@Composable
fun ActivityDetails(
    title: String,
    content: String,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(isOpen) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = NimayTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) LocalIcons.ExpandLess else LocalIcons.ExpandMore,
                contentDescription = null,
                tint = NimayTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = NimayInput,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MonoStyle.copy(fontSize = 12.sp),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { expanded = false; onClose() },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = strings.close,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayAccent
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tokenindikator (sirkel, OpenCode-inspirert)
// ---------------------------------------------------------------------------

/**
 * Diskret tokenindikator – vises som en liten prikk + prosent i toppfeltet.
 * [used] = antall tokens i kontekst, [max] = maks kontekstvindu.
 * Hvis [max] er 0 eller [used] er 0 vises "Ikke tilgjengelig".
 * Ingen farge, ingen boks, ingen ramme – kun dempet tekst i appens palett.
 */
@Composable
fun TokenIndicator(
    used: Int,
    max: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val available = max > 0 && used > 0
    val percent = if (available) (used.toDouble() / max * 100).roundToInt() else 0

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (available) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NimayTextSecondary.copy(alpha = 0.6f))
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = NimayTextSecondary.copy(alpha = 0.8f),
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = LocalIcons.Info,
                contentDescription = null,
                tint = NimayTextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = strings.tokensNotAvailable,
                style = MaterialTheme.typography.labelSmall,
                color = NimayTextSecondary.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/** Detalj-panel for tokenbruk (åpnes når brukeren trykker på TokenIndicator). */
@Composable
fun TokenDetails(
    used: Int,
    max: Int,
    model: String?,
    inputTokens: Int,
    outputTokens: Int,
    cost: Double?,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val available = max > 0 && used > 0
    val percent = if (available) (used.toDouble() / max * 100).roundToInt() else 0
    var expanded by remember { mutableStateOf(isOpen) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tokenUsage,
                style = MaterialTheme.typography.labelMedium,
                color = NimayTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) LocalIcons.ExpandLess else LocalIcons.ExpandMore,
                contentDescription = null,
                tint = NimayTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = NimayInput,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TokenRow(
                        label = strings.contextUsed,
                        value = if (available) used.toString() else strings.tokensNotAvailable,
                        sub = if (available) "$percent% av $max" else null
                    )
                    TokenRow(
                        label = strings.contextTotal,
                        value = if (available) max.toString() else strings.tokensNotAvailable
                    )
                    TokenRow(
                        label = strings.inputTokens,
                        value = inputTokens.toString()
                    )
                    TokenRow(
                        label = strings.outputTokens,
                        value = outputTokens.toString()
                    )
                    if (cost != null) {
                        TokenRow(
                            label = strings.estimatedCost,
                            value = String.format("%.4f", cost),
                            sub = strings.estimatedCostSub
                        )
                    }
                    if (model != null && model.isNotBlank()) {
                        TokenRow(label = strings.model, value = model)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { expanded = false; onClose() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = strings.close,
                                style = MaterialTheme.typography.labelSmall,
                                color = NimayAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenRow(label: String, value: String, sub: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimayTextSecondary,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = NimayTextPrimary
        )
        if (sub != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = NimayTextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Send/Stopp-knapp (ingen sterk rød, subtil overgang)
// ---------------------------------------------------------------------------

/** Stopp/send-knapp som bytter subtilt mellom to tilstander uten layout-hopping. */
@Composable
fun SendStopButton(
    streaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    val canSend = enabled && !streaming

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (streaming) NimayAccent.copy(alpha = 0.15f)
                else if (canSend) NimayAccent
                else NimayDivider
            )
            .border(
                if (streaming) 1.dp else 0.dp,
                if (streaming) NimayAccent.copy(alpha = 0.5f) else Color.Transparent,
                CircleShape
            )
            .clickableNoRipple(enabled = enabled) {
                if (streaming) onStop() else onSend()
            },
        contentAlignment = Alignment.Center
    ) {
        if (streaming) {
            Icon(
                imageVector = LocalIcons.Stop,
                contentDescription = strings.stop,
                tint = NimayAccent,
                modifier = Modifier.size(16.dp)
            )
        } else if (canSend) {
            Icon(
                imageVector = LocalIcons.Send,
                contentDescription = strings.send,
                tint = Color(0xFF101314),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = LocalIcons.Send,
                contentDescription = strings.send,
                tint = NimayTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Kollapsbar verktøykall-row (forbedret versjon)
// ---------------------------------------------------------------------------

@Composable
fun ToolCallActivityCard(
    tool: ToolCallInfo,
    onDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var open by remember(tool.id) { mutableStateOf(false) }
    val running = tool.state == "running"
    val error = tool.isError || tool.state == "error"

    Surface(
        color = if (running) NimayAccent.copy(alpha = 0.06f) else NimayInput,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (running) NimayAccent.copy(alpha = 0.3f)
                else if (error) NimayError.copy(alpha = 0.4f)
                else NimayDivider,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (running) NimayAccent.copy(alpha = 0.2f) else NimayDivider)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = toolIcon(tool.name),
                        contentDescription = null,
                        tint = if (error) NimayError else if (running) NimayAccent else NimayTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolLabel(tool),
                        style = MaterialTheme.typography.labelMedium,
                        color = NimayTextPrimary,
                        maxLines = 1
                    )
                    val summary = toolSummary(tool)
                    if (summary.isNotBlank() && !open) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                when {
                    running -> TypingDots(NimayAccent)
                    error -> Icon(
                        imageVector = LocalIcons.Error,
                        contentDescription = null,
                        tint = NimayError,
                        modifier = Modifier.size(16.dp)
                    )
                    else -> Icon(
                        imageVector = LocalIcons.Check,
                        contentDescription = null,
                        tint = NimayActive,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (onDetails != null || tool.result.isNotBlank() || tool.args.isNotBlank()) {
                    Icon(
                        imageVector = if (open) LocalIcons.ExpandLess else LocalIcons.ExpandMore,
                        contentDescription = null,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Detaljer (utvides)
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.padding(top = 8.dp, start = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tool.args.isNotBlank()) {
                        SectionLabel(strings.args)
                        SelectionContainer {
                            Text(
                                text = pretty(tool.args),
                                style = MonoStyle,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NimayInput)
                                    .border(1.dp, NimayDivider, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                    if (tool.result.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel(strings.output)
                        SelectionContainer {
                            Text(
                                text = clamp(tool.result),
                                style = MonoStyle,
                                color = if (error) scheme.error else scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NimayInput)
                                    .border(1.dp, NimayDivider, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (onDetails != null) {
                            TextButton(
                                onClick = onDetails,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = strings.showDetails,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NimayAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when {
    name.contains("bash") || name.contains("shell") || name.contains("terminal") -> OmpIcons.Terminal
    name.contains("write") || name.contains("edit") || name.contains("patch") -> OmpIcons.Pencil
    name.contains("read") || name.contains("grep") || name.contains("search") -> LocalIcons.Search
    name.contains("list") || name.contains("todo") -> OmpIcons.Checklist
    name.contains("web") || name.contains("search") -> OmpIcons.Sparkle
    name.contains("mcp") -> OmpIcons.Sparkle
    name.contains("test") || name.contains("build") -> LocalIcons.Play
    else -> OmpIcons.Sparkle
}

private fun toolLabel(tool: ToolCallInfo): String {
    val name = tool.name.ifBlank { "verktøy" }
    if (tool.intent.isNotBlank()) return "$name · ${tool.intent.take(60)}"
    return name
}

private fun toolSummary(tool: ToolCallInfo): String {
    if (tool.args.isBlank()) return ""
    return try {
        val json = JsonParser.parseString(tool.args)
        if (!json.isJsonObject) return tool.args.take(100)
        val obj = json.asJsonObject
        when {
            obj.has("command") -> obj.str("command")
            obj.has("path") -> obj.str("path")
            obj.has("file_path") -> obj.str("file_path")
            obj.has("query") -> obj.str("query")
            obj.has("url") -> obj.str("url")
            obj.has("pattern") -> obj.str("pattern")
            else -> tool.args.take(100)
        }
    } catch (error: Exception) {
        tool.args.take(100)
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
    if (text.length > 5000) text.take(5000) + "\n… (avkortet)" else text

// ---------------------------------------------------------------------------
// Click without ripple
// ---------------------------------------------------------------------------

@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(
        clickable(
            enabled = enabled,
            indication = null,
            interactionSource = interaction,
            onClick = onClick
        )
    )
}
