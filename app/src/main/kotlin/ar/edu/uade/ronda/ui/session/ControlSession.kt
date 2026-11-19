package ar.edu.uade.ronda.ui.session

import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.policy.CommandAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Qué robot controla este operador. Es estado de la sesión de la app, compartido por Selección,
 * Supervisión y Control: un solo operador da órdenes a cada robot.
 */
class ControlSession(initial: String? = null) {
    private val _controlledId = MutableStateFlow(initial)
    val controlledId: StateFlow<String?> = _controlledId

    fun take(robotId: String) { _controlledId.value = robotId }

    fun release() { _controlledId.value = null }

    companion object {
        const val NO_CONTROL_REASON = "Tomá el control del robot en Selección"

        /** Sin control propio ninguna orden sale, sea cual sea lo que diga la política del robot. */
        fun gate(
            availability: Map<CommandType, CommandAvailability>,
            controlledId: String?,
        ): Map<CommandType, CommandAvailability> =
            if (controlledId != null) availability
            else CommandType.entries.associateWith { CommandAvailability.disabled(NO_CONTROL_REASON) }
    }
}
