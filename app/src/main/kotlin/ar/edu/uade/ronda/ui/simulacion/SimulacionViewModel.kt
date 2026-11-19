package ar.edu.uade.ronda.ui.simulacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.repository.RobotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Rechazo armado: se considera consumido cuando aparece una orden posterior a la última que había. */
private data class RejectArm(val lastIdAtArm: String?)

class SimulacionViewModel(
    private val control: SimulationControl,
    private val repository: RobotRepository,
) : ViewModel() {

    private val rejectArm = MutableStateFlow<RejectArm?>(null)

    private val rejectArmed = combine(rejectArm, repository.commands) { arm, cmds ->
        arm != null && cmds.lastOrNull()?.id == arm.lastIdAtArm
    }

    val uiState: StateFlow<SimulacionUiState> =
        combine(control.settings, repository.telemetry, repository.activeIncident, rejectArmed) { s, t, inc, armed ->
            SimulacionMapper.map(SimulacionInputs(s, t, inc, armed))
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            SimulacionMapper.map(
                SimulacionInputs(control.settings.value, repository.telemetry.value, repository.activeIncident.value, false),
            ),
        )

    fun onLoadScenario(scenario: SimScenario) {
        // Cargar un escenario reinicia el simulador, que también descarta el rechazo armado.
        rejectArm.value = null
        control.loadScenario(scenario)
    }

    fun onToggle(toggle: SimToggle, on: Boolean) {
        when (toggle) {
            SimToggle.LINK_CUT -> control.setLinkUp(!on)
            SimToggle.REJECT_NEXT -> if (on && rejectArm.value == null) {
                rejectArm.value = RejectArm(repository.commands.value.lastOrNull()?.id)
                control.rejectNextCommand()
            }
            SimToggle.DELAY_RESPONSES -> control.setResponseDelayMs(if (on) SimulacionMapper.DELAY_MS else 0)
        }
    }

    fun onTimedCut() {
        if (control.settings.value.linkUp) control.cutLinkFor(SimulacionMapper.TIMED_CUT_MS)
    }

    fun onInject(injection: SimInjection) {
        when (injection) {
            SimInjection.OBSTACLE -> control.injectObstacle()
            SimInjection.PERSON -> control.injectPersonInRestrictedZone()
            SimInjection.FAULT -> control.injectFault()
        }
    }

    fun onBatteryStep(delta: Int) {
        val current = repository.telemetry.value?.battery?.percent ?: return
        control.setBatteryPercent((current + delta).coerceIn(0, 100))
    }

    fun onReset() {
        rejectArm.value = null
        control.reset()
    }
}
