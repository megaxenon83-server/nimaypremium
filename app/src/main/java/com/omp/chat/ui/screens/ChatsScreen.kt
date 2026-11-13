package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import com.omp.chat.ui.theme.VariantColors.userBubble
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.CwdPath
import com.omp.chat.data.FolderListing
import com.omp.chat.data.Project
import com.omp.chat.data.RepoState
import com.omp.chat.data.SessionSummary
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.OmpAvatar
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.components.SectionLabel
import com.omp.chat.ui.components.StatusDot
import com.omp.chat.ui.components.fileName
import com.omp.chat.ui.components.relativeTime
import com.omp.chat.ui.components.shortModel
import java.util.Calendar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    state: RepoState,
    onOpenSession: (SessionSummary) -> Unit,
    onNewChat: (String) -> Unit,
    onRename: (SessionSummary) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onDelete: (SessionSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit = {},
onOpenPcs: () -> Unit = {},
onOpenTasks: () -> Unit = {},
onOpenTerminal: () -> Unit = {},
onOpenGit: () -> Unit = {},
    onRefresh: () -> Unit,
    onListFolders: suspend (String) -> FolderListing? = { null }
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var projectFilter by remember { mutableStateOf("") }
    var projectSheet by remember { mutableStateOf(false) }
    var browseSheet by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    val sessions = remember(state.sessions, state.archivedSessions, query, projectFilter) {
        state.sessions.filter { session ->
            (projectFilter.isBlank() || session.cwd.equals(projectFilter, true)) &&
                (query.isBlank() || session.title.contains(query, true) || session.cwd.contains(query, true))
        }
    }
    val archived = remember(state.archivedSessions, query) {
        state.archivedSessions.filter { query.isBlank() || it.title.contains(query, true) }
    }
    val currentProject = remember(state.projects, projectFilter, state.cwd) {
        state.projects.firstOrNull { it.path.equals(projectFilter, true) }
            ?: state.projects.firstOrNull { it.path.equals(state.cwd, true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // -------------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    strings.chats,
                    style = MaterialTheme.typography.titleLarge,
                    color = VariantColors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online = state.connected, size = 6.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            state.connecting -> strings.connecting
                            state.connected -> state.error.ifBlank { strings.connected }
                            else -> strings.disconnected
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { searching = !searching }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = strings.search,
                    tint = VariantColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = strings.refresh,
                    tint = VariantColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenFiles, modifier = Modifier.size(40.dp)) {
                Icon(
                    OmpIcons.Folder,
                    contentDescription = LocalStrings.current.fileManagerTitle,
                    tint = VariantColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = strings.settings,
                    tint = VariantColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (searching) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchChats, style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VariantColors.surfaceElevated,
                    unfocusedContainerColor = VariantColors.surfaceElevated,
                    focusedTextColor = VariantColors.textPrimary,
                    unfocusedTextColor = VariantColors.textPrimary,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .border(1.dp, VariantColors.divider, RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.height(6.dp))
        }

        // ------------------------------------------------------------ new chat
        Surface(
            color = VariantColors.surfaceElevated,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .border(1.dp, VariantColors.divider, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    val target = projectFilter.ifBlank { currentProject?.path ?: "" }
                    if (target.isBlank()) projectSheet = true else onNewChat(target)
                }
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    OmpIcons.NewChat,
                    contentDescription = null,
                    tint = VariantColors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.newChat,
                        style = MaterialTheme.typography.labelLarge,
                        color = VariantColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentProject?.let { fileName(it.path) } ?: strings.selectProject,
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = VariantColors.userBubble,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { projectSheet = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            OmpIcons.Folder,
                            contentDescription = null,
                            tint = VariantColors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            strings.project,
                            style = MaterialTheme.typography.labelSmall,
                            color = VariantColors.textSecondary
                        )
                    }
                }
            }
        }
        // ----------------------------------------------------------- projects
        if (state.projects.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ProjectChip(
                        label = strings.allProjects,
                        selected = projectFilter.isBlank(),
                        onClick = { projectFilter = "" }
                    )
                }
                items(state.projects.take(12), key = { it.path }) { project ->
                    ProjectChip(
                        label = project.name,
                        selected = projectFilter.equals(project.path, true),
                        onClick = {
                            projectFilter = if (projectFilter.equals(project.path, true)) "" else project.path
                        }
                    )
                }
            }
        }

        // ------------------------------------------------------------ sessions
        if (sessions.isEmpty() && archived.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OmpAvatar(size = 36.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (query.isBlank()) strings.noChats else strings.noResults,
                    style = MaterialTheme.typography.titleMedium,
                    color = VariantColors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.noChatsHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VariantColors.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 26.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val groups = groupSessions(sessions, strings)
                groups.forEach { (label, group) ->
                    item(key = "header-$label") {
                        SectionLabel(label, Modifier.padding(start = 8.dp, top = 10.dp))
                    }
                    items(group, key = { it.file }) { session ->
                        SessionRow(
                            session = session,
                            onClick = { onOpenSession(session) },
                            onRename = { onRename(session) },
                            onArchive = { onArchive(session, true) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }

                if (archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showArchived = !showArchived }
                                .padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("${strings.archived} · ${archived.size}")
                            Spacer(Modifier.weight(1f))
                            Icon(
                                OmpIcons.ChevronDown,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showArchived) {
                        items(archived, key = { "arch-" + it.file }) { session ->
                            SessionRow(
                                session = session,
                                onClick = { onOpenSession(session) },
                                onRename = { onRename(session) },
                                onArchive = { onArchive(session, false) },
                                onDelete = { onDelete(session) },
                                archived = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (projectSheet) {
        ProjectSheet(
            projects = state.projects,
            current = projectFilter,
            onPick = { project ->
                projectSheet = false
                projectFilter = project.path
                onNewChat(project.path)
            },
            onBrowse = {
                projectSheet = false
                browseSheet = true
            },
            onDismiss = { projectSheet = false }
        )
    }

    if (browseSheet) {
        FolderBrowserDialog(
            initialPath = state.cwd.takeIf { CwdPath.isUsable(it) } ?: "",
            onList = onListFolders,
            onPick = { path ->
                browseSheet = false
                onNewChat(path)
            },
            onDismiss = { browseSheet = false }
        )
    }
}

@Composable
private fun ProjectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) VariantColors.accent.copy(alpha = 0.15f) else VariantColors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) VariantColors.accent else VariantColors.divider,
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) VariantColors.accent else VariantColors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    archived: Boolean = false
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(VariantColors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                OmpIcons.Chat,
                contentDescription = null,
                tint = VariantColors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.displayTitle(session.title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (archived) FontWeight.Normal else FontWeight.Medium,
                    color = if (archived) VariantColors.textSecondary else VariantColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (session.messageCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${shortModel(session.model)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val subtitle = buildList {
                add(relativeTime(session.updatedAt, strings.lang))
                add("${session.messageCount} ${strings.messages}")
                if (session.toolCount > 0) add("${session.toolCount} ${strings.tools}")
                if (session.cwd.isNotBlank() && session.messageCount == 0) add(fileName(session.cwd))
            }.filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = strings.chatMenu,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(strings.rename) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (archived) strings.unarchive else strings.archive) },
                    leadingIcon = { Icon(OmpIcons.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; onArchive() }
                )
                DropdownMenuItem(
                    text = { Text(strings.delete, color = scheme.error) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp))
                    },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSheet(
    projects: List<Project>,
    current: String,
    onPick: (Project) -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                strings.selectProject,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Text(
                strings.selectProjectHint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBrowse() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    OmpIcons.Folder,
                    contentDescription = null,
                    tint = VariantColors.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    strings.browseFolders,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VariantColors.accent
                )
            }
            if (projects.isEmpty()) {
                Text(
                    strings.noProjects,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(projects, key = { it.path }) { project ->                    val selected = current.equals(project.path, true)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) scheme.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(project) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            OmpIcons.Folder,
                            contentDescription = null,
                            tint = if (project.exists) scheme.primary else scheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                project.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (project.sessionCount > 0) {
                            Text(
                                "${project.sessionCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FolderBrowserDialog(
    initialPath: String,
    onList: suspend (String) -> FolderListing?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<FolderListing?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun open(target: String) {
        loading = true
        error = ""
        val result = runCatching { onList(target) }.getOrNull()
        if (result == null) {
            error = "Kunne ikke lese mappen på PC-en."
        } else {
            listing = result
            current = result.path
        }
        loading = false
    }

    LaunchedEffect(Unit) { open(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VariantColors.sidebar,
        title = { Text(strings.browseFolders, color = VariantColors.textPrimary) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = current.ifBlank { "…" },
                    style = MaterialTheme.typography.labelSmall,
                    color = VariantColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { scope.launch { open(listing?.parent ?: current) } },
                        enabled = !loading && !listing?.parent.isNullOrBlank()
                    ) {
                        Text(strings.parentFolder, style = MaterialTheme.typography.labelSmall)
                    }
                }
                when {
                    loading -> Text("…", color = VariantColors.textSecondary)
                    error.isNotBlank() -> Text(error, color = scheme.error, style = MaterialTheme.typography.labelSmall)
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val dirs = listing?.dirs.orEmpty()
                        if (dirs.isEmpty()) {
                            item {
                                Text(
                                    "Ingen undermapper",
                                    color = VariantColors.textSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        items(dirs, key = { it.path }) { dir ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { scope.launch { open(dir.path) } }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    OmpIcons.Folder,
                                    contentDescription = null,
                                    tint = VariantColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    dir.name,
                                    color = VariantColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(current) },
                enabled = !loading && current.isNotBlank()
            ) {
                Text(strings.pickThis, color = VariantColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = VariantColors.textSecondary)
            }
        }
    )
}

private fun groupSessions(
    sessions: List<SessionSummary>,
    strings: com.omp.chat.ui.Strings
): List<Pair<String, List<SessionSummary>>> {
    if (sessions.isEmpty()) return emptyList()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000

    val today = sessions.filter { it.updatedAt >= todayStart }
    val yesterday = sessions.filter { it.updatedAt in yesterdayStart until todayStart }
    val earlier = sessions.filter { it.updatedAt < yesterdayStart }

    return buildList {
        if (today.isNotEmpty()) add(strings.today to today)
        if (yesterday.isNotEmpty()) add(strings.yesterday to yesterday)
        if (earlier.isNotEmpty()) add(strings.older to earlier)
    }
}
