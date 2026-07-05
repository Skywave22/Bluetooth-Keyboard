package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.gamepad.GamepadProfile
import com.bluepilot.remote.data.gamepad.GamepadProfileRepository
import com.bluepilot.remote.domain.AirMouseCore
import com.bluepilot.remote.sensors.MotionSensorSource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.bluepilot.remote.domain.GamepadRuntimeCore
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import com.bluepilot.remote.model.gamepad.ResponseCurves
import com.bluepilot.remote.model.gamepad.StickSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.bluepilot.remote.model.widgets.WidgetFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * SECTION 2 — Gamepad builder + player state.
 *
 * Modes: profile list → PLAY (live HID) or EDIT (canvas editor with
 * drag/resize/style/binding + preview toggle + undo).
 */
@HiltViewModel
class GamepadBuilderViewModel @Inject constructor(
    private val repository: GamepadProfileRepository,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase,
    private val sensors: MotionSensorSource,
    private val haptics: com.bluepilot.remote.haptics.Haptics
) : ViewModel() {

    // ------------------------------------------------------------------
    // SECTION 7 — Motion controls (gyro steering/aim) while playing.
    // Gyro maps onto the RIGHT stick axes (camera/aim convention), so the
    // physical left stick still handles movement simultaneously.
    // ------------------------------------------------------------------
    val hasGyro: Boolean get() = sensors.hasGyroscope

    private val _motionEnabled = MutableStateFlow(false)
    val motionEnabled: StateFlow<Boolean> = _motionEnabled.asStateFlow()

    private val _motionSensitivity = MutableStateFlow(50)
    val motionSensitivity: StateFlow<Int> = _motionSensitivity.asStateFlow()

    private val _motionDeadZone = MutableStateFlow(8)
    val motionDeadZone: StateFlow<Int> = _motionDeadZone.asStateFlow()

    private val motionCore = AirMouseCore(sensitivity = 50, smoothing = 35)
    private var motionJob: kotlinx.coroutines.Job? = null
    // Current gyro-driven aim (added to hidState right stick as -1..1).
    private var aimX = 0f
    private var aimY = 0f

    fun setMotionEnabled(value: Boolean) {
        if (value == _motionEnabled.value) return
        _motionEnabled.value = value
        if (value) startMotion() else stopMotion()
    }

    fun setMotionSensitivity(value: Int) {
        _motionSensitivity.value = value.coerceIn(0, 100)
        motionCore.sensitivity = _motionSensitivity.value
    }

    fun setMotionDeadZone(value: Int) { _motionDeadZone.value = value.coerceIn(0, 50) }

    /** Recenter aim (drift correction). */
    fun recenterMotion() {
        motionCore.recenter(); aimX = 0f; aimY = 0f
        pushMotionAim()
    }

    private fun startMotion() {
        motionCore.recenter(); aimX = 0f; aimY = 0f
        motionJob = sensors.gyro()
            .onEach { sample ->
                val (dx, dy) = motionCore.step(gyroYaw = sample.y, gyroPitch = sample.x)
                if (dx == 0 && dy == 0) return@onEach
                // Integrate pixel-ish deltas into a -1..1 aim vector with decay.
                aimX = (aimX + dx / 90f).coerceIn(-1f, 1f)
                aimY = (aimY + dy / 90f).coerceIn(-1f, 1f)
                pushMotionAim()
            }
            .catch { }
            .launchIn(viewModelScope)
        // Aim auto-decays toward center so releasing motion recenters camera.
        viewModelScope.launch {
            while (_motionEnabled.value) {
                delay(50)
                if (kotlin.math.abs(aimX) > 0.02f || kotlin.math.abs(aimY) > 0.02f) {
                    aimX *= 0.9f; aimY *= 0.9f
                    pushMotionAim()
                }
            }
        }
    }

    private fun pushMotionAim() {
        val dz = _motionDeadZone.value / 100f
        val ax = if (kotlin.math.abs(aimX) < dz) 0f else aimX
        val ay = if (kotlin.math.abs(aimY) < dz) 0f else aimY
        hidState = hidState.copy(rightX = ax, rightY = ay)
        sendAction(HidAction.GamepadUpdate(hidState))
    }

    private fun stopMotion() {
        motionJob?.cancel(); motionJob = null
        aimX = 0f; aimY = 0f
        hidState = hidState.copy(rightX = 0f, rightY = 0f)
        sendAction(HidAction.GamepadUpdate(hidState))
    }

    companion object {
        private const val UNDO_LIMIT = 20
    }

    val profiles: StateFlow<List<GamepadProfile>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Profile being PLAYED (live HID); null = not playing. */
    private val _playing = MutableStateFlow<GamepadProfile?>(null)
    val playing: StateFlow<GamepadProfile?> = _playing.asStateFlow()

    /** Draft being EDITED: (rowId or null, spec); null = not editing. */
    private val _draft = MutableStateFlow<Pair<Long?, GamepadLayoutSpec>?>(null)
    val draft: StateFlow<Pair<Long?, GamepadLayoutSpec>?> = _draft.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _previewMode = MutableStateFlow(false)
    val previewMode: StateFlow<Boolean> = _previewMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportPayload = MutableStateFlow<Pair<String, String>?>(null)
    val exportPayload: StateFlow<Pair<String, String>?> = _exportPayload.asStateFlow()

    private val undoStack = ArrayDeque<GamepadLayoutSpec>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private var draftIsBuiltIn = false

    // The live HID gamepad state (single source; controls fold into it).
    private var hidState = GamepadSnapshot()

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    // ------------------------------------------------------------------
    // Profile list actions
    // ------------------------------------------------------------------

    fun play(id: Long) {
        viewModelScope.launch {
            hidState = GamepadSnapshot()
            _playing.value = repository.byId(id)
        }
    }

    /** Quick-switch to next profile while playing (wraps). */
    fun playNext(direction: Int) {
        val list = profiles.value
        if (list.size < 2) return
        val current = _playing.value?.id ?: return
        val idx = list.indexOfFirst { it.id == current }
        if (idx < 0) return
        val next = list[(idx + direction + list.size) % list.size]
        play(next.id)
    }

    fun stopPlaying() {
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        setMotionEnabled(false)
        neutralizeHid()
        _playing.value = null
    }

    /**
     * Neutralize the wire state — no stuck buttons/axes on the host.
     * Idempotent: only sends when something is actually non-neutral, and
     * safe to call even while disconnected (HidEngine drops the report).
     */
    fun setNaming(naming: com.bluepilot.remote.model.gamepad.ButtonNaming) =
        mutate { it.copy(naming = naming) }

    fun setStickSensitivity(value: Int) =
        mutate(withUndo = false) { it.copy(stickSensitivity = value.coerceIn(0, 100)) }

    fun neutralizeHid() {
        if (hidState != GamepadSnapshot()) {
            hidState = GamepadSnapshot()
            sendAction(HidAction.GamepadUpdate(hidState))
        }
    }

    fun edit(profile: GamepadProfile) {
        draftIsBuiltIn = profile.isBuiltIn
        undoStack.clear(); _canUndo.value = false
        _selectedId.value = null
        _previewMode.value = false
        _draft.value = profile.id to profile.spec
    }

    fun newProfile() {
        draftIsBuiltIn = false
        undoStack.clear(); _canUndo.value = false
        _selectedId.value = null
        _previewMode.value = false
        _draft.value = null to GamepadLayoutSpec(name = "My gamepad")
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            repository.byId(id)?.let {
                repository.save(null, it.spec.copy(name = it.spec.name + " (copy)"))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { repository.delete(id) } }
    }

    // ------------------------------------------------------------------
    // Live HID handling (play mode + editor preview)
    // ------------------------------------------------------------------

    // Turbo/rapid-fire jobs per control id (cancelled on release).
    private val turboJobs = mutableMapOf<String, Job>()

    fun onButton(control: GamepadControlSpec, pressed: Boolean) {
        if (control.turbo && pressed) {
            // SECTION 5 — turbo: auto-repeat press/release at turboRate Hz
            // while held. One job per control; releasing cancels it.
            turboJobs[control.id]?.cancel()
            turboJobs[control.id] = viewModelScope.launch {
                val period = (1000L / control.turboRate.coerceIn(2, 20))
                while (true) {
                    hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, true)
                    sendAction(HidAction.GamepadUpdate(hidState))
                    delay(period / 2)
                    hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, false)
                    sendAction(HidAction.GamepadUpdate(hidState))
                    delay(period / 2)
                }
            }
            return
        }
        if (control.turbo && !pressed) {
            turboJobs.remove(control.id)?.cancel()
            hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, false)
            sendAction(HidAction.GamepadUpdate(hidState))
            return
        }
        hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, pressed)
        sendAction(HidAction.GamepadUpdate(hidState))
        // SECTION 8 — per-control haptic pattern on press.
        if (pressed) haptics.play(control.haptic)
    }

    fun onStick(control: GamepadControlSpec, x: Float, y: Float) {
        // SECTION 5 — response curve + per-profile sensitivity before dead zone.
        val sens = ((_playing.value ?: run { null })?.spec?.stickSensitivity
            ?: draft.value?.second?.stickSensitivity ?: 70) / 100f * 1.4f + 0.3f
        val cx = ResponseCurves.apply(x, control.curve) * sens
        val cy = ResponseCurves.apply(y, control.curve) * sens
        hidState = GamepadRuntimeCore.withStick(
            hidState, control.stickSide,
            cx.coerceIn(-1f, 1f), cy.coerceIn(-1f, 1f), control.deadZone
        )
        sendAction(HidAction.GamepadUpdate(hidState))
    }

    fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) {
        val hat = GamepadRuntimeCore.hatFromTouch(dx, dy, control.eightWay)
        if (hat != hidState.hat) {
            hidState = GamepadRuntimeCore.withHat(hidState, hat)
            sendAction(HidAction.GamepadUpdate(hidState))
        }
    }

    fun onDpadRelease(@Suppress("UNUSED_PARAMETER") control: GamepadControlSpec) {
        if (hidState.hat != 8) {
            hidState = GamepadRuntimeCore.withHat(hidState, 8)
            sendAction(HidAction.GamepadUpdate(hidState))
        }
    }

    // ------------------------------------------------------------------
    // Editor mutations
    // ------------------------------------------------------------------

    fun selectedControl(): GamepadControlSpec? =
        _draft.value?.second?.controls?.firstOrNull { it.id == _selectedId.value }

    fun select(id: String?) {
        if (!_previewMode.value) _selectedId.value = id
    }

    fun togglePreview() {
        _previewMode.value = !_previewMode.value
        if (_previewMode.value) {
            _selectedId.value = null
        }
        // BUG FIX: neutralize on BOTH transitions — leaving preview with a
        // button held must not leave it pressed on the host.
        neutralizeHid()
    }

    fun beginGesture() = pushUndo()

    private fun pushUndo() {
        _draft.value?.second?.let {
            undoStack.addLast(it)
            while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
            _canUndo.value = true
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _draft.value = _draft.value?.first to prev
        _canUndo.value = undoStack.isNotEmpty()
    }

    private fun mutate(withUndo: Boolean = true, transform: (GamepadLayoutSpec) -> GamepadLayoutSpec) {
        val (id, spec) = _draft.value ?: return
        if (withUndo) pushUndo()
        _draft.value = id to transform(spec).sanitized()
    }

    fun rename(name: String) = mutate { it.copy(name = name.take(GamepadLayoutSpec.NAME_MAX)) }

    fun addControl(type: GamepadControlType) {
        val spec = _draft.value?.second ?: return
        if (spec.controls.size >= GamepadLayoutSpec.MAX_CONTROLS) return
        val newId = UUID.randomUUID().toString()
        val defaults = when (type) {
            GamepadControlType.BUTTON -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.44f, 0.40f, 0.12f, 0.20f),
                label = "A", buttonIndex = 0
            )
            GamepadControlType.TRIGGER -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.40f, 0.05f, 0.18f, 0.14f),
                shape = com.bluepilot.remote.model.gamepad.ControlShape.ROUNDED,
                label = "L1", buttonIndex = 4, color = 0xFF1A2238
            )
            GamepadControlType.STICK -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.10f, 0.35f, 0.22f, 0.40f),
                stickSide = StickSide.LEFT
            )
            GamepadControlType.DPAD -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.30f, 0.50f, 0.18f, 0.34f),
                color = 0xFF1A2238
            )
        }
        mutate { it.copy(controls = it.controls + defaults.sanitized()) }
        _selectedId.value = newId
    }

    fun removeSelected() {
        val id = _selectedId.value ?: return
        mutate { spec -> spec.copy(controls = spec.controls.filterNot { it.id == id }) }
        _selectedId.value = null
    }

    fun updateSelected(withUndo: Boolean = true, transform: (GamepadControlSpec) -> GamepadControlSpec) {
        val id = _selectedId.value ?: return
        mutate(withUndo) { spec ->
            spec.copy(controls = spec.controls.map {
                if (it.id == id) transform(it).sanitized() else it
            })
        }
    }

    /** Continuous drag/resize — undo captured once at gesture start. */
    fun moveSelected(x: Float, y: Float) = updateSelected(withUndo = false) {
        it.copy(frame = it.frame.copy(x = x, y = y).sanitized())
    }

    fun resizeSelected(w: Float, h: Float) = updateSelected(withUndo = false) {
        it.copy(frame = it.frame.copy(
            w = w.coerceAtLeast(WidgetFrame.MIN_SIZE),
            h = h.coerceAtLeast(WidgetFrame.MIN_SIZE)
        ).sanitized())
    }

    fun closeDraft() {
        _draft.value = null
        _selectedId.value = null
        _previewMode.value = false
    }

    fun saveDraft() {
        val (rowId, spec) = _draft.value ?: return
        viewModelScope.launch {
            runCatching {
                if (draftIsBuiltIn) {
                    repository.save(null, spec.copy(name = spec.name + " (custom)"))
                } else {
                    repository.save(rowId, spec)
                }
            }.onSuccess {
                _message.value = "Gamepad saved ✓"
                _draft.value = null
            }.onFailure {
                Timber.e(it, "gamepad save failed")
                _message.value = "Save failed."
            }
        }
    }

    // ------------------------------------------------------------------
    // Import / export
    // ------------------------------------------------------------------

    fun requestExport(id: Long) {
        viewModelScope.launch {
            val profile = repository.byId(id)
            val json = repository.exportJson(id)
            if (profile != null && json != null) {
                val name = profile.spec.name
                    .replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "gamepad" }
                    .replace(' ', '_') + ".bpgamepad.json"
                _exportPayload.value = name to json
            } else _message.value = "Export failed."
        }
    }

    fun consumeExport() { _exportPayload.value = null }

    fun importFromJson(raw: String?) {
        viewModelScope.launch {
            if (raw.isNullOrBlank()) { _message.value = "Import failed - empty file."; return@launch }
            val id = repository.importJson(raw)
            _message.value = if (id != null) "Gamepad imported ✓" else "Import failed - invalid file."
        }
    }

    fun consumeMessage() { _message.value = null }
}
