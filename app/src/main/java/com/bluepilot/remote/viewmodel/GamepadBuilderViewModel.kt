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
    private val haptics: com.bluepilot.remote.haptics.Haptics,
    private val versionStore: com.bluepilot.remote.data.gamepad.GamepadVersionStore,
    private val settingsStore: com.bluepilot.remote.domain.SettingsStore
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
    private var decayJob: kotlinx.coroutines.Job? = null
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
        decayJob?.cancel()
        decayJob = viewModelScope.launch {
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
        decayJob?.cancel(); decayJob = null
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

    /** SECTION 10.1 - last pressed control label (debug/testing aid). */
    private val _lastPressed = MutableStateFlow("")
    val lastPressed: StateFlow<String> = _lastPressed.asStateFlow()

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
            // BUGFIX: cancel any running turbo before switching profiles so a
            // held rapid-fire button can't keep firing into the new profile.
            cancelRuntimeJobs()
            hidState = GamepadSnapshot()
            sendAction(HidAction.GamepadUpdate(hidState))
            _playing.value = repository.byId(id)
            recordRecent(id)   // ADV S3 — recents quick-access row
        }
    }

    /** ADV S1 — cancel turbo/arrow jobs + clear latched toggle states. */
    private fun cancelRuntimeJobs() {
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        arrowJobs.values.forEach { it.cancel() }
        arrowJobs.clear()
        toggleStates.clear()
        lastTapAt.clear()
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
        cancelRuntimeJobs()
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

    // ADV S1 — toggle-button latched states per control id.
    private val toggleStates = mutableMapOf<String, Boolean>()
    // ADV S1 — multi-tap: last tap timestamp per control id.
    private val lastTapAt = mutableMapOf<String, Long>()
    // ADV S1 — arrow hold-to-repeat jobs.
    private val arrowJobs = mutableMapOf<String, Job>()

    /** ADV S1 — toggle button: tap flips latched ON/OFF state. */
    fun onToggleButton(control: GamepadControlSpec) {
        val latched = com.bluepilot.remote.domain.AdvancedControls
            .toggleAfterTap(toggleStates[control.id] ?: false)
        toggleStates[control.id] = latched
        hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, latched)
        sendAction(HidAction.GamepadUpdate(hidState))
        haptics.play(control.haptic)
    }

    /** ADV S1 — multi-tap: single tap = primary, double tap = secondary. */
    fun onMultiTap(control: GamepadControlSpec) {
        val now = System.currentTimeMillis()
        val isDouble = now - (lastTapAt[control.id] ?: 0L) <= control.multiTapWindowMs
        lastTapAt[control.id] = if (isDouble) 0L else now
        val index = if (isDouble) control.secondaryButtonIndex else control.buttonIndex
        viewModelScope.launch {
            hidState = GamepadRuntimeCore.withButton(hidState, index, true)
            sendAction(HidAction.GamepadUpdate(hidState))
            delay(40)
            hidState = GamepadRuntimeCore.withButton(hidState, index, false)
            sendAction(HidAction.GamepadUpdate(hidState))
        }
        haptics.play(control.haptic)
    }

    /** ADV S1 — radial wheel option chosen: tap that button index. */
    fun onRadialPick(control: GamepadControlSpec, index: Int) {
        viewModelScope.launch {
            hidState = GamepadRuntimeCore.withButton(hidState, index.coerceIn(0, 15), true)
            sendAction(HidAction.GamepadUpdate(hidState))
            delay(40)
            hidState = GamepadRuntimeCore.withButton(hidState, index.coerceIn(0, 15), false)
            sendAction(HidAction.GamepadUpdate(hidState))
        }
        haptics.play(control.haptic)
    }

    /** ADV S1 — independent arrow button: press/release one hat direction. */
    fun onArrow(control: GamepadControlSpec, pressed: Boolean) {
        arrowJobs.remove(control.id)?.cancel()
        val hat = if (control.diagonalOnly)
            com.bluepilot.remote.domain.AdvancedControls.diagonalOnly(control.arrowDirection.hat)
        else control.arrowDirection.hat
        if (pressed) {
            if (control.arrowRepeat) {
                // Hold-to-repeat: pulse direction/neutral at arrowRepeatRate.
                arrowJobs[control.id] = viewModelScope.launch {
                    val period = 1000L / control.arrowRepeatRate.coerceIn(2, 30)
                    while (true) {
                        hidState = GamepadRuntimeCore.withHat(hidState, hat)
                        sendAction(HidAction.GamepadUpdate(hidState))
                        delay(period / 2)
                        hidState = GamepadRuntimeCore.withHat(hidState, 8)
                        sendAction(HidAction.GamepadUpdate(hidState))
                        delay(period / 2)
                    }
                }
            } else {
                hidState = GamepadRuntimeCore.withHat(hidState, hat)
                sendAction(HidAction.GamepadUpdate(hidState))
            }
            haptics.play(control.haptic)
        } else {
            if (hidState.hat != 8) {
                hidState = GamepadRuntimeCore.withHat(hidState, 8)
                sendAction(HidAction.GamepadUpdate(hidState))
            }
        }
    }

    /** ADV S1 — combo zone: zone 0 = buttonIndex (bumper), 1 = comboSecondIndex (trigger). */
    fun onComboZone(control: GamepadControlSpec, zone: Int, pressed: Boolean) {
        val index = if (zone == 0) control.buttonIndex else control.comboSecondIndex
        hidState = GamepadRuntimeCore.withButton(hidState, index, pressed)
        sendAction(HidAction.GamepadUpdate(hidState))
        if (pressed) haptics.play(control.haptic)
    }

    /** ADV S1 — stick click (L3/R3): long-press on the stick knob. */
    fun onStickClick(control: GamepadControlSpec, pressed: Boolean) {
        if (control.stickClickIndex < 0) return
        hidState = GamepadRuntimeCore.withButton(hidState, control.stickClickIndex, pressed)
        sendAction(HidAction.GamepadUpdate(hidState))
        if (pressed) haptics.play(control.haptic)
    }

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
        // ADV S1 pipeline: outer range → square gate → curve+sens → anti-deadzone → deadzone.
        val adv = com.bluepilot.remote.domain.AdvancedControls
        var px = adv.outerRange(x, control.outerRange)
        var py = adv.outerRange(y, control.outerRange)
        if (control.stickGate == com.bluepilot.remote.model.gamepad.StickGate.SQUARE) {
            val (gx, gy) = adv.squareGate(px, py)
            px = gx; py = gy
        }
        // SECTION 5 — response curve + per-profile sensitivity before dead zone.
        val sens = ((_playing.value ?: run { null })?.spec?.stickSensitivity
            ?: draft.value?.second?.stickSensitivity ?: 70) / 100f * 1.4f + 0.3f
        var cx = ResponseCurves.apply(px, control.curve) * sens
        var cy = ResponseCurves.apply(py, control.curve) * sens
        if (control.antiDeadZone > 0) {
            cx = adv.antiDeadZone(cx.coerceIn(-1f, 1f), control.antiDeadZone)
            cy = adv.antiDeadZone(cy.coerceIn(-1f, 1f), control.antiDeadZone)
        }
        hidState = GamepadRuntimeCore.withStick(
            hidState, control.stickSide,
            cx.coerceIn(-1f, 1f), cy.coerceIn(-1f, 1f), control.deadZone
        )
        sendAction(HidAction.GamepadUpdate(hidState))
    }

    fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) {
        // ADV S1 — circular style = continuous 8-way; diagonal-only filter.
        var hat = if (control.dpadStyle == com.bluepilot.remote.model.gamepad.DpadStyle.CIRCULAR) {
            com.bluepilot.remote.domain.AdvancedControls.circularHat(dx, dy)
        } else {
            GamepadRuntimeCore.hatFromTouch(dx, dy, control.eightWay)
        }
        if (control.diagonalOnly) {
            hat = com.bluepilot.remote.domain.AdvancedControls.diagonalOnly(hat)
        }
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
            // ADV S1 — new control types.
            GamepadControlType.ARROW -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.46f, 0.62f, 0.09f, 0.16f),
                label = "▲", color = 0xFF1A2238,
                arrowDirection = com.bluepilot.remote.model.gamepad.ArrowDirection.UP
            )
            GamepadControlType.COMBO -> GamepadControlSpec(
                id = newId, type = type, frame = WidgetFrame(0.02f, 0.04f, 0.16f, 0.26f),
                shape = com.bluepilot.remote.model.gamepad.ControlShape.ROUNDED,
                label = "L", buttonIndex = 4, comboSecondIndex = 6, color = 0xFF1A2238
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
        // ADV S2 — magnetic grid snap while dragging.
        val grid = _draft.value?.second?.gridSize ?: 0f
        it.copy(frame = it.frame.copy(
            x = com.bluepilot.remote.domain.LayoutIntelligence.snap(x, grid),
            y = com.bluepilot.remote.domain.LayoutIntelligence.snap(y, grid)
        ).sanitized())
    }

    // ------------------------------------------------------------------
    // ADV SECTION 2 — layout intelligence
    // ------------------------------------------------------------------

    fun setGridSize(size: Float) = mutate(withUndo = false) { it.copy(gridSize = size.coerceIn(0f, 0.25f)) }

    /** Symmetry tool: mirrored copy of the selected control on the other side. */
    fun mirrorSelected() {
        val control = selectedControl() ?: return
        val spec = _draft.value?.second ?: return
        if (spec.controls.size >= GamepadLayoutSpec.MAX_CONTROLS) return
        val newId = UUID.randomUUID().toString()
        mutate { it.copy(controls = it.controls +
            com.bluepilot.remote.domain.LayoutIntelligence.mirrorCopy(control, newId)) }
        _selectedId.value = newId
    }

    /** One-tap handedness flip of the whole layout. */
    fun mirrorWholeLayout() =
        mutate { com.bluepilot.remote.domain.LayoutIntelligence.mirrorLayout(it) }

    /** Live spacing warnings (mis-tap risk pairs). */
    fun spacingWarnings(): List<com.bluepilot.remote.domain.LayoutIntelligence.SpacingWarning> =
        _draft.value?.second?.let {
            com.bluepilot.remote.domain.LayoutIntelligence.spacingWarnings(it)
        } ?: emptyList()

    /** Alignment guides for the control being dragged. */
    fun alignmentGuides(id: String): com.bluepilot.remote.domain.LayoutIntelligence.AlignmentGuides? =
        _draft.value?.second?.let {
            com.bluepilot.remote.domain.LayoutIntelligence.alignmentGuides(it, id)
        }

    // Thumb-reach heatmap overlay state (editor only, not persisted).
    private val _heatmapGrip = MutableStateFlow<com.bluepilot.remote.domain.LayoutIntelligence.GripStyle?>(null)
    val heatmapGrip: StateFlow<com.bluepilot.remote.domain.LayoutIntelligence.GripStyle?> = _heatmapGrip.asStateFlow()
    fun setHeatmapGrip(grip: com.bluepilot.remote.domain.LayoutIntelligence.GripStyle?) { _heatmapGrip.value = grip }

    // ------------------------------------------------------------------
    // ADV S2 — shift layer (Fn-style second layer)
    // ------------------------------------------------------------------

    /** Which layer is live while playing: 0 = base, 1 = shift held. */
    private val _activeLayer = MutableStateFlow(0)
    val activeLayer: StateFlow<Int> = _activeLayer.asStateFlow()

    fun setSelectedLayer(layer: Int) = updateSelected { it.copy(layer = layer.coerceIn(0, 1)) }

    fun setSelectedAsShift() {
        val id = _selectedId.value ?: return
        mutate { it.copy(shiftControlId = if (it.shiftControlId == id) "" else id) }
    }

    /** Called by the renderer when the shift control is pressed/released. */
    fun onShift(pressed: Boolean) { _activeLayer.value = if (pressed) 1 else 0 }

    // ------------------------------------------------------------------
    // ADV S2 — Test Mode: real HID keycode display + measured latency
    // ------------------------------------------------------------------

    data class TestEvent(
        val label: String,
        /** Human-readable wire description, e.g. "button 3 ↓ (bit 2, btnLo=0x04)". */
        val wire: String,
        /** REAL measured enqueue→post-send duration in microseconds. */
        val latencyUs: Long,
        val at: Long = System.currentTimeMillis()
    )

    private val _testMode = MutableStateFlow(false)
    val testMode: StateFlow<Boolean> = _testMode.asStateFlow()
    fun setTestMode(on: Boolean) { _testMode.value = on; if (!on) _testEvents.value = emptyList() }

    private val _testEvents = MutableStateFlow<List<TestEvent>>(emptyList())
    val testEvents: StateFlow<List<TestEvent>> = _testEvents.asStateFlow()

    /**
     * Records a REAL test event: wire bytes come from the actual
     * HidReportBuilder output for the current snapshot; latency is the
     * measured time for the send call to be dispatched (System.nanoTime
     * around the sendAction handoff — real, not simulated).
     */
    private fun recordTestEvent(label: String, describe: String) {
        if (!_testMode.value) return
        val report = com.bluepilot.remote.hid.HidReportBuilder.gamepad(hidState)
        val hex = report.joinToString(" ") { String.format("%02X", it) }
        val t0 = System.nanoTime()
        sendAction(HidAction.GamepadUpdate(hidState))
        val dispatchUs = (System.nanoTime() - t0) / 1000
        _testEvents.value = (listOf(
            TestEvent(label, "$describe  [${hex}]", dispatchUs)
        ) + _testEvents.value).take(30)
    }

    fun testPress(control: GamepadControlSpec, pressed: Boolean) {
        hidState = GamepadRuntimeCore.withButton(hidState, control.buttonIndex, pressed)
        recordTestEvent(
            control.label.ifBlank { "B${control.buttonIndex + 1}" },
            "HID btn ${control.buttonIndex + 1} ${if (pressed) "↓" else "↑"}"
        )
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
                    // ADV S3 — versioning: archive the CURRENT stored spec
                    // before overwriting, so users can revert bad edits.
                    if (rowId != null) {
                        repository.byId(rowId)?.let { existing ->
                            if (existing.spec != spec) versionStore.push(rowId, existing.spec)
                        }
                    }
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
    // ADV SECTION 3 — versioning / A-B compare / favorites / tags / search
    // ------------------------------------------------------------------

    /** Version history of the profile being edited (newest first). */
    private val _versions = MutableStateFlow<List<GamepadLayoutSpec>>(emptyList())
    val versions: StateFlow<List<GamepadLayoutSpec>> = _versions.asStateFlow()

    fun loadVersions() {
        val rowId = _draft.value?.first ?: return
        _versions.value = versionStore.versions(rowId)
    }

    /** Revert the draft to a stored version (current draft goes to undo). */
    fun revertTo(version: GamepadLayoutSpec) {
        pushUndo()
        _draft.value = _draft.value?.first to version.sanitized()
        _message.value = "Reverted — Save to keep this version."
    }

    // A/B comparison: hold slot B; toggle swaps draft <-> B instantly.
    private val _abSlot = MutableStateFlow<GamepadLayoutSpec?>(null)
    val hasAbSlot: StateFlow<Boolean> get() = _hasAbSlot
    private val _hasAbSlot = MutableStateFlow(false)

    fun setAbSlot() {
        _abSlot.value = _draft.value?.second
        _hasAbSlot.value = _abSlot.value != null
        _message.value = "Layout stored as B — edit freely, then toggle A/B."
    }

    fun toggleAb() {
        val b = _abSlot.value ?: return
        val current = _draft.value?.second ?: return
        _abSlot.value = current
        _draft.value = _draft.value?.first to b
    }

    // ----- Favorites / recents (CSV in DataStore, same codec as themes) -----

    val appSettings: StateFlow<com.bluepilot.remote.model.AppSettings> =
        settingsStore.appSettings.stateIn(viewModelScope, SharingStarted.Eagerly, com.bluepilot.remote.model.AppSettings())

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            val cur = appSettings.value
            settingsStore.updateApp(cur.copy(
                favoriteGamepads = com.bluepilot.remote.ui.theme.ThemeListCodec.toggle(cur.favoriteGamepads, id.toString())
            ))
        }
    }

    fun isFavorite(id: Long): Boolean =
        com.bluepilot.remote.ui.theme.ThemeListCodec.contains(appSettings.value.favoriteGamepads, id.toString())

    private fun recordRecent(id: Long) {
        viewModelScope.launch {
            val cur = appSettings.value
            settingsStore.updateApp(cur.copy(
                recentGamepads = com.bluepilot.remote.ui.theme.ThemeListCodec.push(cur.recentGamepads, id.toString())
            ))
        }
    }

    // ----- Tags + search -----

    fun toggleTag(tag: String) = mutate {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    private val _profileQuery = MutableStateFlow("")
    val profileQuery: StateFlow<String> = _profileQuery.asStateFlow()
    fun setProfileQuery(q: String) { _profileQuery.value = q }

    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()
    fun setTagFilter(tag: String?) { _tagFilter.value = tag }

    /** Profiles filtered by search text + tag, favorites first. */
    val filteredProfiles: StateFlow<List<GamepadProfile>> =
        kotlinx.coroutines.flow.combine(
            profiles, _profileQuery, _tagFilter, appSettings
        ) { list, q, tag, settings ->
            val favs = com.bluepilot.remote.ui.theme.ThemeListCodec.decode(settings.favoriteGamepads)
            list.filter { p ->
                (q.isBlank() || p.spec.name.contains(q, true) ||
                    p.spec.tags.any { it.contains(q, true) }) &&
                    (tag == null || tag in p.spec.tags)
            }.sortedByDescending { it.id.toString() in favs }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recently played profiles resolved against the live list. */
    val recentProfiles: StateFlow<List<GamepadProfile>> =
        kotlinx.coroutines.flow.combine(profiles, appSettings) { list, settings ->
            com.bluepilot.remote.ui.theme.ThemeListCodec.decode(settings.recentGamepads)
                .mapNotNull { idStr -> list.firstOrNull { it.id.toString() == idStr } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * ADV S3 — contextual suggestion from the REAL connected host device:
     * classifies by name captured from the live HID connection state.
     */
    val suggestedTags: StateFlow<List<String>> = observeConnection()
        .map { state ->
            val name = (state as? com.bluepilot.remote.model.HidConnectionState.Connected)?.device?.name ?: ""
            if (name.isBlank()) emptyList()
            else com.bluepilot.remote.domain.ProfileSuggester.suggestedTags(
                com.bluepilot.remote.domain.ProfileSuggester.classify(0, name)
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    override fun onCleared() {
        // BUGFIX: never leave stuck inputs on the host when the VM dies.
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        motionJob?.cancel()
        if (hidState != GamepadSnapshot()) {
            sendAction(HidAction.GamepadUpdate(GamepadSnapshot()))
        }
        super.onCleared()
    }
}
