package ar.edu.uade.ronda.data.local

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
    }

    suspend fun recordEvent(e: RobotEvent) {
        history.saveEvent(HistoryMapper.toRecord(e, tracker.currentMissionId))
    }

    suspend fun recordCommand(o: CommandOutcome) {
        history.saveEvent(HistoryMapper.toRecord(o, tracker.currentMissionId))
    }
}
