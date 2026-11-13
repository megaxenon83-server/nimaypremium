package com.omp.chat.ui.chstgpt

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.omp.chat.OmpApp
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.Strings
import com.omp.chat.ui.components.Attachment
import com.omp.chat.ui.screens.CrashScreen
import com.omp.chat.ui.theme.OmpTheme
import com.omp.chat.util.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Host for the ChatGPT-inspired frontend. It owns exactly the same Android
 * edges as [com.omp.chat.ui.MainActivity] (camera, pickers, speech, QR, deep
 * links) so no existing capability is lost, and hands plain data to the same
 * [AppViewModel] and repository.
 */
class ChatGptActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = CrashReporter.read(this)
        if (crash != null && savedInstanceState == null) {
            val appSettings = OmpApp.instance.settings.state.value
            setContent {
                OmpTheme(theme = appSettings.theme, dynamicColor = false, fontScale = appSettings.fontScale) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalStrings provides Strings(appSettings.language)
                    ) {
                        CrashScreen(
                            report = crash,
                            english = appSettings.language == "en",
                            onContinue = {
                                CrashReporter.clear(this)
                                recreate()
                            }
                        )
                    }
                }
            }
            return
        }

        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        viewModel.onAppForeground()
                        viewModel.repository.refreshLiveStatus()
                    }
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
            ChatGptRoot(
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
                onVoice = { startSpeech() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

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

    private fun startSpeech() {
        if (!com.omp.chat.data.FeatureFlags.VoiceEnabled) {
            viewModel.toast(getString(com.omp.chat.R.string.speech_unavailable))
            return
        }
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

    companion object {
        private const val MAX_BYTES = 12 * 1024 * 1024
    }
}
