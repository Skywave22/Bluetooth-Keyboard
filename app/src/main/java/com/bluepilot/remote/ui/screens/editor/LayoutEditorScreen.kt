package com.bluepilot.remote.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType
import com.bluepilot.remote.ui.widgets.RenderWidget
import com.bluepilot.remote.ui.widgets.WidgetEvents
import com.bluepilot.remote.viewmodel.LayoutEditorViewModel

/**
 * The Layout Editor — Edit Mode ⇄ Preview Mode.
 *
 * Edit mode:
 *  - tap widget = select (blue outline + resize handle)
 *  - drag widget body = move (grid snap)
 *  - drag corner handle = resize
 *  - palette row adds new widgets; style sheet edits selected widget
 * Preview mode: identical rendering, live HID behavior via WidgetInteractor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onBack: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val previewMode by viewModel.previewMode.collectAsState()
    val dirty by viewModel.dirty.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()

    var showStyleSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(layout.name + if (dirty) " •" else "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo && !previewMode) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.togglePreview() }) {
                        Icon(
                            if (previewMode) Icons.Rounded.Edit else Icons.Rounded.PlayArrow,
                            contentDescription = if (previewMode) "Edit mode" else "Preview mode"
                        )
                    }
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ---------- Palette / selected-widget toolbar (edit mode only) ----------
            if (!previewMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedId == null) {
                        Text(
                            "Add:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        paletteEntries.forEach { (label, type) ->
                            AssistChip(onClick = { viewModel.addWidget(type) }, label = { Text(label) })
                        }
                        Text(
                            "Zones:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.TOP_BOTTOM_THIRDS,
                                    listOf(WidgetType.TRACKPAD, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Pad + keys") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.HORIZONTAL_2,
                                    listOf(WidgetType.TRACKPAD, WidgetType.DPAD)
                                )
                            },
                            label = { Text("Split H") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.VERTICAL_2,
                                    listOf(WidgetType.JOYSTICK, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Split V") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.GRID_2X2,
                                    listOf(WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Grid 2×2") }
                        )
                    } else {
                        AssistChip(onClick = { showStyleSheet = true }, label = { Text("Style & action") })
                        AssistChip(
                            onClick = { viewModel.duplicateSelected() },
                            label = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = { viewModel.removeSelected() },
                            label = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp)) }
                        )
                        AssistChip(onClick = { viewModel.select(null) }, label = { Text("Deselect") })
                    }
                }
            }

            // ---------- Canvas ----------
            EditorCanvas(
                viewModel = viewModel,
                previewMode = previewMode,
                selectedId = selectedId,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }

    if (showStyleSheet && selectedId != null) {
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
            WidgetStylePanel(viewModel = viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val paletteEntries = listOf(
    "Button" to WidgetType.BUTTON,
    "Trackpad" to WidgetType.TRACKPAD,
    "Scroll" to WidgetType.SCROLL_STRIP,
    "Joystick" to WidgetType.JOYSTICK,
    "Slider" to WidgetType.SLIDER,
    "D-pad" to WidgetType.DPAD
)

// ----------------------------------------------------------------------
// Canvas
// ----------------------------------------------------------------------

@Composable
private fun EditorCanvas(
    viewModel: LayoutEditorViewModel,
    previewMode: Boolean,
    selectedId: String?,
    modifier: Modifier = Modifier
) {
    val layout by viewModel.layout.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.background,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
    ) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        val canvasWpx = constraints.maxWidth.toFloat()
        val canvasHpx = constraints.maxHeight.toFloat()

        val events = remember(previewMode) {
            object : WidgetEvents {
                override fun onPrimary(widget: WidgetSpec) { if (previewMode) viewModel.interactor.primary(widget) }
                override fun onSecondary(widget: WidgetSpec) { if (previewMode) viewModel.interactor.secondary(widget) }
                override fun onTrackpadStart() { if (previewMode) viewModel.interactor.trackpadStart() }
                override fun onTrackpadDelta(dx: Float, dy: Float) { if (previewMode) viewModel.interactor.trackpadDelta(dx, dy) }
                override fun onScrollDelta(dy: Float) { if (previewMode) viewModel.interactor.scrollDelta(dy) }
                override fun onJoystick(x: Float, y: Float) { if (previewMode) viewModel.interactor.joystick(x, y) }
                override fun onDpad(dirX: Int, dirY: Int) { if (previewMode) viewModel.interactor.dpad(dirX, dirY) }
            }
        }

        layout.widgets.filter { it.style.visible || !previewMode }.forEach { widget ->
            val frame = widget.frame.sanitized()
            val isSelected = widget.id == selectedId && !previewMode

            Box(
                modifier = Modifier
                    .offset(x = canvasW * frame.x, y = canvasH * frame.y)
                    .size(width = canvasW * frame.w, height = canvasH * frame.h)
            ) {
                if (previewMode) {
                    RenderWidget(widget = widget, events = events, modifier = Modifier.fillMaxSize())
                } else {
                    // Edit mode: static render + selection/drag overlay on top.
                    RenderWidget(widget = widget, events = noOpEvents, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            .then(
                                if (!widget.style.visible) Modifier.background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            .pointerInputSelectAndDrag(
                                widgetId = widget.id,
                                onSelect = { viewModel.select(widget.id) },
                                onDragStart = { viewModel.beginGesture(); viewModel.select(widget.id) },
                                onDrag = { dxPx, dyPx ->
                                    val current = viewModel.layout.value.widgets.firstOrNull { it.id == widget.id } ?: return@pointerInputSelectAndDrag
                                    viewModel.moveSelected(
                                        current.frame.x + dxPx / canvasWpx,
                                        current.frame.y + dyPx / canvasHpx
                                    )
                                }
                            )
                    )
                    // Resize handle (bottom-right corner) for the selected widget.
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .pointerInputResize(
                                    onStart = { viewModel.beginGesture() },
                                    onResize = { dxPx, dyPx ->
                                        val current = viewModel.layout.value.widgets.firstOrNull { it.id == widget.id } ?: return@pointerInputResize
                                        viewModel.resizeSelected(
                                            current.frame.w + dxPx / canvasWpx,
                                            current.frame.h + dyPx / canvasHpx
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/** No-op events for static edit-mode rendering. */
private val noOpEvents = object : WidgetEvents {
    override fun onPrimary(widget: WidgetSpec) {}
    override fun onSecondary(widget: WidgetSpec) {}
    override fun onTrackpadStart() {}
    override fun onTrackpadDelta(dx: Float, dy: Float) {}
    override fun onScrollDelta(dy: Float) {}
    override fun onJoystick(x: Float, y: Float) {}
    override fun onDpad(dirX: Int, dirY: Int) {}
}

// Gesture helpers -------------------------------------------------------

private fun Modifier.pointerInputSelectAndDrag(
    widgetId: String,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit
): Modifier = this
    .pointerInput(widgetId + "-tap") {
        detectTapGestures(onTap = { onSelect() })
    }
    .pointerInput(widgetId + "-drag") {
        detectDragGestures(
            onDragStart = { onDragStart() }
        ) { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        }
    }

private fun Modifier.pointerInputResize(
    onStart: () -> Unit,
    onResize: (Float, Float) -> Unit
): Modifier = this.pointerInput("resize") {
    detectDragGestures(
        onDragStart = { onStart() }
    ) { change, dragAmount ->
        change.consume()
        onResize(dragAmount.x, dragAmount.y)
    }
}
