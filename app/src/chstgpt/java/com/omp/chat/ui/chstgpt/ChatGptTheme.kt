package com.omp.chat.ui.chstgpt

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.R

// ---------------------------------------------------------------------------
// ChatGPT-inspired design tokens. Colours match the official ChatGPT Android
// app dark theme as closely as possible; no proprietary assets are copied.
// ---------------------------------------------------------------------------

@Immutable
data class ChatGptColors(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val userBubble: Color,
    val selected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val inlineCode: Color,
    val error: Color,
    val warning: Color,
    val online: Color
)

private val Dark = ChatGptColors(
    background = Color(0xFF0D0D0D),
    sidebar = Color(0xFF171717),
    surface = Color(0xFF212121),
    surfaceElevated = Color(0xFF272727),
    userBubble = Color(0xFF2A2A2A),
    selected = Color(0xFF333333),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFA3A3A3),
    textTertiary = Color(0xFF8E8E8E),
    divider = Color(0xFF3A3A3A),
    accent = Color(0xFFF5F5F5),
    onAccent = Color(0xFF0D0D0D),
    codeBackground = Color(0xFF171717),
    codeHeader = Color(0xFF1F1F1F),
    inlineCode = Color(0xFF2A2A2A),
    error = Color(0xFFFF5F56),
    warning = Color(0xFFFFB300),
    online = Color(0xFF8E8E8E)
)

private val Light = ChatGptColors(
    background = Color(0xFFF5F5F5),
    sidebar = Color(0xFFEAEAEA),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    userBubble = Color(0xFFE5E5EA),
    selected = Color(0xFFD1D1D6),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF8E8E93),
    textTertiary = Color(0xFFC7C7CC),
    divider = Color(0xFFE5E5EA),
    accent = Color(0xFF1C1C1E),
    onAccent = Color(0xFFF5F5F5),
    codeBackground = Color(0xFFF0F0F0),
    codeHeader = Color(0xFFE5E5EA),
    inlineCode = Color(0xFFCCCCCC),
    error = Color(0xFFFF3B30),
    warning = Color(0xFFFF9500),
    online = Color(0xFF8E8E8E)
)

val LocalChatGptColors = staticCompositionLocalOf { Dark }

private val Inter = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal),
    Font(R.font.inter, weight = FontWeight.Medium),
    Font(R.font.inter, weight = FontWeight.SemiBold),
    Font(R.font.inter, weight = FontWeight.Bold)
)

private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.4).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp
        ),
        titleMedium = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp
        ),
        titleSmall = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.1).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp
        ),
        labelLarge = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    )
}

private val ChatGptShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun ChatGptTheme(
    theme: String,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.background,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.error,
            onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.background,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.error,
            onError = Color.White
        )
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalChatGptColors provides palette,
        LocalDensity provides Density(density.density, density.fontScale * fontScale.coerceIn(0.8f, 1.35f))
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = ChatGptShapes,
            content = content
        )
    }
}
