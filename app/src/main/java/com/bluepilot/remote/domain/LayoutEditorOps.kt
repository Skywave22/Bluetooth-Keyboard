package com.bluepilot.remote.domain

import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Pure layout-editing operations — the whole customization engine's logic
 * with zero Android dependencies, so every operation is unit-tested.
 *
 * All operations return a NEW LayoutSpec (immutable editing) and always
 * sanitize, so an editor bug can never persist an invalid layout.
 */
object LayoutEditorOps {

    /** Snap a coordinate to the grid (grid<=0 disables snapping). */
    fun snap(value: Float, grid: Float): Float =
        if (grid <= 0f) value else (value / grid).roundToInt() * grid

    /** Place a widget at an absolute fractional position (snap + clamp). */
    fun place(layout: LayoutSpec, id: String, x: Float, y: Float): LayoutSpec =
        updateWidget(layout, id) { w ->
            w.copy(
                frame = w.frame.copy(
                    x = snap(x, layout.gridSize),
                    y = snap(y, layout.gridSize)
                ).sanitized()
            )
        }

    /** Resize a widget to absolute fractional dimensions (snap + clamp). */
    fun resize(layout: LayoutSpec, id: String, w: Float, h: Float): LayoutSpec =
        updateWidget(layout, id) { widget ->
            widget.copy(
                frame = widget.frame.copy(
                    w = snap(w, layout.gridSize).coerceAtLeast(WidgetFrame.MIN_SIZE),
                    h = snap(h, layout.gridSize).coerceAtLeast(WidgetFrame.MIN_SIZE)
                ).sanitized()
            )
        }

    /** Generic single-widget transform (sanitizes the result). */
    fun updateWidget(layout: LayoutSpec, id: String, transform: (WidgetSpec) -> WidgetSpec): LayoutSpec =
        layout.copy(
            widgets = layout.widgets.map { if (it.id == id) transform(it).sanitized() else it }
        )

    /** Update only the style of a widget. */
    fun updateStyle(layout: LayoutSpec, id: String, transform: (WidgetStyle) -> WidgetStyle): LayoutSpec =
        updateWidget(layout, id) { it.copy(style = transform(it.style)) }

    /** Add a new widget of [type] near the canvas center. Returns (layout, newId). */
    fun add(layout: LayoutSpec, type: WidgetType): Pair<LayoutSpec, String?> {
        if (layout.widgets.size >= LayoutSpec.MAX_WIDGETS) return layout to null
        val id = UUID.randomUUID().toString()
        val defaults = when (type) {
            WidgetType.BUTTON -> WidgetFrame(0.35f, 0.4f, 0.3f, 0.12f)
            WidgetType.TRACKPAD -> WidgetFrame(0.1f, 0.1f, 0.6f, 0.4f)
            WidgetType.SCROLL_STRIP -> WidgetFrame(0.8f, 0.1f, 0.15f, 0.4f)
            WidgetType.JOYSTICK -> WidgetFrame(0.3f, 0.5f, 0.4f, 0.3f)
            WidgetType.SLIDER -> WidgetFrame(0.8f, 0.5f, 0.15f, 0.35f)
            WidgetType.DPAD -> WidgetFrame(0.3f, 0.55f, 0.4f, 0.35f)
        }
        val label = when (type) {
            WidgetType.BUTTON -> "Button"
            WidgetType.TRACKPAD -> "Trackpad"
            WidgetType.SCROLL_STRIP -> "Scroll"
            WidgetType.JOYSTICK -> ""
            WidgetType.SLIDER -> ""
            WidgetType.DPAD -> ""
        }
        val widget = WidgetSpec(
            id = id,
            type = type,
            frame = defaults,
            style = WidgetStyle(label = label)
        ).sanitized()
        return layout.copy(widgets = layout.widgets + widget) to id
    }

    /** Duplicate a widget slightly offset. Returns (layout, newId or null). */
    fun duplicate(layout: LayoutSpec, id: String): Pair<LayoutSpec, String?> {
        if (layout.widgets.size >= LayoutSpec.MAX_WIDGETS) return layout to null
        val source = layout.widgets.firstOrNull { it.id == id } ?: return layout to null
        val newId = UUID.randomUUID().toString()
        val copy = source.copy(
            id = newId,
            frame = source.frame.copy(
                x = source.frame.x + 0.04f,
                y = source.frame.y + 0.04f
            ).sanitized()
        )
        return layout.copy(widgets = layout.widgets + copy) to newId
    }

    /** Remove a widget by id. */
    fun remove(layout: LayoutSpec, id: String): LayoutSpec =
        layout.copy(widgets = layout.widgets.filterNot { it.id == id })

    /** Rename the layout (validated). */
    fun rename(layout: LayoutSpec, name: String): LayoutSpec =
        layout.copy(name = name.take(LayoutSpec.NAME_MAX).ifBlank { layout.name })

    // ------------------------------------------------------------------
    // Zone splitter (combo system)
    // ------------------------------------------------------------------

    /** How to split the canvas into zones. */
    enum class ZoneSplit { HORIZONTAL_2, VERTICAL_2, GRID_2X2, TOP_BOTTOM_THIRDS }

    /** Fractional rectangles produced by a split (with a small gutter). */
    fun zoneFrames(split: ZoneSplit): List<WidgetFrame> {
        val g = 0.02f // gutter between zones
        return when (split) {
            ZoneSplit.HORIZONTAL_2 -> listOf(
                WidgetFrame(g, g, 1f - 2 * g, 0.5f - 1.5f * g),
                WidgetFrame(g, 0.5f + 0.5f * g, 1f - 2 * g, 0.5f - 1.5f * g)
            )
            ZoneSplit.VERTICAL_2 -> listOf(
                WidgetFrame(g, g, 0.5f - 1.5f * g, 1f - 2 * g),
                WidgetFrame(0.5f + 0.5f * g, g, 0.5f - 1.5f * g, 1f - 2 * g)
            )
            ZoneSplit.GRID_2X2 -> listOf(
                WidgetFrame(g, g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(0.5f + 0.5f * g, g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(g, 0.5f + 0.5f * g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(0.5f + 0.5f * g, 0.5f + 0.5f * g, 0.5f - 1.5f * g, 0.5f - 1.5f * g)
            )
            ZoneSplit.TOP_BOTTOM_THIRDS -> listOf(
                WidgetFrame(g, g, 1f - 2 * g, 0.62f - 1.5f * g),
                WidgetFrame(g, 0.62f + 0.5f * g, 1f - 2 * g, 0.38f - 1.5f * g)
            )
        }
    }

    /**
     * Apply a zone split: adds one widget of [types] per zone, sized to fill
     * that zone. Types beyond the zone count are ignored; missing types
     * default to BUTTON. Respects the widget cap.
     */
    fun applyZones(layout: LayoutSpec, split: ZoneSplit, types: List<WidgetType>): LayoutSpec {
        val frames = zoneFrames(split)
        var result = layout
        frames.forEachIndexed { index, frame ->
            if (result.widgets.size >= LayoutSpec.MAX_WIDGETS) return result
            val type = types.getOrNull(index) ?: WidgetType.BUTTON
            val (next, newId) = add(result, type)
            result = if (newId != null) {
                updateWidget(next, newId) { it.copy(frame = frame.sanitized()) }
            } else next
        }
        return result
    }
}
