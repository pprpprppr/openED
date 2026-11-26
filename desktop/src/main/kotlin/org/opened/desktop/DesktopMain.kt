package org.opened.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.ui.platform.LocalOpenEdViewModelFactory
import org.opened.app.ui.platform.LocalPlatformBackupSection
import org.opened.desktop.ui.DesktopAppShell
import org.opened.desktop.ui.theme.DesktopOpenEDTheme
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.UIManager

fun main() {
    runCatching {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    }

    val container = DesktopAppContainer()

    application {
        val windowState = rememberWindowState(width = 1320.dp, height = 860.dp)
        val appIcon = remember { BitmapPainter(createOpenEdAppIcon().toComposeImageBitmap()) }
        val userPrefs by container.preferencesStore.userPreferencesFlow.collectAsState(initial = UserPreferences())
        val themeMode by container.preferencesStore.desktopThemeModeFlow.collectAsState(initial = DesktopThemeMode.MODERN_DARK)
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        Window(
            onCloseRequest = {
                container.ttsManager.shutdown()
                exitApplication()
            },
            state = windowState,
            title = "openED — PC Knowledge Studio",
            icon = appIcon,
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    navController.popBackStack()
                    true
                } else {
                    false
                }
            }
        ) {
            window.minimumSize = Dimension(960, 620)

            DesktopOpenEDTheme(
                themeMode = themeMode,
                highContrast = userPrefs.highContrastEnabled,
                reducedMotion = userPrefs.reducedMotionEnabled
            ) {
                CompositionLocalProvider(
                    LocalOpenEdViewModelFactory provides container.viewModelFactory,
                    LocalPlatformBackupSection provides { uiState, vm ->
                        DesktopBackupAndExportSection(uiState = uiState, viewModel = vm)
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        DesktopAppShell(
                            container = container,
                            navController = navController,
                            appIcon = appIcon,
                            themeMode = themeMode,
                            onSelectThemeMode = { selectedMode ->
                                scope.launch {
                                    container.preferencesStore.setDesktopThemeMode(selectedMode)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun createOpenEdAppIcon(): BufferedImage {
    val stream = DesktopAppContainer::class.java.getResourceAsStream("/icon.png")
    if (stream != null) {
        val loaded = runCatching { stream.use { ImageIO.read(it) } }.getOrNull()
        if (loaded != null) return loaded
    }
    val size = 128
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val gradient = GradientPaint(0f, 0f, Color(0x4F46E5), size.toFloat(), size.toFloat(), Color(0x0EA5E9))
    g.paint = gradient
    g.fillRoundRect(4, 4, size - 8, size - 8, 28, 28)

    g.color = Color.WHITE
    g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 54)
    val fm = g.fontMetrics
    val label = "oE"
    val x = (size - fm.stringWidth(label)) / 2
    val y = (size - fm.height) / 2 + fm.ascent
    g.drawString(label, x, y)
    g.dispose()
    return img
}
