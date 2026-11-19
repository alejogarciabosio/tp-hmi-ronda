package ar.edu.uade.ronda.ui.simulacion

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.control.SimulationSettings
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.incident
import ar.edu.uade.ronda.ui.supervision.tele
import ar.edu.uade.ronda.ui.supervision.tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class FakeSimulationControl : SimulationControl {
    override val settings = MutableStateFlow(SimulationSettings())
    val calls = mutableListOf<String>()

    override fun loadScenario(scenario: SimScenario) { calls += "load:$scenario"; settings.value = SimulationSettings(scenario = scenario) }
    override fun setLinkUp(up: Boolean) { calls += "link:$up"; settings.value = settings.value.copy(linkUp = up) }
    override fun cutLinkFor(durationMs: Long) { calls += "cut:$durationMs"; settings.value = settings.value.copy(linkUp = false) }
    override fun rejectNextCommand(reason: RejectReason, text: String?) { calls += "reject" }
    override fun setResponseDelayMs(delayMs: Long) { calls += "delay:$delayMs"; settings.value = settings.value.copy(responseDelayMs = delayMs) }
    override fun setLatencyMs(latencyMs: Int?) { calls += "latency:$latencyMs" }
    override fun injectObstacle() { calls += "obstacle" }
    override fun injectPersonInRestrictedZone() { calls += "person" }
    override fun injectFault() { calls += "fault" }
    override fun setBatteryPercent(percent: Int) { calls += "battery:$percent" }
    override fun setTechRoomTempC(celsius: Double) { calls += "temp:$celsius" }
    override fun setExitObstructed(exitId: String, obstructed: Boolean) { calls += "exit:$exitId:$obstructed" }
    override fun reset() { calls += "reset"; settings.value = SimulationSettings() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SimulacionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun inputs(
        settings: SimulationSettings = SimulationSettings(),
        state: OperationalState = OperationalState.EN_RONDA,
        batteryPct: Int = 62,
        incidentType: IncidentType? = null,
        armed: Boolean = false,
    ) = SimulacionInputs(settings, tele(1_000, state = state, batteryPct = batteryPct), incidentType?.let { incident(it) }, armed)

    private fun TestScope.subscribe(vm: SimulacionViewModel) {
        vm.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
    }

    @Test fun marcaElEscenarioCargadoYDaLaPista() {
        val s = SimulacionMapper.map(inputs(SimulationSettings(scenario = SimScenario.OBSTACLE)))
        assertEquals(5, s.scenarios.size)
        assertEquals(SimScenario.OBSTACLE, s.scenarios.single { it.selected }.scenario)
        assertTrue(s.scenarioHint.contains("PC4"))
    }

    @Test fun inyectarSoloDuranteUnaRonda() {
        val s = SimulacionMapper.map(inputs(state = OperationalState.EN_BASE))
        val obstacle = s.injections.first { it.injection == SimInjection.OBSTACLE }.button
        assertEquals(CommandUiState.DISABLED, obstacle.state)
        assertEquals("Solo durante una ronda en curso.", obstacle.reason)
        assertEquals(CommandUiState.ENABLED, s.injections.first { it.injection == SimInjection.FAULT }.button.state)
    }

    @Test fun conUnIncidenteAbiertoNoSeInyectaOtro() {
        val s = SimulacionMapper.map(inputs(incidentType = IncidentType.OBSTACLE))
        assertTrue(s.injections.filter { it.injection != SimInjection.FAULT }.all { it.button.state == CommandUiState.DISABLED })
    }

    @Test fun enlaceCortadoSeVeEnElToggleYBloqueaElCorteTemporizado() {
        val s = SimulacionMapper.map(inputs(SimulationSettings(linkUp = false)))
        assertTrue(s.toggles.first { it.toggle == SimToggle.LINK_CUT }.on)
        assertEquals(CommandUiState.DISABLED, s.timedCut.state)
    }

    @Test fun sinTelemetriaNoSeAjustaLaBateria() {
        val s = SimulacionMapper.map(SimulacionInputs(SimulationSettings(), null, null, false))
        assertEquals("Batería · sin dato", s.batteryTitle)
        assertEquals(CommandUiState.DISABLED, s.batteryPlus.state)
    }

    @Test fun cargarEscenarioLlamaAlControl() {
        val control = FakeSimulationControl()
        val vm = SimulacionViewModel(control, FakeRobotRepository())
        vm.onLoadScenario(SimScenario.LOW_BATTERY)
        assertEquals(listOf("load:LOW_BATTERY"), control.calls)
    }

    @Test fun lasFallasVanAlControl() {
        val control = FakeSimulationControl()
        val vm = SimulacionViewModel(control, FakeRobotRepository())
        vm.onToggle(SimToggle.LINK_CUT, true)
        vm.onToggle(SimToggle.DELAY_RESPONSES, true)
        vm.onToggle(SimToggle.DELAY_RESPONSES, false)
        vm.onInject(SimInjection.PERSON)
        assertEquals(listOf("link:false", "delay:${SimulacionMapper.DELAY_MS}", "delay:0", "person"), control.calls)
    }

    @Test fun elCorteTemporizadoNoSeRepiteConElEnlaceCaido() {
        val control = FakeSimulationControl()
        val vm = SimulacionViewModel(control, FakeRobotRepository())
        vm.onTimedCut()
        vm.onTimedCut()
        assertEquals(listOf("cut:${SimulacionMapper.TIMED_CUT_MS}"), control.calls)
    }

    @Test fun bateriaSeAjustaDesdeElValorActualConLimites() {
        val control = FakeSimulationControl()
        val repo = FakeRobotRepository()
        val vm = SimulacionViewModel(control, repo)
        repo.publish(tele(1_000, batteryPct = 95))
        vm.onBatteryStep(10)
        repo.publish(tele(1_500, batteryPct = 34))
        vm.onBatteryStep(-10)
        assertEquals(listOf("battery:100", "battery:24"), control.calls)
    }

    @Test fun rechazoArmadoSeDesarmaConLaProximaOrden() = runTest(dispatcher) {
        val control = FakeSimulationControl()
        val repo = FakeRobotRepository()
        repo.commands.value = listOf(tracked("c1", CommandType.PAUSE_MISSION, CommandStatus.CONFIRMED, 100))
        val vm = SimulacionViewModel(control, repo)
        subscribe(vm)

        vm.onToggle(SimToggle.REJECT_NEXT, true)
        advanceUntilIdle()
        val armed = vm.uiState.value.toggles.first { it.toggle == SimToggle.REJECT_NEXT }
        assertTrue(armed.on)
        assertFalse(armed.enabled)
        assertEquals(listOf("reject"), control.calls)

        repo.commands.value = repo.commands.value + tracked("c2", CommandType.RESUME_MISSION, CommandStatus.REJECTED, 200)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.toggles.first { it.toggle == SimToggle.REJECT_NEXT }.on)
    }

    @Test fun cargarEscenarioDescartaElRechazoArmado() = runTest(dispatcher) {
        val control = FakeSimulationControl()
        val vm = SimulacionViewModel(control, FakeRobotRepository())
        subscribe(vm)
        vm.onToggle(SimToggle.REJECT_NEXT, true)
        vm.onLoadScenario(SimScenario.NORMAL)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.toggles.first { it.toggle == SimToggle.REJECT_NEXT }.on)
    }
}
