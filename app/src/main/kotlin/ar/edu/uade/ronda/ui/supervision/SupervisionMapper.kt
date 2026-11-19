package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.TempTrend
import ar.edu.uade.ronda.core.policy.BatteryPolicy
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CheckpointState
import ar.edu.uade.ronda.ui.model.CheckpointUi
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.FloorPlanUi
import ar.edu.uade.ronda.ui.model.RobotPoseUi
import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.model.Trend
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Todo lo que hace falta para armar el estado de pantalla en un instante. */
internal data class SupervisionInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val incident: Incident?,
    val commands: List<TrackedCommand>,
    val availability: Map<CommandType, CommandAvailability>,
    val log: CheckpointLog,
    val selectedFloor: Int?,
    val dismissedIncidentId: String?,
    val nowMs: Long,
)

internal data class CheckpointResolution(val state: CheckpointState, val at: Long)

/**
 * Recuerda cómo terminó cada punto de control. El robot informa cuántos verificó, no cuáles:
 * se deduce al ver avanzar el índice y comparar los contadores de la telemetría.
 */
internal data class CheckpointLog(
    val missionId: String? = null,
    val lastIndex: Int = 0,
    val lastVerified: Int = 0,
    val lastIssues: Int = 0,
    val resolved: Map<Int, CheckpointResolution> = emptyMap(),
) {
    fun next(t: RobotTelemetry): CheckpointLog {
        val m = t.mission ?: return CheckpointLog()
        val verified = t.service.checkpointsVerified
        val issues = t.service.checkpointsWithIssues
        if (m.id != missionId) return CheckpointLog(m.id, m.checkpointIndex, verified, issues)
        val advanced = m.checkpointIndex > lastIndex
        val finishedLast = t.operationalState == OperationalState.COMPLETADA && lastIndex !in resolved
        val newResolved = if (advanced || finishedLast) {
            val state = when {
                verified <= lastVerified -> CheckpointState.SKIPPED
                issues > lastIssues -> CheckpointState.WITH_ISSUE
                else -> CheckpointState.VERIFIED
            }
            resolved + (lastIndex to CheckpointResolution(state, t.timestamp))
        } else resolved
        return CheckpointLog(m.id, m.checkpointIndex, verified, issues, newResolved)
    }
}

internal object SupervisionMapper {
    private val battery = BatteryPolicy()
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun map(i: SupervisionInputs): SupervisionUiState {
        val t = i.telemetry
        val stale = t == null || i.connection.isStale
        val ageMs = i.lastTelemetryAt?.let { (i.nowMs - it).coerceAtLeast(0) }
        val route = t?.mission?.let { m -> RouteId.entries.firstOrNull { it.displayName == m.name } } ?: RouteId.RONDA_COMPLETA
        val allCheckpoints = checkpoints(route, t, i.log)
        val floor = i.selectedFloor ?: t?.pose?.floor ?: 0
        val robot = t?.pose?.let {
            RobotPoseUi(it.floor, BuildingPlan.normX(it.x), BuildingPlan.normY(it.y), ((it.headingDeg + 90.0) % 360.0).toFloat())
        }
        val dismissed = i.incident != null && i.incident.id == i.dismissedIncidentId
        return SupervisionUiState(
            strip = strip(i, t, stale, ageMs),
            staleBanner = if (t != null && stale && ageMs != null) staleBanner(ageMs) else null,
            hasData = t != null,
            plan = FloorPlanUi(floor, BuildingPlan.floorLabel(floor), allCheckpoints, robot),
            floorTabs = listOf(0, 1).map { FloorTabUi(it, BuildingPlan.floorLabel(it), it == floor) },
            planCaption = planCaption(t, stale, ageMs),
            progressTitle = t?.mission?.let { "${it.name} · PC ${it.checkpointIndex}/${it.checkpointsTotal}" } ?: "Sin misión en curso",
            checkpoints = if (t?.mission != null) window(allCheckpoints) else emptyList(),
            readouts = if (t != null) readouts(t, i.connection, stale, ageMs ?: 0) else emptyList(),
            alerts = t?.unacknowledgedAlerts.orEmpty().sortedByDescending { it.severity.ordinal }.map { alertRow(it, i.nowMs) },
            pause = button("Pausar", CommandKind.SECONDARY, CommandType.PAUSE_MISSION, i),
            resume = button("Reanudar", CommandKind.SECONDARY, CommandType.RESUME_MISSION, i).let {
                // Con una decisión abierta, reanudar sin elegir opción lo rechaza el robot.
                if (i.incident != null) it.copy(state = CommandUiState.DISABLED, reason = "Elegí una opción de la decisión pendiente") else it
            },
            returnToBase = button("Regresar a base", CommandKind.PRIMARY, CommandType.RETURN_TO_SAFE_POINT, i),
            stop = i.availability[CommandType.OPERATIONAL_STOP].let { StopButtonUi(it?.enabled ?: false, it?.reason) },
            lastCommand = lastCommand(i),
            decision = i.incident?.let { decision(it, i) },
            decisionDismissed = dismissed,
        )
    }

    // ---- Franja de estado ----

    private fun strip(i: SupervisionInputs, t: RobotTelemetry?, stale: Boolean, ageMs: Long?): StateStripUi {
        val connLabel = when {
            t == null -> "Sin datos · todavía no llegó telemetría"
            i.connection == ConnectionState.CONNECTED -> "Conectado · ${seconds(ageMs ?: 0)}"
            i.connection == ConnectionState.DEGRADED -> "Enlace degradado · ${seconds(ageMs ?: 0)}"
            i.connection == ConnectionState.NO_DATA -> "Sin datos · ${ageText(ageMs ?: 0)}"
            else -> "Desconectado · ${ageText(ageMs ?: 0)}"
        }
        val connLevel = when (i.connection) {
            ConnectionState.CONNECTED -> StatusLevel.OK
            ConnectionState.DEGRADED -> StatusLevel.WARNING
            else -> StatusLevel.STALE
        }
        if (t == null) return StateStripUi("Sin datos", StatusLevel.STALE, true, "Sí: esperá a que el robot informe su estado", connLabel, connLevel)
        val s = t.operationalState
        val label = if (stale) "${stateLabel(s)} (último conocido)" else stateLabel(s)
        val (needs, text) = when {
            stale -> true to "Sí: sin datos del robot, su estado actual es desconocido"
            s == OperationalState.REQUIERE_DECISION -> true to "Sí: decidí cómo seguir en PC${t.mission?.checkpointIndex ?: i.incident?.checkpointIndex ?: "?"}"
            s == OperationalState.DETENIDO -> true to "Sí: reanudar o regresar a base"
            s == OperationalState.FALLA -> true to "Sí: el robot requiere un técnico"
            t.unacknowledgedAlerts.any { it.severity == Severity.CRITICAL } -> true to "Sí: hay una alerta crítica sin reconocer"
            else -> false to "No"
        }
        return StateStripUi(label, if (stale) StatusLevel.STALE else stateLevel(s), needs, text, connLabel, connLevel)
    }

    private fun staleBanner(ageMs: Long) = StaleBannerUi(
        secondsWithoutData = (ageMs / 1000).toInt(),
        policyText = if (ageMs >= 30_000) "Política del robot sin enlace (supuesto declarado): se detiene y espera." else null,
    )

    fun stateLabel(s: OperationalState) = when (s) {
        OperationalState.EN_BASE -> "En base"
        OperationalState.PREPARANDO -> "Preparando"
        OperationalState.EN_RONDA -> "En ronda"
        OperationalState.PAUSADA -> "Pausada"
        OperationalState.REQUIERE_DECISION -> "Requiere decisión"
        OperationalState.DETENIDO -> "Detenido"
        OperationalState.REGRESANDO -> "Regresando"
        OperationalState.COMPLETADA -> "Completada"
        OperationalState.CANCELADA -> "Cancelada"
        OperationalState.FALLA -> "Falla"
    }

    private fun stateLevel(s: OperationalState) = when (s) {
        OperationalState.PREPARANDO, OperationalState.EN_RONDA, OperationalState.REGRESANDO, OperationalState.COMPLETADA -> StatusLevel.OK
        OperationalState.PAUSADA, OperationalState.REQUIERE_DECISION -> StatusLevel.WARNING
        OperationalState.DETENIDO, OperationalState.FALLA -> StatusLevel.ERROR
        OperationalState.EN_BASE, OperationalState.CANCELADA -> StatusLevel.DISABLED
    }

    // ---- Plano y puntos de control ----

    private fun checkpoints(route: RouteId, t: RobotTelemetry?, log: CheckpointLog): List<CheckpointUi> {
        val mission = t?.mission
        return BuildingPlan.stops(route).mapIndexed { idx, stop ->
            val n = idx + 1
            val resolved = if (mission != null && log.missionId == mission.id) log.resolved[n] else null
            val state = when {
                resolved != null -> resolved.state
                mission != null && mission.checkpointIndex == n && t.operationalState != OperationalState.COMPLETADA -> CheckpointState.CURRENT
                else -> CheckpointState.PENDING
            }
            CheckpointUi(
                n, stop.name, state, resolved?.takeIf { it.state != CheckpointState.SKIPPED }?.let { clockFormat.format(Instant.ofEpochMilli(it.at)) },
                stop.floor, BuildingPlan.normX(stop.xM), BuildingPlan.normY(stop.yM),
            )
        }
    }

    /** Cuatro filas alrededor del punto actual, como en el Figma. */
    private fun window(all: List<CheckpointUi>): List<CheckpointUi> {
        if (all.size <= WINDOW) return all
        val current = all.indexOfFirst { it.state == CheckpointState.CURRENT }.takeIf { it >= 0 }
            ?: all.indexOfLast { it.state != CheckpointState.PENDING }.coerceAtLeast(0)
        val start = (current - 1).coerceIn(0, all.size - WINDOW)
        return all.subList(start, start + WINDOW)
    }

    private fun planCaption(t: RobotTelemetry?, stale: Boolean, ageMs: Long?): String? = when {
        t == null -> null
        stale -> "Última posición conocida, no vigente: ${t.pose.zone}, ${ageText(ageMs ?: 0)}"
        else -> "Zona: ${t.pose.zone}"
    }

    // ---- Lecturas ----

    private fun readouts(t: RobotTelemetry, conn: ConnectionState, stale: Boolean, ageMs: Long): List<TelemetryReadoutUi> {
        val age = ageText(ageMs)
        fun lvl(l: StatusLevel) = if (stale) StatusLevel.STALE else l

        val pct = t.battery.percent
        val canReturn = battery.canReturnToBase(pct, BuildingPlan.distanceToBaseM(t.pose.floor, t.pose.x, t.pose.y))
        val batteryLevel = when {
            !canReturn || pct <= 15 -> StatusLevel.ERROR
            pct <= 25 -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val batteryDetail = "Alcanza para volver: ${if (canReturn) "sí" else "no"} · ${t.battery.minutesLeft} min restantes" +
            if (t.battery.charging) " · cargando" else ""

        val temp = t.service.techRoomTempC
        val tempLevel = when {
            temp >= 32.0 -> StatusLevel.ERROR
            temp > 27.0 -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val exits = t.service.emergencyExitsClear
        val free = exits.count { it.status == ExitStatus.LIBRE }
        val exitLevel = when {
            exits.any { it.status == ExitStatus.OBSTRUIDA } -> StatusLevel.ERROR
            exits.any { it.status == ExitStatus.NO_VERIFICADA } -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val exitDetail = exits.filter { it.status != ExitStatus.LIBRE }.joinToString(" · ") {
            "${it.name}: ${if (it.status == ExitStatus.OBSTRUIDA) "obstruida" else "sin verificar"}"
        }.ifEmpty { null }

        val connLevel = when (conn) {
            ConnectionState.CONNECTED -> StatusLevel.OK
            ConnectionState.DEGRADED -> StatusLevel.WARNING
            else -> StatusLevel.STALE
        }
        return listOf(
            TelemetryReadoutUi("Batería", pct.toString(), "%", lvl(batteryLevel), if (t.battery.charging) Trend.RISING else null, age, batteryDetail),
            TelemetryReadoutUi(
                "Conexión", t.latencyMs.toString(), "ms", connLevel, null, age,
                when (conn) {
                    ConnectionState.CONNECTED -> "Latencia del último mensaje"
                    ConnectionState.DEGRADED -> "Enlace degradado"
                    ConnectionState.NO_DATA -> "Sin datos recientes"
                    ConnectionState.DISCONNECTED -> "Desconectado"
                },
            ),
            TelemetryReadoutUi(
                "Sala técnica", dec1(temp), "°C", lvl(tempLevel),
                when (t.service.techRoomTempTrend) { TempTrend.RISING -> Trend.RISING; TempTrend.FALLING -> Trend.FALLING; TempTrend.STABLE -> Trend.STABLE },
                age, "Sensor externo",
            ),
            TelemetryReadoutUi("Salidas libres", "$free/${exits.size}", "", lvl(exitLevel), null, age, exitDetail),
        )
    }

    private fun alertRow(a: Alert, nowMs: Long) = AlertRowUi(
        a.id, a.message,
        when (a.severity) { Severity.CRITICAL -> StatusLevel.ERROR; Severity.WARNING -> StatusLevel.WARNING; Severity.INFO -> StatusLevel.OK },
        ageText(nowMs - a.raisedAt),
    )

    // ---- Comandos ----

    private fun button(label: String, kind: CommandKind, type: CommandType, i: SupervisionInputs): CommandButtonUi {
        val av = i.availability[type] ?: CommandAvailability.disabled("Sin información de disponibilidad")
        val last = i.commands.lastOrNull { it.command.type == type }
        if (last?.status == CommandStatus.PENDING) {
            return CommandButtonUi(label, kind, CommandUiState.PENDING, "Esperando respuesta del robot")
        }
        if (!av.enabled) return CommandButtonUi(label, kind, CommandUiState.DISABLED, av.reason)
        if (last != null && last === i.commands.lastOrNull()) {
            when (last.status) {
                CommandStatus.REJECTED -> return CommandButtonUi(label, kind, CommandUiState.REJECTED, "Rechazada: ${rejectionText(last)}")
                CommandStatus.NO_RESPONSE -> return CommandButtonUi(label, kind, CommandUiState.NO_RESPONSE, "Sin respuesta: no se sabe si se ejecutó")
                else -> {}
            }
        }
        return CommandButtonUi(label, kind, CommandUiState.ENABLED)
    }

    private fun rejectionText(c: TrackedCommand): String = c.reasonText ?: when (c.reasonCode) {
        RejectReason.INVALID_STATE -> "estado no válido para la orden"
        RejectReason.LOW_BATTERY -> "batería insuficiente"
        RejectReason.NOT_CONNECTED -> "robot sin conexión"
        RejectReason.COMMAND_PENDING -> "hay otra orden pendiente"
        RejectReason.UNACKNOWLEDGED_ALERT -> "hay una alerta sin reconocer"
        RejectReason.EXPIRED -> "la orden venció"
        RejectReason.UNKNOWN_TARGET -> "destino desconocido"
        null -> "motivo no informado"
    }

    fun commandName(type: CommandType) = when (type) {
        CommandType.START_MISSION -> "Iniciar ronda"
        CommandType.PAUSE_MISSION -> "Pausar"
        CommandType.RESUME_MISSION -> "Reanudar"
        CommandType.CANCEL_MISSION -> "Cancelar misión"
        CommandType.RETURN_TO_SAFE_POINT -> "Regresar a base"
        CommandType.OPERATIONAL_STOP -> "Detención operativa"
        CommandType.ACKNOWLEDGE_ALERT -> "Reconocer alerta"
    }

    private fun lastCommand(i: SupervisionInputs): LastCommandUi? {
        val c = i.commands.lastOrNull() ?: return null
        val recent = i.nowMs - c.updatedAt < RECENT_MS
        if (!(c.status == CommandStatus.PENDING || c.status == CommandStatus.NO_RESPONSE || recent)) return null
        val chip = when (c.status) {
            CommandStatus.PENDING -> CommandChipStatus.PENDING
            CommandStatus.CONFIRMED -> CommandChipStatus.CONFIRMED
            CommandStatus.VERIFIED -> CommandChipStatus.VERIFIED
            CommandStatus.REJECTED -> CommandChipStatus.REJECTED
            CommandStatus.NO_RESPONSE -> CommandChipStatus.NO_RESPONSE
        }
        val detail = when (c.status) {
            CommandStatus.REJECTED -> rejectionText(c)
            CommandStatus.NO_RESPONSE -> "no se sabe si se ejecutó"
            else -> ageText(i.nowMs - c.sentAt)
        }
        return LastCommandUi(commandName(c.command.type), CommandChipUi(chip), detail)
    }

    // ---- Decisión ----

    private fun decision(inc: Incident, i: SupervisionInputs): DecisionUi {
        fun av(type: CommandType) = i.availability[type] ?: CommandAvailability.disabled("Sin información de disponibilidad")
        val title = when (inc.type) {
            IncidentType.OBSTACLE -> "Obstáculo en el pasillo"
            IncidentType.PERSON_IN_RESTRICTED_ZONE -> "Persona en zona restringida"
            IncidentType.BATTERY_RETURN -> "Batería crítica"
        }
        return DecisionUi(
            "$title · PC${inc.checkpointIndex}", inc.message,
            inc.options.map { o ->
                val type = SupervisionViewModel.commandFor(o.id)
                val a = av(type)
                DecisionOptionUi(o.id, o.label, o.id == SupervisionViewModel.OPTION_RETURN, a.enabled, a.reason)
            },
        )
    }

    // ---- Formato ----

    fun dec1(v: Double): String = String.format(Locale.US, "%.1f", v).replace('.', ',')

    private fun seconds(ms: Long) = "${dec1(ms / 1000.0)} s"

    fun ageText(ms: Long): String {
        val m = ms.coerceAtLeast(0)
        return when {
            m < 10_000 -> "hace ${seconds(m)}"
            m < 60_000 -> "hace ${m / 1000} s"
            else -> "hace ${m / 60_000} min ${(m / 1000) % 60} s"
        }
    }

    private const val WINDOW = 4
    private const val RECENT_MS = 30_000L
}
