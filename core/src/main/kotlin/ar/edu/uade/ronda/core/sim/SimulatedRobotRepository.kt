package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.core.repository.TransportRobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random

/**
 * Repositorio con el robot simulado detrás. Es el único punto donde la app toca el paquete `sim`
 * (desde `AppContainer`); los ViewModels solo ven [RobotRepository] y [SimulationControl].
 */
class SimulatedRobotRepository private constructor(
    repository: RobotRepository,
    val control: SimulationControl,
) : RobotRepository by repository {

    companion object {
        fun create(scope: CoroutineScope, clock: Clock, seed: Long = 42L): SimulatedRobotRepository {
            val transport = FakeTransport(RobotSimulator(Random(seed)), clock, scope)
            return SimulatedRobotRepository(TransportRobotRepository(transport, clock, scope, idRandom = Random(seed + 1)), transport)
        }
    }
}
