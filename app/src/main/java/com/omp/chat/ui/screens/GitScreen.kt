package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.PcProfile
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GitFile(val path: String, val status: String, val staged: Boolean, val unstaged: Boolean)

@Composable
fun GitScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val profiles by viewModel.pcProfileList.collectAsState()
    val activePcId by viewModel.activePcId.collectAsState()
    var selectedPcId by remember { mutableStateOf(activePcId) }
    val selectedPc: PcProfile? = profiles.firstOrNull { it.id == selectedPcId } ?: profiles.firstOrNull()

    var cwd by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var files by remember { mutableStateOf<List<GitFile>>(emptyList()) }
    var logText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showCommit by remember { mutableStateOf(false) }
    var commitMsg by remember { mutableStateOf("") }
    var showDiff by remember { mutableStateOf<GitFile?>(null) }
    var diffText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf<String?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var editorMtime by remember { mutableStateOf(0L) }
    var editorError by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    val baseUrl = selectedPc?.baseUrl?.trimEnd('/') ?: ""
    val token = selectedPc?.token ?: ""

    suspend fun api(method: String, path: String, body: org.json.JSONObject? = null): org.json.JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("$baseUrl$path")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            org.json.JSONObject(text.ifBlank { "{}" })
        } catch (e: Exception) {
            null
        }
    }

    fun refresh() {
        if (baseUrl.isBlank() || cwd.isBlank()) return
        scope.launch {
            loading = true
            error = ""
            val st = api("GET", "/api/git/status?path=${java.net.URLEncoder.encode(cwd, "UTF-8")}")
            if (st == null) { error = "Kunne ikke hente status"; loading = false; return@launch }
            branch = st.optString("branch")
            val arr = st.optJSONArray("files") ?: org.json.JSONArray()
            val list = mutableListOf<GitFile>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(GitFile(o.optString("path"), o.optString("status"), o.optBoolean("staged"), o.optBoolean("unstaged")))
            }
            files = list
            val br = api("GET", "/api/git/branches?path=${java.net.URLEncoder.encode(cwd, "UTF-8")}")
            branches = br?.optJSONArray("branches")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val lg = api("GET", "/api/git/log?path=${java.net.URLEncoder.encode(cwd, "UTF-8")}&limit=15")
            val carr = lg?.optJSONArray("commits")
            logText = if (carr != null) (0 until carr.length()).map { carr.optJSONObject(it)?.optString("message").orEmpty() + "  (" + carr.optJSONObject(it)?.optString("hash")?.take(7).orEmpty() + ")" }.joinToString("\n") else ""
            loading = false
        }
    }

    LaunchedEffect(selectedPcId) {
        // reset cwd; user must set it (or use lastCwd)
        cwd = selectedPc?.lastCwd ?: ""
        if (cwd.isNotBlank()) refresh()
    }

    // Main UI
    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary)
            }
            Text("Git", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VariantColors.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Oppdater", tint = VariantColors.textSecondary)
            }
        }
        // PC selector
        if (profiles.size > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                profiles.forEach { p ->
                    Surface(shape = RoundedCornerShape(6.dp), color = if (p.id == selectedPcId) VariantColors.accent.copy(alpha = 0.2f) else VariantColors.surfaceElevated, modifier = Modifier.clickable { selectedPcId = p.id }) {
                        Text(p.name, color = if (p.id == selectedPcId) VariantColors.accent else VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        // CWD input
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cwd,
                onValueChange = { cwd = it },
                label = { Text("Arbeidsmappe", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { refresh() }) { Text("Åpne", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall) }
        }

        if (loading) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = VariantColors.accent)
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = VariantColors.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
        }

        // Branch + actions
        if (branch.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Branch: $branch", color = VariantColors.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCommit = true }) { Text("Commit", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { scope.launch { val r = api("POST", "/api/git/pull", org.json.JSONObject().put("cwd", cwd)); toast = r?.optString("output") } }) { Text("Pull", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { scope.launch { val r = api("POST", "/api/git/push", org.json.JSONObject().put("cwd", cwd)); toast = r?.optString("output") } }) { Text("Push", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { scope.launch { val r = api("POST", "/api/git/fetch", org.json.JSONObject().put("cwd", cwd)); toast = r?.optString("output") } }) { Text("Fetch", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) }
            }
        }

        // Changed files
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(files, key = { it.path }) { f ->
                Row(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        val r = api("GET", "/api/git/diff?path=${java.net.URLEncoder.encode(cwd, "UTF-8")}&file=${java.net.URLEncoder.encode(f.path, "UTF-8")}")
                        diffText = r?.optString("diff").orEmpty()
                        showDiff = f
                    }
                }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.status, color = when {
                        f.status.contains("M") -> Color(0xFFFFB74D)
                        f.status.contains("?") -> VariantColors.online
                        f.status.contains("D") -> VariantColors.error
                        else -> VariantColors.textSecondary
                    }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                    Text(f.path, color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!f.staged) {
                        TextButton(onClick = { scope.launch { api("POST", "/api/git/stage", org.json.JSONObject().put("cwd", cwd).put("files", org.json.JSONArray().put(f.path))); refresh() } }) { Text("Stage", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        TextButton(onClick = { scope.launch { api("POST", "/api/git/unstage", org.json.JSONObject().put("cwd", cwd).put("files", org.json.JSONArray().put(f.path))); refresh() } }) { Text("Unstage", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = {
                        showEditor = f.path
                        scope.launch {
                            val r = api("GET", "/api/files/read?path=${java.net.URLEncoder.encode("$cwd/${f.path}".replace("\\", "/"), "UTF-8")}")
                            editorContent = r?.optString("content").orEmpty()
                            editorMtime = r?.optLong("mtime") ?: 0L
                        }
                    }) { Text("Rediger", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (logText.isNotBlank()) {
                item {
                    Text("Siste commits:", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    Text(logText, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        toast?.let { t ->
            LaunchedEffect(t) { kotlinx.coroutines.delay(2500); toast = null }
            Text(t, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
        }
    }

    if (showCommit) {
        AlertDialog(onDismissRequest = { showCommit = false }, title = { Text("Commit", color = VariantColors.textPrimary) }, text = {
            OutlinedTextField(value = commitMsg, onValueChange = { commitMsg = it }, label = { Text("Commit-melding") }, modifier = Modifier.fillMaxWidth())
        }, confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val r = api("POST", "/api/git/commit", org.json.JSONObject().put("cwd", cwd).put("message", commitMsg))
                    toast = r?.optString("output").orEmpty().ifBlank { "Commit feilet" }
                    commitMsg = ""
                    showCommit = false
                    refresh()
                }
            }) { Text("Commit", color = VariantColors.accent) }
        }, dismissButton = { TextButton(onClick = { showCommit = false }) { Text("Avbryt", color = VariantColors.textSecondary) } }, containerColor = VariantColors.sidebar)
    }

    showDiff?.let { f ->
        AlertDialog(onDismissRequest = { showDiff = null }, title = { Text(f.path, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, text = {
            Column(Modifier.height(400.dp)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(diffText.lines()) { line ->
                        val c = when {
                            line.startsWith("+") && !line.startsWith("+++") -> VariantColors.online
                            line.startsWith("-") && !line.startsWith("---") -> VariantColors.error
                            else -> VariantColors.textSecondary
                        }
                        Text(line, color = c, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showDiff = null }) { Text("Lukk", color = VariantColors.accent) } }, containerColor = VariantColors.sidebar)
    }

    showEditor?.let { relPath ->
        val fullPath = "$cwd/${relPath}".replace("\\", "/")
        AlertDialog(onDismissRequest = { showEditor = null }, title = { Text(relPath, color = VariantColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, text = {
            OutlinedTextField(
                value = editorContent,
                onValueChange = { editorContent = it },
                modifier = Modifier.fillMaxWidth().height(360.dp),
                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
        }, confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val body = org.json.JSONObject().put("path", fullPath).put("content", editorContent).put("expectedMtime", editorMtime)
                    val r = api("POST", "/api/files/write", body)
                    if (r?.optBoolean("ok") == true) {
                        editorMtime = r.optLong("mtime")
                        toast = "Lagret"
                        showEditor = null
                    } else {
                        editorError = r?.optString("error").orEmpty().ifBlank { "Lagring feilet" }
                        toast = editorError
                    }
                }
            }) { Text("Lagre", color = VariantColors.accent) }
        }, dismissButton = { TextButton(onClick = { showEditor = null }) { Text("Avbryt", color = VariantColors.textSecondary) } }, containerColor = VariantColors.sidebar)
    }
}
