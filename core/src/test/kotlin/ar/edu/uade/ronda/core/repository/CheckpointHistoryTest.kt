package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.model.Battery
import ar.edu.uade.ronda.core.model.MissionProgress
import ar.edu.uade.ronda.core.model.Motion
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.Pose
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.ServiceTelemetry
import ar.edu.uade.ronda.core.model.TempTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckpointHistoryTest {

    private fun tele(
        ts: Long,
        index: Int,
        verified: Int,
        issues: Int = 0,
        state: OperationalState = OperationalState.EN_RONDA,
        missionId: String? = "m1",
    ) = RobotTelemetry(
        "t$ts", "RONDA-01", ts, 100, Battery(80, 90, false), state,
        missionId?.let { MissionProgress(it, "Ronda completa", index, 8, "-") },
        Pose(0, 0.0, 0.0, 0.0, "Hall"), Motion(0.0, MotionMode.DETENIDO), emptyList(),
        ServiceTelemetry(verified, issues, 24.5, TempTrend.STABLE, emptyList()),
    )

    @Test
    fun `deduce verificados, con observacion y salteados`() {
        var log = CheckpointLog()
        log = log.next(tele(1, index = 1, verified = 0))
        log = log.next(tele(2, index = 2, verified = 1))
        log = log.next(tele(3, index = 3, verified = 2, issues = 1))
        log = log.next(tele(4, index = 4, verified = 2, issues = 1))
        assertEquals(CheckpointOutcome.VERIFIED, log.resolved.getValue(1).outcome)
        assertEquals(CheckpointOutcome.WITH_ISSUE, log.resolved.getValue(2).outcome)
        assertEquals(CheckpointOutcome.SKIPPED, log.resolved.getValue(3).outcome)
        assertEquals(2L, log.resolved.getValue(1).at)
    }

    @Test
    fun `al completar la ronda el ultimo punto queda resuelto`() {
        var log = CheckpointLog()
        log = log.next(tele(1, index = 8, verified = 7))
        log = log.next(tele(2, index = 8, verified = 8, state = OperationalState.COMPLETADA))
        assertEquals(CheckpointOutcome.VERIFIED, log.resolved.getValue(8).outcome)
    }

    @Test
    fun `recolecta con su propio alcance y se reinicia al empezar otra mision`() = runTest {
        val telemetry = MutableStateFlow<RobotTelemetry?>(null)
        val history = CheckpointHistory(telemetry, backgroundScope)
        telemetry.value = tele(1, index = 1, verified = 0); runCurrent()
        telemetry.value = tele(2, index = 2, verified = 1); runCurrent()
        assertEquals(CheckpointOutcome.VERIFIED, history.log.value.resolved.getValue(1).outcome)

        // Nadie observa la historia y la sigue llevando: no depende de una pantalla.
        telemetry.value = tele(3, index = 3, verified = 2); runCurrent()
        assertEquals(setOf(1, 2), history.log.value.resolved.keys)

        telemetry.value = tele(4, index = 1, verified = 0, missionId = "m2"); runCurrent()
        assertEquals("m2", history.log.value.missionId)
        assertTrue(history.log.value.resolved.isEmpty())
    }
}
