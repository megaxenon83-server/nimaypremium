package com.omp.chat.data

/**
 * Central feature flags. Voice is preserved in source but disabled by default
 * (Etappe 8) so the UI stays clean. Set VoiceEnabled = true to re-enable.
 */
object FeatureFlags {
    const val VoiceEnabled: Boolean = false
}
