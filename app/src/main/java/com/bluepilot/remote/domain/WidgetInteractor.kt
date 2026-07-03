package com.bluepilot.remote.domain

import com.bluepilot.remote.hid.PointerMath
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType

/**
 * Shared "use mode" behavior for data-driven widgets.
 *
 * Both the Layouts player and the editor's Preview mode delegate here, so
 * widget behavior is identical everywhere and tested once. Holds per-gesture
 * trackpad smoothing state; create one instance per screen.
 *
 * [runMacro] is invoked for WidgetAction.RunMacro bindings (macro engine).
 */
class WidgetInteractor(
    private val send: (HidAction) -> Unit,
    private val mouseSettings: () -> MouseSettings,
    private val runMacro: (Long) -> Unit = {}
) {

    fun primary(widget: WidgetSpec) {
        val action = widget.action
        if (action is WidgetAction.RunMacro) {
            runMacro(action.macroId)
            return
        }
        WidgetActionMapper.toHidAction(action)?.let(send)
    }

    fun secondary(widget: WidgetSpec) {
        val action = widget.secondaryAction
        if (action is WidgetAction.RunMacro) {
            runMacro(action.macroId)
            return
        }
        val bound = WidgetActionMapper.toHidAction(action)
        when {
            bound != null -> send(bound)
            // Sensible default: long-press on a trackpad = right click.
            widget.type == WidgetType.TRACKPAD -> send(HidAction.MouseClick(MouseButton.RIGHT))
        }
    }

    // ------------------------------------------------------------------
    // Trackpad (same math as the Mouse screen)
    // ------------------------------------------------------------------

    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f
    private var scrollAccum = 0f

    fun trackpadStart() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
    }

    fun trackpadDelta(dx: Float, dy: Float) {
        val s = mouseSettings()
        val g = PointerMath.gain(s.sensitivity, s.penMode)
        smoothX = PointerMath.smooth(smoothX, dx * g, s.movementSmoothing)
        smoothY = PointerMath.smooth(smoothY, dy * g, s.movementSmoothing)
        val fx = smoothX + fracX
        val fy = smoothY + fracY
        val ix = fx.toInt(); val iy = fy.toInt()
        fracX = fx - ix; fracY = fy - iy
        if (ix != 0 || iy != 0) send(HidAction.MouseMove(ix, iy))
    }

    fun scrollDelta(dy: Float) {
        val s = mouseSettings()
        scrollAccum += dy
        val (steps, remainder) = PointerMath.scrollSteps(scrollAccum, s.scrollSpeed, s.invertScroll)
        scrollAccum = remainder
        if (steps != 0) send(HidAction.MouseScroll(steps))
    }

    /** Joystick in custom layouts drives the mouse pointer. */
    fun joystick(x: Float, y: Float) {
        val speed = 14f
        val dx = (x * speed).toInt(); val dy = (y * speed).toInt()
        if (dx != 0 || dy != 0) send(HidAction.MouseMove(dx, dy))
    }

    fun dpad(dirX: Int, dirY: Int) {
        when {
            dirY < 0 -> send(HidAction.KeyTap(HidKeys.ARROW_UP))
            dirY > 0 -> send(HidAction.KeyTap(HidKeys.ARROW_DOWN))
            dirX < 0 -> send(HidAction.KeyTap(HidKeys.ARROW_LEFT))
            dirX > 0 -> send(HidAction.KeyTap(HidKeys.ARROW_RIGHT))
        }
    }
}
