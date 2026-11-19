package ar.edu.uade.ronda.ui.desconexion

import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.data.LinkLog
import ar.edu.uade.ronda.data.ReceivedEvent
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.enabledAll
import ar.edu.uade.ronda.ui.supervision.tele
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesconexionMapperTest {
    private fun inputs(
        connection: ConnectionState,
        commands: List<TrackedCommand> = emptyList(),
        received: List<ReceivedEvent> = emptyList(),
        lastCutDataAt: Long? = 1_000,
    ): DesconexionInputs {
        // Con enlace, el último dato es reciente; sin enlace, tiene 34 s.
        val lastAt = if (connection.isStale) 1_000L else 34_800L
        return DesconexionInputs(tele(lastAt), connection, lastAt, commands, enabledAll(), LinkLog(lastCutDataAt, received = received), 35_000)
    }

    private fun cmd(id: String, sentAt: Long, status: CommandStatus = CommandStatus.NO_RESPONSE) =
        TrackedCommand(Command(id, "RONDA-01", sentAt, "op", CommandType.RETURN_TO_SAFE_POINT), status, sentAt, sentAt + 4_000)

    @Test fun conEnlaceNoMuestraDatosViejos() {
        val s = DesconexionMapper.map(inputs(ConnectionState.CONNECTED))
        assertTrue(s.linkOk)
        assertTrue(s.lastKnown.isEmpty())
    }

    @Test fun sinEnlaceMuestraElUltimoValorComoNoVigente() {
        val s = DesconexionMapper.map(inputs(ConnectionState.DISCONNECTED))
        assertFalse(s.linkOk)
        assertEquals(listOf("Batería", "Posición", "Movimiento"), s.lastKnown.map { it.label })
        assertTrue(s.lastKnown.all { it.level == StatusLevel.STALE })
        assertFalse(s.strip.connectionLabel.startsWith("Conectado"))
        assertTrue(s.strip.needsAction)
    }

    @Test fun laOrdenSinRespuestaQuedaListadaComoPendiente() {
        val cmd = TrackedCommand(Command("m1", "RONDA-01", 2_000, "op", CommandType.RETURN_TO_SAFE_POINT), CommandStatus.NO_RESPONSE, 2_000, 6_000)
        val s = DesconexionMapper.map(inputs(ConnectionState.DISCONNECTED, listOf(cmd)))
        assertEquals(CommandChipStatus.NO_RESPONSE, s.pending.single().chip.status)
    }

    @Test fun losEventosQueLlegaronTardeConservanSuHoraOriginal() {
        val alert = Alert("a1", AlertType.OBSTACLE, Severity.WARNING, "Obstáculo en PC4", 10_000, false)
        val late = ReceivedEvent(RobotEvent.AlertRaised("e1", "RONDA-01", 10_000, Severity.WARNING, alert), 40_000)
        val onTime = ReceivedEvent(RobotEvent.AlertRaised("e2", "RONDA-01", 39_500, Severity.WARNING, alert), 40_000)
        val s = DesconexionMapper.map(inputs(ConnectionState.CONNECTED, received = listOf(late, onTime)))
        assertEquals(listOf("e1"), s.lateEvents.map { it.id })
    }

    @Test fun soloListaLasOrdenesEnviadasDesdeElUltimoCorte() {
        val before = cmd("viejo", 500)
        val during = cmd("corte", 2_000)
        val s = DesconexionMapper.map(inputs(ConnectionState.DISCONNECTED, listOf(before, during)))
        assertEquals(1, s.pending.size)
        assertEquals("ÓRDENES PENDIENTES", s.pendingTitle)
        assertTrue(s.pending.single().detail!!.contains("al volver el enlace"))
    }

    @Test fun sinCortesNoListaOrdenesPendientes() {
        val s = DesconexionMapper.map(inputs(ConnectionState.CONNECTED, listOf(cmd("x", 30_000, CommandStatus.PENDING)), lastCutDataAt = null))
        assertTrue(s.pending.isEmpty())
    }

    @Test fun conElEnlaceDeVueltaElTextoLoDice() {
        val s = DesconexionMapper.map(inputs(ConnectionState.CONNECTED, listOf(cmd("corte", 2_000))))
        assertTrue(s.linkOk)
        assertEquals("ÓRDENES SIN RESPUESTA DEL ÚLTIMO CORTE", s.pendingTitle)
        assertTrue(s.pending.single().detail!!.contains("el enlace volvió"))
    }
}
