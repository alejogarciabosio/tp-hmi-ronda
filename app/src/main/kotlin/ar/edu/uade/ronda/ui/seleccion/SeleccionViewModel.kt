package ar.edu.uade.ronda.ui.seleccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.session.ControlSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SeleccionViewModel(private val repository: RobotRepository, private val session: ControlSession) : ViewModel() {

    private val controlledId = session.controlledId

    val uiState: StateFlow<SeleccionUiState> =
        combine(repository.telemetry, repository.connection, controlledId) { t, c, id ->
            SeleccionMapper.map(t, c, id)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            SeleccionMapper.map(repository.telemetry.value, repository.connection.value, controlledId.value),
        )

    /** Devuelve true si el operador quedó con el control (la pantalla navega a Supervisión). */
    fun onTakeControl(robotId: String): Boolean {
        val card = uiState.value.robots.firstOrNull { it.id == robotId } ?: return false
        if (card.takeControl.state != CommandUiState.ENABLED) return false
        session.take(robotId)
        return true
    }
}
