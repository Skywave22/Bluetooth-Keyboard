package com.bluepilot.remote.ui.screens.gamepadbuilder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.gamepad.ControlShape
import com.bluepilot.remote.model.gamepad.GamepadButtonNames
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.StickSide
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.gamepad.GamepadCanvas
import com.bluepilot.remote.ui.gamepad.GamepadEvents
import com.bluepilot.remote.ui.gamepad.RenderGamepadControl
import com.bluepilot.remote.viewmodel.GamepadBuilderViewModel
import timber.log.Timber

/**
 * SECTION 2 — Gamepad Builder: profile list → PLAY / EDIT modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadBuilderScreen(
    onBack: () -> Unit,
    viewModel: GamepadBuilderViewModel = hiltViewModel()
) {
    val playing by viewModel.playing.collectAsState()
    val draft by viewModel.draft.collectAsState()

    when {
        playing != null -> GamepadPlayer(viewModel)
        draft != null -> GamepadEditor(viewModel)
        else -> ProfileList(onBack, viewModel)
    }
}

// ======================================================================
// Profile list
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileList(onBack: () -> Unit, viewModel: GamepadBuilderViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    val message by viewModel.message.collectAsState()
    val exportPayload by viewModel.exportPayload.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.onFailure { Timber.e(it, "gamepad import read failed") }.getOrNull()
        viewModel.importFromJson(text)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.second.toByteArray()) }
            }.onFailure { Timber.e(it, "gamepad export write failed") }
        }
        viewModel.consumeExport()
    }
    LaunchedEffect(exportPayload) { exportPayload?.let { exportLauncher.launch(it.first) } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Gamepad Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Import gamepad")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newProfile() }) {
                Icon(Icons.Rounded.Add, contentDescription = "New gamepad")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tap ▶ to play a gamepad • pencil to edit • + to build a new one. Rotate to landscape for the best play experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(profiles, key = { it.id }) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.spec.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${profile.spec.controls.size} controls" + if (profile.isBuiltIn) "  •  template" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.play(profile.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.edit(profile) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.duplicate(profile.id) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate")
                        }
                        IconButton(onClick = { viewModel.requestExport(profile.id) }) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Export")
                        }
                        if (!profile.isBuiltIn) {
                            IconButton(onClick = { viewModel.delete(profile.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================
// Player (live HID)
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamepadPlayer(viewModel: GamepadBuilderViewModel) {
    val playing by viewModel.playing.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val profile = playing ?: return

    // BUG FIX: if the user leaves via system back (route pop), the composable
    // is disposed without stopPlaying() — neutralize HID state so no button
    // stays pressed on the host. neutralizeHid() is idempotent.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.neutralizeHid() }
    }

    val events = remember {
        object : GamepadEvents {
            override fun onButton(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onButton(control, pressed)
            override fun onStick(control: GamepadControlSpec, x: Float, y: Float) =
                viewModel.onStick(control, x, y)
            override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) =
                viewModel.onDpadTouch(control, dx, dy)
            override fun onDpadRelease(control: GamepadControlSpec) =
                viewModel.onDpadRelease(control)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(profile.spec.name) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopPlaying() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick-switch between saved gamepad profiles.
                    IconButton(onClick = { viewModel.playNext(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous profile")
                    }
                    IconButton(onClick = { viewModel.playNext(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = "Next profile")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            NotConnectedBanner(!isConnected)
            GamepadCanvas(
                layout = profile.spec,
                events = events,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ======================================================================
// Editor
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamepadEditor(viewModel: GamepadBuilderViewModel) {
    val draft by viewModel.draft.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val previewMode by viewModel.previewMode.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val (_, spec) = draft ?: return
    var showProperties by remember { mutableStateOf(false) }

    val playEvents = remember {
        object : GamepadEvents {
            override fun onButton(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onButton(control, pressed)
            override fun onStick(control: GamepadControlSpec, x: Float, y: Float) =
                viewModel.onStick(control, x, y)
            override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) =
                viewModel.onDpadTouch(control, dx, dy)
            override fun onDpadRelease(control: GamepadControlSpec) =
                viewModel.onDpadRelease(control)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(spec.name) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeDraft() }) {
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
                    IconButton(onClick = { viewModel.saveDraft() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        Text("Add:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AssistChip(onClick = { viewModel.addControl(GamepadControlType.BUTTON) }, label = { Text("Button") })
                        AssistChip(onClick = { viewModel.addControl(GamepadControlType.TRIGGER) }, label = { Text("Trigger") })
                        AssistChip(onClick = { viewModel.addControl(GamepadControlType.STICK) }, label = { Text("Stick") })
                        AssistChip(onClick = { viewModel.addControl(GamepadControlType.DPAD) }, label = { Text("D-pad") })
                    } else {
                        AssistChip(onClick = { showProperties = true }, label = { Text("Properties") })
                        AssistChip(onClick = { viewModel.removeSelected() }, label = { Text("Delete") })
                        AssistChip(onClick = { viewModel.select(null) }, label = { Text("Deselect") })
                    }
                }
            }

            // ---------- Canvas ----------
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                val w = maxWidth
                val h = maxHeight
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()

                spec.controls.forEach { control ->
                    val frame = control.frame.sanitized()
                    val isSelected = control.id == selectedId && !previewMode
                    Box(
                        modifier = Modifier
                            .offset(x = w * frame.x, y = h * frame.y)
                            .size(width = w * frame.w, height = h * frame.h)
                    ) {
                        if (previewMode) {
                            RenderGamepadControl(control, playEvents, Modifier.fillMaxSize())
                        } else {
                            RenderGamepadControl(control, noOpGamepadEvents, Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .pointerInput(control.id + "-tap") {
                                        detectTapGestures(onTap = { viewModel.select(control.id) })
                                    }
                                    .pointerInput(control.id + "-drag") {
                                        detectDragGestures(
                                            onDragStart = { viewModel.beginGesture(); viewModel.select(control.id) }
                                        ) { change, drag ->
                                            change.consume()
                                            val current = viewModel.draft.value?.second
                                                ?.controls?.firstOrNull { it.id == control.id }
                                                ?: return@detectDragGestures
                                            viewModel.moveSelected(
                                                current.frame.x + drag.x / wPx,
                                                current.frame.y + drag.y / hPx
                                            )
                                        }
                                    }
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(26.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .pointerInput("resize-" + control.id) {
                                            detectDragGestures(
                                                onDragStart = { viewModel.beginGesture() }
                                            ) { change, drag ->
                                                change.consume()
                                                val current = viewModel.draft.value?.second
                                                    ?.controls?.firstOrNull { it.id == control.id }
                                                    ?: return@detectDragGestures
                                                viewModel.resizeSelected(
                                                    current.frame.w + drag.x / wPx,
                                                    current.frame.h + drag.y / hPx
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProperties && selectedId != null) {
        ModalBottomSheet(onDismissRequest = { showProperties = false }) {
            ControlPropertiesPanel(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val noOpGamepadEvents = object : GamepadEvents {
    override fun onButton(control: GamepadControlSpec, pressed: Boolean) {}
    override fun onStick(control: GamepadControlSpec, x: Float, y: Float) {}
    override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) {}
    override fun onDpadRelease(control: GamepadControlSpec) {}
}

// ======================================================================
// Properties panel
// ======================================================================

private val controlPalette = listOf(
    0xFF2F6BFF, 0xFF29C5FF, 0xFF2ECC71, 0xFFF1C40F, 0xFFE67E22,
    0xFFE74C3C, 0xFFFF2D95, 0xFF9B59B6, 0xFF1A2238, 0xFF7F8C8D
)

@Composable
private fun ControlPropertiesPanel(viewModel: GamepadBuilderViewModel) {
    val draft by viewModel.draft.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val control = draft?.second?.controls?.firstOrNull { it.id == selectedId } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Control: ${control.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        // Label (buttons/triggers)
        if (control.type == GamepadControlType.BUTTON || control.type == GamepadControlType.TRIGGER) {
            OutlinedTextField(
                value = control.label,
                onValueChange = { new -> viewModel.updateSelected { it.copy(label = new) } },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // HID button binding
            Text("Sends HID button", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..15).forEach { index ->
                    FilterChip(
                        selected = control.buttonIndex == index,
                        onClick = { viewModel.updateSelected { it.copy(buttonIndex = index) } },
                        label = { Text(GamepadButtonNames.label(index)) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Shape
            Text("Shape", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlShape.entries.forEach { shape ->
                    FilterChip(
                        selected = control.shape == shape,
                        onClick = { viewModel.updateSelected { it.copy(shape = shape) } },
                        label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        // Stick options
        if (control.type == GamepadControlType.STICK) {
            Text("Axis pair", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StickSide.entries.forEach { side ->
                    FilterChip(
                        selected = control.stickSide == side,
                        onClick = { viewModel.updateSelected { it.copy(stickSide = side) } },
                        label = { Text(if (side == StickSide.LEFT) "Left stick (X/Y)" else "Right stick (Z/Rz)") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Dead zone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${control.deadZone}%", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.deadZone.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(deadZone = v.toInt()) } },
                    valueRange = 0f..50f
                )
            }
        }

        // D-pad options
        if (control.type == GamepadControlType.DPAD) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("8-way (diagonals)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.eightWay,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(eightWay = v) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Color
        Text("Color", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            controlPalette.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(argb.toULong().toLong() and 0xFFFFFFFF), CircleShape)
                        .border(
                            width = if (argb == control.color) 3.dp else 1.dp,
                            color = if (argb == control.color) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { viewModel.updateSelected { it.copy(color = argb) } }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Opacity
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Opacity", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format("%.2f", control.opacity), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = control.opacity,
                onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(opacity = v) } },
                valueRange = 0.15f..1f
            )
        }
    }
}
