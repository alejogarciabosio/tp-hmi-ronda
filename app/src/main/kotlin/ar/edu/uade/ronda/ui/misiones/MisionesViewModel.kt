package ar.edu.uade.ronda.ui.misiones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.CommandUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class MisionesViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    tickMs: Long = 500,
) : ViewModel() {

    private val selectedRoute = MutableStateFlow(RouteId.RONDA_COMPLETA)

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    val uiState: StateFlow<MisionesUiState> = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt, repository.commands,
        combine(selectedRoute, ticker) { r, _ -> r },
    ) { t, c, at, cmds, route ->
        MisionesMapper.map(MisionesInputs(t, c, at, cmds, route, clock.nowMs()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun current() = MisionesMapper.map(
        MisionesInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.commands.value, selectedRoute.value, clock.nowMs(),
        ),
    )

    fun onSelectRoute(id: String) {
        RouteId.entries.firstOrNull { it.name == id }?.let { selectedRoute.value = it }
    }

    fun onStart() {
        // Se recalcula en el momento: el estado observado puede tener medio segundo.
        if (current().start.state != CommandUiState.ENABLED) return
        repository.send(CommandType.START_MISSION, CommandParams(routeId = selectedRoute.value))
    }
}
