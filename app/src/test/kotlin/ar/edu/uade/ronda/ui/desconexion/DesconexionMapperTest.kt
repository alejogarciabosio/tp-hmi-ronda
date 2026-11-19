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
    ) = DesconexionInputs(tele(1_000), connection, 1_000, commands, enabledAll(), received, 35_000)

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
}
