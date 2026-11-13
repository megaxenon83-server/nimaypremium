package com.omp.chat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val baseUrl: String = "",
    val paired: Boolean = false,
    val theme: String = "dark",
    val dynamicColor: Boolean = false,
    val language: String = "nb",
    val fontScale: Float = 1f,
    val haptics: Boolean = true,
    val notifications: Boolean = true,
    val showThinking: Boolean = false,
    val activityDetailLevel: Int = 0, // 0 = minimal, 1 = extended
    val speechLanguage: String = "nb-NO",
    val lastCwd: String = "",
    val lastSession: String = "",
    val lastModelProvider: String = "",
    val lastModelId: String = "",
    val lastModelName: String = "",
    val displayName: String = "",
    val voiceHandsFree: Boolean = true,
    val approvalMode: String = "always-ask",
    val sttEngine: String = "",
    val ttsEngine: String = "",
    val voiceSpeed: Float = 1f,
    val voiceBargeIn: Boolean = true,
    val voiceAutoListen: Boolean = true,
    val wolServiceUrl: String = "",
    val wolServiceToken: String = "",
    val animatedActiveIndicator: Boolean = true,
    val gotifyEnabled: Boolean = false,
    val gotifyServer: String = "",
    val gotifyToken: String = "",
    val terminalFontSize: Int = 14,
    val resourceMonitorEnabled: Boolean = true,
    val pinMessagesEnabled: Boolean = true
)

/**
 * Single source of truth for user preferences. The token is never exposed through
 * [state]; it is fetched on demand from [SecureStore].
 */
class SettingsStore(context: Context) {

    val context: Context = context.applicationContext
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secure = SecureStore(appContext)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""

    /** Oppdaterer primæradressen uten å røre tokenet (brukes ved failover). */
    fun updateBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        if (normalized.isBlank() || normalized == baseUrl()) return
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        refresh()
    }

    fun token(): String? = secure.get()

    fun read(): AppSettings = AppSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
        paired = !(prefs.getString(KEY_BASE_URL, "") ?: "").isEmpty() && secure.get() != null,
        theme = prefs.getString(KEY_THEME, "dark") ?: "dark",
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        language = prefs.getString(KEY_LANGUAGE, "nb") ?: "nb",
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        showThinking = prefs.getBoolean(KEY_THINKING, false),
        activityDetailLevel = prefs.getInt(KEY_ACTIVITY_DETAIL, 0),
        speechLanguage = prefs.getString(KEY_SPEECH, "nb-NO") ?: "nb-NO",
        lastCwd = prefs.getString(KEY_LAST_CWD, "") ?: "",
        lastSession = prefs.getString(KEY_LAST_SESSION, "") ?: "",
        lastModelProvider = prefs.getString(KEY_LAST_MODEL_PROVIDER, "") ?: "",
        lastModelId = prefs.getString(KEY_LAST_MODEL_ID, "") ?: "",
        lastModelName = prefs.getString(KEY_LAST_MODEL_NAME, "") ?: "",
        displayName = prefs.getString(KEY_NAME, "") ?: "",
        voiceHandsFree = prefs.getBoolean(KEY_VOICE_HANDSFREE, true),
        approvalMode = prefs.getString(KEY_APPROVAL_MODE, "always-ask") ?: "always-ask",
        sttEngine = prefs.getString(KEY_STT_ENGINE, "") ?: "",
        ttsEngine = prefs.getString(KEY_TTS_ENGINE, "") ?: "",
        voiceSpeed = prefs.getFloat(KEY_VOICE_SPEED, 1f),
        voiceBargeIn = prefs.getBoolean(KEY_VOICE_BARGE_IN, true),
        voiceAutoListen = prefs.getBoolean(KEY_VOICE_AUTO_LISTEN, true),
        wolServiceUrl = prefs.getString(KEY_WOL_SERVICE_URL, "") ?: "",
        wolServiceToken = prefs.getString(KEY_WOL_SERVICE_TOKEN, "") ?: "",
        animatedActiveIndicator = prefs.getBoolean(KEY_ANIMATED_ACTIVE, true),
        gotifyEnabled = prefs.getBoolean(KEY_GOTIFY_ENABLED, false),
        gotifyServer = prefs.getString(KEY_GOTIFY_SERVER, "") ?: "",
        gotifyToken = prefs.getString(KEY_GOTIFY_TOKEN, "") ?: "",
        terminalFontSize = prefs.getInt(KEY_TERMINAL_FONT_SIZE, 14),
        resourceMonitorEnabled = prefs.getBoolean(KEY_RESOURCE_MONITOR, true),
        pinMessagesEnabled = prefs.getBoolean(KEY_PIN_MESSAGES, true)
    )

    fun saveConnection(baseUrl: String, token: String) {
        val normalized = normalizeBaseUrl(baseUrl)
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        secure.put(token)
        refresh()
    }

    fun clearConnection() {
        prefs.edit().remove(KEY_BASE_URL).apply()
        secure.clear()
        refresh()
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        refresh()
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        refresh()
    }

    fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
        refresh()
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale.coerceIn(0.8f, 1.35f)).apply()
        refresh()
    }

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        refresh()
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        refresh()
    }

    fun setShowThinking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THINKING, enabled).apply()
        refresh()
    }

    fun setActivityDetailLevel(level: Int) {
        prefs.edit().putInt(KEY_ACTIVITY_DETAIL, level.coerceIn(0, 1)).apply()
        refresh()
    }

    fun setSpeechLanguage(language: String) {
        prefs.edit().putString(KEY_SPEECH, language).apply()
        refresh()
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        refresh()
    }

    fun setVoiceHandsFree(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_HANDSFREE, enabled).apply()
        refresh()
    }

    fun setApprovalMode(mode: String) {
        prefs.edit().putString(KEY_APPROVAL_MODE, mode).apply()
        refresh()
    }

    fun setSttEngine(engineId: String) {
        prefs.edit().putString(KEY_STT_ENGINE, engineId).apply()
        refresh()
    }

    fun setTtsEngine(engineId: String) {
        prefs.edit().putString(KEY_TTS_ENGINE, engineId).apply()
        refresh()
    }

    fun setVoiceSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_VOICE_SPEED, speed.coerceIn(0.5f, 2.0f)).apply()
        refresh()
    }

    fun setVoiceBargeIn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_BARGE_IN, enabled).apply()
        refresh()
    }

    fun setVoiceAutoListen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_AUTO_LISTEN, enabled).apply()
        refresh()
    }

    fun setWolServiceUrl(url: String) {
        prefs.edit().putString(KEY_WOL_SERVICE_URL, url.trim()).apply()
        refresh()
    }

    fun setWolServiceToken(token: String) {
        prefs.edit().putString(KEY_WOL_SERVICE_TOKEN, token.trim()).apply()
        refresh()
    }

    fun setAnimatedActiveIndicator(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATED_ACTIVE, enabled).apply()
        refresh()
    }

    fun setGotifyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GOTIFY_ENABLED, enabled).apply()
        refresh()
    }

    fun setGotifyServer(server: String) {
        prefs.edit().putString(KEY_GOTIFY_SERVER, server).apply()
        refresh()
    }

    fun setGotifyToken(token: String) {
        prefs.edit().putString(KEY_GOTIFY_TOKEN, token).apply()
        refresh()
    }

    fun setTerminalFontSize(size: Int) {
        prefs.edit().putInt(KEY_TERMINAL_FONT_SIZE, size.coerceIn(10, 28)).apply()
        refresh()
    }

    fun setResourceMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESOURCE_MONITOR, enabled).apply()
        refresh()
    }

    fun setPinMessagesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIN_MESSAGES, enabled).apply()
        refresh()
    }

    fun rememberLast(cwd: String, sessionFile: String?) {
        prefs.edit()
            .putString(KEY_LAST_CWD, cwd)
            .putString(KEY_LAST_SESSION, sessionFile ?: "")
            .apply()
        refresh()
    }

    /** Keeps the chosen model so a new chat or a restart does not reset it. */
    fun rememberModel(provider: String, id: String, name: String) {
        prefs.edit()
            .putString(KEY_LAST_MODEL_PROVIDER, provider)
            .putString(KEY_LAST_MODEL_ID, id)
            .putString(KEY_LAST_MODEL_NAME, name)
            .apply()
        refresh()
    }

    private fun refresh() {
        _state.value = read()
    }

    companion object {
        private const val PREFS = "ompchat_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_THINKING = "show_thinking"
        private const val KEY_ACTIVITY_DETAIL = "activity_detail_level"
        private const val KEY_SPEECH = "speech_lang"
        private const val KEY_LAST_CWD = "last_cwd"
        private const val KEY_LAST_SESSION = "last_session"
        private const val KEY_LAST_MODEL_PROVIDER = "last_model_provider"
        private const val KEY_LAST_MODEL_ID = "last_model_id"
        private const val KEY_LAST_MODEL_NAME = "last_model_name"
        private const val KEY_NAME = "display_name"
        private const val KEY_VOICE_HANDSFREE = "voice_hands_free"
        private const val KEY_APPROVAL_MODE = "approval_mode"
        private const val KEY_STT_ENGINE = "stt_engine"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_BARGE_IN = "voice_barge_in"
        private const val KEY_VOICE_AUTO_LISTEN = "voice_auto_listen"
        private const val KEY_WOL_SERVICE_URL = "wol_service_url"
        private const val KEY_WOL_SERVICE_TOKEN = "wol_service_token"
        private const val KEY_ANIMATED_ACTIVE = "animated_active_indicator"
        private const val KEY_GOTIFY_ENABLED = "gotify_enabled"
        private const val KEY_GOTIFY_SERVER = "gotify_server"
        private const val KEY_GOTIFY_TOKEN = "gotify_token"
        private const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
        private const val KEY_RESOURCE_MONITOR = "resource_monitor_enabled"
        private const val KEY_PIN_MESSAGES = "pin_messages_enabled"

        /** Accepts `omp://host:port?token=...`, `omp2://…`, `ompchat://…` and plain URLs. */
        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            val lower = value.lowercase()
            for (scheme in listOf("ompchat://", "omp2://", "omp://")) {
                if (lower.startsWith(scheme)) {
                    value = "http://" + value.substring(scheme.length)
                    break
                }
            }
            if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
                value = "http://$value"
            }
            value = if (value.startsWith("https://", true)) {
                "https://" + value.substring(8)
            } else {
                "http://" + value.substring(7)
            }
            value = value.substringBefore("?").substringBefore("#").trimEnd('/')
            if (value.endsWith("/v1")) value = value.removeSuffix("/v1")
            return value
        }

        /** Extracts `token` from a pairing payload; keeps tokens URL-encoded by QR tools. */
        fun tokenFrom(raw: String): String? {
            val query = raw.substringAfter('?', "")
            if (query.isEmpty()) return null
            for (pair in query.split('&')) {
                val key = pair.substringBefore('=')
                if (key.equals("token", true) || key.equals("api_key", true) || key.equals("key", true)) {
                    val value = pair.substringAfter('=', "")
                    return try {
                        java.net.URLDecoder.decode(value, "UTF-8")
                    } catch (error: Exception) {
                        value
                    }
                }
            }
            return null
        }
    }
}
