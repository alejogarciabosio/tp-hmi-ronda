package ar.edu.uade.ronda.ui.desconexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.data.LinkLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Los eventos atrasados y los cortes los sigue `LinkMonitor` desde que arranca la app: si los
 * juntara este ViewModel, solo se verían los que llegaron con la pantalla ya abierta.
 * La detención operativa está en la barra fija, igual que en las demás pantallas operativas.
 */
class DesconexionViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    private val link: StateFlow<LinkLog>,
    tickMs: Long = 1_000,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val base = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt, repository.commands, repository.availability,
    ) { _, _, _, _, _ -> Unit }

    val uiState: StateFlow<DesconexionUiState> = combine(base, link, ticker) { _, _, _ -> current() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun current() = DesconexionMapper.map(
        DesconexionInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.commands.value, repository.availability.value, link.value, clock.nowMs(),
        ),
    )
}
