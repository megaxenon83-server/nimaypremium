package com.omp.chat.data

import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure building blocks for the voice-call feature.
 *
 * Everything in this file runs on a plain JVM (no Android classes) so the WAV
 * container, the endpointing logic and the "what should be read aloud" rules
 * can be unit tested without a device.
 */

// ---------------------------------------------------------------------------
// WAV
// ---------------------------------------------------------------------------

/** Wraps raw little-endian PCM in a canonical RIFF/WAVE container. */
object WavEncoder {

    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    fun encode(
        pcm: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.putAscii("RIFF")
        header.putInt(36 + pcm.size)
        header.putAscii("WAVE")
        header.putAscii("fmt ")
        header.putInt(16)                       // PCM fmt chunk size
        header.putShort(1)                      // format = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.putAscii("data")
        header.putInt(pcm.size)

        val out = ByteArrayOutputStream(HEADER_BYTES + pcm.size)
        out.write(header.array())
        out.write(pcm)
        return out.toByteArray()
    }

    /** Converts the first [count] 16-bit samples to little-endian bytes. */
    fun pcm16(samples: ShortArray, count: Int = samples.size): ByteArray {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until min(count, samples.size)) buffer.putShort(samples[index])
        return buffer.array()
    }

    const val HEADER_BYTES = 44

    private fun ByteBuffer.putAscii(value: String) = put(value.toByteArray(Charsets.US_ASCII))
}

// ---------------------------------------------------------------------------
// Voice activity detection / endpointing
// ---------------------------------------------------------------------------

/**
 * Frame based endpointing. Feed it one short frame at a time and it reports
 * when speech starts, continues or ends. A frame counts as voiced when its RMS
 * level is at or above [threshold]; [VoiceCallEngine] adapts the threshold to
 * the room noise before every frame.
 */
class VoiceActivityDetector(
    private val frameMs: Int = 20,
    private val startMs: Int = 140,
    private val endSilenceMs: Int = 800,
    private val minSpeechMs: Int = 350,
    private val maxSpeechMs: Int = 20_000,
    var threshold: Float = 600f,
    /** Når nivå overstiger threshold * [burstFactor] trigger taletiden umiddelbart
     *  (én ramme) - kort taleknakk spike utløser start uten venting 140 ms. */
    var burstFactor: Float = Float.POSITIVE_INFINITY
) {

    enum class Event { SILENCE, STARTED, SPEECH, ENDED, TOO_SHORT, TIMEOUT }

    var speaking: Boolean = false
        private set

    /** Milliseconds of audio seen since [reset], including leading silence. */
    var totalMs: Long = 0
        private set

    private var voicedRun = 0
    private var voicedTotal = 0
    private var speechMs = 0
    private var silenceRun = 0

    fun reset() {
        speaking = false
        totalMs = 0
        voicedRun = 0
        voicedTotal = 0
        speechMs = 0
        silenceRun = 0
    }

    fun accept(samples: ShortArray): Event = acceptRms(rms(samples))

    fun acceptRms(level: Float): Event {
        totalMs += frameMs
        val voiced = level >= threshold
        val burst = level >= threshold * burstFactor

        if (!speaking) {
            if (burst) {
                speaking = true
                speechMs = frameMs
                voicedTotal = frameMs
                voicedRun = 0
                silenceRun = 0
                return Event.STARTED
            }
            if (voiced) {
                voicedRun += frameMs
                voicedTotal += frameMs
            } else {
                voicedRun = 0
            }
            if (voicedRun >= startMs) {
                speaking = true
                speechMs = voicedRun
                silenceRun = 0
                return Event.STARTED
            }
            return Event.SILENCE
        }

        speechMs += frameMs
        if (voiced) {
            silenceRun = 0
            voicedTotal += frameMs
        } else {
            silenceRun += frameMs
        }

        if (speechMs >= maxSpeechMs) {
            speaking = false
            return Event.TIMEOUT
        }
        if (silenceRun >= endSilenceMs) {
            speaking = false
            val spokeLongEnough = speechMs - silenceRun >= minSpeechMs && voicedTotal >= minSpeechMs
            return if (spokeLongEnough) Event.ENDED else Event.TOO_SHORT
        }
        return Event.SPEECH
    }

    companion object {
        /** Root-mean-square level of a frame (0..32768). */
        fun rms(samples: ShortArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (sample in samples) {
                val value = sample.toDouble()
                sum += value * value
            }
            return sqrt(sum / samples.size).toFloat()
        }
    }
}

// ---------------------------------------------------------------------------
// Text to read aloud
// ---------------------------------------------------------------------------

/**
 * Turns a markdown answer into something that sounds acceptable through a
 * speech synthesiser: code blocks, tables and markup are removed instead of
 * being spelled out, and the result is capped at the bridge's 2000 character
 * limit.
 */
object VoiceText {

    fun forSpeech(markdown: String, maxChars: Int = 1_800): String {
        if (markdown.isBlank()) return ""
        var text = markdown

        text = text.replace(Regex("```[\\s\\S]*?```"), " ")
        text = text.replace(Regex("~~~[\\s\\S]*?~~~"), " ")
        text = text.replace(Regex("`([^`]*)`"), "\$1")
        text = text.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "\$1")
        text = text.replace(Regex("<[^>]+>"), " ")
        text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), " ")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), " ")
        text = text.replace(Regex("(?m)^\\s*>\\s?"), " ")
        text = text.replace(Regex("(?m)^\\s*\\|.*\\|\\s*$"), " ")
        text = text.replace(Regex("[*_~]{1,3}"), "")
        text = text.replace(Regex("\\s+"), " ").trim()

        if (text.length > maxChars) {
            val cut = text.take(maxChars)
            val sentence = max(
                cut.lastIndexOf(". "),
                max(cut.lastIndexOf("! "), cut.lastIndexOf("? "))
            )
            text = if (sentence > maxChars / 3) {
                cut.take(sentence + 1).trim()
            } else {
                cut.trimEnd() + "…"
            }
        }
        return text
    }
}

// ---------------------------------------------------------------------------
// Waiting for the assistant
// ---------------------------------------------------------------------------

/**
 * Watches [RepoState] until a turn that started after [baseline] has produced
 * an assistant answer and streaming has stopped.
 */
object VoiceTurn {

    data class Baseline(val id: String, val length: Int, val messages: Int)

    fun baseline(state: RepoState): Baseline? =
        state.messages.lastOrNull { it.isAssistant }?.let {
            Baseline(it.id, it.text.length, state.messages.size)
        } ?: Baseline("", 0, state.messages.size)

    suspend fun awaitReply(
        baseline: Baseline?,
        snapshot: () -> RepoState,
        timeoutMs: Long = 300_000,
        intervalMs: Long = 200,
        onTick: (Long) -> Unit = {}
    ): String {
        val started = System.currentTimeMillis()
        var latest = ""
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            onTick(elapsed)
            val state = snapshot()
            val startIndex = baseline?.messages ?: 0

            // A failed prompt never produces an assistant message, so stop
            // immediately instead of waiting out the whole timeout.
            val failedUser = state.messages.drop(startIndex)
                .lastOrNull { it.isUser && it.status == "error" }
            if (failedUser != null) {
                throw IllegalStateException(state.notice?.text ?: "Kunne ikke sende meldingen")
            }
            val failedAnswer = state.messages.drop(startIndex)
                .lastOrNull { it.isAssistant && it.error.isNotBlank() && it.text.isBlank() }
            if (failedAnswer != null && !state.streaming) {
                throw IllegalStateException(failedAnswer.error)
            }
            if (!state.connected) {
                throw IllegalStateException("Mistet forbindelsen til PC-en")
            }

            val last = state.messages.lastOrNull { it.isAssistant && it.text.isNotBlank() }
            if (last != null) {
                val fresh = baseline == null ||
                    last.id != baseline.id ||
                    last.text.length > baseline.length
                if (fresh) {
                    latest = last.text.trim()
                    if (!state.streaming) return latest
                }
            }
            if (elapsed >= timeoutMs) return latest
            delay(intervalMs)
        }
    }
}
