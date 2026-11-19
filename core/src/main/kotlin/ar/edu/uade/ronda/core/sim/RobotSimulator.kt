package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.contract.ContractCodec
import ar.edu.uade.ronda.core.contract.ContractException
import ar.edu.uade.ronda.core.contract.ContractMessage
import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.Battery
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandAck
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.EmergencyExit
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentOption
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.MissionProgress
import ar.edu.uade.ronda.core.model.Motion
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.OperationalState.CANCELADA
import ar.edu.uade.ronda.core.model.OperationalState.COMPLETADA
import ar.edu.uade.ronda.core.model.OperationalState.DETENIDO
import ar.edu.uade.ronda.core.model.OperationalState.EN_BASE
import ar.edu.uade.ronda.core.model.OperationalState.EN_RONDA
import ar.edu.uade.ronda.core.model.OperationalState.FALLA
import ar.edu.uade.ronda.core.model.OperationalState.PAUSADA
import ar.edu.uade.ronda.core.model.OperationalState.PREPARANDO
import ar.edu.uade.ronda.core.model.OperationalState.REGRESANDO
import ar.edu.uade.ronda.core.model.OperationalState.REQUIERE_DECISION
import ar.edu.uade.ronda.core.model.Pose
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.model.ServiceTelemetry
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.TempTrend
import ar.edu.uade.ronda.core.policy.BatteryPolicy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class OutputKind { TELEMETRY, ACK, EVENT }

/** Mensaje JSON que el robot simulado le entrega al transporte. */
data class SimOutput(val kind: OutputKind, val json: String)

/**
 * Robot simulado. No usa corrutinas ni reloj propio: el transporte llama a [tick] y a [onMessage]
 * con la hora actual, así los escenarios son deterministas con tiempo virtual. Habla JSON
 * a través de [ContractCodec]. No es thread-safe: el transporte serializa los accesos.
 */
class RobotSimulator(
    private val random: Random = Random(1),
    private val batteryPolicy: BatteryPolicy = BatteryPolicy(),
    val robotId: String = "RONDA-01",
) {
    private enum class Phase { NONE, VERIFYING, WAITING_OBSTACLE }

    private class Rejection(val reason: RejectReason, val text: String)

    private val outbox = mutableListOf<SimOutput>()
    private val counters = mutableMapOf<String, Int>()

    // Estado del robot
    private var state = EN_BASE
    private var batteryPct = 100.0
    private var lastTickMs: Long? = null
    private var phase = Phase.NONE
    private var phaseUntil = 0L
    private var holdStartMs = 0L
    private var stoppedFrom: OperationalState? = null
    private var linkHold = false
    private var linkDownSince: Long? = null

    // Misión
    private var routeActive = false
    private var stops: List<Waypoint> = emptyList()
    private var nextStop = 0
    private var missionId: String? = null
    private var missionName = ""
    private var verified = 0
    private var issues = 0
    private var issueOnNextVerify = false

    // Movimiento
    private var x = SimMap.base.x
    private var y = SimMap.base.y
    private var floor = SimMap.base.floor
    private var legStartX = x
    private var legStartY = y
    private var legStartFloor = floor
    private var legTarget: Waypoint = SimMap.base
    private var legLength = 0.0
    private var legRemaining = 0.0
    private var speedMps = 0.0
    private var mode = MotionMode.DETENIDO
    private var headingDeg = 0.0
    private var legOriginZone = SimMap.base.zone

    // Alertas e incidentes
    private val alerts = mutableListOf<Alert>()
    private var incident: Incident? = null
    private var lowBatteryAlerted = false
    private var batteryReturnProposed = false
    private var tempWarnAlerted = false
    private var tempCritAlerted = false

    // Servicio
    private var techRoomTempC = DEFAULT_TEMP_C
    private var techRoomTargetC = DEFAULT_TEMP_C
    private var tempTrend = TempTrend.STABLE
    private val exits = linkedMapOf(
        "SE-PB" to ("Salida de emergencia PB" to ExitStatus.NO_VERIFICADA),
        "SE-1" to ("Salida de emergencia 1°" to ExitStatus.NO_VERIFICADA),
    )
    private val obstructedExits = mutableSetOf<String>()

    // Configuración del panel de simulación
    var drainFactor = 1.0
    var latencyOverrideMs: Int? = null
    private var forcedRejection: Rejection? = null
    private val plannedIncidents = mutableMapOf<String, IncidentType>()

    val currentState: OperationalState get() = state
    val batteryPercent: Double get() = batteryPct

    // ---- Control ----

    fun reset() {
        state = EN_BASE; batteryPct = 100.0; phase = Phase.NONE; phaseUntil = 0; stoppedFrom = null
        linkHold = false; linkDownSince = null
        routeActive = false; stops = emptyList(); nextStop = 0; missionId = null; missionName = ""
        verified = 0; issues = 0; issueOnNextVerify = false
        x = SimMap.base.x; y = SimMap.base.y; floor = SimMap.base.floor
        legTarget = SimMap.base; legLength = 0.0; legRemaining = 0.0
        speedMps = 0.0; mode = MotionMode.DETENIDO
        alerts.clear(); incident = null
        lowBatteryAlerted = false; batteryReturnProposed = false; tempWarnAlerted = false; tempCritAlerted = false
        techRoomTempC = DEFAULT_TEMP_C; techRoomTargetC = DEFAULT_TEMP_C; tempTrend = TempTrend.STABLE
        resetExits(); obstructedExits.clear()
        drainFactor = 1.0; latencyOverrideMs = null; forcedRejection = null; plannedIncidents.clear()
        outbox.clear()
    }

    fun setBatteryPercent(percent: Int) {
        batteryPct = percent.coerceIn(0, 100).toDouble()
        if (batteryPct > LOW_BATTERY_PERCENT) lowBatteryAlerted = false
        if (batteryPct > CRITICAL_BATTERY_PERCENT) batteryReturnProposed = false
    }

    fun setTechRoomTempC(celsius: Double) {
        techRoomTargetC = celsius
    }

    fun setExitObstructed(exitId: String, obstructed: Boolean) {
        if (obstructed) obstructedExits += exitId else obstructedExits -= exitId
    }

    fun rejectNextCommand(reason: RejectReason, text: String?) {
        forcedRejection = Rejection(reason, text ?: defaultRejectionText(reason))
    }

    /** Programa un incidente que salta al acercarse al punto de control `checkpointId` (p. ej. "PC4"). */
    fun planIncident(checkpointId: String, type: IncidentType) {
        plannedIncidents[checkpointId] = type
    }

    fun injectObstacle(nowMs: Long): List<SimOutput> {
        if (state == EN_RONDA && routeActive) raiseObstacle(nowMs)
        return drain()
    }

    fun injectPerson(nowMs: Long): List<SimOutput> {
        if (state == EN_RONDA && routeActive) raisePerson(nowMs)
        return drain()
    }

    fun injectFault(nowMs: Long): List<SimOutput> {
        if (state != FALLA) {
            stopMoving()
            state = FALLA
            incident = null
            raiseAlert(nowMs, AlertType.ROBOT_FAULT, Severity.CRITICAL, "Falla del robot. Requiere técnico.")
        }
        return drain()
    }

    /** El transporte avisa cuando el enlace se corta o vuelve; el robot no "sabe" que la app no lo ve. */
    fun setLinkUp(nowMs: Long, up: Boolean) {
        if (!up) {
            if (linkDownSince == null) linkDownSince = nowMs
            // Supuesto declarado: ante pérdida de enlace el robot se detiene y espera.
            if (state.inMotion && state != REQUIERE_DECISION) {
                enterHold(DETENIDO, nowMs)
                linkHold = true
            }
        } else {
            linkDownSince = null
        }
    }

    // ---- Entrada de mensajes ----

    fun onMessage(nowMs: Long, text: String): List<SimOutput> {
        val msg = try {
            ContractCodec.decode(text)
        } catch (e: ContractException) {
            return emptyList()
        }
        if (msg is ContractMessage.CommandMsg) handleCommand(nowMs, msg.value)
        return drain()
    }

    private fun handleCommand(now: Long, cmd: Command) {
        val rejection = when {
            cmd.robotId != robotId -> Rejection(RejectReason.UNKNOWN_TARGET, "El robot ${cmd.robotId} no existe.")
            now > cmd.issuedAt + cmd.ttlMs -> Rejection(RejectReason.EXPIRED, "La orden llegó vencida y se descartó.")
            else -> forcedRejection?.also { forcedRejection = null } ?: execute(cmd, now)
        }
        val ack = CommandAck(
            messageId = nextId("r"),
            correlationId = cmd.messageId,
            robotId = robotId,
            receivedAt = now,
            status = if (rejection == null) AckStatus.CONFIRMED else AckStatus.REJECTED,
            reasonCode = rejection?.reason,
            reasonText = rejection?.text,
        )
        outbox += SimOutput(OutputKind.ACK, ContractCodec.encode(ack))
    }

    private fun invalid(text: String) = Rejection(RejectReason.INVALID_STATE, text)

    private fun execute(cmd: Command, now: Long): Rejection? {
        if (state == FALLA && cmd.type != CommandType.ACKNOWLEDGE_ALERT) {
            return invalid("El robot tiene una falla y no acepta órdenes de misión.")
        }
        val p = cmd.params
        // Las órdenes sobre una misión tienen que apuntar a la misión actual.
        if (cmd.type in MISSION_TARGETED && p.missionId != null && p.missionId != missionId) {
            return Rejection(RejectReason.UNKNOWN_TARGET, "La misión ${p.missionId} no es la actual.")
        }
        return when (cmd.type) {
            CommandType.START_MISSION -> start(p.routeId, now)
            CommandType.PAUSE_MISSION -> {
                if (state != EN_RONDA) return invalid("La misión no está en curso; no hay nada que pausar.")
                enterHold(PAUSADA, now); null
            }
            CommandType.RESUME_MISSION -> resume(p.decision, now)
            CommandType.CANCEL_MISSION -> {
                if (state !in CANCELABLE) return invalid("No hay una misión para cancelar.")
                stopMoving(); routeActive = false; phase = Phase.NONE; incident = null
                state = CANCELADA; stoppedFrom = null; null
            }
            CommandType.RETURN_TO_SAFE_POINT -> {
                if (state == EN_BASE) return invalid("El robot ya está en la base.")
                if (state == REGRESANDO) return invalid("El robot ya está regresando.")
                val target = if (p.safePointId == SafePointId.DESCANSO_ESCALERA_E) SimMap.restPoint else SimMap.base
                startReturn(target); null
            }
            CommandType.OPERATIONAL_STOP -> {
                if (!state.inMotion) return invalid("El robot no está en movimiento.")
                enterHold(DETENIDO, now); null
            }
            CommandType.ACKNOWLEDGE_ALERT -> acknowledge(p.alertId, p.resolution, now)
        }
    }

    private fun start(routeId: RouteId?, now: Long): Rejection? {
        if (state != EN_BASE) return invalid("El robot tiene que estar en la base para iniciar.")
        if (routeId == null) return Rejection(RejectReason.UNKNOWN_TARGET, "Falta la ruta de la misión.")
        if (!batteryPolicy.canStart(batteryPct.toInt(), routeId)) {
            return Rejection(
                RejectReason.LOW_BATTERY,
                "Batería insuficiente para ${routeId.displayName.lowercase()} (necesita ${batteryPolicy.percentToStart(routeId)} %).",
            )
        }
        alerts.clear()
        resetExits()
        stops = SimMap.stops(routeId)
        nextStop = 0
        verified = 0; issues = 0; issueOnNextVerify = false
        routeActive = true
        missionId = nextId("m"); missionName = routeId.displayName
        lowBatteryAlerted = false; batteryReturnProposed = false
        state = PREPARANDO
        phaseUntil = now + PREPARING_MS
        return null
    }

    private fun resume(decision: IncidentDecision?, now: Long): Rejection? {
        when (state) {
            REQUIERE_DECISION -> {
                val inc = incident ?: return invalid("No hay una decisión pendiente.")
                if (inc.type != IncidentType.OBSTACLE) {
                    return invalid("Esta decisión se resuelve con las opciones que ofrece el robot, no reanudando.")
                }
                return when (decision) {
                    null -> invalid("Hay una decisión pendiente: elegí una de las opciones.")
                    IncidentDecision.WAIT_30S -> {
                        resolveIncident(AlertType.OBSTACLE)
                        state = EN_RONDA; phase = Phase.WAITING_OBSTACLE; phaseUntil = now + OBSTACLE_WAIT_MS
                        null
                    }
                    IncidentDecision.SKIP_CHECKPOINT -> {
                        resolveIncident(AlertType.OBSTACLE)
                        state = EN_RONDA
                        issues++
                        advanceStop(now)
                        null
                    }
                }
            }
            PAUSADA, DETENIDO -> {
                if (alerts.any { !it.acknowledged && it.severity == Severity.CRITICAL }) {
                    return Rejection(RejectReason.UNACKNOWLEDGED_ALERT, "Reconocé la alerta antes de reanudar.")
                }
                val back = stoppedFrom
                if (back == null || (back != REGRESANDO && !routeActive)) return invalid("No hay una misión para reanudar.")
                exitHold(now)
                return null
            }
            else -> return invalid("La misión ya está en curso; no hay nada que reanudar.")
        }
    }

    private fun acknowledge(alertId: String?, resolution: AlertResolution?, now: Long): Rejection? {
        val alert = alerts.firstOrNull { it.id == alertId }
            ?: return Rejection(RejectReason.UNKNOWN_TARGET, "La alerta $alertId no existe.")
        if (alert.acknowledged) return invalid("La alerta ya estaba reconocida.")
        if (alert.type == AlertType.PERSON_DETECTED && state == REQUIERE_DECISION) {
            when (resolution) {
                null -> return invalid("Indicá cómo se resuelve la detección de persona.")
                // El vigilante va en camino: el robot espera y la alerta sigue abierta hasta que se resuelva.
                AlertResolution.SEND_GUARD -> return null
                AlertResolution.AUTHORIZED_PERSON -> {}
                AlertResolution.CONTINUE_ROUND -> issueOnNextVerify = true
            }
            replaceAlert(alert.copy(acknowledged = true))
            incident = null
            exitHold(now)
            return null
        }
        replaceAlert(alert.copy(acknowledged = true))
        return null
    }

    // ---- Avance del tiempo ----

    fun tick(nowMs: Long): List<SimOutput> {
        val dt = lastTickMs?.let { ((nowMs - it) / 1000.0).coerceAtLeast(0.0) } ?: 0.0
        lastTickMs = nowMs

        speedMps = 0.0
        mode = MotionMode.DETENIDO
        advance(nowMs, dt)
        updateBattery(dt)
        updateTemperature(dt)
        checkThresholds(nowMs)
        outbox += SimOutput(OutputKind.TELEMETRY, ContractCodec.encode(buildTelemetry(nowMs)))
        return drain()
    }

    private fun advance(now: Long, dt: Double) {
        when (state) {
            PREPARANDO -> if (now >= phaseUntil) {
                state = EN_RONDA; phase = Phase.NONE
                startLeg(stops[nextStop])
            }
            EN_RONDA -> when (phase) {
                Phase.VERIFYING -> if (now >= phaseUntil) verifyCheckpoint(now)
                Phase.WAITING_OBSTACLE -> if (now >= phaseUntil) phase = Phase.NONE
                Phase.NONE -> travel(now, dt)
            }
            REGRESANDO -> travel(now, dt)
            COMPLETADA -> if (now >= phaseUntil) startReturn(SimMap.base)
            DETENIDO -> {
                val down = linkDownSince
                if (linkHold && down != null && now - down >= LINK_LOSS_RETURN_MS) {
                    linkHold = false
                    startReturn(SimMap.base)
                }
            }
            else -> {}
        }
    }

    private fun travel(now: Long, dt: Double) {
        val stairs = legTarget.floor != legStartFloor
        val speed = if (stairs) STAIRS_SPEED_MPS else WALK_SPEED_MPS
        var step = speed * dt

        // Los incidentes programados frenan al robot a 2 m del punto de control.
        val planned = if (state == EN_RONDA) plannedIncidents[legTarget.id] else null
        if (planned != null) {
            if (legRemaining <= INCIDENT_DISTANCE_M) {
                plannedIncidents.remove(legTarget.id)
                when (planned) {
                    IncidentType.PERSON_IN_RESTRICTED_ZONE -> raisePerson(now)
                    else -> raiseObstacle(now)
                }
                return
            }
            step = min(step, legRemaining - INCIDENT_DISTANCE_M)
        }

        step = min(step, legRemaining)
        legRemaining -= step
        if (dt > 0) {
            speedMps = step / dt
            mode = if (stairs) MotionMode.ESCALERA else MotionMode.CAMINANDO
        }
        updatePose()
        if (legRemaining <= 1e-9) arrive(now)
    }

    private fun arrive(now: Long) {
        speedMps = 0.0
        mode = MotionMode.DETENIDO
        if (state == EN_RONDA) {
            phase = Phase.VERIFYING
            phaseUntil = now + VERIFY_MS
            return
        }
        // REGRESANDO
        if (legTarget.id == SimMap.base.id) {
            state = EN_BASE
            routeActive = false; missionId = null; missionName = ""; stops = emptyList()
            alerts.clear(); incident = null
        } else {
            state = if (routeActive) PAUSADA else DETENIDO
            stoppedFrom = if (routeActive) EN_RONDA else null
        }
    }

    private fun verifyCheckpoint(now: Long) {
        val stop = stops[nextStop]
        phase = Phase.NONE
        var problem = issueOnNextVerify
        issueOnNextVerify = false
        exitIdAt(stop)?.let { exitId ->
            val blocked = exitId in obstructedExits
            exits[exitId] = exits.getValue(exitId).first to (if (blocked) ExitStatus.OBSTRUIDA else ExitStatus.LIBRE)
            if (blocked) {
                problem = true
                raiseAlert(now, AlertType.EXIT_BLOCKED, Severity.WARNING, "${exits.getValue(exitId).first} obstruida.")
            }
        }
        if (stop.id == TECH_ROOM_CHECKPOINT && techRoomTempC > TEMP_WARNING_C) problem = true
        if (problem) issues++
        verified++
        advanceStop(now)
    }

    /** Pasa al punto siguiente o termina la ronda. Se usa al verificar y al saltar un punto. */
    private fun advanceStop(now: Long) {
        nextStop++
        phase = Phase.NONE
        if (nextStop >= stops.size) {
            state = COMPLETADA
            phaseUntil = now + COMPLETED_HOLD_MS
            stopMoving()
        } else {
            startLeg(stops[nextStop])
        }
    }

    private fun startLeg(target: Waypoint) {
        if (!(legLength > 0 && legRemaining / legLength > 0.5)) legOriginZone = legTarget.zone
        legStartX = x; legStartY = y; legStartFloor = floor
        legTarget = target
        legLength = SimMap.legLengthM(x, y, floor, target)
        legRemaining = legLength
        headingDeg = (Math.toDegrees(atan2(target.y - y, target.x - x)) + 360.0) % 360.0
    }

    private fun startReturn(target: Waypoint) {
        phase = Phase.NONE
        incident = null
        linkHold = false
        state = REGRESANDO
        startLeg(target)
    }

    private fun updatePose() {
        val fraction = if (legLength <= 0) 1.0 else 1.0 - legRemaining / legLength
        x = legStartX + (legTarget.x - legStartX) * fraction
        y = legStartY + (legTarget.y - legStartY) * fraction
        floor = if (legStartFloor == legTarget.floor || fraction < 0.5) legStartFloor else legTarget.floor
    }

    private fun stopMoving() {
        speedMps = 0.0
        mode = MotionMode.DETENIDO
    }

    private fun enterHold(newState: OperationalState, now: Long) {
        stoppedFrom = state
        state = newState
        holdStartMs = now
        stopMoving()
    }

    private fun exitHold(now: Long) {
        val back = stoppedFrom ?: EN_RONDA
        // El tiempo detenido no cuenta para los temporizadores (verificación, preparación, espera).
        if (phaseUntil > 0) phaseUntil += now - holdStartMs
        state = if (back == PAUSADA) EN_RONDA else back
        // Si el robot quedó en un punto de descanso, retoma la ruta desde ahí.
        if (state == EN_RONDA && phase == Phase.NONE && routeActive && legTarget !== stops.getOrNull(nextStop)) {
            startLeg(stops[nextStop])
        }
        stoppedFrom = null
        linkHold = false
    }

    // ---- Batería, temperatura y umbrales ----

    private fun updateBattery(dt: Double) {
        if (state == EN_BASE) {
            batteryPct = min(100.0, batteryPct + CHARGE_PERCENT_PER_S * dt)
            return
        }
        val perMeter = batteryPolicy.percentToReturn(1.0)
        // El consumo es proporcional a la velocidad, más un consumo base del robot encendido.
        batteryPct = (batteryPct - (IDLE_PERCENT_PER_S + speedMps * perMeter) * drainFactor * dt).coerceAtLeast(0.0)
    }

    private fun updateTemperature(dt: Double) {
        val delta = techRoomTargetC - techRoomTempC
        val maxStep = TEMP_RATE_C_PER_S * dt
        techRoomTempC += delta.coerceIn(-maxStep, maxStep)
        val remaining = techRoomTargetC - techRoomTempC
        tempTrend = when {
            abs(remaining) < 0.01 -> TempTrend.STABLE
            remaining > 0 -> TempTrend.RISING
            else -> TempTrend.FALLING
        }
    }

    private fun checkThresholds(now: Long) {
        if (state != EN_BASE && batteryPct <= LOW_BATTERY_PERCENT && !lowBatteryAlerted) {
            lowBatteryAlerted = true
            val pct = batteryPct.roundToInt()
            val enough = batteryPolicy.canReturnToBase(batteryPct.toInt(), distanceToBaseM())
            raiseAlert(
                now, AlertType.LOW_BATTERY, Severity.WARNING,
                if (enough) "Batería baja ($pct %): alcanza para volver a la base desde acá."
                else "Batería baja ($pct %): no alcanza para volver a la base. Regresá ya.",
            )
        }
        if (state == EN_RONDA && batteryPct <= CRITICAL_BATTERY_PERCENT && !batteryReturnProposed) {
            batteryReturnProposed = true
            raiseAlert(now, AlertType.LOW_BATTERY, Severity.CRITICAL, "Batería crítica (${batteryPct.roundToInt()} %).")
            requireDecision(
                now,
                Incident(
                    nextId("i"), IncidentType.BATTERY_RETURN, currentPosition(),
                    "Batería crítica. El robot propone regresar a la base.",
                    listOf(IncidentOption("RETURN_TO_SAFE_POINT", "Regresar a la base")),
                ),
            )
        }
        if (techRoomTempC > TEMP_WARNING_C && !tempWarnAlerted) {
            tempWarnAlerted = true
            raiseAlert(now, AlertType.TECH_ROOM_TEMPERATURE, Severity.WARNING, "Sala técnica en ${"%.1f".format(techRoomTempC)} °C (sensor externo).")
        }
        if (techRoomTempC > TEMP_CRITICAL_C && !tempCritAlerted) {
            tempCritAlerted = true
            raiseAlert(now, AlertType.TECH_ROOM_TEMPERATURE, Severity.CRITICAL, "Sala técnica en ${"%.1f".format(techRoomTempC)} °C: sobre el umbral crítico.")
        }
        if (techRoomTempC < TEMP_WARNING_C - 1) tempWarnAlerted = false
        if (techRoomTempC < TEMP_CRITICAL_C - 1) tempCritAlerted = false
    }

    private fun distanceToBaseM(): Double = SimMap.legLengthM(x, y, floor, SimMap.base)

    // ---- Incidentes y alertas ----

    private fun currentPosition(): Int = min(nextStop + 1, stops.size.coerceAtLeast(1))

    private fun raiseObstacle(now: Long) {
        val stop = stops[nextStop]
        raiseAlert(now, AlertType.OBSTACLE, Severity.WARNING, "Objeto detectado en el pasillo, a ${INCIDENT_DISTANCE_M.toInt()} m.")
        requireDecision(
            now,
            Incident(
                nextId("i"), IncidentType.OBSTACLE, currentPosition(),
                "Un objeto bloquea el ${stop.zone}. El robot se detuvo.",
                listOf(
                    IncidentOption("WAIT_30S", "Esperar 30 s"),
                    IncidentOption("SKIP_CHECKPOINT", "Saltar el ${stop.id}"),
                    IncidentOption("RETURN_TO_SAFE_POINT", "Regresar a la base"),
                ),
            ),
        )
    }

    private fun raisePerson(now: Long) {
        val stop = stops[nextStop]
        raiseAlert(now, AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona detectada en ${stop.zone}. El robot mantiene 2 m y se identificó por parlante.")
        requireDecision(
            now,
            Incident(
                nextId("i"), IncidentType.PERSON_IN_RESTRICTED_ZONE, currentPosition(),
                "Persona en zona restringida (${stop.zone}) fuera de horario.",
                listOf(
                    IncidentOption("AUTHORIZED_PERSON", "Persona autorizada (registrar)"),
                    IncidentOption("SEND_GUARD", "Enviar vigilante"),
                    IncidentOption("CONTINUE_ROUND", "Continuar ronda"),
                ),
            ),
        )
    }

    private fun requireDecision(now: Long, inc: Incident) {
        incident = inc
        enterHold(REQUIERE_DECISION, now)
        outbox += SimOutput(
            OutputKind.EVENT,
            ContractCodec.encode(RobotEvent.DecisionRequired(nextId("e"), robotId, now, Severity.CRITICAL, inc)),
        )
    }

    private fun resolveIncident(alertType: AlertType) {
        incident = null
        alerts.firstOrNull { it.type == alertType && !it.acknowledged }?.let { replaceAlert(it.copy(acknowledged = true)) }
        stoppedFrom = null
        linkHold = false
    }

    private fun raiseAlert(now: Long, type: AlertType, severity: Severity, message: String) {
        val alert = Alert(nextId("a"), type, severity, message, now, acknowledged = false)
        alerts += alert
        outbox += SimOutput(
            OutputKind.EVENT,
            ContractCodec.encode(RobotEvent.AlertRaised(nextId("e"), robotId, now, severity, alert)),
        )
    }

    private fun replaceAlert(updated: Alert) {
        val i = alerts.indexOfFirst { it.id == updated.id }
        if (i >= 0) alerts[i] = updated
    }

    // ---- Telemetría ----

    private fun resetExits() {
        exits.keys.toList().forEach { exits[it] = exits.getValue(it).first to ExitStatus.NO_VERIFICADA }
    }

    private fun exitIdAt(stop: Waypoint): String? = when (stop.id) {
        "PC2" -> "SE-PB"
        "PC6" -> "SE-1"
        else -> null
    }

    private fun buildTelemetry(now: Long): RobotTelemetry {
        val pct = batteryPct.roundToInt()
        val mission = missionId?.let {
            MissionProgress(
                id = it,
                name = missionName,
                checkpointIndex = currentPosition(),
                checkpointsTotal = stops.size,
                stage = stageName(),
            )
        }
        val zone = if (legLength > 0 && legRemaining / legLength > 0.5) legOriginZone else legTarget.zone
        return RobotTelemetry(
            messageId = nextId("t"),
            robotId = robotId,
            timestamp = now,
            latencyMs = latencyOverrideMs ?: (LATENCY_BASE_MS + random.nextInt(LATENCY_JITTER_MS)),
            battery = Battery(pct, (batteryPct * MINUTES_PER_PERCENT).toInt(), charging = state == EN_BASE && batteryPct < 100),
            operationalState = state,
            mission = mission,
            pose = Pose(floor, round1(x), round1(y), round1(headingDeg), zone),
            motion = Motion(round1(speedMps), mode),
            alerts = alerts.toList(),
            service = ServiceTelemetry(
                checkpointsVerified = verified,
                checkpointsWithIssues = issues,
                techRoomTempC = round1(techRoomTempC),
                techRoomTempTrend = tempTrend,
                emergencyExitsClear = exits.map { (id, v) -> EmergencyExit(id, v.first, v.second) },
            ),
        )
    }

    private fun stageName(): String = when (state) {
        COMPLETADA -> "Ronda completa"
        REGRESANDO -> if (legTarget.id == SimMap.base.id) "Regreso a la base" else "Regreso a ${legTarget.name.lowercase()}"
        CANCELADA -> "Misión cancelada"
        else -> stops.getOrNull(nextStop)?.name ?: "-"
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    private fun nextId(prefix: String): String {
        val n = (counters[prefix] ?: 0) + 1
        counters[prefix] = n
        return "$prefix-" + n.toString().padStart(if (prefix == "t") 6 else 4, '0')
    }

    private fun drain(): List<SimOutput> = outbox.toList().also { outbox.clear() }

    private fun defaultRejectionText(reason: RejectReason) = when (reason) {
        RejectReason.INVALID_STATE -> "El robot no puede ejecutar la orden en su estado actual."
        RejectReason.LOW_BATTERY -> "Batería insuficiente para la orden."
        RejectReason.NOT_CONNECTED -> "El gateway no tiene enlace con el robot."
        RejectReason.COMMAND_PENDING -> "Hay otra orden en curso."
        RejectReason.UNACKNOWLEDGED_ALERT -> "Hay una alerta sin reconocer."
        RejectReason.EXPIRED -> "La orden llegó vencida."
        RejectReason.UNKNOWN_TARGET -> "El destino de la orden no existe."
    }

    private companion object {
        const val DEFAULT_TEMP_C = 24.5
        const val WALK_SPEED_MPS = 0.8
        const val STAIRS_SPEED_MPS = 0.4
        const val PREPARING_MS = 3_000L
        const val VERIFY_MS = 3_000L
        const val OBSTACLE_WAIT_MS = 30_000L
        const val COMPLETED_HOLD_MS = 2_000L
        const val LINK_LOSS_RETURN_MS = 60_000L
        const val INCIDENT_DISTANCE_M = 2.0
        const val IDLE_PERCENT_PER_S = 0.003
        const val CHARGE_PERCENT_PER_S = 0.01
        const val LOW_BATTERY_PERCENT = 25.0
        const val CRITICAL_BATTERY_PERCENT = 15.0
        const val TEMP_WARNING_C = 27.0
        const val TEMP_CRITICAL_C = 32.0
        const val TEMP_RATE_C_PER_S = 0.05
        const val TECH_ROOM_CHECKPOINT = "PC5"
        const val LATENCY_BASE_MS = 80
        const val LATENCY_JITTER_MS = 80
        const val MINUTES_PER_PERCENT = 1.25
        val MISSION_TARGETED = setOf(CommandType.PAUSE_MISSION, CommandType.RESUME_MISSION, CommandType.CANCEL_MISSION)
        val CANCELABLE = setOf(EN_RONDA, PAUSADA, REQUIERE_DECISION, DETENIDO)
    }
}
