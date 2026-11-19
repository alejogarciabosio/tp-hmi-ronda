package ar.edu.uade.ronda.data

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.core.repository.TrackedCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LinkMonitorTest {
    private class Repo : RobotRepository {
        override val telemetry = MutableStateFlow<RobotTelemetry?>(null)
        override val connection = MutableStateFlow(ConnectionState.CONNECTED)
        override val lastTelemetryAt = MutableStateFlow<Long?>(1_000)
        override val events = MutableSharedFlow<RobotEvent>()
        override val activeIncident = MutableStateFlow<Incident?>(null)
        override val commands = MutableStateFlow<List<TrackedCommand>>(emptyList())
        override val availability = MutableStateFlow<Map<CommandType, CommandAvailability>>(emptyMap())
        override fun send(type: CommandType, params: CommandParams) = "x"
    }

    @Test fun recuerdaElCorteYLosEventosSinQueHayaUnaPantallaAbierta() = runTest {
        val repo = Repo()
        var now = 1_000L
        val monitor = LinkMonitor(repo, Clock { now }, backgroundScope)
        runCurrent()
        assertNull(monitor.log.value.lastCutDataAt)

        now = 5_000; repo.connection.value = ConnectionState.NO_DATA; runCurrent()
        assertEquals(1_000L, monitor.log.value.lastCutDataAt)
        assertNull(monitor.log.value.restoredAt)

        now = 20_000; repo.lastTelemetryAt.value = 20_000; repo.connection.value = ConnectionState.CONNECTED; runCurrent()
        val alert = Alert("a1", AlertType.OBSTACLE, Severity.WARNING, "Obstáculo", 8_000, false)
        repo.events.emit(RobotEvent.AlertRaised("e1", "RONDA-01", 8_000, Severity.WARNING, alert)); runCurrent()
        assertEquals(20_000L, monitor.log.value.restoredAt)
        assertEquals(1_000L, monitor.log.value.lastCutDataAt)
        assertEquals(20_000L, monitor.log.value.received.single().receivedAt)
    }
}
