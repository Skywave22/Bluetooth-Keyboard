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
import com.bluepilot.remote.ui.navigation.BluePilotApp
import com.bluepilot.remote.ui.theme.BluePilotTheme
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

            val darkTheme = when (app.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

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

            BluePilotTheme(darkTheme = darkTheme) {
                BluePilotApp()
            }
        }
    }
}
