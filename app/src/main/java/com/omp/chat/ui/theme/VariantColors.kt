package com.omp.chat.ui.theme

import androidx.compose.ui.graphics.Color
import com.omp.chat.BuildConfig

// ---------------------------------------------------------------------------
// Variant-aware color system
//
// Classic flavour uses Nimay colours; ChatGPT-inspired flavour uses a neutral,
// near-black palette. Shared screens read from VariantColors so they adapt
// automatically to the active product flavour without hardcoding Nimay colours.
// ---------------------------------------------------------------------------

object VariantColors {
    /** True when building the chatgpt-inspired product flavour. */
    val isChatgpt: Boolean
        get() = BuildConfig.FLAVOR == "chstgpt"

    // ── Backgrounds ──────────────────────────────────────────────────────
    val background: Color
        get() = if (isChatgpt) Color(0xFF0D0D0D) else NimayBackground
    val sidebar: Color
        get() = if (isChatgpt) Color(0xFF171717) else NimaySidebar
    val surface: Color
        get() = if (isChatgpt) Color(0xFF212121) else NimaySurface
    val surfaceElevated: Color
        get() = if (isChatgpt) Color(0xFF272727) else Color(0xFF172B47)

    // ── Text ─────────────────────────────────────────────────────────────
    val textPrimary: Color
        get() = if (isChatgpt) Color(0xFFF5F5F5) else NimayTextPrimary
    val textSecondary: Color
        get() = if (isChatgpt) Color(0xFFA3A3A3) else NimayTextSecondary
    val textTertiary: Color
        get() = if (isChatgpt) Color(0xFF8E8E8E) else Color(0xFFA2B4CF)

    // ── Dividers & borders ───────────────────────────────────────────────
    val divider: Color
        get() = if (isChatgpt) Color(0xFF3A3A3A) else NimayDivider

    // ── Accents ──────────────────────────────────────────────────────────
    val accent: Color
        get() = if (isChatgpt) Color(0xFF10A37F) else NimayAccent
    val onAccent: Color
        get() = if (isChatgpt) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
    val error: Color
        get() = if (isChatgpt) Color(0xFFFF5F56) else NimayError
    val warning: Color
        get() = if (isChatgpt) Color(0xFFFFB300) else Color(0xFFFF6278)
    val online: Color
        get() = if (isChatgpt) Color(0xFF8E8E8E) else NimayActive

    // ── Code blocks ──────────────────────────────────────────────────────
    val codeBackground: Color
        get() = if (isChatgpt) Color(0xFF171717) else Color(0xFF111F35)
    val codeHeader: Color
        get() = if (isChatgpt) Color(0xFF1F1F1F) else Color(0xFF0D192B)
    val inlineCode: Color
        get() = if (isChatgpt) Color(0xFF2A2A2A) else Color(0xFF1D2427)

    // ── User message bubble ──────────────────────────────────────────────
    val userBubble: Color
        get() = if (isChatgpt) Color(0xFF2A2A2A) else NimayUserBubble
    val selected: Color
        get() = if (isChatgpt) Color(0xFF333333) else Color(0xFF263F60)
}
