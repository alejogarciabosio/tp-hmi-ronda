package ar.edu.uade.ronda.ui.supervision

import androidx.lifecycle.SavedStateHandle
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
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.session.ControlSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Supervisión: deriva un único [SupervisionUiState] del repositorio y traduce los eventos
 * de la pantalla en comandos. Solo conoce la interfaz [RobotRepository].
 *
 * La historia de puntos de control llega de afuera ([checkpoints], la arma `CheckpointHistory`
 * con el alcance de la aplicación): si se armara acá, se perdería al salir de la pantalla.
 */
class SupervisionViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    private val session: ControlSession,
    private val checkpoints: StateFlow<CheckpointLog>,
    tickMs: Long = 500,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Escala de tiempo del simulador; la arma AppContainer (la pantalla no ve el panel de simulación). */
    private val timeScale: StateFlow<Double> = MutableStateFlow(1.0),
) : ViewModel() {

    private val selectedFloor = MutableStateFlow<Int?>(null)

    /**
     * Hoja de decisión cerrada por el operador. Vive en [SavedStateHandle] para que volver a
     * Supervisión desde otra pestaña (o recrear la actividad) no la haga reaparecer.
     */
    private val dismissedIncidentId: StateFlow<String?> = savedState.getStateFlow(KEY_DISMISSED, null)

    private fun setDismissed(id: String?) { savedState[KEY_DISMISSED] = id }
    private val notice = MutableStateFlow<String?>(null)

    /** Hace que las antigüedades ("hace 12 s") sigan corriendo aunque no llegue telemetría. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val fromRepository = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt,
        repository.activeIncident, repository.commands,
    ) { telemetry, connection, lastAt, incident, commands ->
        Inputs1(telemetry, connection, lastAt, incident, commands)
    }

    private val local = combine(
        repository.availability, session.controlledId, checkpoints, selectedFloor, dismissedIncidentId, ticker,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Inputs2(ControlSession.gate(values[0] as Map<CommandType, CommandAvailability>, values[1] as String?),
            values[2] as CheckpointLog, values[3] as Int?, values[4] as String?)
    }

    val uiState: StateFlow<SupervisionUiState> = combine(fromRepository, local, notice, timeScale) { a, b, message, scale ->
        SupervisionMapper.map(
            SupervisionInputs(
                a.telemetry, a.connection, a.lastAt, a.incident, a.commands,
                b.availability, b.log, b.floor, b.dismissed, clock.nowMs(), scale,
            ),
        ).copy(notice = message)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        SupervisionMapper.map(
            SupervisionInputs(
                repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
                repository.activeIncident.value, repository.commands.value,
                ControlSession.gate(repository.availability.value, session.controlledId.value),
                checkpoints.value, null, null, clock.nowMs(), timeScale.value,
            ),
        ),
    )

    /** Estado recalculado en el momento: el observado puede tener medio segundo de atraso. */
    private fun current(): SupervisionUiState = SupervisionMapper.map(
        SupervisionInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.activeIncident.value, repository.commands.value,
            ControlSession.gate(repository.availability.value, session.controlledId.value),
            checkpoints.value, selectedFloor.value, dismissedIncidentId.value, clock.nowMs(),
        ),
    )

    /** Como `ControlViewModel.sendIf`: solo sale si el botón sigue habilitado o se puede reintentar. */
    private fun sendIf(button: (SupervisionUiState) -> CommandButtonUi, type: CommandType, params: CommandParams = CommandParams()) {
        val state = button(current()).state
        if (state == CommandUiState.DISABLED || state == CommandUiState.PENDING) return
        repository.send(type, params)
    }

    fun onPause() = sendIf({ it.pause }, CommandType.PAUSE_MISSION)

    fun onResume() = sendIf({ it.resume }, CommandType.RESUME_MISSION)

    fun onReturnToBase() =
        sendIf({ it.returnToBase }, CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))

    /** La pantalla lo llama recién cuando se completó el mantener apretado. No depende del control. */
    fun onOperationalStop() {
        if (current().stop.enabled) repository.send(CommandType.OPERATIONAL_STOP)
    }

    fun onSelectFloor(floor: Int) { selectedFloor.value = floor }

    fun onDismissDecision() { setDismissed(repository.activeIncident.value?.id) }

    fun onNoticeDismissed() { notice.value = null }

    fun onReopenDecision() { setDismissed(null) }

    fun onDecisionOption(optionId: String) {
        notice.value = null
        val incident = repository.activeIncident.value ?: run {
            notice.value = "La decisión ya no está pendiente: el robot no espera una respuesta."
            return
        }
        // Guarda ante el doble toque: la opción tiene que seguir habilitada en el estado actual.
        val option = current().decision?.options?.firstOrNull { it.id == optionId }
        if (option != null && !option.enabled) {
            if (!option.pending) notice.value = "No se envió la orden: ${option.reason ?: "la opción no está disponible"}."
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
        // Cualquier decisión cierra la hoja: no se vuelve a ofrecer lo mismo. Si el robot rechaza
        // la orden, el incidente sigue abierto y "Decidir cómo seguir" permite reabrirla.
        setDismissed(incident.id)
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
        private const val KEY_DISMISSED = "dismissedIncidentId"
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
