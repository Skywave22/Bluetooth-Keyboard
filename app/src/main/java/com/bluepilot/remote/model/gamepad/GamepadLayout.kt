package com.bluepilot.remote.model.gamepad

import com.bluepilot.remote.model.widgets.WidgetFrame
import kotlinx.serialization.Serializable

/**
 * SECTION 2 — Custom gamepad builder models.
 *
 * A [GamepadLayoutSpec] is a user-designed controller: a set of controls,
 * each with position/size (fractional frame), shape, colors, label and a
 * HID binding. Bindings map DIRECTLY onto the HID gamepad report
 * (report ID 5): 16 buttons, 8-way hat, 4 axes — so anything built in the
 * editor is guaranteed to be expressible on the wire.
 *
 * All serializable; the JSON doubles as the .bpgamepad.json share format.
 */

@Serializable
enum class GamepadControlType {
    /** Momentary button → one of the 16 HID button bits. */
    BUTTON,
    /** Shoulder/trigger — same wire format as BUTTON, different default look. */
    TRIGGER,
    /** 4- or 8-way directional pad → HID hat switch. */
    DPAD,
    /** Analog stick → left (X/Y) or right (Z/Rz) axis pair. */
    STICK
}

@Serializable
enum class ControlShape { CIRCLE, ROUNDED, SQUARE }

@Serializable
enum class StickSide { LEFT, RIGHT }

/** Face-button naming convention (industry standards). */
@Serializable
enum class ButtonNaming { XBOX, PLAYSTATION }

/** Stick/trigger response curve. */
@Serializable
enum class ResponseCurve { LINEAR, EXPONENTIAL, AGGRESSIVE }

/** Haptic pattern assignable per control (Section 8 library). */
@Serializable
enum class HapticPattern { NONE, LIGHT_TAP, MEDIUM_CLICK, HEAVY_THUD, DOUBLE_PULSE, LONG_BUZZ }

@Serializable
data class GamepadControlSpec(
    val id: String,
    val type: GamepadControlType,
    val frame: WidgetFrame = WidgetFrame(),
    val shape: ControlShape = ControlShape.CIRCLE,
    /** ARGB fill color. */
    val color: Long = 0xFF2F6BFF,
    val opacity: Float = 0.9f,
    val label: String = "",
    /** HID button index 0..15 (BUTTON / TRIGGER). */
    val buttonIndex: Int = 0,
    /** Which axis pair a STICK drives. */
    val stickSide: StickSide = StickSide.LEFT,
    /** Radial dead zone percent 0..50 (STICK only). */
    val deadZone: Int = 10,
    /** DPAD: true = 8-way (diagonals), false = 4-way. */
    val eightWay: Boolean = false,
    /** Turbo/rapid-fire: auto-repeat while held (BUTTON/TRIGGER). */
    val turbo: Boolean = false,
    /** Turbo repeat rate in presses per second (2..20). */
    val turboRate: Int = 8,
    /** Response curve for STICK axes. */
    val curve: ResponseCurve = ResponseCurve.LINEAR,
    /** Haptic pattern played on press. */
    val haptic: HapticPattern = HapticPattern.LIGHT_TAP,
    /** Custom emoji/icon rendered above the label ("" = none). */
    val icon: String = ""
) {
    companion object {
        const val LABEL_MAX = 8
        const val BUTTON_INDEX_MAX = 15
    }

    fun sanitized(): GamepadControlSpec = copy(
        frame = frame.sanitized(),
        opacity = if (opacity.isNaN()) 0.9f else opacity.coerceIn(0.15f, 1f),
        label = label.take(LABEL_MAX),
        buttonIndex = buttonIndex.coerceIn(0, BUTTON_INDEX_MAX),
        deadZone = deadZone.coerceIn(0, 50),
        turboRate = turboRate.coerceIn(2, 20),
        icon = icon.take(4)
    )
}

@Serializable
data class GamepadLayoutSpec(
    val name: String = "Custom pad",
    val controls: List<GamepadControlSpec> = emptyList(),
    /** Face-button naming shown in the binding picker (Xbox/PlayStation). */
    val naming: ButtonNaming = ButtonNaming.XBOX,
    /** Stick sensitivity preset percent 0..100 (per-profile, e.g. FPS vs Racing). */
    val stickSensitivity: Int = 70
) {
    companion object {
        const val MAX_CONTROLS = 32
        const val NAME_MAX = 40
    }

    fun sanitized(): GamepadLayoutSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Gamepad" },
        controls = controls.take(MAX_CONTROLS).map { it.sanitized() },
        stickSensitivity = stickSensitivity.coerceIn(0, 100)
    )
}

/** Standard HID button-index names shown in the binding picker. */
object GamepadButtonNames {
    /** Xbox-style names: A/B/X/Y, LB/RB, LT/RT, View/Menu, L3/R3, Guide. */
    private val XBOX: List<String> = listOf(
        "A (1)", "B (2)", "X (3)", "Y (4)",
        "LB (5)", "RB (6)", "LT (7)", "RT (8)",
        "View (9)", "Menu (10)", "L3 (11)", "R3 (12)",
        "Guide (13)", "B14", "B15", "B16"
    )

    /** PlayStation-style names: Cross/Circle/Square/Triangle, L1/R1, L2/R2, Share/Options. */
    private val PLAYSTATION: List<String> = listOf(
        "Cross (1)", "Circle (2)", "Square (3)", "Triangle (4)",
        "L1 (5)", "R1 (6)", "L2 (7)", "R2 (8)",
        "Share (9)", "Options (10)", "L3 (11)", "R3 (12)",
        "PS (13)", "B14", "B15", "B16"
    )

    fun label(index: Int, naming: ButtonNaming = ButtonNaming.XBOX): String {
        val list = if (naming == ButtonNaming.PLAYSTATION) PLAYSTATION else XBOX
        return list.getOrElse(index.coerceIn(0, 15)) { "B${index + 1}" }
    }
}

/** Response-curve math (pure, unit-tested). Input/output -1..1 per axis. */
object ResponseCurves {
    fun apply(value: Float, curve: ResponseCurve): Float {
        val v = if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)
        val mag = kotlin.math.abs(v)
        val shaped = when (curve) {
            ResponseCurve.LINEAR -> mag
            // Slow start, fast finish (FPS aim style)
            ResponseCurve.EXPONENTIAL -> mag * mag
            // Even slower start, snappier end
            ResponseCurve.AGGRESSIVE -> mag * mag * mag
        }
        return if (v < 0) -shaped else shaped
    }
}
