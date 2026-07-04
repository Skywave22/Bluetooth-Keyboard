package com.bluepilot.remote.domain

import com.bluepilot.remote.hid.PointerMath
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.gamepad.StickSide
import kotlin.math.abs
import kotlin.math.atan2

/**
 * SECTION 2 — Pure gamepad input state machine.
 *
 * Translates control events from the custom gamepad renderer into
 * [GamepadSnapshot] updates. No Android imports — fully unit-tested.
 * Multi-touch correctness comes from this being a pure fold over events:
 * each control updates only its own bits/axes, so simultaneous stick moves
 * and button presses never clobber each other.
 */
object GamepadRuntimeCore {

    /** Press/release one of the 16 HID buttons. Invalid index = no-op. */
    fun withButton(snapshot: GamepadSnapshot, index: Int, pressed: Boolean): GamepadSnapshot {
        if (index !in 0..15) return snapshot
        val mask = 1 shl index
        val buttons = if (pressed) snapshot.buttons or mask else snapshot.buttons and mask.inv()
        return snapshot.copy(buttons = buttons)
    }

    /** Move a stick with its dead zone applied; NaN-safe, clamped. */
    fun withStick(
        snapshot: GamepadSnapshot,
        side: StickSide,
        rawX: Float,
        rawY: Float,
        deadZonePercent: Int
    ): GamepadSnapshot {
        val sx = if (rawX.isNaN()) 0f else rawX.coerceIn(-1f, 1f)
        val sy = if (rawY.isNaN()) 0f else rawY.coerceIn(-1f, 1f)
        val (x, y) = PointerMath.applyDeadZone(sx, sy, deadZonePercent)
        return when (side) {
            StickSide.LEFT -> snapshot.copy(leftX = x, leftY = y)
            StickSide.RIGHT -> snapshot.copy(rightX = x, rightY = y)
        }
    }

    /**
     * D-pad from a touch position relative to the pad center (-1..1 each axis).
     * 4-way: dominant axis wins. 8-way: full 8 directions by angle.
     * Returns hat value 0..7, or 8 (neutral) when inside the center zone.
     */
    fun hatFromTouch(dx: Float, dy: Float, eightWay: Boolean): Int {
        val dead = 0.25f
        if (abs(dx) < dead && abs(dy) < dead) return 8 // neutral center

        if (!eightWay) {
            // Dominant axis → 4 directions (hat: 0=N, 2=E, 4=S, 6=W).
            return if (abs(dx) >= abs(dy)) {
                if (dx > 0) 2 else 6
            } else {
                if (dy > 0) 4 else 0
            }
        }

        // 8-way: angle → one of 8 sectors. atan2 with screen-y (down = positive).
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) // -180..180, 0 = east
        val sector = (((angle + 360 + 22.5) % 360) / 45.0).toInt() % 8
        // sector 0=E,1=SE,2=S,3=SW,4=W,5=NW,6=N,7=NE → map to hat 0=N..7=NW
        return when (sector) {
            0 -> 2; 1 -> 3; 2 -> 4; 3 -> 5
            4 -> 6; 5 -> 7; 6 -> 0; else -> 1
        }
    }

    fun withHat(snapshot: GamepadSnapshot, hat: Int): GamepadSnapshot =
        snapshot.copy(hat = hat.coerceIn(0, 8))
}
