package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.*
import ar.edu.uade.ronda.ui.model.CheckpointState
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeRobotRepository()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /** El reloj es el del planificador de pruebas: avanzar el tiempo virtual avanza la antigüedad. */
    private fun TestScope.viewModel(): SupervisionViewModel {
        val vm = SupervisionViewModel(repo, Clock { testScheduler.currentTime }, ControlSession("RONDA-01"))
        vm.uiState.onEach { }.launchIn(backgroundScope)
        runCurrent()
        return vm
    }

    @Test
    fun `sin telemetria no dice conectado y avisa que no hay datos`() = runTest(dispatcher) {
        val vm = viewModel()
        val s = vm.uiState.value
        assertFalse(s.hasData)
        assertEquals(StatusLevel.STALE, s.strip.connectionLevel)
        assertFalse(s.strip.connectionLabel.contains("Conectado"))
        assertTrue(s.strip.needsAction)
        assertTrue(s.readouts.isEmpty())
    }

    @Test
    fun `con enlace sano muestra estado, conexion con antiguedad y lecturas`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(1_000)
        repo.availability.value = enabledAll()
        repo.publish(tele(ts = testScheduler.currentTime))
        advanceTimeBy(500); runCurrent()
        val s = vm.uiState.value
        assertEquals("En ronda", s.strip.stateLabel)
        assertEquals("Conectado · 0,5 s", s.strip.connectionLabel)
        assertEquals(StatusLevel.OK, s.strip.connectionLevel)
        assertFalse(s.strip.needsAction)
        assertNull(s.staleBanner)
        assertEquals("Ronda completa · PC 3/8", s.progressTitle)
        assertEquals(listOf("Batería", "Conexión", "Sala técnica", "Salidas libres"), s.readouts.map { it.label })
        assertTrue(s.readouts[0].detail!!.startsWith("Alcanza para volver: sí"))
        assertEquals("2/2", s.readouts[3].value)
    }

    @Test
    fun `sin telemetria nueva la antiguedad sigue corriendo y nunca queda conectado con datos viejos`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(ts = 0))
        advanceTimeBy(500); runCurrent()
        assertEquals("Conectado · 0,5 s", vm.uiState.value.strip.connectionLabel)

        // El repositorio degrada el enlace por antigüedad; el modelo de UI lo refleja con la edad real.
        repo.connection.value = ConnectionState.NO_DATA
        advanceTimeBy(11_500); runCurrent()
        val s = vm.uiState.value
        assertEquals("Sin datos · hace 12 s", s.strip.connectionLabel)
        assertEquals(StatusLevel.STALE, s.strip.level)
        assertEquals("En ronda (último conocido)", s.strip.stateLabel)
        assertEquals(12, s.staleBanner?.secondsWithoutData)
        assertTrue(s.readouts.all { it.level == StatusLevel.STALE })
        assertTrue(s.planCaption!!.contains("no vigente"))
    }

    @Test
    fun `la politica sin enlace se declara recien desde los 30 segundos`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.publish(tele(ts = 0))
        repo.connection.value = ConnectionState.DISCONNECTED
        advanceTimeBy(15_000); runCurrent()
        assertNull(vm.uiState.value.staleBanner?.policyText)
        advanceTimeBy(16_000); runCurrent()
        assertNotNull(vm.uiState.value.staleBanner?.policyText)
    }

    @Test
    fun `los botones siguen la disponibilidad y muestran el motivo`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll(CommandType.PAUSE_MISSION to "Solo se puede pausar durante la ronda")
        repo.publish(tele(0, OperationalState.PAUSADA))
        runCurrent()
        val s = vm.uiState.value
        assertEquals(CommandUiState.DISABLED, s.pause.state)
        assertEquals("Solo se puede pausar durante la ronda", s.pause.reason)
        assertEquals(CommandUiState.ENABLED, s.resume.state)
        assertTrue(s.stop.enabled)
    }

    @Test
    fun `la detencion operativa deshabilitada explica por que`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll(CommandType.OPERATIONAL_STOP to "El robot no está en movimiento")
        repo.publish(tele(0, OperationalState.EN_BASE, missionId = null))
        runCurrent()
        assertFalse(vm.uiState.value.stop.enabled)
        assertEquals("El robot no está en movimiento", vm.uiState.value.stop.reason)
    }

    @Test
    fun `los eventos de la pantalla envian los comandos correspondientes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPause(); vm.onResume(); vm.onReturnToBase(); vm.onOperationalStop()
        assertEquals(
            listOf(CommandType.PAUSE_MISSION, CommandType.RESUME_MISSION, CommandType.RETURN_TO_SAFE_POINT, CommandType.OPERATIONAL_STOP),
            repo.sent.map { it.first },
        )
        assertEquals(SafePointId.BASE, repo.sent[2].second.safePointId)
    }

    @Test
    fun `un comando pendiente se ve en el boton y en el chip`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll(CommandType.PAUSE_MISSION to "Hay una orden pendiente de respuesta")
        repo.publish(tele(0))
        repo.commands.value = listOf(tracked("c1", CommandType.PAUSE_MISSION, CommandStatus.PENDING, sentAt = 0))
        advanceTimeBy(1_200); runCurrent()
        val s = vm.uiState.value
        assertEquals(CommandUiState.PENDING, s.pause.state)
        assertEquals(CommandChipStatus.PENDING, s.lastCommand?.chip?.status)
        assertEquals("Pausar", s.lastCommand?.label)
    }

    @Test
    fun `un comando rechazado muestra el motivo del robot`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0))
        repo.commands.value = listOf(
            tracked("c1", CommandType.RETURN_TO_SAFE_POINT, CommandStatus.REJECTED, 0).copy(reasonText = "Batería insuficiente"),
        )
        runCurrent()
        val s = vm.uiState.value
        assertEquals(CommandUiState.REJECTED, s.returnToBase.state)
        assertEquals("Rechazada: Batería insuficiente", s.returnToBase.reason)
        assertEquals(CommandChipStatus.REJECTED, s.lastCommand?.chip?.status)
    }

    @Test
    fun `un comando sin respuesta no se da por ejecutado`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0))
        repo.commands.value = listOf(tracked("c1", CommandType.PAUSE_MISSION, CommandStatus.NO_RESPONSE, 0))
        advanceTimeBy(120_000); runCurrent()
        assertEquals(CommandUiState.NO_RESPONSE, vm.uiState.value.pause.state)
        assertEquals(CommandChipStatus.NO_RESPONSE, vm.uiState.value.lastCommand?.chip?.status)
    }

    @Test
    fun `el comando confirmado desaparece del chip pasado el tiempo`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0))
        repo.commands.value = listOf(tracked("c1", CommandType.PAUSE_MISSION, CommandStatus.VERIFIED, 0))
        runCurrent()
        assertNotNull(vm.uiState.value.lastCommand)
        advanceTimeBy(31_000); runCurrent()
        assertNull(vm.uiState.value.lastCommand)
    }

    @Test
    fun `la decision con foto arma el pie con la antiguedad y la descripcion accesible`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 4))
        repo.activeIncident.value = incident().copy(snapshot = Snapshot("sim-obstaculo", "image/jpeg", 0))
        advanceTimeBy(4_000); runCurrent()
        repo.publish(tele(4_000, OperationalState.REQUIERE_DECISION, index = 4)); runCurrent()
        val snap = vm.uiState.value.decision?.snapshot
        assertEquals("sim-obstaculo", snap?.id)
        assertEquals("Cámara frontal · hace 4,0 s", snap?.caption)
        assertEquals("Foto de la cámara frontal del robot: Obstáculo en el pasillo", snap?.contentDescription)
    }

    @Test
    fun `con el enlace caido la foto se marca como del momento del incidente`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 4))
        repo.activeIncident.value = incident().copy(snapshot = Snapshot("sim-obstaculo", "image/jpeg", 0))
        runCurrent()
        repo.connection.value = ConnectionState.NO_DATA
        advanceTimeBy(20_000); runCurrent()
        assertEquals("Imagen del momento del incidente", vm.uiState.value.decision?.snapshot?.caption)
    }

    @Test
    fun `un incidente sin foto no inventa una`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 4))
        repo.activeIncident.value = incident()
        runCurrent()
        assertNull(vm.uiState.value.decision?.snapshot)
    }

    @Test
    fun `con un incidente abre la decision y bloquea reanudar a secas`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 4))
        repo.activeIncident.value = incident()
        runCurrent()
        val s = vm.uiState.value
        assertEquals("Sí: decidí cómo seguir en PC4", s.strip.actionText)
        assertEquals(3, s.decision?.options?.size)
        assertTrue(s.decision!!.options.single { it.id == "RETURN_TO_SAFE_POINT" }.requiresHold)
        assertEquals(CommandUiState.DISABLED, s.resume.state)
        assertFalse(s.decisionDismissed)
    }

    @Test
    fun `las opciones de decision envian el comando con sus parametros`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 4))
        repo.activeIncident.value = incident()
        runCurrent()
        vm.onDecisionOption("WAIT_30S")
        vm.onDecisionOption("SKIP_CHECKPOINT")
        vm.onDecisionOption("RETURN_TO_SAFE_POINT")
        assertEquals(CommandType.RESUME_MISSION to IncidentDecision.WAIT_30S, repo.sent[0].first to repo.sent[0].second.decision)
        assertEquals(IncidentDecision.SKIP_CHECKPOINT, repo.sent[1].second.decision)
        assertEquals(CommandType.RETURN_TO_SAFE_POINT, repo.sent[2].first)
        assertEquals(SafePointId.BASE, repo.sent[2].second.safePointId)
    }

    @Test
    fun `la decision sobre una persona reconoce la alerta con la resolucion elegida`() = runTest(dispatcher) {
        val vm = viewModel()
        val alert = Alert("a7", AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona detectada", 0, false)
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 5, alerts = listOf(alert)))
        repo.activeIncident.value = Incident(
            "i2", IncidentType.PERSON_IN_RESTRICTED_ZONE, 5, "Persona en zona restringida",
            listOf(IncidentOption("SEND_GUARD", "Enviar vigilante")),
        )
        runCurrent()
        vm.onDecisionOption("SEND_GUARD")
        val (type, params) = repo.sent.single()
        assertEquals(CommandType.ACKNOWLEDGE_ALERT, type)
        assertEquals("a7", params.alertId)
        assertEquals(AlertResolution.SEND_GUARD, params.resolution)
        assertEquals(StatusLevel.ERROR, vm.uiState.value.alerts.single().level)
    }

    @Test
    fun `una opcion de persona sin alerta de persona avisa en vez de callar`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 5))
        repo.activeIncident.value = incident(IncidentType.PERSON_IN_RESTRICTED_ZONE)
        runCurrent()
        vm.onDecisionOption("SEND_GUARD"); runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertTrue(vm.uiState.value.notice!!.contains("alerta de persona"))
        vm.onNoticeDismissed(); runCurrent()
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `una decision que ya no esta pendiente avisa`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onDecisionOption("WAIT_30S"); runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertNotNull(vm.uiState.value.notice)
    }

    @Test
    fun `cerrar la hoja la oculta hasta reabrirla y un incidente nuevo vuelve a abrirla`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION))
        repo.activeIncident.value = incident()
        runCurrent()
        vm.onDismissDecision(); runCurrent()
        assertTrue(vm.uiState.value.decisionDismissed)
        vm.onReopenDecision(); runCurrent()
        assertFalse(vm.uiState.value.decisionDismissed)
        vm.onDismissDecision(); runCurrent()
        repo.activeIncident.value = incident().copy(id = "i9"); runCurrent()
        assertFalse(vm.uiState.value.decisionDismissed)
    }

    @Test
    fun `la bateria baja lejos de la base avisa que no alcanza para volver`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, batteryPct = 14, pose = Pose(1, 40.0, 55.0, 0.0, "Pasillo B")))
        runCurrent()
        val battery = vm.uiState.value.readouts[0]
        assertEquals(StatusLevel.ERROR, battery.level)
        assertTrue(battery.detail!!.startsWith("Alcanza para volver: no"))
    }

    @Test
    fun `una salida obstruida marca las salidas libres con error y dice cual`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.publish(
            tele(
                0, exits = listOf(
                    EmergencyExit("SE-PB", "Salida PB", ExitStatus.LIBRE),
                    EmergencyExit("SE-1", "Salida 1°", ExitStatus.OBSTRUIDA),
                ),
            ),
        )
        runCurrent()
        val exits = vm.uiState.value.readouts[3]
        assertEquals("1/2", exits.value)
        assertEquals(StatusLevel.ERROR, exits.level)
        assertEquals("Salida 1°: obstruida", exits.detail)
    }

    @Test
    fun `el piso del plano sigue al robot hasta que se elige otro`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.publish(tele(0, pose = Pose(1, 35.0, 35.0, 180.0, "Pasillo A")))
        runCurrent()
        assertEquals(1, vm.uiState.value.plan.floor)
        vm.onSelectFloor(0); runCurrent()
        assertEquals(0, vm.uiState.value.plan.floor)
        assertEquals(1, vm.uiState.value.plan.robot?.floor)
    }

    @Test
    fun `los puntos de control verificados, con observacion y salteados se deducen de la telemetria`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.publish(tele(1_000, index = 1, verified = 0))
        runCurrent()
        repo.publish(tele(2_000, index = 2, verified = 1))
        runCurrent()
        repo.publish(tele(3_000, index = 3, verified = 2, issues = 1))
        runCurrent()
        repo.publish(tele(4_000, index = 4, verified = 2, issues = 1))
        runCurrent()
        val byIndex = vm.uiState.value.plan.checkpoints.associateBy { it.index }
        assertEquals(CheckpointState.VERIFIED, byIndex.getValue(1).state)
        assertEquals(CheckpointState.WITH_ISSUE, byIndex.getValue(2).state)
        assertEquals(CheckpointState.SKIPPED, byIndex.getValue(3).state)
        assertEquals(CheckpointState.CURRENT, byIndex.getValue(4).state)
        assertEquals(CheckpointState.PENDING, byIndex.getValue(5).state)
        assertNull(byIndex.getValue(3).time)
        assertEquals(4, vm.uiState.value.checkpoints.size)
    }

    @Test
    fun `al completar la ronda el ultimo punto queda resuelto`() {
        var log = CheckpointLog()
        log = log.next(tele(1, index = 8, verified = 7))
        log = log.next(tele(2, OperationalState.COMPLETADA, index = 8, verified = 8))
        assertEquals(CheckpointState.VERIFIED, log.resolved.getValue(8).state)
    }
}
