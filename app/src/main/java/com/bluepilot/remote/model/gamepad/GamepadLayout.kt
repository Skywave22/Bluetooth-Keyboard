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
    val eightWay: Boolean = false
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
        deadZone = deadZone.coerceIn(0, 50)
    )
}

@Serializable
data class GamepadLayoutSpec(
    val name: String = "Custom pad",
    val controls: List<GamepadControlSpec> = emptyList()
) {
    companion object {
        const val MAX_CONTROLS = 32
        const val NAME_MAX = 40
    }

    fun sanitized(): GamepadLayoutSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Gamepad" },
        controls = controls.take(MAX_CONTROLS).map { it.sanitized() }
    )
}

/** Standard HID button-index names shown in the binding picker. */
object GamepadButtonNames {
    val NAMES: List<String> = listOf(
        "A (1)", "B (2)", "X (3)", "Y (4)",
        "L1 (5)", "R1 (6)", "L2 (7)", "R2 (8)",
        "Select (9)", "Start (10)", "LS (11)", "RS (12)",
        "Home (13)", "B14", "B15", "B16"
    )

    fun label(index: Int): String = NAMES.getOrElse(index.coerceIn(0, 15)) { "B${index + 1}" }
}
