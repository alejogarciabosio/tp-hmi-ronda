package ar.edu.uade.ronda.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class ControlViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    tickMs: Long = 500,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val base = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt,
        repository.activeIncident, repository.commands,
    ) { t, c, at, inc, cmds -> listOf(t, c, at, inc, cmds) }

    val uiState: StateFlow<ControlUiState> = combine(base, repository.availability, ticker) { _, _, _ -> current() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun current() = ControlMapper.map(
        ControlInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.activeIncident.value, repository.commands.value, repository.availability.value, clock.nowMs(),
        ),
    )

    /** Solo se envía si el botón está habilitado o se puede reintentar (rechazada / sin respuesta). */
    private fun sendIf(button: (ControlUiState) -> CommandButtonUi, type: CommandType, params: CommandParams = CommandParams()) {
        val state = button(current()).state
        if (state == CommandUiState.DISABLED || state == CommandUiState.PENDING) return
        repository.send(type, params)
    }

    fun onPause() = sendIf({ it.pause }, CommandType.PAUSE_MISSION)

    fun onResume() = sendIf({ it.resume }, CommandType.RESUME_MISSION)

    fun onReturnToBase() =
        sendIf({ it.returnToBase }, CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))

    fun onSafePoint() =
        sendIf({ it.safePoint }, CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.DESCANSO_ESCALERA_E))

    fun onSkipCheckpoint() =
        sendIf({ it.skipCheckpoint }, CommandType.RESUME_MISSION, CommandParams(decision = IncidentDecision.SKIP_CHECKPOINT))

    /** La pantalla lo llama recién cuando se completó el mantener apretado. */
    fun onCancelMission() = sendIf({ it.cancel }, CommandType.CANCEL_MISSION)

    fun onOperationalStop() {
        if (current().stop.enabled) repository.send(CommandType.OPERATIONAL_STOP)
    }
}
