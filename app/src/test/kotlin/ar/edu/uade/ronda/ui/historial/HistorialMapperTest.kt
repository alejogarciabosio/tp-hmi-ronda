package ar.edu.uade.ronda.ui.historial

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.data.local.EventRecord
import ar.edu.uade.ronda.data.local.MissionRecord
import ar.edu.uade.ronda.ui.model.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorialMapperTest {
    private val now = 100L * DAY
    private val missions = listOf(
        MissionRecord("m1", "RONDA-01", "Ronda completa", now - HOUR, now - 10_000, "COMPLETADA", 8, 8, 1),
        MissionRecord("m2", "RONDA-01", "Ronda completa", now - 3 * DAY, now - 3 * DAY + HOUR, "CANCELADA", 6, 8, 0),
        MissionRecord("m3", "RONDA-01", "Salidas de emergencia", now - 20 * DAY, now - 20 * DAY + HOUR, "COMPLETADA", 2, 2, 0),
    )
    private val events = listOf(
        EventRecord("e1", now - HOUR, "ALERTA", "PERSON_DETECTED", "CRITICAL", "Persona en zona restringida"),
        EventRecord("e2", now - 2 * HOUR, "INCIDENTE", "OBSTACLE", "WARNING", "Obstáculo en PC4"),
        EventRecord("e3", now - 3 * HOUR, "COMANDO", "PAUSE_MISSION", "INFO", "PAUSE_MISSION", commandType = "PAUSE_MISSION", commandStatus = "VERIFIED"),
    )

    private fun map(view: HistoryView = HistoryView.TODOS, sev: SeverityFilter = SeverityFilter.TODAS, period: PeriodFilter = PeriodFilter.SIETE) =
        HistorialMapper.map(HistorialInputs(null, ConnectionState.DISCONNECTED, null, missions, events, view, sev, period, now))

    @Test fun losUltimos7DiasDejanAfueraLaMisionVieja() {
        assertEquals(listOf("m1", "m2"), map().missions.map { it.id })
        assertEquals(3, map(period = PeriodFilter.TREINTA).missions.size)
    }

    @Test fun cancelarSeMuestraComoInterrumpida() {
        val m2 = map().missions.first { it.id == "m2" }
        assertEquals("Interrumpida", m2.badge.label)
        assertEquals(StatusLevel.WARNING, m2.badge.level)
        assertTrue(m2.summary.startsWith("6/8 PC"))
    }

    @Test fun filtroDeSeveridadCritica() {
        val evs = map(sev = SeverityFilter.CRITICA).eventGroups.flatMap { it.events }
        assertEquals(listOf("e1"), evs.map { it.id })
    }

    @Test fun vistaMisionesOcultaEventosYViceversa() {
        assertTrue(map(view = HistoryView.MISIONES).eventGroups.isEmpty())
        assertTrue(map(view = HistoryView.EVENTOS).missions.isEmpty())
    }

    @Test fun lasOrdenesSeLeenConSuEstado() {
        val e3 = map().eventGroups.flatMap { it.events }.first { it.id == "e3" }
        assertEquals("Orden: Pausar · verificada", e3.text)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
