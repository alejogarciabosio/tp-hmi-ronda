package ar.edu.uade.ronda.ui.alertas

import ar.edu.uade.ronda.ui.model.Etiquetas
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.SnapshotUi
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class AlertasInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val incident: Incident?,
    val commands: List<TrackedCommand>,
    val availability: Map<CommandType, CommandAvailability>,
    val nowMs: Long,
)

internal object AlertasMapper {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun map(i: AlertasInputs, notice: String? = null): AlertasUiState {
        val sup = SupervisionMapper.map(
            SupervisionInputs(
                i.telemetry, i.connection, i.lastTelemetryAt, i.incident, i.commands,
                i.availability, CheckpointLog(), null, null, i.nowMs,
            ),
        )
        val alerts = i.telemetry?.alerts.orEmpty()
        val active = alerts.filter { !it.acknowledged }
            .sortedWith(compareByDescending<Alert> { it.severity.ordinal }.thenByDescending { it.raisedAt })
        val done = alerts.filter { it.acknowledged }.sortedByDescending { it.raisedAt }
        return AlertasUiState(
            strip = sup.strip,
            activeTitle = if (active.isEmpty()) "ACTIVAS" else "ACTIVAS · ${active.size} SIN RECONOCER",
            active = active.map { card(it, i) },
            acknowledged = done.map { card(it, i) },
            notice = notice,
        )
    }

    private fun card(a: Alert, i: AlertasInputs): AlertCardUi {
        val level = when (a.severity) {
            Severity.CRITICAL -> StatusLevel.ERROR
            Severity.WARNING -> StatusLevel.WARNING
            Severity.INFO -> StatusLevel.OK
        }
        val sev = when (a.severity) {
            Severity.CRITICAL -> "CRÍTICA"
            Severity.WARNING -> "ADVERTENCIA"
            Severity.INFO -> "INFORMACIÓN"
        }
        val opensDecision = !a.acknowledged && a.type == AlertType.PERSON_DETECTED &&
            i.incident?.type == IncidentType.PERSON_IN_RESTRICTED_ZONE
        val title = titleFor(a.type) + (i.incident?.takeIf { opensDecision }?.let { " · PC${it.checkpointIndex}" } ?: "")
        return AlertCardUi(
            id = a.id,
            level = if (i.connection.isStale && !a.acknowledged) StatusLevel.STALE else level,
            tag = "$sev · ${if (a.acknowledged) "RECONOCIDA" else "SIN RECONOCER"}",
            title = title,
            message = a.message,
            time = timeFormat.format(Instant.ofEpochMilli(a.raisedAt)),
            code = Etiquetas.alerta(a.type),
            // La foto es de una persona o de una zona: solo mientras el incidente sigue activo y la alerta sin reconocer.
            snapshot = a.snapshot?.takeIf { !a.acknowledged && i.incident != null }?.let {
                val caption = if (i.connection.isStale) "Imagen del momento de la alerta"
                else "Cámara frontal · ${SupervisionMapper.ageText(i.nowMs - it.capturedAt)}"
                SnapshotUi(it.id, it.data, caption, "Foto de la cámara frontal del robot: ${titleFor(a.type)}")
            },
            acknowledge = if (a.acknowledged || opensDecision) null else acknowledgeButton(a, i),
            opensDecision = opensDecision,
        )
    }

    private fun acknowledgeButton(a: Alert, i: AlertasInputs): CommandButtonUi {
        val pending = i.commands.lastOrNull {
            it.command.type == CommandType.ACKNOWLEDGE_ALERT && it.command.params.alertId == a.id
        }
        if (pending?.status == CommandStatus.PENDING) {
            return CommandButtonUi("Reconocer", CommandKind.SECONDARY, CommandUiState.PENDING, "Esperando respuesta del robot")
        }
        val av = i.availability[CommandType.ACKNOWLEDGE_ALERT] ?: CommandAvailability.disabled("Sin información de disponibilidad")
        if (!av.enabled) return CommandButtonUi("Reconocer", CommandKind.SECONDARY, CommandUiState.DISABLED, av.reason)
        return when (pending?.status) {
            CommandStatus.REJECTED -> CommandButtonUi("Reconocer", CommandKind.SECONDARY, CommandUiState.REJECTED,
                "Rechazada: ${pending?.reasonText ?: "motivo no informado"}")
            CommandStatus.NO_RESPONSE -> CommandButtonUi("Reconocer", CommandKind.SECONDARY, CommandUiState.NO_RESPONSE,
                "Sin respuesta: no se sabe si se ejecutó")
            else -> CommandButtonUi("Reconocer", CommandKind.SECONDARY)
        }
    }

    fun titleFor(type: AlertType) = when (type) {
        AlertType.OBSTACLE -> "Obstáculo en el recorrido"
        AlertType.LOW_BATTERY -> "Batería baja"
        AlertType.PERSON_DETECTED -> "Persona en zona restringida"
        AlertType.TECH_ROOM_TEMPERATURE -> "Temperatura de sala técnica"
        AlertType.EXIT_BLOCKED -> "Salida de emergencia obstruida"
        AlertType.LINK_DEGRADED -> "Enlace degradado"
        AlertType.ROBOT_FAULT -> "Falla del robot"
    }
}
