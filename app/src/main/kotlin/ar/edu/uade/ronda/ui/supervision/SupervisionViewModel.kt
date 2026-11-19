package ar.edu.uade.ronda.ui.supervision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.session.ControlSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * Supervisión: deriva un único [SupervisionUiState] del repositorio y traduce los eventos
 * de la pantalla en comandos. Solo conoce la interfaz [RobotRepository].
 */
class SupervisionViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    private val session: ControlSession,
    tickMs: Long = 500,
) : ViewModel() {

    private val selectedFloor = MutableStateFlow<Int?>(null)
    private val dismissedIncidentId = MutableStateFlow<String?>(null)
    private val notice = MutableStateFlow<String?>(null)

    /** Hace que las antigüedades ("hace 12 s") sigan corriendo aunque no llegue telemetría. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val checkpointLog: Flow<CheckpointLog> =
        repository.telemetry.filterNotNull().scan(CheckpointLog()) { log, t -> log.next(t) }

    private val fromRepository = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt,
        repository.activeIncident, repository.commands,
    ) { telemetry, connection, lastAt, incident, commands ->
        Inputs1(telemetry, connection, lastAt, incident, commands)
    }

    private val local = combine(
        repository.availability, session.controlledId, checkpointLog, selectedFloor, dismissedIncidentId, ticker,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Inputs2(ControlSession.gate(values[0] as Map<CommandType, CommandAvailability>, values[1] as String?),
            values[2] as CheckpointLog, values[3] as Int?, values[4] as String?)
    }

    val uiState: StateFlow<SupervisionUiState> = combine(fromRepository, local, notice) { a, b, message ->
        SupervisionMapper.map(
            SupervisionInputs(
                a.telemetry, a.connection, a.lastAt, a.incident, a.commands,
                b.availability, b.log, b.floor, b.dismissed, clock.nowMs(),
            ),
        ).copy(notice = message)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        SupervisionMapper.map(
            SupervisionInputs(
                repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
                repository.activeIncident.value, repository.commands.value,
                ControlSession.gate(repository.availability.value, session.controlledId.value),
                CheckpointLog(), null, null, clock.nowMs(),
            ),
        ),
    )

    fun onPause() { repository.send(CommandType.PAUSE_MISSION) }

    fun onResume() { repository.send(CommandType.RESUME_MISSION) }

    fun onReturnToBase() {
        repository.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))
    }

    /** La pantalla lo llama recién cuando se completó el mantener apretado. */
    fun onOperationalStop() { repository.send(CommandType.OPERATIONAL_STOP) }

    fun onSelectFloor(floor: Int) { selectedFloor.value = floor }

    fun onDismissDecision() { dismissedIncidentId.value = repository.activeIncident.value?.id }

    fun onNoticeDismissed() { notice.value = null }

    fun onReopenDecision() { dismissedIncidentId.value = null }

    fun onDecisionOption(optionId: String) {
        notice.value = null
        val incident = repository.activeIncident.value ?: run {
            notice.value = "La decisión ya no está pendiente: el robot no espera una respuesta."
            return
        }
        val params = when (optionId) {
            OPTION_WAIT -> CommandParams(decision = IncidentDecision.WAIT_30S)
            OPTION_SKIP -> CommandParams(decision = IncidentDecision.SKIP_CHECKPOINT)
            OPTION_RETURN -> CommandParams(
                safePointId = incident.options.firstOrNull { it.id == optionId }?.safePointId ?: SafePointId.BASE,
            )
            OPTION_AUTHORIZED, OPTION_GUARD, OPTION_CONTINUE -> {
                // La persona detectada llega como alerta: la resolución se envía al reconocerla.
                val alert = repository.telemetry.value?.unacknowledgedAlerts
                    ?.firstOrNull { it.type == AlertType.PERSON_DETECTED }
                if (alert == null) {
                    notice.value = "No se envió la orden: no hay una alerta de persona sin reconocer. Revisá Alertas."
                    return
                }
                CommandParams(
                    alertId = alert.id,
                    resolution = when (optionId) {
                        OPTION_AUTHORIZED -> AlertResolution.AUTHORIZED_PERSON
                        OPTION_GUARD -> AlertResolution.SEND_GUARD
                        else -> AlertResolution.CONTINUE_ROUND
                    },
                )
            }
            else -> {
                notice.value = "Opción de decisión desconocida: no se envió ninguna orden."
                return
            }
        }
        repository.send(commandFor(optionId), params)
    }

    private data class Inputs1(
        val telemetry: ar.edu.uade.ronda.core.model.RobotTelemetry?,
        val connection: ar.edu.uade.ronda.core.model.ConnectionState,
        val lastAt: Long?,
        val incident: ar.edu.uade.ronda.core.model.Incident?,
        val commands: List<ar.edu.uade.ronda.core.repository.TrackedCommand>,
    )

    private data class Inputs2(
        val availability: Map<CommandType, ar.edu.uade.ronda.core.policy.CommandAvailability>,
        val log: CheckpointLog,
        val floor: Int?,
        val dismissed: String?,
    )

    companion object {
        const val OPTION_WAIT = "WAIT_30S"
        const val OPTION_SKIP = "SKIP_CHECKPOINT"
        const val OPTION_RETURN = "RETURN_TO_SAFE_POINT"
        const val OPTION_AUTHORIZED = "AUTHORIZED_PERSON"
        const val OPTION_GUARD = "SEND_GUARD"
        const val OPTION_CONTINUE = "CONTINUE_ROUND"

        /** Comando que lleva cada opción de una decisión (contrato propuesto, plan §3). */
        fun commandFor(optionId: String): CommandType = when (optionId) {
            OPTION_WAIT, OPTION_SKIP -> CommandType.RESUME_MISSION
            OPTION_RETURN -> CommandType.RETURN_TO_SAFE_POINT
            else -> CommandType.ACKNOWLEDGE_ALERT
        }
    }
}
