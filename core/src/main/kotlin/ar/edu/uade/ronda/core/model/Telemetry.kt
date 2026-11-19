package ar.edu.uade.ronda.core.model

data class Battery(val percent: Int, val minutesLeft: Int, val charging: Boolean)

/** El progreso es por puntos de control verificados, nunca un porcentaje inventado. */
data class MissionProgress(
    val id: String,
    val name: String,
    val checkpointIndex: Int,
    val checkpointsTotal: Int,
    val stage: String,
)

data class Pose(val floor: Int, val x: Double, val y: Double, val headingDeg: Double, val zone: String)

enum class MotionMode { CAMINANDO, DETENIDO, ESCALERA, GIRANDO }

data class Motion(val speedMps: Double, val mode: MotionMode)

enum class TempTrend { RISING, STABLE, FALLING }

enum class ExitStatus { LIBRE, OBSTRUIDA, NO_VERIFICADA }

data class EmergencyExit(val exitId: String, val name: String, val status: ExitStatus)

/** Las tres variables propias del servicio de ronda (plan §2). */
data class ServiceTelemetry(
    val checkpointsVerified: Int,
    val checkpointsWithIssues: Int,
    val techRoomTempC: Double,
    val techRoomTempTrend: TempTrend,
    val emergencyExitsClear: List<EmergencyExit>,
)

data class RobotTelemetry(
    val messageId: String,
    val robotId: String,
    val timestamp: Long,
    val latencyMs: Int,
    val battery: Battery,
    val operationalState: OperationalState,
    val mission: MissionProgress?,
    val pose: Pose,
    val motion: Motion,
    val alerts: List<Alert>,
    val service: ServiceTelemetry,
) {
    val unacknowledgedAlerts: List<Alert> get() = alerts.filter { !it.acknowledged }
}
