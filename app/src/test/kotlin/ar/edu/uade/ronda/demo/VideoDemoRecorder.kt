package ar.edu.uade.ronda.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.drawToBitmap
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import ar.edu.uade.ronda.data.local.EventKind
import ar.edu.uade.ronda.data.local.EventRecord
import ar.edu.uade.ronda.data.local.EventRecorder
import ar.edu.uade.ronda.data.local.HistoryRepository
import ar.edu.uade.ronda.data.local.MissionRecord
import ar.edu.uade.ronda.data.local.toOutcomes
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.historial.HistorialViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.navigation.RondaNavHost
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.data.LinkMonitor
import ar.edu.uade.ronda.ui.operacion.OperationalBarViewModel
import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.ui.simulacion.SimulacionViewModel
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import ar.edu.uade.ronda.ui.theme.RondaTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
 * Graba los cuadros del video de demostración con la app completa: `RondaNavHost` real, los
 * ViewModels reales contra el simulador (semilla 42) y tiempo virtual. Escribe PNG y un
 * `frames.json` con la duración en pantalla y el texto del panel de cada cuadro; el MP4 lo arma
 * `docs/evidencias/video/componer_video.py`. No corre con `testDebugUnitTest`: lo activa
 * `./gradlew :app:recordVideo`.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoDemoRecorder {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock { START_MS + scheduler.currentTime }
    private val repoScope = CoroutineScope(dispatcher)
    private val repo = SimulatedRobotRepository.create(repoScope, clock, seed = 42)
    private val checkpointHistory = CheckpointHistory(repo.telemetry, repoScope)
    private val control = repo.control
    private val session = ControlSession()
    private val linkMonitor = LinkMonitor(repo, clock, repoScope)
    private val history = MemoryHistory()

    private lateinit var out: File
    private lateinit var nav: NavHostController

    private class Entry(val file: String, var seconds: Double, val scenario: Int, val title: String, val text: String)

    private val entries = mutableListOf<Entry>()
    private var lastBitmap: Bitmap? = null
    private var scenario = 0
    private var title = ""
    private var text = ""

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        out = File(System.getProperty("ronda.video.frames") ?: error("falta ronda.video.frames; usar :app:recordVideo"))
        out.deleteRecursively(); out.mkdirs()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun grabar() {
        EventRecorder(history, repoScope).start(repo.telemetry.filterNotNull(), repo.events, repo.commands.toOutcomes())
        val factory = viewModelFactory {
            initializer { SupervisionViewModel(repo, clock, session, checkpointHistory.log) }
            initializer { SeleccionViewModel(repo, session) }
            initializer { TelemetriaViewModel(repo, clock) }
            initializer { MisionesViewModel(repo, clock, session) }
            initializer { ControlViewModel(repo, clock, session) }
            initializer { AlertasViewModel(repo, clock) }
            initializer { HistorialViewModel(repo, history, clock) }
            initializer { DesconexionViewModel(repo, clock, linkMonitor.log) }
            initializer { SimulacionViewModel(control, repo) }
            initializer { OperationalBarViewModel(repo, clock) }
        }
        advance(3_000)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            nav = rememberNavController()
            RondaTheme {
                Surface(Modifier.fillMaxSize()) {
                    RondaNavHost(viewModelFactory = factory, navController = nav, startDestination = Route.SELECCION)
                }
            }
        }
        settle()

        apertura()
        misionNormal()
        bateriaBaja()
        desconexion()
        obstaculo()
        persona()

        writeJson()
    }

    // ---- Guion ----

    private fun apertura() {
        scene(0, "Apertura")
        say("Es la garita de un edificio de oficinas de noche. El operador elige el robot para la ronda.")
        shot(3.5)
        say("RONDA-01 está disponible. RONDA-03 lo controla otro operador y RONDA-02 no es compatible.")
        shot(4.5)
        say("Con “Tomar control” el operador queda como el único que puede darle órdenes.")
        click("Tomar control")
        shot(1.0)
        say("Supervisión: estado, enlace, plano del edificio y progreso por puntos de control. El robot está en la base.")
        live(frames = 20, virtualMs = 500)
        shot(3.0)
    }

    private fun misionNormal() {
        scene(1, "Misión normal")
        go(Route.MISIONES)
        say("Antes de salir, la app verifica batería, enlace, alertas críticas y que el control sea propio.")
        scrollToText("ANTES DE SALIR")
        shot(5.5)
        say("Al tocar “Iniciar ronda”, la orden queda PENDIENTE hasta que el robot responde.")
        click("Iniciar ronda")
        scrollToText("ANTES DE SALIR")
        shot(3.0)
        say("CONFIRMADA: el robot aceptó la orden. Recién cuando la telemetría muestra el efecto, pasa a VERIFICADA.")
        live(frames = 12, virtualMs = 100)
        scrollToText("ANTES DE SALIR")
        shot(3.5)

        go(Route.SUPERVISION)
        say("La ronda avanza en cámara rápida. Cada punto de control se marca recién cuando el robot lo verifica.")
        lapse(msPerFrame = 4_000, maxFrames = 50) { verified() >= 3 }
        scrollToText("RONDA COMPLETA")
        lapse(msPerFrame = 4_000, maxFrames = 25) { verified() >= 4 }
        shot(1.5)

        go(Route.TELEMETRIA)
        say("Telemetría: cada lectura indica su antigüedad. También muestra los puntos verificados y la temperatura de la sala técnica.")
        live(frames = 8, virtualMs = 400)
        shot(4.0)

        go(Route.SUPERVISION)
        say("La ronda sigue hasta el último punto de control.")
        scrollToText("RONDA COMPLETA")
        lapse(msPerFrame = 7_000, maxFrames = 60) { state() == OperationalState.COMPLETADA }
        say("Ronda completada: 8 de 8 puntos verificados.")
        shot(3.0)
        scrollTop()
        say("Estado COMPLETADA. Después el robot vuelve solo a la base.")
        shot(2.5)
        lapse(msPerFrame = 2_000, maxFrames = 6) { false }
        shot(1.5)
    }

    private fun bateriaBaja() {
        scene(2, "Batería baja")
        go(Route.SIMULACION)
        say("El panel de simulación existe solo para la demo. Carga el escenario: 40 % de batería y una celda degradada.")
        click("BATERÍA BAJA")
        live(frames = 2, virtualMs = 500)
        shot(4.0)
        back()
        advance(2_000)
        say("Se inicia la ronda con el mínimo operativo. La batería baja rápido.")
        repoStartRound()
        lapse(msPerFrame = 6_000, maxFrames = 80) { battery() <= 25 && repo.telemetry.value?.unacknowledgedAlerts?.isNotEmpty() == true }
        settle()
        scrollTop()
        say("A 25 % aparece la alerta y arriba se destaca “Regresar a base” como acción principal: todavía alcanza para volver.")
        shot(5.0)
        say("La confirmación sigue siendo mantener presionado 1 s. El operador decide seguir un poco más.")
        scrollToText("ALERTAS ACTIVAS")
        shot(3.5)
        scrollTop()
        say("La ronda continúa mientras la batería sigue bajando.")
        lapse(msPerFrame = 6_000, maxFrames = 80) { repo.activeIncident.value != null }
        settle()
        say("A 15 % el robot propone volver a la base. La máquina propone y la persona decide.")
        shot(4.5)
        say("Para confirmar hay que mantener presionado 1 s: un toque accidental no alcanza.")
        holdSheetButton("Regresar a la base")
        say("El robot regresa a la base con la batería que le queda.")
        live(frames = 8, virtualMs = 1_500)
        shot(2.5)
    }

    private fun desconexion() {
        scene(3, "Desconexión")
        go(Route.SIMULACION)
        say("Escenario de desconexión, cargado desde el panel. Se inicia una ronda normal.")
        click("DESCONEXIÓN")
        live(frames = 2, virtualMs = 500)
        shot(2.5)
        back()
        advance(2_000)
        repoStartRound()
        lapse(msPerFrame = 6_000, maxFrames = 60) { verified() >= 2 }
        // La alerta de temperatura salta durante el corte: queda retenida hasta que vuelve el enlace.
        control.setTechRoomTempC(29.0)
        lapse(msPerFrame = 4_000, maxFrames = 12) { false }
        control.setResponseDelayMs(4_000)
        scrollToText("ÓRDENES")
        say("El operador pausa la ronda. La orden sale y queda PENDIENTE…")
        click("Pausar")
        live(frames = 2, virtualMs = 200)
        shot(2.5)
        say("…y justo ahí se corta el enlace. La respuesta del robot no llega.")
        control.setLinkUp(false)
        live(frames = 10, virtualMs = 500)
        say("Sin respuesta a tiempo, la orden queda SIN RESPUESTA: la app no sabe si se ejecutó y lo dice.")
        live(frames = 6, virtualMs = 500)
        shot(3.0)
        scrollTop()
        say("Arriba: “Sin datos desde hace N s”. Los valores se muestran como último dato conocido, nunca como actuales.")
        live(frames = 12, virtualMs = 500)
        shot(2.0)
        scrollToText("ÓRDENES")
        say("Las órdenes se deshabilitan con su motivo. La detención operativa sigue disponible.")
        live(frames = 4, virtualMs = 500)
        shot(3.0)
        go(Route.DESCONEXION)
        say("Desconexión: el último dato conocido y qué hace el robot sin enlace (se detiene y a los 60 s vuelve a la base).")
        live(frames = 6, virtualMs = 500)
        shot(4.0)
        back()
        control.setResponseDelayMs(0)
        control.setLinkUp(true)
        say("Vuelve el enlace: datos al día. La pausa sí había llegado: el robot está PAUSADA.")
        live(frames = 4, virtualMs = 500)
        scrollTop()
        shot(3.5)
        go(Route.ALERTAS)
        say("La alerta que el robot emitió durante el corte llega ahora, con su hora original.")
        live(frames = 2, virtualMs = 500)
        shot(5.0)
    }

    private fun obstaculo() {
        scene(4, "Obstáculo en el pasillo")
        go(Route.SIMULACION)
        say("Escenario de obstáculo: un objeto bloquea el pasillo antes del PC4.")
        click("OBSTÁCULO")
        live(frames = 2, virtualMs = 500)
        shot(2.5)
        go(Route.SUPERVISION)
        advance(2_000)
        repoStartRound()
        say("Ronda en curso, en cámara rápida.")
        lapse(msPerFrame = 8_000, maxFrames = 80) { repo.activeIncident.value != null }
        settle()
        say("REQUIERE DECISIÓN: el robot se detiene a 2 m y manda una foto de la cámara frontal.")
        shot(5.0)
        say("Ofrece tres opciones: esperar 30 s, saltar el punto o regresar a la base. Regresar pide mantener presionado.")
        shot(6.0)
        say("El operador elige “Saltar el PC4”.")
        shot(2.0)
        click("Saltar el PC4")
        live(frames = 4, virtualMs = 500)
        settle()
        scrollToText("RONDA COMPLETA")
        say("El PC4 queda con observación, no como verificado. La ronda sigue hacia el PC5.")
        lapse(msPerFrame = 3_000, maxFrames = 10) { false }
        shot(4.0)
    }

    private fun persona() {
        scene(5, "Persona en zona restringida")
        go(Route.SIMULACION)
        say("Escenario de persona en zona restringida: la sala técnica (PC5), fuera de horario.")
        click("PERSONA")
        live(frames = 2, virtualMs = 500)
        shot(2.5)
        go(Route.SUPERVISION)
        advance(2_000)
        repoStartRound()
        say("Ronda en curso, en cámara rápida.")
        lapse(msPerFrame = 9_000, maxFrames = 80) { repo.activeIncident.value != null }
        settle()
        say("Alerta crítica: hay una persona en la sala técnica. El robot mantiene 2 m y se identifica por parlante.")
        shot(5.0)
        say("El detector puede equivocarse: el robot se detiene y la persona decide.")
        shot(5.0)
        say("El operador mira la foto y elige “Enviar vigilante”.")
        shot(2.0)
        click("Enviar vigilante")
        live(frames = 4, virtualMs = 500)
        settle()
        scrollTop()
        say("La decisión queda cerrada y la hoja no vuelve. Arriba: “Vigilante en camino · el robot espera tu orden para seguir”.")
        shot(5.0)
        say("El robot mantiene la distancia y queda PAUSADA en el PC5 hasta que el operador lo reanude.")
        live(frames = 6, virtualMs = 1_000)
        shot(2.5)
        go(Route.HISTORIAL)
        say("Historial: la detección, el lugar, la hora y la decisión del operador (“Enviar vigilante”). La foto no se guarda.")
        live(frames = 2, virtualMs = 500)
        scrollToText("EVENTOS")
        shot(7.0)
        go(Route.SUPERVISION)
        scrollToText("ÓRDENES")
        say("El vigilante resolvió la situación: el operador reanuda y la ronda sigue.")
        shot(2.0)
        click("Reanudar")
        live(frames = 4, virtualMs = 750)
        scrollTop()
        say("La ronda sigue: el PC5 queda con observación por la persona detectada.")
        lapse(msPerFrame = 3_000, maxFrames = 6) { verified() >= 5 }
        shot(3.5)
    }

    // ---- Pasos ----

    private fun scene(n: Int, name: String) { scenario = n; title = name }
    private fun say(t: String) { text = t }

    private fun verified() = repo.telemetry.value?.service?.checkpointsVerified ?: 0
    private fun state() = repo.telemetry.value?.operationalState
    private fun battery() = repo.telemetry.value?.battery?.percent ?: 100

    private fun repoStartRound() {
        repo.send(CommandType.START_MISSION, ar.edu.uade.ronda.core.model.CommandParams(routeId = ar.edu.uade.ronda.core.model.RouteId.RONDA_COMPLETA))
    }

    private fun advance(ms: Long) { scheduler.advanceTimeBy(ms); scheduler.runCurrent() }

    /** Avanza el tiempo virtual y el reloj de Compose, y guarda un cuadro de 0,2 s. */
    private fun frame(virtualMs: Long, seconds: Double = 0.2) {
        advance(virtualMs)
        rule.mainClock.advanceTimeBy(100)
        scheduler.runCurrent()
        rule.mainClock.advanceTimeBy(100)
        shot(seconds)
    }

    private fun live(frames: Int, virtualMs: Long) = repeat(frames) { frame(virtualMs) }

    /** Cámara rápida: avanza de a medio segundo virtual hasta [done] y guarda un cuadro cada [msPerFrame]. */
    private fun lapse(msPerFrame: Long, maxFrames: Int, done: () -> Boolean) {
        var frames = 0
        var acc = 0L
        while (!done() && frames < maxFrames) {
            advance(500); acc += 500
            if (acc >= msPerFrame) { acc = 0; frame(0); frames++ }
        }
        if (acc > 0) frame(0)
    }

    private fun click(label: String) {
        settle()
        rule.onAllNodes(hasText(label, substring = true, ignoreCase = true) and hasClickAction() and isEnabled(), useUnmergedTree = false)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        scheduler.runCurrent()
        settle()
    }

    /** Mantiene presionado el botón de la hoja, mostrando cómo se llena la barra. */
    private fun holdSheetButton(label: String) {
        settle()
        val node = rule.onAllNodes(hasContentDescription(label, substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).onFirst()
        node.performTouchInput { down(center) }
        repeat(3) {
            rule.mainClock.advanceTimeBy(250)
            scheduler.runCurrent()
            shot(1.0)
        }
        rule.mainClock.advanceTimeBy(400)
        // Al completarse la confirmación la hoja se cierra y el nodo ya no está: no hay a quién soltar.
        runCatching { node.performTouchInput { up() } }
        scheduler.runCurrent()
        settle()
    }

    private fun go(route: Route) {
        rule.runOnUiThread { nav.navigate(route.path) { launchSingleTop = true } }
        settle()
    }

    private fun back() {
        rule.runOnUiThread { nav.popBackStack() }
        settle()
    }

    private fun scroller() = rule.onAllNodes(hasScrollAction()).onFirst()

    private fun scrollTop() {
        settle()
        scroller().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        settle()
    }

    /** Deja el texto cerca del borde superior de la pantalla. */
    private fun scrollToText(label: String) {
        scrollTop()
        val node = rule.onAllNodes(hasText(label, substring = true)).onFirst()
        // Un ScrollBy largo no se aplica entero: primero se trae el nodo a la vista y después se ajusta.
        rule.mainClock.autoAdvance = true
        try { node.performScrollTo() } finally { rule.mainClock.autoAdvance = false }
        settle()
        val top = node.fetchSemanticsNode().boundsInRoot.top
        scroller().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - 140f) }
        settle()
    }

    private fun settle() {
        rule.mainClock.autoAdvance = true
        try { rule.waitForIdle() } finally { rule.mainClock.autoAdvance = false }
        repeat(8) { scheduler.runCurrent(); rule.mainClock.advanceTimeBy(250) }
        scheduler.runCurrent(); rule.mainClock.advanceTimeBy(100)
    }

    /** Guarda el cuadro actual por [seconds]; si es igual al anterior con el mismo texto, solo alarga ese. */
    private fun shot(seconds: Double) {
        rule.mainClock.autoAdvance = true
        val bmp = try { capture() } finally { rule.mainClock.autoAdvance = false } ?: return
        val scaled = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
        val prev = entries.lastOrNull()
        if (prev != null && prev.text == text && prev.scenario == scenario && lastBitmap?.sameAs(scaled) == true) {
            prev.seconds += seconds
            return
        }
        val name = "f_%05d.png".format(entries.size)
        File(out, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        lastBitmap = scaled
        entries += Entry(name, seconds, scenario, title, text)
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

    private fun writeJson() {
        fun esc(s: String) = buildString {
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
                    else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                }
            }
        }
        val json = entries.joinToString(",\n", "[\n", "\n]\n") { e ->
            "  {\"archivo\": \"${e.file}\", \"duracion\": ${"%.2f".format(java.util.Locale.ROOT, e.seconds)}, " +
                "\"escenario\": ${e.scenario}, \"titulo\": \"${esc(e.title)}\", \"texto\": \"${esc(e.text)}\"}"
        }
        File(out, "frames.json").writeText(json)
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
