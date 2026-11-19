package ar.edu.uade.ronda.data

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Evento del robot con la hora en que llegó a la app (puede ser mucho después de ocurrido). */
data class ReceivedEvent(val event: RobotEvent, val receivedAt: Long)

/** Lo que la pantalla de Desconexión necesita recordar aunque no esté abierta. */
data class LinkLog(
    /** Hora del último dato recibido antes del último corte; null si nunca hubo un corte. */
    val lastCutDataAt: Long? = null,
    /** Cuándo volvió el enlace tras el último corte; null si sigue cortado o nunca hubo corte. */
    val restoredAt: Long? = null,
    /** Eventos recibidos, con su hora de llegada, del más viejo al más nuevo. */
    val received: List<ReceivedEvent> = emptyList(),
)

/**
 * Sigue los cortes de enlace y la llegada de eventos con el alcance de la aplicación: así los
 * eventos atrasados y las órdenes del último corte se ven aunque Desconexión se abra después.
 */
class LinkMonitor(
    private val repository: RobotRepository,
    private val clock: Clock,
    scope: CoroutineScope,
    private val maxEvents: Int = 20,
) {
    private val _log = MutableStateFlow(LinkLog())
    val log: StateFlow<LinkLog> = _log.asStateFlow()

    init {
        scope.launch {
            repository.events.collect { e ->
                _log.update { it.copy(received = (it.received + ReceivedEvent(e, clock.nowMs())).takeLast(maxEvents)) }
            }
        }
        scope.launch {
            var wasStale = false
            repository.connection.collect { c ->
                val stale = c.isStale
                if (stale && !wasStale) {
                    _log.update { it.copy(lastCutDataAt = repository.lastTelemetryAt.value ?: clock.nowMs(), restoredAt = null) }
                } else if (!stale && wasStale) {
                    _log.update { it.copy(restoredAt = clock.nowMs()) }
                }
                wasStale = stale
            }
        }
    }
}
