package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.OperationalState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.Snapshot
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentDecision
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventRecorderTest {
    @Test fun consumeFlujosYPersiste() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        recorder.recordTelemetry(telemetry(1, OperationalState.EN_RONDA, progress()))
        recorder.start(
            telemetry = flowOf(telemetry(5, OperationalState.COMPLETADA, progress(), verified = 8)),
            events = flowOf(alertEvent()),
            commands = flowOf(command("c1").let {
                CommandOutcome(it, CommandStatus.PENDING, 1000)
            }),
        )
        advanceUntilIdle()
        assertEquals("COMPLETADA", repo.missions.getValue("m1").result)
        assertEquals(2, repo.events.size)
    }

    @Test fun elEstadoDelComandoActualizaLaMismaFila() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        val c = command("c1")
        recorder.recordCommand(CommandOutcome(c, CommandStatus.PENDING, 1000))
        recorder.recordCommand(CommandOutcome(c, CommandStatus.CONFIRMED, 1200))
        assertEquals(1, repo.events.size)
        assertEquals("CONFIRMED", repo.events.getValue("c1").commandStatus)
    }

    @Test fun laDecisionConservaElPuntoDondeSeTomoAunqueElRobotYaAvanzo() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        recorder.recordTelemetry(telemetry(1, OperationalState.REQUIERE_DECISION, progress().copy(checkpointIndex = 4)))
        val skip = Command(
            "c9", "RONDA-01", 1000, "garita", CommandType.RESUME_MISSION,
            CommandParams(decision = IncidentDecision.SKIP_CHECKPOINT),
        )
        recorder.recordCommand(CommandOutcome(skip, CommandStatus.PENDING, 1000))
        recorder.recordTelemetry(telemetry(2, OperationalState.EN_RONDA, progress().copy(checkpointIndex = 5)))
        recorder.recordCommand(CommandOutcome(skip, CommandStatus.VERIFIED, 1500))
        val fila = repo.events.getValue("c9")
        assertEquals("VERIFIED", fila.commandStatus)
        assertEquals("SKIP_CHECKPOINT", fila.commandOption)
        assertEquals(4, fila.checkpointIndex)
    }

    @Test fun laDecisionNombraElPuntoDelEdificioDelIncidente() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        // Ruta corta: el progreso dice 3 de 3, pero el incidente es en el PC5 del edificio.
        recorder.recordTelemetry(telemetry(1, OperationalState.REQUIERE_DECISION, progress(total = 3).copy(checkpointIndex = 3)))
        recorder.recordEvent(
            RobotEvent.DecisionRequired(
                "e5", "RONDA-01", 1, Severity.WARNING,
                ar.edu.uade.ronda.core.model.Incident("i5", ar.edu.uade.ronda.core.model.IncidentType.OBSTACLE, 5, "Obstáculo", emptyList()),
            ),
        )
        val skip = Command("c5", "RONDA-01", 1000, "garita", CommandType.RESUME_MISSION, CommandParams(decision = IncidentDecision.SKIP_CHECKPOINT))
        recorder.recordCommand(CommandOutcome(skip, CommandStatus.PENDING, 1000))
        assertEquals(5, repo.events.getValue("c5").checkpointIndex)
    }

    @Test fun eventoTomaLaMisionAbierta() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        recorder.recordTelemetry(telemetry(1, OperationalState.EN_RONDA, progress("m9")))
        recorder.recordEvent(alertEvent())
        assertEquals("m9", repo.events.getValue("e1").missionId)
    }

    @Test fun noPersisteLaFotoDelIncidente() = runTest {
        val repo = FakeHistory()
        val recorder = EventRecorder(repo, TestScope(testScheduler))
        val marca = "QUJDREVGR0hJSktMTU5PUA=="
        val snap = Snapshot("foto-privada-1", "image/jpeg", 2000, marca)
        recorder.recordEvent(
            RobotEvent.AlertRaised(
                "e7", "RONDA-01", 2000, Severity.CRITICAL,
                Alert("a7", AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona detectada", 2000, false, snap),
            ),
        )
        val fila = repo.events.getValue("e7").toString()
        assertFalse(fila.contains(marca))
        assertFalse(fila.contains("foto-privada-1"))
        assertFalse(fila.contains("image/jpeg"))
        assertTrue(EventRecord::class.java.declaredFields.none { it.name.contains("snapshot", ignoreCase = true) })
    }
}
