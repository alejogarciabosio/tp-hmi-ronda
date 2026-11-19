package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Traduce flujos de dominio a filas del historial. Recibe flujos (no un repositorio
 * concreto) para no depender de la implementación del robot.
 */
class EventRecorder(
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) {
    private val tracker = MissionTracker()

    /** Punto donde se dio cada orden: las actualizaciones de estado llegan con el robot ya más adelante. */
    private val checkpointAtSend = HashMap<String, Int?>()

    /**
     * Punto de control (número del edificio) del incidente abierto: es el que nombran las opciones
     * ("Saltar el PC5"). En rutas cortas no coincide con el progreso de la misión (3 de 3).
     */
    @Volatile
    private var incidentCheckpoint: Int? = null

    fun start(
        telemetry: Flow<RobotTelemetry>,
        events: Flow<RobotEvent>,
        commands: Flow<CommandOutcome>,
    ): List<Job> = listOf(
        scope.launch { telemetry.collect { recordTelemetry(it) } },
        scope.launch { events.collect { recordEvent(it) } },
        scope.launch { commands.collect { recordCommand(it) } },
    )

    suspend fun recordTelemetry(t: RobotTelemetry) {
        tracker.onTelemetry(t)?.let { history.saveMission(it) }
        if (t.operationalState == OperationalState.EN_RONDA || t.operationalState == OperationalState.EN_BASE) incidentCheckpoint = null
    }

    suspend fun recordEvent(e: RobotEvent) {
        if (e is RobotEvent.DecisionRequired) incidentCheckpoint = e.incident.checkpointIndex
        history.saveEvent(HistoryMapper.toRecord(e, tracker.currentMissionId))
    }

    suspend fun recordCommand(o: CommandOutcome) {
        val checkpoint = synchronized(checkpointAtSend) {
            if (checkpointAtSend.size > MAX_REMEMBERED) checkpointAtSend.clear()
            checkpointAtSend.getOrPut(o.command.messageId) { incidentCheckpoint ?: tracker.currentCheckpointIndex }
        }
        history.saveEvent(HistoryMapper.toRecord(o, tracker.currentMissionId, checkpoint))
    }

    private companion object {
        const val MAX_REMEMBERED = 200
    }
}
