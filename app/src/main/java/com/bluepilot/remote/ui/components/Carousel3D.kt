package com.bluepilot.remote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * SECTION 6 - 3D theme carousel effect: cards tilt/recede based on their
 * distance from the viewport center (Coverflow-style), computed from the
 * LazyList layout info. Pure graphicsLayer - GPU-only.
 */
fun Modifier.carousel3D(
    itemIndex: Int,
    firstVisible: Int,
    firstVisibleOffsetPx: Int,
    itemHeightPx: Float,
    enabled: Boolean = true
): Modifier {
    if (!enabled || itemHeightPx <= 0f) return this
    return this.graphicsLayer {
        // Position of this item's center relative to viewport top, in items.
        val posItems = (itemIndex - firstVisible).toFloat() - (firstVisibleOffsetPx / itemHeightPx)
        // 0 at top; tilt increases with distance from ~1.5 items down.
        val dist = (posItems - 1.5f) / 3f
        cameraDistance = 10f * density
        rotationX = (-dist * 10f).coerceIn(-12f, 12f)
        val scale = 1f - (kotlin.math.abs(dist) * 0.06f).coerceIn(0f, 0.12f)
        scaleX = scale
        scaleY = scale
    }
}
