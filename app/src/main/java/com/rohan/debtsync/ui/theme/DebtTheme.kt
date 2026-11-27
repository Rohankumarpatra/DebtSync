package com.rohan.debtsync.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core Gemini Palette Accents
val GeminiBlue = Color(0xFF4285F4)
val GeminiIndigo = Color(0xFF5B6CF9)
val GeminiPurple = Color(0xFF9B72CB)
val GeminiRose = Color(0xFFD96570)
val GeminiCyan = Color(0xFF38BDF8)

@Immutable
data class DebtPalette(
    val isDark: Boolean,
    val bgBase: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val subtleSurface: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoftBg: Color,
    val accentBorder: Color,
    val success: Color,
    val successBg: Color,
    val successBorder: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val barBg: Color,
    val barBorder: Color,
    val summaryBannerBg: Color,
    val summaryBannerText: Color,
    val summaryBannerMuted: Color
)

val LightDebtPalette = DebtPalette(
    isDark = false,
    bgBase = Color(0xFFF6F8FD),
    cardBg = Color(0xE8FFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    subtleSurface = Color(0xFFF1F5FB),
    divider = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    accent = Color(0xFF4C6EF5),
    accentSoftBg = Color(0xFFEDF2FF),
    accentBorder = Color(0xFFBAC8FF),
    success = Color(0xFF059669),
    successBg = Color(0xFFECFDF5),
    successBorder = Color(0xFFA7F3D0),
    warning = Color(0xFFB45309),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFE11D48),
    barBg = Color(0xEBFFFFFF),
    barBorder = Color(0xFFE2E8F0),
    summaryBannerBg = Color(0xFF131A2E),
    summaryBannerText = Color(0xFFFFFFFF),
    summaryBannerMuted = Color(0xFFCBD5E1)
)

val DarkDebtPalette = DebtPalette(
    isDark = true,
    bgBase = Color(0xFF090D18),
    cardBg = Color(0xD9141B2D),
    cardBorder = Color(0xFF26314D),
    subtleSurface = Color(0xFF1A233A),
    divider = Color(0xFF232E47),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accent = Color(0xFF8AB4F8),
    accentSoftBg = Color(0xFF1E2949),
    accentBorder = Color(0xFF3B538C),
    success = Color(0xFF34D399),
    successBg = Color(0xFF0F2D27),
    successBorder = Color(0xFF1B5E4E),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF33260F),
    danger = Color(0xFFFB7185),
    barBg = Color(0xE00F1526),
    barBorder = Color(0xFF1E2942),
    summaryBannerBg = Color(0xFF1A2238),
    summaryBannerText = Color(0xFFF8FAFC),
    summaryBannerMuted = Color(0xFF94A3B8)
)

val LocalDebtPalette = staticCompositionLocalOf { LightDebtPalette }

private val LightColors = lightColorScheme(
    primary = Color(0xFF4C6EF5),
    onPrimary = Color.White,
    secondary = GeminiPurple,
    tertiary = GeminiRose,
    background = Color(0xFFF6F8FD),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5FB),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0A1428),
    secondary = Color(0xFFC58AF9),
    tertiary = Color(0xFFF28B95),
    background = Color(0xFF090D18),
    surface = Color(0xFF141B2D),
    surfaceVariant = Color(0xFF1A233A),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

@Composable
fun DebtSyncTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val palette = if (isDark) DarkDebtPalette else LightDebtPalette
    val colorScheme = if (isDark) DarkColors else LightColors

    CompositionLocalProvider(LocalDebtPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

@Composable
fun GeminiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalDebtPalette.current
    val isDark = palette.isDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgBase)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-left Gemini Blue glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GeminiBlue.copy(alpha = if (isDark) 0.24f else 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.12f, h * 0.08f),
                    radius = w * 0.95f
                )
            )

            // Top-right Gemini Purple glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GeminiPurple.copy(alpha = if (isDark) 0.22f else 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.90f, h * 0.22f),
                    radius = w * 0.85f
                )
            )

            // Mid-left Gemini Rose / Spark glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GeminiRose.copy(alpha = if (isDark) 0.16f else 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.62f),
                    radius = w * 0.80f
                )
            )

            // Bottom-right Gemini Cyan/Blue glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GeminiCyan.copy(alpha = if (isDark) 0.14f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.90f),
                    radius = w * 0.85f
                )
            )
        }
        content()
    }
}
