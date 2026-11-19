package ar.edu.uade.ronda.ui.desconexion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.repository.RobotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

class DesconexionViewModel(
    private val repository: RobotRepository,
    private val clock: Clock,
    tickMs: Long = 1_000,
) : ViewModel() {

    private val received = MutableStateFlow<List<ReceivedEvent>>(emptyList())

    init {
        // Los eventos ocurridos sin enlace llegan juntos al volver: se guarda cuándo llegó cada uno.
        viewModelScope.launch {
            repository.events.collect { e -> received.value = (received.value + ReceivedEvent(e, clock.nowMs())).takeLast(MAX_EVENTS) }
        }
    }

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(tickMs)
        }
    }

    private val base = combine(
        repository.telemetry, repository.connection, repository.lastTelemetryAt, repository.commands, repository.availability,
    ) { _, _, _, _, _ -> Unit }

    val uiState: StateFlow<DesconexionUiState> = combine(base, received, ticker) { _, _, _ -> current() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun current() = DesconexionMapper.map(
        DesconexionInputs(
            repository.telemetry.value, repository.connection.value, repository.lastTelemetryAt.value,
            repository.commands.value, repository.availability.value, received.value, clock.nowMs(),
        ),
    )

    fun onOperationalStop() {
        if (current().stop.enabled) repository.send(CommandType.OPERATIONAL_STOP)
    }

    private companion object {
        const val MAX_EVENTS = 20
    }
}
