package ar.edu.uade.ronda

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.SystemClock
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.repository.CheckpointHistory
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.core.sim.SimulatedRobotRepository
import ar.edu.uade.ronda.data.local.EventRecorder
import ar.edu.uade.ronda.data.local.HistoryRepository
import ar.edu.uade.ronda.data.local.RondaDatabase
import ar.edu.uade.ronda.data.local.RoomHistoryRepository
import ar.edu.uade.ronda.data.local.toOutcomes
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.historial.HistorialViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.ui.session.ControlSession
import ar.edu.uade.ronda.ui.simulacion.SimulacionViewModel
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

    /**
     * Historia de puntos de control de la ronda en curso. Vive con la aplicación para que
     * salir de Supervisión o recrear su ViewModel no la borre; se reinicia con cada misión.
     */
    val checkpointHistory = CheckpointHistory(robotRepository.telemetry, appScope)

    /** Control exclusivo del robot: lo comparten Selección, Supervisión y Control. */
    val controlSession = ControlSession()

    val eventRecorder = EventRecorder(historyRepository, appScope)

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SupervisionViewModel(robotRepository, clock, controlSession, checkpointHistory.log) }
        initializer { SeleccionViewModel(robotRepository, controlSession) }
        initializer { TelemetriaViewModel(robotRepository, clock) }
        initializer { MisionesViewModel(robotRepository, clock) }
        initializer { ControlViewModel(robotRepository, clock, controlSession) }
        initializer { AlertasViewModel(robotRepository, clock) }
        initializer { HistorialViewModel(robotRepository, historyRepository, clock) }
        initializer { DesconexionViewModel(robotRepository, clock) }
        initializer { SimulacionViewModel(simulationControl, robotRepository) }
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
