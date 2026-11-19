package ar.edu.uade.ronda.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.drawToBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.theme.RondaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Graba la pantalla Supervisión real (ViewModel real + repositorio simulado) con tiempo virtual.
 * No corre con `testDebugUnitTest`: lo activa `./gradlew :app:recordDemo`, que arma el MP4.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SupervisionDemoRecorder {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock { START_MS + scheduler.currentTime }
    private val repoScope = CoroutineScope(dispatcher)
    private val repo = SimulatedRobotRepository.create(repoScope, clock, seed = 42)
    private val control = repo.control

    private lateinit var out: File
    private var frameNo = 0
    private var label by mutableStateOf("")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        out = File(System.getProperty("ronda.demo.frames") ?: error("falta ronda.demo.frames; usar :app:recordDemo"))
        out.deleteRecursively(); out.mkdirs()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun grabar() {
        val vm = SupervisionViewModel(repo, clock)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            RondaTheme {
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    Text(
                        label, color = Color(0xFFF2B33D), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF0E0D0B)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Box(Modifier.fillMaxSize()) { SupervisionRoute(vm, onNavigate = {}) }
                }
            }
        }

        // Paso 1: ronda normal
        label = "1/5 · Ronda normal"
        step(3_000); hold(4)
        repo.send(CommandType.START_MISSION, CommandParams(routeId = RouteId.RONDA_COMPLETA))
        run(maxMs = 600_000, what = "3 puntos de control") { (repo.telemetry.value?.service?.checkpointsVerified ?: 0) >= 3 }
        key("01-ronda-normal"); hold(8)

        // Paso 2: obstáculo y decisión
        label = "2/5 · Obstáculo: el robot pide decisión"
        control.injectObstacle()
        run(maxMs = 300_000, what = "incidente") { repo.activeIncident.value != null }
        step(1_500, 3); key("02-obstaculo-decision"); hold(12)
        label = "2/5 · Decisión: Saltar el punto de control"
        rule.onNodeWithText("Saltar", substring = true).performClick()
        step(500, 4); hold(4)
        run(maxMs = 300_000, what = "incidente resuelto") { repo.activeIncident.value == null }
        step(3_000, 6); hold(4)

        // Paso 3: corte de enlace
        label = "3/5 · Enlace cortado: datos viejos"
        control.setLinkUp(false)
        step(1_000, 4)
        step(4_000, 6)
        key("03-sin-datos"); hold(12)
        step(4_000, 4)

        // Paso 4: restauración
        label = "4/5 · Enlace restaurado"
        control.setLinkUp(true)
        step(1_000, 6)
        key("04-enlace-restaurado"); hold(8)
        run(maxMs = 120_000, what = "robot en ronda") { repo.availability.value.getValue(CommandType.PAUSE_MISSION).enabled }

        // Paso 5: rechazo de comando
        label = "5/5 · Pausar rechazado por el robot"
        control.rejectNextCommand(RejectReason.INVALID_STATE, "El robot está cruzando una puerta y no puede pausar ahora.")
        hold(4)
        rule.onNodeWithText("Pausar").performClick()
        step(500, 2)
        step(1_000, 6)
        key("05-comando-rechazado"); hold(14)
    }

    private fun run(maxMs: Long, what: String, every: Long = 1_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            check(waited < maxMs) { "No se cumplió '$what' en $maxMs ms" }
            step(every); waited += every
        }
    }

    /** Avanza el tiempo virtual y el reloj de Compose, y guarda `frames` cuadros. */
    private fun step(ms: Long, frames: Int = 1) {
        repeat(frames) {
            scheduler.advanceTimeBy(ms); scheduler.runCurrent()
            rule.mainClock.advanceTimeBy(600)
            scheduler.runCurrent()
            rule.mainClock.advanceTimeBy(100)
            snapshot()?.let { write(it, scaled = true) }
        }
    }

    private fun hold(frames: Int) {
        repeat(frames) { snapshot()?.let { write(it, scaled = true) } }
    }

    private fun key(name: String) {
        snapshot()?.let { b -> File(out, "key_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private var lastFrame: Bitmap? = null

    /** Compone todas las ventanas (la hoja de decisión es una ventana aparte) sobre la principal. */
    private fun snapshot(): Bitmap? {
        rule.mainClock.autoAdvance = true
        try { return capture() } finally { rule.mainClock.autoAdvance = false }
    }

    /** `captureToImage` se cuelga en Robolectric; se dibuja cada ventana (principal y hoja modal) a mano. */
    private fun capture(): Bitmap? {
        val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val viewsField = wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = (viewsField.get(wmg) as List<android.view.View>).filter { it.width > 0 && it.height > 0 }
        val shots = views.map { it.drawToBitmap() }
        val base = shots.firstOrNull() ?: return lastFrame
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        shots.drop(1).forEach { canvas.drawBitmap(it, 0f, (result.height - it.height).toFloat().coerceAtLeast(0f), null) }
        lastFrame = result
        return result
    }

    private fun write(b: Bitmap, scaled: Boolean) {
        val bmp = if (scaled) Bitmap.createScaledBitmap(b, (b.width / 2) / 2 * 2, (b.height / 2) / 2 * 2, true) else b
        File(out, "f_%05d.png".format(frameNo++)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private companion object { const val START_MS = 1_763_500_000_000L }
}
