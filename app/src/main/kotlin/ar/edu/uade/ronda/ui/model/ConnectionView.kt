package ar.edu.uade.ronda.ui.model

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.policy.ConnectionEvaluator

/**
 * El estado del enlace que ve la pantalla se recalcula con el mismo `now` que la antigüedad:
 * el del repositorio se refresca cada tanto y podría decir "Conectado" con datos de 1,7 s.
 * Se queda con el peor de los dos: el repositorio puede saber algo más (p. ej. enlace caído).
 */
object ConnectionView {
    private val evaluator = ConnectionEvaluator()

    fun effective(reported: ConnectionState, lastTelemetryAt: Long?, latencyMs: Int?, nowMs: Long): ConnectionState {
        if (lastTelemetryAt == null) return reported
        val derived = evaluator.evaluate(lastTelemetryAt, latencyMs, nowMs)
        return if (derived.ordinal > reported.ordinal) derived else reported
    }
}
