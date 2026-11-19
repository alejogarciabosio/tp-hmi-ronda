package ar.edu.uade.ronda.core.scenarios

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Corre los cinco escenarios del plan §4.1 como un operador y escribe una transcripción legible
 * de cada uno en `docs/evidencias/transcripciones/`. La carpeta se pasa con la propiedad
 * `ronda.transcripts.dir`; sin ella el test igual verifica el escenario pero no escribe nada.
 * Se regenera con `./gradlew :core:scenarioTranscripts`.
 */
class ScenarioTranscriptsTest {

    private fun TestScope.rigAndTranscript(title: String, description: String, scenario: SimScenario? = null): Pair<ScenarioRig, Transcript> {
        val rig = ScenarioRig(this)
        val tr = Transcript(this, rig, title, description)
        if (scenario != null) tr.operatorPanel("carga el escenario \"${scenario.displayName}\"") { rig.control.loadScenario(scenario) }
        return rig to tr
    }

    private val full = CommandParams(routeId = RouteId.RONDA_COMPLETA)

    @Test
    fun `transcripcion escenario 1 ronda normal`() = runTest {
        val (rig, tr) = rigAndTranscript(
            "Escenario 1: ronda normal",
            "El operador inicia la ronda completa (8 puntos de control en 2 pisos). El robot la recorre, completa la misión, regresa y queda en la base.",
        )
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        val id = tr.operator("inicia la ronda completa", CommandType.START_MISSION, full)
        rig.until(what = "verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.COMPLETADA)
        tr.note("ronda completada: ${rig.telemetry.service.checkpointsVerified} de 8 puntos verificados")
        rig.untilState(OperationalState.EN_BASE)
        tr.note("el robot volvió a la base")
        tr.save("escenario-1-ronda-normal.txt")
        assertEquals(8, rig.telemetry.service.checkpointsVerified)
    }

    @Test
    fun `transcripcion escenario 2 bateria baja`() = runTest {
        val (rig, tr) = rigAndTranscript(
            "Escenario 2: batería baja",
            "El robot sale con 40 % (el mínimo operativo) y una celda degradada que gasta 6 veces lo normal: la ronda, que a consumo nominal cuesta ~5 %, cruza el 25 % a mitad de camino (alerta: alcanza para volver) y llega al 15 %, donde el robot propone regresar; el operador confirma.",
            SimScenario.LOW_BATTERY,
        )
        rig.until(what = "telemetría") { rig.repo.telemetry.value?.battery?.percent == 40 }
        tr.operator("inicia la ronda completa", CommandType.START_MISSION, full)
        rig.untilState(OperationalState.REQUIERE_DECISION)
        tr.note("el robot pide decisión: ${rig.repo.activeIncident.value?.message}")
        val id = tr.operator("elige \"Regresar a la base\"", CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE))
        rig.until(what = "verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.EN_BASE)
        tr.note("el robot llegó a la base con ${rig.telemetry.battery.percent} % de batería")
        tr.save("escenario-2-bateria-baja.txt")
        assertTrue(rig.events.any { it is ar.edu.uade.ronda.core.model.RobotEvent.AlertRaised && it.alert.type == AlertType.LOW_BATTERY })
    }

    @Test
    fun `transcripcion escenario 3 desconexion`() = runTest {
        val (rig, tr) = rigAndTranscript(
            "Escenario 3: desconexión",
            "Con la ronda en curso se corta el enlace 15 s. La app marca los datos como viejos, la orden enviada queda sin respuesta, y al volver el enlace se entregan los eventos ocurridos durante el corte con su hora original. El robot se detuvo solo y espera al operador.",
            SimScenario.DISCONNECTION,
        )
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        tr.operator("inicia la ronda completa", CommandType.START_MISSION, full)
        rig.untilState(OperationalState.EN_RONDA)
        rig.advance(10_000)
        tr.operatorPanel("corta el enlace por 15 s") { rig.control.cutLinkFor(15_000) }
        rig.advance(2_000)
        val pausa = tr.operator("intenta pausar la ronda sin enlace", CommandType.PAUSE_MISSION, CommandParams(missionId = rig.missionId))
        tr.operatorPanel("inyecta una falla del robot mientras no hay enlace") { rig.control.injectFault() }
        rig.advance(14_000)
        rig.until(what = "reconexión") { rig.repo.connection.value == ConnectionState.CONNECTED }
        rig.advance(1_000)
        tr.note("la orden de pausa quedó en estado ${rig.repo.commands.value.first { it.id == pausa }.status}; el evento de falla llegó con su hora original")
        tr.save("escenario-3-desconexion.txt")
        assertEquals(CommandStatus.NO_RESPONSE, rig.statusOf(pausa))
        assertEquals(OperationalState.FALLA, rig.state)
    }

    @Test
    fun `transcripcion escenario 4 obstaculo`() = runTest {
        val (rig, tr) = rigAndTranscript(
            "Escenario 4: obstáculo en el pasillo 1°A",
            "En el PC4 un objeto bloquea el pasillo. El robot se detiene a 2 m y ofrece tres opciones. El operador salta el PC4 y la ronda continúa; el punto queda con observación.",
            SimScenario.OBSTACLE,
        )
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        tr.operator("inicia la ronda completa", CommandType.START_MISSION, full)
        rig.untilState(OperationalState.REQUIERE_DECISION)
        val id = tr.operator(
            "elige \"Saltar el PC4\"", CommandType.RESUME_MISSION,
            CommandParams(missionId = rig.missionId, decision = IncidentDecision.SKIP_CHECKPOINT),
        )
        rig.until(what = "verificada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.COMPLETADA)
        tr.note("ronda completada con ${rig.telemetry.service.checkpointsVerified} puntos verificados y ${rig.telemetry.service.checkpointsWithIssues} con observación")
        rig.untilState(OperationalState.EN_BASE)
        tr.save("escenario-4-obstaculo.txt")
        assertEquals(7, rig.telemetry.service.checkpointsVerified)
    }

    @Test
    fun `transcripcion escenario 5 persona en zona restringida`() = runTest {
        val (rig, tr) = rigAndTranscript(
            "Escenario 5: persona en zona restringida fuera de horario",
            "En el PC5 (sala técnica) el robot detecta una persona, mantiene 2 m y espera. El operador envía al vigilante y luego la reconoce como persona autorizada. No se guardan imágenes: el evento registra solo el hecho, el punto y la decisión.",
            SimScenario.PERSON_IN_RESTRICTED_ZONE,
        )
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        tr.operator("inicia la ronda completa", CommandType.START_MISSION, full)
        rig.untilState(OperationalState.REQUIERE_DECISION)
        val alerta = rig.telemetry.alerts.single { it.type == AlertType.PERSON_DETECTED }
        val guardia = tr.operator(
            "elige \"Enviar vigilante\"", CommandType.ACKNOWLEDGE_ALERT,
            CommandParams(alertId = alerta.id, resolution = AlertResolution.SEND_GUARD),
        )
        rig.until(what = "confirmada") { rig.statusOf(guardia) == CommandStatus.CONFIRMED }
        rig.advance(20_000)
        tr.note("el robot sigue esperando: estado ${rig.state}")
        val ok = tr.operator(
            "el vigilante confirma: elige \"Persona autorizada (registrar)\"", CommandType.ACKNOWLEDGE_ALERT,
            CommandParams(alertId = alerta.id, resolution = AlertResolution.AUTHORIZED_PERSON),
        )
        rig.until(what = "verificada") { rig.statusOf(ok) == CommandStatus.VERIFIED }
        rig.untilState(OperationalState.COMPLETADA)
        rig.untilState(OperationalState.EN_BASE)
        tr.save("escenario-5-persona-zona-restringida.txt")
        assertEquals(8, rig.telemetry.service.checkpointsVerified)
    }
}
