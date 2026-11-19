package ar.edu.uade.ronda.ui.operacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.ConnectionView
import ar.edu.uade.ronda.ui.supervision.StopButtonUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Lo que la barra fija de las pantallas operativas muestra en todo momento. */
data class OperationalBarUi(
    /** Detención operativa (software): depende solo de la política, nunca del control exclusivo. */
    val stop: StopButtonUi,
    /** "Persona cerca del robot" mientras haya una alerta de persona sin reconocer. */
    val personNearby: String? = null,
    /** Alertas sin reconocer, para la insignia de la pestaña Alertas. */
    val unacknowledgedAlerts: Int = 0,
)

internal object OperationalBarMapper {
    fun map(
        telemetry: RobotTelemetry?,
        reported: ConnectionState,
        lastTelemetryAt: Long?,
        availability: Map<CommandType, CommandAvailability>,
        nowMs: Long,
    ): OperationalBarUi {
        val connection = ConnectionView.effective(reported, lastTelemetryAt, telemetry?.latencyMs, nowMs)
        val stop = availability[CommandType.OPERATIONAL_STOP]
            ?.let { StopButtonUi(it.enabled, it.reason) }
            ?: StopButtonUi(false, "Sin información de disponibilidad")
        val unack = telemetry?.unacknowledgedAlerts.orEmpty()
        val person = unack.any { it.type == AlertType.PERSON_DETECTED }
        return OperationalBarUi(
            stop = stop,
            personNearby = if (!person) null
            else if (connection.isStale) "Persona cerca del robot (último dato, no vigente)"
            else "Persona cerca del robot",
            unacknowledgedAlerts = unack.size,
        )
    }
}

/**
 * Estado de la barra fija (detención operativa, persona cerca e insignia de alertas). Vive con
 * la actividad, no con cada pantalla: así la barra es la misma en todas las pestañas.
 */
class OperationalBarViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<OperationalBarUi> = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt, repository.availability,
    ) { _, _, _, _ -> current() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun current() = OperationalBarMapper.map(
        repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
        repository.availability.value, clock.nowMs(),
    )

    /** La barra lo llama recién cuando se completó el mantener apretado; se revalida en el momento. */
    fun onOperationalStop() {
        if (current().stop.enabled) repository.send(CommandType.OPERATIONAL_STOP)
    }
}
