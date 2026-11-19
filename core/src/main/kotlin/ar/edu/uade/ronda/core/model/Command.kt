package ar.edu.uade.ronda.core.model

enum class CommandType {
    START_MISSION,
    PAUSE_MISSION,
    RESUME_MISSION,
    CANCEL_MISSION,
    RETURN_TO_SAFE_POINT,
    OPERATIONAL_STOP,
    ACKNOWLEDGE_ALERT,
}

/** Opciones de `RESUME_MISSION` ante un incidente. */
enum class IncidentDecision { WAIT_30S, SKIP_CHECKPOINT }

/** Resultado de la persona en zona restringida, enviado con `ACKNOWLEDGE_ALERT`. */
enum class AlertResolution { AUTHORIZED_PERSON, SEND_GUARD, CONTINUE_ROUND }

/** Parámetros posibles; cada comando usa solo los suyos. `decision` y `resolution` son opcionales. */
data class CommandParams(
    val routeId: RouteId? = null,
    val missionId: String? = null,
    val safePointId: SafePointId? = null,
    val alertId: String? = null,
    val decision: IncidentDecision? = null,
    val resolution: AlertResolution? = null,
)

data class Command(
    val messageId: String,
    val robotId: String,
    val issuedAt: Long,
    val operatorId: String,
    val type: CommandType,
    val params: CommandParams = CommandParams(),
    val ttlMs: Long = 4000,
)

/** Estado visible de un comando (plan §3.2). El gateway solo responde CONFIRMED o REJECTED. */
enum class CommandStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    NO_RESPONSE,
    /** La telemetría ya refleja el efecto del comando. */
    VERIFIED,
}

enum class AckStatus { CONFIRMED, REJECTED }

enum class RejectReason {
    INVALID_STATE,
    LOW_BATTERY,
    NOT_CONNECTED,
    COMMAND_PENDING,
    UNACKNOWLEDGED_ALERT,
    EXPIRED,
    UNKNOWN_TARGET,
}

data class CommandAck(
    val messageId: String,
    val correlationId: String,
    val robotId: String,
    val receivedAt: Long,
    val status: AckStatus,
    val reasonCode: RejectReason? = null,
    val reasonText: String? = null,
)
