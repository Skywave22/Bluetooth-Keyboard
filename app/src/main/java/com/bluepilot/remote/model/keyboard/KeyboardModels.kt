package com.bluepilot.remote.model.keyboard

import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import kotlinx.serialization.Serializable

/**
 * SECTION: Full Keyboard + individual key customization.
 *
 * Every key is DATA: label, HID binding (keycode + modifiers — so a single
 * key can be a combo like Ctrl+C), width weight, optional color override,
 * and an optional secondary (long-press) binding. The whole board is a
 * serializable [KeyboardLayoutSpec]; user edits persist as JSON.
 */
@Serializable
data class KeySpec(
    val id: String,
    val label: String,
    val keyCode: Byte,
    val modifiers: Byte = 0,
    /** Row width weight: 1.0 = standard key. Resizable per key. */
    val widthWeight: Float = 1f,
    /** ARGB override; null = themed default. */
    val colorArgb: Long? = null,
    /** Optional long-press binding (secondary function). */
    val secondaryKeyCode: Byte? = null,
    val secondaryModifiers: Byte = 0,
    val secondaryLabel: String = ""
) {
    companion object {
        const val LABEL_MAX = 10
        const val WEIGHT_MIN = 0.5f
        const val WEIGHT_MAX = 3f
    }

    fun sanitized(): KeySpec = copy(
        label = label.take(LABEL_MAX).ifBlank { "?" },
        widthWeight = if (widthWeight.isNaN()) 1f else widthWeight.coerceIn(WEIGHT_MIN, WEIGHT_MAX),
        secondaryLabel = secondaryLabel.take(LABEL_MAX)
    )
}

@Serializable
data class KeyboardLayoutSpec(
    /** Full board: rows of keys top-to-bottom. */
    val rows: List<List<KeySpec>> = emptyList(),
    /** Which row indices are hidden in COMPACT mode. */
    val compactHiddenRows: List<Int> = emptyList(),
    /** User's favorite custom keys (combo shortcuts row). */
    val favorites: List<KeySpec> = emptyList()
) {
    companion object {
        const val FAVORITES_MAX = 12
        const val KEYS_PER_ROW_MAX = 20
    }

    fun sanitized(): KeyboardLayoutSpec = copy(
        rows = rows.map { row -> row.take(KEYS_PER_ROW_MAX).map { it.sanitized() } },
        favorites = favorites.take(FAVORITES_MAX).map { it.sanitized() }
    )

    fun findKey(id: String): KeySpec? =
        rows.asSequence().flatten().firstOrNull { it.id == id }
}

/** Factory for the built-in boards. */
object DefaultKeyboards {

    private fun k(
        id: String, label: String, code: Byte, mods: Byte = 0, w: Float = 1f
    ) = KeySpec(id = id, label = label, keyCode = code, modifiers = mods, widthWeight = w)

    /**
     * Full PC board: F-row, number row, QWERTY, modifiers, nav cluster + arrows.
     * Row indices 0 (F-row), 6 (nav) and 7 (arrows) hide in compact mode.
     */
    fun fullQwerty(): KeyboardLayoutSpec = KeyboardLayoutSpec(
        rows = listOf(
            // 0: Esc + F1-F12
            listOf(
                k("esc", "Esc", HidKeys.ESCAPE, w = 1.4f),
                k("f1", "F1", HidKeys.F1), k("f2", "F2", HidKeys.F2),
                k("f3", "F3", HidKeys.F3), k("f4", "F4", HidKeys.F4),
                k("f5", "F5", HidKeys.F5), k("f6", "F6", HidKeys.F6),
                k("f7", "F7", HidKeys.F7), k("f8", "F8", HidKeys.F8),
                k("f9", "F9", HidKeys.F9), k("f10", "F10", HidKeys.F10),
                k("f11", "F11", HidKeys.F11), k("f12", "F12", HidKeys.F12)
            ),
            // 1: number row
            listOf(
                k("grave", "`", HidKeys.GRAVE),
                k("1", "1", HidKeys.NUM_1), k("2", "2", HidKeys.NUM_2),
                k("3", "3", HidKeys.NUM_3), k("4", "4", HidKeys.NUM_4),
                k("5", "5", HidKeys.NUM_5), k("6", "6", HidKeys.NUM_6),
                k("7", "7", HidKeys.NUM_7), k("8", "8", HidKeys.NUM_8),
                k("9", "9", HidKeys.NUM_9), k("0", "0", HidKeys.NUM_0),
                k("minus", "-", HidKeys.MINUS), k("equal", "=", HidKeys.EQUAL),
                k("bksp", "⌫", HidKeys.BACKSPACE, w = 1.8f)
            ),
            // 2: QWERTY
            listOf(
                k("tab", "Tab", HidKeys.TAB, w = 1.5f),
                k("q", "Q", HidKeys.Q), k("w", "W", HidKeys.W), k("e", "E", HidKeys.E),
                k("r", "R", HidKeys.R), k("t", "T", HidKeys.T), k("y", "Y", HidKeys.Y),
                k("u", "U", HidKeys.U), k("i", "I", HidKeys.I), k("o", "O", HidKeys.O),
                k("p", "P", HidKeys.P),
                k("lbr", "[", HidKeys.LEFT_BRACKET), k("rbr", "]", HidKeys.RIGHT_BRACKET),
                k("bslash", "\\", HidKeys.BACKSLASH, w = 1.3f)
            ),
            // 3: home row
            listOf(
                k("caps", "Caps", HidKeys.CAPS_LOCK, w = 1.8f),
                k("a", "A", HidKeys.A), k("s", "S", HidKeys.S), k("d", "D", HidKeys.D),
                k("f", "F", HidKeys.F), k("g", "G", HidKeys.G), k("h", "H", HidKeys.H),
                k("j", "J", HidKeys.J), k("k", "K", HidKeys.K), k("l", "L", HidKeys.L),
                k("semi", ";", HidKeys.SEMICOLON), k("quote", "'", HidKeys.QUOTE),
                k("enter", "Enter ⏎", HidKeys.ENTER, w = 2.1f)
            ),
            // 4: shift row
            listOf(
                KeySpec("lshift", "Shift", HidKeys.NONE, HidModifiers.LEFT_SHIFT, widthWeight = 2.3f),
                k("z", "Z", HidKeys.Z), k("x", "X", HidKeys.X), k("c", "C", HidKeys.C),
                k("v", "V", HidKeys.V), k("b", "B", HidKeys.B), k("n", "N", HidKeys.N),
                k("m", "M", HidKeys.M),
                k("comma", ",", HidKeys.COMMA), k("period", ".", HidKeys.PERIOD),
                k("slash", "/", HidKeys.SLASH),
                KeySpec("rshift", "Shift", HidKeys.NONE, HidModifiers.RIGHT_SHIFT, widthWeight = 2.3f)
            ),
            // 5: bottom modifiers
            listOf(
                KeySpec("lctrl", "Ctrl", HidKeys.NONE, HidModifiers.LEFT_CTRL, widthWeight = 1.5f),
                KeySpec("lgui", "Win", HidKeys.NONE, HidModifiers.LEFT_GUI, widthWeight = 1.3f),
                KeySpec("lalt", "Alt", HidKeys.NONE, HidModifiers.LEFT_ALT, widthWeight = 1.3f),
                k("space", "Space", HidKeys.SPACE, w = 6f),
                KeySpec("ralt", "Alt", HidKeys.NONE, HidModifiers.RIGHT_ALT, widthWeight = 1.3f),
                k("menu", "☰", HidKeys.APPLICATION),
                KeySpec("rctrl", "Ctrl", HidKeys.NONE, HidModifiers.RIGHT_CTRL, widthWeight = 1.5f)
            ),
            // 6: nav cluster
            listOf(
                k("prtsc", "PrtSc", HidKeys.PRINT_SCREEN),
                k("ins", "Ins", HidKeys.INSERT), k("del", "Del", HidKeys.DELETE),
                k("home", "Home", HidKeys.HOME), k("end", "End", HidKeys.END),
                k("pgup", "PgUp", HidKeys.PAGE_UP), k("pgdn", "PgDn", HidKeys.PAGE_DOWN)
            ),
            // 7: arrows
            listOf(
                k("left", "←", HidKeys.ARROW_LEFT, w = 2f),
                k("up", "↑", HidKeys.ARROW_UP, w = 2f),
                k("down", "↓", HidKeys.ARROW_DOWN, w = 2f),
                k("right", "→", HidKeys.ARROW_RIGHT, w = 2f)
            )
        ),
        compactHiddenRows = listOf(0, 6, 7),
        favorites = listOf(
            KeySpec("fav-copy", "Copy", HidKeys.C, HidModifiers.LEFT_CTRL),
            KeySpec("fav-paste", "Paste", HidKeys.V, HidModifiers.LEFT_CTRL),
            KeySpec("fav-undo", "Undo", HidKeys.Z, HidModifiers.LEFT_CTRL)
        )
    )
}
