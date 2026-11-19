package ar.edu.uade.ronda.core.scenarios

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.model.Severity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Los cinco escenarios del plan §4.1, de punta a punta por el repositorio y con tiempo virtual. */
class EscenariosTest {

    private fun ScenarioRig.startFullRound(): String {
        assertTrue(repo.availability.value.getValue(CommandType.START_MISSION).enabled)
        return send(CommandType.START_MISSION, CommandParams(routeId = RouteId.RONDA_COMPLETA))
    }

    @Test
    fun `escenario 1 ronda normal - 8 puntos, completada, regreso y en base`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "primera telemetría") { rig.repo.telemetry.value != null }
        assertEquals(ConnectionState.CONNECTED, rig.repo.connection.value)

        val id = rig.startFullRound()
        assertEquals(CommandStatus.PENDING, rig.statusOf(id), "enviada pero sin respuesta todavía")
        assertFalse(rig.repo.availability.value.getValue(CommandType.START_MISSION).enabled, "el pendiente bloquea otros envíos")

        rig.until(what = "orden verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }

        rig.untilState(OperationalState.COMPLETADA)
        assertEquals(8, rig.telemetry.service.checkpointsVerified)
        assertEquals(0, rig.telemetry.service.checkpointsWithIssues)
        assertEquals(8, rig.telemetry.mission?.checkpointIndex)

        rig.untilState(OperationalState.REGRESANDO)
        rig.untilState(OperationalState.EN_BASE)
        assertNull(rig.telemetry.mission)
        assertEquals(ConnectionState.CONNECTED, rig.repo.connection.value)
        assertEquals(setOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTED), rig.connectionsSeen, "nunca degradó ni perdió datos")
    }

    @Test
    fun `escenario 2 bateria baja - alerta a 25, propuesta de regreso a 15 y el operador confirma`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(SimScenario.LOW_BATTERY)
        rig.until(what = "telemetría") { rig.repo.telemetry.value?.battery?.percent == 40 }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)

        rig.until(what = "alerta batería") { rig.events.any { it is RobotEvent.AlertRaised && it.alert.type == AlertType.LOW_BATTERY } }
        val alerta = rig.events.filterIsInstance<RobotEvent.AlertRaised>().first { it.alert.type == AlertType.LOW_BATTERY }
        assertTrue("alcanza para volver" in alerta.alert.message, alerta.alert.message)
        assertTrue(rig.telemetry.battery.percent <= 25)
        assertTrue(rig.telemetry.service.checkpointsVerified in 1..7, "la alerta salta a mitad de ronda")

        rig.untilState(OperationalState.REQUIERE_DECISION)
        val incidente = assertNotNull(rig.repo.activeIncident.value)
        assertEquals(IncidentType.BATTERY_RETURN, incidente.type)
        assertEquals(listOf("RETURN_TO_SAFE_POINT"), incidente.options.map { it.id })
        assertTrue(rig.telemetry.battery.percent <= 15)

        val id = rig.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))
        rig.until(what = "regreso verificado") { rig.statusOf(id) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.EN_BASE)
        assertTrue(rig.telemetry.battery.percent > 0, "llegó con batería")
        assertNull(rig.repo.activeIncident.value)
    }

    @Test
    fun `escenario 3 desconexion - datos viejos, ordenes sin respuesta y resincronizacion de eventos`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(10_000)
        val antes = rig.telemetry.timestamp

        rig.control.cutLinkFor(15_000)
        rig.advance(2_000)
        rig.send(CommandType.PAUSE_MISSION, CommandParams(missionId = rig.missionId)).let { pausar ->
            // un evento ocurre mientras no hay enlace
            rig.control.injectFault()
            rig.advance(4_500)
            assertEquals(CommandStatus.NO_RESPONSE, rig.statusOf(pausar), "la orden nunca llegó")
        }
        assertTrue(rig.telemetry.timestamp <= antes + 1_000, "la telemetría quedó congelada")
        assertEquals(ConnectionState.NO_DATA, rig.repo.connection.value)
        assertTrue(ConnectionState.DEGRADED in rig.connectionsSeen)

        val disponibles = rig.repo.availability.value.filterValues { it.enabled }.keys
        assertEquals(setOf(CommandType.OPERATIONAL_STOP), disponibles, "solo queda la detención")
        assertTrue(rig.events.none { it is RobotEvent.AlertRaised && it.alert.type == AlertType.ROBOT_FAULT }, "el evento retenido no llegó")

        rig.advance(6_000)
        assertEquals(ConnectionState.DISCONNECTED, rig.repo.connection.value)

        rig.advance(4_000) // t = 16 s del corte: el enlace volvió a los 15
        rig.until(what = "reconexión", maxMs = 5_000) { rig.repo.connection.value == ConnectionState.CONNECTED }
        val fault = rig.events.filterIsInstance<RobotEvent.AlertRaised>().single { it.alert.type == AlertType.ROBOT_FAULT }
        assertTrue(fault.timestamp < rig.clock.nowMs() - 5_000, "conserva el timestamp original del corte")
        assertEquals(OperationalState.FALLA, rig.state)
    }

    @Test
    fun `escenario 3b desconexion - el robot se detiene solo y la detencion enviada sin enlace queda sin respuesta`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(10_000)
        rig.control.cutLinkFor(15_000)
        rig.advance(1_000)
        val stop = rig.send(CommandType.OPERATIONAL_STOP)
        assertEquals(CommandStatus.PENDING, rig.statusOf(stop))
        rig.advance(4_000)
        assertEquals(CommandStatus.NO_RESPONSE, rig.statusOf(stop))

        rig.until(what = "enlace de vuelta") { rig.repo.connection.value == ConnectionState.CONNECTED }
        assertEquals(OperationalState.DETENIDO, rig.state, "el robot se detuvo y espera al operador")
        val resume = rig.send(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId))
        rig.until(what = "reanudada") { rig.statusOf(resume) == CommandStatus.VERIFIED }
        assertEquals(OperationalState.EN_RONDA, rig.state)
    }

    @Test
    fun `escenario 4 obstaculo en PC4 - se detiene, ofrece tres opciones y saltar continua la ronda`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(SimScenario.OBSTACLE)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.REQUIERE_DECISION)

        val incidente = assertNotNull(rig.repo.activeIncident.value)
        assertEquals(IncidentType.OBSTACLE, incidente.type)
        assertEquals(4, incidente.checkpointIndex)
        assertEquals(listOf("WAIT_30S", "SKIP_CHECKPOINT", "RETURN_TO_SAFE_POINT"), incidente.options.map { it.id })
        assertEquals(0.0, rig.telemetry.motion.speedMps)
        assertEquals(3, rig.telemetry.service.checkpointsVerified)
        val disp = rig.repo.availability.value
        assertTrue(disp.getValue(CommandType.RESUME_MISSION).enabled)
        assertTrue(disp.getValue(CommandType.OPERATIONAL_STOP).enabled)
        assertFalse(disp.getValue(CommandType.PAUSE_MISSION).enabled)

        val id = rig.send(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId, decision = IncidentDecision.SKIP_CHECKPOINT))
        rig.until(what = "decisión verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        assertNull(rig.repo.activeIncident.value)

        rig.untilState(OperationalState.COMPLETADA)
        assertEquals(7, rig.telemetry.service.checkpointsVerified, "el PC4 se saltó")
        assertEquals(1, rig.telemetry.service.checkpointsWithIssues)
        rig.untilState(OperationalState.EN_BASE)
    }

    @Test
    fun `escenario 5 persona en sala tecnica - el operador envia al vigilante, el robot espera pausado y la ronda sigue al reanudar`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(SimScenario.PERSON_IN_RESTRICTED_ZONE)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.REQUIERE_DECISION)

        val incidente = assertNotNull(rig.repo.activeIncident.value)
        assertEquals(IncidentType.PERSON_IN_RESTRICTED_ZONE, incidente.type)
        assertEquals(5, incidente.checkpointIndex)
        assertEquals("sim-persona", incidente.snapshot?.id)
        assertEquals(listOf("AUTHORIZED_PERSON", "SEND_GUARD", "CONTINUE_ROUND"), incidente.options.map { it.id })
        val alerta = rig.telemetry.alerts.single { it.type == AlertType.PERSON_DETECTED }

        // enviar vigilante: cierra la decisión y el robot queda pausado esperando la orden del operador
        val vigilante = rig.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alerta.id, resolution = AlertResolution.SEND_GUARD))
        rig.until(what = "alerta reconocida") { rig.statusOf(vigilante) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.PAUSADA)
        assertNull(rig.repo.activeIncident.value, "la hoja de decisión no vuelve")
        rig.advance(10_000)
        assertEquals(OperationalState.PAUSADA, rig.state)
        assertNull(rig.repo.activeIncident.value)
        assertTrue(rig.repo.availability.value.getValue(CommandType.RESUME_MISSION).enabled)
        assertEquals(1, rig.events.count { it is RobotEvent.DecisionRequired }, "no se vuelve a pedir la decisión")

        val reanudar = rig.send(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId))
        rig.until(what = "reanudada") { rig.statusOf(reanudar) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.COMPLETADA)
        assertEquals(8, rig.telemetry.service.checkpointsVerified)
        assertEquals(1, rig.telemetry.service.checkpointsWithIssues, "el PC5 queda con observación")
        assertIs<RobotEvent.DecisionRequired>(rig.events.last { it is RobotEvent.DecisionRequired })
    }

    @Test
    fun `rechazo forzado muestra el motivo y no altera al robot`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.control.rejectNextCommand(RejectReason.LOW_BATTERY, "Batería insuficiente (simulada).")
        val id = rig.startFullRound()
        rig.until(what = "rechazo") { rig.statusOf(id) == CommandStatus.REJECTED }
        val c = rig.repo.commands.value.single()
        assertEquals(RejectReason.LOW_BATTERY, c.reasonCode)
        assertEquals("Batería insuficiente (simulada).", c.reasonText)
        rig.advance(5_000)
        assertEquals(OperationalState.EN_BASE, rig.state)
    }

    @Test
    fun `respuesta demorada mas que el ttl - la orden ejecutada se verifica por telemetria sin confirmacion`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.control.setResponseDelayMs(6_000)
        val id = rig.startFullRound()
        rig.advance(3_500)
        assertEquals(CommandStatus.PENDING, rig.statusOf(id), "hasta el ttl se espera la respuesta")
        rig.advance(1_000)
        assertEquals(CommandStatus.VERIFIED, rig.statusOf(id), "la telemetría mostró el efecto: no queda sin respuesta")
        assertTrue(rig.repo.commands.value.single { it.id == id }.verifiedWithoutAck)
        rig.advance(5_000)
        assertEquals(CommandStatus.VERIFIED, rig.statusOf(id), "la respuesta tardía no cambia nada")
        assertEquals(OperationalState.EN_RONDA, rig.state)
    }

    @Test
    fun `respuesta demorada de una orden que no se ejecuto sigue sin respuesta`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.control.setResponseDelayMs(6_000)
        rig.control.rejectNextCommand(RejectReason.INVALID_STATE, null)
        val id = rig.startFullRound()
        rig.advance(10_000)
        assertEquals(CommandStatus.NO_RESPONSE, rig.statusOf(id))
        assertEquals(OperationalState.EN_BASE, rig.state)
    }

    @Test
    fun `latencia alta degrada la conexion aunque los datos sean frescos`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.control.setLatencyMs(1_800)
        rig.until(what = "degradada", maxMs = 3_000) { rig.repo.connection.value == ConnectionState.DEGRADED }
        assertFalse(rig.repo.availability.value.getValue(CommandType.START_MISSION).enabled, "no se inicia con enlace degradado")
    }

    // ---- Correcciones del QA ----

    @Test
    fun `modo acelerado - a x8 la ronda completa termina en poco mas de un minuto y la conexion no se degrada`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.control.setTimeScale(8.0)
        assertEquals(8.0, rig.control.settings.value.timeScale)
        val t0 = rig.clock.nowMs()
        val id = rig.startFullRound()
        rig.until(what = "verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.EN_BASE, maxMs = 120_000)
        assertTrue(rig.clock.nowMs() - t0 < 90_000, "tardó ${rig.clock.nowMs() - t0} ms")
        assertEquals(8, rig.telemetry.service.checkpointsVerified)
        assertEquals(setOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTED), rig.connectionsSeen.toSet(), "la conexión sigue el reloj real")
    }

    @Test
    fun `modo acelerado - cargar un escenario o reiniciar no cambia la escala y otro valor se rechaza`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.setTimeScale(4.0)
        rig.control.loadScenario(SimScenario.OBSTACLE)
        assertEquals(4.0, rig.control.settings.value.timeScale)
        rig.control.reset()
        assertEquals(4.0, rig.control.settings.value.timeScale)
        kotlin.test.assertFailsWith<IllegalArgumentException> { rig.control.setTimeScale(3.0) }
        assertEquals(4.0, rig.control.settings.value.timeScale)
    }

    @Test
    fun `detencion durante la decision - el incidente se conserva y la decision se reemite al reanudar`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(SimScenario.OBSTACLE)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.REQUIERE_DECISION)
        val incidente = assertNotNull(rig.repo.activeIncident.value)

        val stop = rig.send(CommandType.OPERATIONAL_STOP)
        rig.until(what = "detención verificada") { rig.statusOf(stop) == CommandStatus.VERIFIED }
        rig.advance(3_000)
        assertEquals(OperationalState.DETENIDO, rig.state)
        assertEquals(incidente.id, rig.repo.activeIncident.value?.id, "el incidente sigue activo mientras está detenido")
        assertTrue(rig.repo.availability.value.getValue(CommandType.RESUME_MISSION).enabled)

        val decisiones = rig.events.count { it is RobotEvent.DecisionRequired }
        val resume = rig.send(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId))
        rig.until(what = "reanudada") { rig.statusOf(resume) == CommandStatus.VERIFIED }
        assertEquals(OperationalState.REQUIERE_DECISION, rig.state)
        assertEquals(decisiones + 1, rig.events.count { it is RobotEvent.DecisionRequired }, "se reemitió la decisión")
        assertEquals(incidente.id, rig.repo.activeIncident.value?.id)
    }

    @Test
    fun `escenario 3 - la alerta de enlace perdido queda retenida y llega al volver con su hora original`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(SimScenario.DISCONNECTION)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(10_000)
        val zona = rig.telemetry.pose.zone
        val corte = rig.clock.nowMs()
        rig.control.cutLinkFor(15_000)
        rig.advance(2_000)
        assertTrue(rig.events.none { it is RobotEvent.AlertRaised && it.alert.type == AlertType.LINK_DEGRADED }, "retenida durante el corte")

        // Las inyecciones durante el corte se rechazan con motivo y no llegan al robot.
        assertNotNull(rig.control.settings.value.incidentInjectionBlockedReason)
        rig.control.injectObstacle()
        assertNotNull(rig.control.settings.value.lastInjectionRejection)
        rig.control.injectPersonInRestrictedZone()

        rig.until(what = "reconexión", maxMs = 20_000) { rig.repo.connection.value == ConnectionState.CONNECTED }
        val alerta = rig.events.filterIsInstance<RobotEvent.AlertRaised>().single { it.alert.type == AlertType.LINK_DEGRADED }
        assertEquals(Severity.WARNING, alerta.severity)
        assertEquals("Enlace perdido: me detuve en $zona", alerta.alert.message)
        assertTrue(alerta.timestamp - corte < 1_000, "conserva la hora del corte")
        assertEquals(OperationalState.DETENIDO, rig.state)
        assertTrue(rig.events.none { it is RobotEvent.DecisionRequired }, "el obstáculo y la persona no se inyectaron")
        rig.advance(1_000)
        assertNull(rig.control.settings.value.incidentInjectionBlockedReason?.takeIf { rig.state == OperationalState.EN_RONDA })
    }

    @Test
    fun `cancelar y luego ir al punto seguro no deja reanudar`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.until(what = "PC2") { rig.repo.telemetry.value?.mission?.checkpointIndex == 2 }
        val cancel = rig.send(CommandType.CANCEL_MISSION, CommandParams(missionId = rig.missionId))
        rig.until(what = "cancelada") { rig.statusOf(cancel) == CommandStatus.VERIFIED }
        rig.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.DESCANSO_ESCALERA_E))
        rig.until(what = "llegó al punto seguro") { rig.state == OperationalState.DETENIDO }
        rig.advance(1_000)
        val disp = rig.repo.availability.value
        assertFalse(disp.getValue(CommandType.RESUME_MISSION).enabled)
        assertFalse(disp.getValue(CommandType.CANCEL_MISSION).enabled)
        assertTrue(disp.getValue(CommandType.RETURN_TO_SAFE_POINT).enabled)
    }

    @Test
    fun `cancelar regresa solo a la base`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(5_000)
        rig.send(CommandType.CANCEL_MISSION, CommandParams(missionId = rig.missionId))
        rig.untilState(OperationalState.CANCELADA, maxMs = 2_000)
        rig.untilState(OperationalState.REGRESANDO, maxMs = 3_000)
        rig.untilState(OperationalState.EN_BASE)
    }

    @Test
    fun `cortar enlace 15 s - tocar el enlace a mano cancela el temporizador`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.cutLinkFor(15_000)
        rig.advance(5_000)
        rig.control.setLinkUp(true)
        rig.advance(3_000)
        rig.control.setLinkUp(false)
        rig.advance(10_000) // ya pasaron los 15 s del primer corte
        assertFalse(rig.control.settings.value.linkUp, "el temporizador viejo no restauró el enlace")
    }

    @Test
    fun `cortar enlace 15 s - reiniciar o cargar un escenario cancela el temporizador`() = runTest {
        val rig = ScenarioRig(this)
        rig.control.cutLinkFor(15_000)
        rig.advance(2_000)
        rig.control.reset()
        rig.control.setLinkUp(false)
        rig.advance(15_000)
        assertFalse(rig.control.settings.value.linkUp, "tras reiniciar")

        rig.control.setLinkUp(true)
        rig.control.cutLinkFor(15_000)
        rig.advance(2_000)
        rig.control.loadScenario(SimScenario.NORMAL)
        rig.control.setLinkUp(false)
        rig.advance(15_000)
        assertFalse(rig.control.settings.value.linkUp, "tras cargar un escenario")
    }

    @Test
    fun `falla - la alerta trae la foto y se sale reconociendola y regresando a la base`() = runTest {
        val rig = ScenarioRig(this)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.startFullRound()
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(5_000)
        rig.control.injectFault()
        rig.untilState(OperationalState.FALLA)
        val falla = rig.events.filterIsInstance<RobotEvent.AlertRaised>().single { it.alert.type == AlertType.ROBOT_FAULT }
        assertEquals("sim-falla", falla.alert.snapshot?.id)
        assertFalse(rig.repo.availability.value.getValue(CommandType.RETURN_TO_SAFE_POINT).enabled)

        val ack = rig.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = falla.alert.id))
        rig.until(what = "falla reconocida") { rig.statusOf(ack) == CommandStatus.VERIFIED }
        assertTrue(rig.repo.availability.value.getValue(CommandType.RETURN_TO_SAFE_POINT).enabled)
        val back = rig.send(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))
        rig.until(what = "regreso verificado") { rig.statusOf(back) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.EN_BASE)
        assertTrue(rig.telemetry.alerts.isEmpty())
    }
}
