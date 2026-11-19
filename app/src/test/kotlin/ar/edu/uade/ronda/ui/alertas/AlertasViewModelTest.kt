package ar.edu.uade.ronda.ui.alertas

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.enabledAll
import ar.edu.uade.ronda.ui.supervision.incident
import ar.edu.uade.ronda.ui.supervision.tele
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertasViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val battery = Alert("a1", AlertType.LOW_BATTERY, Severity.WARNING, "Batería baja", 900, false)
    private val person = Alert("a2", AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona en sala técnica", 950, false)
    private val old = Alert("a0", AlertType.OBSTACLE, Severity.WARNING, "Obstáculo resuelto", 100, true)

    private fun inputs(vararg alerts: Alert, connection: ConnectionState = ConnectionState.CONNECTED, personIncident: Boolean = false) =
        AlertasInputs(
            tele(1_000, alerts = alerts.toList()), connection, 1_000,
            if (personIncident) incident(IncidentType.PERSON_IN_RESTRICTED_ZONE) else null,
            emptyList(), enabledAll(), 1_400,
        )

    @Test fun separaActivasDeReconocidasYOrdenaPorSeveridad() {
        val s = AlertasMapper.map(inputs(battery, person, old))
        assertEquals(listOf("a2", "a1"), s.active.map { it.id })
        assertEquals(listOf("a0"), s.acknowledged.map { it.id })
        assertEquals("ACTIVAS · 2 SIN RECONOCER", s.activeTitle)
        assertNull(s.acknowledged.single().acknowledge)
    }

    @Test fun laPersonaConDecisionPendienteSeResuelveDesdeLaDecision() {
        val s = AlertasMapper.map(inputs(person, personIncident = true))
        val card = s.active.single()
        assertTrue(card.opensDecision)
        assertNull(card.acknowledge)
    }

    @Test fun sinEnlaceReconocerQuedaDeshabilitadoYLaAlertaComoNoVigente() {
        val i = inputs(battery, connection = ConnectionState.NO_DATA)
        val s = AlertasMapper.map(i.copy(availability = enabledAll(CommandType.ACKNOWLEDGE_ALERT to "Sin conexión con el robot")))
        assertEquals(CommandUiState.DISABLED, s.active.single().acknowledge!!.state)
        assertEquals(StatusLevel.STALE, s.active.single().level)
    }

    @Test fun reconocerEnviaElIdDeLaAlerta() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000, state = OperationalState.EN_RONDA, alerts = listOf(battery)))
        repo.availability.value = enabledAll()
        val vm = AlertasViewModel(repo, Clock { 1_200L })
        vm.onAcknowledge("a1")
        assertEquals(CommandType.ACKNOWLEDGE_ALERT, repo.sent.single().first)
        assertEquals("a1", repo.sent.single().second.alertId)
    }

    @Test fun reconocerUnaAlertaQueYaNoEstaAvisa() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000))
        repo.availability.value = enabledAll()
        val vm = AlertasViewModel(repo, Clock { 1_200L })
        vm.onAcknowledge("nada")
        assertTrue(repo.sent.isEmpty())
    }
}
