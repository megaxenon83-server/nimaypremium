package com.omp.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceCallTest {

    // -----------------------------------------------------------------------
    // WAV container
    // -----------------------------------------------------------------------

    @Test
    fun wavHeaderDescribes16kMonoPcm() {
        val pcm = ByteArray(640) { it.toByte() }
        val wav = WavEncoder.encode(pcm)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", wav.ascii(0, 4))
        assertEquals("WAVE", wav.ascii(8, 4))
        assertEquals("fmt ", wav.ascii(12, 4))
        assertEquals("data", wav.ascii(36, 4))

        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + pcm.size, buffer.getInt(4))
        assertEquals(16, buffer.getInt(16).toShort().toInt())
        assertEquals(1, buffer.getShort(20).toInt())
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(32_000, buffer.getInt(28))
        assertEquals(2, buffer.getShort(32).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(pcm.size, buffer.getInt(40))
    }

    @Test
    fun pcm16WritesLittleEndianSamples() {
        val bytes = WavEncoder.pcm16(shortArrayOf(1, -2, 256), 3)
        assertEquals(6, bytes.size)
        assertEquals(1, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())
        assertEquals(-2, (bytes[2].toInt() and 0xFF or (bytes[3].toInt() shl 8)).toShort().toInt())
        assertEquals(0, bytes[4].toInt())
        assertEquals(1, bytes[5].toInt())
    }

    // -----------------------------------------------------------------------
    // Endpointing
    // -----------------------------------------------------------------------

    @Test
    fun silenceDoesNotStartSpeech() {
        val vad = VoiceActivityDetector(threshold = 500f)
        repeat(20) { assertEquals(VoiceActivityDetector.Event.SILENCE, vad.acceptRms(120f)) }
        assertFalse(vad.speaking)
    }

    @Test
    fun loudTransientStartsSpeechImmediatelyWithBurstFactor() {
        val vad = VoiceActivityDetector(threshold = 600f)
        vad.burstFactor = 1.35f
        repeat(5) { assertEquals(VoiceActivityDetector.Event.SILENCE, vad.acceptRms(700f)) }
        // Ett tydelig spike over threshold * 1.35 (810) starter med en gang,
        // uten 140 ms fastholdelse.
        assertEquals(VoiceActivityDetector.Event.STARTED, vad.acceptRms(900f))
        assertTrue(vad.speaking)
    }

    @Test
    fun burstFactorDisabledKeepsDialogBehaviour() {
        val vad = VoiceActivityDetector(threshold = 600f)
        repeat(5) { assertEquals(VoiceActivityDetector.Event.SILENCE, vad.acceptRms(1_000f)) }
        assertFalse(vad.speaking)
    }

    @Test
    fun speechStartsAfterStartWindowAndEndsAfterPause() {
        val vad = VoiceActivityDetector(threshold = 500f)

        repeat(6) { vad.acceptRms(50f) }
        var event = VoiceActivityDetector.Event.SILENCE
        repeat(7) { event = vad.acceptRms(1_500f) }   // 140 ms
        assertEquals(VoiceActivityDetector.Event.STARTED, event)
        assertTrue(vad.speaking)

        repeat(20) { assertEquals(VoiceActivityDetector.Event.SPEECH, vad.acceptRms(1_200f)) }

        var end = VoiceActivityDetector.Event.SPEECH
        repeat(40) { end = vad.acceptRms(60f) }        // 800 ms pause
        assertEquals(VoiceActivityDetector.Event.ENDED, end)
        assertFalse(vad.speaking)
    }

    @Test
    fun shortNoiseBurstIsRejected() {
        val vad = VoiceActivityDetector(threshold = 500f)
        repeat(7) { vad.acceptRms(1_500f) }            // started
        var event = VoiceActivityDetector.Event.SPEECH
        repeat(40) { event = vad.acceptRms(40f) }
        assertEquals(VoiceActivityDetector.Event.TOO_SHORT, event)
    }

    @Test
    fun longSpeechHitsTheMaximum() {
        val vad = VoiceActivityDetector(threshold = 400f, maxSpeechMs = 200)
        var event = VoiceActivityDetector.Event.SILENCE
        var frames = 0
        while (event != VoiceActivityDetector.Event.TIMEOUT && frames < 40) {
            event = vad.acceptRms(2_000f)
            frames++
        }
        assertEquals(VoiceActivityDetector.Event.TIMEOUT, event)
        assertFalse(vad.speaking)
    }

    @Test
    fun rmsOfSineFrameIsAboutAmplitudeOverRootTwo() {
        val samples = ShortArray(320) { index ->
            (10_000 * kotlin.math.sin(2.0 * Math.PI * 200.0 * index / 16_000.0)).toInt().toShort()
        }
        val level = VoiceActivityDetector.rms(samples)
        assertTrue("level=$level", level > 6_000f && level < 7_500f)
    }

    // -----------------------------------------------------------------------
    // Speech text
    // -----------------------------------------------------------------------

    @Test
    fun markdownIsReducedToSpeakableText() {
        val markdown = """
            ## Hovedstaden

            **Oslo** er hovedstaden i [Norge](https://example.com).

            ```kotlin
            val x = 1
            ```

            - Punkt en
            - Punkt to
        """.trimIndent()

        val spoken = VoiceText.forSpeech(markdown)
        assertTrue(spoken.contains("Hovedstaden"))
        assertTrue(spoken.contains("Oslo"))
        assertTrue(spoken.contains("Norge"))
        assertFalse(spoken.contains("```"))
        assertFalse(spoken.contains("val x"))
        assertFalse(spoken.contains("**"))
        assertFalse(spoken.contains("https://"))
    }

    @Test
    fun codeOnlyAnswerBecomesEmpty() {
        assertEquals("", VoiceText.forSpeech("```\nfun main() = Unit\n```"))
    }

    @Test
    fun longAnswerIsTrimmedToTheBridgeLimit() {
        val long = (1..400).joinToString(" ") { "ord$it." }
        val spoken = VoiceText.forSpeech(long, maxChars = 200)
        assertTrue(spoken.length <= 201)
        assertTrue(spoken.endsWith(".") || spoken.endsWith("…"))
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)
}
