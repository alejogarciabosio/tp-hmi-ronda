package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.*
import ar.edu.uade.ronda.core.repository.CheckpointHistory
import ar.edu.uade.ronda.core.repository.TrackedCommand
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

    private var history: CheckpointHistory? = null

    /** Como en AppContainer: una sola historia de puntos de control para todos los ViewModels. */
    private fun TestScope.history(): CheckpointHistory =
        history ?: CheckpointHistory(repo.telemetry, backgroundScope).also { history = it }

    private fun TestScope.newViewModel() =
        SupervisionViewModel(repo, Clock { testScheduler.currentTime }, ControlSession("RONDA-01"), history().log)

    /** El reloj es el del planificador de pruebas: avanzar el tiempo virtual avanza la antigüedad. */
    private fun TestScope.viewModel(): SupervisionViewModel {
        val vm = newViewModel()
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
    fun `la historia de puntos de control sobrevive a salir de la pantalla mas de 5 segundos`() = runTest(dispatcher) {
        val vm = newViewModel()
        val sub = vm.uiState.onEach { }.launchIn(backgroundScope)
        runCurrent()
        repo.publish(tele(1_000, index = 1, verified = 0)); runCurrent()
        repo.publish(tele(2_000, index = 2, verified = 1)); runCurrent()
        sub.cancel()
        // El operador se va de Supervisión: mientras tanto el robot verifica el PC2.
        advanceTimeBy(6_000); runCurrent()
        repo.publish(tele(8_000, index = 3, verified = 2)); runCurrent()
        vm.uiState.onEach { }.launchIn(backgroundScope)
        runCurrent()
        val byIndex = vm.uiState.value.plan.checkpoints.associateBy { it.index }
        assertEquals(CheckpointState.VERIFIED, byIndex.getValue(1).state)
        assertNotNull(byIndex.getValue(1).time)
        assertEquals(CheckpointState.VERIFIED, byIndex.getValue(2).state)
        assertEquals(CheckpointState.CURRENT, byIndex.getValue(3).state)
    }

    @Test
    fun `un viewmodel nuevo muestra los puntos ya verificados de la ronda en curso`() = runTest(dispatcher) {
        val first = viewModel()
        repo.publish(tele(1_000, index = 1, verified = 0)); runCurrent()
        repo.publish(tele(2_000, index = 2, verified = 1)); runCurrent()
        assertEquals(CheckpointState.VERIFIED, first.uiState.value.plan.checkpoints[0].state)
        // Al navegar, el ViewModel anterior se destruye y se crea otro.
        val second = viewModel()
        val byIndex = second.uiState.value.plan.checkpoints.associateBy { it.index }
        assertEquals(CheckpointState.VERIFIED, byIndex.getValue(1).state)
        assertNotNull(byIndex.getValue(1).time)
        assertEquals(CheckpointState.CURRENT, byIndex.getValue(2).state)
    }

    private fun personAlert(acknowledged: Boolean = false) =
        Alert("a7", AlertType.PERSON_DETECTED, Severity.CRITICAL, "Persona detectada", 0, acknowledged)

    private fun personIncident() = Incident(
        "i2", IncidentType.PERSON_IN_RESTRICTED_ZONE, 5, "Persona en zona restringida",
        listOf(
            IncidentOption("AUTHORIZED_PERSON", "Persona autorizada (registrar)"),
            IncidentOption("SEND_GUARD", "Enviar vigilante"),
            IncidentOption("CONTINUE_ROUND", "Continuar ronda"),
        ),
    )

    private fun ackGuard(status: CommandStatus, at: Long) = TrackedCommand(
        Command("g$at", "RONDA-01", at, "garita", CommandType.ACKNOWLEDGE_ALERT,
            CommandParams(alertId = "a7", resolution = AlertResolution.SEND_GUARD)),
        status, at, at,
    )

    @Test
    fun `elegir una opcion cierra la hoja enseguida y si se rechaza se puede reabrir`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 5, alerts = listOf(personAlert())))
        repo.activeIncident.value = personIncident()
        runCurrent()
        vm.onDecisionOption("SEND_GUARD"); runCurrent()
        assertTrue(vm.uiState.value.decisionDismissed, "la hoja no vuelve a ofrecer las mismas opciones")
        // Mientras el robot no cierre el incidente (p. ej. si rechaza la orden), se puede reabrir.
        vm.onReopenDecision(); runCurrent()
        assertFalse(vm.uiState.value.decisionDismissed)
    }

    @Test
    fun `con el vigilante en camino avisa que el robot espera la orden y reanudar queda habilitado`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, OperationalState.REQUIERE_DECISION, index = 5, alerts = listOf(personAlert())))
        repo.activeIncident.value = personIncident()
        runCurrent()
        assertNull(vm.uiState.value.guardNotice)

        // El robot confirma: cierra el incidente y queda pausado en el PC5.
        repo.commands.value = listOf(ackGuard(CommandStatus.VERIFIED, 500))
        repo.activeIncident.value = null
        repo.publish(tele(1_000, OperationalState.PAUSADA, index = 5, alerts = listOf(personAlert(acknowledged = true))))
        runCurrent()
        val s = vm.uiState.value
        assertNull(s.decision, "la hoja no vuelve")
        assertEquals("Vigilante en camino · el robot espera tu orden para seguir", s.guardNotice)
        assertEquals(CommandUiState.ENABLED, s.resume.state)
        assertTrue(s.strip.needsAction)
        assertTrue(s.strip.actionText.contains("reanudá"))

        // Al reanudar, el aviso desaparece.
        repo.commands.value = repo.commands.value + tracked("r1", CommandType.RESUME_MISSION, CommandStatus.VERIFIED, 2_000)
        repo.publish(tele(2_500, OperationalState.EN_RONDA, index = 5, alerts = listOf(personAlert(acknowledged = true))))
        runCurrent()
        assertNull(vm.uiState.value.guardNotice)
    }

    private fun lowBatteryAlert() =
        Alert("a3", AlertType.LOW_BATTERY, Severity.WARNING, "Batería baja (25 %): alcanza para volver a la base desde acá.", 0, false)

    @Test
    fun `con bateria baja activa destaca regresar a la base arriba`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, batteryPct = 24, alerts = listOf(lowBatteryAlert())))
        runCurrent()
        val s = vm.uiState.value
        assertEquals("Batería baja (24 %): volvé a la base mientras alcanza", s.lowBatteryReturn)
        assertEquals(CommandUiState.ENABLED, s.returnToBase.state)
    }

    @Test
    fun `si la bateria ya no alcanza el aviso pide regresar ya`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, batteryPct = 14, pose = Pose(1, 40.0, 55.0, 0.0, "Pasillo B"), alerts = listOf(lowBatteryAlert())))
        runCurrent()
        assertEquals("Batería baja (14 %): puede no alcanzar para volver a la base. Regresá ya.", vm.uiState.value.lowBatteryReturn)
    }

    @Test
    fun `sin alerta de bateria o sin poder regresar no se destaca nada`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.availability.value = enabledAll()
        repo.publish(tele(0, batteryPct = 24))
        runCurrent()
        assertNull(vm.uiState.value.lowBatteryReturn, "sin alerta activa la UI queda como está")

        repo.publish(tele(500, batteryPct = 24, alerts = listOf(lowBatteryAlert().copy(acknowledged = true))))
        runCurrent()
        assertNull(vm.uiState.value.lowBatteryReturn, "una alerta ya reconocida no está activa")

        repo.availability.value = enabledAll(CommandType.RETURN_TO_SAFE_POINT to "El robot ya está regresando")
        repo.publish(tele(1_000, OperationalState.REGRESANDO, batteryPct = 24, alerts = listOf(lowBatteryAlert())))
        runCurrent()
        assertNull(vm.uiState.value.lowBatteryReturn, "con regresar deshabilitado no se ofrece")
    }
}
