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
    /** Escala de tiempo de la simulación: ×1, ×4 u ×8 (ver [ALLOWED_TIME_SCALES]). */
    val timeScale: Double = 1.0,
    /**
     * Por qué ahora no se puede inyectar un obstáculo ni una persona (sin enlace, o el robot no está
     * en ronda), o `null` si se puede. La UI lo usa para deshabilitar esos botones con el motivo.
     */
    val incidentInjectionBlockedReason: String? = null,
    /** Motivo del último rechazo de una inyección de obstáculo o persona; se borra con la próxima aceptada. */
    val lastInjectionRejection: String? = null,
) {
    companion object {
        val ALLOWED_TIME_SCALES: List<Double> = listOf(1.0, 4.0, 8.0)
    }
}

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

    /**
     * Acelera la simulación: el robot se mueve y sus fases avanzan `scale` veces más rápido. Valores
     * permitidos: 1, 4 y 8 ([SimulationSettings.ALLOWED_TIME_SCALES]); otro valor lanza
     * `IllegalArgumentException`. La conexión y el vencimiento de las órdenes siguen con el reloj real.
     * Cargar un escenario o reiniciar no cambia la escala.
     */
    fun setTimeScale(scale: Double)

    /**
     * Inyectan un obstáculo o una persona delante del robot. Solo se aceptan con enlace y con el robot
     * en ronda; si no, no pasa nada en el robot y el motivo queda en
     * [SimulationSettings.lastInjectionRejection].
     */
    fun injectObstacle()
    fun injectPersonInRestrictedZone()
    fun injectFault()
    fun setBatteryPercent(percent: Int)
    fun setTechRoomTempC(celsius: Double)
    fun setExitObstructed(exitId: String, obstructed: Boolean)
    fun reset()
}
