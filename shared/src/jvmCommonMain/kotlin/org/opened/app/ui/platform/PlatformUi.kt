package org.opened.app.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opened.app.ui.settings.SettingsUiState
import org.opened.app.ui.settings.SettingsViewModel

val LocalOpenEdViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("LocalOpenEdViewModelFactory must be provided at the root of openED")
}

@Composable
inline fun <reified VM : ViewModel> openEdViewModel(key: String? = null): VM {
    val factory = LocalOpenEdViewModelFactory.current
    return viewModel(
        modelClass = VM::class,
        key = key,
        factory = factory
    )
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun rememberIsImeVisible(): Boolean

@Composable
expect fun rememberPlatformCopyFeedback(): (label: String, text: String, toastMessage: String) -> Unit

@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
expect fun PlatformThemeSideEffect(colorScheme: ColorScheme, darkTheme: Boolean)

val LocalPlatformBackupSection = staticCompositionLocalOf<@Composable (SettingsUiState, SettingsViewModel) -> Unit> {
    { _, _ -> }
}
