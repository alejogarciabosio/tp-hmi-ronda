package ar.edu.uade.ronda.ui.telemetria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.repository.RobotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class TelemetriaViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    tickMs: Long = 500,
) : ViewModel() {

    /** Mantiene vivas las antigüedades aunque deje de llegar telemetría. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    val uiState: StateFlow<TelemetriaUiState> =
        combine(repository.telemetry, repository.connection, repository.lastTelemetryAt, ticker) { t, c, at, _ ->
            TelemetriaMapper.map(t, c, at, clock.nowMs())
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            TelemetriaMapper.map(
                repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value, clock.nowMs(),
            ),
        )
}
