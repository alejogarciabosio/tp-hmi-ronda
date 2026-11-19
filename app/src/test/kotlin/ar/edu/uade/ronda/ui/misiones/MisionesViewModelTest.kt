package ar.edu.uade.ronda.ui.misiones

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.tele
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MisionesViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun inputs(batteryPct: Int, connection: ConnectionState = ConnectionState.CONNECTED, route: RouteId = RouteId.RONDA_COMPLETA) =
        MisionesInputs(
            tele(1_000, state = OperationalState.EN_BASE, batteryPct = batteryPct, missionId = null),
            connection, 1_000, emptyList(), route, 1_400,
        )

    @Test fun conBateriaYEnlaceSePuedeIniciar() {
        val s = MisionesMapper.map(inputs(96))
        assertEquals(CommandUiState.ENABLED, s.start.state)
        assertTrue(s.preflight.all { it.level == StatusLevel.OK })
        assertEquals("No", s.strip.actionText)
    }

    @Test fun bateriaInsuficienteDeshabilitaYPideAccion() {
        val s = MisionesMapper.map(inputs(32))
        assertEquals(CommandUiState.DISABLED, s.start.state)
        assertEquals(StatusLevel.ERROR, s.preflight.first().level)
        assertTrue(s.strip.needsAction)
    }

    @Test fun unaRutaMasCortaPideMenosBateria() {
        val s = MisionesMapper.map(inputs(36, route = RouteId.SOLO_SALA_TECNICA))
        assertEquals(CommandUiState.ENABLED, s.start.state)
    }

    @Test fun sinDatosNoSeIniciaYNoSeMuestraBateriaVieja() {
        val s = MisionesMapper.map(inputs(96, ConnectionState.NO_DATA))
        assertEquals(CommandUiState.DISABLED, s.start.state)
        assertEquals(StatusLevel.STALE, s.preflight.first().level)
    }

    @Test fun iniciarEnviaLaRutaElegida() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000, state = OperationalState.EN_BASE, batteryPct = 96, missionId = null))
        val vm = MisionesViewModel(repo, Clock { 1_200L })
        vm.onSelectRoute(RouteId.SALIDAS_EMERGENCIA.name)
        vm.onStart()
        assertEquals(CommandType.START_MISSION, repo.sent.single().first)
        assertEquals(RouteId.SALIDAS_EMERGENCIA, repo.sent.single().second.routeId)
    }

    @Test fun conBateriaInsuficienteIniciarNoEnviaNada() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000, state = OperationalState.EN_BASE, batteryPct = 20, missionId = null))
        val vm = MisionesViewModel(repo, Clock { 1_200L })
        vm.onStart()
        assertTrue(repo.sent.isEmpty())
    }
}
