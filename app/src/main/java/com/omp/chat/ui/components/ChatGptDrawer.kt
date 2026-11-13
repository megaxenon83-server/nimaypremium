package com.omp.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.data.ActiveBridgeSession
import com.omp.chat.data.CwdPath
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.RepoState
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimaySidebar
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import com.omp.chat.ui.theme.NimayUserBubble
import kotlinx.coroutines.delay

@Composable
fun ChatGptDrawerContent(
    state: RepoState,
    currentSessionFile: String,
    pcName: String,
    onOpenSession: (SessionSummary) -> Unit,
    onNewChat: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenModelPicker: () -> Unit,
    onRename: (SessionSummary) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onDelete: (SessionSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenPcs: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenGit: () -> Unit,
    onReconnect: () -> Unit,
    onOpenActiveChat: () -> Unit,
    onOpenCleanup: () -> Unit,
    onOpenProjectHistory: (String) -> Unit,
    loadActiveBridge: suspend () -> ActiveBridgeSession?,
    animatedActive: Boolean,
    onToggleFavorite: (String) -> Unit,
    isFavorite: (String) -> Boolean,
    onCloseDrawer: () -> Unit
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var showAllProjects by remember { mutableStateOf(false) }
    val expandedProjects = remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    val activeBridge = state.activeBridgeSession

    val recentSessions = remember(state.sessions) { state.sessions.take(5) }
    val favoriteSessions = remember(state.sessions) { state.sessions.filter { isFavorite(it.file) } }
    val searchResults = remember(state.sessions, query) {
        if (query.isBlank()) emptyList()
        else state.sessions.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.cwd.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(NimaySidebar)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ------------------------------------------------------------- Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // New chat
            Surface(
                color = NimayInput,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onNewChat(state.cwd)
                        onCloseDrawer()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        OmpIcons.NewChat,
                        contentDescription = null,
                        tint = NimayTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        strings.newChat,
                        style = MaterialTheme.typography.labelLarge,
                        color = NimayTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // PC-velger
            Surface(
                color = NimayInput,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPcs(); onCloseDrawer() }
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusDotSmall(online = state.connected)
                    Column(Modifier.weight(1f)) {
                        Text(
                            pcName,
                            style = MaterialTheme.typography.labelMedium,
                            color = NimayTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (state.connected) strings.connected else strings.disconnected,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        OmpIcons.ChevronDown,
                        contentDescription = null,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Aktiv i Bridge
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (activeBridge != null) {
                            onOpenActiveChat()
                            onCloseDrawer()
                        }
                    }
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (activeBridge?.source == "omp-tui") "Aktiv pÃ¥ PC-en" else "Aktiv i Bridge",
                        style = MaterialTheme.typography.labelSmall,
                        color = NimayAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (activeBridge != null) {
                        val sess = state.sessions.firstOrNull { it.file == activeBridge!!.sessionFile }
                        Text(
                            sess?.title?.ifBlank { activeBridge!!.sessionId } ?: activeBridge!!.sessionId,
                            style = MaterialTheme.typography.labelMedium,
                            color = NimayTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            sess?.cwd ?: activeBridge!!.cwd,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            "Ingen aktiv Bridge-samtale",
                            style = MaterialTheme.typography.labelSmall,
                            color = NimayTextSecondary
                        )
                    }
                }
            }

            // SÃ¸k
            Surface(
                color = NimayInput,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        OmpIcons.Search,
                        contentDescription = null,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimayTextPrimary),
                        cursorBrush = SolidColor(NimayAccent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    strings.searchChats,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NimayTextSecondary
                                )
                            }
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = NimayTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = NimayDivider, thickness = 1.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (query.isNotBlank()) {
                items(searchResults, key = { it.file }) { session ->
                    SessionRowCompact(session, currentSessionFile, state.activeBridgeSession?.sessionFile, animatedActive, session.file == currentSessionFile && state.streaming, onOpenSession, onCloseDrawer, onToggleFavorite, isFavorite)
                }
                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            strings.noResults,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NimayTextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                // Nylige samtaler
                item { SectionHeader("Nylige samtaler") }
                items(recentSessions, key = { it.file }) { session ->
                    SessionRowCompact(session, currentSessionFile, state.activeBridgeSession?.sessionFile, animatedActive, session.file == currentSessionFile && state.streaming, onOpenSession, onCloseDrawer, onToggleFavorite, isFavorite)
                }

                // Favoritter
                if (favoriteSessions.isNotEmpty()) {
                    item { SectionHeader("Favoritter") }
                    items(favoriteSessions, key = { it.file }) { session ->
                        SessionRowCompact(session, currentSessionFile, state.activeBridgeSession?.sessionFile, animatedActive, session.file == currentSessionFile && state.streaming, onOpenSession, onCloseDrawer, onToggleFavorite, isFavorite)
                    }
                }

                // Prosjekter
                item { SectionHeader("Prosjekter") }
                items(state.projects, key = { it.path }) { project ->
                    val expanded = expandedProjects.value.contains(project.path)
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    expandedProjects.value = if (expanded) {
                                        expandedProjects.value - project.path
                                    } else {
                                        expandedProjects.value + project.path
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (expanded) OmpIcons.ChevronDown else OmpIcons.ChevronRight,
                                contentDescription = null,
                                tint = NimayTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Icon(
                                OmpIcons.Folder,
                                contentDescription = null,
                                tint = if (project.exists) NimayAccent else NimayTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                project.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = NimayTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (project.sessionCount > 0) {
                                Text(
                                    "${project.sessionCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NimayTextSecondary
                                )
                            }
                        }
                        if (expanded) {
                            val projectSessions = state.sessions
                                .filter { CwdPath.same(it.cwd, project.path) }
                                .take(5)
                            Column(Modifier.padding(start = 28.dp)) {
                                projectSessions.forEach { session ->
                                    SessionRowCompact(session, currentSessionFile, state.activeBridgeSession?.sessionFile, animatedActive, session.file == currentSessionFile && state.streaming, onOpenSession, onCloseDrawer, onToggleFavorite, isFavorite)
                                }
                                if (project.sessionCount > 5) {
                                    Text(
                                        "Vis ${project.sessionCount - 5} eldre samtaler",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NimayAccent,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp, horizontal = 8.dp)
                                            .clickable {
                                                onOpenProjectHistory(project.path)
                                                onCloseDrawer()
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.projects.isEmpty()) {
                    item {
                        Text(
                            strings.noProjects,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NimayTextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // Rydd opp
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenCleanup(); onCloseDrawer() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Rydd opp i samtaler", color = NimayTextSecondary, style = MaterialTheme.typography.labelMedium)
        }

        // ------------------------------------------------------------- Bottom
        HorizontalDivider(color = NimayDivider, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomIcon(Icons.Default.Settings, strings.settings, onOpenSettings)
            BottomIcon(OmpIcons.Folder, strings.fileManagerTitle, onOpenFiles)
            BottomIcon(Icons.Default.Computer, "PC-er", onOpenPcs)
            BottomIcon(Icons.Default.List, "Oppgaver", onOpenTasks)
            BottomIcon(OmpIcons.Terminal, strings.terminal, onOpenTerminal)
            BottomIcon(OmpIcons.Git, "Git", onOpenGit)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = NimayTextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun SessionRowCompact(
    session: SessionSummary,
    currentSessionFile: String,
    activeBridgeFile: String?,
    animatedActive: Boolean,
    isWorking: Boolean,
    onOpenSession: (SessionSummary) -> Unit,
    onCloseDrawer: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val isActive = session.file == currentSessionFile
    Surface(
        color = if (isActive) NimayInput else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                onOpenSession(session)
                onCloseDrawer()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { onToggleFavorite(session.file) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite(session.file)) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (isFavorite(session.file)) NimayAccent else NimayTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (session.file == activeBridgeFile) {
                ActiveSessionIndicator(animated = animatedActive, working = isWorking)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { session.file.substringAfterLast('/') },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) NimayTextPrimary else NimayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = CwdPath.displayName(session.cwd).ifBlank { session.cwd },
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BottomIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = NimayTextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatusDotSmall(online: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(if (online) NimayAccent else NimayTextSecondary, CircleShape)
    )
}

@Composable
private fun ActiveSessionIndicator(animated: Boolean, working: Boolean) {
    val angle = if (animated) {
        val transition = rememberInfiniteTransition(label = "live")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (working) 1400 else 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        ).value
    } else {
        0f
    }
    val colors = listOf(
        Color(0xFF007AFF),
        Color(0xFF5AC8FA),
        Color(0xFFBF5AF2),
        Color(0xFF007AFF)
    )
    val brush = if (animated) Brush.sweepGradient(colors) else SolidColor(NimayAccent)
    Box(
        modifier = Modifier
            .size(12.dp)
            .rotate(angle)
            .drawBehind {
                drawCircle(
                    brush = brush,
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
    )
}
