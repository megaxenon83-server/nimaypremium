package com.omp.chat.ui.chstgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Compact 40dp icon target used throughout the ChatGPT-like chrome. */
@Composable
fun GptIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalChatGptColors.current.textPrimary,
    size: Int = 40,
    iconSize: Int = 22,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Primary circular action, matching the white/black send button of ChatGPT. */
@Composable
fun GptPrimaryCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Int = 36,
    iconSize: Int = 19,
    background: Color? = null,
    tint: Color? = null
) {
    val c = LocalChatGptColors.current
    val bg = background ?: if (enabled) c.textPrimary else c.surface
    val fg = tint ?: if (enabled) c.background else c.textTertiary
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Pill-shaped selector used for model/theme/language pickers. */
@Composable
fun GptPill(
    text: String,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalChatGptColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.surface else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else c.divider,
                shape = RoundedCornerShape(50)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.accent else c.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
fun GptSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val c = LocalChatGptColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.divider, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(18.dp)
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = c.textTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            GptIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Tøm",
                onClick = { onValueChange("") },
                size = 32,
                iconSize = 16,
                tint = c.textSecondary
            )
        }
    }
}

@Composable
fun GptDivider(modifier: Modifier = Modifier, inset: Int = 0) {
    Box(
        modifier
            .padding(horizontal = inset.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalChatGptColors.current.divider)
    )
}

/** Three-dot "working" pulse used instead of invented progress percentages. */
@Composable
fun GptWorkingDots(color: Color = LocalChatGptColors.current.textSecondary) {
    val transition = rememberInfiniteTransition(label = "working")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "workingPhase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val active = phase.toInt() % 3 == index
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 1f else 0.35f))
            )
        }
    }
}

fun Context.copyText(text: String, label: String = "Nimay OMP") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun rememberCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text -> context.copyText(text) }
    }
}

fun formatChatTime(millis: Long): String {
    if (millis <= 0) return ""
    val date = Date(millis)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val pattern = if (sameYear) "d. MMM" else "d. MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

fun dayBucket(millis: Long): Int {
    if (millis <= 0) return 3
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    fun startOfDay(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }
    val days = ((startOfDay(now) - startOfDay(then)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> 0
        days == 1 -> 1
        days <= 7 -> 2
        else -> 3
    }
}

fun shortModelName(name: String): String =
    name.substringAfterLast('/').takeIf { it.isNotBlank() } ?: name

fun compactNumber(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

/** Brand mark: our own simple circular glyph, not the OpenAI/ChatGPT logo. */
@Composable
fun NimayMark(size: Int = 30, modifier: Modifier = Modifier) {
    val c = LocalChatGptColors.current
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(c.textPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "N",
            color = c.background,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.52f).sp
        )
    }
}
