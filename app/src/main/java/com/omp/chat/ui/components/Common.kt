package com.omp.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Round mark used for the assistant — sleek, minimalist AI emblem. */
@Composable
fun OmpAvatar(size: Dp = 24.dp, letter: String = "O", muted: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (muted) scheme.surfaceContainerHigh else NimayAccent.copy(alpha = 0.18f))
            .border(1.dp, if (muted) NimayDivider else NimayAccent.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = OmpIcons.Sparkle,
            contentDescription = null,
            tint = if (muted) NimayTextSecondary else NimayAccent,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun StatusDot(online: Boolean, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (online) NimayAccent else Color(0xFFEF4444))
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val background = if (selected) NimayAccent.copy(alpha = 0.2f) else NimayInput
    val content = if (selected) NimayAccent else NimayTextSecondary
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() } else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(text, color = content, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.1.sp,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** Short, human time label; follows the app language. */
fun relativeTime(timestamp: Long, lang: String = "nb"): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> if (lang == "en") "now" else "nå"
        diff < 3_600_000 -> "${diff / 60_000} min"
        diff < 86_400_000 -> "${diff / 3_600_000}${if (lang == "en") "h" else "t"}"
        else -> {
            val date = Date(timestamp)
            val today = Calendar.getInstance()
            val then = Calendar.getInstance().apply { time = date }
            val locale = Locale.forLanguageTag(if (lang == "en") "en" else "nb")
            if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                SimpleDateFormat("d. MMM", locale).format(date)
            } else {
                SimpleDateFormat("d. MMM yyyy", locale).format(date)
            }
        }
    }
}

fun shortModel(model: String): String {
    if (model.isBlank()) return ""
    return model.substringAfterLast('/')
}

fun shortNumber(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

fun fileName(path: String): String =
    path.substringAfterLast('\\').substringAfterLast('/').ifBlank { path }
