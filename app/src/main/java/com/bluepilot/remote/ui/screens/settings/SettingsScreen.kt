package com.bluepilot.remote.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.model.ThemeMode
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.theme.LocalAppTheme
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
    val spec = LocalAppTheme.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "SETTINGS" else "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                OutlinedButton(
                    onClick = onOpenThemes,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = spec.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = spec.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (spec.monoFont) "OPEN THEME GALLERY" else "Open theme gallery",
                        style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (spec.monoFont) "THEME" else "Theme",
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = app.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = {
                                Text(
                                    text = if (spec.monoFont) label.uppercase() else label,
                                    style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                    else MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                ToggleRow("Fullscreen mode", app.fullscreenMode, viewModel::setFullscreen)
                ToggleRow("Keep screen on", app.keepScreenOn, viewModel::setKeepScreenOn)
                ToggleRow("Touch vibrations", app.touchVibrations, viewModel::setTouchVibrations)
                if (app.touchVibrations) {
                    Text(
                        text = if (spec.monoFont) "VIBRATION STRENGTH" else "Vibration strength",
                        style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        HapticIntensity.entries.forEach { level ->
                            val label = level.name.lowercase().replaceFirstChar { it.uppercase() }
                            FilterChip(
                                selected = app.hapticIntensity == level,
                                onClick = { viewModel.setHapticIntensity(level) },
                                label = {
                                    Text(
                                        text = if (spec.monoFont) label.uppercase() else label,
                                        style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                        else MaterialTheme.typography.labelLarge
                                    )
                                },
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
                Text(
                    text = if (spec.monoFont) "MODE" else "Mode",
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    GamepadMappingMode.entries.forEach { mode ->
                        val label = gamepadModeLabel(mode)
                        FilterChip(
                            selected = gamepad.mappingMode == mode,
                            onClick = { viewModel.setGamepadMode(mode) },
                            label = {
                                Text(
                                    text = if (spec.monoFont) label.uppercase() else label,
                                    style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                    else MaterialTheme.typography.labelLarge
                                )
                            },
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
    val spec = LocalAppTheme.current
    Text(
        text = if (spec.monoFont) title.uppercase() else title,
        style = if (spec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
        else MaterialTheme.typography.titleMedium,
        color = spec.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    GlassCard {
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
    val spec = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (spec.monoFont) title.uppercase() else title,
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = if (spec.monoFont) subtitle.uppercase() else subtitle,
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = spec.primary,
                checkedTrackColor = spec.primary.copy(alpha = 0.35f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    max: Int = 100
) {
    val spec = LocalAppTheme.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (spec.monoFont) title.uppercase() else title,
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value",
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = spec.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = spec.primary,
                activeTrackColor = spec.primary,
                inactiveTrackColor = spec.outline.copy(alpha = 0.3f)
            )
        )
    }
}
