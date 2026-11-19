package ar.edu.uade.ronda.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.data.local.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Lee el historial de Room; la franja de estado sigue mostrando el robot en vivo. */
class HistorialViewModel(
    private val robot: RobotRepository,
    history: HistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filters = MutableStateFlow(Triple(HistoryView.TODOS, SeverityFilter.TODAS, PeriodFilter.SIETE))

    private val live = combine(robot.telemetry, robot.connection, robot.lastTelemetryAt) { t, c, at -> Triple(t, c, at) }

    val uiState: StateFlow<HistorialUiState> = combine(
        live, history.observeMissions(), history.observeEvents(), filters,
    ) { (t, c, at), missions, events, (view, severity, period) ->
        HistorialMapper.map(HistorialInputs(t, c, at, missions, events, view, severity, period, clock.nowMs()))
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        HistorialMapper.map(
            HistorialInputs(
                robot.telemetry.value, robot.connection.value, robot.lastTelemetryAt.value,
                emptyList(), emptyList(), HistoryView.TODOS, SeverityFilter.TODAS, PeriodFilter.SIETE, clock.nowMs(),
            ),
        ),
    )

    fun onSelectView(view: HistoryView) { filters.value = filters.value.copy(first = view) }

    fun onCycleSeverity() {
        val next = SeverityFilter.entries[(filters.value.second.ordinal + 1) % SeverityFilter.entries.size]
        filters.value = filters.value.copy(second = next)
    }

    fun onCyclePeriod() {
        val next = PeriodFilter.entries[(filters.value.third.ordinal + 1) % PeriodFilter.entries.size]
        filters.value = filters.value.copy(third = next)
    }
}
