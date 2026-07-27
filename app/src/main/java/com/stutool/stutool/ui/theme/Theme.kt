package com.stutool.stutool.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground, // Mapped to text
    surfaceVariant = LightSurfaceContainer, // Fallback for old compose versions
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorColor
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceContainerHigh, // Fallback
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorColor
)

// ── Glass surface system tokens ──────────────────────────────────────────────
// These hold the theme-dependent values for the glass illusion layer system.
// Components reference these via LocalGlassTokens instead of hard-coding alphas.

data class GlassTokens(
    /** Soft vertical background gradient brush */
    val backgroundBrush: Brush,
    /** Card surface with slight transparency */
    val cardSurface: Color,
    /** Top-edge highlight for cards (simulated light reflection) */
    val cardHighlight: Color,
    /** Border color for glass cards */
    val cardBorder: Color,
    /** Drawer panel surface */
    val drawerSurface: Color,
    /** Bottom nav surface */
    val navSurface: Color,
    /** Top bar surface */
    val topBarSurface: Color,
    /** Subtle divider below top bar */
    val topBarDivider: Color,
    /** Nav bar border highlight */
    val navBorder: Color,
    /** CTA button highlight */
    val ctaHighlight: Color
)

val LocalGlassTokens = staticCompositionLocalOf {
    // Fallback – overridden inside StuToolTheme
    GlassTokens(
        backgroundBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        cardSurface = Color.Unspecified,
        cardHighlight = Color.Transparent,
        cardBorder = Color.Transparent,
        drawerSurface = Color.Unspecified,
        navSurface = Color.Unspecified,
        topBarSurface = Color.Unspecified,
        topBarDivider = Color.Transparent,
        navBorder = Color.Transparent,
        ctaHighlight = Color.Transparent
    )
}

/**
 * Single source of truth for whether the app is in dark mode.
 * This respects the app-level theme override (from ThemeViewModel/ThemePreferences),
 * NOT the raw system/device dark mode. All UI components must read from this
 * instead of calling isSystemInDarkTheme() directly.
 */
val LocalAppThemeIsDark = compositionLocalOf { false }

@Composable
fun StuToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but we want to stick to our custom palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val glassTokens = if (darkTheme) {
        GlassTokens(
            backgroundBrush = Brush.verticalGradient(
                colors = listOf(DarkBackground, DarkBackgroundVariant)
            ),
            cardSurface = DarkSurface.copy(alpha = 0.90f),
            cardHighlight = Color.White.copy(alpha = 0.05f),
            cardBorder = Color.White.copy(alpha = 0.06f),
            drawerSurface = DarkSurface.copy(alpha = 0.92f),
            navSurface = DarkSurface.copy(alpha = 0.85f),
            topBarSurface = DarkSurfaceContainerLow.copy(alpha = 0.95f),
            topBarDivider = Color.White.copy(alpha = 0.05f),
            navBorder = Color.White.copy(alpha = 0.08f),
            ctaHighlight = Color.White.copy(alpha = 0.15f)
        )
    } else {
        GlassTokens(
            backgroundBrush = Brush.verticalGradient(
                colors = listOf(LightBackground, LightBackgroundVariant)
            ),
            cardSurface = LightSurface.copy(alpha = 0.95f),
            cardHighlight = Color.White.copy(alpha = 0.03f),
            cardBorder = LightOutline,
            drawerSurface = LightSurface.copy(alpha = 0.97f),
            navSurface = Color.White.copy(alpha = 0.90f),
            topBarSurface = LightSurface.copy(alpha = 0.97f),
            topBarDivider = Color.Black.copy(alpha = 0.04f),
            navBorder = Color.Black.copy(alpha = 0.05f),
            ctaHighlight = Color.White.copy(alpha = 0.25f)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalGlassTokens provides glassTokens,
        LocalAppThemeIsDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}