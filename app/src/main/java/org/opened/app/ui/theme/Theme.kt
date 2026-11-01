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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

@Composable
fun OpenEDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun OpenEdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    OpenEDTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}

