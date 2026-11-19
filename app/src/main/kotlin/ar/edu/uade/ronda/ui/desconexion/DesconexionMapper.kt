package ar.edu.uade.ronda.ui.desconexion

import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.supervision.BuildingPlan
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.LastCommandUi
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Un evento del robot junto con la hora en que lo recibió la app. */
internal data class ReceivedEvent(val event: RobotEvent, val receivedAt: Long)

internal data class DesconexionInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val commands: List<TrackedCommand>,
    val availability: Map<CommandType, CommandAvailability>,
    val received: List<ReceivedEvent>,
    val nowMs: Long,
)

internal object DesconexionMapper {
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Supuesto declarado del simulador (docs/supuestos-simulacion.md), no un dato del Go2. */
    val POLICY = listOf(
        "1. Se detiene donde está.",
        "2. Si el enlace no vuelve en 60 s, regresa solo a la base.",
        "3. Si el enlace vuelve antes, espera la orden del operador: no reanuda la ronda por su cuenta.",
    )

    /** Un evento que llega más de esto después de ocurrido se considera entregado tarde. */
    private const val LATE_MS = 3_000L

    fun map(i: DesconexionInputs): DesconexionUiState {
        val sup = SupervisionMapper.map(
            SupervisionInputs(
                i.telemetry, i.connection, i.lastTelemetryAt, null, i.commands,
                i.availability, CheckpointLog(), null, null, i.nowMs,
            ),
        )
        val t = i.telemetry
        val stale = i.connection.isStale || t == null
        val ageMs = i.lastTelemetryAt?.let { (i.nowMs - it).coerceAtLeast(0) } ?: 0
        val age = SupervisionMapper.ageText(ageMs)
        val lastAtText = i.lastTelemetryAt?.let { clock.format(Instant.ofEpochMilli(it)) }

        val lastKnown = if (stale && t != null) listOf(
            TelemetryReadoutUi("Batería", t.battery.percent.toString(), "%", StatusLevel.STALE, null, age,
                "Último valor (a las $lastAtText)"),
            TelemetryReadoutUi("Posición", BuildingPlan.floorLabel(t.pose.floor), "", StatusLevel.STALE, null, age,
                "${t.pose.zone} · último valor"),
            TelemetryReadoutUi("Movimiento", SupervisionMapper.dec1(t.motion.speedMps), "m/s", StatusLevel.STALE, null, age,
                "${modeText(t.motion.mode)} (puede haber cambiado)"),
        ) else emptyList()

        val pending = i.commands.filter { it.status == CommandStatus.PENDING || it.status == CommandStatus.NO_RESPONSE }
            .map {
                LastCommandUi(
                    SupervisionMapper.commandName(it.command.type),
                    CommandChipUi(if (it.status == CommandStatus.PENDING) CommandChipStatus.PENDING else CommandChipStatus.NO_RESPONSE),
                    "enviada ${clock.format(Instant.ofEpochMilli(it.sentAt))} · al volver el enlace, confirmá antes de reenviarla",
                )
            }

        val late = i.received.filter { it.receivedAt - it.event.timestamp > LATE_MS }
            .sortedByDescending { it.event.timestamp }
            .map {
                val text = when (val e = it.event) {
                    is RobotEvent.AlertRaised -> e.alert.message
                    is RobotEvent.DecisionRequired -> e.incident.message
                }
                LateEventUi(it.event.messageId, text, clock.format(Instant.ofEpochMilli(it.event.timestamp)), clock.format(Instant.ofEpochMilli(it.receivedAt)))
            }

        return DesconexionUiState(
            robotId = t?.robotId ?: "RONDA-01",
            strip = if (stale) sup.strip.copy(needsAction = true, actionText = "Sí: el robot no responde; si hay riesgo, usá la parada física") else sup.strip,
            staleBanner = sup.staleBanner,
            linkOk = !stale,
            lastKnownTitle = if (lastKnown.isNotEmpty()) "ÚLTIMO DATO CONOCIDO · $lastAtText" else null,
            lastKnown = lastKnown,
            policy = POLICY,
            pending = pending,
            lateEvents = late,
            stop = sup.stop,
        )
    }

    private fun modeText(m: MotionMode) = when (m) {
        MotionMode.CAMINANDO -> "Caminando"
        MotionMode.DETENIDO -> "Detenido"
        MotionMode.ESCALERA -> "En escalera"
        MotionMode.GIRANDO -> "Girando"
    }
}
