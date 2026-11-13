package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.TaskInfo
import com.omp.chat.data.TaskState
import com.omp.chat.data.TaskStore
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.online
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.surface
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import kotlinx.coroutines.delay

@Composable
fun TasksScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val strings = LocalStrings.current
    val activePcId by viewModel.activePcId.collectAsState()
    val tasks = viewModel.tasks
    var list by remember { mutableStateOf(tasks.tasksFor(activePcId.ifBlank { "default" })) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(activePcId) {
        val pcId = activePcId.ifBlank { "default" }
        list = tasks.tasksFor(pcId)
        val listener: (String, List<com.omp.chat.data.TaskInfo>) -> Unit = { id, updated ->
            if (id == pcId) list = updated
        }
        tasks.addListener(listener)
        while (true) {
            delay(2000)
            list = tasks.tasksFor(pcId)
        }
    }

    val running = list.filter { it.state == TaskState.RUNNING }
    val done = list.filter { it.state == TaskState.DONE }
    val failed = list.filter { it.state == TaskState.FAILED || it.state == TaskState.CANCELLED }

    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary)
            }
            Text("Oppgaver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VariantColors.textPrimary, modifier = Modifier.weight(1f))
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Ingen oppgaver ennå.", color = VariantColors.textSecondary)
                Text("Start en samtale på PC-en for å se aktivitet her.", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (running.isNotEmpty()) {
                    item { SectionHeader("Aktive (${running.size})") }
                    items(running, key = { it.id }) { task ->
                        TaskCard(task, viewModel, expanded.contains(task.id), {
                            expanded = if (expanded.contains(task.id)) expanded - task.id else expanded + task.id
                        }, onOpenSession)
                    }
                }
                if (done.isNotEmpty()) {
                    item { SectionHeader("Fullførte (${done.size})") }
                    items(done, key = { it.id }) { task ->
                        TaskCard(task, viewModel, expanded.contains(task.id), {
                            expanded = if (expanded.contains(task.id)) expanded - task.id else expanded + task.id
                        }, onOpenSession)
                    }
                }
                if (failed.isNotEmpty()) {
                    item { SectionHeader("Feilede/avbrutte (${failed.size})") }
                    items(failed, key = { it.id }) { task ->
                        TaskCard(task, viewModel, expanded.contains(task.id), {
                            expanded = if (expanded.contains(task.id)) expanded - task.id else expanded + task.id
                        }, onOpenSession)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun TaskCard(task: TaskInfo, viewModel: AppViewModel, expanded: Boolean, onToggle: () -> Unit, onOpenSession: (String) -> Unit) {
    val icon = when (task.state) {
        TaskState.RUNNING -> Icons.Default.PlayArrow
        TaskState.DONE -> Icons.Default.Done
        TaskState.FAILED -> Icons.Default.Warning
        TaskState.CANCELLED -> Icons.Default.Close
        TaskState.UNKNOWN -> Icons.Default.Warning
    }
    val color = when (task.state) {
        TaskState.RUNNING -> VariantColors.accent
        TaskState.DONE -> VariantColors.online
        TaskState.FAILED -> VariantColors.error
        TaskState.CANCELLED -> VariantColors.textSecondary
        TaskState.UNKNOWN -> VariantColors.textSecondary
    }
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp), color = VariantColors.surface) {
        Column(Modifier.clickable { onToggle() }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(task.sessionTitle.ifBlank { "Sesjon" }, color = VariantColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (task.modelName.isNotBlank()) {
                    Text(task.modelName, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            val statusLabel = when (task.state) {
                TaskState.RUNNING -> "Kjører"
                TaskState.DONE -> "Fullført"
                TaskState.FAILED -> "Feilet"
                TaskState.CANCELLED -> "Avbrutt"
                TaskState.UNKNOWN -> "Mistet tilkobling"
            }
            Text(statusLabel, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            if (task.lastActivity.isNotBlank()) {
                Text(task.lastActivity, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val elapsed = if (task.endedAt > 0) task.endedAt - task.startedAt else System.currentTimeMillis() - task.startedAt
            Text("Tid: ${elapsed / 1000}s  ·  Verktøy: ${task.tools.size}  ·  Filer: ${task.filesChanged.size}", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
            if (task.error.isNotBlank()) {
                Text(task.error, color = VariantColors.error, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                if (task.tools.isNotEmpty()) {
                    Text("Verktøykall:", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    task.tools.forEach { tc ->
                        Text("• ${tc.name}${if (tc.isError) " (feil)" else ""}", color = if (tc.isError) VariantColors.error else VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (task.filesChanged.isNotEmpty()) {
                    Text("Filer:", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    task.filesChanged.forEach { f ->
                        Text("• $f", color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (task.activityLog.isNotEmpty()) {
                    Text("Aktivitet:", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    task.activityLog.takeLast(8).forEach { line ->
                        Text(line, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val isActiveSession = task.sessionId.isNotBlank() && task.sessionId == viewModel.repository.state.value.sessionFile
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.state == TaskState.RUNNING && isActiveSession) {
                        TextButton(onClick = {
                            viewModel.repository.stop()
                            viewModel.tasks.cancel(viewModel.activePcId.value.ifBlank { "default" }, task.sessionId)
                        }) {
                            Text("Stopp", color = VariantColors.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    task.pendingApproval?.let { req ->
                        TextButton(onClick = { viewModel.repository.answer(req, confirmed = true) }) {
                            Text("Godkjenn", color = VariantColors.online, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { viewModel.repository.answer(req, confirmed = false) }) {
                            Text("Avvis", color = VariantColors.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = { onOpenSession(task.sessionId) }) {
                        Text("Åpne", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { content() }
}
