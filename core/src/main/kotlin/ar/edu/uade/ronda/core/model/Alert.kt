package ar.edu.uade.ronda.core.model

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
)

enum class IncidentType { OBSTACLE, PERSON_IN_RESTRICTED_ZONE, BATTERY_RETURN }

data class IncidentOption(val id: String, val label: String)

/** Situación no prevista en la que el robot se detiene y espera una decisión del operador. */
data class Incident(
    val id: String,
    val type: IncidentType,
    val checkpointIndex: Int,
    val message: String,
    val options: List<IncidentOption>,
)
