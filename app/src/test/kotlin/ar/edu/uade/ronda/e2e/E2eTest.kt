package ar.edu.uade.ronda.e2e

import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.ronda.MainActivity
import ar.edu.uade.ronda.core.model.OperationalState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de los tests de punta a punta: `MainActivity` con la `RondaApp` real ([E2eApp]: el
 * `AppContainer` de la app, el simulador real con semilla 42, Room en memoria y tiempo virtual).
 * Los tests recorren la app como en una demo: tocan botones y pestañas por su texto o su
 * descripción y afirman lo que se ve. El estado del simulador solo se consulta para saber hasta
 * cuándo avanzar el reloj, nunca para afirmar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = E2eApp::class)
abstract class E2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    protected val app: E2eApp get() = ApplicationProvider.getApplicationContext()
    private val repo get() = app.container.robotRepository

    @Before fun arrancar() {
        rule.mainClock.autoAdvance = false
        // Primera telemetría y enlace estable antes de mirar la pantalla.
        advance(3_000)
        settle()
    }

    @After fun cerrar() { Dispatchers.resetMain() }

    // ---- Tiempo virtual ----

    protected fun advance(ms: Long) { app.scheduler.advanceTimeBy(ms); app.scheduler.runCurrent() }

    /** Deja que Compose procese lo pendiente: recomposición, navegación y animaciones. */
    protected fun settle() {
        rule.mainClock.autoAdvance = true
        try { rule.waitForIdle() } finally { rule.mainClock.autoAdvance = false }
        repeat(8) { app.scheduler.runCurrent(); rule.mainClock.advanceTimeBy(250) }
        app.scheduler.runCurrent(); rule.mainClock.advanceTimeBy(100)
    }

    /** Pasa [ms] de tiempo virtual y actualiza la pantalla. */
    protected fun pasar(ms: Long) {
        var t = 0L
        while (t < ms) { advance(500); t += 500 }
        settle()
    }

    /** Avanza el reloj de a medio segundo hasta que el robot llegue a [que]. Solo sirve para esperar. */
    protected fun esperarRobot(que: String, maxMs: Long = 600_000, cond: () -> Boolean) {
        var t = 0L
        while (!cond()) {
            if (t >= maxMs) fail("El robot no llegó a '$que' en $maxMs ms virtuales")
            advance(500); t += 500
        }
        settle()
    }

    /** Avanza el reloj de a [pasoMs] y mira la pantalla en cada paso hasta ver [texto]. */
    protected fun esperarEnPantalla(texto: String, maxMs: Long = 120_000, pasoMs: Long = 1_000) {
        var t = 0L
        while (!visible(texto)) {
            if (t >= maxMs) fail("No apareció '$texto' en $maxMs ms virtuales. En pantalla: ${textos()}")
            advance(pasoMs); t += pasoMs
            settle()
        }
    }

    protected fun incidenteAbierto() = app.container.robotRepository.activeIncident.value != null
    protected fun estado(): OperationalState? = repo.telemetry.value?.operationalState
    protected fun verificados(): Int = repo.telemetry.value?.service?.checkpointsVerified ?: 0
    protected fun bateria(): Int = repo.telemetry.value?.battery?.percent ?: 100

    // ---- Lectura de la pantalla ----

    private fun nodos(): List<SemanticsNode> =
        rule.onAllNodes(SemanticsMatcher("cualquiera") { true }, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)

    /** Todo el texto y las descripciones de la pantalla actual (incluida una hoja modal abierta). */
    protected fun textos(): List<String> = nodos().flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    /** Solo el texto dibujado (sin descripciones para el lector de pantalla). */
    protected fun textosVisibles(): List<String> = nodos().flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    protected fun visible(texto: String) = textos().any { it.contains(texto, ignoreCase = true) }
    protected fun visible(re: Regex) = textos().any { re.containsMatchIn(it) }

    protected fun assertVisible(texto: String) =
        assertTrue("Se esperaba ver '$texto'. En pantalla: ${textos()}", visible(texto))

    protected fun assertVisible(re: Regex) =
        assertTrue("Se esperaba ver /${re.pattern}/. En pantalla: ${textos()}", visible(re))

    protected fun assertNoVisible(texto: String) =
        assertFalse("No se esperaba ver '$texto'. En pantalla: ${textos().filter { it.contains(texto, true) }}", visible(texto))

    protected fun assertNoVisible(re: Regex) =
        assertFalse("No se esperaba ver /${re.pattern}/: ${textos().filter { re.containsMatchIn(it) }}", visible(re))

    // ---- Botones ----

    private fun etiqueta(label: String) =
        hasText(label, ignoreCase = true) or hasContentDescription(label, substring = true, ignoreCase = true)

    protected fun boton(label: String): SemanticsNodeInteraction =
        rule.onAllNodes(etiqueta(label) and hasClickAction()).onFirst()

    protected fun habilitado(label: String): Boolean =
        rule.onAllNodes(etiqueta(label) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()

    protected fun assertHabilitado(label: String) = assertTrue("'$label' debería estar habilitado", habilitado(label))

    protected fun assertDeshabilitado(label: String) {
        val nodos = rule.onAllNodes(etiqueta(label) and hasClickAction()).fetchSemanticsNodes()
        assertTrue("No se encontró el botón '$label'", nodos.isNotEmpty())
        assertFalse("'$label' debería estar deshabilitado", habilitado(label))
    }

    /** Un toque, como el dedo del operador: solo sobre un botón habilitado. */
    protected fun tocar(label: String) {
        settle()
        val n = rule.onAllNodes(etiqueta(label) and hasClickAction() and isEnabled())
        if (n.fetchSemanticsNodes().isEmpty()) fail("No hay un botón habilitado '$label'. En pantalla: ${textos()}")
        n.onFirst().performSemanticsAction(SemanticsActions.OnClick)
        app.scheduler.runCurrent()
        settle()
    }

    /** Pestaña de la barra inferior. */
    protected fun pestana(nombre: String) {
        settle()
        rule.onAllNodes(hasContentDescription(nombre, substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        app.scheduler.runCurrent()
        settle()
    }

    private fun mantenible(label: String) = SemanticsMatcher("mantener '$label'") { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("$label.") } &&
            n.config.contains(SemanticsActions.OnLongClick)
    }

    protected fun puedeMantener(label: String) = rule.onAllNodes(mantenible(label)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Mantener presionado: apoya el dedo, deja correr 1,1 s del reloj de Compose y lo levanta.
     * Es el gesto real de `HoldToConfirm`, no la acción de accesibilidad.
     */
    protected fun mantener(label: String, ms: Long = 1_100) {
        settle()
        val nodes = rule.onAllNodes(mantenible(label))
        if (nodes.fetchSemanticsNodes().isEmpty()) fail("No hay un '$label' habilitado para mantener. En pantalla: ${textos()}")
        val node = nodes.onFirst()
        rule.mainClock.autoAdvance = true
        try { runCatching { node.performScrollTo() } } finally { rule.mainClock.autoAdvance = false }
        node.performTouchInput { down(center) }
        var t = 0L
        while (t < ms) { rule.mainClock.advanceTimeBy(100); app.scheduler.runCurrent(); t += 100 }
        // Si la confirmación cerró la hoja, el nodo ya no está y no hay a quién soltar.
        runCatching { node.performTouchInput { up() } }
        app.scheduler.runCurrent()
        settle()
    }

    /** Botón Atrás del sistema: llega a la ventana con foco (la hoja de decisión, si está abierta). */
    protected fun atras() {
        settle()
        rule.runOnUiThread {
            val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val views = wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(wmg) as List<*>
            val top = views.filterIsInstance<View>().last { it.isShown }
            top.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            top.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        app.scheduler.runCurrent()
        settle()
    }

    /** Rotar el teléfono: la actividad se destruye y se vuelve a crear. */
    protected fun rotar() {
        rule.activityRule.scenario.recreate()
        app.scheduler.runCurrent()
        settle()
    }

    // ---- Pasos de la demo ----

    protected fun tomarControl() {
        assertVisible("Selección de robot")
        tocar("Tomar control")
        assertVisible("Supervisión")
    }

    /** Abre el panel desde "Simulación", arriba a la derecha, y vuelve con "Volver". */
    protected fun enPanel(paso: () -> Unit) {
        tocar("Simulación")
        assertVisible("Panel de simulación")
        paso()
        tocar("Volver")
    }

    protected fun elegirVelocidad8() {
        tocar("Velocidad ×8")
        assertVisible("×8 · actual")
        assertVisible("Velocidad · ×8")
    }

    protected fun cargarEscenario(nombre: String) {
        settle()
        rule.onAllNodes(hasText(nombre, substring = true, ignoreCase = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        app.scheduler.runCurrent()
        settle()
        assertVisible("$nombre · cargado")
    }

    /** Tomar control, ×8 y (opcional) escenario desde el panel, y la ronda iniciada desde Misiones. */
    protected fun prepararEIniciar(escenario: String? = null) {
        tomarControl()
        enPanel {
            elegirVelocidad8()
            escenario?.let { cargarEscenario(it) }
        }
        pasar(2_000)
        pestana("Misiones")
        tocar("Iniciar ronda")
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        pestana("Supervisión")
    }

    /** Texto en mayúsculas con guion bajo (un enum crudo) o una palabra suelta de los enums del dominio. */
    protected val enumCrudo = Regex(
        "\\b[A-Z0-9]+_[A-Z0-9_]+\\b|\\b(OBSTACLE|MISSION|PENDING|CONFIRMED|VERIFIED|REJECTED|BATTERY|PERSON|DETECTED|" +
            "WARNING|CRITICAL|INFO|ALERT|FAULT|LINK|DEGRADED|START|PAUSE|RESUME|CANCEL|RETURN|STOP|ACKNOWLEDGE|" +
            "OPERATIONAL|SKIP|WAIT|GUARD|AUTHORIZED|CONTINUE|CONNECTED|DISCONNECTED|NONE|NULL|null)\\b",
    )
}
