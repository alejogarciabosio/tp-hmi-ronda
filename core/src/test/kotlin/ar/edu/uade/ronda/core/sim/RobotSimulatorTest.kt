package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.contract.ContractCodec
import ar.edu.uade.ronda.core.contract.ContractMessage
import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandAck
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Maneja el simulador a mano: avanza el tiempo y habla JSON, como lo haría el transporte. */
class SimHarness(val sim: RobotSimulator = RobotSimulator(Random(7))) {
    var now = 1_763_500_000_000L
    var last: RobotTelemetry? = null
    val events = mutableListOf<RobotEvent>()
    private var seq = 0

    private fun collect(outs: List<SimOutput>): CommandAck? {
        var ack: CommandAck? = null
        for (o in outs) when (val m = ContractCodec.decode(o.json)) {
            is ContractMessage.Telemetry -> last = m.value
            is ContractMessage.Event -> events += m.value
            is ContractMessage.Ack -> ack = m.value
            is ContractMessage.CommandMsg -> error("el robot no emite comandos")
        }
        return ack
    }

    fun tick(): RobotTelemetry { now += 500; collect(sim.tick(now)); return last!! }

    fun run(ms: Long) { repeat((ms / 500).toInt()) { tick() } }

    fun runUntil(maxMs: Long = 900_000, cond: (RobotTelemetry) -> Boolean): RobotTelemetry {
        var waited = 0L
        while (last == null || !cond(last!!)) {
            check(waited <= maxMs) { "condición no alcanzada; estado ${last?.operationalState}, pc ${last?.mission?.checkpointIndex}" }
            tick(); waited += 500
        }
        return last!!
    }

    fun send(type: CommandType, params: CommandParams = CommandParams(), issuedAt: Long = now, ttl: Long = 4000): CommandAck {
        val cmd = Command("c-${++seq}", "RONDA-01", issuedAt, "garita-pb", type, params, ttl)
        return collect(sim.onMessage(now, ContractCodec.encode(cmd)))!!
    }

    fun start(route: RouteId = RouteId.RONDA_COMPLETA) =
        send(CommandType.START_MISSION, CommandParams(routeId = route))

    val missionId get() = last!!.mission!!.id
}

class RobotSimulatorTest {

    @Test
    fun `arranca en base cargando y sin mision`() {
        val h = SimHarness()
        val t = h.tick()
        assertEquals(OperationalState.EN_BASE, t.operationalState)
        assertEquals(100, t.battery.percent)
        assertEquals(null, t.mission)
        assertEquals(24.5, t.service.techRoomTempC)
        assertTrue(t.service.emergencyExitsClear.all { it.status == ExitStatus.NO_VERIFICADA })
    }

    @Test
    fun `iniciar confirma, prepara y sale a la ronda`() {
        val h = SimHarness(); h.tick()
        val ack = h.start()
        assertEquals(AckStatus.CONFIRMED, ack.status)
        assertEquals(OperationalState.PREPARANDO, h.tick().operationalState)
        val t = h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        assertEquals(1, t.mission?.checkpointIndex)
        assertEquals(8, t.mission?.checkpointsTotal)
    }

    @Test
    fun `ronda completa verifica los 8 puntos y vuelve a base`() {
        val h = SimHarness(); h.tick(); h.start()
        val vistos = mutableListOf<Int>()
        h.runUntil { t ->
            if (t.mission != null && t.service.checkpointsVerified !in vistos) vistos += t.service.checkpointsVerified
            t.operationalState == OperationalState.COMPLETADA
        }
        assertEquals((0..8).toList(), vistos, "los puntos se verifican de a uno")
        val fin = h.runUntil { it.operationalState == OperationalState.EN_BASE }
        assertEquals(null, fin.mission)
        assertEquals(0.0, fin.pose.x)
        assertEquals(0, fin.pose.floor)
        assertTrue(fin.service.emergencyExitsClear.all { it.status == ExitStatus.LIBRE }, "las salidas se verifican en PC2 y PC6")
    }

    @Test
    fun `la bateria baja proporcional a la velocidad y la ronda completa gasta entre 5 y 10 por ciento`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        val detenido = h.sim.batteryPercent
        // detenido en verificación gasta mucho menos que caminando
        val a = h.sim.batteryPercent; h.run(10_000); val caminando = a - h.sim.batteryPercent
        assertTrue(caminando > 0.1, "caminando 10 s gasta $caminando")
        h.runUntil { it.operationalState == OperationalState.EN_BASE }
        assertTrue(h.sim.batteryPercent in 90.0..95.0, "batería final ${h.sim.batteryPercent} (partió de 100, inicio $detenido)")
    }

    @Test
    fun `pausar y reanudar conservan el avance`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.mission?.checkpointIndex == 2 && it.motion.mode == MotionMode.CAMINANDO }
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.PAUSE_MISSION, CommandParams(missionId = h.missionId)).status)
        val pausada = h.tick()
        assertEquals(OperationalState.PAUSADA, pausada.operationalState)
        assertEquals(0.0, pausada.motion.speedMps)
        h.run(20_000)
        assertEquals(pausada.pose.x, h.last!!.pose.x)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId)).status)
        assertEquals(OperationalState.EN_RONDA, h.tick().operationalState)
    }

    @Test
    fun `el robot rechaza ordenes invalidas con motivo`() {
        val h = SimHarness(); h.tick()
        val pausar = h.send(CommandType.PAUSE_MISSION)
        assertEquals(RejectReason.INVALID_STATE, pausar.reasonCode)
        assertNotNull(pausar.reasonText)
        h.sim.setBatteryPercent(30)
        assertEquals(RejectReason.LOW_BATTERY, h.start().reasonCode)
        h.sim.setBatteryPercent(80)
        assertEquals(AckStatus.CONFIRMED, h.start().status)
        assertEquals(RejectReason.INVALID_STATE, h.start().reasonCode)
        h.tick()
        assertEquals(RejectReason.UNKNOWN_TARGET, h.send(CommandType.CANCEL_MISSION, CommandParams(missionId = "m-otra")).reasonCode)
        assertEquals(RejectReason.UNKNOWN_TARGET, h.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = "a-9999")).reasonCode)
    }

    @Test
    fun `una orden vencida se descarta`() {
        val h = SimHarness(); h.tick()
        val ack = h.send(CommandType.START_MISSION, CommandParams(routeId = RouteId.RONDA_COMPLETA), issuedAt = h.now - 5_000)
        assertEquals(RejectReason.EXPIRED, ack.reasonCode)
        assertEquals(OperationalState.EN_BASE, h.tick().operationalState)
    }

    @Test
    fun `rechazar la proxima orden aplica una sola vez y no ejecuta la orden`() {
        val h = SimHarness(); h.tick()
        h.sim.rejectNextCommand(RejectReason.NOT_CONNECTED, "Gateway sin enlace.")
        val ack = h.start()
        assertEquals(RejectReason.NOT_CONNECTED, ack.reasonCode)
        assertEquals("Gateway sin enlace.", ack.reasonText)
        assertEquals(OperationalState.EN_BASE, h.tick().operationalState)
        assertEquals(AckStatus.CONFIRMED, h.start().status)
    }

    @Test
    fun `cancelar deja la mision cancelada y se puede regresar a base`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        h.run(10_000)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.CANCEL_MISSION, CommandParams(missionId = h.missionId)).status)
        assertEquals(OperationalState.CANCELADA, h.tick().operationalState)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE)).status)
        assertEquals(OperationalState.REGRESANDO, h.tick().operationalState)
        h.runUntil { it.operationalState == OperationalState.EN_BASE }
    }

    @Test
    fun `la detencion operativa frena y reanudar continua`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.mission?.checkpointIndex == 1 && it.motion.mode == MotionMode.CAMINANDO }
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.OPERATIONAL_STOP).status)
        assertEquals(OperationalState.DETENIDO, h.tick().operationalState)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId)).status)
        assertEquals(OperationalState.EN_RONDA, h.tick().operationalState)
    }

    @Test
    fun `solo sala tecnica recorre tres puntos`() {
        val h = SimHarness(); h.tick(); h.start(RouteId.SOLO_SALA_TECNICA)
        val t = h.runUntil { it.operationalState == OperationalState.COMPLETADA }
        assertEquals(3, t.service.checkpointsVerified)
        assertEquals(3, t.mission?.checkpointsTotal)
        h.runUntil { it.operationalState == OperationalState.EN_BASE }
    }

    @Test
    fun `la escalera usa modo ESCALERA y cambia de piso`() {
        val h = SimHarness(); h.tick(); h.start()
        val t = h.runUntil { it.motion.mode == MotionMode.ESCALERA }
        assertTrue(t.motion.speedMps < 0.8)
        val arriba = h.runUntil { it.mission?.checkpointIndex == 4 }
        assertEquals(1, arriba.pose.floor)
    }

    @Test
    fun `obstaculo en PC4 pide decision y saltar el punto lo cuenta como observacion`() {
        val h = SimHarness(); h.sim.planIncident("PC4", IncidentType.OBSTACLE); h.tick(); h.start()
        val t = h.runUntil { it.operationalState == OperationalState.REQUIERE_DECISION }
        assertEquals(4, t.mission?.checkpointIndex)
        assertEquals(3, t.service.checkpointsVerified)
        val ev = h.events.filterIsInstance<RobotEvent.DecisionRequired>().single()
        assertEquals(IncidentType.OBSTACLE, ev.incident.type)
        assertEquals(listOf("WAIT_30S", "SKIP_CHECKPOINT", "RETURN_TO_SAFE_POINT"), ev.incident.options.map { it.id })
        assertTrue(h.events.any { it is RobotEvent.AlertRaised && it.alert.type == AlertType.OBSTACLE })

        val sinDecision = h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId))
        assertEquals(RejectReason.INVALID_STATE, sinDecision.reasonCode)

        h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId, decision = IncidentDecision.SKIP_CHECKPOINT))
        val seguir = h.runUntil { it.mission?.checkpointIndex == 5 }
        assertEquals(3, seguir.service.checkpointsVerified)
        assertEquals(1, seguir.service.checkpointsWithIssues)
        assertTrue(seguir.alerts.single { it.type == AlertType.OBSTACLE }.acknowledged)
    }

    @Test
    fun `obstaculo esperar 30 segundos y reintentar verifica el punto`() {
        val h = SimHarness(); h.sim.planIncident("PC4", IncidentType.OBSTACLE); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.REQUIERE_DECISION }
        h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId, decision = IncidentDecision.WAIT_30S))
        val enEspera = h.tick()
        assertEquals(OperationalState.EN_RONDA, enEspera.operationalState)
        assertEquals(0.0, enEspera.motion.speedMps)
        h.run(20_000)
        assertEquals(0.0, h.last!!.motion.speedMps, "sigue esperando a los 20 s")
        val t = h.runUntil { it.service.checkpointsVerified == 4 }
        assertEquals(0, t.service.checkpointsWithIssues)
    }

    @Test
    fun `persona en sala tecnica se resuelve reconociendo la alerta con resolution`() {
        val h = SimHarness(); h.sim.planIncident("PC5", IncidentType.PERSON_IN_RESTRICTED_ZONE); h.tick(); h.start()
        val t = h.runUntil { it.operationalState == OperationalState.REQUIERE_DECISION }
        assertEquals(5, t.mission?.checkpointIndex)
        val alert = t.alerts.single { it.type == AlertType.PERSON_DETECTED }
        assertFalse(alert.acknowledged)

        // sin resolution no alcanza
        assertEquals(RejectReason.INVALID_STATE, h.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alert.id)).reasonCode)
        // enviar vigilante: el robot sigue esperando
        h.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alert.id, resolution = AlertResolution.SEND_GUARD))
        assertEquals(OperationalState.REQUIERE_DECISION, h.tick().operationalState)
        h.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alert.id, resolution = AlertResolution.CONTINUE_ROUND))
        assertEquals(OperationalState.EN_RONDA, h.tick().operationalState)
        val seguir = h.runUntil { it.service.checkpointsVerified == 5 }
        assertEquals(1, seguir.service.checkpointsWithIssues)
    }

    @Test
    fun `falla detiene al robot y rechaza ordenes de mision`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        h.events.clear()
        h.sim.injectFault(h.now).forEach { }
        val t = h.tick()
        assertEquals(OperationalState.FALLA, t.operationalState)
        assertEquals(0.0, t.motion.speedMps)
        assertEquals(AlertType.ROBOT_FAULT, t.alerts.last().type)
        assertEquals(RejectReason.INVALID_STATE, h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId)).reasonCode)
        assertEquals(RejectReason.INVALID_STATE, h.send(CommandType.RETURN_TO_SAFE_POINT).reasonCode)
    }

    @Test
    fun `salida obstruida se detecta en PC2 y suma una observacion`() {
        val h = SimHarness(); h.sim.setExitObstructed("SE-PB", true); h.tick(); h.start()
        val t = h.runUntil { it.service.checkpointsVerified == 2 }
        assertEquals(ExitStatus.OBSTRUIDA, t.service.emergencyExitsClear.single { it.exitId == "SE-PB" }.status)
        assertEquals(ExitStatus.NO_VERIFICADA, t.service.emergencyExitsClear.single { it.exitId == "SE-1" }.status)
        assertEquals(1, t.service.checkpointsWithIssues)
        assertTrue(t.alerts.any { it.type == AlertType.EXIT_BLOCKED })
    }

    @Test
    fun `la temperatura sube hacia el objetivo, marca tendencia y alerta sobre 27`() {
        val h = SimHarness(); h.tick()
        h.sim.setTechRoomTempC(29.0)
        val subiendo = h.tick()
        assertEquals(ar.edu.uade.ronda.core.model.TempTrend.RISING, subiendo.service.techRoomTempTrend)
        h.run(120_000)
        assertEquals(29.0, h.last!!.service.techRoomTempC)
        assertEquals(ar.edu.uade.ronda.core.model.TempTrend.STABLE, h.last!!.service.techRoomTempTrend)
        assertEquals(1, h.events.filterIsInstance<RobotEvent.AlertRaised>().count { it.alert.type == AlertType.TECH_ROOM_TEMPERATURE })
    }

    @Test
    fun `bateria baja alerta a 25 por ciento y propone regresar a 15`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        h.sim.setBatteryPercent(26)
        h.sim.drainFactor = 20.0
        h.runUntil { it.alerts.any { a -> a.type == AlertType.LOW_BATTERY } }
        h.runUntil { it.operationalState == OperationalState.REQUIERE_DECISION }
        val ev = h.events.filterIsInstance<RobotEvent.DecisionRequired>().last()
        assertEquals(IncidentType.BATTERY_RETURN, ev.incident.type)
        assertTrue(h.last!!.battery.percent <= 15)
    }

    @Test
    fun `con el enlace caido el robot se detiene y a los 60 segundos regresa solo`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        h.run(10_000)
        h.sim.setLinkUp(h.now, false)
        assertEquals(OperationalState.DETENIDO, h.tick().operationalState)
        h.run(58_000)
        assertEquals(OperationalState.DETENIDO, h.last!!.operationalState)
        h.run(3_000)
        assertEquals(OperationalState.REGRESANDO, h.last!!.operationalState)
    }

    @Test
    fun `si el enlace vuelve antes de 60 segundos el robot espera la orden del operador`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.operationalState == OperationalState.EN_RONDA }
        h.sim.setLinkUp(h.now, false)
        h.run(15_000)
        h.sim.setLinkUp(h.now, true)
        h.run(70_000)
        assertEquals(OperationalState.DETENIDO, h.last!!.operationalState)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId)).status)
        assertEquals(OperationalState.EN_RONDA, h.tick().operationalState)
    }

    @Test
    fun `regresar al descanso de la escalera E deja al robot pausado y se puede retomar`() {
        val h = SimHarness(); h.tick(); h.start()
        h.runUntil { it.mission?.checkpointIndex == 4 && it.motion.mode == MotionMode.CAMINANDO }
        h.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.DESCANSO_ESCALERA_E))
        val t = h.runUntil { it.operationalState == OperationalState.PAUSADA }
        assertEquals(60.0, t.pose.x)
        assertEquals(AckStatus.CONFIRMED, h.send(CommandType.RESUME_MISSION, CommandParams(missionId = h.missionId)).status)
        h.runUntil { it.operationalState == OperationalState.EN_RONDA && it.motion.mode == MotionMode.CAMINANDO }
        h.runUntil { it.service.checkpointsVerified == 4 }
    }

    @Test
    fun `mismo seed produce la misma secuencia`() {
        fun trace(): List<String> {
            val h = SimHarness(RobotSimulator(Random(99))); h.tick(); h.start()
            return (1..200).map { h.tick().let { t -> "${t.latencyMs}/${t.pose.x}/${t.battery.percent}" } }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `reset vuelve al estado inicial`() {
        val h = SimHarness(); h.tick(); h.start(); h.run(30_000)
        h.sim.reset()
        val t = h.tick()
        assertEquals(OperationalState.EN_BASE, t.operationalState)
        assertEquals(100, t.battery.percent)
        assertIs<Unit>(Unit)
    }
}
