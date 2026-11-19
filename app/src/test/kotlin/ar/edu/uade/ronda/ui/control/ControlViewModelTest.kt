package ar.edu.uade.ronda.ui.control

import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.supervision.FakeRobotRepository
import ar.edu.uade.ronda.ui.supervision.enabledAll
import ar.edu.uade.ronda.ui.supervision.incident
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
class ControlViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun tracked(type: CommandType, status: CommandStatus, at: Long, params: CommandParams = CommandParams(), reason: RejectReason? = null) =
        TrackedCommand(Command("m$at", "RONDA-01", at, "op", type, params), status, at, at, reason)

    private fun inputs(commands: List<TrackedCommand> = emptyList(), incident: Boolean = false) = ControlInputs(
        tele(1_000), ConnectionState.CONNECTED, 1_000, if (incident) incident() else null, commands, enabledAll(), 1_400,
    )

    @Test fun pausarPendienteSeMuestraComoPendiente() {
        val s = ControlMapper.map(inputs(listOf(tracked(CommandType.PAUSE_MISSION, CommandStatus.PENDING, 1_000))))
        assertEquals(CommandUiState.PENDING, s.pause.state)
    }

    @Test fun regresoRechazadoMuestraElMotivo() {
        val s = ControlMapper.map(
            inputs(listOf(tracked(CommandType.RETURN_TO_SAFE_POINT, CommandStatus.REJECTED, 1_000, CommandParams(safePointId = SafePointId.BASE), RejectReason.INVALID_STATE))),
        )
        assertEquals(CommandUiState.REJECTED, s.returnToBase.state)
        assertTrue(s.returnToBase.reason!!.contains("estado no válido"))
        assertEquals(CommandUiState.ENABLED, s.safePoint.state)
    }

    @Test fun puntoSeguroSinRespuesta() {
        val s = ControlMapper.map(
            inputs(listOf(tracked(CommandType.RETURN_TO_SAFE_POINT, CommandStatus.NO_RESPONSE, 1_000, CommandParams(safePointId = SafePointId.DESCANSO_ESCALERA_E)))),
        )
        assertEquals(CommandUiState.NO_RESPONSE, s.safePoint.state)
        assertEquals(CommandChipStatus.NO_RESPONSE, s.log.single().chip.status)
        assertEquals("Ir al punto seguro", s.log.single().label)
    }

    @Test fun saltarSoloConDecisionDeObstaculo() {
        assertEquals(CommandUiState.DISABLED, ControlMapper.map(inputs()).skipCheckpoint.state)
        assertEquals(CommandUiState.ENABLED, ControlMapper.map(inputs(incident = true)).skipCheckpoint.state)
    }

    @Test fun elRegistroVaDelMasNuevoAlMasViejo() {
        val s = ControlMapper.map(
            inputs(listOf(
                tracked(CommandType.START_MISSION, CommandStatus.VERIFIED, 1_000),
                tracked(CommandType.PAUSE_MISSION, CommandStatus.CONFIRMED, 2_000),
            )),
        )
        assertEquals(listOf("Pausar", "Iniciar ronda"), s.log.map { it.label })
    }

    @Test fun saltarEnviaLaDecision() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000, state = OperationalState.REQUIERE_DECISION))
        repo.activeIncident.value = incident()
        repo.availability.value = enabledAll()
        val vm = ControlViewModel(repo, Clock { 1_200L }, ControlSession("RONDA-01"))
        vm.onSkipCheckpoint()
        assertEquals(CommandType.RESUME_MISSION, repo.sent.single().first)
        assertEquals(IncidentDecision.SKIP_CHECKPOINT, repo.sent.single().second.decision)
    }

    @Test fun sinControlPropioLasOrdenesQuedanDeshabilitadasConSuMotivo() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000, state = OperationalState.EN_RONDA))
        repo.availability.value = enabledAll()
        val session = ControlSession()
        val vm = ControlViewModel(repo, Clock { 1_200L }, session)
        assertEquals(CommandUiState.DISABLED, vm.uiState.value.pause.state)
        assertEquals(ControlSession.NO_CONTROL_REASON, vm.uiState.value.returnToBase.reason)
        vm.onPause()
        assertTrue(repo.sent.isEmpty())
        session.take("RONDA-01")
        vm.onPause()
        assertEquals(CommandType.PAUSE_MISSION, repo.sent.single().first)
    }

    @Test fun deshabilitadoNoEnvia() {
        val repo = FakeRobotRepository()
        repo.publish(tele(1_000))
        repo.availability.value = enabledAll(CommandType.CANCEL_MISSION to "No hay una misión para cancelar")
        val vm = ControlViewModel(repo, Clock { 1_200L }, ControlSession("RONDA-01"))
        vm.onCancelMission()
        assertTrue(repo.sent.isEmpty())
    }
}
