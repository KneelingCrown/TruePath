package ru.truepath.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import ru.truepath.app.ui.theme.LocalTruePathDark

/** Dialogs own their window and do not inherit the Activity's bar icon colors. */
@Composable
fun DialogBars(dark: Boolean = LocalTruePathDark.current) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
