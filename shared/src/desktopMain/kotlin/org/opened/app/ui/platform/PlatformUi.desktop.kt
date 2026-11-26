package org.opened.app.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop handles back navigation via Esc key / TopAppBar navigation icon
}

@Composable
actual fun rememberIsImeVisible(): Boolean = false

@Composable
actual fun rememberPlatformCopyFeedback(): (label: String, text: String, toastMessage: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { _, text, _ ->
        clipboard.setText(AnnotatedString(text))
    }
}

@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

@Composable
actual fun PlatformThemeSideEffect(colorScheme: ColorScheme, darkTheme: Boolean) {
    // Desktop window chrome is styled by the host OS and Window composable
}
