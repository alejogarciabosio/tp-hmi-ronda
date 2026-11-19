package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cómo terminó un punto de control de la ronda. */
enum class CheckpointOutcome { VERIFIED, WITH_ISSUE, SKIPPED }

data class CheckpointResolution(val outcome: CheckpointOutcome, val at: Long)

/**
 * Recuerda cómo terminó cada punto de control. El robot informa cuántos verificó, no cuáles:
 * se deduce al ver avanzar el índice y comparar los contadores de la telemetría.
 * Una misión nueva (otro `mission.id`) o la vuelta a la base sin misión arrancan de cero.
 */
data class CheckpointLog(
    val missionId: String? = null,
    val lastIndex: Int = 0,
    val lastVerified: Int = 0,
    val lastIssues: Int = 0,
    val resolved: Map<Int, CheckpointResolution> = emptyMap(),
) {
    fun next(t: RobotTelemetry): CheckpointLog {
        val m = t.mission ?: return CheckpointLog()
        val verified = t.service.checkpointsVerified
        val issues = t.service.checkpointsWithIssues
        if (m.id != missionId) return CheckpointLog(m.id, m.checkpointIndex, verified, issues)
        val advanced = m.checkpointIndex > lastIndex
        val finishedLast = t.operationalState == OperationalState.COMPLETADA && lastIndex !in resolved
        val newResolved = if (advanced || finishedLast) {
            val outcome = when {
                verified <= lastVerified -> CheckpointOutcome.SKIPPED
                issues > lastIssues -> CheckpointOutcome.WITH_ISSUE
                else -> CheckpointOutcome.VERIFIED
            }
            resolved + (lastIndex to CheckpointResolution(outcome, t.timestamp))
        } else resolved
        return CheckpointLog(m.id, m.checkpointIndex, verified, issues, newResolved)
    }
}

/**
 * Historia de puntos de control de la ronda en curso. Vive con la aplicación, no con una
 * pantalla: recolecta la telemetría desde que se crea, así que salir de Supervisión o
 * recrear su ViewModel no borra los puntos ya verificados.
 */
class CheckpointHistory(telemetry: Flow<RobotTelemetry?>, scope: CoroutineScope) {
    private val _log = MutableStateFlow(CheckpointLog())
    val log: StateFlow<CheckpointLog> = _log.asStateFlow()

    init {
        // UNDISPATCHED: queda suscripto antes de que llegue la primera telemetría.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            telemetry.filterNotNull().collect { t -> _log.update { it.next(t) } }
        }
    }
}
