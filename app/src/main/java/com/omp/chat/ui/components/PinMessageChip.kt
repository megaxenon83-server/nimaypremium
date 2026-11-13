package com.omp.chat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.omp.chat.data.ChatMessage
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.Strings

/**
 * Diskret pinne-knapp for chat-meldinger.
 * Viser et lite pushpin-ikon uten tekst. Ved trykk åpnes en liste med
 * alle pinnede meldinger som kan åpnes eller opp-pinnes.
 *
 * Popup rendres via Compose Popup (eget overlay-vindu), så den ikke påvirker
 * header-radens måling — chippen holder 32dp og flytter seg ikke når menyen åpnes.
 *
 * [pinnedMessageIds] er settet med ID-er til pinnede meldinger.
 * [messages] er alle meldinger i samtalen (for å slå opp tekst av id).
 * [onPinToggled] kalles med messageId når brukeren bytter pin-status.
 * [onPinnedMessageClicked] kalles med messageId når brukeren velger en pinnet melding.
 * [onToast] kalles med bekreftelsesmelding.
 */
@Composable
fun PinMessageChip(
    strings: Strings,
    pinnedMessageIds: Set<String>,
    messages: List<ChatMessage>,
    onPinToggled: (String) -> Unit = {},
    onPinnedMessageClicked: (String) -> Unit = {},
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(false) }

    val pinnedMessages = messages.filter { pinnedMessageIds.contains(it.id) }
    val latestMessageId = messages.lastOrNull()?.id ?: ""
    val isLatestPinned = pinnedMessageIds.contains(latestMessageId)

    Box(modifier = modifier) {
        // Diskret ikon-knapp (32dp, fast størrelse)
        Surface(
            onClick = { showPopup = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = strings.pinMessage,
                    tint = if (pinnedMessageIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Pinnede meldinger liste i eget overlay-vindu
        if (showPopup) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.pinnedMessages,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )

                        if (pinnedMessages.isEmpty()) {
                            Text(
                                text = "Ingen heftede meldinger",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        } else {
                            pinnedMessages.forEach { msg ->
                                PinnedMessageRow(
                                    message = msg,
                                    onClick = { onPinnedMessageClicked(msg.id) },
                                    onUnpin = { onPinToggled(msg.id); showPopup = false }
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
 * En rad i listen over pinnede meldinger.
 * Viser en kort forhåndsvisning av teksten og en opp-pinn-knapp.
 */
@Composable
private fun PinnedMessageRow(
    message: ChatMessage,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    val strings = LocalStrings.current
    val preview = message.text.take(60).ifBlank { "[ingen tekst]" }
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (message.isUser) "Bruker" else "Assistent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 9.sp
            )
        }
        Icon(
            Icons.Default.PushPin,
            contentDescription = strings.unpinMessage,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}