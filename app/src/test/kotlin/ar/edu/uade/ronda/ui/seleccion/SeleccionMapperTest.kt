package ar.edu.uade.ronda.ui.seleccion

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.supervision.tele
import org.junit.Assert.assertEquals
import org.junit.Test

class SeleccionMapperTest {
    @Test fun conEnlaceRonda01EstaDisponibleConBateria() {
        val s = SeleccionMapper.map(tele(1_000, batteryPct = 88), ConnectionState.CONNECTED, null)
        val r = s.robots.first()
        assertEquals("Disponible", r.badge.label)
        assertEquals(CommandUiState.ENABLED, r.takeControl.state)
        assertEquals("Go2 EDU · simulado · batería 88 %", r.specs)
    }

    @Test fun sinDatosNoSePuedeTomarControlNiSeMuestraBateriaVieja() {
        val s = SeleccionMapper.map(tele(1_000, batteryPct = 88), ConnectionState.NO_DATA, null)
        val r = s.robots.first()
        assertEquals(CommandUiState.DISABLED, r.takeControl.state)
        assertEquals("Go2 EDU · simulado · batería sin datos", r.specs)
    }

    @Test fun losOtrosRobotsNuncaSeHabilitan() {
        val s = SeleccionMapper.map(null, ConnectionState.CONNECTED, null)
        s.robots.drop(1).forEach { assertEquals(CommandUiState.DISABLED, it.takeControl.state) }
    }
}
