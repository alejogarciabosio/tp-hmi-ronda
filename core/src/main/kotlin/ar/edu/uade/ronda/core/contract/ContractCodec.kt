package ar.edu.uade.ronda.core.contract

import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.Battery
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandAck
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.EmergencyExit
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentOption
import ar.edu.uade.ronda.core.model.MissionProgress
import ar.edu.uade.ronda.core.model.Motion
import ar.edu.uade.ronda.core.model.Pose
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.ServiceTelemetry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/** Mensaje ya decodificado, en términos de dominio. */
sealed interface ContractMessage {
    data class Telemetry(val value: RobotTelemetry) : ContractMessage
    data class CommandMsg(val value: Command) : ContractMessage
    data class Ack(val value: CommandAck) : ContractMessage
    data class Event(val value: RobotEvent) : ContractMessage
}

class ContractException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Codifica y decodifica los mensajes de `docs/contrato/`. Es un contrato propio del grupo,
 * no el protocolo oficial de Unitree.
 */
object ContractCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    fun decode(text: String): ContractMessage {
        val dto = try {
            json.decodeFromString<MessageDto>(text)
        } catch (e: SerializationException) {
            throw ContractException("Mensaje inválido: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ContractException("Mensaje inválido: ${e.message}", e)
        }
        if (dto.schemaVersion != SCHEMA_VERSION) {
            throw ContractException("schemaVersion no soportada: ${dto.schemaVersion}")
        }
        return when (dto) {
            is TelemetryDto -> ContractMessage.Telemetry(dto.toDomain())
            is CommandDto -> ContractMessage.CommandMsg(dto.toDomain())
            is CommandAckDto -> ContractMessage.Ack(dto.toDomain())
            is EventDto -> ContractMessage.Event(dto.toDomain())
        }
    }

    fun encode(telemetry: RobotTelemetry): String = encodeDto(telemetry.toDto())
    fun encode(command: Command): String = encodeDto(command.toDto())
    fun encode(ack: CommandAck): String = encodeDto(ack.toDto())
    fun encode(event: RobotEvent): String = encodeDto(event.toDto())

    private fun encodeDto(dto: MessageDto): String = json.encodeToString(MessageDto.serializer(), dto)

    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    // --- DTO -> dominio ---

    private fun TelemetryDto.toDomain() = RobotTelemetry(
        messageId = messageId,
        robotId = robotId,
        timestamp = timestamp,
        latencyMs = latencyMs,
        battery = Battery(battery.percent, battery.minutesLeft, battery.charging),
        operationalState = operationalState,
        mission = mission?.let { MissionProgress(it.id, it.name, it.checkpointIndex, it.checkpointsTotal, it.stage) },
        pose = Pose(pose.floor, pose.x, pose.y, pose.headingDeg, pose.zone),
        motion = Motion(motion.speedMps, motion.mode),
        alerts = alerts.map { it.toDomain() },
        service = ServiceTelemetry(
            checkpointsVerified = service.checkpointsVerified,
            checkpointsWithIssues = service.checkpointsWithIssues,
            techRoomTempC = service.techRoomTempC,
            techRoomTempTrend = service.techRoomTempTrend,
            emergencyExitsClear = service.emergencyExitsClear.map { EmergencyExit(it.exitId, it.name, it.status) },
        ),
    )

    private fun AlertDto.toDomain() = Alert(id, type, severity, message, raisedAt, acknowledged)

    private fun CommandDto.toDomain() = Command(
        messageId = messageId,
        robotId = robotId,
        issuedAt = issuedAt,
        operatorId = operatorId,
        type = command,
        params = CommandParams(
            params.routeId, params.missionId, params.safePointId,
            params.alertId, params.decision, params.resolution,
        ),
        ttlMs = ttlMs,
    )

    private fun CommandAckDto.toDomain() =
        CommandAck(messageId, correlationId, robotId, receivedAt, status, reasonCode, reasonText)

    private fun EventDto.toDomain(): RobotEvent = when (event) {
        EventKind.ALERT_RAISED -> RobotEvent.AlertRaised(
            messageId, robotId, timestamp, severity,
            (alert ?: throw ContractException("ALERT_RAISED sin 'alert'")).toDomain(),
        )
        EventKind.DECISION_REQUIRED -> {
            val i = incident ?: throw ContractException("DECISION_REQUIRED sin 'incident'")
            RobotEvent.DecisionRequired(
                messageId, robotId, timestamp, severity,
                Incident(i.id, i.type, i.checkpointIndex, i.message, i.options.map { IncidentOption(it.id, it.label, it.safePointId) }),
            )
        }
    }

    // --- dominio -> DTO ---

    private fun RobotTelemetry.toDto() = TelemetryDto(
        messageId = messageId,
        robotId = robotId,
        timestamp = timestamp,
        timestampIso = iso(timestamp),
        latencyMs = latencyMs,
        battery = BatteryDto(battery.percent, battery.minutesLeft, battery.charging),
        operationalState = operationalState,
        mission = mission?.let { MissionDto(it.id, it.name, it.checkpointIndex, it.checkpointsTotal, it.stage) },
        pose = PoseDto(pose.floor, pose.x, pose.y, pose.headingDeg, pose.zone),
        motion = MotionDto(motion.speedMps, motion.mode),
        alerts = alerts.map { it.toDto() },
        service = ServiceDto(
            service.checkpointsVerified,
            service.checkpointsWithIssues,
            service.techRoomTempC,
            service.techRoomTempTrend,
            service.emergencyExitsClear.map { ExitDto(it.exitId, it.name, it.status) },
        ),
    )

    private fun Alert.toDto() = AlertDto(id, type, severity, message, raisedAt, acknowledged)

    private fun Command.toDto() = CommandDto(
        messageId = messageId,
        robotId = robotId,
        issuedAt = issuedAt,
        issuedAtIso = iso(issuedAt),
        operatorId = operatorId,
        command = type,
        params = ParamsDto(
            params.routeId, params.missionId, params.safePointId,
            params.alertId, params.decision, params.resolution,
        ),
        ttlMs = ttlMs,
    )

    private fun CommandAck.toDto() = CommandAckDto(
        messageId = messageId,
        correlationId = correlationId,
        robotId = robotId,
        receivedAt = receivedAt,
        receivedAtIso = iso(receivedAt),
        status = status,
        reasonCode = reasonCode,
        reasonText = reasonText,
    )

    private fun RobotEvent.toDto(): EventDto = when (this) {
        is RobotEvent.AlertRaised -> EventDto(
            messageId = messageId, robotId = robotId, timestamp = timestamp, timestampIso = iso(timestamp),
            event = EventKind.ALERT_RAISED, severity = severity, alert = alert.toDto(),
        )
        is RobotEvent.DecisionRequired -> EventDto(
            messageId = messageId, robotId = robotId, timestamp = timestamp, timestampIso = iso(timestamp),
            event = EventKind.DECISION_REQUIRED, severity = severity,
            incident = IncidentDto(
                incident.id, incident.type, incident.checkpointIndex, incident.message,
                incident.options.map { IncidentOptionDto(it.id, it.label, it.safePointId) },
            ),
        )
    }
}
