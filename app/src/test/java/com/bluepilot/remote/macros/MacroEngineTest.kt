package com.bluepilot.remote.macros

import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Macro expansion contract: steps → executable timed plan.
 * Pure function, no Android — invalid steps must be skipped, never fatal.
 */
class MacroEngineTest {

    @Test
    fun `key steps expand to key taps with modifiers`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(name = "m", steps = listOf(MacroStep.KeyTap(0x04, 0x05)))
        )
        assertEquals(1, plan.size)
        assertEquals(HidAction.KeyTap(0x04, 0x05), plan[0].action)
        assertEquals(MacroEngine.INTER_STEP_DELAY_MS, plan[0].delayMs)
    }

    @Test
    fun `delay steps expand to pure waits`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.Delay(700)))
        )
        assertEquals(1, plan.size)
        assertEquals(null, plan[0].action)
        assertEquals(700L, plan[0].delayMs)
    }

    @Test
    fun `delay is capped by sanitize`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.Delay(999_999)))
        )
        assertEquals(MacroSpec.DELAY_MAX_MS, plan[0].delayMs)
    }

    @Test
    fun `full sequence keeps order`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.KeyTap(0x28),          // Enter
                    MacroStep.Delay(100),
                    MacroStep.TypeText("hi"),
                    MacroStep.Media(0x00CD),
                    MacroStep.MouseClick(0x01)
                )
            )
        )
        assertEquals(5, plan.size)
        assertTrue(plan[0].action is HidAction.KeyTap)
        assertEquals(null, plan[1].action)
        assertEquals(HidAction.TypeText("hi"), plan[2].action)
        assertEquals(HidAction.MediaTap(0x00CD), plan[3].action)
        assertTrue(plan[4].action is HidAction.MouseClick)
    }

    @Test
    fun `invalid mouse mask and empty text are skipped`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.MouseClick(0x40),   // invalid mask
                    MacroStep.TypeText(""),       // empty
                    MacroStep.KeyTap(0x2C)        // valid Space
                )
            )
        )
        assertEquals(1, plan.size)
        assertEquals(HidAction.KeyTap(0x2C, 0), plan[0].action)
    }

    @Test
    fun `step count is capped`() {
        val many = (1..200).map { MacroStep.KeyTap(0x04) }
        val plan = MacroEngine.Companion.expand(MacroSpec(steps = many))
        assertEquals(MacroSpec.STEPS_MAX, plan.size)
    }

    @Test
    fun `text step is capped at TEXT_MAX`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.TypeText("x".repeat(2000))))
        )
        val action = plan[0].action as HidAction.TypeText
        assertEquals(MacroSpec.TEXT_MAX, action.text.length)
    }

    @Test
    fun `macro spec sanitize fixes blank name`() {
        assertEquals("Macro", MacroSpec(name = "   ").sanitized().name)
        assertEquals(MacroSpec.NAME_MAX, MacroSpec(name = "y".repeat(99)).sanitized().name.length)
    }
}
