package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.SafePointId

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

    /** [checkpointIndex] es el punto de la ruta en el que el operador dio la orden, si se conoce. */
    fun toRecord(outcome: CommandOutcome, missionId: String? = null, checkpointIndex: Int? = null): EventRecord {
        val c = outcome.command
        val option = commandOption(c)
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
            commandOption = option,
            checkpointIndex = if (option != null) checkpointIndex else null,
        )
    }

    /** La decisión del operador en crudo: resolución, opción del incidente o punto seguro distinto de la base. */
    fun commandOption(c: Command): String? = c.params.resolution?.name
        ?: c.params.decision?.name
        ?: c.params.safePointId?.takeIf { it != SafePointId.BASE }?.name

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

    /**
     * Misiones ya cerradas. Tras COMPLETADA o CANCELADA el robot sigue informando la misma misión
     * mientras regresa a la base: esa telemetría no debe reabrirla.
     */
    private val closedIds = ArrayDeque<String>()

    val currentMissionId: String? get() = open?.id

    /** Punto de la ruta que informó la última telemetría con misión. */
    @Volatile
    var currentCheckpointIndex: Int? = null
        private set

    fun onTelemetry(t: RobotTelemetry): MissionRecord? {
        val m = t.mission
        currentCheckpointIndex = m?.checkpointIndex
        val result = HistoryMapper.resultOf(t.operationalState)
        val current = open
        if (current == null) {
            if (m == null || result != null || m.id in closedIds) return null
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
            result != null -> updated.copy(endedAt = t.timestamp, result = result.name).also { close(current.id) }
            // La misión desapareció sin estado terminal (p. ej. vuelta a base): se cierra como cancelada.
            m == null || m.id != current.id ->
                current.copy(endedAt = t.timestamp, result = MissionResult.CANCELADA.name).also {
                    close(current.id)
                }
            updated != current -> updated.also { open = it }
            else -> null
        }
    }

    private fun close(id: String) {
        open = null
        closedIds.addLast(id)
        if (closedIds.size > MAX_CLOSED) closedIds.removeFirst()
    }

    private companion object {
        const val MAX_CLOSED = 32
    }
}
