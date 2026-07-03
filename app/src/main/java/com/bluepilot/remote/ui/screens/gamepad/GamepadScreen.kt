package com.bluepilot.remote.ui.screens.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.DpadDirection
import com.bluepilot.remote.model.GamepadButton
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.RemoteControlViewModel
import kotlin.math.roundToInt

/**
 * Gamepad: virtual left stick (drag), D-pad, ABXY, shoulder + menu buttons.
 * Behavior depends on the mapping mode in Settings (HID / keyboard / hybrid).
 * Gamepad state resets to neutral when leaving the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val gamepadSettings by viewModel.gamepadSettings.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)

    // Neutralize everything when this screen goes away.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetGamepad() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gamepad") },
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
                .padding(16.dp)
        ) {
            NotConnectedBanner(!isConnected)
            Text(
                text = "Mode: " + when (gamepadSettings.mappingMode) {
                    GamepadMappingMode.HID_GAMEPAD -> "HID gamepad"
                    GamepadMappingMode.KEYBOARD_FALLBACK -> "Keyboard fallback"
                    GamepadMappingMode.MOUSE_KEYBOARD -> "Mouse + keyboard"
                } + " (change in Settings)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // ---------- Left: stick + dpad ----------
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    VirtualStick(
                        onMove = { x, y -> viewModel.onStick(left = true, rawX = x, rawY = y) },
                        onRelease = { viewModel.onStick(left = true, rawX = 0f, rawY = 0f) }
                    )
                    DpadCluster(
                        onPress = { dir -> haptic(); viewModel.onDpad(dir) },
                        onRelease = { viewModel.onDpad(DpadDirection.NONE) }
                    )
                }

                // ---------- Right: ABXY + shoulders ----------
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FaceButton("L1") { pressed -> viewModel.onGamepadButton(GamepadButton.L1, pressed); if (pressed) haptic() }
                        FaceButton("R1") { pressed -> viewModel.onGamepadButton(GamepadButton.R1, pressed); if (pressed) haptic() }
                    }
                    // ABXY diamond
                    FaceButton("Y") { pressed -> viewModel.onGamepadButton(GamepadButton.Y, pressed); if (pressed) haptic() }
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        FaceButton("X") { pressed -> viewModel.onGamepadButton(GamepadButton.X, pressed); if (pressed) haptic() }
                        FaceButton("B") { pressed -> viewModel.onGamepadButton(GamepadButton.B, pressed); if (pressed) haptic() }
                    }
                    FaceButton("A", emphasized = true) { pressed -> viewModel.onGamepadButton(GamepadButton.A, pressed); if (pressed) haptic() }
                }
            }

            // ---------- Bottom: menu buttons ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KeyCard("Select", Modifier.weight(1f)) { haptic(); pressAndRelease(viewModel, GamepadButton.SELECT) }
                KeyCard("Home", Modifier.weight(1f)) { haptic(); pressAndRelease(viewModel, GamepadButton.HOME) }
                KeyCard("Start", Modifier.weight(1f)) { haptic(); pressAndRelease(viewModel, GamepadButton.START) }
            }
        }
    }
}

private fun pressAndRelease(viewModel: RemoteControlViewModel, button: GamepadButton) {
    viewModel.onGamepadButton(button, pressed = true)
    viewModel.onGamepadButton(button, pressed = false)
}

/**
 * Draggable virtual joystick. Knob follows the finger inside the base circle;
 * releasing snaps back to center and emits (0,0).
 */
@Composable
private fun VirtualStick(
    onMove: (Float, Float) -> Unit,
    onRelease: () -> Unit
) {
    val baseSize = 140.dp
    val knobSize = 52.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { (baseSize - knobSize).toPx() / 2f }
    var knob by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(baseSize)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { knob = Offset.Zero; onRelease() },
                    onDragCancel = { knob = Offset.Zero; onRelease() }
                ) { change, dragAmount ->
                    change.consume()
                    val next = knob + dragAmount
                    val clamped = if (next.getDistance() > radiusPx) {
                        next * (radiusPx / next.getDistance())
                    } else next
                    knob = clamped
                    onMove(
                        (clamped.x / radiusPx).coerceIn(-1f, 1f),
                        (clamped.y / radiusPx).coerceIn(-1f, 1f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(knobSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

/** Round press-and-release button reporting both press states. */
@Composable
private fun FaceButton(
    label: String,
    emphasized: Boolean = false,
    onPress: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed }) {
                            onPress(true)
                            // Wait for release
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            onPress(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** D-pad: 4 direction keys in a plus layout. */
@Composable
private fun DpadCluster(
    onPress: (DpadDirection) -> Unit,
    onRelease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DpadKey("▲") { pressed -> if (pressed) onPress(DpadDirection.UP) else onRelease() }
        Row(horizontalArrangement = Arrangement.spacedBy(46.dp)) {
            DpadKey("◀") { pressed -> if (pressed) onPress(DpadDirection.LEFT) else onRelease() }
            DpadKey("▶") { pressed -> if (pressed) onPress(DpadDirection.RIGHT) else onRelease() }
        }
        DpadKey("▼") { pressed -> if (pressed) onPress(DpadDirection.DOWN) else onRelease() }
    }
}

@Composable
private fun DpadKey(label: String, onPressChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed }) {
                            onPressChange(true)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            onPressChange(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}
