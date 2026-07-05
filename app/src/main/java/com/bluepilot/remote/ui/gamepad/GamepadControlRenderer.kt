package com.bluepilot.remote.ui.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.gamepad.ControlShape
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import kotlin.math.roundToInt

/**
 * SECTION 2 — Runtime renderer for custom gamepad layouts.
 *
 * Multi-touch: every control has its OWN pointerInput scope, so Compose
 * routes each finger to the control under it independently — moving the
 * stick while hammering buttons works out of the box.
 *
 * Press semantics for buttons/triggers: awaitPointerEventScope tracks
 * press → release precisely (no missed inputs, no double-fires: one
 * transition per physical press).
 */
interface GamepadEvents {
    fun onButton(control: GamepadControlSpec, pressed: Boolean)
    fun onStick(control: GamepadControlSpec, x: Float, y: Float)
    /** dx/dy relative to pad center, -1..1; NaN never sent. */
    fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float)
    fun onDpadRelease(control: GamepadControlSpec)
}

/** Renders a full custom gamepad layout at fractional positions. */
@Composable
fun GamepadCanvas(
    layout: GamepadLayoutSpec,
    events: GamepadEvents,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        layout.controls.forEach { control ->
            val frame = control.frame.sanitized()
            RenderGamepadControl(
                control = control,
                events = events,
                modifier = Modifier
                    .offset(x = w * frame.x, y = h * frame.y)
                    .size(width = w * frame.w, height = h * frame.h)
            )
        }
    }
}

@Composable
fun RenderGamepadControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier = Modifier
) {
    when (control.type) {
        GamepadControlType.BUTTON, GamepadControlType.TRIGGER ->
            PressableControl(control, events, modifier)
        GamepadControlType.STICK -> StickControl(control, events, modifier)
        GamepadControlType.DPAD -> DpadControl(control, events, modifier)
    }
}

// ----------------------------------------------------------------------

internal fun controlShape(shape: ControlShape): Shape = when (shape) {
    ControlShape.CIRCLE -> CircleShape
    ControlShape.ROUNDED -> RoundedCornerShape(18.dp)
    ControlShape.SQUARE -> RoundedCornerShape(4.dp)
}

internal fun controlColor(control: GamepadControlSpec): Color =
    Color(control.color.toULong().toLong() and 0xFFFFFFFF)
        .copy(alpha = control.opacity.coerceIn(0.15f, 1f))

/** Button/trigger: exact press & release tracking, visual pressed state. */
@Composable
private fun PressableControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = controlShape(control.shape)
    val base = controlColor(control)
    // SECTION 3D — raised button look: sits proud (slight highlight), and on
    // press sinks + tips back. State-driven graphicsLayer: applied on the
    // already-tracked `pressed` flag, so the input path gains ZERO latency.
    val reduceMotion = com.bluepilot.remote.ui.components.LocalReduceMotion.current

    Box(
        modifier = modifier
            .graphicsLayer {
                // Sink + tip-back on press; collapses to plain scale under
                // Reduce Motion. Pure GPU transform, no latency added.
                if (pressed) {
                    if (reduceMotion) {
                        scaleX = 0.96f; scaleY = 0.96f
                    } else {
                        cameraDistance = 8f * density
                        rotationX = -5f
                        scaleX = 0.94f; scaleY = 0.94f
                        translationY = size.height * 0.02f
                    }
                }
            }
            .background(if (pressed) base.copy(alpha = 1f) else base, shape)
            .pointerInput(control.id) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed } && !pressed) {
                            pressed = true
                            events.onButton(control, true)
                            // Hold until every pointer on this control lifts.
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            pressed = false
                            events.onButton(control, false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (control.label.isNotBlank()) {
            Text(
                text = control.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

/** Analog stick: knob follows finger inside base circle; snaps to center. */
@Composable
private fun StickControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val baseMin = if (maxWidth < maxHeight) maxWidth else maxHeight
        val knobSize = baseMin * 0.42f
        val density = LocalDensity.current
        val radiusPx = with(density) { ((baseMin - knobSize) / 2f).toPx() }.coerceAtLeast(1f)
        var knob by remember { mutableStateOf(Offset.Zero) }
        val base = controlColor(control)

        Box(
            modifier = Modifier
                .size(baseMin)
                .background(base.copy(alpha = base.alpha * 0.4f), CircleShape)
                .pointerInput(control.id) {
                    detectDragGestures(
                        onDragEnd = { knob = Offset.Zero; events.onStick(control, 0f, 0f) },
                        onDragCancel = { knob = Offset.Zero; events.onStick(control, 0f, 0f) }
                    ) { change, drag ->
                        change.consume()
                        val next = knob + drag
                        val dist = next.getDistance()
                        val clamped = if (dist > radiusPx) next * (radiusPx / dist) else next
                        knob = clamped
                        events.onStick(
                            control,
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
                    .background(base.copy(alpha = 1f), CircleShape)
            )
        }
    }
}

/** D-pad: touch position → direction; release → neutral. */
@Composable
private fun DpadControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val base = controlColor(control)
        val sizePx = with(LocalDensity.current) {
            (if (maxWidth < maxHeight) maxWidth else maxHeight).toPx()
        }.coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(base, RoundedCornerShape(20.dp))
                .pointerInput(control.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                val dx = ((change.position.x - size.width / 2f) / (sizePx / 2f)).coerceIn(-1f, 1f)
                                val dy = ((change.position.y - size.height / 2f) / (sizePx / 2f)).coerceIn(-1f, 1f)
                                events.onDpadTouch(control, dx, dy)
                            } else {
                                events.onDpadRelease(control)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (control.eightWay) "✚" else "✛",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
