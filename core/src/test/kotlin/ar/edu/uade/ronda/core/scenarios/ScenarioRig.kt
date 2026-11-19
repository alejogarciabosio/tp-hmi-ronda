package ar.edu.uade.ronda.core.scenarios

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

/** Arma el repositorio simulado sobre el tiempo virtual de `runTest` y ofrece avance cómodo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioRig(private val scope: TestScope, seed: Long = 42) {
    val clock = Clock { START_MS + scope.currentTime }
    val repo = SimulatedRobotRepository.create(scope.backgroundScope, clock, seed)
    val control = repo.control
    val events = mutableListOf<RobotEvent>()
    val connectionsSeen = linkedSetOf<ConnectionState>()

    init {
        scope.backgroundScope.launch { repo.events.collect { events += it } }
        scope.backgroundScope.launch { repo.connection.collect { connectionsSeen += it } }
        scope.runCurrent()
    }

    val telemetry: RobotTelemetry get() = repo.telemetry.value!!
    val state: OperationalState get() = telemetry.operationalState

    suspend fun advance(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    /** Avanza de a 250 ms hasta que se cumpla la condición; falla con contexto si no pasa en `maxMs`. */
    suspend fun until(maxMs: Long = 900_000, what: String = "condición", cond: () -> Boolean) {
        var waited = 0L
        while (!cond()) {
            check(waited < maxMs) { "No se cumplió '$what' en $maxMs ms; estado=${repo.telemetry.value?.operationalState}" }
            advance(250); waited += 250
        }
    }

    suspend fun untilState(target: OperationalState, maxMs: Long = 900_000) =
        until(maxMs, "estado $target") { repo.telemetry.value?.operationalState == target }

    fun send(type: CommandType, params: CommandParams = CommandParams()): String = repo.send(type, params).also { scope.runCurrent() }

    fun statusOf(id: String): CommandStatus = repo.commands.value.single { it.id == id }.status

    val missionId: String get() = telemetry.mission!!.id

    companion object {
        const val START_MS = 1_763_500_000_000L
    }
}
