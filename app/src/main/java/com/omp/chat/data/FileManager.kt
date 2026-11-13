package com.omp.chat.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

// ---------------------------------------------------------------------------
// Modeller
// ---------------------------------------------------------------------------

data class FsEntry(
    val name: String,
    val dir: Boolean,
    val size: Long = 0,
    val mtime: Long = 0,
    /** Relativ sti fra søkeroten (bare brukt i søkeresultater). */
    val relative: String = ""
)

data class FsPlace(val label: String, val path: String)

data class FsListing(
    val path: String,
    val entries: List<FsEntry>,
    val roots: Boolean = false,
    val places: List<FsPlace> = emptyList(),
    val defaultPath: String = "",
    val platform: String = ""
)

data class FsStorage(val free: Long, val total: Long)

data class FsRoot(val label: String, val uri: String)

data class FsWalkEntry(
    val relative: String,
    val dir: Boolean,
    val size: Long,
    val uri: Uri? = null
)

enum class TransferKind { DOWNLOAD, UPLOAD }

enum class TransferState { QUEUED, RUNNING, PAUSED, CONFLICT, DONE, FAILED, CANCELLED, SKIPPED }

enum class ConflictMode { SKIP, OVERWRITE, RENAME }

data class TransferItem(
    val id: Long,
    val kind: TransferKind,
    /** Filnavn hos mottaker. */
    val name: String,
    /** Kort visningssti (kilde/mål) for køen. */
    val displayPath: String = "",
    /** Full PC-sti til filen. */
    val pcPath: String = "",
    val treeUri: String = "",
    val treeLabel: String = "",
    /** Mappe på telefonen, relativ til tree-uri. */
    val relativeDir: List<String> = emptyList(),
    val sizeBytes: Long = 0,
    val state: TransferState = TransferState.QUEUED,
    val bytesDone: Long = 0,
    val bytesPerSecond: Long = 0,
    val error: String = "",
    val sha256: String = "",
    val verified: Boolean = false,
    val conflictMode: ConflictMode? = null,
    val conflictReason: String = ""
)

// ---------------------------------------------------------------------------
// Rene hjelpere (testbare)
// ---------------------------------------------------------------------------

object FsPaths {

    fun sep(path: String): String =
        if (path.contains('\\') || (path.length >= 2 && path[1] == ':')) "\\" else "/"

    fun join(base: String, vararg parts: String): String {
        var result = base
        for (part in parts) {
            if (part.isEmpty()) continue
            val clean = part.trim('\\', '/')
            if (clean.isEmpty()) continue
            result = when {
                result.isEmpty() -> clean
                result.endsWith("\\") || result.endsWith("/") -> result + clean
                else -> result + sep(result) + clean
            }
        }
        return result
    }

    fun fileName(path: String): String {
        val trimmed = path.trimEnd('\\', '/')
        val cut = max(trimmed.lastIndexOf('\\'), trimmed.lastIndexOf('/'))
        return if (cut < 0) trimmed else trimmed.substring(cut + 1)
    }

    /** Foreldermappe. Tom streng betyr "vis rot/drev". */
    fun parent(path: String): String {
        val trimmed = path.trimEnd('\\', '/')
        if (trimmed.isEmpty() || trimmed == "/") return ""
        if (trimmed.length == 2 && trimmed[1] == ':') return ""
        val cut = max(trimmed.lastIndexOf('\\'), trimmed.lastIndexOf('/'))
        if (cut < 0) return ""
        if (cut == 0) return "/"
        val head = trimmed.substring(0, cut)
        return if (head.length == 2 && head[1] == ':') head + "\\" else head
    }

    fun isInside(child: String, parent: String): Boolean {
        if (parent.isBlank()) return false
        val a = child.replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
        val b = parent.replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
        return a != b && a.startsWith("$b/")
    }

    /** "rapport.pdf" -> "rapport (1).pdf" når navnet er opptatt. */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (taken.none { it.equals(name, ignoreCase = true) }) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$stem ($index)$ext"
            if (taken.none { it.equals(candidate, ignoreCase = true) }) return candidate
            index += 1
        }
    }

    fun breadcrumbs(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        val sep = sep(path)
        val root = if (sep == "\\") {
            if (path.length >= 2 && path[1] == ':') path.substring(0, 2) + "\\" else ""
        } else "/"
        val body = path.removePrefix(root).trim('\\', '/')
        val parts = body.split('\\', '/').filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var current = root
        for (part in parts) {
            current = if (current.isEmpty()) part else join(current, part)
            out.add(current)
        }
        return out
    }
}

object FsFormat {

    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000_000L -> dec(bytes / 1e12) + " TB"
        bytes >= 1_000_000_000L -> dec(bytes / 1e9) + " GB"
        bytes >= 1_000_000L -> dec(bytes / 1e6) + " MB"
        bytes >= 1_000L -> dec(bytes / 1e3) + " kB"
        else -> "$bytes B"
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "" else size(bytesPerSecond) + "/s"

    fun date(ms: Long): String =
        if (ms <= 0) "" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    fun percent(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done.coerceAtLeast(0) * 100) / total).coerceIn(0, 100).toInt()

    private fun dec(value: Double): String =
        String.format(Locale.getDefault(), "%.1f", value)
}

object FsMime {
    fun of(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "txt", "md", "log" -> "text/plain"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
            setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
            setOf("mp4", "webm", "mkv", "avi", "mov", "3gp")
}

// ---------------------------------------------------------------------------
// SAF (telefonens filsystem)
// ---------------------------------------------------------------------------

object FsSaf {

    private const val PREF = "omp_fs"
    private const val KEY_ROOTS = "roots"

    fun roots(context: Context): List<FsRoot> = try {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ROOTS, "") ?: ""
        raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val at = line.indexOf('|')
            if (at > 0) {
                val label = line.substring(0, at)
                val uri = line.substring(at + 1)
                if (uri.isBlank()) null else FsRoot(label, uri)
            } else if (line.isNotBlank()) {
                FsRoot("Mappe", line)
            } else null
        }
    } catch (_: Throwable) { emptyList() }

    fun addRoot(context: Context, name: String, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = (prefs.getString(KEY_ROOTS, "") ?: "")
            .split("\n").filter { it.isNotBlank() }.filterNot { it.endsWith("|$uri") }
        val label = name.trim().ifBlank { "Mappe" }
        prefs.edit().putString(KEY_ROOTS, (list + "$label|$uri").joinToString("\n")).apply()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun removeRoot(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = (prefs.getString(KEY_ROOTS, "") ?: "")
            .split("\n").filter { it.isNotBlank() }.filterNot { it.endsWith("|$uri") }
        prefs.edit().putString(KEY_ROOTS, list.joinToString("\n")).apply()
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    fun children(context: Context, rootUri: Uri, relativeDir: List<String>): List<FsEntry> {
        val folder = folder(context, rootUri, relativeDir) ?: return emptyList()
        return folder.listFiles().mapNotNull { entry ->
            val name = entry.name ?: return@mapNotNull null
            if (name.contains(".ompdownload")) return@mapNotNull null
            FsEntry(
                name = name,
                dir = entry.isDirectory,
                size = if (entry.isFile) entry.length() else 0,
                mtime = entry.lastModified()
            )
        }
    }

    fun folder(context: Context, rootUri: Uri, relativeDir: List<String>): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        for (segment in relativeDir) {
            current = current.findFile(segment) ?: return null
        }
        return if (current.isDirectory) current else null
    }

    fun find(context: Context, rootUri: Uri, relativeDir: List<String>, name: String): DocumentFile? =
        folder(context, rootUri, relativeDir)?.findFile(name)

    fun ensureDir(context: Context, rootUri: Uri, relativeDir: List<String>): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        for (segment in relativeDir) {
            current = current.findFile(segment) ?: current.createDirectory(segment) ?: return null
        }
        return current
    }

    fun mkdir(context: Context, rootUri: Uri, relativeDir: List<String>, name: String): Boolean {
        val parent = folder(context, rootUri, relativeDir) ?: return false
        if (parent.findFile(name) != null) throw IOException("Finnes allerede: $name")
        return parent.createDirectory(name) != null
    }

    fun rename(context: Context, rootUri: Uri, relativeDir: List<String>, from: String, to: String): Boolean {
        val parent = folder(context, rootUri, relativeDir) ?: return false
        if (parent.findFile(to) != null) throw IOException("Finnes allerede: $to")
        val doc = parent.findFile(from) ?: return false
        return doc.renameTo(to)
    }

    fun delete(context: Context, rootUri: Uri, relativeDir: List<String>, name: String): Boolean {
        val parent = folder(context, rootUri, relativeDir) ?: return false
        val doc = parent.findFile(name) ?: return false
        return doc.delete()
    }

    fun fileUri(context: Context, rootUri: Uri, relativeDir: List<String>, name: String): Uri? =
        folder(context, rootUri, relativeDir)?.findFile(name)?.uri

    /** Kopierer/flytter dokument(er) inn i en målmappe, rekursivt. */
    fun copyMove(
        context: Context,
        rootUri: Uri,
        fromDir: List<String>,
        names: List<String>,
        toDir: List<String>,
        cut: Boolean
    ): List<String> {
        val source = folder(context, rootUri, fromDir) ?: throw IOException("Kildemappen finnes ikke")
        val target = ensureDir(context, rootUri, toDir) ?: throw IOException("Målmappen finnes ikke")
        val taken = target.listFiles().mapNotNull { it.name }.toMutableSet()
        val copied = ArrayList<String>()
        for (name in names) {
            val doc = source.findFile(name) ?: throw IOException("Fant ikke $name")
            val finalName = FsPaths.uniqueName(name, taken)
            copyDocument(context, doc, target, finalName, cut)
            taken.add(finalName)
            copied.add(finalName)
        }
        return copied
    }

    private fun copyDocument(context: Context, source: DocumentFile, parent: DocumentFile, name: String, cut: Boolean) {
        if (source.isDirectory) {
            val dir = parent.findFile(name)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(name)
                ?: throw IOException("Klarte ikke opprette mappen $name")
            val children = source.listFiles()
            for (child in children) {
                val childName = child.name ?: continue
                copyDocument(context, child, dir, childName, false)
            }
            if (cut) source.delete()
            return
        }
        val target = parent.findFile(name) ?: parent.createFile(FsMime.of(name), name)
            ?: throw IOException("Klarte ikke opprette $name")
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                input.copyTo(output, 256 * 1024)
            } ?: throw IOException("Klarte ikke åpne målfilen $name")
        } ?: throw IOException("Klarte ikke å åpne $name")
        if (cut) source.delete()
    }

    /** Rekursiv liste for mappeopplasting. */
    fun walk(context: Context, rootUri: Uri, relativeDir: List<String>, maxEntries: Int = 30000): List<FsWalkEntry> {
        val root = folder(context, rootUri, relativeDir) ?: return emptyList()
        val out = ArrayList<FsWalkEntry>()
        val stack = ArrayDeque<Pair<DocumentFile, String>>()
        stack.addLast(root to "")
        while (stack.isNotEmpty() && out.size < maxEntries) {
            val (dir, rel) = stack.removeLast()
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                if (name.contains(".ompdownload")) continue
                val childRel = if (rel.isEmpty()) name else "$rel/$name"
                if (child.isDirectory) {
                    out.add(FsWalkEntry(childRel, true, 0, child.uri))
                    stack.addLast(child to childRel)
                } else if (child.isFile) {
                    out.add(FsWalkEntry(childRel, false, child.length(), child.uri))
                }
                if (out.size >= maxEntries) break
            }
        }
        return out
    }

    /**
     * Sikkert midlertidig navn. Leverandører som MediaStore legger på en ekstra
     * filendelse når navnet ikke passer MIME-typen, så vi beholder den originale
     * endelsen etter ".ompdownload" (f.eks. "bilde.ompdownload.png").
     */
    fun tempName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) + ".ompdownload" + name.substring(dot)
        else name + ".ompdownload"
    }

    fun tempDoc(context: Context, rootUri: Uri, relativeDir: List<String>, name: String): DocumentFile? {
        val parent = folder(context, rootUri, relativeDir) ?: return null
        val temp = tempName(name)
        parent.findFile(temp)?.let { if (it.isFile) return it }
        val created = runCatching { parent.createFile(FsMime.of(name), temp) }.getOrNull() ?: return null
        if (created.name != temp) {
            val renamed = runCatching { created.renameTo(temp) }.getOrDefault(false)
            if (!renamed || created.name != temp) {
                runCatching { created.delete() }
                return null
            }
        }
        return created
    }

    fun renameTemp(context: Context, tempDoc: DocumentFile, finalName: String, overwrite: Boolean): Boolean {
        val parent = tempDoc.parentFile ?: return false
        val existing = parent.findFile(finalName)
        if (existing != null) {
            if (!overwrite) return false
            if (!existing.delete()) return false
        }
        if (parent.findFile(finalName) != null) return false
        return runCatching { tempDoc.renameTo(finalName) }.getOrDefault(false)
    }

    fun deleteTemp(context: Context, rootUri: Uri, relativeDir: List<String>, name: String) {
        val parent = folder(context, rootUri, relativeDir) ?: return
        runCatching { parent.findFile(tempName(name))?.delete() }
    }
}

// ---------------------------------------------------------------------------
// Repository: PC (bro), telefon (SAF) og overføringskø
// ---------------------------------------------------------------------------

class FileManagerRepository(
    private val settings: SettingsStore,
    appContext: Context
) {

    private val ctx: Context = appContext.applicationContext
    private val client = BridgeClient({ settings.baseUrl() }, { settings.token() })

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers

    private val lock = Any()
    private val running = HashSet<Long>()
    private val paused = HashSet<Long>()
    private val cancelled = HashSet<Long>()
    private val conflicts = HashMap<Long, ConflictMode>()
    private var seq = 0L

    private val thumbs = LruCache<String, Bitmap>(48)

    companion object {
        private const val MAX_ACTIVE = 2
        private const val RETRIES = 5
    }

    // ============================ PC (broen) ================================

    fun baseUrl(): String = settings.baseUrl()

    fun isConfigured(): Boolean = settings.baseUrl().isNotBlank() && !settings.token().isNullOrBlank()

    fun friendlyError(error: Throwable): String {
        val raw = error.message ?: error.javaClass.simpleName
        return when {
            error is java.net.ConnectException ||
                raw.contains("Failed to connect", true) ||
                raw.contains("Connection refused", true) ||
                raw.contains("ECONNREFUSED", true) ->
                "Får ikke kontakt med PC-broen. Sjekk at Nimay OMP kjører på PC-en og at telefonen er på samme nett (LAN eller Tailscale)."
            error is java.net.UnknownHostException ->
                "Fant ikke PC-adressen. Sjekk adressen i Innstillinger."
            error is SocketTimeoutException ->
                "PC-en svarte ikke i tide. Prøv igjen."
            else -> raw
        }
    }

    private suspend fun apiJson(
        method: String,
        path: String,
        body: JsonObject? = null,
        timeoutSeconds: Long = 60
    ): JsonObject = withContext(Dispatchers.IO) {
        client.callJson(method, path, body, timeoutSeconds)
    }

    suspend fun listPc(path: String): Result<FsListing> = runCatching {
        val json = apiJson("GET", "/api/fs/list?path=" + enc(path), null, 120)
        val entries = json.arr("entries").mapNotNull { element ->
            element.asObjectOrNull()?.let {
                FsEntry(
                    name = it.str("name"),
                    dir = it.str("type") == "dir",
                    size = it.long("size"),
                    mtime = it.long("mtime")
                )
            }
        }
        val places = json.arr("places").mapNotNull { element ->
            element.asObjectOrNull()?.let { FsPlace(it.str("label"), it.str("path")) }
        }
        FsListing(
            path = json.str("path").ifBlank { path },
            entries = entries,
            roots = json.bool("roots"),
            places = places,
            defaultPath = json.str("defaultPath"),
            platform = json.str("platform")
        )
    }

    /** Hjemmemappen på PC-en (brukes som startpunkt for PC-fanen). */
    suspend fun pcHome(): String {
        val listing = listPc("").getOrNull() ?: return ""
        return listing.defaultPath.ifBlank {
            listing.places.firstOrNull()?.path ?: listing.entries.firstOrNull()?.name.orEmpty()
        }
    }

    suspend fun pcStorage(path: String): Result<FsStorage> = runCatching {
        val json = apiJson("GET", "/api/fs/storage?path=" + enc(path), null, 45)
        FsStorage(free = json.long("free"), total = json.long("total"))
    }

    suspend fun searchPc(path: String, query: String): Result<FsListing> = runCatching {
        val json = apiJson(
            "GET",
            "/api/fs/search?path=" + enc(path) + "&query=" + enc(query) + "&depth=8&max=150",
            null, 120
        )
        val entries = json.arr("entries").mapNotNull { element ->
            element.asObjectOrNull()?.let {
                FsEntry(
                    name = it.str("name"),
                    dir = it.str("type") == "dir",
                    size = it.long("size"),
                    mtime = it.long("mtime"),
                    relative = it.str("relative")
                )
            }
        }
        FsListing(path = path, entries = entries)
    }

    suspend fun pcStat(path: String): Result<FsEntry> = runCatching {
        val json = apiJson("GET", "/api/fs/tree?path=" + enc(path), null, 45)
        FsEntry(
            name = json.str("name").ifBlank { FsPaths.fileName(path) },
            dir = json.str("type") == "dir",
            size = json.long("size"),
            mtime = json.long("mtime")
        )
    }

    suspend fun pcMkdir(path: String): Result<Unit> = runCatching {
        val body = JsonObject().apply { addProperty("path", path) }
        apiJson("POST", "/api/fs/mkdir", body, 60)
        Unit
    }

    suspend fun pcRename(from: String, to: String): Result<Unit> = runCatching {
        val body = JsonObject().apply {
            addProperty("from", from)
            addProperty("to", to)
        }
        apiJson("POST", "/api/fs/rename", body, 60)
        Unit
    }

    suspend fun pcDelete(paths: List<String>): Result<Unit> = runCatching {
        for (path in paths) {
            val body = JsonObject().apply { addProperty("path", path) }
            apiJson("POST", "/api/fs/delete", body, 600)
        }
        Unit
    }

    suspend fun pcCopyMove(from: List<String>, toDir: String, cut: Boolean): Result<Unit> = runCatching {
        for (path in from) {
            val body = JsonObject().apply {
                addProperty("from", path)
                addProperty("to", toDir)
            }
            val json = apiJson("POST", if (cut) "/api/fs/move" else "/api/fs/copy", body, 60)
            val jobId = json.str("id")
            if (jobId.isNotBlank()) awaitFsJob(jobId)
        }
        Unit
    }

    private suspend fun awaitFsJob(id: String) {
        val deadline = System.currentTimeMillis() + 15 * 60 * 1000
        while (System.currentTimeMillis() < deadline) {
            val json = apiJson("POST", "/api/fs/job", JsonObject().apply { addProperty("id", id) }, 30)
            val job = json.getAsJsonObject("job") ?: JsonObject()
            when (job.str("stage")) {
                "done" -> return
                "error" -> throw IOException(job.str("error").ifBlank { "Kopieringen feilet" })
                "cancelled" -> throw IOException("Kopieringen ble avbrutt")
            }
            delay(300)
        }
        throw IOException("Kopieringen tok for lang tid")
    }

    suspend fun pcUniqueName(dir: String, name: String): String {
        val listing = listPc(dir).getOrNull() ?: return name
        return FsPaths.uniqueName(name, listing.entries.map { it.name }.toSet())
    }

    suspend fun pcWalk(path: String): Result<Pair<List<FsWalkEntry>, Long>> = runCatching {
        val json = apiJson("GET", "/api/fs/walk?path=" + enc(path) + "&max=30000", null, 180)
        val entries = json.arr("entries").mapNotNull { element ->
            element.asObjectOrNull()?.let {
                FsWalkEntry(it.str("relative"), it.str("type") == "dir", it.long("size"))
            }
        }
        entries to json.long("total")
    }

    suspend fun pcSha(path: String): String? = runCatching {
        apiJson("GET", "/api/files/hash?path=" + enc(path), null, 900).str("sha256")
    }.getOrNull()

    suspend fun downloadBytes(pcPath: String, capBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(joinUrl("/api/files/download?path=" + enc(pcPath)))
            .apply { authHeader() }
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(bridgeError(response.code, response.body?.string().orEmpty()))
            }
            val body = response.body ?: throw IOException("Tom respons fra PC-en")
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > capBytes) throw IOException("Filen er for stor for forhåndsvisning")
                    out.write(buffer, 0, read)
                }
            }
            out.toByteArray()
        }
    }

    /** Kopierer en PC-fil til cache og gir en delbar content-uri. */
    suspend fun sharePcFile(pcPath: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val target = File(dir, FsPaths.fileName(pcPath).ifBlank { "fil" })
        val request = Request.Builder()
            .url(joinUrl("/api/files/download?path=" + enc(pcPath)))
            .apply { authHeader() }
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(bridgeError(response.code, response.body?.string().orEmpty()))
            }
            val body = response.body ?: throw IOException("Tom respons fra PC-en")
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
        }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", target)
    }

    // ============================ Telefon (SAF) =============================

    fun roots(): List<FsRoot> = FsSaf.roots(ctx)

    fun addRoot(label: String, uri: Uri) = FsSaf.addRoot(ctx, label, uri)

    fun removeRoot(uri: Uri) = FsSaf.removeRoot(ctx, uri)

    fun childrenPhone(rootUri: String, relativeDir: List<String>): List<FsEntry> =
        FsSaf.children(ctx, Uri.parse(rootUri), relativeDir)

    fun phoneStorage(): FsStorage = try {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        FsStorage(stat.availableBytes, stat.totalBytes)
    } catch (_: Throwable) { FsStorage(0, 0) }

    fun phoneMkdir(rootUri: String, relativeDir: List<String>, name: String): Result<Unit> = runCatching {
        if (!FsSaf.mkdir(ctx, Uri.parse(rootUri), relativeDir, name)) throw IOException("Klarte ikke opprette mappen")
    }

    fun phoneRename(rootUri: String, relativeDir: List<String>, from: String, to: String): Result<Unit> = runCatching {
        if (!FsSaf.rename(ctx, Uri.parse(rootUri), relativeDir, from, to)) throw IOException("Klarte ikke endre navn")
    }

    fun phoneDelete(rootUri: String, relativeDir: List<String>, names: List<String>): Result<Unit> = runCatching {
        for (name in names) {
            if (!FsSaf.delete(ctx, Uri.parse(rootUri), relativeDir, name)) {
                throw IOException("Klarte ikke slette $name")
            }
        }
    }

    fun phoneCopyMove(
        rootUri: String,
        fromDir: List<String>,
        names: List<String>,
        toDir: List<String>,
        cut: Boolean
    ): Result<List<String>> = runCatching {
        FsSaf.copyMove(ctx, Uri.parse(rootUri), fromDir, names, toDir, cut)
    }

    fun phoneChildUri(rootUri: String, relativeDir: List<String>, name: String): Uri? =
        FsSaf.fileUri(ctx, Uri.parse(rootUri), relativeDir, name)

    // ============================ Miniatyrbilder ============================

    fun cachedThumbnail(key: String): Bitmap? = thumbs.get(key)

    suspend fun phoneThumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "p:$uri:$sizePx"
        thumbs.get(key)?.let { return@withContext it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                ctx.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, options)
                }
            }
        }.getOrNull()
        if (bitmap != null) thumbs.put(key, bitmap)
        bitmap
    }

    suspend fun pcThumbnail(pcPath: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "pc:$pcPath:$sizePx"
        thumbs.get(key)?.let { return@withContext it }
        val bitmap = runCatching {
            val bytes = downloadBytes(pcPath, 8L * 1024 * 1024)
            decodeSample(bytes, sizePx)
        }.getOrNull()
        if (bitmap != null) thumbs.put(key, bitmap)
        bitmap
    }

    private fun decodeSample(data: ByteArray, maxDim: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }.getOrNull()

    // ============================ Overføringer ==============================

    suspend fun downloadEntries(
        pcDir: String,
        entries: List<FsEntry>,
        treeLabel: String,
        treeUri: String,
        phoneDir: List<String>
    ): Result<Int> = runCatching {
        var queued = 0
        val tree = Uri.parse(treeUri)
        for (entry in entries) {
            val full = if (entry.name.startsWith("/") || entry.name.matches(Regex("^[A-Za-z]:.*"))) {
                entry.name
            } else FsPaths.join(pcDir, entry.name)
            if (entry.dir) {
                val walked = pcWalk(full).getOrThrow()
                for (child in walked.first) {
                    if (child.dir) continue
                    val segments = child.relative.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) continue
                    val name = segments.last()
                    val dirs = phoneDir + entry.name + segments.dropLast(1)
                    // Opprett målmappen(e) med en gang, ellers feiler overføringen
                    // før den har startet (SAF oppretter ikke mellomliggende mapper selv).
                    FsSaf.ensureDir(ctx, tree, dirs)
                        ?: throw IOException("Klarte ikke opprette mappen ${dirs.joinToString("/")}")
                    enqueue(TransferItem(
                        id = nextId(),
                        kind = TransferKind.DOWNLOAD,
                        name = name,
                        displayPath = "$treeLabel/${(phoneDir + entry.name).joinToString("/")}",
                        pcPath = FsPaths.join(full, *segments.toTypedArray()),
                        treeUri = treeUri,
                        treeLabel = treeLabel,
                        relativeDir = dirs,
                        sizeBytes = child.size
                    ))
                    queued += 1
                    if (queued >= 5000) break
                }
            } else {
                enqueue(TransferItem(
                    id = nextId(),
                    kind = TransferKind.DOWNLOAD,
                    name = entry.name,
                    displayPath = "$treeLabel/${phoneDir.joinToString("/")}".trimEnd('/'),
                    pcPath = full,
                    treeUri = treeUri,
                    treeLabel = treeLabel,
                    relativeDir = phoneDir,
                    sizeBytes = entry.size
                ))
                queued += 1
            }
        }
        pump()
        queued
    }

    suspend fun uploadEntries(
        treeUri: String,
        treeLabel: String,
        phoneDir: List<String>,
        entries: List<FsEntry>,
        pcDir: String
    ): Result<Int> = runCatching {
        var queued = 0
        for (entry in entries) {
            if (entry.dir) {
                val walked = FsSaf.walk(ctx, Uri.parse(treeUri), phoneDir + entry.name)
                for (child in walked) {
                    if (child.dir) continue
                    val segments = child.relative.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) continue
                    val name = segments.last()
                    val targetDir = FsPaths.join(pcDir, entry.name, *segments.dropLast(1).toTypedArray())
                    enqueue(TransferItem(
                        id = nextId(),
                        kind = TransferKind.UPLOAD,
                        name = name,
                        displayPath = "$treeLabel/${(phoneDir + entry.name).joinToString("/")}",
                        pcPath = FsPaths.join(targetDir, name),
                        treeUri = treeUri,
                        treeLabel = treeLabel,
                        relativeDir = phoneDir + entry.name + segments.dropLast(1),
                        sizeBytes = child.size
                    ))
                    queued += 1
                    if (queued >= 5000) break
                }
            } else {
                enqueue(TransferItem(
                    id = nextId(),
                    kind = TransferKind.UPLOAD,
                    name = entry.name,
                    displayPath = "$treeLabel/${phoneDir.joinToString("/")}".trimEnd('/'),
                    pcPath = FsPaths.join(pcDir, entry.name),
                    treeUri = treeUri,
                    treeLabel = treeLabel,
                    relativeDir = phoneDir,
                    sizeBytes = entry.size
                ))
                queued += 1
            }
        }
        pump()
        queued
    }

    fun pause(id: Long) {
        synchronized(lock) {
            paused.add(id)
            cancelled.remove(id)
        }
        update(id) {
            if (it.state == TransferState.QUEUED || it.state == TransferState.RUNNING) {
                it.copy(state = TransferState.PAUSED, bytesPerSecond = 0)
            } else it
        }
    }

    fun resume(id: Long) {
        synchronized(lock) {
            paused.remove(id)
            cancelled.remove(id)
        }
        update(id) {
            if (it.state == TransferState.PAUSED || it.state == TransferState.FAILED) {
                it.copy(state = TransferState.QUEUED, error = "")
            } else it
        }
        pump()
    }

    fun cancel(id: Long) {
        synchronized(lock) {
            cancelled.add(id)
            paused.remove(id)
            conflicts.remove(id)
        }
        update(id) { it.copy(state = TransferState.CANCELLED, bytesPerSecond = 0) }
    }

    fun resolveConflict(id: Long, mode: ConflictMode) {
        val item = item(id) ?: return
        if (mode == ConflictMode.SKIP) {
            synchronized(lock) { conflicts.remove(id) }
            update(id) { it.copy(state = TransferState.SKIPPED, error = "Hoppet over", bytesPerSecond = 0) }
            return
        }
        synchronized(lock) {
            conflicts[id] = mode
            paused.remove(id)
            cancelled.remove(id)
        }
        if (mode == ConflictMode.RENAME) {
            // Behold CONFLICT-status til nytt navn er beregnet, ellers kan
            // motoren starte med det gamle navnet mens oppslaget pågår.
            scope.launch {
                val fresh = item(id) ?: return@launch
                val unique = if (fresh.kind == TransferKind.DOWNLOAD) {
                    val folder = FsSaf.folder(ctx, Uri.parse(fresh.treeUri), fresh.relativeDir)
                    FsPaths.uniqueName(
                        fresh.name,
                        folder?.listFiles()?.mapNotNull { it.name }?.toSet() ?: emptySet()
                    )
                } else {
                    pcUniqueName(FsPaths.parent(fresh.pcPath), fresh.name)
                }
                synchronized(lock) { conflicts.remove(id) }
                update(id) {
                    it.copy(
                        name = unique,
                        conflictMode = null,
                        state = TransferState.QUEUED,
                        error = "",
                        conflictReason = "",
                        bytesPerSecond = 0
                    )
                }
                pump()
            }
        } else {
            update(id) {
                it.copy(
                    conflictMode = mode,
                    state = TransferState.QUEUED,
                    error = "",
                    conflictReason = "",
                    bytesPerSecond = 0
                )
            }
            pump()
        }
    }

    fun clearFinished() {
        _transfers.update { list ->
            list.filter {
                it.state == TransferState.QUEUED || it.state == TransferState.RUNNING ||
                    it.state == TransferState.PAUSED || it.state == TransferState.CONFLICT
            }
        }
    }

    fun remove(id: Long) {
        if (_transfers.value.firstOrNull { it.id == id }?.state in
            listOf(TransferState.RUNNING, TransferState.QUEUED, TransferState.PAUSED, TransferState.CONFLICT)) {
            cancel(id)
        }
        _transfers.update { list -> list.filterNot { it.id == id } }
    }

    private fun enqueue(item: TransferItem) {
        _transfers.update { it + item }
    }

    private fun nextId(): Long = synchronized(lock) { seq += 1; seq }

    private fun item(id: Long): TransferItem? = _transfers.value.firstOrNull { it.id == id }

    private fun update(id: Long, transform: (TransferItem) -> TransferItem) {
        _transfers.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun isPaused(id: Long) = synchronized(lock) { id in paused }
    private fun isCancelled(id: Long) = synchronized(lock) { id in cancelled }
    private fun conflictMode(id: Long) = synchronized(lock) { conflicts[id] ?: item(id)?.conflictMode }

    private fun pump() {
        val started = ArrayList<TransferItem>()
        synchronized(lock) {
            while (running.size < MAX_ACTIVE) {
                val next = _transfers.value.firstOrNull {
                    it.state == TransferState.QUEUED && it.id !in running && it.id !in cancelled && it.id !in paused
                } ?: break
                running.add(next.id)
                started.add(next)
            }
        }
        for (entry in started) {
            update(entry.id) { it.copy(state = TransferState.RUNNING, error = "", bytesPerSecond = 0) }
            scope.launch { execute(entry.id) }
        }
    }

    private suspend fun execute(id: Long) {
        val error: Throwable? = try {
            val current = item(id) ?: return
            when (current.kind) {
                TransferKind.DOWNLOAD -> runDownload(id)
                TransferKind.UPLOAD -> runUpload(id)
            }
            null
        } catch (failure: Throwable) {
            failure
        } finally {
            synchronized(lock) { running.remove(id) }
        }
        val now = item(id)
        if (error != null && now != null && !isCancelled(id) && !isPaused(id)) {
            if (now.state != TransferState.CONFLICT && now.state != TransferState.DONE &&
                now.state != TransferState.FAILED && now.state != TransferState.SKIPPED &&
                now.state != TransferState.PAUSED) {
                update(id) {
                    it.copy(
                        state = TransferState.FAILED,
                        bytesPerSecond = 0,
                        error = friendlyError(error)
                    )
                }
            }
        }
        pump()
    }

    private class PausedTransfer(val done: Long) : IOException("pause")

    // -----------------------------------------------------------------------
    // PC -> telefon
    // -----------------------------------------------------------------------

    private suspend fun runDownload(id: Long) {
        var transfer = item(id) ?: return
        val tree = Uri.parse(transfer.treeUri)
        val folder = FsSaf.ensureDir(ctx, tree, transfer.relativeDir)
            ?: throw IOException("Mappen finnes ikke lenger på telefonen")

        if (conflictMode(id) == null && folder.findFile(transfer.name) != null) {
            update(id) {
                it.copy(
                    state = TransferState.CONFLICT,
                    conflictReason = "Filen finnes allerede på telefonen",
                    error = "Filen finnes allerede",
                    bytesPerSecond = 0
                )
            }
            return
        }

        var attempt = 0
        while (true) {
            if (isCancelled(id)) {
                FsSaf.deleteTemp(ctx, tree, transfer.relativeDir, transfer.name)
                return
            }
            if (isPaused(id)) {
                update(id) { it.copy(state = TransferState.PAUSED, bytesPerSecond = 0) }
                return
            }
            var temp = FsSaf.tempDoc(ctx, tree, transfer.relativeDir, transfer.name)
                ?: throw IOException("Klarte ikke opprette midlertidig fil på telefonen")
            var offset = temp.length()
            if (transfer.sizeBytes > 0 && offset > transfer.sizeBytes) {
                temp.delete()
                temp = FsSaf.tempDoc(ctx, tree, transfer.relativeDir, transfer.name)
                    ?: throw IOException("Klarte ikke opprette midlertidig fil på telefonen")
                offset = 0
            }

            // Åpne skrivestrømmen FØR HTTP-kallet, slik at vi vet om gjenopptak
            // faktisk støttes. Mange SAF-leverandører støtter ikke "wa".
            var output: java.io.OutputStream? = null
            if (offset > 0) {
                output = runCatching { ctx.contentResolver.openOutputStream(temp.uri, "wa") }.getOrNull()
                if (output == null) {
                    temp.delete()
                    temp = FsSaf.tempDoc(ctx, tree, transfer.relativeDir, transfer.name)
                        ?: throw IOException("Klarte ikke opprette midlertidig fil på telefonen")
                    offset = 0
                }
            }
            if (output == null) {
                output = runCatching { ctx.contentResolver.openOutputStream(temp.uri, "wt") }.getOrNull()
                    ?: throw IOException("Klarte ikke åpne skrifestrømmen")
            }
            val sink = output

            var restart = false
            try {
                val finalOffset = offset
                val request = Request.Builder()
                    .url(joinUrl("/api/files/download?path=" + enc(transfer.pcPath)))
                    .apply {
                        if (finalOffset > 0) header("Range", "bytes=$finalOffset-")
                        authHeader()
                    }
                    .get()
                    .build()

                http.newCall(request).execute().use { response ->
                    if (response.code == 416) {
                        val expected = transfer.sizeBytes
                        if (expected > 0 && temp.length() == expected) {
                            update(id) { it.copy(bytesDone = expected) }
                            finishDownload(id, transfer)
                            return
                        }
                        restart = true
                        return@use
                    }
                    if (!response.isSuccessful) {
                        throw IOException(bridgeError(response.code, response.body?.string().orEmpty()))
                    }
                    val body = response.body ?: throw IOException("Tom respons fra PC-en")
                    val contentLength = body.contentLength()
                    if (finalOffset > 0 && parseRangeStart(response.header("Content-Range")) == null) {
                        // Server ignorerte Range; skriv om fra start.
                        restart = true
                        return@use
                    }
                    val total = if (contentLength > 0) contentLength + finalOffset else transfer.sizeBytes
                    if (total > 0) {
                        update(id) { it.copy(sizeBytes = max(it.sizeBytes, total)) }
                    }

                    var written = finalOffset
                    var tick = System.nanoTime()
                    var tickBytes = written
                    var speed = 0L
                    val buffer = ByteArray(256 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            if (isCancelled(id)) {
                                FsSaf.deleteTemp(ctx, tree, transfer.relativeDir, transfer.name)
                                return
                            }
                            if (isPaused(id)) {
                                update(id) {
                                    it.copy(
                                        bytesDone = written,
                                        state = TransferState.PAUSED,
                                        bytesPerSecond = 0
                                    )
                                }
                                return
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            val now = System.nanoTime()
                            if (now - tick > 400_000_000L) {
                                speed = ((written - tickBytes) * 1_000_000_000L) / (now - tick)
                                tick = now
                                tickBytes = written
                                update(id) {
                                    it.copy(
                                        bytesDone = written,
                                        bytesPerSecond = speed,
                                        sizeBytes = if (total > 0) total else written
                                    )
                                }
                            }
                        }
                    }
                    update(id) { it.copy(bytesDone = written, bytesPerSecond = 0) }
                }
            } catch (error: Throwable) {
                if (isCancelled(id)) return
                if (isPaused(id) || error is PausedTransfer) {
                    val pausedAt = FsSaf.find(ctx, tree, transfer.relativeDir, FsSaf.tempName(transfer.name))
                        ?.length() ?: transfer.bytesDone
                    update(id) {
                        it.copy(state = TransferState.PAUSED, bytesPerSecond = 0, bytesDone = pausedAt)
                    }
                    return
                }
                attempt += 1
                if (attempt > RETRIES) throw error
                delay(700L * attempt)
                transfer = item(id) ?: return
            } finally {
                runCatching { output?.flush() }
                runCatching { output?.close() }
            }

            if (restart) {
                runCatching { temp.delete() }
                update(id) { it.copy(bytesDone = 0, bytesPerSecond = 0) }
                continue
            }

            val written = FsSaf.find(ctx, tree, transfer.relativeDir, FsSaf.tempName(transfer.name))
                ?.length() ?: 0
            val expected = transfer.sizeBytes
            if (expected > 0 && written != expected) {
                attempt += 1
                if (attempt > RETRIES) {
                    throw IOException("Uventet størrelse: $written av $expected byte")
                }
                delay(700L * attempt)
                transfer = item(id) ?: return
                continue
            }
            finishDownload(id, transfer)
            return
        }
    }

    private suspend fun finishDownload(id: Long, transfer: TransferItem) {
        val tree = Uri.parse(transfer.treeUri)
        val folder = FsSaf.ensureDir(ctx, tree, transfer.relativeDir)
            ?: throw IOException("Mappen finnes ikke lenger på telefonen")
        val existing = folder.findFile(transfer.name)
        val mode = conflictMode(id)
        if (existing != null && mode == null) {
            update(id) {
                it.copy(state = TransferState.CONFLICT, conflictReason = "Filen finnes allerede på telefonen",
                    error = "Filen finnes allerede")
            }
            return
        }
        val temp = FsSaf.find(ctx, tree, transfer.relativeDir, FsSaf.tempName(transfer.name))
        if (temp == null) {
            // Ingen temp betyr at filen allerede er ferdig (f.eks. etter 416).
            update(id) { it.copy(state = TransferState.DONE, verified = false, bytesPerSecond = 0) }
            return
        }
        val overwrite = mode != ConflictMode.SKIP
        if (!FsSaf.renameTemp(ctx, temp, transfer.name, overwrite)) {
            update(id) {
                it.copy(state = TransferState.CONFLICT, conflictReason = "Klarte ikke å fullføre filen",
                    error = "Klarte ikke å fullføre filen")
            }
            return
        }
        synchronized(lock) { conflicts.remove(id) }
        val localUri = FsSaf.fileUri(ctx, tree, transfer.relativeDir, transfer.name)
        val localSha = sha256Of(localUri)
        val remoteSha = pcSha(transfer.pcPath)
        val verified = remoteSha != null && remoteSha.equals(localSha, ignoreCase = true)
        val size = folder.findFile(transfer.name)?.length() ?: transfer.sizeBytes
        update(id) {
            it.copy(
                state = TransferState.DONE,
                bytesDone = size,
                sizeBytes = if (size > 0) size else it.sizeBytes,
                bytesPerSecond = 0,
                sha256 = localSha,
                verified = verified,
                error = when {
                    remoteSha == null -> "Kunne ikke verifisere SHA-256"
                    !verified -> "SHA-256 stemte ikke med PC-en"
                    else -> ""
                }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Telefon -> PC
    // -----------------------------------------------------------------------

    private suspend fun runUpload(id: Long) {
        var transfer = item(id) ?: return
        val tree = Uri.parse(transfer.treeUri)
        val sourceUri = FsSaf.fileUri(ctx, tree, transfer.relativeDir, transfer.name)
            ?: throw IOException("Filen finnes ikke lenger på telefonen")
        val localSize = DocumentFile.fromSingleUri(ctx, sourceUri)?.length() ?: 0
        if (localSize > 0) {
            update(id) { it.copy(sizeBytes = localSize) }
        }
        val dir = FsPaths.parent(transfer.pcPath)
        ensurePcDir(dir)

        if (conflictMode(id) == null) {
            val status = uploadStatus(dir, transfer.name)
            if (status.finalExists) {
                update(id) {
                    it.copy(
                        state = TransferState.CONFLICT,
                        conflictReason = "Filen finnes allerede på PC-en (${FsFormat.size(status.finalSize)})",
                        error = "Filen finnes allerede på PC-en",
                        bytesPerSecond = 0
                    )
                }
                return
            }
        }

        val localSha = sha256Of(sourceUri)
        var attempt = 0
        while (true) {
            if (isCancelled(id)) return
            if (isPaused(id)) {
                update(id) { it.copy(state = TransferState.PAUSED, bytesPerSecond = 0) }
                return
            }
            var offset = uploadStatus(dir, transfer.name).tmpSize
            if (offset > localSize) offset = 0
            try {
                val body = UploadBody(ctx, sourceUri, offset, localSize, id)
                val overwrite = conflictMode(id) == ConflictMode.OVERWRITE
                val url = joinUrl(
                    "/api/files/upload?path=" + enc(dir) +
                        "&name=" + enc(transfer.name) +
                        "&offset=" + offset +
                        "&finalize=1" +
                        (if (overwrite) "&overwrite=1" else "")
                )
                val request = Request.Builder()
                    .url(url)
                    .apply { authHeader() }
                    .post(body)
                    .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 409) {
                        val json = parseJson(text)
                        if (json.bool("conflict") || json.str("error").contains("Finnes allerede", true)) {
                            update(id) {
                                it.copy(
                                    state = TransferState.CONFLICT,
                                    conflictReason = "Filen finnes allerede på PC-en",
                                    error = "Filen finnes allerede på PC-en",
                                    bytesPerSecond = 0
                                )
                            }
                            return
                        }
                        throw IOException(json.str("error").ifBlank { "Trenger ny opplasting" })
                    }
                    if (!response.isSuccessful) {
                        throw IOException(bridgeError(response.code, text))
                    }
                    val json = parseJson(text)
                    val remoteSha = json.str("sha256")
                    val done = json.long("size").takeIf { it > 0 } ?: localSize
                    val verified = remoteSha.isNotBlank() && remoteSha.equals(localSha, ignoreCase = true)
                    synchronized(lock) { conflicts.remove(id) }
                    update(id) {
                        it.copy(
                            state = TransferState.DONE,
                            bytesDone = done,
                            sizeBytes = if (done > 0) done else it.sizeBytes,
                            bytesPerSecond = 0,
                            sha256 = localSha,
                            verified = verified,
                            error = if (remoteSha.isBlank()) "Kunne ikke verifisere SHA-256"
                            else if (!verified) "SHA-256 stemte ikke med PC-en" else ""
                        )
                    }
                    return
                }
            } catch (error: Throwable) {
                if (isCancelled(id)) return
                if (isPaused(id) || error is PausedTransfer) {
                    val pausedAt = uploadStatus(dir, transfer.name).tmpSize
                    update(id) {
                        it.copy(state = TransferState.PAUSED, bytesPerSecond = 0,
                            bytesDone = pausedAt.coerceAtLeast(it.bytesDone))
                    }
                    return
                }
                attempt += 1
                if (attempt > RETRIES) throw error
                delay(800L * attempt)
            }
        }
    }

    private data class UploadStatus(val tmpSize: Long, val finalExists: Boolean, val finalSize: Long)

    private suspend fun uploadStatus(dir: String, name: String): UploadStatus = runCatching {
        val json = apiJson(
            "GET",
            "/api/files/upload-status?path=" + enc(dir) + "&name=" + enc(name),
            null, 45
        )
        UploadStatus(
            tmpSize = json.long("size"),
            finalExists = json.bool("finalExists"),
            finalSize = json.long("finalSize")
        )
    }.getOrDefault(UploadStatus(0, false, 0))

    private suspend fun ensurePcDir(dir: String) {
        if (dir.isBlank()) return
        val result = pcMkdir(dir)
        if (result.isFailure) {
            val message = result.exceptionOrNull()?.message.orEmpty()
            if (!message.contains("409") && !message.contains("Finnes allerede", true)) {
                // Mappen finnes ofte allerede; en annen feil skal derimot stoppe.
                val stat = pcStat(dir)
                if (stat.isFailure) throw result.exceptionOrNull() ?: IOException("Klarte ikke opprette mappen")
            }
        }
    }

    private fun parseRangeStart(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val match = Regex("bytes\\s+(\\d+)-").find(contentRange) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private inner class UploadBody(
        private val context: Context,
        private val src: Uri,
        private val offset: Long,
        private val total: Long,
        private val id: Long
    ) : RequestBody() {

        override fun contentType() = "application/octet-stream".toMediaType()

        override fun contentLength(): Long =
            if (total <= 0) -1L else max(0, total - offset)

        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(src)
                ?: throw IOException("Klarte ikke åpne filen på telefonen")
            input.use { stream ->
                var skipped = 0L
                val skipBuffer = ByteArray(64 * 1024)
                while (skipped < offset) {
                    val want = min(offset - skipped, skipBuffer.size.toLong()).toInt()
                    val read = stream.read(skipBuffer, 0, want)
                    if (read < 0) break
                    skipped += read
                }
                var bytes = offset
                var tick = System.nanoTime()
                var tickBytes = bytes
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    if (isCancelled(id)) throw PausedTransfer(bytes)
                    if (isPaused(id)) throw PausedTransfer(bytes)
                    val read = stream.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    bytes += read
                    val now = System.nanoTime()
                    if (now - tick > 400_000_000L) {
                        val speed = ((bytes - tickBytes) * 1_000_000_000L) / (now - tick)
                        tick = now
                        tickBytes = bytes
                        update(id) { it.copy(bytesDone = bytes, bytesPerSecond = speed) }
                    }
                }
                update(id) { it.copy(bytesDone = bytes) }
            }
        }
    }

    // ============================ Hjelpere ==================================

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun joinUrl(path: String): String = settings.baseUrl().trimEnd('/') + path

    private fun Request.Builder.authHeader(): Request.Builder =
        apply { settings.token()?.let { header("Authorization", "Bearer $it") } }

    private fun bridgeError(code: Int, text: String): String {
        val json = parseJson(text)
        val message = json.str("error")
        return if (message.isBlank()) "HTTP $code" else message
    }

    private fun parseJson(text: String): JsonObject = try {
        if (text.isBlank()) JsonObject()
        else com.google.gson.JsonParser.parseString(text).asJsonObject
    } catch (_: Throwable) {
        JsonObject()
    }

    private fun sha256Of(uri: Uri?): String {
        if (uri == null) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return ""
            digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
        } catch (_: Throwable) { "" }
    }
}
