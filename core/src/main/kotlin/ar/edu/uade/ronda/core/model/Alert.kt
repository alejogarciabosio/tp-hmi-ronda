package ar.edu.uade.ronda.core.model

/**
 * Foto de la cámara frontal adjunta a un incidente. `data` es JPEG en base64 y puede faltar:
 * en ese caso la app resuelve la imagen por `id`.
 */
data class Snapshot(val id: String, val mediaType: String, val capturedAt: Long, val data: String? = null)

enum class Severity { INFO, WARNING, CRITICAL }

enum class AlertType {
    OBSTACLE,
    LOW_BATTERY,
    PERSON_DETECTED,
    TECH_ROOM_TEMPERATURE,
    EXIT_BLOCKED,
    LINK_DEGRADED,
    ROBOT_FAULT,
}

data class Alert(
    val id: String,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val raisedAt: Long,
    val acknowledged: Boolean,
    val snapshot: Snapshot? = null,
)

enum class IncidentType { OBSTACLE, PERSON_IN_RESTRICTED_ZONE, BATTERY_RETURN }

/** `safePointId` solo viaja en la opción de regresar: indica a qué punto seguro se vuelve. */
data class IncidentOption(val id: String, val label: String, val safePointId: SafePointId? = null)

/** Situación no prevista en la que el robot se detiene y espera una decisión del operador. */
data class Incident(
    val id: String,
    val type: IncidentType,
    val checkpointIndex: Int,
    val message: String,
    val options: List<IncidentOption>,
    val snapshot: Snapshot? = null,
)
