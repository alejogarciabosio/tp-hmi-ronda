package ar.edu.uade.ronda.core.contract

import ar.edu.uade.ronda.core.model.AckStatus
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.model.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ContractCodecTest {

    companion object {
        // Los ejemplos se leen directo de docs/contrato: no hay copia que se desincronice.
        private val dir = File("../docs/contrato").also {
            check(it.isDirectory) { "No se encuentra ${it.absolutePath}" }
        }

        @JvmStatic
        fun contractFiles(): List<File> = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

        private fun load(name: String) = File(dir, name).readText()
        private fun command(name: String) = (ContractCodec.decode(load(name)) as ContractMessage.CommandMsg).value
    }

    @Test
    fun `hay ejemplos para todos los tipos de mensaje`() {
        assertEquals(13, contractFiles().size)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractFiles")
    fun `cada ejemplo de docs-contrato se decodifica y sobrevive a ida y vuelta`(file: File) {
        val first = ContractCodec.decode(file.readText())
        val reencoded = when (first) {
            is ContractMessage.Telemetry -> ContractCodec.encode(first.value)
            is ContractMessage.CommandMsg -> ContractCodec.encode(first.value)
            is ContractMessage.Ack -> ContractCodec.encode(first.value)
            is ContractMessage.Event -> ContractCodec.encode(first.value)
        }
        assertEquals(first, ContractCodec.decode(reencoded))
    }

    @Test
    fun `telemetria mapea todos los campos`() {
        val t = (ContractCodec.decode(load("telemetria.json")) as ContractMessage.Telemetry).value
        assertEquals("RONDA-01", t.robotId)
        assertEquals(1763500000500, t.timestamp)
        assertEquals(120, t.latencyMs)
        assertEquals(78, t.battery.percent)
        assertEquals(OperationalState.EN_RONDA, t.operationalState)
        assertEquals(4, t.mission?.checkpointIndex)
        assertEquals(8, t.mission?.checkpointsTotal)
        assertEquals("Pasillo A", t.pose.zone)
        assertEquals(0.6, t.motion.speedMps)
        assertEquals(AlertType.OBSTACLE, t.alerts.single().type)
        assertEquals(1, t.unacknowledgedAlerts.size)
        assertEquals(24.5, t.service.techRoomTempC)
        assertEquals(listOf(ExitStatus.LIBRE, ExitStatus.NO_VERIFICADA), t.service.emergencyExitsClear.map { it.status })
    }

    @Test
    fun `telemetria sin mision se decodifica con mision nula`() {
        val sinMision = load("telemetria.json").replace(Regex("\"mission\": \\{[^}]*\\},"), "")
        val t = (ContractCodec.decode(sinMision) as ContractMessage.Telemetry).value
        assertNull(t.mission)
    }

    @Test
    fun `los siete comandos mapean tipo y parametros`() {
        assertEquals(RouteId.RONDA_COMPLETA, command("comando-iniciar-mision.json").params.routeId)
        assertEquals("m-20251118-01", command("comando-pausar.json").params.missionId)
        assertEquals(CommandType.RESUME_MISSION, command("comando-reanudar.json").type)
        assertEquals(CommandType.CANCEL_MISSION, command("comando-cancelar.json").type)
        assertEquals(SafePointId.BASE, command("comando-regresar-punto-seguro.json").params.safePointId)
        assertEquals(CommandParams(), command("comando-detencion-operativa.json").params)
        assertEquals("a-0007", command("comando-reconocer-alerta.json").params.alertId)
    }

    @Test
    fun `decision y resolution son opcionales y se transportan cuando estan`() {
        val reanudar = command("comando-reanudar.json")
        assertNull(reanudar.params.decision)
        assertNull(reanudar.params.resolution)

        val conDecision = reanudar.copy(params = CommandParams(missionId = "m-1", decision = IncidentDecision.SKIP_CHECKPOINT))
        val encoded = ContractCodec.encode(conDecision)
        assertTrue("\"decision\":\"SKIP_CHECKPOINT\"" in encoded)
        assertEquals(conDecision, (ContractCodec.decode(encoded) as ContractMessage.CommandMsg).value)

        val reconocer = Command(
            "c-9", "RONDA-01", 1L, "garita-pb", CommandType.ACKNOWLEDGE_ALERT,
            CommandParams(alertId = "a-1", resolution = AlertResolution.SEND_GUARD),
        )
        assertEquals(reconocer, (ContractCodec.decode(ContractCodec.encode(reconocer)) as ContractMessage.CommandMsg).value)
    }

    @Test
    fun `sin decision ni resolution el JSON no los incluye`() {
        val encoded = ContractCodec.encode(command("comando-reanudar.json"))
        assertFalse("decision" in encoded)
        assertFalse("resolution" in encoded)
    }

    @Test
    fun `respuestas confirmada y rechazada`() {
        val ok = (ContractCodec.decode(load("respuesta-confirmada.json")) as ContractMessage.Ack).value
        assertEquals(AckStatus.CONFIRMED, ok.status)
        assertEquals("c-0002", ok.correlationId)
        assertNull(ok.reasonCode)

        val no = (ContractCodec.decode(load("respuesta-rechazada.json")) as ContractMessage.Ack).value
        assertEquals(AckStatus.REJECTED, no.status)
        assertEquals(RejectReason.INVALID_STATE, no.reasonCode)
        assertEquals("La misión ya está en curso; no hay nada que reanudar.", no.reasonText)
    }

    @Test
    fun `eventos de alerta y de decision requerida`() {
        val alerta = (ContractCodec.decode(load("evento-alerta.json")) as ContractMessage.Event).value
        assertIs<RobotEvent.AlertRaised>(alerta)
        assertEquals(AlertType.LOW_BATTERY, alerta.alert.type)
        assertEquals(Severity.WARNING, alerta.severity)

        val decision = (ContractCodec.decode(load("evento-decision-requerida.json")) as ContractMessage.Event).value
        assertIs<RobotEvent.DecisionRequired>(decision)
        assertEquals(IncidentType.OBSTACLE, decision.incident.type)
        assertEquals(listOf("WAIT_30S", "SKIP_CHECKPOINT", "RETURN_TO_SAFE_POINT"), decision.incident.options.map { it.id })
        assertEquals(SafePointId.BASE, decision.incident.options.single { it.id == "RETURN_TO_SAFE_POINT" }.safePointId)
        assertNull(decision.incident.options.first { it.id == "WAIT_30S" }.safePointId)
    }

    @Test
    fun `incidente de persona en zona restringida ofrece las tres resoluciones`() {
        val e = (ContractCodec.decode(load("evento-persona-zona-restringida.json")) as ContractMessage.Event).value
        assertIs<RobotEvent.DecisionRequired>(e)
        assertEquals(IncidentType.PERSON_IN_RESTRICTED_ZONE, e.incident.type)
        assertEquals(5, e.incident.checkpointIndex)
        val resoluciones = e.incident.options.map { AlertResolution.valueOf(it.id) }
        assertEquals(AlertResolution.entries, resoluciones, "cada opción es un valor de resolution de ACKNOWLEDGE_ALERT")
    }

    @Test
    fun `mensajes invalidos lanzan ContractException`() {
        assertFailsWith<ContractException> { ContractCodec.decode("no es json") }
        assertFailsWith<ContractException> { ContractCodec.decode("""{"schemaVersion":1,"type":"OTRO","messageId":"x","robotId":"r"}""") }
        assertFailsWith<ContractException> { ContractCodec.decode(load("telemetria.json").replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")) }
        assertFailsWith<ContractException> { ContractCodec.decode(load("comando-pausar.json").replace("PAUSE_MISSION", "VOLAR")) }
    }
}
