package ar.edu.uade.ronda.ui.telemetria

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.tele
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetriaMapperTest {
    @Test fun sinTelemetriaNoInventaLecturas() {
        val s = TelemetriaMapper.map(null, ConnectionState.DISCONNECTED, null, 5_000)
        assertTrue(s.robot.isEmpty())
        assertEquals("Sin datos", s.updated)
    }

    @Test fun conEnlaceMuestraRobotYServicio() {
        val s = TelemetriaMapper.map(tele(1_000), ConnectionState.CONNECTED, 1_000, 1_400)
        assertEquals(listOf("Conexión", "Batería", "Posición", "Movimiento", "Misión"), s.robot.map { it.label })
        assertEquals("Actualizado hace 0,4 s", s.updated)
        assertTrue(s.service.any { it.label == "Sala técnica" })
        assertTrue(s.service.none { it.level == StatusLevel.STALE })
    }

    @Test fun conDatosViejosTodasLasLecturasQuedanComoNoVigentes() {
        val s = TelemetriaMapper.map(tele(1_000), ConnectionState.NO_DATA, 1_000, 6_000)
        assertTrue((s.robot + s.service).all { it.level == StatusLevel.STALE })
        assertTrue(!s.strip.connectionLabel.startsWith("Conectado"))
    }

    @Test fun laMisionSeExpresaPorPuntosDeControl() {
        val s = TelemetriaMapper.map(tele(1_000, index = 3), ConnectionState.CONNECTED, 1_000, 1_000)
        assertEquals("PC 3/8", s.robot.first { it.label == "Misión" }.value)
    }

    @Test fun lasSalidasUsanElNombreTalCual() {
        val exits = listOf(
            ar.edu.uade.ronda.core.model.EmergencyExit("SE-PB", "Salida de emergencia PB", ar.edu.uade.ronda.core.model.ExitStatus.LIBRE),
            ar.edu.uade.ronda.core.model.EmergencyExit("SE-1", "Salida de emergencia 1°", ar.edu.uade.ronda.core.model.ExitStatus.OBSTRUIDA),
        )
        val s = TelemetriaMapper.map(tele(1_000, exits = exits), ConnectionState.CONNECTED, 1_000, 1_000)
        val labels = s.service.map { it.label }
        assertTrue(labels.contains("Salida de emergencia PB"))
        assertTrue(labels.contains("Salida de emergencia 1°"))
        assertTrue(labels.none { it.contains("de emergencia de emergencia") })
    }
}
