package org.opened.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.opened.app.ui.theme.LocalReducedMotion
import org.opened.app.ui.theme.OpenEDDarkBackground
import org.opened.app.ui.theme.OpenEDDarkSurface
import org.opened.app.ui.theme.OpenEDDarkSurfaceVariant
import org.opened.app.ui.theme.OpenEDOnPrimary
import org.opened.app.ui.theme.OpenEDOnPrimaryContainer
import org.opened.app.ui.theme.OpenEDOnSecondary
import org.opened.app.ui.theme.OpenEDOnSecondaryContainer
import org.opened.app.ui.theme.OpenEDOnTertiary
import org.opened.app.ui.theme.OpenEDOnTertiaryContainer
import org.opened.app.ui.theme.OpenEDPrimary
import org.opened.app.ui.theme.OpenEDPrimaryContainer
import org.opened.app.ui.theme.OpenEDSecondary
import org.opened.app.ui.theme.OpenEDSecondaryContainer
import org.opened.app.ui.theme.OpenEDTertiary
import org.opened.app.ui.theme.OpenEDTertiaryContainer
import org.opened.app.ui.theme.Typography
import org.opened.desktop.DesktopThemeMode

@Immutable
data class DesktopTokens(
    val mode: DesktopThemeMode,
    val isDark: Boolean,
    val canvas: Color,
    val sidebar: Color,
    val header: Color,
    val card: Color,
    val cardSubtle: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

val LocalDesktopTokens = staticCompositionLocalOf {
    ModernDarkTokens
}

private val ModernDarkTokens = DesktopTokens(
    mode = DesktopThemeMode.MODERN_DARK,
    isDark = true,
    canvas = Color(0xFF0A0D14),
    sidebar = Color(0xFF0F131D),
    header = Color(0xFF0D1119),
    card = Color(0xFF141926),
    cardSubtle = Color(0xFF192030),
    border = Color(0xFF232B3E),
    borderStrong = Color(0xFF33415C),
    accent = Color(0xFF6366F1),
    accentSoft = Color(0x296366F1),
    secondaryAccent = Color(0xFF22D3EE),
    textPrimary = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444)
)

private val ModernWhiteTokens = DesktopTokens(
    mode = DesktopThemeMode.MODERN_WHITE,
    isDark = false,
    canvas = Color(0xFFFFFFFF),
    sidebar = Color(0xFFF8FAFC),
    header = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardSubtle = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    accent = Color(0xFF4F46E5),
    accentSoft = Color(0x174F46E5),
    secondaryAccent = Color(0xFF0284C7),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF475569),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626)
)

private val ExistingClassicTokens = DesktopTokens(
    mode = DesktopThemeMode.EXISTING,
    isDark = true,
    canvas = OpenEDDarkBackground,
    sidebar = Color(0xFF0E1422),
    header = OpenEDDarkBackground,
    card = OpenEDDarkSurface,
    cardSubtle = OpenEDDarkSurfaceVariant,
    border = Color(0xFF2D3748),
    borderStrong = Color(0xFF4B5563),
    accent = OpenEDPrimary,
    accentSoft = Color(0x2E6366F1),
    secondaryAccent = OpenEDSecondary,
    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFFCBD5E1),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444)
)

private val ModernDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E2A6E),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF001F25),
    secondaryContainer = Color(0xFF123B48),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1E103D),
    tertiaryContainer = Color(0xFF3E2470),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = ModernDarkTokens.canvas,
    onBackground = ModernDarkTokens.textPrimary,
    surface = ModernDarkTokens.card,
    onSurface = ModernDarkTokens.textPrimary,
    surfaceVariant = ModernDarkTokens.cardSubtle,
    onSurfaceVariant = ModernDarkTokens.textMuted,
    outline = ModernDarkTokens.borderStrong,
    outlineVariant = ModernDarkTokens.border,
    surfaceContainerLowest = Color(0xFF080B11),
    surfaceContainerLow = ModernDarkTokens.sidebar,
    surfaceContainer = ModernDarkTokens.card,
    surfaceContainerHigh = ModernDarkTokens.cardSubtle,
    surfaceContainerHighest = Color(0xFF20293E)
)

private val ModernWhiteColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = ModernWhiteTokens.canvas,
    onBackground = ModernWhiteTokens.textPrimary,
    surface = ModernWhiteTokens.card,
    onSurface = ModernWhiteTokens.textPrimary,
    surfaceVariant = ModernWhiteTokens.cardSubtle,
    onSurfaceVariant = ModernWhiteTokens.textMuted,
    outline = ModernWhiteTokens.borderStrong,
    outlineVariant = ModernWhiteTokens.border,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1)
)

private val ClassicExistingColorScheme = darkColorScheme(
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
    onSurfaceVariant = OpenEDOnPrimaryContainer,
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF2D3748)
)

private val HighContrastDarkScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF67E8F9),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E7490),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF080808),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color.White,
    outline = Color(0xFF818CF8),
    outlineVariant = Color(0xFF52525B)
)

private val HighContrastLightScheme = lightColorScheme(
    primary = Color(0xFF1E1B4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF0C4A6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color(0xFF475569)
)

@Composable
fun DesktopOpenEDTheme(
    themeMode: DesktopThemeMode,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseTokens = when (themeMode) {
        DesktopThemeMode.MODERN_DARK -> ModernDarkTokens
        DesktopThemeMode.MODERN_WHITE -> ModernWhiteTokens
        DesktopThemeMode.EXISTING -> ExistingClassicTokens
    }

    val tokens = if (!highContrast) {
        baseTokens
    } else if (baseTokens.isDark) {
        baseTokens.copy(
            canvas = Color.Black,
            sidebar = Color(0xFF050505),
            card = Color(0xFF0B0B0B),
            cardSubtle = Color(0xFF171717),
            border = Color(0xFF52525B),
            borderStrong = Color(0xFFA5B4FC),
            textPrimary = Color.White,
            textMuted = Color(0xFFE2E8F0)
        )
    } else {
        baseTokens.copy(
            canvas = Color.White,
            sidebar = Color(0xFFFAFAFA),
            card = Color.White,
            cardSubtle = Color(0xFFF1F5F9),
            border = Color(0xFF334155),
            borderStrong = Color.Black,
            textPrimary = Color.Black,
            textMuted = Color(0xFF0F172A)
        )
    }

    val colorScheme = when {
        highContrast && tokens.isDark -> HighContrastDarkScheme
        highContrast && !tokens.isDark -> HighContrastLightScheme
        themeMode == DesktopThemeMode.MODERN_DARK -> ModernDarkColorScheme
        themeMode == DesktopThemeMode.MODERN_WHITE -> ModernWhiteColorScheme
        else -> ClassicExistingColorScheme
    }

    CompositionLocalProvider(
        LocalDesktopTokens provides tokens,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
