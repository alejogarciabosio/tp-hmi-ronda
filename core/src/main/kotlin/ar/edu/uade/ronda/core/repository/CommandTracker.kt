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
    /** Estado operativo que mostraba la última telemetría al enviar la orden; `null` si no había. */
    val stateAtSend: OperationalState? = null,
    /**
     * VERIFICADA sin respuesta del robot: la telemetría posterior al envío mostró el efecto, pero la
     * confirmación no llegó (o no llegó a tiempo). La UI lo dice: "Verificada por telemetría (sin confirmación)".
     */
    val verifiedWithoutAck: Boolean = false,
) {
    val id: String get() = command.messageId
}

/**
 * Sigue cada comando enviado: PENDIENTE hasta que llega la respuesta o vence su ttl (SIN_RESPUESTA);
 * CONFIRMADA o RECHAZADA según el gateway; VERIFICADA cuando la telemetría refleja el efecto.
 * Confirmar es aceptar la orden, no haberla ejecutado.
 *
 * Una orden PENDIENTE o SIN_RESPUESTA también pasa a VERIFICADA (con [TrackedCommand.verifiedWithoutAck])
 * si una telemetría posterior al envío muestra un cambio coherente con la orden respecto del estado
 * que había al enviarla: así una orden ejecutada cuya respuesta se perdió no queda sin respuesta para siempre.
 */
class CommandTracker(private val maxHistory: Int = 50) {
    private val _commands = MutableStateFlow<List<TrackedCommand>>(emptyList())
    val commands: StateFlow<List<TrackedCommand>> = _commands.asStateFlow()

    @Volatile
    private var lastTelemetry: RobotTelemetry? = null

    /** Órdenes de reconocer alerta cuya alerta figuraba sin reconocer al enviarlas. */
    private val alertPendingAtSend = mutableSetOf<String>()

    /** Órdenes pendientes cuyo efecto ya se vio en la telemetría: al vencer pasan a VERIFICADA y no a SIN_RESPUESTA. */
    private val effectSeenWhilePending = mutableSetOf<String>()

    fun register(command: Command, nowMs: Long): TrackedCommand {
        val before = lastTelemetry
        if (command.type == CommandType.ACKNOWLEDGE_ALERT &&
            before?.alerts?.any { it.id == command.params.alertId && !it.acknowledged } == true
        ) {
            synchronized(alertPendingAtSend) { alertPendingAtSend += command.messageId }
        }
        val tracked = TrackedCommand(command, CommandStatus.PENDING, nowMs, nowMs, stateAtSend = before?.operationalState)
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
                val after = telemetry.timestamp >= c.sentAt
                when {
                    !after -> c
                    c.status == CommandStatus.CONFIRMED && effectVisible(c, telemetry) ->
                        c.copy(status = CommandStatus.VERIFIED, updatedAt = nowMs)
                    // Pendiente: se espera la respuesta hasta el ttl; si no llega, el efecto visto la verifica.
                    c.status == CommandStatus.PENDING && coherentChange(c, telemetry) -> {
                        synchronized(effectSeenWhilePending) { effectSeenWhilePending += c.id }
                        c
                    }
                    c.status == CommandStatus.NO_RESPONSE && coherentChange(c, telemetry) ->
                        c.copy(status = CommandStatus.VERIFIED, verifiedWithoutAck = true, updatedAt = nowMs)
                    else -> c
                }
            }
        }
    }

    fun expire(nowMs: Long) {
        _commands.update { list ->
            list.map { c ->
                if (c.status == CommandStatus.PENDING && nowMs - c.sentAt >= c.command.ttlMs) {
                    val seen = synchronized(effectSeenWhilePending) { c.id in effectSeenWhilePending }
                    if (seen) c.copy(status = CommandStatus.VERIFIED, verifiedWithoutAck = true, updatedAt = nowMs)
                    else c.copy(status = CommandStatus.NO_RESPONSE, updatedAt = nowMs)
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

    /**
     * Sin respuesta del robot se exige más que ver el efecto: un cambio de estado coherente con la orden
     * respecto del que había al enviarla, para no atribuirle a la orden algo que ya pasaba o que pasó por otra causa.
     */
    private fun coherentChange(c: TrackedCommand, t: RobotTelemetry): Boolean {
        val before = c.stateAtSend ?: return false
        val now = t.operationalState
        return when (c.command.type) {
            CommandType.START_MISSION -> before == OperationalState.EN_BASE && now in setOf(OperationalState.PREPARANDO, OperationalState.EN_RONDA)
            CommandType.PAUSE_MISSION -> before == OperationalState.EN_RONDA && now == OperationalState.PAUSADA
            CommandType.RESUME_MISSION -> before in RESUMABLE && now != before && effectVisible(c, t)
            CommandType.CANCEL_MISSION -> before in CANCELABLE && now in setOf(OperationalState.CANCELADA, OperationalState.REGRESANDO, OperationalState.EN_BASE)
            CommandType.RETURN_TO_SAFE_POINT -> before != OperationalState.REGRESANDO && before != OperationalState.EN_BASE && now == OperationalState.REGRESANDO
            CommandType.OPERATIONAL_STOP -> before.inMotion && now == OperationalState.DETENIDO
            CommandType.ACKNOWLEDGE_ALERT ->
                synchronized(alertPendingAtSend) { c.id in alertPendingAtSend } &&
                    t.alerts.any { it.id == c.command.params.alertId && it.acknowledged }
        }
    }

    private fun effectVisible(c: TrackedCommand, t: RobotTelemetry): Boolean = effectVisible(c.command, c.stateAtSend, t)

    private fun effectVisible(command: Command, stateAtSend: OperationalState?, t: RobotTelemetry): Boolean = when (command.type) {
        CommandType.START_MISSION -> t.operationalState in setOf(OperationalState.PREPARANDO, OperationalState.EN_RONDA)
        CommandType.PAUSE_MISSION -> t.operationalState == OperationalState.PAUSADA
        // Reanudar una detención hecha durante una decisión vuelve a REQUIERE_DECISION.
        CommandType.RESUME_MISSION -> t.operationalState in setOf(OperationalState.EN_RONDA, OperationalState.REGRESANDO) ||
            (stateAtSend == OperationalState.DETENIDO && t.operationalState == OperationalState.REQUIERE_DECISION)
        CommandType.CANCEL_MISSION -> t.operationalState in setOf(OperationalState.CANCELADA, OperationalState.REGRESANDO, OperationalState.EN_BASE)
        CommandType.RETURN_TO_SAFE_POINT -> t.operationalState in setOf(OperationalState.REGRESANDO, OperationalState.EN_BASE, OperationalState.PAUSADA, OperationalState.DETENIDO)
        CommandType.OPERATIONAL_STOP -> t.operationalState == OperationalState.DETENIDO
        CommandType.ACKNOWLEDGE_ALERT -> t.alerts.none { it.id == command.params.alertId && !it.acknowledged }
    }

    private companion object {
        val RESUMABLE = setOf(OperationalState.PAUSADA, OperationalState.DETENIDO, OperationalState.REQUIERE_DECISION)
        val CANCELABLE = setOf(OperationalState.EN_RONDA, OperationalState.PAUSADA, OperationalState.REQUIERE_DECISION, OperationalState.DETENIDO)
    }
}
