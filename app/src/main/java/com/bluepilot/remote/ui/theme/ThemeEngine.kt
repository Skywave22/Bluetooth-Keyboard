package com.bluepilot.remote.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.max

/**
 * SECTION 1 — Theme engine.
 *
 * [LocalAppTheme] carries the active [AppThemeSpec] to every composable.
 * [BluePilotAppTheme] bridges the spec into a Material3 ColorScheme so all
 * existing M3 components re-skin automatically — every current screen keeps
 * working untouched, and theme changes propagate instantly app-wide.
 */
val LocalAppTheme = staticCompositionLocalOf { BuiltInThemes.PILOT_DARK }

@Composable
fun BluePilotAppTheme(
    spec: AppThemeSpec,
    content: @Composable () -> Unit
) {
    val scheme = if (spec.isDark) {
        darkColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = spec.primary.copy(alpha = 0.75f),
            onPrimaryContainer = spec.onPrimary,
            secondary = spec.secondary, onSecondary = spec.onPrimary,
            background = spec.background, onBackground = spec.onBackground,
            surface = spec.surface, onSurface = spec.onSurface,
            surfaceVariant = spec.surfaceVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = spec.onPrimary,
            errorContainer = spec.error.copy(alpha = 0.25f), onErrorContainer = spec.onBackground
        )
    } else {
        lightColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = spec.primary.copy(alpha = 0.15f),
            onPrimaryContainer = spec.primary,
            secondary = spec.secondary, onSecondary = spec.onPrimary,
            background = spec.background, onBackground = spec.onBackground,
            surface = spec.surface, onSurface = spec.onSurface,
            surfaceVariant = spec.surfaceVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = spec.onPrimary,
            errorContainer = spec.error.copy(alpha = 0.15f), onErrorContainer = spec.error
        )
    }

    // Cockpit HUD theme flips the type ramp to monospace.
    val typography = if (spec.monoFont) monoTypography() else BluePilotTypography

    CompositionLocalProvider(LocalAppTheme provides spec) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content
        )
    }
}

private fun monoTypography(): Typography {
    val base = BluePilotTypography
    return Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Monospace),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Monospace),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace)
    )
}

/**
 * Themed background: solid color plus the theme's blurred gradient orbs
 * (the liquid-glass aurora / tropical lagoon look). Pure Canvas — cheap to
 * draw, no bitmap allocations, no real-time blur cost.
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spec = LocalAppTheme.current
    Box(modifier = modifier.fillMaxSize().background(spec.background)) {
        if (spec.backgroundOrbs.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxEdge = max(size.width, size.height)
                spec.backgroundOrbs.forEach { orb ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                orb.color.copy(alpha = orb.alpha),
                                orb.color.copy(alpha = 0f)
                            ),
                            center = Offset(size.width * orb.x, size.height * orb.y),
                            radius = maxEdge * orb.radius
                        ),
                        radius = maxEdge * orb.radius,
                        center = Offset(size.width * orb.x, size.height * orb.y)
                    )
                }
            }
        }
        content()
    }
}
