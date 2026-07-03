package com.bluepilot.remote.domain

import com.bluepilot.remote.data.macros.MacroRepository
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Macro playback.
 *
 * [expand] is a pure function (unit-tested): MacroStep list → timed HidAction
 * plan. [play] runs the plan on a background scope; only one macro runs at a
 * time (a new play cancels the previous run — prevents runaway loops).
 */
@Singleton
class MacroEngine @Inject constructor(
    private val repository: MacroRepository,
    private val sendAction: SendHidActionUseCase
) {

    companion object {
        /** Pause inserted between consecutive steps so hosts keep up. */
        const val INTER_STEP_DELAY_MS = 30L

        /** One entry of the executable plan: optional wait, then optional action. */
        data class PlanEntry(val delayMs: Long, val action: HidAction?)

        /**
         * Pure expansion of macro steps into an executable plan.
         * Invalid steps (unknown mouse mask, empty text) are skipped, never fatal.
         */
        fun expand(spec: MacroSpec): List<PlanEntry> {
            val plan = mutableListOf<PlanEntry>()
            spec.sanitized().steps.forEach { step ->
                when (step) {
                    is MacroStep.KeyTap ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.KeyTap(step.key, step.modifiers))
                    is MacroStep.TypeText ->
                        if (step.text.isNotEmpty()) {
                            plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.TypeText(step.text))
                        }
                    is MacroStep.Media ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MediaTap(step.usage))
                    is MacroStep.MouseClick ->
                        WidgetActionMapper.maskToButton(step.buttonMask)?.let {
                            plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MouseClick(it))
                        }
                    is MacroStep.Delay ->
                        plan += PlanEntry(step.ms, null)
                }
            }
            return plan
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentRun: Job? = null

    /** Play a stored macro by id. Cancels any macro already running. */
    fun play(macroId: Long) {
        currentRun?.cancel()
        currentRun = scope.launch {
            val macro = runCatching { repository.byId(macroId) }.getOrNull()
            if (macro == null) {
                Timber.w("macro %d not found", macroId)
                return@launch
            }
            Timber.i("playing macro '%s' (%d steps)", macro.spec.name, macro.spec.steps.size)
            expand(macro.spec).forEach { entry ->
                if (entry.delayMs > 0) delay(entry.delayMs)
                entry.action?.let { sendAction(it) }
            }
        }
    }

    /** Play an unsaved spec (used by the macro editor's Test button). */
    fun playSpec(spec: MacroSpec) {
        currentRun?.cancel()
        currentRun = scope.launch {
            expand(spec).forEach { entry ->
                if (entry.delayMs > 0) delay(entry.delayMs)
                entry.action?.let { sendAction(it) }
            }
        }
    }

    fun stop() {
        currentRun?.cancel()
        currentRun = null
    }
}
