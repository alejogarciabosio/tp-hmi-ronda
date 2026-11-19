package ar.edu.uade.ronda.core.contract

import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.TempTrend
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Sobre común: el discriminador es el campo `type` del contrato. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MessageDto {
    val schemaVersion: Int
    val messageId: String
    val robotId: String
}

@Serializable
@SerialName("TELEMETRY")
data class TelemetryDto(
    override val schemaVersion: Int = ContractCodec.SCHEMA_VERSION,
    override val messageId: String,
    override val robotId: String,
    val timestamp: Long,
    val timestampIso: String? = null,
    val latencyMs: Int,
    val battery: BatteryDto,
    val operationalState: OperationalState,
    val mission: MissionDto? = null,
    val pose: PoseDto,
    val motion: MotionDto,
    val alerts: List<AlertDto> = emptyList(),
    val service: ServiceDto,
) : MessageDto

@Serializable
@SerialName("COMMAND")
data class CommandDto(
    override val schemaVersion: Int = ContractCodec.SCHEMA_VERSION,
    override val messageId: String,
    override val robotId: String,
    val issuedAt: Long,
    val issuedAtIso: String? = null,
    val operatorId: String,
    val command: CommandType,
    val params: ParamsDto = ParamsDto(),
    val ttlMs: Long = 4000,
) : MessageDto

@Serializable
@SerialName("COMMAND_ACK")
data class CommandAckDto(
    override val schemaVersion: Int = ContractCodec.SCHEMA_VERSION,
    override val messageId: String,
    val correlationId: String,
    override val robotId: String,
    val receivedAt: Long,
    val receivedAtIso: String? = null,
    val status: AckStatus,
    val reasonCode: RejectReason? = null,
    val reasonText: String? = null,
) : MessageDto

@Serializable
@SerialName("EVENT")
data class EventDto(
    override val schemaVersion: Int = ContractCodec.SCHEMA_VERSION,
    override val messageId: String,
    override val robotId: String,
    val timestamp: Long,
    val timestampIso: String? = null,
    val event: EventKind,
    val severity: Severity,
    val alert: AlertDto? = null,
    val incident: IncidentDto? = null,
) : MessageDto

@Serializable
enum class EventKind { ALERT_RAISED, DECISION_REQUIRED }

@Serializable
data class ParamsDto(
    val routeId: RouteId? = null,
    val missionId: String? = null,
    val safePointId: SafePointId? = null,
    val alertId: String? = null,
    val decision: IncidentDecision? = null,
    val resolution: AlertResolution? = null,
)

@Serializable
data class BatteryDto(val percent: Int, val minutesLeft: Int, val charging: Boolean)

@Serializable
data class MissionDto(
    val id: String,
    val name: String,
    val checkpointIndex: Int,
    val checkpointsTotal: Int,
    val stage: String,
)

@Serializable
data class PoseDto(val floor: Int, val x: Double, val y: Double, val headingDeg: Double, val zone: String)

@Serializable
data class MotionDto(val speedMps: Double, val mode: MotionMode)

@Serializable
data class AlertDto(
    val id: String,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val raisedAt: Long,
    val acknowledged: Boolean = false,
)

@Serializable
data class ExitDto(val exitId: String, val name: String, val status: ExitStatus)

@Serializable
data class ServiceDto(
    val checkpointsVerified: Int,
    val checkpointsWithIssues: Int,
    val techRoomTempC: Double,
    val techRoomTempTrend: TempTrend,
    val emergencyExitsClear: List<ExitDto> = emptyList(),
)

@Serializable
data class IncidentOptionDto(val id: String, val label: String)

@Serializable
data class IncidentDto(
    val id: String,
    val type: IncidentType,
    val checkpointIndex: Int,
    val message: String,
    val options: List<IncidentOptionDto>,
)
