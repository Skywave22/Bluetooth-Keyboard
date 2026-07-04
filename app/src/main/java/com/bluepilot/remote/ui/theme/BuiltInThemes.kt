package com.bluepilot.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The built-in theme catalog — each one is a direct translation of a
 * design mockup from the redesign phase (designs/ folder).
 */
object BuiltInThemes {

    /** Classic BluePilot look (the pre-update default). */
    val PILOT_DARK = AppThemeSpec(
        id = "pilot_dark",
        name = "Pilot Dark",
        isDark = true,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF0B0F1A), onBackground = Color(0xFFE6EAF5),
        surface = Color(0xFF121828), onSurface = Color(0xFFE6EAF5),
        surfaceVariant = Color(0xFF1A2238), onSurfaceVariant = Color(0xFFAAB4CE),
        outline = Color(0xFF2C3A5C),
        cornerRadius = 16, elevation = 2
    )

    /** designs/glass-*: frosted panels over aurora orbs, luminous edges. */
    val LIQUID_GLASS = AppThemeSpec(
        id = "liquid_glass",
        name = "Liquid Glass",
        isDark = true,
        primary = Color(0xFF4D8DFF), onPrimary = Color.White,
        secondary = Color(0xFF7AE7FF),
        background = Color(0xFF0A0E1C), onBackground = Color(0xFFEDF2FF),
        surface = Color(0xFF18203A), onSurface = Color(0xFFEDF2FF),
        surfaceVariant = Color(0xFF222C4E), onSurfaceVariant = Color(0xFFB6C2E4),
        outline = Color(0xFF3A4A7E),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF3B5BFF), 0.15f, 0.10f, 0.45f, 0.40f),
            ThemeOrb(Color(0xFF8A4DFF), 0.90f, 0.35f, 0.40f, 0.30f),
            ThemeOrb(Color(0xFF29C5FF), 0.30f, 0.90f, 0.50f, 0.28f)
        ),
        cornerRadius = 24, surfaceAlpha = 0.72f, edgeGlow = true, elevation = 0
    )

    /** designs/hi-* dark: Tropical Night — moonlit lagoon + tiki lantern glow. */
    val HAWAII_NIGHT = AppThemeSpec(
        id = "hawaii_night",
        name = "Hawaii Night",
        isDark = true,
        primary = Color(0xFF00B4C6), onPrimary = Color.White,
        secondary = Color(0xFFFF6B5E),
        background = Color(0xFF06222B), onBackground = Color(0xFFE2F6F8),
        surface = Color(0xFF0C3340), onSurface = Color(0xFFE2F6F8),
        surfaceVariant = Color(0xFF14424F), onSurfaceVariant = Color(0xFF9CCBD4),
        outline = Color(0xFF20596B),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF00B4C6), 0.20f, 0.15f, 0.45f, 0.35f),
            ThemeOrb(Color(0xFFFF6B5E), 0.88f, 0.80f, 0.35f, 0.30f),
            ThemeOrb(Color(0xFF3EB489), 0.75f, 0.20f, 0.30f, 0.22f)
        ),
        cornerRadius = 26, surfaceAlpha = 0.78f, edgeGlow = true, elevation = 0,
        connected = Color(0xFF3EB489)
    )

    /** designs/hi-* light: Island Day — sun-washed lagoon + sand. */
    val HAWAII_DAY = AppThemeSpec(
        id = "hawaii_day",
        name = "Hawaii Day",
        isDark = false,
        primary = Color(0xFF00A0B4), onPrimary = Color.White,
        secondary = Color(0xFFFF6B5E),
        background = Color(0xFFF2FBFC), onBackground = Color(0xFF0E3A44),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E3A44),
        surfaceVariant = Color(0xFFD9F2F4), onSurfaceVariant = Color(0xFF3C6E79),
        outline = Color(0xFFA5D6DD),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF4DD9E8), 0.18f, 0.12f, 0.45f, 0.35f),
            ThemeOrb(Color(0xFFFFB347), 0.90f, 0.30f, 0.32f, 0.28f),
            ThemeOrb(Color(0xFFF2E3C6), 0.55f, 0.92f, 0.50f, 0.45f)
        ),
        cornerRadius = 26, surfaceAlpha = 0.85f, edgeGlow = true, elevation = 0
    )

    /** designs/hud-* dark: Night Flight — carbon + HUD green, mono readouts. */
    val COCKPIT_HUD = AppThemeSpec(
        id = "cockpit_hud",
        name = "Cockpit HUD",
        isDark = true,
        primary = Color(0xFF00FF9C), onPrimary = Color.Black,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF0A0C10), onBackground = Color(0xFFC8FFE9),
        surface = Color(0xFF10141C), onSurface = Color(0xFFC8FFE9),
        surfaceVariant = Color(0xFF161D28), onSurfaceVariant = Color(0xFF6FBF9E),
        outline = Color(0xFF1F4A38),
        cornerRadius = 8, surfaceAlpha = 1f, edgeGlow = true, elevation = 0,
        glowColor = Color(0xFF00FF9C),
        connected = Color(0xFF00FF9C), connecting = Color(0xFFFFB300),
        monoFont = true
    )

    /** Gaming RGB energy: magenta/cyan neon on near-black. */
    val DARK_NEON = AppThemeSpec(
        id = "dark_neon",
        name = "Dark Neon",
        isDark = true,
        primary = Color(0xFFFF2D95), onPrimary = Color.White,
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF0D0716), onBackground = Color(0xFFF4E9FF),
        surface = Color(0xFF190E2A), onSurface = Color(0xFFF4E9FF),
        surfaceVariant = Color(0xFF241540), onSurfaceVariant = Color(0xFFB79ADB),
        outline = Color(0xFF4A2A78),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFFF2D95), 0.10f, 0.85f, 0.40f, 0.25f),
            ThemeOrb(Color(0xFF00E5FF), 0.90f, 0.12f, 0.38f, 0.25f)
        ),
        cornerRadius = 18, surfaceAlpha = 0.92f, edgeGlow = true, elevation = 0,
        glowColor = Color(0xFFFF2D95)
    )

    /** Pure-black AMOLED battery saver. */
    val OLED_BLACK = AppThemeSpec(
        id = "oled_black",
        name = "OLED Black",
        isDark = true,
        primary = Color(0xFF4D8DFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF000000), onBackground = Color(0xFFDDE3F0),
        surface = Color(0xFF0A0A0C), onSurface = Color(0xFFDDE3F0),
        surfaceVariant = Color(0xFF131318), onSurfaceVariant = Color(0xFF8B93A8),
        outline = Color(0xFF26262E),
        cornerRadius = 14, elevation = 0
    )

    /** Clean bright minimalism. */
    val MINIMAL_LIGHT = AppThemeSpec(
        id = "minimal_light",
        name = "Minimal Light",
        isDark = false,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFFF7F8FB), onBackground = Color(0xFF16203A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16203A),
        surfaceVariant = Color(0xFFECEFF6), onSurfaceVariant = Color(0xFF4A5878),
        outline = Color(0xFFD3DAE8),
        cornerRadius = 18, elevation = 1
    )

    /** All themes, gallery order. */
    val ALL: List<AppThemeSpec> = listOf(
        PILOT_DARK, LIQUID_GLASS, HAWAII_NIGHT, HAWAII_DAY,
        COCKPIT_HUD, DARK_NEON, OLED_BLACK, MINIMAL_LIGHT
    )

    /** Safe lookup with default fallback. */
    fun byId(id: String?): AppThemeSpec =
        ALL.firstOrNull { it.id == id } ?: PILOT_DARK
}
