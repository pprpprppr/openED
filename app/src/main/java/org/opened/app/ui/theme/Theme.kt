package org.opened.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalReducedMotion = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = OpenEDPrimary,
    onPrimary = OpenEDOnPrimary,
    primaryContainer = OpenEDPrimaryContainer,
    onPrimaryContainer = OpenEDOnPrimaryContainer,
    secondary = OpenEDSecondary,
    onSecondary = OpenEDOnSecondary,
    secondaryContainer = OpenEDSecondaryContainer,
    onSecondaryContainer = OpenEDOnSecondaryContainer,
    tertiary = OpenEDTertiary,
    onTertiary = OpenEDOnTertiary,
    tertiaryContainer = OpenEDTertiaryContainer,
    onTertiaryContainer = OpenEDOnTertiaryContainer,
    background = OpenEDDarkBackground,
    onBackground = OpenEDOnPrimary,
    surface = OpenEDDarkSurface,
    onSurface = OpenEDOnPrimary,
    surfaceVariant = OpenEDDarkSurfaceVariant,
    onSurfaceVariant = OpenEDOnPrimaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = OpenEDPrimary,
    onPrimary = OpenEDOnPrimary,
    primaryContainer = OpenEDPrimaryContainer,
    onPrimaryContainer = OpenEDOnPrimaryContainer,
    secondary = OpenEDSecondary,
    onSecondary = OpenEDOnSecondary,
    secondaryContainer = OpenEDSecondaryContainer,
    onSecondaryContainer = OpenEDOnSecondaryContainer,
    tertiary = OpenEDTertiary,
    onTertiary = OpenEDOnTertiary,
    tertiaryContainer = OpenEDTertiaryContainer,
    onTertiaryContainer = OpenEDOnTertiaryContainer,
    background = OpenEDLightBackground,
    onBackground = OpenEDDarkBackground,
    surface = OpenEDLightSurface,
    onSurface = OpenEDDarkBackground,
    surfaceVariant = OpenEDLightSurfaceVariant,
    onSurfaceVariant = OpenEDDarkSurfaceVariant
)

// Phase 8: High-Contrast Color Schemes for accessibility (WCAG AAA compliance)
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC), // High-visibility indigo
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF67E8F9), // High-visibility cyan
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0E7490),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFC4B5FD), // High-visibility lavender
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000), // Pure Black for maximum OLED contrast
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFFFFFFF)
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E1B4B), // Deepest indigo for maximum text contrast
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF164E63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF4C1D95),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFFFFFFF), // Pure White
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF000000)
)

@Composable
fun OpenEDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast && !darkTheme -> HighContrastLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun OpenEdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    OpenEDTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
        content = content
    )
}
