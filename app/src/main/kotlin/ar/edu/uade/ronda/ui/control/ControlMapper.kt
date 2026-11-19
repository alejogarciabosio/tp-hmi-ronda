package ar.edu.uade.ronda.ui.control

import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.Etiquetas
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ControlInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val incident: Incident?,
    val commands: List<TrackedCommand>,
    val availability: Map<CommandType, CommandAvailability>,
    val nowMs: Long,
)

/**
 * Todas las órdenes con su estado. Pausar, reanudar, regresar y la detención operativa se
 * calculan igual que en Supervisión para que ambas pantallas coincidan.
 */
internal object ControlMapper {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun map(i: ControlInputs): ControlUiState {
        val sup = SupervisionMapper.map(
            SupervisionInputs(
                i.telemetry, i.connection, i.lastTelemetryAt, i.incident, i.commands,
                i.availability, CheckpointLog(), null, null, i.nowMs,
            ),
        )
        return ControlUiState(
            robotId = i.telemetry?.robotId ?: "RONDA-01",
            strip = sup.strip,
            stop = sup.stop,
            pause = sup.pause,
            resume = sup.resume,
            returnToBase = button("Regresar a base", CommandKind.PRIMARY, CommandType.RETURN_TO_SAFE_POINT, i) {
                it.command.params.safePointId != SafePointId.DESCANSO_ESCALERA_E
            },
            safePoint = button("Ir al punto seguro", CommandKind.SECONDARY, CommandType.RETURN_TO_SAFE_POINT, i) {
                it.command.params.safePointId == SafePointId.DESCANSO_ESCALERA_E
            }.let { if (it.state == CommandUiState.ENABLED && it.reason == null) it.copy(reason = SafePointId.DESCANSO_ESCALERA_E.displayName) else it },
            skipCheckpoint = skip(i),
            cancel = button("Cancelar misión", CommandKind.DESTRUCTIVE, CommandType.CANCEL_MISSION, i),
            log = i.commands.asReversed().map { logRow(it) },
            // Mismo criterio que Supervisión, pero con el botón de regresar a la base de Control.
            lowBatteryReturn = sup.lowBatteryReturn,
            guardNotice = sup.guardNotice,
        )
    }

    /** Saltar el punto actual solo tiene sentido cuando el robot pide decidir ante un obstáculo. */
    private fun skip(i: ControlInputs): CommandButtonUi {
        val canSkip = i.incident?.type == IncidentType.OBSTACLE &&
            i.incident.options.any { it.id == IncidentDecision.SKIP_CHECKPOINT.name }
        if (!canSkip) {
            return CommandButtonUi("Saltar PC actual", CommandKind.SECONDARY, CommandUiState.DISABLED, SKIP_ONLY_OBSTACLE)
        }
        return button("Saltar PC actual", CommandKind.SECONDARY, CommandType.RESUME_MISSION, i) {
            it.command.params.decision == IncidentDecision.SKIP_CHECKPOINT
        }
    }

    private fun button(
        label: String,
        kind: CommandKind,
        type: CommandType,
        i: ControlInputs,
        matches: (TrackedCommand) -> Boolean = { true },
    ): CommandButtonUi {
        val av = i.availability[type] ?: CommandAvailability.disabled("Sin información de disponibilidad")
        val last = i.commands.lastOrNull { it.command.type == type && matches(it) }
        if (last?.status == CommandStatus.PENDING) {
            return CommandButtonUi(label, kind, CommandUiState.PENDING, "Esperando confirmación del robot · enviada ${time(last.sentAt)}")
        }
        if (!av.enabled) return CommandButtonUi(label, kind, CommandUiState.DISABLED, av.reason)
        if (last != null && last === i.commands.lastOrNull()) {
            when (last.status) {
                CommandStatus.REJECTED -> return CommandButtonUi(label, kind, CommandUiState.REJECTED, "Rechazada por el robot: ${rejection(last)}")
                CommandStatus.NO_RESPONSE -> return CommandButtonUi(label, kind, CommandUiState.NO_RESPONSE, "Sin respuesta: no se sabe si se ejecutó")
                else -> {}
            }
        }
        return CommandButtonUi(label, kind)
    }

    private fun logRow(c: TrackedCommand): CommandLogRowUi {
        val chip = when (c.status) {
            CommandStatus.PENDING -> CommandChipStatus.PENDING
            CommandStatus.CONFIRMED -> CommandChipStatus.CONFIRMED
            CommandStatus.VERIFIED -> CommandChipStatus.VERIFIED
            CommandStatus.REJECTED -> CommandChipStatus.REJECTED
            CommandStatus.NO_RESPONSE -> CommandChipStatus.NO_RESPONSE
        }
        val p = c.command.params
        val label = when {
            c.command.type == CommandType.RETURN_TO_SAFE_POINT && p.safePointId == SafePointId.DESCANSO_ESCALERA_E -> "Ir al punto seguro"
            c.command.type == CommandType.RESUME_MISSION && p.decision == IncidentDecision.SKIP_CHECKPOINT -> "Saltar PC actual"
            c.command.type == CommandType.RESUME_MISSION && p.decision == IncidentDecision.WAIT_30S -> "Esperar 30 s"
            c.command.type == CommandType.START_MISSION && p.routeId != null -> "Iniciar: ${p.routeId?.displayName}"
            p.resolution != null -> "${SupervisionMapper.commandName(c.command.type)} · ${Etiquetas.opcion(p.resolution!!.name)}"
            else -> SupervisionMapper.commandName(c.command.type)
        }
        val detail = when {
            c.status == CommandStatus.REJECTED -> rejection(c)
            c.status == CommandStatus.NO_RESPONSE -> "no se sabe si se ejecutó"
            c.verifiedWithoutAck -> SupervisionMapper.VERIFIED_WITHOUT_ACK
            else -> null
        }
        return CommandLogRowUi(c.id, time(c.sentAt), label, CommandChipUi(chip, c.verifiedWithoutAck), detail)
    }

    private fun rejection(c: TrackedCommand): String = c.reasonText ?: when (c.reasonCode) {
        RejectReason.INVALID_STATE -> "estado no válido para la orden"
        RejectReason.LOW_BATTERY -> "batería insuficiente"
        RejectReason.NOT_CONNECTED -> "robot sin conexión"
        RejectReason.COMMAND_PENDING -> "hay otra orden pendiente"
        RejectReason.UNACKNOWLEDGED_ALERT -> "hay una alerta sin reconocer"
        RejectReason.EXPIRED -> "la orden venció"
        RejectReason.UNKNOWN_TARGET -> "destino desconocido"
        null -> "motivo no informado"
    }

    private fun time(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms))

    const val SKIP_ONLY_OBSTACLE = "Solo ante un obstáculo"
}
