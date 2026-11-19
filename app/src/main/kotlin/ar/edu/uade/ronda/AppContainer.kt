package ar.edu.uade.ronda

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.SystemClock
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import ar.edu.uade.ronda.data.local.EventRecorder
import ar.edu.uade.ronda.data.local.HistoryRepository
import ar.edu.uade.ronda.data.local.RondaDatabase
import ar.edu.uade.ronda.data.local.RoomHistoryRepository
import ar.edu.uade.ronda.data.local.toOutcomes
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Inyección manual (sin Hilt, ver docs/decisiones.md). Es el único lugar de `app` que conoce
 * la implementación simulada del robot: el resto recibe [RobotRepository] y [SimulationControl].
 */
class AppContainer(
    context: Context,
    private val clock: Clock = SystemClock,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val simulated = SimulatedRobotRepository.create(appScope, clock)

    val robotRepository: RobotRepository = simulated

    /** Controles del panel de simulación (F7). No forman parte de `RobotRepository`. */
    val simulationControl: SimulationControl = simulated.control

    private val database = RondaDatabase.create(context)

    val historyRepository: HistoryRepository =
        RoomHistoryRepository(database.missionDao(), database.eventDao())

    val eventRecorder = EventRecorder(historyRepository, appScope)

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SupervisionViewModel(robotRepository, clock) }
        initializer { SeleccionViewModel(robotRepository) }
        initializer { TelemetriaViewModel(robotRepository, clock) }
    }

    init {
        eventRecorder.start(
            telemetry = robotRepository.telemetry.filterNotNull(),
            events = robotRepository.events,
            commands = robotRepository.commands.toOutcomes(),
        )
        appScope.launch { historyRepository.purgeOlderThan(clock.nowMs() - RETENTION_MS) }
    }

    private companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
