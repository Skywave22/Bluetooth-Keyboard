package com.bluepilot.remote.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.model.ThemeMode
import com.bluepilot.remote.viewmodel.SettingsViewModel

/**
 * Full settings screen: General / Mouse / Keyboard / Gamepad groups.
 * Every control writes through the sanitizing SettingsStore immediately
 * (no save button needed) and UI state comes straight back from DataStore,
 * so what you see is always what is persisted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenThemes: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsState()
    val mouse by viewModel.mouse.collectAsState()
    val keyboard by viewModel.keyboard.collectAsState()
    val gamepad by viewModel.gamepad.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ---------- General ----------
            SettingsGroup("General") {
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenThemes,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open theme gallery") }
                Spacer(Modifier.height(8.dp))
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = app.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                ToggleRow("Fullscreen mode", app.fullscreenMode, viewModel::setFullscreen)
                ToggleRow("Keep screen on", app.keepScreenOn, viewModel::setKeepScreenOn)
                ToggleRow("Touch vibrations", app.touchVibrations, viewModel::setTouchVibrations)
                if (app.touchVibrations) {
                    Text("Vibration strength", style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        HapticIntensity.entries.forEach { level ->
                            FilterChip(
                                selected = app.hapticIntensity == level,
                                onClick = { viewModel.setHapticIntensity(level) },
                                label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
                ToggleRow(
                    "Secure screen",
                    app.secureScreen,
                    viewModel::setSecureScreen,
                    subtitle = "Blocks screenshots and app-switcher preview"
                )
            }

            // ---------- Mouse ----------
            SettingsGroup("Mouse & trackpad") {
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)
                SliderRow("Scroll speed", mouse.scrollSpeed, viewModel::setScrollSpeed)
                SliderRow("Movement smoothing", mouse.movementSmoothing, viewModel::setMovementSmoothing)
                ToggleRow("Invert scroll", mouse.invertScroll, viewModel::setInvertScroll)
                ToggleRow("Tap to click", mouse.tapToClick, viewModel::setTapToClick)
                ToggleRow("Pen mode", mouse.penMode, viewModel::setPenMode, subtitle = "Slower, precise pointer")
            }

            // ---------- Keyboard ----------
            SettingsGroup("Keyboard") {
                ToggleRow("Show text input bar", keyboard.showTextInputBar, viewModel::setShowTextInputBar)
            }

            // ---------- Gamepad ----------
            SettingsGroup("Gamepad") {
                Text("Mode", style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    GamepadMappingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = gamepad.mappingMode == mode,
                            onClick = { viewModel.setGamepadMode(mode) },
                            label = { Text(gamepadModeLabel(mode)) },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                SliderRow("Joystick sensitivity", gamepad.joystickSensitivity, viewModel::setJoystickSensitivity)
                SliderRow(
                    "Dead zone", gamepad.deadZone, viewModel::setDeadZone,
                    max = com.bluepilot.remote.model.GamepadSettings.DEAD_ZONE_MAX
                )
                ToggleRow("Haptic feedback", gamepad.hapticFeedback, viewModel::setHapticFeedback)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun gamepadModeLabel(mode: GamepadMappingMode): String = when (mode) {
    GamepadMappingMode.HID_GAMEPAD -> "HID gamepad (real controller)"
    GamepadMappingMode.KEYBOARD_FALLBACK -> "Keyboard fallback (WASD/keys)"
    GamepadMappingMode.MOUSE_KEYBOARD -> "Mouse + keyboard hybrid"
}

// ----------------------------------------------------------------------
// Reusable rows
// ----------------------------------------------------------------------

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    max: Int = 100
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "$value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat()
        )
    }
}
