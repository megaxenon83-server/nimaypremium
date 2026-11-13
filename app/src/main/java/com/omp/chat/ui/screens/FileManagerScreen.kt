package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omp.chat.data.ConflictMode
import com.omp.chat.data.FileManagerRepository
import com.omp.chat.data.FsEntry
import com.omp.chat.data.FsFormat
import com.omp.chat.data.FsListing
import com.omp.chat.data.FsMime
import com.omp.chat.data.FsPaths
import com.omp.chat.data.FsPlace
import com.omp.chat.data.FsRoot
import com.omp.chat.data.FsStorage
import com.omp.chat.data.TransferItem
import com.omp.chat.data.TransferKind
import com.omp.chat.data.TransferState
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.Strings
import com.omp.chat.ui.components.OmpIcons
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// ---------------------------------------------------------------------------
// Hjelpere
// ---------------------------------------------------------------------------

private val StringListSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it }
)

private data class FsClipboard(
    val tab: Int,
    val rootUri: String,
    val dir: List<String>,
    val pcDir: String,
    val entries: List<FsEntry>,
    val cut: Boolean
)

private data class PreviewTarget(
    val name: String,
    val pcPath: String? = null,
    val phoneUri: Uri? = null
)

private fun sortEntries(entries: List<FsEntry>, key: Int): List<FsEntry> {
    val dirsFirst = compareByDescending<FsEntry> { it.dir }
    return when (key) {
        1 -> entries.sortedWith(dirsFirst.thenByDescending { it.mtime })
        2 -> entries.sortedWith(dirsFirst.thenByDescending { it.size })
        3 -> entries.sortedWith(
            dirsFirst
                .thenBy { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy { it.name.lowercase() }
        )
        else -> entries.sortedWith(dirsFirst.thenBy { it.name.lowercase() })
    }
}

private fun stateLabel(state: TransferState, strings: Strings): String = when (state) {
    TransferState.QUEUED -> strings.queuedLabel
    TransferState.RUNNING -> strings.transferring
    TransferState.PAUSED -> strings.pausedLabel
    TransferState.CONFLICT -> strings.conflictLabel
    TransferState.DONE -> strings.doneLabel
    TransferState.FAILED -> strings.failedLabel
    TransferState.CANCELLED -> strings.cancelledLabel
    TransferState.SKIPPED -> strings.skippedLabel
}

private fun safeName(name: String): String? {
    val value = name.trim()
    if (value.isEmpty() || value == "." || value == "..") return null
    if (value.any { it in "\\/:*?\"<>|" }) return null
    return value
}

// ---------------------------------------------------------------------------
// Skjerm
// ---------------------------------------------------------------------------

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun FileManagerScreen(
    repo: FileManagerRepository,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Navigasjon (bevares ved rotasjon og fanebytte)
    var tab by rememberSaveable { mutableStateOf(0) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var sortKey by rememberSaveable { mutableStateOf(0) }
    var pcPath by rememberSaveable { mutableStateOf("") }
    var phoneRootUri by rememberSaveable { mutableStateOf("") }
    var phoneDir by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(emptyList<String>()) }

    // Lister
    var roots by remember { mutableStateOf(repo.roots()) }
    val phoneRoot: FsRoot? = roots.firstOrNull { it.uri == phoneRootUri } ?: roots.firstOrNull()

    var phoneListing by remember { mutableStateOf<FsListing?>(null) }
    var phoneStorage by remember { mutableStateOf<FsStorage?>(null) }
    var phoneError by remember { mutableStateOf("") }
    var phoneDetail by remember { mutableStateOf("") }
    var phoneLoading by remember { mutableStateOf(false) }
    var phoneQuick by remember { mutableStateOf<List<String>>(emptyList()) }

    var pcListing by remember { mutableStateOf<FsListing?>(null) }
    var pcStorage by remember { mutableStateOf<FsStorage?>(null) }
    var pcError by remember { mutableStateOf("") }
    var pcDetail by remember { mutableStateOf("") }
    var pcLoading by remember { mutableStateOf(false) }
    var pcPlaces by remember { mutableStateOf<List<FsPlace>>(emptyList()) }

    var refreshKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Søk
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var pcSearch by remember { mutableStateOf<FsListing?>(null) }
    var searchError by remember { mutableStateOf("") }

    // Meny/dialoger
    var menuOpen by remember { mutableStateOf(false) }
    var showTransfers by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    var connectionOpen by remember { mutableStateOf(false) }
    var rootsOpen by remember { mutableStateOf(false) }
    var detailsTarget by remember { mutableStateOf<FsEntry?>(null) }
    var previewTarget by remember { mutableStateOf<PreviewTarget?>(null) }
    var clipboard by remember { mutableStateOf<FsClipboard?>(null) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Målvalg for overføring
    var pcPickerOpen by remember { mutableStateOf(false) }
    var phonePickerOpen by remember { mutableStateOf(false) }
    var pendingTransfer by remember { mutableStateOf<List<FsEntry>>(emptyList()) }

    val transfers by repo.transfers.collectAsStateWithLifecycle()
    val activeTransfers = transfers.filter {
        it.state == TransferState.RUNNING || it.state == TransferState.QUEUED ||
            it.state == TransferState.PAUSED || it.state == TransferState.CONFLICT
    }
    val conflictCount = transfers.count { it.state == TransferState.CONFLICT }

    LaunchedEffect(conflictCount) {
        if (conflictCount > 0) showTransfers = true
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    fun say(text: String) { toast = text }

    fun reload() {
        refreshKey += 1
    }

    // ---- Telefon ----
    LaunchedEffect(tab, phoneRootUri, phoneDir, refreshKey) {
        if (tab != 0 || phoneRoot == null) return@LaunchedEffect
        phoneLoading = true
        phoneError = ""
        phoneDetail = ""
        val result = withContext(Dispatchers.IO) {
            runCatching { repo.childrenPhone(phoneRoot.uri, phoneDir) }
        }
        result.onSuccess {
            phoneListing = FsListing(phoneDir.joinToString("/"), it)
        }.onFailure {
            phoneError = repo.friendlyError(it)
            phoneDetail = it.message ?: it.javaClass.simpleName
            phoneListing = null
        }
        phoneStorage = repo.phoneStorage()
        phoneLoading = false
    }

    LaunchedEffect(phoneRootUri, refreshKey) {
        val root = phoneRoot ?: run { phoneQuick = emptyList(); return@LaunchedEffect }
        phoneQuick = withContext(Dispatchers.IO) {
            listOf("Download", "Downloads", "DCIM", "Pictures", "Documents", "Music", "Movies", "Podcasts")
                .filter { name ->
                    runCatching {
                        com.omp.chat.data.FsSaf.folder(context, Uri.parse(root.uri), listOf(name)) != null
                    }.getOrDefault(false)
                }
        }
    }

    // ---- PC ----
    LaunchedEffect(tab, pcPath, refreshKey) {
        if (tab != 1) return@LaunchedEffect
        pcLoading = true
        pcError = ""
        pcDetail = ""
        pcSearch = null
        searchError = ""
        repo.listPc(pcPath).onSuccess { listing ->
            pcListing = listing
            if (listing.places.isNotEmpty()) pcPlaces = listing.places
            repo.pcStorage(listing.path.ifBlank { pcPath }).onSuccess { pcStorage = it }
        }.onFailure {
            pcError = repo.friendlyError(it)
            pcDetail = it.message ?: it.javaClass.simpleName
            pcListing = null
        }
        pcLoading = false
    }

    // ---- Rotasjon: sikre at lagret rot fortsatt finnes ----
    LaunchedEffect(roots.size) {
        if (phoneRootUri.isNotBlank() && roots.none { it.uri == phoneRootUri }) {
            phoneRootUri = roots.firstOrNull()?.uri.orEmpty()
            phoneDir = emptyList()
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val label = runCatching {
                DocumentFile.fromTreeUri(context, uri)?.name
            }.getOrNull().orEmpty().ifBlank {
                uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Mappe"
            }
            repo.addRoot(label, uri)
            roots = repo.roots()
            phoneRootUri = uri.toString()
            phoneDir = emptyList()
            selected = emptySet()
            reload()
        }
    }

    fun goUp() {
        selected = emptySet()
        when (tab) {
            0 -> if (phoneDir.isNotEmpty()) phoneDir = phoneDir.dropLast(1)
            else -> if (pcPath.isNotBlank()) pcPath = FsPaths.parent(pcPath)
        }
    }

    val canGoUp = when (tab) {
        0 -> phoneDir.isNotEmpty()
        else -> pcPath.isNotBlank()
    }

    BackHandler(enabled = true) {
        when {
            showTransfers -> showTransfers = false
            selected.isNotEmpty() -> selected = emptySet()
            searchOpen || pcSearch != null -> {
                searchOpen = false
                query = ""
                pcSearch = null
                searchError = ""
            }
            canGoUp -> goUp()
            else -> onBack()
        }
    }

    fun pcPathFor(entry: FsEntry): String {
        val search = pcSearch
        if (search != null && entry.relative.isNotBlank()) {
            return FsPaths.join(search.path, *entry.relative.split('/').toTypedArray())
        }
        if (pcPath.isBlank()) return entry.name
        return FsPaths.join(pcPath, entry.name)
    }

    fun currentPathOf(entry: FsEntry): String {
        if (tab == 0) return (listOf(phoneRoot?.label.orEmpty()) + phoneDir + entry.name).joinToString("/")
        return pcPathFor(entry)
    }

    fun openEntry(entry: FsEntry) {
        if (entry.dir) {
            selected = emptySet()
            pcSearch = null
            searchError = ""
            query = ""
            if (tab == 0) {
                phoneDir = phoneDir + entry.name
            } else {
                pcPath = if (pcPath.isBlank()) entry.name else FsPaths.join(pcPath, entry.name)
            }
            return
        }
        if (FsMime.isImage(entry.name)) {
            if (tab == 0) {
                val root = phoneRoot ?: return
                val uri = repo.phoneChildUri(root.uri, phoneDir, entry.name) ?: return
                previewTarget = PreviewTarget(entry.name, phoneUri = uri)
            } else {
                previewTarget = PreviewTarget(entry.name, pcPath = currentPathOf(entry))
            }
            return
        }
        if (tab == 0) {
            val root = phoneRoot ?: return
            val uri = repo.phoneChildUri(root.uri, phoneDir, entry.name) ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FsMime.of(entry.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, entry.name))
            } catch (_: Throwable) {
                say("Ingen app kan åpne denne filtypen")
            }
        } else {
            pendingTransfer = listOf(entry)
            if (phoneRoot == null) {
                say(strings.pickFolderFirst)
                rootsOpen = true
            } else {
                phonePickerOpen = true
            }
        }
    }

    val rawEntries: List<FsEntry> = when {
        tab == 1 && pcSearch != null -> pcSearch!!.entries
        tab == 0 -> phoneListing?.entries ?: emptyList()
        else -> pcListing?.entries ?: emptyList()
    }
    val filtered = if (query.isNotBlank() && (tab == 0 || pcSearch == null)) {
        rawEntries.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.relative.contains(query, ignoreCase = true)
        }
    } else rawEntries
    val visible = remember(filtered, sortKey) { sortEntries(filtered, sortKey) }

    fun selectionEntries(): List<FsEntry> = visible.filter { it.name in selected }

    fun startCopy(cut: Boolean) {
        val entries = selectionEntries()
        if (entries.isEmpty()) return
        if (tab == 0 && phoneRoot == null) { say(strings.pickFolderFirst); return }
        clipboard = FsClipboard(
            tab = tab,
            rootUri = phoneRoot?.uri.orEmpty(),
            dir = phoneDir,
            pcDir = if (tab == 1) (pcSearch?.path ?: pcPath) else "",
            entries = entries,
            cut = cut
        )
        selected = emptySet()
        say("${entries.size} → ${if (cut) strings.cut else strings.copy}")
    }

    fun refreshAfterChange() {
        selected = emptySet()
        reload()
    }

    fun deleteSelected() {
        val names = selected.toList()
        if (names.isEmpty()) return
        scope.launch {
            busyText = strings.delete
            val result = withContext(Dispatchers.IO) {
                if (tab == 0) {
                    val root = phoneRoot
                    if (root == null) Result.failure(IllegalStateException(strings.pickFolderFirst))
                    else repo.phoneDelete(root.uri, phoneDir, names)
                } else {
                    repo.pcDelete(names.map { FsPaths.join(pcPath, it) })
                }
            }
            busyText = null
            result.onSuccess {
                say("${names.size} ${if (names.size == 1) "element" else "elementer"} slettet")
                refreshAfterChange()
            }.onFailure { say(repo.friendlyError(it)) }
        }
    }

    fun renameSelected(to: String) {
        val from = renameTarget ?: return
        val clean = safeName(to)
        if (clean == null) { say("Ugyldig navn"); return }
        if (clean == from) return
        scope.launch {
            busyText = strings.renameFile
            val result = withContext(Dispatchers.IO) {
                if (tab == 0) {
                    val root = phoneRoot
                    if (root == null) Result.failure(IllegalStateException(strings.pickFolderFirst))
                    else repo.phoneRename(root.uri, phoneDir, from, clean)
                } else {
                    repo.pcRename(FsPaths.join(pcPath, from), FsPaths.join(pcPath, clean))
                }
            }
            busyText = null
            result.onSuccess { refreshAfterChange() }
                .onFailure { say(repo.friendlyError(it)) }
        }
    }

    fun createFolder(name: String) {
        val clean = safeName(name)
        if (clean == null) { say("Ugyldig navn"); return }
        scope.launch {
            busyText = strings.newFolder
            val result = withContext(Dispatchers.IO) {
                if (tab == 0) {
                    val root = phoneRoot
                    if (root == null) Result.failure(IllegalStateException(strings.pickFolderFirst))
                    else repo.phoneMkdir(root.uri, phoneDir, clean)
                } else repo.pcMkdir(FsPaths.join(pcPath, clean))
            }
            busyText = null
            result.onSuccess { refreshAfterChange() }
                .onFailure { say(repo.friendlyError(it)) }
        }
    }

    fun paste() {
        val clip = clipboard ?: return
        if (tab == 0 && phoneRoot == null) { say(strings.pickFolderFirst); return }
        if (tab == 1 && pcPath.isBlank()) { say("Åpne en mappe på PC-en først"); return }
        scope.launch {
            busyText = strings.paste
            val result = withContext(Dispatchers.IO) {
                when {
                    clip.tab == 0 && tab == 0 -> {
                        val root = phoneRoot
                        if (root == null) Result.failure(IllegalStateException(strings.pickFolderFirst))
                        else if (clip.rootUri != root.uri) {
                            Result.failure(IOException("Kopiering mellom ulike telefonmapper støttes ikke ennå"))
                        } else {
                            repo.phoneCopyMove(root.uri, clip.dir, clip.entries.map { it.name }, phoneDir, clip.cut)
                        }
                    }
                    clip.tab == 1 && tab == 1 -> {
                        if (clip.pcDir == pcPath) Result.failure(IOException("Kilde og mål er samme mappe"))
                        else repo.pcCopyMove(clip.entries.map { FsPaths.join(clip.pcDir, it.name) }, pcPath, clip.cut)
                    }
                    clip.tab == 1 && tab == 0 -> {
                        val root = phoneRoot
                        if (root == null) Result.failure(IllegalStateException(strings.pickFolderFirst))
                        else repo.downloadEntries(clip.pcDir, clip.entries, root.label, root.uri, phoneDir)
                    }
                    else -> {
                        val root = roots.firstOrNull { it.uri == clip.rootUri }
                        if (root == null) Result.failure(IOException("Telefonmappen finnes ikke"))
                        else repo.uploadEntries(clip.rootUri, root.label, clip.dir, clip.entries, pcPath)
                    }
                }
            }
            busyText = null
            result.onSuccess {
                if (clip.cut) clipboard = null
                say(strings.paste)
                refreshAfterChange()
            }.onFailure { say(repo.friendlyError(it)) }
        }
    }

    fun shareSelection() {
        val entry = selectionEntries().singleOrNull() ?: run {
            say("Del gjelder én fil om gangen")
            return
        }
        if (tab == 0) {
            val root = phoneRoot ?: return
            val uri = repo.phoneChildUri(root.uri, phoneDir, entry.name) ?: return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = FsMime.of(entry.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, entry.name))
            } catch (_: Throwable) {
                say("Ingen app kan dele filen")
            }
        } else {
            scope.launch {
                busyText = "Klargjør deling …"
                val result = runCatching { repo.sharePcFile(pcPathFor(entry)) }
                busyText = null
                result.onSuccess { uri ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = FsMime.of(entry.name)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, entry.name))
                    } catch (_: Throwable) {
                        say("Ingen app kan dele filen")
                    }
                }.onFailure { say(repo.friendlyError(it)) }
            }
        }
    }

    fun transferSelection() {
        val entries = selectionEntries()
        if (entries.isEmpty()) return
        pendingTransfer = entries
        if (tab == 0) {
            pcPickerOpen = true
        } else {
            if (phoneRoot == null) { say(strings.pickFolderFirst); rootsOpen = true }
            else phonePickerOpen = true
        }
    }

    fun queuePendingToPc(dir: String) {
        val root = phoneRoot ?: return
        val entries = pendingTransfer
        pendingTransfer = emptyList()
        if (entries.isEmpty()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.uploadEntries(root.uri, root.label, phoneDir, entries, dir)
            }
            result.onSuccess {
                selected = emptySet()
                say("${it} ${if (it == 1) "fil" else "filer"} i kø")
                showTransfers = true
            }.onFailure { say(repo.friendlyError(it)) }
        }
    }

    fun queuePendingToPhone(rootUri: String, dir: List<String>, label: String) {
        val entries = pendingTransfer
        pendingTransfer = emptyList()
        if (entries.isEmpty()) return
        scope.launch {
            var queued = 0
            var failure: Throwable? = null
            withContext(Dispatchers.IO) {
                for (entry in entries) {
                    val full = pcPathFor(entry)
                    val parent = FsPaths.parent(full)
                    val leaf = FsEntry(
                        name = FsPaths.fileName(full),
                        dir = entry.dir,
                        size = entry.size,
                        mtime = entry.mtime
                    )
                    val result = repo.downloadEntries(parent, listOf(leaf), label, rootUri, dir)
                    result.onSuccess { queued += it }
                        .onFailure { failure = it; return@withContext }
                }
            }
            failure?.let { say(repo.friendlyError(it)) }
            if (queued > 0) {
                selected = emptySet()
                say("$queued ${if (queued == 1) "fil" else "filer"} i kø")
                showTransfers = true
            }
        }
    }

    // -----------------------------------------------------------------------
    // Toppfelt
    // -----------------------------------------------------------------------

    Column(
        Modifier
            .fillMaxSize()
            .background(VariantColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (selected.isNotEmpty()) selected = emptySet() else onBack()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = strings.close, tint = VariantColors.textPrimary)
            }
            Text(
                text = strings.fileManagerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VariantColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { searchOpen = !searchOpen; if (!searchOpen) { query = ""; pcSearch = null } },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(OmpIcons.Search, contentDescription = strings.searchFiles, tint = VariantColors.textSecondary)
            }
            IconButton(onClick = { gridMode = !gridMode }, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (gridMode) OmpIcons.ListIcon else OmpIcons.Grid,
                    contentDescription = null,
                    tint = VariantColors.textSecondary
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = VariantColors.textSecondary)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = VariantColors.surface
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.newFolder) },
                        leadingIcon = { Icon(OmpIcons.NewFolder, null, tint = VariantColors.accent) },
                        onClick = {
                            menuOpen = false
                            if (tab == 0 && phoneRoot == null) {
                                say(strings.pickFolderFirst)
                                rootsOpen = true
                            } else if (tab == 1 && pcPath.isBlank()) {
                                say("Åpne en mappe på PC-en først")
                            } else newFolderOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.selectAll) },
                        leadingIcon = { Icon(OmpIcons.SelectAll, null) },
                        enabled = visible.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            selected = visible.map { it.name }.toSet()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.refresh) },
                        leadingIcon = { Icon(OmpIcons.Refresh, null) },
                        onClick = { menuOpen = false; reload() }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.sortBy) },
                        leadingIcon = { Icon(OmpIcons.Sort, null) },
                        onClick = { menuOpen = false; sortOpen = true }
                    )
                    HorizontalDivider(color = VariantColors.divider)
                    DropdownMenuItem(
                        text = { Text("${strings.transfersRunning} (${activeTransfers.size})") },
                        leadingIcon = { Icon(OmpIcons.Upload, null) },
                        onClick = { menuOpen = false; showTransfers = true }
                    )
                    if (tab == 0) {
                        DropdownMenuItem(
                            text = { Text(strings.addPhoneFolder) },
                            leadingIcon = { Icon(OmpIcons.Folder, null) },
                            onClick = { menuOpen = false; treePicker.launch(null) }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.phoneFolders) },
                            leadingIcon = { Icon(OmpIcons.Home, null) },
                            onClick = { menuOpen = false; rootsOpen = true }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(strings.connection) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { menuOpen = false; connectionOpen = true }
                    )
                }
            }
        }

        // Telefon/PC-velger
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SegmentButton(
                label = strings.phoneTab,
                icon = OmpIcons.PhoneDevice,
                selected = tab == 0,
                modifier = Modifier.weight(1f),
                onClick = { tab = 0; selected = emptySet(); pcSearch = null; searchError = "" }
            )
            SegmentButton(
                label = strings.pcTab,
                icon = OmpIcons.Computer,
                selected = tab == 1,
                modifier = Modifier.weight(1f),
                statusColor = when {
                    pcError.isNotBlank() -> VariantColors.error
                    pcListing != null -> VariantColors.online
                    else -> VariantColors.textSecondary.copy(alpha = 0.55f)
                },
                onClick = { tab = 1; selected = emptySet(); searchError = "" }
            )
        }

        // Status: lagring / tilkobling
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val storage = if (tab == 0) phoneStorage else pcStorage
            if (storage != null && storage.total > 0) {
                Text(
                    "${FsFormat.size(storage.free)} ${strings.storageFreeOf} ${FsFormat.size(storage.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VariantColors.textSecondary
                )
            } else {
                Text(
                    if (tab == 0) "Telefon" else repo.baseUrl().ifBlank { strings.notConnected },
                    style = MaterialTheme.typography.labelSmall,
                    color = VariantColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            if (tab == 1) {
                Text(
                    text = when {
                        !repo.isConfigured() -> strings.notConnected
                        pcError.isNotBlank() -> "Frakoblet"
                        else -> "Tilkoblet"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !repo.isConfigured() || pcError.isNotBlank() -> VariantColors.error
                        else -> VariantColors.online
                    }
                )
            }
        }

        if (tab == 1 && pcStorage != null && pcStorage!!.total > 0) {
            val used = (pcStorage!!.total - pcStorage!!.free).coerceAtLeast(0)
            LinearProgressIndicator(
                progress = { used.toFloat() / pcStorage!!.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 12.dp),
                color = VariantColors.accent,
                trackColor = VariantColors.surfaceElevated
            )
        }

        // Søk
        if (searchOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.searchFiles, color = VariantColors.textSecondary) },
                    leadingIcon = { Icon(OmpIcons.Search, null, tint = VariantColors.textSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (tab == 0) {
                            // Telefon: filtrerer gjeldende mappe direkte.
                        } else {
                            if (pcPath.isBlank()) {
                                say("Åpne en mappe på PC-en først")
                            } else if (query.isNotBlank()) {
                                scope.launch {
                                    searchBusy = true
                                    searchError = ""
                                    val result = repo.searchPc(pcPath, query)
                                    searchBusy = false
                                    result.onSuccess {
                                        pcSearch = it
                                        if (it.entries.isEmpty()) say(strings.searchNoHits)
                                    }.onFailure {
                                        searchError = repo.friendlyError(it)
                                    }
                                }
                            }
                        }
                    }),
                    trailingIcon = {
                        if (searchBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = VariantColors.accent)
                        } else {
                            Row {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { query = ""; pcSearch = null; searchError = "" }) {
                                        Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary)
                                    }
                                }
                                IconButton(onClick = {
                                    if (tab == 1 && query.isNotBlank()) {
                                        scope.launch {
                                            searchBusy = true
                                            searchError = ""
                                            val result = repo.searchPc(pcPath, query)
                                            searchBusy = false
                                            result.onSuccess { pcSearch = it }
                                                .onFailure { searchError = repo.friendlyError(it) }
                                        }
                                    }
                                }) {
                                    Icon(OmpIcons.Done, null, tint = VariantColors.accent)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VariantColors.accent,
                        unfocusedBorderColor = VariantColors.divider,
                        cursorColor = VariantColors.accent,
                        focusedTextColor = VariantColors.textPrimary,
                        unfocusedTextColor = VariantColors.textPrimary
                    )
                )
            }
            if (searchError.isNotBlank()) {
                Text(
                    searchError,
                    color = VariantColors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        // Brødsmuler
        BreadcrumbRow(
            tab = tab,
            pcPath = pcPath,
            phoneRootLabel = phoneRoot?.label.orEmpty(),
            phoneDir = phoneDir,
            searchLabel = if (pcSearch != null) "${strings.searchFiles}: $query" else "",
            canGoUp = canGoUp,
            onUp = { goUp() },
            onRoot = {
                selected = emptySet()
                if (tab == 0) phoneDir = emptyList() else pcPath = ""
            },
            onCrumb = { target ->
                selected = emptySet()
                if (tab == 0) {
                    phoneDir = (target as? List<*>)?.filterIsInstance<String>() ?: phoneDir
                } else {
                    pcPath = target as? String ?: pcPath
                    pcSearch = null
                }
            },
            onRefresh = { reload() },
            onCloseSearch = {
                pcSearch = null
                query = ""
                searchOpen = false
                reload()
            }
        )

        // Hurtigtilgang
        val quickPc = if (tab == 1 && pcPath.isBlank()) pcPlaces else emptyList()
        val quickPhone = if (tab == 0) phoneQuick else emptyList()
        if (quickPc.isNotEmpty() || quickPhone.isNotEmpty()) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                if (tab == 1) {
                    items(quickPc, key = { it.path }) { place ->
                        QuickChip(place.label) {
                            selected = emptySet()
                            pcPath = place.path
                        }
                    }
                } else {
                    items(quickPhone, key = { it }) { name ->
                        QuickChip(name) {
                            selected = emptySet()
                            phoneDir = listOf(name)
                        }
                    }
                }
            }
        }

        // Utklipp
        clipboard?.let { clip ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(VariantColors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (clip.cut) OmpIcons.Cut else OmpIcons.Copy,
                    contentDescription = null,
                    tint = VariantColors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${clip.entries.size} ${strings.itemsInClipboard}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VariantColors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { paste() }) { Text(strings.pasteHere, color = VariantColors.accent) }
                IconButton(onClick = { clipboard = null }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Innhold
        Box(Modifier.weight(1f)) {
            if (tab == 0 && phoneRoot == null) {
                PhoneWelcome(
                    strings = strings,
                    roots = roots,
                    onAdd = { treePicker.launch(null) },
                    onOpen = { root ->
                        phoneRootUri = root.uri
                        phoneDir = emptyList()
                        selected = emptySet()
                        reload()
                    },
                    onRemove = { root ->
                        repo.removeRoot(Uri.parse(root.uri))
                        roots = repo.roots()
                        if (phoneRootUri == root.uri) {
                            phoneRootUri = roots.firstOrNull()?.uri.orEmpty()
                            phoneDir = emptyList()
                        }
                        reload()
                    }
                )
            } else {
                val error = if (tab == 0) phoneError else pcError
                val detail = if (tab == 0) phoneDetail else pcDetail
                val loading = if (tab == 0) phoneLoading else pcLoading
                when {
                    error.isNotBlank() -> ErrorState(
                        message = error,
                        detail = detail,
                        endpoint = if (tab == 1) repo.baseUrl() else "",
                        strings = strings,
                        onRetry = { reload() },
                        onConnection = { connectionOpen = true }
                    )
                    loading && visible.isEmpty() -> {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center),
                                color = VariantColors.accent
                            )
                        }
                    }
                    visible.isEmpty() -> EmptyState(
                        searching = query.isNotBlank() || pcSearch != null,
                        strings = strings,
                        onNewFolder = {
                            if (tab == 0) newFolderOpen = true
                            else if (pcPath.isBlank()) say("Åpne en mappe på PC-en først")
                            else newFolderOpen = true
                        },
                        onRetry = { reload() }
                    )
                    gridMode -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(92.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(visible, key = { it.name }) { entry ->
                            FileCell(
                                entry = entry,
                                selected = entry.name in selected,
                                repo = repo,
                                tab = tab,
                                treeUri = phoneRoot?.uri.orEmpty(),
                                phoneDir = phoneDir,
                                pcPath = pcPath,
                                onClick = { openEntry(entry) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selected = selected + entry.name
                                }
                            )
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.name }) { entry ->
                            FileRow(
                                entry = entry,
                                selected = entry.name in selected,
                                showRelative = pcSearch != null && entry.relative.isNotBlank(),
                                repo = repo,
                                tab = tab,
                                treeUri = phoneRoot?.uri.orEmpty(),
                                phoneDir = phoneDir,
                                pcPath = pcPath,
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (entry.name in selected) selected - entry.name
                                        else selected + entry.name
                                    } else openEntry(entry)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selected = if (entry.name in selected) selected - entry.name
                                    else selected + entry.name
                                }
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }

        // Overføringsbjelke
        if (activeTransfers.isNotEmpty() || transfers.isNotEmpty()) {
            TransferStrip(
                transfers = transfers,
                active = activeTransfers,
                onClick = { showTransfers = true }
            )
        }

        // Flervalg
        if (selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                single = selected.size == 1,
                strings = strings,
                onClose = { selected = emptySet() },
                onCopy = { startCopy(false) },
                onCut = { startCopy(true) },
                onTransfer = { transferSelection() },
                onDelete = { deleteTargets = selected.toList() },
                onShare = { shareSelection() },
                onRename = { renameTarget = selected.firstOrNull() },
                onDetails = {
                    val entry = selectionEntries().firstOrNull()
                    if (entry != null) detailsTarget = entry
                }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Overføringspanel
    // -----------------------------------------------------------------------

    if (showTransfers) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTransfers = false },
            sheetState = sheetState,
            containerColor = VariantColors.sidebar
        ) {
            TransferSheet(
                transfers = transfers,
                strings = strings,
                onPause = { repo.pause(it) },
                onResume = { repo.resume(it) },
                onCancel = { repo.cancel(it) },
                onRemove = { repo.remove(it) },
                onClear = { repo.clearFinished() },
                onOverwrite = { repo.resolveConflict(it, ConflictMode.OVERWRITE) },
                onKeepBoth = { repo.resolveConflict(it, ConflictMode.RENAME) },
                onSkip = { repo.resolveConflict(it, ConflictMode.SKIP) }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Dialoger
    // -----------------------------------------------------------------------

    if (newFolderOpen) {
        NameDialog(
            title = strings.newFolder,
            initial = "",
            label = strings.folderName,
            confirm = strings.create,
            onConfirm = { newFolderOpen = false; createFolder(it) },
            onDismiss = { newFolderOpen = false }
        )
    }

    renameTarget?.let { old ->
        NameDialog(
            title = strings.renameFile,
            initial = old,
            label = strings.nameLabel,
            confirm = strings.save,
            onConfirm = {
                renameTarget = null
                renameSelected(it)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTargets?.let { targets ->
        DeleteDialog(
            targets = targets,
            strings = strings,
            onConfirm = {
                deleteTargets = null
                deleteSelected()
            },
            onDismiss = { deleteTargets = null }
        )
    }

    if (sortOpen) {
        SortDialog(
            current = sortKey,
            strings = strings,
            onPick = { sortKey = it; sortOpen = false },
            onDismiss = { sortOpen = false }
        )
    }

    if (connectionOpen) {
        ConnectionDialog(
            baseUrl = repo.baseUrl(),
            configured = repo.isConfigured(),
            lastError = pcError,
            strings = strings,
            onDismiss = { connectionOpen = false },
            onRetry = { connectionOpen = false; reload() }
        )
    }

    if (rootsOpen) {
        PhoneRootsDialog(
            roots = roots,
            selectedUri = phoneRoot?.uri.orEmpty(),
            strings = strings,
            onPick = { root ->
                rootsOpen = false
                phoneRootUri = root.uri
                phoneDir = emptyList()
                selected = emptySet()
                reload()
            },
            onAdd = { treePicker.launch(null) },
            onRemove = { root ->
                repo.removeRoot(Uri.parse(root.uri))
                roots = repo.roots()
                if (phoneRootUri == root.uri) {
                    phoneRootUri = roots.firstOrNull()?.uri.orEmpty()
                    phoneDir = emptyList()
                }
                reload()
            },
            onDismiss = { rootsOpen = false }
        )
    }

    detailsTarget?.let { entry ->
        DetailsDialog(
            entry = entry,
            path = if (tab == 0) {
                (listOf(phoneRoot?.label.orEmpty()) + phoneDir + entry.name).joinToString("/")
            } else pcPathFor(entry),
            strings = strings,
            onDismiss = { detailsTarget = null }
        )
    }

    previewTarget?.let { target ->
        PreviewDialog(
            target = target,
            repo = repo,
            strings = strings,
            onDismiss = { previewTarget = null }
        )
    }

    if (pcPickerOpen) {
        PcFolderPickerDialog(
            repo = repo,
            initial = if (pcPath.isNotBlank()) pcPath else "",
            strings = strings,
            onPick = { dir ->
                pcPickerOpen = false
                queuePendingToPc(dir)
            },
            onDismiss = { pcPickerOpen = false }
        )
    }

    if (phonePickerOpen) {
        PhoneFolderPickerDialog(
            repo = repo,
            roots = roots,
            initialRootUri = phoneRoot?.uri.orEmpty(),
            initialDir = phoneDir,
            strings = strings,
            onAddRoot = { phonePickerOpen = false; treePicker.launch(null) },
            onPick = { root, dir ->
                phonePickerOpen = false
                queuePendingToPhone(root.uri, dir, root.label)
            },
            onDismiss = { phonePickerOpen = false }
        )
    }

    if (busyText != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text(busyText ?: "") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = VariantColors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Vent litt …", color = VariantColors.textSecondary)
                }
            },
            containerColor = VariantColors.sidebar
        )
    }

    // Toast
    toast?.let { text ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                text,
                modifier = Modifier
                    .padding(bottom = 118.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VariantColors.surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = VariantColors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Byggeklosser
// ---------------------------------------------------------------------------

@Composable
private fun SegmentButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    statusColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VariantColors.accent.copy(alpha = 0.18f) else VariantColors.sidebar)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) VariantColors.accent else VariantColors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) VariantColors.textPrimary else VariantColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        if (statusColor != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VariantColors.surfaceElevated,
        modifier = Modifier
            .height(28.dp)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BreadcrumbRow(
    tab: Int,
    pcPath: String,
    phoneRootLabel: String,
    phoneDir: List<String>,
    searchLabel: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRoot: () -> Unit,
    onCrumb: (Any) -> Unit,
    onRefresh: () -> Unit,
    onCloseSearch: () -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onUp, enabled = canGoUp, modifier = Modifier.size(34.dp)) {
            Icon(
                OmpIcons.ArrowUpward,
                contentDescription = null,
                tint = if (canGoUp) VariantColors.accent else VariantColors.textSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searchLabel.isNotBlank()) {
                Text(
                    searchLabel,
                    color = VariantColors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onCloseSearch, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary, modifier = Modifier.size(14.dp))
                }
            } else {
                Crumb(
                    text = if (tab == 0) phoneRootLabel.ifBlank { "Telefon" } else "PC",
                    active = (tab == 0 && phoneDir.isEmpty()) || (tab == 1 && pcPath.isBlank()),
                    onClick = onRoot
                )
                val crumbs: List<Pair<String, Any>> = if (tab == 0) {
                    phoneDir.mapIndexed { index, _ -> phoneDir[index] to phoneDir.subList(0, index + 1) }
                } else {
                    FsPaths.breadcrumbs(pcPath).map { it to it }
                }
                for ((label, target) in crumbs) {
                    Icon(
                        OmpIcons.ChevronRight,
                        null,
                        tint = VariantColors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    val text = if (tab == 0) label else FsPaths.fileName(label).ifBlank { label }
                    Crumb(text = text, active = false) { onCrumb(target) }
                }
            }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
            Icon(OmpIcons.Refresh, null, tint = VariantColors.textSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun Crumb(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) VariantColors.textPrimary else VariantColors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FsEntry,
    selected: Boolean,
    showRelative: Boolean,
    repo: FileManagerRepository,
    tab: Int,
    treeUri: String,
    phoneDir: List<String>,
    pcPath: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) VariantColors.accent.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileLeading(
            entry = entry,
            repo = repo,
            tab = tab,
            treeUri = treeUri,
            phoneDir = phoneDir,
            pcPath = pcPath,
            box = 38.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = VariantColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                showRelative && entry.relative.isNotBlank() -> entry.relative
                entry.dir -> FsFormat.date(entry.mtime).ifBlank { "" }
                else -> listOf(FsFormat.size(entry.size), FsFormat.date(entry.mtime))
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = VariantColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(VariantColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else if (entry.dir) {
            Icon(
                OmpIcons.ChevronRight,
                contentDescription = null,
                tint = VariantColors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(color = VariantColors.divider.copy(alpha = 0.45f), thickness = 0.5.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCell(
    entry: FsEntry,
    selected: Boolean,
    repo: FileManagerRepository,
    tab: Int,
    treeUri: String,
    phoneDir: List<String>,
    pcPath: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VariantColors.accent.copy(alpha = 0.18f) else VariantColors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(VariantColors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            FileLeading(
                entry = entry,
                repo = repo,
                tab = tab,
                treeUri = treeUri,
                phoneDir = phoneDir,
                pcPath = pcPath,
                box = 58.dp
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(VariantColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.name,
            color = VariantColors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (!entry.dir) {
            Text(
                FsFormat.size(entry.size),
                color = VariantColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FileLeading(
    entry: FsEntry,
    repo: FileManagerRepository,
    tab: Int,
    treeUri: String,
    phoneDir: List<String>,
    pcPath: String,
    box: androidx.compose.ui.unit.Dp
) {
    val image = !entry.dir && (FsMime.isImage(entry.name) || FsMime.isVideo(entry.name))
    val key = if (tab == 0) {
        "p:${entry.name}:$treeUri"
    } else {
        "pc:${FsPaths.join(pcPath, entry.name)}"
    }
    var bitmap by remember(key) { mutableStateOf(repo.cachedThumbnail(key)) }
    if (image && bitmap == null) {
        LaunchedEffect(key, entry.name) {
            bitmap = withContext(Dispatchers.IO) {
                if (tab == 0) {
                    val uri = runCatching {
                        repo.phoneChildUri(treeUri, phoneDir, entry.name)
                    }.getOrNull() ?: return@withContext null
                    repo.phoneThumbnail(uri, 96)
                } else {
                    repo.pcThumbnail(FsPaths.join(pcPath, entry.name), 96)
                }
            }
        }
    }
    Box(
        Modifier
            .size(box)
            .clip(RoundedCornerShape(7.dp))
            .background(if (bitmap != null) Color.Transparent else VariantColors.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (FsMime.isVideo(entry.name)) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(OmpIcons.Play, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Icon(
                entryIcon(entry),
                contentDescription = null,
                tint = entryTint(entry),
                modifier = Modifier.size((if (box > 44.dp) 26 else 21).dp)
            )
        }
    }
}

private fun entryIcon(entry: FsEntry) = when {
    entry.dir -> OmpIcons.Folder
    FsMime.isImage(entry.name) -> OmpIcons.Image
    FsMime.isVideo(entry.name) -> OmpIcons.FileVideo
    entry.name.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "m4a", "flac") -> OmpIcons.FileAudio
    entry.name.substringAfterLast('.', "").lowercase() in setOf("zip", "rar", "7z", "gz", "tar") -> OmpIcons.FileZip
    entry.name.substringAfterLast('.', "").lowercase() == "pdf" -> OmpIcons.FilePdf
    else -> OmpIcons.File
}

private fun entryTint(entry: FsEntry): Color = when {
    entry.dir -> VariantColors.accent
    FsMime.isImage(entry.name) -> Color(0xFFB388FF)
    FsMime.isVideo(entry.name) -> Color(0xFFFF80AB)
    entry.name.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "m4a", "flac") -> Color(0xFFFFB74D)
    entry.name.substringAfterLast('.', "").lowercase() == "pdf" -> Color(0xFFEF9A9A)
    else -> VariantColors.textSecondary
}

@Composable
private fun SelectionBar(
    count: Int,
    single: Boolean,
    strings: Strings,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(VariantColors.sidebar)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary, modifier = Modifier.size(18.dp))
        }
        Text(
            "$count ${strings.selectedCount}",
            color = VariantColors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
            Icon(OmpIcons.Copy, null, tint = VariantColors.textSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onCut, modifier = Modifier.size(38.dp)) {
            Icon(OmpIcons.Cut, null, tint = VariantColors.textSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onTransfer, modifier = Modifier.size(38.dp)) {
            Icon(OmpIcons.Upload, null, tint = VariantColors.accent, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { more = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = VariantColors.textSecondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = more,
                onDismissRequest = { more = false },
                containerColor = VariantColors.surface
            ) {
                DropdownMenuItem(
                    text = { Text(strings.renameFile) },
                    enabled = single,
                    onClick = { more = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Del") },
                    enabled = single,
                    onClick = { more = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text(strings.details) },
                    enabled = single,
                    onClick = { more = false; onDetails() }
                )
                HorizontalDivider(color = VariantColors.divider)
                DropdownMenuItem(
                    text = { Text(strings.delete, color = VariantColors.error) },
                    onClick = { more = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun TransferStrip(
    transfers: List<TransferItem>,
    active: List<TransferItem>,
    onClick: () -> Unit
) {
    val done = transfers.count { it.state == TransferState.DONE }
    val bytes = active.sumOf { it.bytesDone }
    val total = active.sumOf { it.sizeBytes }
    val speed = active.sumOf { it.bytesPerSecond }
    val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .background(VariantColors.sidebar)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (active.any { it.kind == TransferKind.UPLOAD }) OmpIcons.Upload else OmpIcons.Download,
                contentDescription = null,
                tint = if (active.isNotEmpty()) VariantColors.accent else VariantColors.online,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (active.isNotEmpty()) {
                    "${active.size} aktive" + if (done > 0) " · $done ferdige" else ""
                } else "$done ferdige",
                color = VariantColors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (speed > 0) {
                Text(
                    FsFormat.speed(speed),
                    color = VariantColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${FsFormat.size(bytes)} / ${FsFormat.size(total)}",
                color = VariantColors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Icon(
                OmpIcons.ArrowUpward,
                contentDescription = null,
                tint = VariantColors.textSecondary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = VariantColors.accent,
            trackColor = VariantColors.surfaceElevated
        )
    }
}

@Composable
private fun TransferSheet(
    transfers: List<TransferItem>,
    strings: Strings,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onOverwrite: (Long) -> Unit,
    onKeepBoth: (Long) -> Unit,
    onSkip: (Long) -> Unit
) {
    val active = transfers.filter {
        it.state == TransferState.RUNNING || it.state == TransferState.QUEUED ||
            it.state == TransferState.PAUSED || it.state == TransferState.CONFLICT
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Overføringer",
                color = VariantColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${active.size} aktive",
                color = VariantColors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = onClear) {
                Text("Fjern ferdige", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (transfers.isEmpty()) {
            Text(
                "Ingen overføringer",
                color = VariantColors.textSecondary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(transfers, key = { it.id }) { item ->
                    TransferRow(
                        item = item,
                        strings = strings,
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onCancel = { onCancel(item.id) },
                        onRemove = { onRemove(item.id) },
                        onOverwrite = { onOverwrite(item.id) },
                        onKeepBoth = { onKeepBoth(item.id) },
                        onSkip = { onSkip(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    item: TransferItem,
    strings: Strings,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onSkip: () -> Unit
) {
    val progress = if (item.sizeBytes > 0) {
        (item.bytesDone.toFloat() / item.sizeBytes).coerceIn(0f, 1f)
    } else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.kind == TransferKind.UPLOAD) OmpIcons.Upload else OmpIcons.Download,
                contentDescription = null,
                tint = when (item.state) {
                    TransferState.DONE -> VariantColors.online
                    TransferState.FAILED, TransferState.CANCELLED -> VariantColors.error
                    else -> VariantColors.accent
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = VariantColors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(stateLabel(item.state, strings))
                        if (item.sizeBytes > 0) {
                            append(" · ")
                            append(FsFormat.size(item.bytesDone))
                            append(" / ")
                            append(FsFormat.size(item.sizeBytes))
                        }
                        if (item.bytesPerSecond > 0) {
                            append(" · ")
                            append(FsFormat.speed(item.bytesPerSecond))
                        }
                    },
                    color = if (item.state == TransferState.FAILED) VariantColors.error else VariantColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (item.state) {
                TransferState.RUNNING -> {
                    IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                        Icon(OmpIcons.Pause, null, tint = VariantColors.textPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, null, tint = VariantColors.error, modifier = Modifier.size(18.dp))
                    }
                }
                TransferState.QUEUED -> {
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, null, tint = VariantColors.error, modifier = Modifier.size(18.dp))
                    }
                }
                TransferState.PAUSED -> {
                    IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                        Icon(OmpIcons.Play, null, tint = VariantColors.accent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, null, tint = VariantColors.error, modifier = Modifier.size(18.dp))
                    }
                }
                TransferState.FAILED, TransferState.CANCELLED, TransferState.SKIPPED -> {
                    IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                        Icon(OmpIcons.Refresh, null, tint = VariantColors.accent, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary, modifier = Modifier.size(17.dp))
                    }
                }
                else -> {
                    IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, null, tint = VariantColors.textSecondary, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        if (item.state != TransferState.CONFLICT && item.sizeBytes > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = if (item.state == TransferState.DONE) VariantColors.online else VariantColors.accent,
                trackColor = VariantColors.surfaceElevated
            )
        }
        if (item.error.isNotBlank() && item.state != TransferState.DONE) {
            Text(
                item.error,
                color = VariantColors.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.state == TransferState.CONFLICT) {
            if (item.conflictReason.isNotBlank()) {
                Text(
                    item.conflictReason,
                    color = VariantColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onOverwrite, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings.overwrite, color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onKeepBoth, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings.keepBoth, color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings.skip, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    HorizontalDivider(color = VariantColors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
}

// ---------------------------------------------------------------------------
// Tomtilstander
// ---------------------------------------------------------------------------

@Composable
private fun PhoneWelcome(
    strings: Strings,
    roots: List<FsRoot>,
    onAdd: () -> Unit,
    onOpen: (FsRoot) -> Unit,
    onRemove: (FsRoot) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Icon(OmpIcons.PhoneDevice, null, tint = VariantColors.accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "Telefonfiler",
            color = VariantColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(strings.addRootHint, color = VariantColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(OmpIcons.NewFolder, null, tint = VariantColors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.pickFolderBtn, color = VariantColors.accent)
        }
        if (roots.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            roots.forEach { root ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen(root) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(OmpIcons.Folder, null, tint = VariantColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(root.label, color = VariantColors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { onRemove(root) }, modifier = Modifier.size(32.dp)) {
                        Icon(OmpIcons.Trash, null, tint = VariantColors.error, modifier = Modifier.size(17.dp))
                    }
                }
                HorizontalDivider(color = VariantColors.divider.copy(alpha = 0.45f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun EmptyState(
    searching: Boolean,
    strings: Strings,
    onNewFolder: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (searching) OmpIcons.Search else OmpIcons.Folder,
            contentDescription = null,
            tint = VariantColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (searching) strings.searchNoHits else strings.folderEmpty,
            color = VariantColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!searching) {
                TextButton(onClick = onNewFolder) { Text(strings.newFolder, color = VariantColors.accent) }
            }
            TextButton(onClick = onRetry) { Text(strings.refresh, color = VariantColors.textSecondary) }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    detail: String,
    endpoint: String,
    strings: Strings,
    onRetry: () -> Unit,
    onConnection: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, null, tint = VariantColors.error, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = VariantColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            strings.pcOfflineHint,
            color = VariantColors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        if (endpoint.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                endpoint,
                color = VariantColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = VariantColors.textSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRetry) { Text(strings.retry, color = VariantColors.accent) }
            TextButton(onClick = onConnection) { Text(strings.connection, color = VariantColors.textSecondary) }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialoger
// ---------------------------------------------------------------------------

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = VariantColors.textPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(label, color = VariantColors.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VariantColors.accent,
                    unfocusedBorderColor = VariantColors.divider,
                    cursorColor = VariantColors.accent,
                    focusedTextColor = VariantColors.textPrimary,
                    unfocusedTextColor = VariantColors.textPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text(confirm, color = VariantColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.cancel, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun DeleteDialog(
    targets: List<String>,
    strings: Strings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deleteFilesTitle, color = VariantColors.textPrimary) },
        text = {
            Text(
                if (targets.size == 1) targets[0]
                else "${targets.size} ${strings.filesDeleteMultiple}: ${targets.joinToString(", ").take(160)}",
                color = VariantColors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(strings.delete, color = VariantColors.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun SortDialog(
    current: Int,
    strings: Strings,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        strings.sortName to 0,
        strings.sortDate to 1,
        strings.sortSize to 2,
        strings.sortType to 3
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sortBy, color = VariantColors.textPrimary) },
        text = {
            Column {
                options.forEach { (label, key) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(key) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == key, onClick = { onPick(key) })
                        Text(label, color = VariantColors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun ConnectionDialog(
    baseUrl: String,
    configured: Boolean,
    lastError: String,
    strings: Strings,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.connection, color = VariantColors.textPrimary) },
        text = {
            Column {
                Text("PC-adresse", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    baseUrl.ifBlank { strings.notConnected },
                    color = VariantColors.textPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (configured) "Paringstoken er lagret." else "Ingen paring funnet. Koble til PC-en på nytt.",
                    color = if (configured) VariantColors.online else VariantColors.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(strings.pcOfflineHint, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                if (lastError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(lastError, color = VariantColors.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text(strings.retry, color = VariantColors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close, color = VariantColors.textSecondary) } },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun PhoneRootsDialog(
    roots: List<FsRoot>,
    selectedUri: String,
    strings: Strings,
    onPick: (FsRoot) -> Unit,
    onAdd: () -> Unit,
    onRemove: (FsRoot) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.phoneFolders, color = VariantColors.textPrimary) },
        text = {
            Column {
                if (roots.isEmpty()) {
                    Text(strings.addRootHint, color = VariantColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                roots.forEach { root ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(root) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = root.uri == selectedUri, onClick = { onPick(root) })
                        Text(
                            root.label,
                            color = VariantColors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onRemove(root) }, modifier = Modifier.size(32.dp)) {
                            Icon(OmpIcons.Trash, null, tint = VariantColors.error, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(strings.addPhoneFolder, color = VariantColors.accent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun DetailsDialog(
    entry: FsEntry,
    path: String,
    strings: Strings,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, color = VariantColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DetailLine(strings.pathLabel, path)
                DetailLine(strings.typeLabel, if (entry.dir) strings.folderType else FsMime.of(entry.name))
                if (!entry.dir) DetailLine(strings.sizeLabel, FsFormat.size(entry.size))
                DetailLine(strings.modifiedLabel, FsFormat.date(entry.mtime).ifBlank { "–" })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close, color = VariantColors.accent) } },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = VariantColors.textPrimary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PreviewDialog(
    target: PreviewTarget,
    repo: FileManagerRepository,
    strings: Strings,
    onDismiss: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(target) {
        loading = true
        error = ""
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    target.pcPath != null -> {
                        val bytes = repo.downloadBytes(target.pcPath, 12L * 1024 * 1024)
                        decodeSample(bytes, 1400)
                    }
                    target.phoneUri != null -> {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(target.phoneUri)
                            ?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        context.contentResolver.openInputStream(target.phoneUri)
                            ?.use { BitmapFactory.decodeStream(it, null, options) }
                    }
                    else -> null
                }
            }.getOrNull()
        }
        loading = false
        if (bitmap == null) error = strings.unreadableImage
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(target.name, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 430.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> CircularProgressIndicator(color = VariantColors.accent)
                    bitmap != null -> AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                setImageBitmap(bitmap)
                                adjustViewBounds = true
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            }
                        }
                    )
                    else -> Text(error, color = VariantColors.textSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close, color = VariantColors.accent) } },
        containerColor = VariantColors.sidebar
    )
}

private fun decodeSample(data: ByteArray, maxDim: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
}.getOrNull()

@Composable
public fun PcFolderPickerDialog(
    repo: FileManagerRepository,
    initial: String,
    strings: Strings,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(initial) }
    var listing by remember { mutableStateOf<FsListing?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        loading = true
        error = ""
        repo.listPc(path).onSuccess { listing = it }
            .onFailure { error = repo.friendlyError(it); listing = null }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.pickPcFolder, color = VariantColors.textPrimary) },
        text = {
            Column(Modifier.size(width = 300.dp, height = 330.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { path = FsPaths.parent(path) },
                        enabled = path.isNotBlank(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            OmpIcons.ArrowUpward,
                            null,
                            tint = if (path.isNotBlank()) VariantColors.accent else VariantColors.textSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        path.ifBlank { "PC" },
                        color = VariantColors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                var showNewFolder by remember { mutableStateOf(false) }
                var newFolderName by remember { mutableStateOf("") }
                var creatingFolder by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showNewFolder = !showNewFolder }) {
                        Text(if (showNewFolder) "Avbryt" else "Ny mappe", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                    }
                    if (showNewFolder) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Mappenavn", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(36.dp),
                            textStyle = MaterialTheme.typography.labelSmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VariantColors.textPrimary,
                                unfocusedTextColor = VariantColors.textPrimary,
                                focusedBorderColor = VariantColors.accent,
                                unfocusedBorderColor = VariantColors.divider
                            )
                        )
                        TextButton(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    creatingFolder = true
                                    scope.launch {
                                        val newPath = if (path.isBlank()) newFolderName else FsPaths.join(path, newFolderName)
                                        repo.pcMkdir(newPath).onSuccess {
                                            path = newPath
                                            newFolderName = ""
                                            showNewFolder = false
                                        }.onFailure { e ->
                                            error = repo.friendlyError(e)
                                        }
                                        creatingFolder = false
                                    }
                                }
                            },
                            enabled = newFolderName.isNotBlank() && !creatingFolder
                        ) {
                            if (creatingFolder) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            else Text("Opprett", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                when {
                    error.isNotBlank() -> {
                        Text(error, color = VariantColors.error, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { path = path }) { Text(strings.retry, color = VariantColors.accent) }
                    }
                    listing == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VariantColors.accent)
                    }
                    else -> {
                        val dirs = listing!!.entries.filter { it.dir }
                        if (dirs.isEmpty()) {
                            Text(strings.folderEmpty, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(dirs, key = { it.name }) { entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                path = if (path.isBlank()) entry.name
                                                else FsPaths.join(path, entry.name)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            OmpIcons.Folder,
                                            null,
                                            tint = VariantColors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            entry.name,
                                            color = VariantColors.textPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(path) },
                enabled = path.isNotBlank() && error.isBlank()
            ) { Text(strings.pickThis, color = VariantColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

@Composable
private fun PhoneFolderPickerDialog(
    repo: FileManagerRepository,
    roots: List<FsRoot>,
    initialRootUri: String,
    initialDir: List<String>,
    strings: Strings,
    onAddRoot: () -> Unit,
    onPick: (FsRoot, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var rootUri by remember { mutableStateOf(initialRootUri.ifBlank { roots.firstOrNull()?.uri.orEmpty() }) }
    var dir by remember { mutableStateOf(initialDir) }
    var listing by remember { mutableStateOf<List<FsEntry>?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val root = roots.firstOrNull { it.uri == rootUri }

    LaunchedEffect(rootUri, dir) {
        if (rootUri.isBlank()) { listing = emptyList(); return@LaunchedEffect }
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) { runCatching { repo.childrenPhone(rootUri, dir) } }
        result.onSuccess { listing = it }
            .onFailure { error = repo.friendlyError(it); listing = null }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velg mappe på telefonen", color = VariantColors.textPrimary) },
        text = {
            Column(Modifier.size(width = 300.dp, height = 340.dp)) {
                if (roots.isEmpty()) {
                    Text(strings.addRootHint, color = VariantColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onAddRoot) { Text(strings.addPhoneFolder, color = VariantColors.accent) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (dir.isNotEmpty()) dir = dir.dropLast(1) else rootUri = ""
                            },
                            enabled = rootUri.isNotBlank(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                OmpIcons.ArrowUpward,
                                null,
                                tint = if (rootUri.isNotBlank()) VariantColors.accent else VariantColors.textSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            if (rootUri.isBlank()) "Telefonmapper"
                            else root?.label.orEmpty() + if (dir.isEmpty()) "" else "/" + dir.joinToString("/"),
                            color = VariantColors.textPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    when {
                        error.isNotBlank() -> Text(error, color = VariantColors.error, style = MaterialTheme.typography.labelSmall)
                        rootUri.isBlank() -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(roots, key = { it.uri }) { item ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { rootUri = item.uri; dir = emptyList() }
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(OmpIcons.Folder, null, tint = VariantColors.accent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(item.label, color = VariantColors.textPrimary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        listing == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VariantColors.accent)
                        }
                        else -> {
                            val dirs = (listing ?: emptyList()).filter { it.dir }
                            if (dirs.isEmpty()) {
                                Text(strings.folderEmpty, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                            } else {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(dirs, key = { it.name }) { entry ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { dir = dir + entry.name }
                                                .padding(vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                OmpIcons.Folder,
                                                null,
                                                tint = VariantColors.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                entry.name,
                                                color = VariantColors.textPrimary,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onAddRoot, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(strings.addPhoneFolder, color = VariantColors.accent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { root?.let { onPick(it, dir) } },
                enabled = root != null && error.isBlank()
            ) { Text(strings.pickThis, color = VariantColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}
