package com.omp.chat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.omp.chat.data.PcResources

/**
 * Diskret ressursmonitor-knapp for chat-header.
 * Viser et lite ikon uten tekst. Ved trykk vises popup med CPU/RAM/GPU.
 *
 * Popup rendres via Compose Popup (eget overlay-vindu), så den ikke påvirker
 * header-radens måling — chippen holder 32dp og flytter seg ikke når popup åpnes.
 * Popup-innholdet rekomponeres når [resources] endres, så ekte data vises så snart
 * de er hentet fra bridgen.
 *
 * Sett [DEBUG] til true for midlertidig feilsøkningslogging i logcat (tag "OmpChat").
 */
private const val DEBUG = false

@Composable
fun ResourceMonitorChip(
    resources: PcResources?,
    enabled: Boolean,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var showPopup by remember { mutableStateOf(false) }
    var lastResources by remember { mutableStateOf<PcResources?>(null) }

    if (DEBUG && lastResources != resources) {
        android.util.Log.d("OmpChat", "RESOURCEMONITOR resources changed: null=${resources == null}")
        lastResources = resources
    }

    Box(modifier = modifier) {
        // Diskret ikon-knapp (32dp, fast størrelse)
        Surface(
            onClick = {
                val opening = !showPopup
                showPopup = opening
                if (DEBUG) {
                    android.util.Log.d("OmpChat", "RESOURCEMONITOR tap showPopup=$opening resources=null=${resources == null}")
                }
                if (opening) onRefresh()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "PC-ressurser",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Popup med detaljer i eget overlay-vindu - påvirker ikke header-layout
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
                    modifier = Modifier.width(200.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (resources == null) {
                            Text("Laster ressurser...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ResourceRow("CPU", resources.cpuPercent, "${resources.cpuCores} kjerner")
                            ResourceRow("RAM", resources.ramPercent, "${formatBytes(resources.ramUsedBytes)} / ${formatBytes(resources.ramTotalBytes)}")
                            if (resources.gpuName.isNotBlank()) {
                                ResourceRow("GPU", resources.gpuPercent, resources.gpuName.take(20))
                            }
                            if (resources.gpuMemoryTotalBytes > 0) {
                                Text(
                                    "GPU-minne: ${formatBytes(resources.gpuMemoryUsedBytes)} / ${formatBytes(resources.gpuMemoryTotalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                "Oppdatert: ${formatTime(resources.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(label: String, percent: Int, detail: String) {
    val color = when {
        percent < 60 -> Color(0xFF4CAF50)
        percent < 85 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("$percent%", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024))
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}