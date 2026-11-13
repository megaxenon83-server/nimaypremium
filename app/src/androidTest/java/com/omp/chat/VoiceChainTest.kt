package com.omp.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omp.chat.data.BridgeClient
import com.omp.chat.data.ChatRepository
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.SettingsStore
import com.omp.chat.data.VoiceCallEngine
import com.omp.chat.data.VoicePhase
import com.omp.chat.data.VoiceTurn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end checks against a real bridge with real STT/TTS engines and a live
 * OMP session. They drive exactly the code paths the app uses:
 *
 *   TTS fixture -> /api/voice/speak -> /api/voice/audio -> /api/voice/transcribe
 *   -> real session -> /api/prompt -> SSE transcript -> assistant answer
 *   -> /api/voice/speak -> WAV download -> Android MediaPlayer,
 *
 * and [engineRunsWholeTurnWithFixtureAudio] additionally runs the production
 * [VoiceCallEngine] turn loop with the microphone replaced by that fixture
 * (the emulator has no real microphone input).
 *
 * Run with (bridge already running, adb reverse or LAN address):
 *
 *   adb shell am instrument -w \
 *     -e baseUrl http://127.0.0.1:7790 -e token <bridge token> \
 *     -e cwd 'C:\Users\Megaxenon\AppData\Local\Temp\omp-voice-android' \
 *     -e provider ollama -e modelId gemma4:12b \
 *     com.omp.chat.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Without the arguments the tests are skipped instead of failing normal builds.
 */
@RunWith(AndroidJUnit4::class)
class VoiceChainTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()

    private val baseUrl: String get() = args.getString("baseUrl").orEmpty()
    private val token: String get() = args.getString("token").orEmpty()
    private val cwd: String
        get() = args.getString("cwd") ?: "C:\\Users\\Megaxenon\\AppData\\Local\\Temp\\omp-voice-android"
    private val provider: String get() = args.getString("provider") ?: "ollama"
    private val modelId: String get() = args.getString("modelId") ?: "gemma4:12b"
    private val keepSession: Boolean get() = args.getString("keepSession") == "1"

    @Test
    fun spokenTurnReachesOmpAndComesBackAsAudio() {
        requireArguments()
        val client = BridgeClient({ baseUrl }, { token })

        // 1. The PC really has both local engines installed.
        val engines = client.voiceEngines()
        assertTrue("Bridge reports no installed engines: $engines", engines.get("installed")?.asBoolean == true)
        val stt = engines.getAsJsonObject("stt")?.get("engine")?.asString.orEmpty()
        val tts = engines.getAsJsonObject("active")?.get("engine")?.asString.orEmpty()
        assertTrue("No STT engine", stt.isNotBlank())
        assertTrue("No TTS engine", tts.isNotBlank())
        Log.i(TAG, "engines: stt=$stt tts=$tts")

        // 2. A deliberate spoken fixture goes through the real TTS, download and STT.
        val fixtureText = "Dette er en test av talesamtalen i OMP Chat."
        val fixtureClip = client.voiceSpeak(fixtureText)
        val fixtureWav = client.voiceAudio(fixtureClip.audioUrl)
        assertTrue("TTS WAV is too small: ${fixtureWav.size}", fixtureWav.size > 1_000)
        assertEquals("RIFF", String(fixtureWav, 0, 4, Charsets.US_ASCII))
        val transcription = client.voiceTranscribe(fixtureWav)
        assertTrue("STT returned no text", transcription.text.isNotBlank())
        Log.i(TAG, "fixture stt (${transcription.engine}): ${transcription.text}")

        // 3. A real app repository turn against a real OMP session.
        withRepository(client) { repository, session ->
            val baseline = VoiceTurn.baseline(repository.state.value)
            repository.send("Svar med én kort setning: hva er hovedstaden i Norge?")
            val reply = runBlocking {
                VoiceTurn.awaitReply(
                    baseline = baseline,
                    snapshot = { repository.state.value },
                    timeoutMs = 300_000
                )
            }
            assertTrue("OMP produced no answer", reply.isNotBlank())
            Log.i(TAG, "OMP answered: $reply")

            // 4. The answer comes back from local TTS and Android can decode it.
            val answerClip = client.voiceSpeak(reply.take(1_800))
            val answerWav = client.voiceAudio(answerClip.audioUrl)
            assertTrue("Answer WAV is too small: ${answerWav.size}", answerWav.size > 1_000)
            playAndWait(answerWav)
            if (session.isNotBlank()) Log.i(TAG, "session: $session")
        }
    }

    @Test
    fun engineRunsWholeTurnWithFixtureAudio() {
        requireArguments()
        val client = BridgeClient({ baseUrl }, { token })

        val question = "Hva er hovedstaden i Norge? Svar med én kort setning."
        val fixture = client.voiceAudio(client.voiceSpeak(question).audioUrl)
        assertTrue(fixture.size > 1_000)

        withRepository(client) { repository, _ ->
            val engine = VoiceCallEngine(context, SettingsStore(context), repository) { fixture }
            try {
                engine.start(handsFree = false)
                await("voice engine turn", 300_000) {
                    val call = engine.state.value
                    !call.active && (call.phase == VoicePhase.Idle || call.phase == VoicePhase.Error)
                }
                val call = engine.state.value
                assertTrue("Voice engine failed: ${call.error}", call.error.isBlank())
                assertEquals(VoicePhase.Idle, call.phase)
                assertTrue("No transcript", call.transcript.isNotBlank())
                assertTrue("No answer", call.reply.isNotBlank())
                assertEquals(1, call.turns)
                Log.i(TAG, "engine transcript: ${call.transcript}")
                Log.i(TAG, "engine answer: ${call.reply}")
            } finally {
                engine.stop()
            }
        }
    }

    // -----------------------------------------------------------------------

    private fun requireArguments() {
        assumeTrue(
            "Set -e baseUrl and -e token to run the real voice chain",
            baseUrl.isNotBlank() && token.isNotBlank()
        )
    }

    /**
     * Creates a real bridge session, points a real [ChatRepository] at it and
     * restores the app's previous pairing afterwards.
     */
    private fun withRepository(
        client: BridgeClient,
        block: (ChatRepository, String) -> Unit
    ) {
        val settings = SettingsStore(context)
        val previousUrl = settings.baseUrl()
        val previousToken = settings.token()
        val repository = ChatRepository(settings)
        var file = ""
        try {
            settings.saveConnection(baseUrl, token)
            settings.rememberLast(cwd, "")
            repository.connect()
            await("bridge connection", 60_000) { repository.state.value.connected }
            repository.newChat(cwd)
            await("new session", 90_000) { repository.state.value.sessionFile.isNotBlank() }
            file = repository.state.value.sessionFile
            assertTrue("New session has no file", file.isNotBlank())
            client.setModel(
                cwd,
                ModelInfo(
                    id = modelId,
                    name = modelId,
                    provider = provider,
                    reasoning = false,
                    input = emptyList(),
                    contextWindow = null,
                    maxTokens = null
                )
            )
            block(repository, file)
        } finally {
            if (file.isNotBlank() && !keepSession) {
                try {
                    client.deleteSession(file)
                } catch (error: Exception) {
                    Log.w(TAG, "Could not delete test session", error)
                }
            }
            repository.disconnect()
            if (previousUrl.isNotBlank() && previousToken != null) {
                settings.saveConnection(previousUrl, previousToken)
            } else {
                settings.clearConnection()
            }
        }
    }

    private fun playAndWait(wav: ByteArray) {
        val file = File(context.cacheDir, "voice-chain-test.wav")
        file.writeBytes(wav)
        val done = CountDownLatch(1)
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { done.countDown() }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error $what/$extra")
                done.countDown()
                true
            }
            player.prepare()
            player.start()
            assertTrue("Playback did not finish", done.await(90, TimeUnit.SECONDS))
        } finally {
            try {
                player.release()
            } catch (_: Exception) {
            }
            file.delete()
        }
    }

    private fun await(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        fail("Timed out waiting for $label")
    }

    companion object {
        private const val TAG = "VoiceChainTest"
    }
}
