package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.contract.ContractCodec
import ar.edu.uade.ronda.core.contract.ContractMessage
import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandAck
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotTelemetry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandTrackerTest {
    private val t0 = 1_000_000L
    private val tracker = CommandTracker()

    private fun telemetry(state: OperationalState, at: Long, acknowledgeAlert: Boolean = false): RobotTelemetry {
        val base = (ContractCodec.decode(File("../docs/contrato/telemetria.json").readText()) as ContractMessage.Telemetry).value
        return base.copy(
            operationalState = state, timestamp = at,
            alerts = base.alerts.map { it.copy(acknowledged = acknowledgeAlert) },
        )
    }

    private fun command(id: String, type: CommandType, params: CommandParams = CommandParams(), ttl: Long = 4000) =
        Command(id, "RONDA-01", t0, "garita-pb", type, params, ttl)

    private fun ack(id: String, status: AckStatus, reason: RejectReason? = null, text: String? = null) =
        CommandAck("r-$id", id, "RONDA-01", t0 + 100, status, reason, text)

    private fun status(id: String) = tracker.commands.value.single { it.id == id }.status

    @Test
    fun `un comando nuevo queda pendiente`() {
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        assertEquals(CommandStatus.PENDING, status("c-1"))
        assertTrue(tracker.hasPendingMissionCommand())
    }

    @Test
    fun `confirmada no es verificada hasta que la telemetria refleja el efecto`() {
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 100)
        assertEquals(CommandStatus.CONFIRMED, status("c-1"))
        assertFalse(tracker.hasPendingMissionCommand())

        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 + 200), t0 + 200)
        assertEquals(CommandStatus.CONFIRMED, status("c-1"), "sigue en ronda: todavía no se ejecutó")

        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 + 700), t0 + 700)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
    }

    @Test
    fun `rechazada conserva el motivo para mostrarlo`() {
        tracker.register(command("c-1", CommandType.RESUME_MISSION), t0)
        tracker.onAck(ack("c-1", AckStatus.REJECTED, RejectReason.INVALID_STATE, "No hay nada que reanudar."), t0 + 100)
        val c = tracker.commands.value.single()
        assertEquals(CommandStatus.REJECTED, c.status)
        assertEquals(RejectReason.INVALID_STATE, c.reasonCode)
        assertEquals("No hay nada que reanudar.", c.reasonText)
    }

    @Test
    fun `sin respuesta recien al cumplirse el ttl`() {
        tracker.register(command("c-1", CommandType.PAUSE_MISSION, ttl = 4000), t0)
        tracker.expire(t0 + 3_999)
        assertEquals(CommandStatus.PENDING, status("c-1"))
        tracker.expire(t0 + 4_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
        assertFalse(tracker.hasPendingMissionCommand())
    }

    @Test
    fun `una respuesta tardia no revive un comando sin respuesta`() {
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.expire(t0 + 4_000)
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 5_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
    }

    @Test
    fun `una telemetria anterior al envio no verifica`() {
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 100)
        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 - 500), t0 + 120)
        assertEquals(CommandStatus.CONFIRMED, status("c-1"))
    }

    @Test
    fun `si la telemetria llega antes que la respuesta se verifica al confirmar`() {
        tracker.register(command("c-1", CommandType.OPERATIONAL_STOP), t0)
        tracker.onTelemetry(telemetry(OperationalState.DETENIDO, t0 + 50), t0 + 50)
        assertEquals(CommandStatus.PENDING, status("c-1"))
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 100)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
    }

    @Test
    fun `reconocer alerta se verifica cuando la alerta figura reconocida`() {
        tracker.register(command("c-1", CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = "a-0007")), t0)
        assertFalse(tracker.hasPendingMissionCommand(), "no bloquea otras ordenes")
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 100)
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 + 200), t0 + 200)
        assertEquals(CommandStatus.CONFIRMED, status("c-1"))
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 + 700, acknowledgeAlert = true), t0 + 700)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
    }

    @Test
    fun `el historial se acota`() {
        val small = CommandTracker(maxHistory = 3)
        repeat(5) { small.register(command("c-$it", CommandType.PAUSE_MISSION), t0) }
        assertEquals(listOf("c-2", "c-3", "c-4"), small.commands.value.map { it.id })
    }

    // ---- Verificación por telemetría sin respuesta ----

    private fun tracked(id: String) = tracker.commands.value.single { it.id == id }

    @Test
    fun `una orden sin respuesta pasa a verificada si la telemetria muestra el efecto`() {
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        assertEquals(OperationalState.EN_RONDA, tracked("c-1").stateAtSend)
        tracker.expire(t0 + 4_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 + 5_000), t0 + 5_000)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
        assertTrue(tracked("c-1").verifiedWithoutAck)
    }

    @Test
    fun `una orden pendiente con el efecto visible se verifica al vencer en vez de quedar sin respuesta`() {
        tracker.onTelemetry(telemetry(OperationalState.EN_BASE, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.START_MISSION), t0)
        tracker.onTelemetry(telemetry(OperationalState.PREPARANDO, t0 + 500), t0 + 500)
        assertEquals(CommandStatus.PENDING, status("c-1"), "todavía puede llegar la respuesta")
        tracker.expire(t0 + 4_000)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
        assertTrue(tracked("c-1").verifiedWithoutAck)
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 6_000)
        assertEquals(CommandStatus.VERIFIED, status("c-1"), "la respuesta tardía no cambia nada")
    }

    @Test
    fun `si la respuesta llega a tiempo la verificacion es la normal`() {
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 + 100), t0 + 100)
        tracker.onAck(ack("c-1", AckStatus.CONFIRMED), t0 + 200)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
        assertFalse(tracked("c-1").verifiedWithoutAck)
    }

    @Test
    fun `sin respuesta no se verifica si el estado ya era el esperado al enviar`() {
        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.expire(t0 + 4_000)
        tracker.onTelemetry(telemetry(OperationalState.PAUSADA, t0 + 5_000), t0 + 5_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
    }

    @Test
    fun `sin respuesta no se verifica con un cambio que no corresponde a la orden`() {
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.PAUSE_MISSION), t0)
        tracker.expire(t0 + 4_000)
        tracker.onTelemetry(telemetry(OperationalState.DETENIDO, t0 + 5_000), t0 + 5_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
    }

    @Test
    fun `sin telemetria previa al envio no se verifica sin respuesta`() {
        tracker.register(command("c-1", CommandType.OPERATIONAL_STOP), t0)
        tracker.expire(t0 + 4_000)
        tracker.onTelemetry(telemetry(OperationalState.DETENIDO, t0 + 5_000), t0 + 5_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
    }

    @Test
    fun `reconocer alerta sin respuesta se verifica cuando la alerta figura reconocida`() {
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 - 200), t0 - 200)
        tracker.register(command("c-1", CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = "a-0007")), t0)
        tracker.expire(t0 + 4_000)
        assertEquals(CommandStatus.NO_RESPONSE, status("c-1"))
        tracker.onTelemetry(telemetry(OperationalState.EN_RONDA, t0 + 5_000, acknowledgeAlert = true), t0 + 5_000)
        assertEquals(CommandStatus.VERIFIED, status("c-1"))
    }
}
