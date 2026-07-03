package com.bluepilot.remote.model.macros

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Macro system models.
 *
 * A macro is a named ordered list of [MacroStep]s. Steps are serializable
 * (stored as JSON in the Room `macros` table) and the macro engine expands
 * them into HidActions at playback time.
 *
 * Design rules:
 *  - Every step type is explicit — no "raw bytes" escape hatch that could
 *    crash a host.
 *  - All values validated by sanitized(): delays capped, text capped,
 *    step count capped.
 */

@Serializable
sealed class MacroStep {

    /** Press + release one key with optional modifiers (chords like Ctrl+Alt+Del). */
    @Serializable
    @SerialName("key")
    data class KeyTap(val key: Byte, val modifiers: Byte = 0) : MacroStep()

    /** Type a text string. */
    @Serializable
    @SerialName("text")
    data class TypeText(val text: String) : MacroStep()

    /** Media/consumer control tap. */
    @Serializable
    @SerialName("media")
    data class Media(val usage: Int) : MacroStep()

    /** Mouse click (HID mask: 1=left 2=right 4=middle). */
    @Serializable
    @SerialName("mouse")
    data class MouseClick(val buttonMask: Int) : MacroStep()

    /** Wait between steps. */
    @Serializable
    @SerialName("delay")
    data class Delay(val ms: Long) : MacroStep()

    fun sanitized(): MacroStep = when (this) {
        is TypeText -> copy(text = text.take(MacroSpec.TEXT_MAX))
        is Delay -> copy(ms = ms.coerceIn(0, MacroSpec.DELAY_MAX_MS))
        else -> this
    }
}

@Serializable
data class MacroSpec(
    val name: String = "New macro",
    val steps: List<MacroStep> = emptyList()
) {
    companion object {
        const val STEPS_MAX = 64
        const val TEXT_MAX = 500
        const val DELAY_MAX_MS = 5_000L
        const val NAME_MAX = 40
    }

    fun sanitized(): MacroSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Macro" },
        steps = steps.take(STEPS_MAX).map { it.sanitized() }
    )
}
