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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.RepoState
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary

enum class CleanupFilter { SHORT, EMPTY, ALL }

@Composable
fun CleanupScreen(
    state: RepoState,
    onBack: () -> Unit,
    onOpenSession: (SessionSummary) -> Unit,
    onArchiveSelected: (List<String>) -> Unit,
    onTrashSelected: (List<String>) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var filter by remember { mutableStateOf(CleanupFilter.SHORT) }
    var projectFilter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val candidates = remember(state.sessions, filter, projectFilter) {
        state.sessions.filter { s ->
            val projectOk = projectFilter.isBlank() || s.cwd.equals(projectFilter, true)
            val filterOk = when (filter) {
                CleanupFilter.SHORT -> s.messageCount in 1..10
                CleanupFilter.EMPTY -> s.messageCount == 0
                CleanupFilter.ALL -> true
            }
            projectOk && filterOk
        }
    }

    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary) }
            Text("Rydd opp i samtaler", style = MaterialTheme.typography.titleMedium, color = VariantColors.textPrimary, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Korte", filter == CleanupFilter.SHORT) { filter = CleanupFilter.SHORT }
            FilterChip("Tomme", filter == CleanupFilter.EMPTY) { filter = CleanupFilter.EMPTY }
            FilterChip("Alle", filter == CleanupFilter.ALL) { filter = CleanupFilter.ALL }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenArchived) { Text("Arkiv") }
            OutlinedButton(onClick = onOpenTrash) { Text("Papirkurv") }
        }
        Text("${candidates.size} samtaler funnet, ${selected.size} valgt", color = VariantColors.textSecondary, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(candidates, key = { it.file }) { s ->
                Surface(color = VariantColors.surfaceElevated, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected.contains(s.file), onCheckedChange = { checked ->
                            selected = if (checked) selected + s.file else selected - s.file
                        })
                        Column(Modifier.weight(1f).clickable { onOpenSession(s) }) {
                            Text(s.title, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.cwd} · ${s.messageCount} meldinger", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onArchiveSelected(selected.toList()); selected = emptySet() }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Arkiver")
            }
            Button(onClick = { onTrashSelected(selected.toList()); selected = emptySet() }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Flytt til papirkurv")
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) VariantColors.accent.copy(alpha = 0.2f) else VariantColors.surfaceElevated,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = if (selected) VariantColors.accent else VariantColors.textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
fun ArchivedScreen(state: RepoState, onBack: () -> Unit, onOpenSession: (SessionSummary) -> Unit, onRestore: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary) }
            Text("Arkiverte samtaler", style = MaterialTheme.typography.titleMedium, color = VariantColors.textPrimary)
        }
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.archivedSessions) { s ->
                Surface(color = VariantColors.surfaceElevated, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onOpenSession(s) }) {
                            Text(s.title, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.cwd, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { onRestore(s.file) }) { Text("Gjenopprett") }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashScreen(
    items: List<com.omp.chat.data.TrashItem>,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onOpen: (com.omp.chat.data.TrashItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary) }
            Text("Papirkurv", style = MaterialTheme.typography.titleMedium, color = VariantColors.textPrimary)
        }
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.file }) { t ->
                Surface(color = VariantColors.surfaceElevated, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onOpen(t) }) {
                            Text(t.title, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.cwd} · ${t.messageCount} meldinger", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { onRestore(t.file) }) { Text("Gjenopprett") }
                    }
                }
            }
        }
    }
}