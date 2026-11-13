package com.omp.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.omp.chat.ui.Strings

/**
 * Diskret eksporter-knapp for chat-header.
 * Viser et lite ikon uten tekst. Ved trykk vises en meny for å kopiere
 * siste N chats til utklipp.
 *
 * Popup rendres via Compose Popup (eget overlay-vindu), så den ikke påvirker
 * header-radens måling — chippen holder 32dp og flytter seg ikke når menyen åpnes.
 *
 * [onExport] kalles med antall chats som skal kopieres (3, 5 eller 10).
 * Kalleren er ansvarlig for å hente meldingene og kopiere til utklipp.
 */
@Composable
fun ExportChatChip(
    strings: Strings,
    onExport: (Int) -> Unit = {},
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Diskret ikon-knapp (32dp, fast størrelse)
        Surface(
            onClick = { showMenu = !showMenu },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = strings.exportChat,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Eksport-meny i eget overlay-vindu - påvirker ikke header-layout
        if (showMenu) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(200.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportMenuItem(
                            strings = strings,
                            label = strings.exportLast3,
                            count = 3,
                            onExport = onExport,
                            onMenuDismiss = { showMenu = false },
                            onToast = onToast
                        )
                        ExportMenuItem(
                            strings = strings,
                            label = strings.exportLast5,
                            count = 5,
                            onExport = onExport,
                            onMenuDismiss = { showMenu = false },
                            onToast = onToast
                        )
                        ExportMenuItem(
                            strings = strings,
                            label = strings.exportLast10,
                            count = 10,
                            onExport = onExport,
                            onMenuDismiss = { showMenu = false },
                            onToast = onToast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportMenuItem(
    strings: Strings,
    label: String,
    count: Int,
    onExport: (Int) -> Unit,
    onMenuDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onMenuDismiss()
                onExport(count)
            }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Kopierer siste [count] chat-meldinger til utklipp.
 * Returnerer formatted tekst som ble kopiert, eller tom streng hvis ingen meldinger.
 */
fun copyLastNChatsToClipboard(
    context: Context,
    messages: List<com.omp.chat.data.ChatMessage>,
    count: Int
): String {
    if (messages.isEmpty()) return ""

    val lastN = messages.takeLast(count)
    val sb = StringBuilder()
    for ((index, msg) in lastN.withIndex()) {
        val role = if (msg.isUser) "Bruker" else "Assistent"
        sb.append("=== $role ===\n")
        sb.append(msg.text)
        sb.append("\n\n")
    }

    val text = sb.toString().trimEnd()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OmpChat-eksport", text)
    clipboard.setPrimaryClip(clip)

    return text
}