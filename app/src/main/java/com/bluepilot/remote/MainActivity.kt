package com.bluepilot.remote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.ThemeMode
import com.bluepilot.remote.ui.components.LocalHapticIntensity
import com.bluepilot.remote.ui.navigation.BluePilotApp
import com.bluepilot.remote.ui.theme.BluePilotAppTheme
import com.bluepilot.remote.ui.theme.BuiltInThemes
import com.bluepilot.remote.ui.theme.ThemedBackground
import com.bluepilot.remote.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Applies live app settings:
 *  - theme (Light/Dark/System)
 *  - keep screen on
 *  - secure screen (FLAG_SECURE)
 *  - fullscreen (immersive) mode
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val app by settingsViewModel.app.collectAsState()

            // Section 1 theme engine: resolve the active AppThemeSpec.
            // Light/Dark/System mode maps onto the spec catalog: if the user
            // forces LIGHT but picked a dark spec (or vice versa), we swap to
            // the closest built-in of the requested brightness.
            val systemDark = isSystemInDarkTheme()
            val baseSpec = BuiltInThemes.byId(app.themeId)
            val wantDark = when (app.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            // Family-aware fallback: forcing Light while Hawaii Night is
            // active gives Hawaii Day (not a generic light theme), etc.
            val spec = if (baseSpec.isDark == wantDark) baseSpec
            else BuiltInThemes.counterpart(baseSpec)

            // Apply window-level settings as side effects, restoring on change.
            DisposableEffect(app.keepScreenOn, app.secureScreen, app.fullscreenMode) {
                if (app.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (app.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (app.fullscreenMode) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            BluePilotAppTheme(spec = spec) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalHapticIntensity provides app.hapticIntensity
                ) {
                    ThemedBackground {
                        BluePilotApp()
                    }
                }
            }
        }
    }
}
