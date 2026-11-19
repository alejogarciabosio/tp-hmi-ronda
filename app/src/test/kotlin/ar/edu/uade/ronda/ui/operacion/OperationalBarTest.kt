package ar.edu.uade.ronda.ui.operacion

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.repository.CheckpointLog
import ar.edu.uade.ronda.data.LinkLog
import ar.edu.uade.ronda.ui.alertas.AlertasRoute
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.control.ControlRoute
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionRoute
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.historial.HistorialRoute
import ar.edu.uade.ronda.ui.historial.HistorialViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesRoute
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.supervision.enabledAll
import ar.edu.uade.ronda.ui.supervision.tele
import ar.edu.uade.ronda.ui.telemetria.TelemetriaRoute
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import ar.edu.uade.ronda.ui.theme.RondaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Detención operativa siempre visible (una sola, fuera del scroll) e insignia de alertas sin reconocer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class OperationalBarTest {
    @get:Rule val rule = createComposeRule()

    private val clock = Clock { 1_200L }
    private val battery = Alert("a1", AlertType.LOW_BATTERY, Severity.WARNING, "Batería baja.", 900, false)
    private val person = Alert("a2", AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona.", 900, false)

    private fun repo(vararg alerts: Alert) = FakeRobotRepository().apply {
        publish(tele(1_000, alerts = alerts.toList()), ConnectionState.CONNECTED)
        availability.value = enabledAll()
    }

    @Test fun laDetencionNoDependeDelControlYAvisaPersonaCerca() {
        val ui = OperationalBarMapper.map(tele(1_000, alerts = listOf(person, battery)), ConnectionState.CONNECTED, 1_000, enabledAll(), 1_200)
        assertTrue(ui.stop.enabled)
        assertEquals("Persona cerca del robot", ui.personNearby)
        assertEquals(2, ui.unacknowledgedAlerts)
        val sinPersona = OperationalBarMapper.map(tele(1_000, alerts = listOf(battery)), ConnectionState.CONNECTED, 1_000, enabledAll(), 1_200)
        assertNull(sinPersona.personNearby)
        val parado = OperationalBarMapper.map(
            tele(1_000), ConnectionState.CONNECTED, 1_000,
            enabledAll(CommandType.OPERATIONAL_STOP to "El robot no está en movimiento"), 1_200,
        )
        assertFalse(parado.stop.enabled)
        assertEquals("El robot no está en movimiento", parado.stop.reason)
    }

    @Test fun detenerDesdeLaBarraRevalidaYEnvia() {
        val r = repo()
        val vm = OperationalBarViewModel(r, clock)
        vm.onOperationalStop()
        assertEquals(listOf(CommandType.OPERATIONAL_STOP), r.sent.map { it.first })
        r.availability.value = enabledAll(CommandType.OPERATIONAL_STOP to "El robot no está en movimiento")
        vm.onOperationalStop()
        assertEquals(1, r.sent.size)
    }

    private fun show(repo: FakeRobotRepository, content: @Composable () -> Unit) {
        rule.setContent {
            val bar = remember { OperationalBarViewModel(repo, clock) }
            RondaTheme { ProvideOperationalBar(bar) { content() } }
        }
        rule.waitForIdle()
    }

    private fun assertSingleStop() {
        val stops = rule.onAllNodesWithContentDescription(OperationalStopTexts.LABEL, substring = true).fetchSemanticsNodes()
        assertEquals(1, stops.size)
        rule.onNodeWithText(OperationalStopTexts.NOTE).assertExists()
    }

    @Test fun supervisionTieneUnaSolaDetencionFijaAunSinControl() {
        val r = repo(battery)
        show(r) { SupervisionRoute(SupervisionViewModel(r, clock, ControlSession(), MutableStateFlow(CheckpointLog())), {}) }
        assertSingleStop()
    }

    @Test fun controlTieneUnaSolaDetencion() {
        val r = repo()
        show(r) { ControlRoute(ControlViewModel(r, clock, ControlSession("RONDA-01")), {}, {}) }
        assertSingleStop()
    }

    @Test fun telemetriaTieneLaDetencion() {
        val r = repo()
        show(r) { TelemetriaRoute(TelemetriaViewModel(r, clock), {}, {}) }
        assertSingleStop()
    }

    @Test fun misionesTieneLaDetencion() {
        val r = repo()
        show(r) { MisionesRoute(MisionesViewModel(r, clock, ControlSession("RONDA-01")), {}) }
        assertSingleStop()
    }

    @Test fun desconexionTieneUnaSolaDetencion() {
        val r = repo()
        show(r) { DesconexionRoute(DesconexionViewModel(r, clock, MutableStateFlow(LinkLog())), {}, {}) }
        assertSingleStop()
    }

    @Test fun historialTieneLaDetencion() {
        val r = repo()
        val history = EmptyHistory()
        show(r) { HistorialRoute(HistorialViewModel(r, history, clock), {}) }
        assertSingleStop()
    }

    @Test fun alertasMuestraPersonaCercaYLaInsigniaConLaCantidad() {
        val r = repo(battery, person)
        show(r) { AlertasRoute(AlertasViewModel(r, clock), {}) }
        assertSingleStop()
        rule.onNodeWithText("Persona cerca del robot").assertExists()
        rule.onNodeWithContentDescription("Alertas, 2 sin reconocer", substring = true).assertExists()
        assertEquals(1, rule.onAllNodesWithText("2").fetchSemanticsNodes().size)
    }

    private class EmptyHistory : ar.edu.uade.ronda.data.local.HistoryRepository {
        override fun observeMissions() = kotlinx.coroutines.flow.flowOf(emptyList<ar.edu.uade.ronda.data.local.MissionRecord>())
        override fun observeEvents(kind: ar.edu.uade.ronda.data.local.EventKind?) =
            kotlinx.coroutines.flow.flowOf(emptyList<ar.edu.uade.ronda.data.local.EventRecord>())
        override fun observeMissionEvents(missionId: String) =
            kotlinx.coroutines.flow.flowOf(emptyList<ar.edu.uade.ronda.data.local.EventRecord>())
        override suspend fun saveMission(mission: ar.edu.uade.ronda.data.local.MissionRecord) {}
        override suspend fun saveEvent(event: ar.edu.uade.ronda.data.local.EventRecord) {}
        override suspend fun purgeOlderThan(before: Long) = 0
    }
}
