package ar.edu.uade.ronda.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.CommandUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class AlertasViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    tickMs: Long = 1_000,
) : ViewModel() {

    private val notice = MutableStateFlow<String?>(null)

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val base = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt,
        repository.activeIncident, repository.commands,
    ) { _, _, _, _, _ -> Unit }

    val uiState: StateFlow<AlertasUiState> = combine(base, repository.availability, notice, ticker) { _, _, n, _ -> current(n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current(null))

    private fun current(n: String?) = AlertasMapper.map(
        AlertasInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.activeIncident.value, repository.commands.value, repository.availability.value, clock.nowMs(),
        ),
        n,
    )

    fun onAcknowledge(alertId: String) {
        val card = current(null).active.firstOrNull { it.id == alertId }
        val button = card?.acknowledge
        if (button == null) {
            notice.value = "La alerta ya no está pendiente de reconocer."
            return
        }
        if (button.state == CommandUiState.DISABLED || button.state == CommandUiState.PENDING) return
        notice.value = null
        repository.send(CommandType.ACKNOWLEDGE_ALERT, CommandParams(alertId = alertId))
    }

    fun onNoticeDismissed() { notice.value = null }
}
