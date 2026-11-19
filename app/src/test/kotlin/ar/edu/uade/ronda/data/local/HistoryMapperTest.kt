package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryMapperTest {
    @Test fun alertaSeMapeaAFila() {
        val r = HistoryMapper.toRecord(alertEvent(), "m1")
        assertEquals("ALERTA", r.kind)
        assertEquals("LOW_BATTERY", r.type)
        assertEquals("WARNING", r.severity)
        assertEquals("Batería baja", r.message)
        assertEquals("m1", r.missionId)
    }

    @Test fun incidenteSeMapeaAFila() {
        val ev = RobotEvent.DecisionRequired(
            "e2", "RONDA-01", 3000, Severity.CRITICAL,
            Incident("i1", IncidentType.OBSTACLE, 3, "Pasillo bloqueado", emptyList()),
        )
        val r = HistoryMapper.toRecord(ev)
        assertEquals("INCIDENTE", r.kind)
        assertEquals("OBSTACLE", r.type)
        assertNull(r.missionId)
    }

    @Test fun comandoRechazadoEsAdvertencia() {
        val r = HistoryMapper.toRecord(CommandOutcome(command("c1"), CommandStatus.REJECTED, 1100, "Batería insuficiente"))
        assertEquals("COMANDO", r.kind)
        assertEquals("REJECTED", r.commandStatus)
        assertEquals("WARNING", r.severity)
        assertEquals("Batería insuficiente", r.message)
        assertEquals("c1", r.sourceId)
    }

    @Test fun trackerAbreActualizaYCierraMision() {
        val t = MissionTracker()
        assertNull(t.onTelemetry(telemetry(1, OperationalState.EN_BASE)))
        val open = t.onTelemetry(telemetry(2, OperationalState.EN_RONDA, progress()))!!
        assertEquals("EN_CURSO", open.result)
        assertEquals("m1", t.currentMissionId)
        val upd = t.onTelemetry(telemetry(3, OperationalState.EN_RONDA, progress(), verified = 2, issues = 1))!!
        assertEquals(2, upd.checkpointsVerified)
        assertEquals(1, upd.anomalies)
        assertNull(t.onTelemetry(telemetry(4, OperationalState.EN_RONDA, progress(), verified = 2, issues = 1)))
        val closed = t.onTelemetry(telemetry(5, OperationalState.COMPLETADA, progress(), verified = 8, issues = 1))!!
        assertEquals("COMPLETADA", closed.result)
        assertEquals(5, closed.endedAt)
        assertEquals(2, closed.startedAt)
        assertNull(t.currentMissionId)
    }

    @Test fun misionQueDesapareceSinEstadoTerminalSeCierraCancelada() {
        val t = MissionTracker()
        t.onTelemetry(telemetry(2, OperationalState.EN_RONDA, progress()))
        val closed = t.onTelemetry(telemetry(3, OperationalState.EN_BASE))!!
        assertEquals("CANCELADA", closed.result)
    }
}
