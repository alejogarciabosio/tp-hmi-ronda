package ar.edu.uade.ronda.core.model

sealed interface RobotEvent {
    val messageId: String
    val robotId: String
    val timestamp: Long
    val severity: Severity

    data class AlertRaised(
        override val messageId: String,
        override val robotId: String,
        override val timestamp: Long,
        override val severity: Severity,
        val alert: Alert,
    ) : RobotEvent

    data class DecisionRequired(
        override val messageId: String,
        override val robotId: String,
        override val timestamp: Long,
        override val severity: Severity,
        val incident: Incident,
    ) : RobotEvent
}
