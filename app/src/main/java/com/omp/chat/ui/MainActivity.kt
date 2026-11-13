package com.omp.chat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.omp.chat.OmpApp
import com.omp.chat.data.SettingsStore
import com.omp.chat.ui.components.Attachment
import com.omp.chat.ui.screens.CrashScreen
import com.omp.chat.util.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single-activity Compose host. It owns the Android-specific edges (camera,
 * pickers, speech recognition, QR scanning and deep links) and hands plain data
 * to [AppViewModel]; all chat logic lives in the repository layer.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private var speechText by mutableStateOf<String?>(null)

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result?.contents
        if (!contents.isNullOrBlank()) {
            val error = viewModel.pair(contents)
            if (error != null) viewModel.toast(error)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchScanner() else viewModel.toast(getString(com.omp.chat.R.string.camera_needed))
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch { uris.forEach { addAttachment(it) } }
    }

    private var pendingCamera: File? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCamera
        pendingCamera = null
        if (success && file != null && file.exists()) {
            lifecycleScope.launch { addAttachment(Uri.fromFile(file), fallbackName = file.name) }
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch { uris.forEach { addAttachment(it) } }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spoken.isNotEmpty()) speechText = spoken
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    /** Callback waiting for the RECORD_AUDIO answer, so a denied request shows a hint. */
    private var pendingVoicePermission: ((Boolean) -> Unit)? = null

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingVoicePermission?.invoke(granted)
        pendingVoicePermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A crash on a physical phone cannot be read via adb here, so the next
        // launch renders the saved stack trace and offers "copy".
        val crash = CrashReporter.read(this)
        if (crash != null && savedInstanceState == null) {
            setContent {
                CrashScreen(
                    report = crash,
                    english = OmpApp.instance.settings.state.value.language == "en",
                    onContinue = {
                        CrashReporter.clear(this)
                        recreate()
                    }
                )
            }
            return
        }

        // Rebuild the bridge stream and resync the conversation every time the
        // app becomes visible again (opened, unlocked, back from background):
        // Android may have dropped the SSE socket or the whole process, and a
        // long OMP task may have produced many events in the meantime.
        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START ->
                        viewModel.onAppForeground()
                    androidx.lifecycle.Lifecycle.Event.ON_STOP ->
                        viewModel.onAppBackground()
                    else -> Unit
                }
            }
        )

        enableEdgeToEdge()

        askForNotifications()
        handleIntent(intent)

        lifecycleScope.launch {
            viewModel.speech.collectLatest { text ->
                if (text != null) {
                    speechText = text
                    viewModel.consumeSpeech()
                }
            }
        }

        setContent {
            var updateInfo by remember { mutableStateOf<com.omp.chat.data.UpdateInfo?>(null) }

            LaunchedEffect(Unit) {
                val info = withContext(Dispatchers.IO) {
                    com.omp.chat.data.UpdateChecker.fetch(this@MainActivity)
                }
                if (info != null && info.available) {
                    updateInfo = info
                }
            }

            if (updateInfo != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = { Text("Ny versjon tilgjengelig") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val info = updateInfo
                                updateInfo = null
                                val url = info!!.androidUrl
                                if (url.isNotBlank()) {
                                    val intent = android.content.Intent(Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(url)
                                    }
                                    startActivity(intent)
                                }
                            }
                        ) { Text("Last ned") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { updateInfo = null }) { Text("Senere") }
                    },
                    text = {
                        val info = updateInfo
                        Text(
                            "Versjon ${info!!.androidVersion} (kode ${info!!.androidVersionCode}) er tilgjengelig.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }

            AppRoot(
                viewModel = viewModel,
                speechText = speechText,
                onSpeechConsumed = { speechText = null },
                onScan = { requestScan() },
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickCamera = { takePhoto() },
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onVoice = { startSpeech() },
                onVoicePermission = { onResult -> requestVoicePermission(onResult) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // -----------------------------------------------------------------------
    // Pairing
    // -----------------------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme != "omp" && scheme != "omp2" && scheme != "ompchat") return
        val error = viewModel.pair(data.toString())
        if (error != null) viewModel.toast(error)

    }

    private fun requestScan() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchScanner() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(com.omp.chat.R.string.scan_prompt))
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // -----------------------------------------------------------------------
    // Attachments
    // -----------------------------------------------------------------------

    private fun takePhoto() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCamera = file
        cameraLauncher.launch(uri)
    }

    private suspend fun addAttachment(uri: Uri, fallbackName: String? = null) {
        val attachment = withContext(Dispatchers.IO) { readAttachment(uri, fallbackName) }
        if (attachment == null) {
            viewModel.toast(getString(com.omp.chat.R.string.attach_failed))
            return
        }
        val error = viewModel.addAttachment(attachment)
        if (error != null) viewModel.toast(error)
    }

    private fun readAttachment(uri: Uri, fallbackName: String?): Attachment? {
        return try {
            val resolver = contentResolver
            val mime = resolver.getType(uri) ?: guessMime(uri, fallbackName)
            val name = queryName(uri) ?: fallbackName ?: "fil"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null
            if (bytes.size > MAX_BYTES) {
                viewModel.toast(getString(com.omp.chat.R.string.file_too_large))
                return null
            }
            Attachment(
                name = name,
                mime = mime,
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            )
        } catch (error: Exception) {
            null
        }
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (error: Exception) {
        null
    }

    private fun guessMime(uri: Uri, fallbackName: String?): String {
        val path = fallbackName ?: uri.lastPathSegment.orEmpty()
        return when (path.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "kt" -> "text/x-kotlin"
            else -> "application/octet-stream"
        }
    }

    // -----------------------------------------------------------------------
    // Speech
    // -----------------------------------------------------------------------

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, viewModel.settings.state.value.speechLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(com.omp.chat.R.string.speech_prompt))
        }
        if (intent.resolveActivity(packageManager) == null) {
            viewModel.toast(getString(com.omp.chat.R.string.speech_unavailable))
            return
        }
        try {
            speechLauncher.launch(intent)
        } catch (error: Exception) {
            viewModel.toast(getString(com.omp.chat.R.string.speech_unavailable))
        }
    }

    /**
     * Voice calls record raw audio for the bridge's local STT, so the runtime
     * permission is required before the call screen is opened.
     */
    private fun requestVoicePermission(onResult: (Boolean) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult(true)
            return
        }
        pendingVoicePermission = onResult
        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        private const val MAX_BYTES = 12 * 1024 * 1024
    }
}
