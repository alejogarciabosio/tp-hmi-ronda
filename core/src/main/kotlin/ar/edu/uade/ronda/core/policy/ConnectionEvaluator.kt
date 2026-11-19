package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.ConnectionState

/**
 * Deriva el estado del enlace a partir de la antigüedad del último mensaje y de la latencia.
 * Lo viejo nunca cuenta como conectado: la antigüedad manda aunque la última latencia haya sido buena.
 */
class ConnectionEvaluator(
    private val degradedAfterMs: Long = 1_500,
    private val noDataAfterMs: Long = 3_000,
    private val disconnectedAfterMs: Long = 10_000,
    private val highLatencyMs: Int = 1_000,
) {
    /** `lastMessageAt` es null si todavía no llegó ningún mensaje. */
    fun evaluate(lastMessageAt: Long?, latencyMs: Int?, nowMs: Long): ConnectionState {
        if (lastMessageAt == null) return ConnectionState.DISCONNECTED
        val ageMs = (nowMs - lastMessageAt).coerceAtLeast(0)
        return when {
            ageMs > disconnectedAfterMs -> ConnectionState.DISCONNECTED
            ageMs > noDataAfterMs -> ConnectionState.NO_DATA
            ageMs > degradedAfterMs -> ConnectionState.DEGRADED
            latencyMs != null && latencyMs > highLatencyMs -> ConnectionState.DEGRADED
            else -> ConnectionState.CONNECTED
        }
    }
}
