package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.model.AlertResolution
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentDecision
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.SafePointId
import ar.edu.uade.ronda.core.scenarios.ScenarioRig
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Por el repositorio: cualquier decisión del operador cierra el incidente activo (docs/decisiones.md, D12). */
class DecisionRepositoryTest {

    private suspend fun TestScope.rigWith(scenario: SimScenario): ScenarioRig {
        val rig = ScenarioRig(this)
        rig.control.loadScenario(scenario)
        rig.until(what = "telemetría") { rig.repo.telemetry.value != null }
        rig.send(CommandType.START_MISSION, CommandParams(routeId = RouteId.RONDA_COMPLETA))
        rig.untilState(OperationalState.REQUIERE_DECISION)
        assertNotNull(rig.repo.activeIncident.value)
        return rig
    }

    private suspend fun ScenarioRig.decide(type: CommandType, params: CommandParams, expected: OperationalState) {
        val id = send(type, params)
        until(what = "decisión confirmada") { statusOf(id) == CommandStatus.CONFIRMED || statusOf(id) == CommandStatus.VERIFIED }
        untilState(expected)
        assertNull(repo.activeIncident.value, "la decisión quedó cerrada")
        advance(15_000)
        assertNull(repo.activeIncident.value, "la hoja no vuelve a ofrecer las mismas opciones")
    }

    private fun ScenarioRig.personParams(resolution: AlertResolution) = CommandParams(
        alertId = telemetry.alerts.single { it.type == AlertType.PERSON_DETECTED }.id,
        resolution = resolution,
    )

    @Test
    fun `persona autorizada cierra el incidente y la ronda sigue`() = runTest {
        val rig = rigWith(SimScenario.PERSON_IN_RESTRICTED_ZONE)
        rig.decide(CommandType.ACKNOWLEDGE_ALERT, rig.personParams(AlertResolution.AUTHORIZED_PERSON), OperationalState.EN_RONDA)
    }

    @Test
    fun `enviar vigilante cierra el incidente y deja la ronda pausada hasta reanudar`() = runTest {
        val rig = rigWith(SimScenario.PERSON_IN_RESTRICTED_ZONE)
        rig.decide(CommandType.ACKNOWLEDGE_ALERT, rig.personParams(AlertResolution.SEND_GUARD), OperationalState.PAUSADA)
        assertEquals(OperationalState.PAUSADA, rig.state, "espera la orden del operador")
        val id = rig.send(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId))
        rig.until(what = "reanudada") { rig.statusOf(id) == CommandStatus.VERIFIED }
        assertEquals(OperationalState.EN_RONDA, rig.state)
    }

    @Test
    fun `continuar ronda cierra el incidente`() = runTest {
        val rig = rigWith(SimScenario.PERSON_IN_RESTRICTED_ZONE)
        rig.decide(CommandType.ACKNOWLEDGE_ALERT, rig.personParams(AlertResolution.CONTINUE_ROUND), OperationalState.EN_RONDA)
    }

    @Test
    fun `obstaculo esperar, saltar y regresar cierran la decision`() = runTest {
        rigWith(SimScenario.OBSTACLE).let { rig ->
            rig.decide(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId, decision = IncidentDecision.WAIT_30S), OperationalState.EN_RONDA)
        }
        rigWith(SimScenario.OBSTACLE).let { rig ->
            rig.decide(CommandType.RESUME_MISSION, CommandParams(missionId = rig.missionId, decision = IncidentDecision.SKIP_CHECKPOINT), OperationalState.EN_RONDA)
        }
        rigWith(SimScenario.OBSTACLE).let { rig ->
            rig.decide(CommandType.RETURN_TO_SAFE_POINT, CommandParams(safePointId = SafePointId.BASE), OperationalState.REGRESANDO)
        }
    }
}
