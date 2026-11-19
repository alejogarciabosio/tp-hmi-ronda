package ar.edu.uade.ronda.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import ar.edu.uade.ronda.data.local.EventKind
import ar.edu.uade.ronda.data.local.EventRecord
import ar.edu.uade.ronda.data.local.EventRecorder
import ar.edu.uade.ronda.data.local.HistoryRepository
import ar.edu.uade.ronda.data.local.MissionRecord
import ar.edu.uade.ronda.data.local.toOutcomes
import ar.edu.uade.ronda.ui.alertas.AlertasRoute
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.control.ControlRoute
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionRoute
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.historial.HistorialRoute
import ar.edu.uade.ronda.ui.historial.HistorialViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesRoute
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.seleccion.SeleccionRoute
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.data.LinkMonitor
import ar.edu.uade.ronda.ui.operacion.OperationalBarViewModel
import ar.edu.uade.ronda.ui.operacion.ProvideOperationalBar
import androidx.compose.runtime.remember
import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.ui.simulacion.SimulacionRoute
import ar.edu.uade.ronda.ui.simulacion.SimulacionViewModel
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.telemetria.TelemetriaRoute
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaTheme
import ar.edu.uade.ronda.core.repository.CheckpointHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
 * Genera las capturas de la tabla de docs/evidencias/README.md con la app real (ViewModels reales
 * contra el simulador, tiempo virtual, semilla 42). No corre con `testDebugUnitTest`: lo activa
 * `./gradlew :app:recordCapturas`.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CapturasRecorder {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock { START_MS + scheduler.currentTime }
    private val repoScope = CoroutineScope(dispatcher)
    private val repo = SimulatedRobotRepository.create(repoScope, clock, seed = 42)
    private val history = CheckpointHistory(repo.telemetry, repoScope)
    private val control = repo.control
    private val session = ControlSession("RONDA-01")
    private val linkMonitor = LinkMonitor(repo, clock, repoScope)
    private lateinit var out: File

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        out = File(System.getProperty("ronda.capturas.dir") ?: error("falta ronda.capturas.dir; usar :app:recordCapturas"))
        out.mkdirs()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun seleccion() {
        advance(3_000)
        show { SeleccionRoute(SeleccionViewModel(repo, ControlSession()), {}) }
        shot("app-01-seleccion")
    }

    @Test fun supervision() {
        startRound(); runUntil("3 puntos de control") { verified() >= 3 }
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("app-02-supervision")
    }

    @Test fun telemetria() {
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        show { TelemetriaRoute(TelemetriaViewModel(repo, clock), {}, {}) }
        shot("app-03-telemetria")
    }

    @Test fun misiones() {
        advance(3_000)
        show { MisionesRoute(MisionesViewModel(repo, clock, session), {}) }
        shot("app-04-misiones")
    }

    @Test fun misionesPreflightFallido() {
        advance(3_000)
        control.setBatteryPercent(30)
        advance(2_000)
        show { MisionesRoute(MisionesViewModel(repo, clock, session), {}) }
        shot("app-04b-misiones-preflight-fallido")
    }

    @Test fun controlPantalla() {
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        show { ControlRoute(ControlViewModel(repo, clock, session), {}, {}) }
        shot("app-05-control")
    }

    @Test fun alertas() {
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        control.injectObstacle()
        runUntil("incidente") { repo.activeIncident.value != null }
        advance(1_500)
        show { AlertasRoute(AlertasViewModel(repo, clock), {}) }
        shot("app-06-alertas")
    }

    @Test fun historial() {
        val history = MemoryHistory()
        EventRecorder(history, repoScope).start(repo.telemetry.filterNotNull(), repo.events, repo.commands.toOutcomes())
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        control.injectObstacle()
        runUntil("incidente") { repo.activeIncident.value != null }
        repo.send(CommandType.CANCEL_MISSION, CommandParams())
        advance(60_000)
        show { HistorialRoute(HistorialViewModel(repo, history, clock), {}) }
        shot("app-07-historial")
    }

    @Test fun desconexion() {
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        control.setLinkUp(false)
        advance(12_000)
        show { DesconexionRoute(DesconexionViewModel(repo, clock, linkMonitor.log), {}, {}) }
        shot("app-08-desconexion")
    }

    @Test fun panelSimulacion() {
        advance(3_000)
        show { SimulacionRoute(SimulacionViewModel(control, repo), {}) }
        shot("app-09-panel-simulacion")
    }

    @Test fun escenario1() {
        startRound(); runUntil("4 puntos de control") { verified() >= 4 }
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-1-ronda-normal")
    }

    @Test fun escenario2() {
        control.loadScenario(SimScenario.LOW_BATTERY); advance(1_000)
        startRound()
        runUntil("alerta de batería baja", maxMs = 900_000) {
            repo.telemetry.value?.unacknowledgedAlerts?.any { it.type == AlertType.LOW_BATTERY } == true
        }
        advance(1_500)
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-2-bateria-baja")
    }

    @Test fun escenario3() {
        control.loadScenario(SimScenario.DISCONNECTION); advance(1_000)
        startRound(); runUntil("2 puntos de control") { verified() >= 2 }
        control.cutLinkFor(15_000)
        advance(9_000)
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-3-desconexion")
    }

    @Test fun escenario4() {
        control.loadScenario(SimScenario.OBSTACLE); advance(1_000)
        startRound(); runUntil("incidente", maxMs = 900_000) { repo.activeIncident.value != null }
        advance(1_500)
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-4-obstaculo")
    }

    @Test fun escenario5() {
        control.loadScenario(SimScenario.PERSON_IN_RESTRICTED_ZONE); advance(1_000)
        startRound(); runUntil("incidente", maxMs = 900_000) { repo.activeIncident.value != null }
        advance(1_500)
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-5-persona")
    }

    @Test fun escenario5Vigilante() {
        control.loadScenario(SimScenario.PERSON_IN_RESTRICTED_ZONE); advance(1_000)
        startRound(); runUntil("incidente", maxMs = 900_000) { repo.activeIncident.value != null }
        val alerta = repo.telemetry.value!!.unacknowledgedAlerts.first { it.type == AlertType.PERSON_DETECTED }
        repo.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alerta.id, resolution = AlertResolution.SEND_GUARD))
        runUntil("robot pausado") { repo.telemetry.value?.operationalState == OperationalState.PAUSADA }
        advance(3_000)
        show { SupervisionRoute(SupervisionViewModel(repo, clock, session, history.log), {}) }
        shot("escenario-5b-vigilante-en-camino")
    }

    private fun verified() = repo.telemetry.value?.service?.checkpointsVerified ?: 0

    private fun startRound() {
        advance(2_000)
        repo.send(CommandType.START_MISSION, CommandParams(routeId = RouteId.RONDA_COMPLETA))
    }

    private fun runUntil(what: String, maxMs: Long = 600_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            check(waited < maxMs) { "No se cumplió '$what' en $maxMs ms" }
            advance(1_000); waited += 1_000
        }
    }

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms); scheduler.runCurrent()
    }

    private fun show(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            // Como en el NavHost: la barra fija de detención operativa la provee un ViewModel compartido.
            val bar = remember { OperationalBarViewModel(repo, clock) }
            RondaTheme { ProvideOperationalBar(bar) { Box(Modifier.fillMaxSize().background(RondaColors.ink950)) { content() } } }
        }
        settle()
    }

    private fun settle() {
        rule.mainClock.autoAdvance = true
        try { rule.waitForIdle() } finally { rule.mainClock.autoAdvance = false }
        repeat(8) { scheduler.runCurrent(); rule.mainClock.advanceTimeBy(250) }
        scheduler.runCurrent(); rule.mainClock.advanceTimeBy(100)
    }

    private fun shot(name: String) {
        settle()
        rule.mainClock.autoAdvance = true
        val bmp = try { capture() } finally { rule.mainClock.autoAdvance = false }
        checkNotNull(bmp) { "sin ventanas para $name" }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** `captureToImage` se cuelga en Robolectric; se dibuja cada ventana (principal y hoja modal) a mano. */
    private fun capture(): Bitmap? {
        val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val viewsField = wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = (viewsField.get(wmg) as List<android.view.View>).filter { it.width > 0 && it.height > 0 }
        val shots = views.map { it.drawToBitmap() }
        val base = shots.firstOrNull() ?: return null
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        shots.drop(1).forEach { canvas.drawBitmap(it, 0f, (result.height - it.height).toFloat().coerceAtLeast(0f), null) }
        return result
    }

    /** Historial en memoria: el de Room necesita su propio executor y acá alcanza con el mismo contrato. */
    private class MemoryHistory : HistoryRepository {
        private val missions = MutableStateFlow<Map<String, MissionRecord>>(emptyMap())
        private val events = MutableStateFlow<Map<String, EventRecord>>(emptyMap())
        override fun observeMissions(): Flow<List<MissionRecord>> = missions.map { it.values.sortedByDescending { m -> m.startedAt } }
        override fun observeEvents(kind: EventKind?): Flow<List<EventRecord>> =
            events.map { all -> all.values.filter { kind == null || it.kind == kind.name }.sortedByDescending { it.timestamp } }
        override fun observeMissionEvents(missionId: String): Flow<List<EventRecord>> =
            events.map { all -> all.values.filter { it.missionId == missionId }.sortedByDescending { it.timestamp } }
        override suspend fun saveMission(mission: MissionRecord) { missions.value = missions.value + (mission.id to mission) }
        override suspend fun saveEvent(event: EventRecord) { events.value = events.value + (event.sourceId to event) }
        override suspend fun purgeOlderThan(before: Long) = 0
    }

    private companion object { const val START_MS = 1_763_500_000_000L }
}
