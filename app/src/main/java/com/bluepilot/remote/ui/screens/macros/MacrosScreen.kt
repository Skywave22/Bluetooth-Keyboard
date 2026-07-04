package com.bluepilot.remote.ui.screens.macros

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.viewmodel.MacrosViewModel

/**
 * Macros hub: list + step-sequence editor.
 * Editor: add key/chord/text/media/mouse/delay steps, reorder, delete,
 * test-play live, save to Room. Saved macros are bindable to any custom
 * button via the layout editor's action picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(
    onBack: () -> Unit,
    viewModel: MacrosViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()

    if (draft != null) {
        MacroEditor(viewModel)
        return
    }

    val macros by viewModel.macros.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordedSteps by viewModel.recordedSteps.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Macros") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newMacro() }) {
                Icon(Icons.Rounded.Add, contentDescription = "New macro")
            }
        },
        bottomBar = {
            // SECTION 3A — Recorder bar: arm → use ANY control screen
            // (keyboard/media/mouse clicks are captured) → stop = draft.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isRecording) "● REC — $recordedSteps steps captured"
                            else "Macro recorder",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isRecording) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (isRecording) "Go use Keyboard/Media/Mouse — then come back and stop."
                            else "Record real inputs from any screen, replay with one tap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isRecording) {
                        androidx.compose.material3.Button(onClick = { viewModel.stopRecording() }) {
                            Text("Stop")
                        }
                    } else {
                        androidx.compose.material3.OutlinedButton(onClick = { viewModel.startRecording() }) {
                            Text("● Record")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    NotConnectedBanner(!isConnected)
                    Text(
                        text = "Macros are step sequences (keys, chords, text, delays). " +
                            "Bind one to any custom button in the Layout editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(macros, key = { it.id }) { macro ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(macro.spec.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${macro.spec.steps.size} steps  •  id ${macro.id}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.play(macro.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.edit(macro) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.delete(macro.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Editor
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroEditor(viewModel: MacrosViewModel) {
    val draft by viewModel.draft.collectAsState()
    val (rowId, spec) = draft ?: return
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (rowId == null) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeDraft() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.testDraft() }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Test-play")
                    }
                    IconButton(onClick = { viewModel.saveDraft() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = spec.name,
                onValueChange = viewModel::renameDraft,
                label = { Text("Macro name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- Step palette ----------
            Text(
                "Add step (${spec.steps.size}/${MacroSpec.STEPS_MAX})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            StepChipRow(
                entries = listOf(
                    "Enter" to MacroStep.KeyTap(HidKeys.ENTER),
                    "Tab" to MacroStep.KeyTap(HidKeys.TAB),
                    "Esc" to MacroStep.KeyTap(HidKeys.ESCAPE),
                    "Space" to MacroStep.KeyTap(HidKeys.SPACE),
                    "Copy" to MacroStep.KeyTap(HidKeys.C, HidModifiers.LEFT_CTRL),
                    "Paste" to MacroStep.KeyTap(HidKeys.V, HidModifiers.LEFT_CTRL),
                    "Alt+Tab" to MacroStep.KeyTap(HidKeys.TAB, HidModifiers.LEFT_ALT),
                    "Win+D" to MacroStep.KeyTap(HidKeys.D, HidModifiers.LEFT_GUI),
                    "Ctl+Alt+Del" to MacroStep.KeyTap(
                        HidKeys.DELETE,
                        (HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_ALT.toInt()).toByte()
                    )
                ),
                onAdd = viewModel::addStep
            )
            StepChipRow(
                entries = listOf(
                    "Play/Pause" to MacroStep.Media(HidConsumer.PLAY_PAUSE),
                    "Vol +" to MacroStep.Media(HidConsumer.VOLUME_UP),
                    "Vol −" to MacroStep.Media(HidConsumer.VOLUME_DOWN),
                    "L-click" to MacroStep.MouseClick(0x01),
                    "R-click" to MacroStep.MouseClick(0x02),
                    "Wait 0.5s" to MacroStep.Delay(500),
                    "Wait 1s" to MacroStep.Delay(1000)
                ),
                onAdd = viewModel::addStep
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text step") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                AssistChip(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.addStep(MacroStep.TypeText(textInput))
                            textInput = ""
                        }
                    },
                    label = { Text("Add") }
                )
            }

            // ---------- Step list ----------
            Text(
                "Steps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(spec.steps) { index, step ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${stepLabel(step)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.moveStepUp(index) }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.removeStep(index) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun StepChipRow(
    entries: List<Pair<String, MacroStep>>,
    onAdd: (MacroStep) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { (label, step) ->
            AssistChip(onClick = { onAdd(step) }, label = { Text(label) })
        }
    }
}

private fun stepLabel(step: MacroStep): String = when (step) {
    is MacroStep.KeyTap -> {
        val mods = mutableListOf<String>()
        val m = step.modifiers.toInt()
        if (m and 0x01 != 0) mods += "Ctrl"
        if (m and 0x02 != 0) mods += "Shift"
        if (m and 0x04 != 0) mods += "Alt"
        if (m and 0x08 != 0) mods += "Win"
        val prefix = if (mods.isEmpty()) "" else mods.joinToString("+") + "+"
        "Key: " + prefix + "0x%02X".format(step.key)
    }
    is MacroStep.TypeText -> "Type: \"${step.text.take(30)}${if (step.text.length > 30) "…" else ""}\""
    is MacroStep.Media -> "Media: 0x%04X".format(step.usage)
    is MacroStep.MouseClick -> "Mouse: " + when (step.buttonMask) {
        0x01 -> "left click"; 0x02 -> "right click"; 0x04 -> "middle click"; else -> "?"
    }
    is MacroStep.Delay -> "Wait ${step.ms} ms"
}
