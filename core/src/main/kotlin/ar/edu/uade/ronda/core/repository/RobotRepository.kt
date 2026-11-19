package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.policy.CommandAvailability
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo único que los ViewModels conocen del robot. Desacopla telemetría, comandos y eventos
 * de la implementación (simulada hoy; un gateway real mañana).
 */
interface RobotRepository {
    /** Última telemetría recibida; null hasta que llega la primera. Puede ser vieja: mirar [connection]. */
    val telemetry: StateFlow<RobotTelemetry?>

    /** Estado del enlace derivado de la antigüedad de la última telemetría y de la latencia. */
    val connection: StateFlow<ConnectionState>

    /** Hora (epoch ms) en que llegó la última telemetría. */
    val lastTelemetryAt: StateFlow<Long?>

    /** Alertas e incidentes que informa el robot, con su timestamp original. */
    val events: SharedFlow<RobotEvent>

    /** Decisión que el robot espera del operador, mientras esté en REQUIERE_DECISION. */
    val activeIncident: StateFlow<Incident?>

    /** Comandos enviados, del más viejo al más nuevo, con su estado actual. */
    val commands: StateFlow<List<TrackedCommand>>

    /** Habilitación de cada comando según `CommandPolicy`, para la ronda completa. */
    val availability: StateFlow<Map<CommandType, CommandAvailability>>

    /**
     * Envía el comando y devuelve su `messageId`; el estado se sigue en [commands].
     * No valida contra la política: la UI ya deshabilita lo que no corresponde y el robot
     * rechaza lo que no puede hacer.
     */
    fun send(type: CommandType, params: CommandParams = CommandParams()): String
}
