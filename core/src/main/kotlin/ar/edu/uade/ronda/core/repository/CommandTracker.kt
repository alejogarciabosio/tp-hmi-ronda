package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandAck
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackedCommand(
    val command: Command,
    val status: CommandStatus,
    val sentAt: Long,
    val updatedAt: Long,
    val reasonCode: RejectReason? = null,
    val reasonText: String? = null,
) {
    val id: String get() = command.messageId
}

/**
 * Sigue cada comando enviado: PENDIENTE hasta que llega la respuesta o vence su ttl (SIN_RESPUESTA);
 * CONFIRMADA o RECHAZADA según el gateway; VERIFICADA cuando la telemetría refleja el efecto.
 * Confirmar es aceptar la orden, no haberla ejecutado.
 */
class CommandTracker(private val maxHistory: Int = 50) {
    private val _commands = MutableStateFlow<List<TrackedCommand>>(emptyList())
    val commands: StateFlow<List<TrackedCommand>> = _commands.asStateFlow()

    @Volatile
    private var lastTelemetry: RobotTelemetry? = null

    fun register(command: Command, nowMs: Long): TrackedCommand {
        val tracked = TrackedCommand(command, CommandStatus.PENDING, nowMs, nowMs)
        _commands.update { (it + tracked).takeLast(maxHistory) }
        return tracked
    }

    fun onAck(ack: CommandAck, nowMs: Long) {
        update(ack.correlationId) { c ->
            // Una respuesta tardía a un comando que ya figura sin respuesta no lo revive.
            if (c.status != CommandStatus.PENDING) c
            else when (ack.status) {
                AckStatus.CONFIRMED -> c.copy(status = CommandStatus.CONFIRMED, updatedAt = nowMs)
                AckStatus.REJECTED -> c.copy(
                    status = CommandStatus.REJECTED, updatedAt = nowMs,
                    reasonCode = ack.reasonCode, reasonText = ack.reasonText,
                )
            }
        }
        lastTelemetry?.let { onTelemetry(it, nowMs) }
    }

    fun onTelemetry(telemetry: RobotTelemetry, nowMs: Long) {
        lastTelemetry = telemetry
        _commands.update { list ->
            list.map { c ->
                if (c.status == CommandStatus.CONFIRMED && telemetry.timestamp >= c.sentAt && effectVisible(c.command, telemetry)) {
                    c.copy(status = CommandStatus.VERIFIED, updatedAt = nowMs)
                } else c
            }
        }
    }

    fun expire(nowMs: Long) {
        _commands.update { list ->
            list.map { c ->
                if (c.status == CommandStatus.PENDING && nowMs - c.sentAt >= c.command.ttlMs) {
                    c.copy(status = CommandStatus.NO_RESPONSE, updatedAt = nowMs)
                } else c
            }
        }
    }

    /** Hay una orden de misión esperando respuesta. Reconocer alertas no bloquea a las demás. */
    fun hasPendingMissionCommand(): Boolean =
        _commands.value.any { it.status == CommandStatus.PENDING && it.command.type != CommandType.ACKNOWLEDGE_ALERT }

    private fun update(id: String, transform: (TrackedCommand) -> TrackedCommand) {
        _commands.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun effectVisible(command: Command, t: RobotTelemetry): Boolean = when (command.type) {
        CommandType.START_MISSION -> t.operationalState in setOf(OperationalState.PREPARANDO, OperationalState.EN_RONDA)
        CommandType.PAUSE_MISSION -> t.operationalState == OperationalState.PAUSADA
        CommandType.RESUME_MISSION -> t.operationalState in setOf(OperationalState.EN_RONDA, OperationalState.REGRESANDO)
        CommandType.CANCEL_MISSION -> t.operationalState in setOf(OperationalState.CANCELADA, OperationalState.REGRESANDO, OperationalState.EN_BASE)
        CommandType.RETURN_TO_SAFE_POINT -> t.operationalState in setOf(OperationalState.REGRESANDO, OperationalState.EN_BASE, OperationalState.PAUSADA, OperationalState.DETENIDO)
        CommandType.OPERATIONAL_STOP -> t.operationalState == OperationalState.DETENIDO
        CommandType.ACKNOWLEDGE_ALERT -> t.alerts.none { it.id == command.params.alertId && !it.acknowledged }
    }
}
