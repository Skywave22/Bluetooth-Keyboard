package com.bluepilot.remote.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.layout.LayoutRepository
import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.domain.WidgetInteractor
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import com.bluepilot.remote.data.macros.Macro
import com.bluepilot.remote.data.macros.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Layout editor state machine.
 *
 * Edit mode: drag/resize/style/add/delete widgets (all via LayoutEditorOps).
 * Preview mode: the SAME layout becomes live using WidgetInteractor.
 * Dirty tracking + explicit save to Room. Simple undo (last 20 states).
 */
@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val repository: LayoutRepository,
    sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    macroEngine: MacroEngine,
    macroRepository: MacroRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val UNDO_LIMIT = 20
    }

    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    /** Live behavior for Preview mode. */
    val interactor = WidgetInteractor(
        send = { sendAction(it) },
        mouseSettings = { mouseSettings.value },
        runMacro = { macroEngine.play(it) }
    )

    private val _layout = MutableStateFlow(LayoutSpec(name = "New layout"))
    val layout: StateFlow<LayoutSpec> = _layout.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _previewMode = MutableStateFlow(false)
    val previewMode: StateFlow<Boolean> = _previewMode.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val undoStack = ArrayDeque<LayoutSpec>()
    val canUndo: StateFlow<Boolean> get() = _canUndo
    private val _canUndo = MutableStateFlow(false)

    private var loadedBuiltIn = false

    init {
        if (profileId > 0) {
            viewModelScope.launch {
                repository.byId(profileId)?.let { profile ->
                    _layout.value = profile.spec
                    loadedBuiltIn = profile.isBuiltIn
                }
            }
        }
    }

    /** Selected widget spec (null when nothing selected). */
    fun selectedWidget(): WidgetSpec? =
        _layout.value.widgets.firstOrNull { it.id == _selectedId.value }

    // ------------------------------------------------------------------
    // Mode + selection
    // ------------------------------------------------------------------

    fun togglePreview() {
        _previewMode.value = !_previewMode.value
        if (_previewMode.value) _selectedId.value = null
    }

    fun select(id: String?) {
        if (!_previewMode.value) _selectedId.value = id
    }

    // ------------------------------------------------------------------
    // Mutations (all push undo + mark dirty)
    // ------------------------------------------------------------------

    private fun mutate(transform: (LayoutSpec) -> LayoutSpec) {
        pushUndo()
        _layout.value = transform(_layout.value).sanitized()
        _dirty.value = true
        _saved.value = false
    }

    /** Continuous drag: no undo per pixel — call [beginGesture] first. */
    fun moveSelected(x: Float, y: Float) {
        val id = _selectedId.value ?: return
        _layout.value = LayoutEditorOps.place(_layout.value, id, x, y)
        _dirty.value = true
        _saved.value = false
    }

    fun resizeSelected(w: Float, h: Float) {
        val id = _selectedId.value ?: return
        _layout.value = LayoutEditorOps.resize(_layout.value, id, w, h)
        _dirty.value = true
        _saved.value = false
    }

    /** Snapshot once at gesture start so a whole drag is one undo step. */
    fun beginGesture() = pushUndo()

    fun addWidget(type: WidgetType) {
        pushUndo()
        val (next, newId) = LayoutEditorOps.add(_layout.value, type)
        _layout.value = next
        newId?.let { _selectedId.value = it }
        _dirty.value = true
        _saved.value = false
    }

    fun duplicateSelected() {
        val id = _selectedId.value ?: return
        pushUndo()
        val (next, newId) = LayoutEditorOps.duplicate(_layout.value, id)
        _layout.value = next
        newId?.let { _selectedId.value = it }
        _dirty.value = true
        _saved.value = false
    }

    fun removeSelected() {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.remove(it, id) }
        _selectedId.value = null
    }

    fun updateSelectedStyle(transform: (WidgetStyle) -> WidgetStyle) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateStyle(it, id, transform) }
    }

    fun setSelectedAction(action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateWidget(it, id) { w -> w.copy(action = action) } }
    }

    fun setSelectedSecondaryAction(action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateWidget(it, id) { w -> w.copy(secondaryAction = action) } }
    }

    /** GESTURE_ZONE: bind one swipe direction / two-finger tap. */
    fun setSelectedGestureAction(slot: GestureSlot, action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate {
            LayoutEditorOps.updateWidget(it, id) { w ->
                when (slot) {
                    GestureSlot.UP -> w.copy(swipeUp = action)
                    GestureSlot.DOWN -> w.copy(swipeDown = action)
                    GestureSlot.LEFT -> w.copy(swipeLeft = action)
                    GestureSlot.RIGHT -> w.copy(swipeRight = action)
                    GestureSlot.TWO_FINGER -> w.copy(twoFingerTap = action)
                }
            }
        }
    }

    enum class GestureSlot { UP, DOWN, LEFT, RIGHT, TWO_FINGER }

    fun rename(name: String) = mutate { LayoutEditorOps.rename(it, name) }

    fun setGridSize(size: Float) = mutate { it.copy(gridSize = size) }

    /** Apply a zone split: one widget per zone, sized to fill it. */
    fun applyZoneSplit(split: LayoutEditorOps.ZoneSplit, types: List<WidgetType>) =
        mutate { LayoutEditorOps.applyZones(it, split, types) }

    /** Saved macros for the action picker (RunMacro bindings). */
    val macros: StateFlow<List<Macro>> = macroRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------------------------------------------------------
    // Undo / save
    // ------------------------------------------------------------------

    private fun pushUndo() {
        undoStack.addLast(_layout.value)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        _canUndo.value = undoStack.isNotEmpty()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _layout.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _dirty.value = true
        _saved.value = false
    }

    /**
     * Persist to Room. Built-in profiles are saved as a NEW user copy
     * (templates stay pristine).
     */
    fun save() {
        viewModelScope.launch {
            runCatching {
                if (loadedBuiltIn) {
                    val copy = _layout.value.copy(name = _layout.value.name + " (custom)")
                    repository.save(null, copy)
                } else {
                    repository.save(if (profileId > 0) profileId else null, _layout.value)
                }
            }.onSuccess {
                _dirty.value = false
                _saved.value = true
                Timber.i("layout saved")
            }.onFailure {
                Timber.e(it, "layout save failed")
            }
        }
    }
}
