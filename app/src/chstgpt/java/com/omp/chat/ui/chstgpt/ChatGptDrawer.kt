package com.omp.chat.ui.chstgpt

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.AppSettings
import com.omp.chat.data.PcProfile
import com.omp.chat.data.RepoState
import com.omp.chat.data.SessionSummary

/**
 * ChatGPT-style history drawer. Every list is derived from the live repository
 * state for the active PC; switching PC is explicit and confirmed.
 */
@Composable
fun ChatGptDrawer(
    state: RepoState,
    settings: AppSettings,
    filesRepo: com.omp.chat.data.FileManagerRepository? = null,
    pcProfiles: List<PcProfile>,
    activePcId: String,
    favoriteIds: Set<String>,
    onClose: () -> Unit,
    onNewChat: (String) -> Unit,
    onCreateProject: (String, String?) -> Unit = { _, _ -> },
    onHideProject: (String) -> Unit = {},
    onUnhideProject: (String) -> Unit = {},
    onRenameProject: (String, String) -> Unit = { _, _ -> },
    onDeleteProject: (String, Boolean) -> Unit = { _, _ -> },
    onOpenSession: (SessionSummary) -> Unit,
    onRename: (SessionSummary) -> Unit,
    onArchive: (SessionSummary, Boolean) -> Unit,
    onDelete: (SessionSummary) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSwitchPc: (String) -> Unit,
    onOpenPcs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenGit: () -> Unit = {},
    onOpenCleanup: () -> Unit = {},
    onOpenArchived: () -> Unit = {}
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    val english = settings.language == "en"
    val activePc = pcProfiles.firstOrNull { it.id == activePcId }
    var query by remember { mutableStateOf("") }
    var showCreateProject by remember { mutableStateOf(false) }
    var createProjectName by remember { mutableStateOf("") }
    var createProjectPath by remember { mutableStateOf("") }
    var showFolderPicker by remember { mutableStateOf(false) }
    var projectsOpen by remember { mutableStateOf(true) }
    var renameProjectTarget by remember { mutableStateOf<com.omp.chat.data.Project?>(null) }
    var showHiddenProjects by remember { mutableStateOf(false) }
    var expandedProjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renameProjectName by remember { mutableStateOf("") }
    var pcDialog by remember { mutableStateOf(false) }
    var moreDialog by remember { mutableStateOf(false) }
    var pendingPc by remember { mutableStateOf<PcProfile?>(null) }

    if (renameProjectTarget != null) {
        AlertDialog(
            onDismissRequest = { renameProjectTarget = null },
            title = { Text("Gi nytt navn", color = c.textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameProjectName,
                    onValueChange = { renameProjectName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameProjectTarget?.let { onRenameProject(it.id, renameProjectName) }
                    renameProjectTarget = null
                }) { Text("Lagre", color = c.textPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { renameProjectTarget = null }) { Text("Avbryt", color = c.textSecondary) }
            },
            containerColor = c.surfaceElevated
        )
    }

    // Create project dialog
    if (showCreateProject) {
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            title = { Text("Nytt prosjekt", color = c.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = createProjectName,
                        onValueChange = { createProjectName = it },
                        label = { Text("Prosjektnavn", color = c.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            focusedBorderColor = c.accent,
                            unfocusedBorderColor = c.divider
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            createProjectPath.ifBlank { "Ingen mappe valgt" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (createProjectPath.isNotBlank()) c.textPrimary else c.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showFolderPicker = true }) {
                            Text("Velg mappe", color = c.accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createProjectName.isNotBlank()) {
                            onCreateProject(createProjectName, createProjectPath.ifBlank { null })
                            showCreateProject = false
                            createProjectName = ""
                            createProjectPath = ""
                        }
                    },
                    enabled = createProjectName.isNotBlank()
                ) { Text("Opprett", color = c.textPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProject = false; createProjectName = ""; createProjectPath = "" }) { Text("Avbryt", color = c.textSecondary) }
            },
            containerColor = c.surfaceElevated
        )
    }

    // Folder picker for project creation
    if (showFolderPicker && filesRepo != null) {
        com.omp.chat.ui.screens.PcFolderPickerDialog(
            repo = filesRepo,
            initial = createProjectPath,
            strings = com.omp.chat.ui.LocalStrings.current,
            onPick = { dir ->
                showFolderPicker = false
                createProjectPath = dir
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    val all = remember(state.sessions) { state.sessions }
    val searched = remember(all, query) {
        val q = query.trim()
        if (q.isBlank()) all else all.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.cwd.contains(q, ignoreCase = true) ||
                it.model.contains(q, ignoreCase = true)
        }
    }
    // Sessions that belong to a registered project are shown under that project,
    // not in the general history list. Empty (unsent) sessions are also hidden
    // until the user sends the first message.
    val projectPaths = state.projects.map { it.path.lowercase() }
    fun isProjectSession(s: com.omp.chat.data.SessionSummary): Boolean =
        projectPaths.any { s.cwd.lowercase().startsWith(it) }
    val isSearching = query.isNotBlank()
    // When searching, show all matching sessions (including project ones).
    // When browsing, hide project sessions and empty sessions from the main list.
    val favorites = searched.filter { favoriteIds.contains(it.file) && it.messageCount > 0 }
    val rest = searched.filterNot { favoriteIds.contains(it.file) }
        .filter { it.messageCount > 0 }
        .filterNot { !isSearching && isProjectSession(it) }
    val groups = listOf(
        0 to strings.today,
        1 to strings.yesterday,
        2 to (if (english) "Previous 7 days" else "Siste 7 dager"),
        3 to strings.older
    )

    ModalDrawerSheet(
        drawerContainerColor = c.sidebar,
        drawerContentColor = c.textPrimary,
        modifier = Modifier.width(312.dp)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NimayMark(size = 30)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nimay OMP",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary
                    )
                    Text(
                        activePc?.name?.takeIf { it.isNotBlank() } ?: (if (english) "No PC selected" else "Ingen PC valgt"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GptIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = strings.close,
                    onClick = onClose,
                    size = 36,
                    iconSize = 18,
                    tint = c.textSecondary
                )
            }

            GptDrawerAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = strings.newChat,
                emphasized = true
            ) {
                onNewChat(state.cwd)
                onClose()
            }

            GptSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = strings.searchChats,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(40.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (query.isBlank()) {
                    item(key = "projects-header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { projectsOpen = !projectsOpen }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                strings.projects.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = c.textTertiary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (projectsOpen) "−" else "+",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textTertiary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "+",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textTertiary,
                                modifier = Modifier.clickable { showCreateProject = true }
                            )
                        }
                    }
                    val visibleProjects = state.projects.filter { showHiddenProjects || !it.hidden }
                    if (projectsOpen) {
                        items(visibleProjects.take(8), key = { "project-${it.path}" }) { project ->
                            val projectSessions = state.sessions.filter { it.cwd.startsWith(project.path, ignoreCase = true) }
                                .sortedByDescending { it.updatedAt }
                            val isExpanded = expandedProjects.contains(project.id)
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable { expandedProjects = if (isExpanded) expandedProjects - project.id else expandedProjects + project.id }
                                        .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isActive = state.cwd.startsWith(project.path, ignoreCase = true)
                                    Text(if (isExpanded) "⌄" else "›", style = MaterialTheme.typography.labelLarge, color = c.textTertiary)
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = if (isActive) Color(0xFF2563EB) else c.textTertiary, modifier = Modifier.size(15.dp))
                                    Text(project.name, style = MaterialTheme.typography.bodySmall, color = if (isActive) Color(0xFF2563EB) else c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (project.sessionCount > 0) {
                                        Text(project.sessionCount.toString(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                                    }
                                    Text("+", style = MaterialTheme.typography.labelLarge, color = c.textTertiary, modifier = Modifier.clickable { onNewChat(project.path) })
                                    var menuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        Text("⋯", style = MaterialTheme.typography.labelLarge, color = c.textTertiary, modifier = Modifier.clickable { menuOpen = true })
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = c.surfaceElevated) {
                                            DropdownMenuItem(text = { Text("Gi nytt navn", color = c.textPrimary) }, onClick = { menuOpen = false; renameProjectTarget = project; renameProjectName = project.name })
                                            DropdownMenuItem(text = { Text("Skjul", color = c.textPrimary) }, onClick = { menuOpen = false; if (project.hidden) onUnhideProject(project.id) else onHideProject(project.id) })
                                            DropdownMenuItem(text = { Text("Fjern fra listen", color = c.textPrimary) }, onClick = { menuOpen = false; onDeleteProject(project.id, false) })
                                            DropdownMenuItem(text = { Text("Slett fysisk mappe fra PC", color = c.error) }, onClick = { menuOpen = false; onDeleteProject(project.id, true) })
                                        }
                                    }
                                }
                                if (isExpanded) {
                                    projectSessions.take(5).forEach { session ->
                                        GptSessionRow(
                                            session = session,
                                            active = session.file == state.sessionFile,
                                            streaming = false,
                                            favorite = favoriteIds.contains(session.file),
                                            onOpen = { onOpenSession(session); onClose() },
                                            onToggleFavorite = { onToggleFavorite(session.file) },
                                            onRename = { onRename(session) },
                                            onArchive = { onArchive(session, !session.archived) },
                                            onDelete = { onDelete(session) }
                                        )
                                    }
                                    if (projectSessions.size > 5) {
                                        Row(Modifier.fillMaxWidth().clickable { /* show all */ }.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)) {
                                            Text("Se alle ${projectSessions.size} samtaler...", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                                        }
                                    }
                                    if (projectSessions.isEmpty()) {
                                        Text("Ingen samtaler ennå", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp))
                                    }
                                }
                            }
                        }
                        if (state.projects.any { it.hidden }) {
                            item(key = "toggle-hidden-projects") {
                                Row(Modifier.fillMaxWidth().clickable { showHiddenProjects = !showHiddenProjects }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (showHiddenProjects) "Skjul skjulte prosjekter" else "Vis skjulte prosjekter", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                                }
                            }
                        }
                    }
                }

                if (favorites.isNotEmpty()) {
                    item(key = "favorites-header") {
                        GptDrawerSection(
                            if (english) "Pinned" else "Festet",
                            Icons.Outlined.PushPin
                        )
                    }
                    items(favorites, key = { "fav-${it.file}" }) { session ->
                        GptSessionRow(
                            session = session,
                            active = session.file == state.sessionFile,
                            streaming = session.file == state.sessionFile && state.streaming,
                            favorite = true,
                            onOpen = { onOpenSession(session); onClose() },
                            onToggleFavorite = { onToggleFavorite(session.file) },
                            onRename = { onRename(session) },
                            onArchive = { onArchive(session, !session.archived) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }

                groups.forEach { (bucket, label) ->
                    val inBucket = rest.filter { dayBucket(it.updatedAt) == bucket }
                    if (inBucket.isNotEmpty()) {
                        item(key = "bucket-$bucket") {
                            GptDrawerSection(label)
                        }
                        items(inBucket, key = { "session-${it.file}" }) { session ->
                            GptSessionRow(
                                session = session,
                                active = session.file == state.sessionFile,
                                streaming = session.file == state.sessionFile && state.streaming,
                                favorite = favoriteIds.contains(session.file),
                                onOpen = { onOpenSession(session); onClose() },
                                onToggleFavorite = { onToggleFavorite(session.file) },
                                onRename = { onRename(session) },
                                onArchive = { onArchive(session, !session.archived) },
                                onDelete = { onDelete(session) }
                            )
                        }
                    }
                }

                if (rest.isEmpty() && favorites.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (query.isBlank()) strings.newChat else strings.searchChats,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
                        )
                    }
                }

                item(key = "spacer") { Spacer(Modifier.height(6.dp)) }
            }

            GptDivider(inset = 12)

            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                GptDrawerAction(
                    icon = Icons.Outlined.Computer,
                    label = activePc?.name ?: (if (english) "Switch PC" else "Bytt PC"),
                    trailing = if (pcProfiles.size > 1) "${pcProfiles.size}" else null
                ) { pcDialog = true }

                GptDrawerAction(
                    icon = Icons.Outlined.GridView,
                    label = if (english) "Tasks" else "Oppgaver"
                ) { onOpenTasks(); onClose() }

                GptDrawerAction(
                    icon = Icons.Outlined.Terminal,
                    label = if (english) "Terminal" else "Terminal"
                ) { onOpenTerminal(); onClose() }

                GptDrawerAction(
                    icon = Icons.Outlined.Folder,
                    label = if (english) "Files" else "Filer"
                ) { onOpenFiles(); onClose() }

                GptDrawerAction(
                    icon = Icons.Outlined.Settings,
                    label = strings.settings
                ) { onOpenSettings(); onClose() }

                GptDrawerAction(
                    icon = Icons.Outlined.MoreHoriz,
                    label = if (english) "More" else "Vis flere"
                ) { moreDialog = true }
            }
            GptDivider(inset = 12)
            Spacer(Modifier.navigationBarsPadding().height(6.dp))
        }
    }

    if (pcDialog) {
        AlertDialog(
            onDismissRequest = { pcDialog = false },
            containerColor = c.surfaceElevated,
            title = {
                Text(
                    if (english) "Choose PC" else "Velg PC",
                    color = c.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pcProfiles.isEmpty()) {
                        Text(
                            if (english) "No PCs paired yet." else "Ingen PC-er er paret ennå.",
                            color = c.textSecondary
                        )
                    }
                    pcProfiles.forEach { profile ->
                        val isActive = profile.id == activePcId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) c.selected else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    if (isActive) {
                                        pcDialog = false
                                    } else {
                                        pcDialog = false
                                        pendingPc = profile
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    profile.name.take(1).uppercase().ifBlank { "P" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textPrimary
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.name.ifBlank { profile.baseUrl },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    profile.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isActive) {
                                Text(
                                    if (english) "Active" else "Aktiv",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pcDialog = false; onOpenPcs() }) {
                        Text(if (english) "Add PC" else "Legg til", color = c.accent)
                    }
                    TextButton(onClick = { pcDialog = false; onOpenPcs() }) {
                        Text(if (english) "Manage" else "Administrer", color = c.textSecondary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pcDialog = false }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }

    if (moreDialog) {
        AlertDialog(
            onDismissRequest = { moreDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text(if (english) "More" else "Mer", color = c.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GptDrawerAction(
                        icon = Icons.Outlined.Archive,
                        label = if (english) "Archived chats" else "Arkiverte samtaler"
                    ) {
                        moreDialog = false
                        onOpenArchived()
                        onClose()
                    }
                    GptDrawerAction(
                        icon = Icons.Outlined.Delete,
                        label = if (english) "Clean up" else "Opprydding"
                    ) {
                        moreDialog = false
                        onOpenCleanup()
                        onClose()
                    }
                    GptDrawerAction(
                        icon = Icons.Outlined.Terminal,
                        label = "Git"
                    ) {
                        moreDialog = false
                        onOpenGit()
                        onClose()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moreDialog = false }) {
                    Text(strings.close, color = c.textSecondary)
                }
            }
        )
    }

    pendingPc?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPc = null },
            containerColor = c.surfaceElevated,
            title = {
                Text(
                    if (english) "Switch to ${target.name}?" else "Bytt til ${target.name}?",
                    color = c.textPrimary
                )
            },
            text = {
                Text(
                    if (english) {
                        "The current conversation closes and ${target.name} gets its own history. Nothing is deleted."
                    } else {
                        "Gjeldende samtale lukkes, og ${target.name} får sin egen historikk. Ingenting slettes."
                    },
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPc = null
                    onSwitchPc(target.id)
                    onClose()
                }) {
                    Text(if (english) "Switch" else "Bytt", color = c.textPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPc = null }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun GptDrawerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: String? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalChatGptColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (emphasized) c.surface else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = c.textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun GptDrawerSection(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val c = LocalChatGptColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(13.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = c.textTertiary
        )
    }
}

@Composable
private fun GptProjectRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onRename: () -> Unit = {},
    onHide: () -> Unit = {},
    projectId: String,
    onDeleteProject: (String, Boolean) -> Unit = { _, _ -> }
) {
    val c = LocalChatGptColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
        Box {
            Text(
                "⋯",
                style = MaterialTheme.typography.labelLarge,
                color = c.textTertiary,
                modifier = Modifier.clickable { menuOpen = true }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = c.surfaceElevated
            ) {
                DropdownMenuItem(
                    text = { Text("Gi nytt navn", color = c.textPrimary) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Skjul", color = c.textPrimary) },
                    onClick = { menuOpen = false; onHide() }
                )
                DropdownMenuItem(
                    text = { Text("Fjern fra listen", color = c.textPrimary) },
                    onClick = { menuOpen = false; onDeleteProject(projectId, false) }
                )
                DropdownMenuItem(
                    text = { Text("Slett fysisk mappe fra PC", color = c.error) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = c.error, modifier = Modifier.size(17.dp))
                    },
                    onClick = { menuOpen = false; onDeleteProject(projectId, true) }
                )
            }
        }
    }
}
@Composable
private fun GptSessionRow(
    session: SessionSummary,
    active: Boolean,
    streaming: Boolean,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalChatGptColors.current
    val english = com.omp.chat.ui.LocalStrings.current.lang == "en"
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) c.selected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onOpen() }
            .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (streaming) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(c.online)
            )
        }
        Text(
            session.title.ifBlank { if (english) "New chat" else "Ny samtale" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) c.textPrimary else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            GptIconButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = if (english) "More" else "Mer",
                onClick = { menu = true },
                size = 28,
                iconSize = 16,
                tint = c.textTertiary
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = c.surfaceElevated
            ) {
                DropdownMenuItem(
                    text = { Text(if (favorite) (if (english) "Unpin" else "Løsne") else (if (english) "Pin" else "Fest"), color = c.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (favorite) c.textPrimary else c.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    onClick = { menu = false; onToggleFavorite() }
                )
                DropdownMenuItem(
                    text = { Text(com.omp.chat.ui.LocalStrings.current.rename, color = c.textPrimary) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(17.dp))
                    },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (session.archived) com.omp.chat.ui.LocalStrings.current.unarchive
                            else com.omp.chat.ui.LocalStrings.current.archive,
                            color = c.textPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Archive, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(17.dp))
                    },
                    onClick = { menu = false; onArchive() }
                )
                DropdownMenuItem(
                    text = { Text(com.omp.chat.ui.LocalStrings.current.delete, color = c.error) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = c.error, modifier = Modifier.size(17.dp))
                    },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}
