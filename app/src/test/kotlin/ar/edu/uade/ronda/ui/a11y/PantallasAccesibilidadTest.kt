package ar.edu.uade.ronda.ui.a11y

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.ui.alertas.AlertasRoute
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.control.ControlRoute
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionRoute
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesRoute
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.seleccion.SeleccionRoute
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.ui.simulacion.FakeSimulationControl
import ar.edu.uade.ronda.ui.simulacion.SimulacionRoute
import ar.edu.uade.ronda.ui.simulacion.SimulacionViewModel
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.supervision.enabledAll
import ar.edu.uade.ronda.ui.supervision.tele
import ar.edu.uade.ronda.ui.telemetria.TelemetriaRoute
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import ar.edu.uade.ronda.ui.theme.RondaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recorre cada pantalla con datos de una ronda en curso y verifica dos reglas de accesibilidad
 * sobre todo lo que se puede tocar: área táctil de al menos 48 dp y un texto que el lector de pantalla pueda leer.
 */
@RunWith(RobolectricTestRunner::class)
// Application vacía: RondaApp arma AppContainer con Room real y sus trabajos siguen corriendo entre tests.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class PantallasAccesibilidadTest {
    @get:Rule val rule = createComposeRule()

    private val clock = Clock { 1_200L }
    private val nav: (Any) -> Unit = {}

    private fun repo(connection: ConnectionState = ConnectionState.CONNECTED) = FakeRobotRepository().apply {
        publish(
            tele(
                1_000,
                alerts = listOf(Alert("a1", AlertType.LOW_BATTERY, Severity.WARNING, "Batería baja.", 900, acknowledged = false)),
            ),
            connection,
        )
        availability.value = enabledAll()
    }

    private fun check(screen: String, content: @Composable () -> Unit) {
        rule.setContent { RondaTheme { content() } }
        rule.waitForIdle()
        val density = rule.density.density
        val problems = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
            // size y no boundsInRoot: lo que queda fuera del scroll viene recortado a 0 en los bounds.
            val w = node.size.width / density
            val h = node.size.height / density
            val label = label(node)
            when {
                label.isBlank() -> "sin texto para lector de pantalla (${w}x$h dp)"
                w < MIN_DP || h < MIN_DP -> "«$label» mide ${"%.0f".format(w)}x${"%.0f".format(h)} dp"
                else -> null
            }
        }
        assertTrue("$screen:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun label(node: SemanticsNode): String {
        val c = node.config
        return (c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    }

    @Test fun seleccion() = check("Selección") { SeleccionRoute(SeleccionViewModel(repo())) { nav(it) } }

    @Test fun supervision() = check("Supervisión") { SupervisionRoute(SupervisionViewModel(repo(), clock), { nav(it) }) }

    @Test fun supervisionSinDatos() = check("Supervisión sin datos") {
        SupervisionRoute(SupervisionViewModel(repo(ConnectionState.NO_DATA), clock), { nav(it) })
    }

    @Test fun telemetria() = check("Telemetría") { TelemetriaRoute(TelemetriaViewModel(repo(), clock), { nav(it) }, {}) }

    @Test fun misiones() = check("Misiones") { MisionesRoute(MisionesViewModel(repo(), clock)) { nav(it) } }

    @Test fun control() = check("Control") { ControlRoute(ControlViewModel(repo(), clock), { nav(it) }, {}) }

    @Test fun alertas() = check("Alertas") { AlertasRoute(AlertasViewModel(repo(), clock)) { nav(it) } }

    @Test fun desconexion() = check("Desconexión") {
        DesconexionRoute(DesconexionViewModel(repo(ConnectionState.DISCONNECTED), clock), { nav(it) }, {})
    }

    @Test fun simulacion() = check("Panel de simulación") {
        SimulacionRoute(SimulacionViewModel(FakeSimulationControl(), repo())) {}
    }

    private companion object {
        // Medio dp de tolerancia por redondeo de píxeles.
        const val MIN_DP = 47.5f
    }
}
