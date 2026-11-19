package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry

/** Estado de un comando en un instante; lo emite quien conoce el ciclo de vida de la orden. */
data class CommandOutcome(
    val command: Command,
    val status: CommandStatus,
    val at: Long,
    val detail: String? = null,
)

/** Funciones puras de dominio a filas del historial. */
object HistoryMapper {
    fun toRecord(event: RobotEvent, missionId: String? = null): EventRecord = when (event) {
        is RobotEvent.AlertRaised -> EventRecord(
            sourceId = event.messageId,
            timestamp = event.timestamp,
            kind = EventKind.ALERTA.name,
            type = event.alert.type.name,
            severity = event.severity.name,
            message = event.alert.message,
            missionId = missionId,
        )
        is RobotEvent.DecisionRequired -> EventRecord(
            sourceId = event.messageId,
            timestamp = event.timestamp,
            kind = EventKind.INCIDENTE.name,
            type = event.incident.type.name,
            severity = event.severity.name,
            message = event.incident.message,
            missionId = missionId,
        )
    }

    fun toRecord(outcome: CommandOutcome, missionId: String? = null): EventRecord {
        val c = outcome.command
        return EventRecord(
            sourceId = c.messageId,
            timestamp = c.issuedAt,
            kind = EventKind.COMANDO.name,
            type = c.type.name,
            severity = severity(outcome.status),
            message = outcome.detail ?: c.type.name,
            missionId = missionId,
            commandType = c.type.name,
            commandStatus = outcome.status.name,
        )
    }

    private fun severity(s: CommandStatus) = when (s) {
        CommandStatus.REJECTED, CommandStatus.NO_RESPONSE -> "WARNING"
        else -> "INFO"
    }

    fun resultOf(state: OperationalState): MissionResult? = when (state) {
        OperationalState.COMPLETADA -> MissionResult.COMPLETADA
        OperationalState.CANCELADA -> MissionResult.CANCELADA
        OperationalState.FALLA -> MissionResult.FALLA
        else -> null
    }
}

/**
 * Deriva el ciclo de vida de las misiones desde la telemetría. Mantiene a lo sumo una
 * misión abierta; cada llamada devuelve la fila a guardar o null si no cambió nada.
 */
class MissionTracker {
    private var open: MissionRecord? = null

    val currentMissionId: String? get() = open?.id

    fun onTelemetry(t: RobotTelemetry): MissionRecord? {
        val m = t.mission
        val result = HistoryMapper.resultOf(t.operationalState)
        val current = open
        if (current == null) {
            if (m == null || result != null) return null
            return MissionRecord(
                id = m.id, robotId = t.robotId, routeName = m.name, startedAt = t.timestamp,
                endedAt = null, result = MissionResult.EN_CURSO.name,
                checkpointsVerified = t.service.checkpointsVerified,
                checkpointsTotal = m.checkpointsTotal,
                anomalies = t.service.checkpointsWithIssues,
            ).also { open = it }
        }
        val updated = current.copy(
            checkpointsVerified = t.service.checkpointsVerified,
            checkpointsTotal = m?.checkpointsTotal ?: current.checkpointsTotal,
            anomalies = t.service.checkpointsWithIssues,
        )
        return when {
            result != null -> updated.copy(endedAt = t.timestamp, result = result.name).also { open = null }
            // La misión desapareció sin estado terminal (p. ej. vuelta a base): se cierra como cancelada.
            m == null || m.id != current.id ->
                current.copy(endedAt = t.timestamp, result = MissionResult.CANCELADA.name).also {
                    open = null
                }
            updated != current -> updated.also { open = it }
            else -> null
        }
    }
}
