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
import ar.edu.uade.ronda.core.model.Snapshot
import ar.edu.uade.ronda.core.model.TempTrend
import ar.edu.uade.ronda.core.policy.BatteryPolicy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import ar.edu.uade.ronda.core.contract.nextUuidV4
import kotlin.random.Random

enum class OutputKind { TELEMETRY, ACK, EVENT }

/** Mensaje JSON que el robot simulado le entrega al transporte. */
data class SimOutput(val kind: OutputKind, val json: String)

/**
 * Robot simulado. No usa corrutinas: el transporte llama a [tick] y a [onMessage] con la hora real,
 * así los escenarios son deterministas con tiempo virtual. Habla JSON a través de [ContractCodec].
 * No es thread-safe: el transporte serializa los accesos.
 *
 * Lleva dos relojes. La hora real (`nowMs` de cada llamada) estampa los mensajes y decide si una
 * orden llegó vencida. El reloj de simulación ([simNowMs]) avanza la hora real multiplicada por
 * [timeScale] y gobierna todo lo físico: desplazamiento, fases (preparación, verificación, espera
 * de 30 s, regreso a los 60 s sin enlace), batería y temperatura.
 */
class RobotSimulator(
    private val random: Random = Random(1),
    // Aparte de `random` para que cambiar los ids no altere la latencia simulada de los escenarios.
    private val idRandom: Random = Random(2),
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
    // Relojes: la hora real de la última llamada y el reloj de simulación (ver la doc de la clase).
    private var lastRealMs: Long? = null
    private var simNowMs = 0L
    private var lastTickSimMs: Long? = null
    private var timeScaleValue = 1.0
    private var phase = Phase.NONE
    private var phaseUntil = 0L
    private var holdStartMs = 0L
    private var stoppedFrom: OperationalState? = null
    // Con una detención operativa durante una decisión: de dónde venía el robot antes de la decisión.
    private var stoppedBeforeDecision: OperationalState? = null
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
    val timeScale: Double get() = timeScaleValue

    /** Reloj de simulación en ms. Arranca igual a la hora real del primer llamado. */
    val simNow: Long get() = simNowMs

    /** Lleva el reloj de simulación hasta la hora real `nowMs`, a la escala vigente. */
    private fun syncClock(nowMs: Long) {
        val last = lastRealMs
        if (last == null) simNowMs = nowMs
        else simNowMs += ((nowMs - last).coerceAtLeast(0) * timeScaleValue).toLong()
        lastRealMs = nowMs
    }

    /** Cambia la escala de tiempo (×1, ×4 u ×8). Lo transcurrido hasta `nowMs` se cuenta con la escala anterior. */
    fun setTimeScale(nowMs: Long, scale: Double) {
        require(scale in ALLOWED_TIME_SCALES) { "Escala de tiempo no permitida: $scale (válidas: $ALLOWED_TIME_SCALES)" }
        syncClock(nowMs)
        timeScaleValue = scale
    }

    // ---- Control ----

    fun reset() {
        // La escala de tiempo y los relojes se conservan: reiniciar el robot no cambia la velocidad de la demo.
        state = EN_BASE; batteryPct = 100.0; phase = Phase.NONE; phaseUntil = 0; stoppedFrom = null; stoppedBeforeDecision = null
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

    /** Por qué ahora no se puede inyectar un obstáculo o una persona, o `null` si se puede. */
    fun incidentInjectionBlockedReason(): String? =
        if (state == EN_RONDA && routeActive) null
        else "El robot tiene que estar en ronda para inyectar un obstáculo o una persona."

    fun injectObstacle(nowMs: Long): List<SimOutput> {
        syncClock(nowMs)
        if (incidentInjectionBlockedReason() == null) raiseObstacle(nowMs)
        return drain()
    }

    fun injectPerson(nowMs: Long): List<SimOutput> {
        syncClock(nowMs)
        if (incidentInjectionBlockedReason() == null) raisePerson(nowMs)
        return drain()
    }

    fun injectFault(nowMs: Long): List<SimOutput> {
        syncClock(nowMs)
        if (state != FALLA) {
            stopMoving()
            state = FALLA
            incident = null
            stoppedFrom = null; stoppedBeforeDecision = null
            linkHold = false
            phase = Phase.NONE
            raiseAlert(nowMs, AlertType.ROBOT_FAULT, Severity.CRITICAL, "Falla del robot. Requiere técnico.", snapshot("sim-falla", nowMs))
        }
        return drain()
    }

    /**
     * El transporte avisa cuando el enlace se corta o vuelve. Al cortarse, el robot se detiene y
     * levanta una alerta `LINK_DEGRADED`; el transporte la retiene y la entrega al volver el enlace.
     */
    fun setLinkUp(nowMs: Long, up: Boolean): List<SimOutput> {
        syncClock(nowMs)
        if (!up) {
            if (linkDownSince == null) linkDownSince = simNowMs
            // Supuesto declarado: ante pérdida de enlace el robot se detiene y espera.
            if (state.inMotion) {
                if (state != REQUIERE_DECISION) {
                    enterHold(DETENIDO)
                    linkHold = true
                }
                raiseAlert(nowMs, AlertType.LINK_DEGRADED, Severity.WARNING, "Enlace perdido: me detuve en ${currentZone()}")
            }
        } else {
            linkDownSince = null
        }
        return drain()
    }

    // ---- Entrada de mensajes ----

    fun onMessage(nowMs: Long, text: String): List<SimOutput> {
        syncClock(nowMs)
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
            messageId = idRandom.nextUuidV4(),
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
        if (state == FALLA) faultRejection(cmd)?.let { return it }
        val p = cmd.params
        // Las órdenes sobre una misión tienen que apuntar a la misión actual.
        if (cmd.type in MISSION_TARGETED && p.missionId != null && p.missionId != missionId) {
            return Rejection(RejectReason.UNKNOWN_TARGET, "La misión ${p.missionId} no es la actual.")
        }
        return when (cmd.type) {
            CommandType.START_MISSION -> start(p.routeId, now)
            CommandType.PAUSE_MISSION -> {
                if (state != EN_RONDA) return invalid("La misión no está en curso; no hay nada que pausar.")
                enterHold(PAUSADA); null
            }
            CommandType.RESUME_MISSION -> resume(p.decision, now)
            CommandType.CANCEL_MISSION -> {
                if (state !in CANCELABLE) return invalid("No hay una misión para cancelar.")
                stopMoving(); routeActive = false; phase = Phase.NONE; incident = null
                state = CANCELADA; stoppedFrom = null; stoppedBeforeDecision = null; linkHold = false
                // Igual que al completar: a los 2 s el robot emprende solo el regreso a la base.
                phaseUntil = simNowMs + COMPLETED_HOLD_MS
                null
            }
            CommandType.RETURN_TO_SAFE_POINT -> {
                if (state == EN_BASE) return invalid("El robot ya está en la base.")
                if (state == REGRESANDO) return invalid("El robot ya está regresando.")
                val target = if (p.safePointId == SafePointId.DESCANSO_ESCALERA_E) SimMap.restPoint else SimMap.base
                if (state == FALLA) {
                    // Salida de la falla: regreso a la base para que lo atienda el técnico. La misión se da por terminada.
                    routeActive = false
                }
                startReturn(target); null
            }
            CommandType.OPERATIONAL_STOP -> {
                if (!state.inMotion) return invalid("El robot no está en movimiento.")
                enterHold(DETENIDO); null
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
        phaseUntil = simNowMs + PREPARING_MS
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
                        state = EN_RONDA; phase = Phase.WAITING_OBSTACLE; phaseUntil = simNowMs + OBSTACLE_WAIT_MS
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
                if (state == DETENIDO && stoppedFrom == REQUIERE_DECISION && incident != null) {
                    // Detenido durante una decisión: reanudar vuelve a pedir la decisión pendiente.
                    exitHold(now)
                    return null
                }
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
        if (alert.type == AlertType.PERSON_DETECTED && state == DETENIDO && stoppedFrom == REQUIERE_DECISION && incident != null) {
            return invalid("El robot está detenido: reanudá para volver a la decisión pendiente y resolverla.")
        }
        if (alert.type == AlertType.PERSON_DETECTED && state == REQUIERE_DECISION) {
            if (resolution == null) return invalid("Indicá cómo se resuelve la detección de persona.")
            // Cualquier decisión del operador cierra el incidente: no se vuelve a ofrecer la hoja.
            replaceAlert(alert.copy(acknowledged = true))
            incident = null
            when (resolution) {
                AlertResolution.AUTHORIZED_PERSON -> exitHold(now)
                AlertResolution.CONTINUE_ROUND -> {
                    issueOnNextVerify = true
                    exitHold(now)
                }
                AlertResolution.SEND_GUARD -> {
                    // El vigilante va en camino: el robot mantiene la distancia y queda pausado en el
                    // punto hasta que el operador reanude. La persona queda como observación del punto.
                    issueOnNextVerify = true
                    state = PAUSADA
                }
            }
            return null
        }
        replaceAlert(alert.copy(acknowledged = true))
        return null
    }

    // ---- Avance del tiempo ----

    fun tick(nowMs: Long): List<SimOutput> {
        syncClock(nowMs)
        // dt en segundos de simulación: la hora real transcurrida por la escala de tiempo.
        val dt = lastTickSimMs?.let { ((simNowMs - it) / 1000.0).coerceAtLeast(0.0) } ?: 0.0
        lastTickSimMs = simNowMs

        speedMps = 0.0
        mode = MotionMode.DETENIDO
        advance(nowMs, dt)
        updateBattery(dt)
        updateTemperature(dt)
        checkThresholds(nowMs)
        outbox += SimOutput(OutputKind.TELEMETRY, ContractCodec.encode(buildTelemetry(nowMs)))
        return drain()
    }

    /** `now` es la hora real (para estampar alertas); las fases se comparan con el reloj de simulación. */
    private fun advance(now: Long, dt: Double) {
        val sim = simNowMs
        when (state) {
            PREPARANDO -> if (sim >= phaseUntil) {
                state = EN_RONDA; phase = Phase.NONE
                startLeg(stops[nextStop])
            }
            EN_RONDA -> when (phase) {
                Phase.VERIFYING -> if (sim >= phaseUntil) verifyCheckpoint(now)
                Phase.WAITING_OBSTACLE -> if (sim >= phaseUntil) phase = Phase.NONE
                Phase.NONE -> travel(now, dt)
            }
            REGRESANDO -> travel(now, dt)
            COMPLETADA, CANCELADA -> if (sim >= phaseUntil) startReturn(SimMap.base)
            DETENIDO -> {
                val down = linkDownSince
                if (linkHold && down != null && sim - down >= LINK_LOSS_RETURN_MS) {
                    linkHold = false
                    startReturn(SimMap.base)
                }
            }
            else -> {}
        }
    }

    /**
     * Tramo de escalera dentro del tramo actual, en metros recorridos desde el inicio:
     * va en el medio del recorrido plano y mide [BuildingLayout.STAIRS_EXTRA_M]. `null` si no cambia de piso.
     */
    private fun stairsSpan(): ClosedFloatingPointRange<Double>? {
        if (legTarget.floor == legStartFloor) return null
        val flat = legLength - STAIRS_EXTRA_M
        val from = flat / 2
        return from..(from + STAIRS_EXTRA_M)
    }

    private fun travel(now: Long, dt: Double) {
        // Los incidentes programados frenan al robot a 2 m del punto de control.
        var maxDistance = legRemaining
        val planned = if (state == EN_RONDA) plannedIncidents[legTarget.id] else null
        if (planned != null) {
            // Con tolerancia: la suma de pasos en coma flotante puede dejar el robot a 2 m + epsilon.
            if (legRemaining <= INCIDENT_DISTANCE_M + 1e-6) {
                plannedIncidents.remove(legTarget.id)
                when (planned) {
                    IncidentType.PERSON_IN_RESTRICTED_ZONE -> raisePerson(now)
                    else -> raiseObstacle(now)
                }
                return
            }
            maxDistance = legRemaining - INCIDENT_DISTANCE_M
        }

        // La velocidad de escalera rige solo en el tramo de escalera; el resto del tramo se camina.
        val span = stairsSpan()
        var timeLeft = dt
        var moved = 0.0
        var onStairs = false
        while (timeLeft > 1e-9 && moved < maxDistance - 1e-9) {
            val traveled = legLength - legRemaining + moved
            val inStairs = span != null && traveled >= span.start - 1e-9 && traveled < span.endInclusive - 1e-9
            val speed = if (inStairs) STAIRS_SPEED_MPS else WALK_SPEED_MPS
            val boundary = when {
                span == null -> Double.MAX_VALUE
                traveled < span.start - 1e-9 -> span.start - traveled
                inStairs -> span.endInclusive - traveled
                else -> Double.MAX_VALUE
            }
            val d = min(min(speed * timeLeft, boundary), maxDistance - moved)
            moved += d
            timeLeft -= d / speed
            if (inStairs && d > 0) onStairs = true
        }
        legRemaining -= moved
        if (dt > 0) {
            speedMps = moved / dt
            mode = if (onStairs) MotionMode.ESCALERA else MotionMode.CAMINANDO
        }
        updatePose()
        if (legRemaining <= 1e-6) {
            legRemaining = 0.0
            arrive(now)
        }
    }

    private fun arrive(now: Long) {
        speedMps = 0.0
        mode = MotionMode.DETENIDO
        if (state == EN_RONDA) {
            phase = Phase.VERIFYING
            phaseUntil = simNowMs + VERIFY_MS
            return
        }
        // REGRESANDO
        if (legTarget.id == SimMap.base.id) {
            state = EN_BASE
            routeActive = false; missionId = null; missionName = ""; stops = emptyList()
            alerts.clear(); incident = null
        } else if (routeActive) {
            state = PAUSADA
            stoppedFrom = EN_RONDA
        } else {
            // Llegó al punto seguro sin una ruta activa (p. ej. tras cancelar): queda detenido y sin
            // misión, así no hay nada que reanudar. Se sale regresando a la base.
            state = DETENIDO
            stoppedFrom = null
            missionId = null; missionName = ""; stops = emptyList(); nextStop = 0
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
            phaseUntil = simNowMs + COMPLETED_HOLD_MS
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
        stoppedFrom = null; stoppedBeforeDecision = null
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

    private fun enterHold(newState: OperationalState) {
        if (state == REQUIERE_DECISION) {
            // Detención durante una decisión: se recuerda de dónde venía y no se reinicia el inicio de la
            // espera, que sigue contando desde que apareció el incidente.
            stoppedBeforeDecision = stoppedFrom
        } else {
            holdStartMs = simNowMs
        }
        stoppedFrom = state
        state = newState
        stopMoving()
    }

    private fun exitHold(now: Long) {
        val back = stoppedFrom ?: EN_RONDA
        if (back == REQUIERE_DECISION) {
            // Vuelve a la decisión pendiente y la reemite: la app tiene que volver a mostrar las opciones.
            state = REQUIERE_DECISION
            stoppedFrom = stoppedBeforeDecision
            stoppedBeforeDecision = null
            linkHold = false
            incident?.let { emitDecision(now, it) }
            return
        }
        // El tiempo detenido no cuenta para los temporizadores (verificación, preparación, espera).
        if (phaseUntil > 0) phaseUntil += simNowMs - holdStartMs
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
                    nextId("i"), IncidentType.BATTERY_RETURN, currentCheckpointNumber(),
                    "Batería crítica. El robot propone regresar a la base.",
                    listOf(IncidentOption("RETURN_TO_SAFE_POINT", "Regresar a la base", SafePointId.BASE)),
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

    private fun currentZone(): String = if (legLength > 0 && legRemaining / legLength > 0.5) legOriginZone else legTarget.zone

    /** Rechazo de una orden con el robot en FALLA. La única salida es reconocer la falla y regresar a la base. */
    private fun faultRejection(cmd: Command): Rejection? = when (cmd.type) {
        CommandType.ACKNOWLEDGE_ALERT -> null
        CommandType.RETURN_TO_SAFE_POINT -> when {
            alerts.any { it.type == AlertType.ROBOT_FAULT && !it.acknowledged } ->
                Rejection(RejectReason.UNACKNOWLEDGED_ALERT, "Reconocé la falla antes de ordenar el regreso a la base.")
            cmd.params.safePointId == SafePointId.DESCANSO_ESCALERA_E ->
                invalid("Con una falla el robot solo puede regresar a la base, donde lo atiende el técnico.")
            else -> null
        }
        else -> invalid("El robot tiene una falla y no acepta órdenes de misión.")
    }

    // ---- Incidentes y alertas ----

    /** Posición 1-based en la ruta: es el progreso de la misión (`mission.checkpointIndex`). */
    private fun currentPosition(): Int = min(nextStop + 1, stops.size.coerceAtLeast(1))

    /**
     * Número del punto de control en el edificio (PC5 → 5), el mismo del plano. Es el que llevan los
     * incidentes, para que el título y las opciones usen una sola numeración en cualquier ruta.
     */
    private fun currentCheckpointNumber(): Int {
        val stop = stops.getOrNull(min(nextStop, stops.size - 1)) ?: return currentPosition()
        return stop.id.removePrefix("PC").toIntOrNull() ?: currentPosition()
    }

    private fun raiseObstacle(now: Long) {
        val stop = stops[nextStop]
        raiseAlert(now, AlertType.OBSTACLE, Severity.WARNING, "Objeto detectado en el pasillo, a ${INCIDENT_DISTANCE_M.toInt()} m.", snapshot("sim-obstaculo", now))
        requireDecision(
            now,
            Incident(
                nextId("i"), IncidentType.OBSTACLE, currentCheckpointNumber(),
                "Un objeto bloquea el ${stop.zone}. El robot se detuvo.",
                listOf(
                    IncidentOption("WAIT_30S", "Esperar 30 s"),
                    IncidentOption("SKIP_CHECKPOINT", "Saltar el ${stop.id}"),
                    IncidentOption("RETURN_TO_SAFE_POINT", "Regresar a la base", SafePointId.BASE),
                ),
                snapshot("sim-obstaculo", now),
            ),
        )
    }

    private fun raisePerson(now: Long) {
        val stop = stops[nextStop]
        raiseAlert(now, AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona detectada en ${stop.zone}. El robot mantiene 2 m y se identificó por parlante.", snapshot("sim-persona", now))
        requireDecision(
            now,
            Incident(
                nextId("i"), IncidentType.PERSON_IN_RESTRICTED_ZONE, currentCheckpointNumber(),
                "Persona en zona restringida (${stop.zone}) fuera de horario.",
                listOf(
                    IncidentOption("AUTHORIZED_PERSON", "Persona autorizada (registrar)"),
                    IncidentOption("SEND_GUARD", "Enviar vigilante"),
                    IncidentOption("CONTINUE_ROUND", "Continuar ronda"),
                ),
                snapshot("sim-persona", now),
            ),
        )
    }

    private fun requireDecision(now: Long, inc: Incident) {
        incident = inc
        enterHold(REQUIERE_DECISION)
        emitDecision(now, inc)
    }

    private fun emitDecision(now: Long, inc: Incident) {
        outbox += SimOutput(
            OutputKind.EVENT,
            ContractCodec.encode(RobotEvent.DecisionRequired(idRandom.nextUuidV4(), robotId, now, Severity.CRITICAL, inc)),
        )
    }

    private fun resolveIncident(alertType: AlertType) {
        incident = null
        alerts.firstOrNull { it.type == alertType && !it.acknowledged }?.let { replaceAlert(it.copy(acknowledged = true)) }
        stoppedFrom = null; stoppedBeforeDecision = null
        linkHold = false
    }

    /** El simulador no genera imagen: manda solo el id y la app la resuelve (`data` null). */
    private fun snapshot(id: String, now: Long) = Snapshot(id, "image/jpeg", now)

    private fun raiseAlert(now: Long, type: AlertType, severity: Severity, message: String, snapshot: Snapshot? = null) {
        val alert = Alert(nextId("a"), type, severity, message, now, acknowledged = false, snapshot = snapshot)
        alerts += alert
        outbox += SimOutput(
            OutputKind.EVENT,
            ContractCodec.encode(RobotEvent.AlertRaised(idRandom.nextUuidV4(), robotId, now, severity, alert)),
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
        val zone = currentZone()
        return RobotTelemetry(
            messageId = idRandom.nextUuidV4(),
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
        return "$prefix-" + n.toString().padStart(4, '0')
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
        const val IDLE_PERCENT_PER_S = 0.007
        const val CHARGE_PERCENT_PER_S = 0.015
        const val LOW_BATTERY_PERCENT = 25.0
        const val CRITICAL_BATTERY_PERCENT = 15.0
        const val TEMP_WARNING_C = 27.0
        const val TEMP_CRITICAL_C = 32.0
        const val TEMP_RATE_C_PER_S = 0.05
        const val TECH_ROOM_CHECKPOINT = "PC5"
        const val LATENCY_BASE_MS = 80
        const val LATENCY_JITTER_MS = 80
        const val MINUTES_PER_PERCENT = 1.25
        const val STAIRS_EXTRA_M = ar.edu.uade.ronda.core.model.BuildingLayout.STAIRS_EXTRA_M
        val ALLOWED_TIME_SCALES = ar.edu.uade.ronda.core.control.SimulationSettings.ALLOWED_TIME_SCALES
        val MISSION_TARGETED = setOf(CommandType.PAUSE_MISSION, CommandType.RESUME_MISSION, CommandType.CANCEL_MISSION)
        val CANCELABLE = setOf(EN_RONDA, PAUSADA, REQUIERE_DECISION, DETENIDO)
    }
}
