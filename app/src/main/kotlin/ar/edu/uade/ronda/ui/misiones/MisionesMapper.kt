package ar.edu.uade.ronda.ui.misiones

import ar.edu.uade.ronda.ui.model.ConnectionView
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.policy.BatteryPolicy
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.policy.CommandContext
import ar.edu.uade.ronda.core.policy.CommandPolicy
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.BuildingPlan
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.LastCommandUi
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper
import ar.edu.uade.ronda.ui.session.ControlSession

internal data class MisionesInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val commands: List<TrackedCommand>,
    val selectedRoute: RouteId,
    val nowMs: Long,
    /** Robot que controla este operador (ver `ControlSession`); null si no tomó el control. */
    val controlledId: String? = null,
    /** Decisión que el robot espera, si la hay: la política la necesita para evaluar. */
    val incident: Incident? = null,
)

/**
 * Elección de ruta y verificación previa. La habilitación de "Iniciar ronda" sale de
 * [CommandPolicy] con la ruta elegida (el repositorio la evalúa solo para la ronda completa).
 */
internal object MisionesMapper {
    private val battery = BatteryPolicy()
    private val policy = CommandPolicy(battery)

    fun map(raw: MisionesInputs): MisionesUiState {
        val i = raw.copy(connection = ConnectionView.effective(raw.connection, raw.lastTelemetryAt, raw.telemetry?.latencyMs, raw.nowMs))
        val t = i.telemetry
        val base = SupervisionMapper.map(
            SupervisionInputs(
                t, i.connection, i.lastTelemetryAt, null, i.commands,
                emptyMap<CommandType, CommandAvailability>(), CheckpointLog(), null, null, i.nowMs,
            ),
        )
        val stale = t == null || i.connection.isStale
        val ageMs = i.lastTelemetryAt?.let { (i.nowMs - it).coerceAtLeast(0) }
        val pct = t?.battery?.percent

        val preflight = preflight(i, stale, pct, ageMs)
        val failed = preflight.filter { it.level != StatusLevel.OK }

        val policyResult = policy.evaluate(
            CommandType.START_MISSION,
            CommandContext(
                state = t?.operationalState ?: OperationalState.EN_BASE,
                connection = i.connection,
                batteryPercent = pct ?: 0,
                hasPendingCommand = i.commands.any { it.status == CommandStatus.PENDING && it.command.type != CommandType.ACKNOWLEDGE_ALERT },
                alerts = t?.alerts.orEmpty(),
                selectedRoute = i.selectedRoute,
                hasMission = t?.mission != null,
                pendingDecision = i.incident != null,
            ),
        )
        val lastStart = i.commands.lastOrNull { it.command.type == CommandType.START_MISSION }
        val start = when {
            i.controlledId == null ->
                CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.DISABLED, ControlSession.NO_CONTROL_REASON)
            lastStart?.status == CommandStatus.PENDING ->
                CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.PENDING, "Esperando respuesta del robot")
            !policyResult.enabled -> CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.DISABLED, policyResult.reason)
            failed.isNotEmpty() -> CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.DISABLED, failed.first().label)
            else -> CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY)
        }

        val batteryShort = pct != null && !stale && t?.operationalState == OperationalState.EN_BASE &&
            !battery.canStart(pct, i.selectedRoute)
        val strip = if (batteryShort) {
            base.strip.copy(needsAction = true, actionText = "Sí: cargá la batería o elegí una ruta más corta")
        } else base.strip

        return MisionesUiState(
            strip = strip,
            routes = RouteId.entries.map { routeCard(it, it == i.selectedRoute) },
            preflightTitle = "ANTES DE SALIR · ${i.selectedRoute.displayName.uppercase()}",
            preflight = preflight,
            start = start,
            currentMission = t?.mission?.takeIf { t.operationalState != OperationalState.EN_BASE }
                ?.let { "${it.name} · PC ${it.checkpointIndex}/${it.checkpointsTotal}" },
            lastStart = lastStart?.let { lastStartUi(it, i.nowMs) },
        )
    }

    private fun routeCard(route: RouteId, selected: Boolean): RouteCardUi {
        val stops = BuildingPlan.stops(route)
        val floors = stops.map { it.floor }.distinct().sorted().joinToString(" y ") { BuildingPlan.floorLabel(it) }
        val description = when (route) {
            RouteId.RONDA_COMPLETA -> stops.joinToString(" → ") { it.name }
            RouteId.SOLO_SALA_TECNICA -> "Para controlar la temperatura de la sala técnica entre rondas."
            RouteId.SALIDAS_EMERGENCIA -> "Verifica que las dos salidas estén libres."
        }
        val ids = if (route == RouteId.RONDA_COMPLETA) floors else stops.joinToString(", ") { it.id }
        return RouteCardUi(
            route.name, route.displayName,
            "${route.checkpointCount} PC · $ids · necesita ${battery.percentToStart(route)} %",
            description, selected,
        )
    }

    private fun preflight(i: MisionesInputs, stale: Boolean, pct: Int?, ageMs: Long?): List<PreflightItemUi> {
        val route = i.selectedRoute
        val need = battery.percentToStart(route)
        val batteryItem = when {
            pct == null || stale -> PreflightItemUi("Batería sin datos vigentes", "—", StatusLevel.STALE, "No se puede verificar sin telemetría reciente.")
            battery.canStart(pct, route) -> PreflightItemUi("Batería suficiente", "$pct %", StatusLevel.OK, "Necesita $need % para volver con margen.")
            else -> {
                val alternative = RouteId.entries.filter { it != route && battery.canStart(pct, it) }
                    .minByOrNull { it.checkpointCount }
                PreflightItemUi(
                    "Batería insuficiente para ${route.displayName.lowercase()} (necesita $need %)", "$pct %", StatusLevel.ERROR,
                    alternative?.let { "Con $pct % alcanza para \"${it.displayName}\"." } ?: "Cargá la batería antes de salir.",
                )
            }
        }
        val connItem = when (i.connection) {
            ConnectionState.CONNECTED ->
                PreflightItemUi("Conexión estable", SupervisionMapper.ageText(ageMs ?: 0).removePrefix("hace "), StatusLevel.OK,
                    "Último dato recibido ${SupervisionMapper.ageText(ageMs ?: 0)}.")
            ConnectionState.DEGRADED ->
                PreflightItemUi("Conexión degradada", SupervisionMapper.ageText(ageMs ?: 0).removePrefix("hace "), StatusLevel.WARNING,
                    "Esperá a que se estabilice para iniciar.")
            else -> PreflightItemUi("Sin conexión con el robot", "—", StatusLevel.STALE,
                ageMs?.let { "Último dato ${SupervisionMapper.ageText(it)}." } ?: "Todavía no llegó telemetría.")
        }
        val critical = i.telemetry?.unacknowledgedAlerts.orEmpty().count { it.severity == Severity.CRITICAL }
        val alertsItem = when {
            stale -> PreflightItemUi("Alertas críticas sin verificar", "—", StatusLevel.STALE)
            critical == 0 -> PreflightItemUi("Sin alertas críticas activas", "0", StatusLevel.OK)
            else -> PreflightItemUi("Hay alertas críticas sin reconocer", critical.toString(), StatusLevel.ERROR, "Revisalas en Alertas.")
        }
        // Control exclusivo: sin tomarlo en Selección no se puede iniciar (mismo gate que Supervisión y Control).
        val controlItem = if (i.controlledId != null) {
            PreflightItemUi("Tenés el control del robot", "vos", StatusLevel.OK)
        } else {
            PreflightItemUi("No tenés el control del robot", "—", StatusLevel.ERROR, "Tomá el control en Selección.")
        }
        return listOf(batteryItem, connItem, alertsItem, controlItem)
    }

    private fun lastStartUi(c: TrackedCommand, nowMs: Long): LastCommandUi {
        val chip = when (c.status) {
            CommandStatus.PENDING -> CommandChipStatus.PENDING
            CommandStatus.CONFIRMED -> CommandChipStatus.CONFIRMED
            CommandStatus.VERIFIED -> CommandChipStatus.VERIFIED
            CommandStatus.REJECTED -> CommandChipStatus.REJECTED
            CommandStatus.NO_RESPONSE -> CommandChipStatus.NO_RESPONSE
        }
        val detail = when {
            c.status == CommandStatus.REJECTED -> c.reasonText ?: "El robot rechazó la orden"
            c.status == CommandStatus.NO_RESPONSE -> "no se sabe si se ejecutó"
            c.verifiedWithoutAck -> "${SupervisionMapper.VERIFIED_WITHOUT_ACK} · ${SupervisionMapper.ageText(nowMs - c.sentAt)}"
            else -> SupervisionMapper.ageText(nowMs - c.sentAt)
        }
        val route = c.command.params.routeId?.displayName ?: "ronda"
        return LastCommandUi("Iniciar: $route", CommandChipUi(chip, c.verifiedWithoutAck), detail)
    }
}
