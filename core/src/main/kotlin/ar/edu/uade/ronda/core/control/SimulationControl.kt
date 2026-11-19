package ar.edu.uade.ronda.core.control

import ar.edu.uade.ronda.core.model.RejectReason
import kotlinx.coroutines.flow.StateFlow

enum class SimScenario(val displayName: String) {
    NORMAL("Ronda normal"),
    LOW_BATTERY("Batería baja"),
    DISCONNECTION("Desconexión"),
    OBSTACLE("Obstáculo en el pasillo"),
    PERSON_IN_RESTRICTED_ZONE("Persona en zona restringida"),
}

/** Lo que el panel de simulación muestra de la configuración vigente. */
data class SimulationSettings(
    val scenario: SimScenario = SimScenario.NORMAL,
    val linkUp: Boolean = true,
    val responseDelayMs: Long = 0,
    val latencyOverrideMs: Int? = null,
    val drainFactor: Double = 1.0,
)

/**
 * Controles del panel de simulación. Es una interfaz aparte de `RobotRepository`:
 * la UI principal no la ve y un gateway real no la implementa.
 */
interface SimulationControl {
    val settings: StateFlow<SimulationSettings>

    /** Reinicia el robot y deja armado el escenario. */
    fun loadScenario(scenario: SimScenario)
    fun setLinkUp(up: Boolean)

    /** Corta el enlace y lo restaura solo pasado `durationMs`. */
    fun cutLinkFor(durationMs: Long)

    /** La próxima orden que llegue al robot se rechaza con este motivo. */
    fun rejectNextCommand(reason: RejectReason = RejectReason.INVALID_STATE, text: String? = null)
    fun setResponseDelayMs(delayMs: Long)
    fun setLatencyMs(latencyMs: Int?)
    fun injectObstacle()
    fun injectPersonInRestrictedZone()
    fun injectFault()
    fun setBatteryPercent(percent: Int)
    fun setTechRoomTempC(celsius: Double)
    fun setExitObstructed(exitId: String, obstructed: Boolean)
    fun reset()
}
