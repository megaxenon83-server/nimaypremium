package com.omp.chat.ui.theme

import androidx.compose.ui.graphics.Color
import com.omp.chat.BuildConfig

// ---------------------------------------------------------------------------
// Variant-aware color system
//
// Classic flavour uses Nimay colours; ChatGPT-inspired flavour (super-premium)
// uses a neutral palette. Shared/legacy screens read from VariantColors so they
// adapt automatically. For super-premium, an active theme snapshot can be
// provided so colours follow light/dark selection.
// ---------------------------------------------------------------------------

data class VariantPaletteSnapshot(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
    val warning: Color,
    val online: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val inlineCode: Color,
    val userBubble: Color,
    val selected: Color
)

object VariantPaletteState {
    @Volatile var superPremium: VariantPaletteSnapshot? = null
}

object VariantColors {
    /** True when building the chatgpt-inspired/super-premium product flavour. */
    val isChatgpt: Boolean
        get() = BuildConfig.FLAVOR == "chstgpt"

    private val sp: VariantPaletteSnapshot? get() = if (isChatgpt) VariantPaletteState.superPremium else null

    // ── Backgrounds ──────────────────────────────────────────────────────
    val background: Color
        get() = sp?.background ?: if (isChatgpt) Color(0xFF0D0D0D) else NimayBackground
    val sidebar: Color
        get() = sp?.sidebar ?: if (isChatgpt) Color(0xFF171717) else NimaySidebar
    val surface: Color
        get() = sp?.surface ?: if (isChatgpt) Color(0xFF212121) else NimaySurface
    val surfaceElevated: Color
        get() = sp?.surfaceElevated ?: if (isChatgpt) Color(0xFF272727) else Color(0xFF172B47)

    // ── Text ─────────────────────────────────────────────────────────────
    val textPrimary: Color
        get() = sp?.textPrimary ?: if (isChatgpt) Color(0xFFF5F5F5) else NimayTextPrimary
    val textSecondary: Color
        get() = sp?.textSecondary ?: if (isChatgpt) Color(0xFFA3A3A3) else NimayTextSecondary
    val textTertiary: Color
        get() = sp?.textTertiary ?: if (isChatgpt) Color(0xFF8E8E8E) else Color(0xFFA2B4CF)

    // ── Dividers & borders ───────────────────────────────────────────────
    val divider: Color
        get() = sp?.divider ?: if (isChatgpt) Color(0xFF3A3A3A) else NimayDivider

    // ── Accents ──────────────────────────────────────────────────────────
    val accent: Color
        get() = sp?.accent ?: if (isChatgpt) Color(0xFF10A37F) else NimayAccent
    val onAccent: Color
        get() = sp?.onAccent ?: if (isChatgpt) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
    val error: Color
        get() = sp?.error ?: if (isChatgpt) Color(0xFFFF5F56) else NimayError
    val warning: Color
        get() = sp?.warning ?: if (isChatgpt) Color(0xFFFFB300) else Color(0xFFFF6278)
    val online: Color
        get() = sp?.online ?: if (isChatgpt) Color(0xFF8E8E8E) else NimayActive

    // ── Code blocks ──────────────────────────────────────────────────────
    val codeBackground: Color
        get() = sp?.codeBackground ?: if (isChatgpt) Color(0xFF171717) else Color(0xFF111F35)
    val codeHeader: Color
        get() = sp?.codeHeader ?: if (isChatgpt) Color(0xFF1F1F1F) else Color(0xFF0D192B)
    val inlineCode: Color
        get() = sp?.inlineCode ?: if (isChatgpt) Color(0xFF2A2A2A) else Color(0xFF1D2427)

    // ── User message bubble ──────────────────────────────────────────────
    val userBubble: Color
        get() = sp?.userBubble ?: if (isChatgpt) Color(0xFF2A2A2A) else NimayUserBubble
    val selected: Color
        get() = sp?.selected ?: if (isChatgpt) Color(0xFF333333) else Color(0xFF263F60)
}
