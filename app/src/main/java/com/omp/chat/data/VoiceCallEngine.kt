package com.omp.chat.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File

enum class VoicePhase { Idle, Listening, Transcribing, Thinking, Speaking, Error }

data class VoiceCallState(
    val active: Boolean = false,
    val phase: VoicePhase = VoicePhase.Idle,
    val handsFree: Boolean = true,
    /** 0..1 input level used by the pulsing microphone button. */
    val level: Float = 0f,
    val transcript: String = "",
    val reply: String = "",
    val error: String = "",
    val elapsedMs: Long = 0,
    val turns: Int = 0,
    /** Mikrofonen er slått av midt i en samtale (Orb = mute-knapp). */
    val muted: Boolean = false
)

/**
 * Real voice conversation: microphone -> VAD -> local STT -> the active OMP
 * session -> assistant answer -> local TTS -> playback.
 *
 * The engine never fakes a turn: if the bridge is not connected, the engines are
 * missing, the transcription is empty or OMP does not answer, the UI gets an
 * explicit error instead of a simulated reply.
 */
class VoiceCallEngine(
    context: Context,
    private val settings: SettingsStore,
    private val repository: ChatRepository,
    /**
     * Test seam: when set, this supplies the next WAV instead of the microphone.
     * The production app always leaves it null and records with [AudioRecord].
     */
    private val captureOverride: (suspend () -> ByteArray?)? = null
) {

    private val appContext = context.applicationContext
    private val bridge: BridgeClient get() = repository.bridge
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        VoiceCallState(handsFree = settings.state.value.voiceHandsFree)
    )
    val state: StateFlow<VoiceCallState> = _state.asStateFlow()

    private var job: Job? = null
    private var recorder: AudioRecord? = null
    private var player: MediaPlayer? = null
    private var playDone: CompletableDeferred<Unit>? = null

    @Volatile
    private var stopped = true

    /** Sann når brukeren har avbrutt pågående OMP-runde ("Stopp OMP"). */
    @Volatile
    private var turnInterrupted = false

    @Volatile
    private var muted = false

    // -----------------------------------------------------------------------
    // Public controls
    // -----------------------------------------------------------------------

    fun start(handsFree: Boolean = _state.value.handsFree) {
        if (job?.isActive == true) return
        stopped = false
        turnInterrupted = false
        muted = false
        _state.update {
            it.copy(
                active = true,
                phase = VoicePhase.Listening,
                handsFree = handsFree,
                muted = false,
                level = 0f,
                transcript = "",
                reply = "",
                error = "",
                elapsedMs = 0
            )
        }
        job = scope.launch { runLoop() }
    }

    fun stop() {
        stopped = true
        muted = false
        turnInterrupted = false
        job?.cancel()
        job = null
        releaseRecorder()
        releasePlayer()
        _state.update { it.copy(active = false, phase = VoicePhase.Idle, level = 0f, muted = false) }
    }

    fun setHandsFree(enabled: Boolean) {
        settings.setVoiceHandsFree(enabled)
        _state.update { it.copy(handsFree = enabled) }
    }

    /** Mute/pause mikrofonen midt i samtalen uten å bryte runden. */
    fun setMuted(value: Boolean) {
        muted = value
        if (value) releaseRecorder()
        _state.update { it.copy(muted = value, level = 0f) }
    }

    /**
     * Avbryt pågående OMP-runde ("Stopp OMP") uten å avslutte samtalen:
     * stopper avspilling, sender /api/abort til broen (som også løser
     * ventende godkjenninger), og appen returnerer til lyttemodus.
     */
    fun interruptTurn() {
        turnInterrupted = true
        releasePlayer()
        runCatching {
            val cwd = repository.state.value.cwd
            if (cwd.isNotBlank()) bridge.abort(cwd)
        }
        _state.update {
            it.copy(
                phase = VoicePhase.Listening,
                reply = "",
                level = 0f,
                elapsedMs = 0
            )
        }
    }

    fun retry() {
        stop()
        start(_state.value.handsFree)
    }

    // -----------------------------------------------------------------------
    // Turn loop
    // -----------------------------------------------------------------------

    private suspend fun runLoop() {
        try {
            checkCapabilities()

            while (currentCoroutineContext().isActive && !stopped) {
                if (muted) {
                    // Mikrofon av: ingen opptak eller transkribering, samtalen
                    // forblir aktiv til brukeren slår på igjen eller avslutter.
                    _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
                    delay(200)
                    continue
                }
                _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
                val wav = captureTurn()
                if (wav == null) {
                    if (stopped) break
                    // Silence (or a cough) is not an error in hands-free mode.
                    if (!_state.value.handsFree) {
                        fail(noSpeechMessage())
                        break
                    }
                    delay(150)
                    continue
                }

                transcribeAndAnswer(wav)
                if (stopped) break
                if (turnInterrupted) {
                    // "Stopp OMP" trykket: hold samtalen i gang og lytt igjen.
                    _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
                    turnInterrupted = false
                    delay(300)
                    continue
                }

                if (!_state.value.handsFree) {
                    finish()
                    break
                }
                _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
                delay(400)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(describe(error))
        } finally {
            releaseRecorder()
            if (stopped) {
                releasePlayer()
            }
        }
    }

    private fun finish() {
        stopped = true
        _state.update { it.copy(active = false, phase = VoicePhase.Idle, level = 0f) }
    }

    private fun fail(message: String) {
        stopped = true
        releaseRecorder()
        releasePlayer()
        _state.update {
            it.copy(active = false, phase = VoicePhase.Error, error = message, level = 0f)
        }
    }

    private suspend fun checkCapabilities() {
        val info = bridge.voiceEngines()
        val stt = info.obj("stt")?.str("engine").orEmpty()
        val tts = info.obj("active")?.str("engine").orEmpty()
        if (info.get("installed")?.asBoolean != true || stt.isBlank() || tts.isBlank()) {
            throw IllegalStateException(
                if (settings.state.value.language == "en") {
                    "The speech engines are not installed on the PC yet. Run the Nimay OMP installer."
                } else {
                    "Talemotorene er ikke installert på PC-en ennå. Kjør Nimay OMP-installasjonen."
                }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Recording + endpointing
    // -----------------------------------------------------------------------

    /** Records one utterance and returns a 16 kHz mono WAV, or null when nobody spoke. */
    private suspend fun captureTurn(): ByteArray? = withContext(Dispatchers.IO) {
        captureOverride?.let { return@withContext it() }
        if (!hasMicrophonePermission()) {
            throw IllegalStateException(
                if (settings.state.value.language == "en") {
                    "Microphone access is required for voice calls."
                } else {
                    "Mikrofontilgang kreves for talesamtale."
                }
            )
        }
        val state = repository.state.value
        if (!state.connected) {
            throw IllegalStateException(
                if (settings.state.value.language == "en") {
                    "Not connected to the PC bridge."
                } else {
                    "Ikke tilkoblet broen på PC-en."
                }
            )
        }
        if (state.sessionFile.isBlank() || state.cwd.isBlank()) {
            throw IllegalStateException(
                if (settings.state.value.language == "en") {
                    "Open a conversation before starting a voice call."
                } else {
                    "Åpne en samtale før du starter talesamtale."
                }
            )
        }

        val record = createRecorder()
        recorder = record
        record.startRecording()

        val vad = VoiceActivityDetector()
        vad.burstFactor = 1.35f
        val frames = ShortArray(FRAME_SAMPLES)
        val pcm = ByteArrayOutputStream()
        val preRoll = ArrayDeque<ByteArray>()
        var noise = 90f
        var speechStarted = false
        var lastUiMs = 0L

        try {
            while (currentCoroutineContext().isActive && !stopped) {
                val read = record.read(frames, 0, FRAME_SAMPLES)
                if (read <= 0) {
                    if (read < 0) throw IllegalStateException("Mikrofonen returnerte en feil ($read)")
                    continue
                }
                val chunk = WavEncoder.pcm16(frames, read)
                val samples = if (read == FRAME_SAMPLES) frames else frames.copyOf(read)
                val level = VoiceActivityDetector.rms(samples)

                // Adapt to the room: a quiet phone gets a lower threshold, a noisy
                // one a higher threshold, so the detector does not fire on hiss.
                // Ceiling 1400 RMS: with ~1200 RMS of room hum the old ceiling
                // pinned the threshold near the max, which forced shouting.
                // Burst start (threshold * 1.35) reacts on the first clear speech
                // transient instead of needing a sustained 140 ms stretch above
                // the threshold, so ordinary soft speech is caught.
                if (!vad.speaking) {
                    noise = noise * 0.85f + level * 0.15f
                    vad.threshold = (noise * 2.0f).coerceIn(300f, 1_400f)
                }

                val event = vad.acceptRms(level)
                when (event) {
                    VoiceActivityDetector.Event.STARTED -> {
                        speechStarted = true
                        while (preRoll.isNotEmpty()) pcm.write(preRoll.removeFirst())
                        pcm.write(chunk)
                    }
                    VoiceActivityDetector.Event.SPEECH -> pcm.write(chunk)
                    VoiceActivityDetector.Event.ENDED,
                    VoiceActivityDetector.Event.TIMEOUT -> {
                        pcm.write(chunk)
                        break
                    }
                    VoiceActivityDetector.Event.TOO_SHORT -> break
                    VoiceActivityDetector.Event.SILENCE -> {
                        if (!speechStarted) {
                            preRoll.addLast(chunk)
                            if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                            if (vad.totalMs >= MAX_LISTEN_MS) break
                        }
                    }
                }

                if (vad.totalMs - lastUiMs >= 60) {
                    lastUiMs = vad.totalMs
                    _state.update { it.copy(level = (level / 3_000f).coerceIn(0f, 1f)) }
                }
            }
        } finally {
            releaseRecorder()
        }

        val bytes = pcm.toByteArray()
        if (!speechStarted || bytes.size < MIN_SPEECH_BYTES) return@withContext null
        WavEncoder.encode(bytes)
    }

    private fun createRecorder(): AudioRecord {
        val minimum = AudioRecord.getMinBufferSize(
            WavEncoder.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) throw IllegalStateException("Mikrofonen er ikke tilgjengelig")

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(WavEncoder.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimum, FRAME_SAMPLES * 2 * 20))
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record.release()
            } catch (_: Exception) {
            }
            throw IllegalStateException("Kunne ikke åpne mikrofonen")
        }
        return record
    }

    private fun releaseRecorder() {
        val record = recorder ?: return
        recorder = null
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        } catch (_: Exception) {
        }
        try {
            record.release()
        } catch (_: Exception) {
        }
    }

    // -----------------------------------------------------------------------
    // Transcribe -> OMP -> speak
    // -----------------------------------------------------------------------

    private suspend fun transcribeAndAnswer(wav: ByteArray) {
        _state.update { it.copy(phase = VoicePhase.Transcribing, level = 0f) }
        val transcription = bridge.voiceTranscribe(wav)
        val text = transcription.text.trim()
        if (text.isBlank()) {
            if (_state.value.handsFree) {
                _state.update { it.copy(phase = VoicePhase.Listening, error = "", level = 0f) }
            } else {
                fail(noSpeechMessage())
            }
            return
        }

        _state.update { it.copy(transcript = text, phase = VoicePhase.Thinking, elapsedMs = 0) }

        val baseline = VoiceTurn.baseline(repository.state.value)
        repository.send(text)
        val reply = try {
            VoiceTurn.awaitReply(
                baseline = baseline,
                snapshot = { repository.state.value },
                onTick = { elapsed -> _state.update { it.copy(elapsedMs = elapsed) } }
            ).trim()
        } catch (error: Exception) {
            // "Stopp OMP" eller avbrutt runde: ikke en feil for samtalen, bare
            // returner til lyttemodus uten å avslutte anropet.
            if (turnInterrupted) {
                _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
                return
            }
            throw error
        }

        if (turnInterrupted) {
            _state.update { it.copy(phase = VoicePhase.Listening, level = 0f) }
            return
        }

        if (reply.isBlank()) {
            throw IllegalStateException(
                if (settings.state.value.language == "en") {
                    "OMP did not answer. Check the model in the chat."
                } else {
                    "OMP svarte ikke. Sjekk modellen i chatten."
                }
            )
        }

        _state.update { it.copy(reply = reply, phase = VoicePhase.Speaking) }

        val spoken = VoiceText.forSpeech(reply)
        if (spoken.isNotBlank()) {
            val clip = bridge.voiceSpeak(spoken)
            val audio = bridge.voiceAudio(clip.audioUrl)
            if (audio.size <= 44) throw IllegalStateException("Talesyntesen returnerte ingen lyd")
            play(audio)
        }

        _state.update { it.copy(turns = it.turns + 1, level = 0f) }
    }

    private suspend fun play(audio: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(appContext.cacheDir, "voice-reply-${System.currentTimeMillis()}.wav")
        try {
            file.writeBytes(audio)
            val done = CompletableDeferred<Unit>()
            playDone = done
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener { done.complete(Unit) }
            media.setOnErrorListener { _, what, extra ->
                done.completeExceptionally(IllegalStateException("Avspilling feilet ($what/$extra)"))
                true
            }
            media.prepare()
            media.start()
            withTimeoutOrNull(180_000) { done.await() }
        } finally {
            releasePlayer()
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun releasePlayer() {
        playDone?.let { if (!it.isCompleted) it.complete(Unit) }
        playDone = null
        val media = player ?: return
        player = null
        try {
            if (media.isPlaying) media.stop()
        } catch (_: Exception) {
        }
        try {
            media.reset()
        } catch (_: Exception) {
        }
        try {
            media.release()
        } catch (_: Exception) {
        }
    }

    // -----------------------------------------------------------------------

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun noSpeechMessage(): String =
        if (settings.state.value.language == "en") {
            "I did not hear any speech. Try again."
        } else {
            "Jeg hørte ingen tale. Prøv igjen."
        }

    private fun describe(error: Exception): String {
        val message = error.message?.trim().orEmpty()
        if (message.isNotEmpty()) return message
        return if (settings.state.value.language == "en") {
            "The voice call stopped unexpectedly."
        } else {
            "Talesamtalen stoppet uventet."
        }
    }

    companion object {
        const val SAMPLE_RATE = WavEncoder.SAMPLE_RATE
        private const val FRAME_MS = 20
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000   // 320
        private const val PRE_ROLL_FRAMES = 10                            // 200 ms
        private const val MAX_LISTEN_MS = 15_000L
        private const val MIN_SPEECH_BYTES = SAMPLE_RATE * 2 * 350 / 1000 // 350 ms
    }
}
