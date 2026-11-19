package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.control.SimulationSettings
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Conecta al [RobotSimulator] con la app por texto JSON y a la vez expone los controles del panel.
 * Con el enlace caído se pierden la telemetría y las respuestas, y los eventos quedan retenidos
 * con su timestamp original hasta que el enlace vuelve (plan, escenario 3).
 */
class FakeTransport(
    private val simulator: RobotSimulator,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val tickMs: Long = 500,
) : Transport, SimulationControl {

    private val lock = Any()
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    override val incoming: Flow<String> = _incoming

    private val _settings = MutableStateFlow(SimulationSettings())
    override val settings: StateFlow<SimulationSettings> = _settings.asStateFlow()

    private companion object {
        const val ACK_LATENCY_MS = 200L
    }

    private val heldEvents = ArrayDeque<String>()

    /** Temporizador de "cortar enlace N s": se cancela si el panel toca el enlace, reinicia o carga un escenario. */
    @Volatile
    private var linkRestoreJob: Job? = null

    init {
        scope.launch {
            while (isActive) {
                delay(tickMs)
                val outputs = synchronized(lock) { simulator.tick(clock.nowMs()) }
                deliver(outputs)
                refreshInjectionAvailability()
            }
        }
    }

    override suspend fun send(text: String) {
        val outputs = synchronized(lock) {
            if (!_settings.value.linkUp) return
            simulator.onMessage(clock.nowMs(), text)
        }
        val acks = outputs.filter { it.kind == OutputKind.ACK }
        // La respuesta tarda un viaje de ida y vuelta, más la demora que pida el panel.
        scope.launch {
            delay(ACK_LATENCY_MS + _settings.value.responseDelayMs)
            deliver(acks)
        }
        deliver(outputs.filter { it.kind != OutputKind.ACK })
    }

    private fun deliver(outputs: List<SimOutput>) {
        for (o in outputs) {
            if (_settings.value.linkUp) {
                _incoming.tryEmit(o.json)
            } else if (o.kind == OutputKind.EVENT) {
                synchronized(lock) { heldEvents.addLast(o.json) }
            }
        }
    }

    // ---- SimulationControl ----

    private fun cancelLinkTimer() {
        linkRestoreJob?.cancel()
        linkRestoreJob = null
    }

    private fun injectionBlockedReason(): String? =
        if (!_settings.value.linkUp) "Sin enlace con el robot: no se puede inyectar un obstáculo ni una persona."
        else synchronized(lock) { simulator.incidentInjectionBlockedReason() }

    private fun refreshInjectionAvailability() {
        val reason = injectionBlockedReason()
        if (_settings.value.incidentInjectionBlockedReason != reason) {
            _settings.update { it.copy(incidentInjectionBlockedReason = reason) }
        }
    }

    override fun loadScenario(scenario: SimScenario) {
        reset()
        synchronized(lock) {
            when (scenario) {
                // Sale con el mínimo operativo (40 %) y una celda degradada que gasta 6 veces lo normal:
                // la ronda (~7 % nominal) cruza el 25 % a mitad de camino y el 15 % cerca del final. Ver D8.
                SimScenario.LOW_BATTERY -> {
                    simulator.setBatteryPercent(40)
                    simulator.drainFactor = 6.0
                }
                SimScenario.OBSTACLE -> simulator.planIncident("PC4", IncidentType.OBSTACLE)
                SimScenario.PERSON_IN_RESTRICTED_ZONE -> simulator.planIncident("PC5", IncidentType.PERSON_IN_RESTRICTED_ZONE)
                SimScenario.NORMAL, SimScenario.DISCONNECTION -> {}
            }
        }
        _settings.update { it.copy(scenario = scenario, drainFactor = simulator.drainFactor) }
    }

    override fun setLinkUp(up: Boolean) {
        cancelLinkTimer()
        applyLink(up)
    }

    private fun applyLink(up: Boolean) {
        val flushed = synchronized(lock) {
            if (_settings.value.linkUp == up) return
            _settings.update { it.copy(linkUp = up) }
            // Con el enlace caído, la alerta de enlace perdido queda retenida con su hora original.
            val outputs = simulator.setLinkUp(clock.nowMs(), up)
            outputs.filter { it.kind == OutputKind.EVENT }.forEach { heldEvents.addLast(it.json) }
            if (up) heldEvents.toList().also { heldEvents.clear() } else emptyList()
        }
        flushed.forEach { _incoming.tryEmit(it) }
        refreshInjectionAvailability()
    }

    override fun cutLinkFor(durationMs: Long) {
        cancelLinkTimer()
        applyLink(false)
        linkRestoreJob = scope.launch {
            delay(durationMs)
            linkRestoreJob = null
            applyLink(true)
        }
    }

    override fun rejectNextCommand(reason: RejectReason, text: String?) {
        synchronized(lock) { simulator.rejectNextCommand(reason, text) }
    }

    override fun setResponseDelayMs(delayMs: Long) = _settings.update { it.copy(responseDelayMs = delayMs.coerceAtLeast(0)) }

    override fun setLatencyMs(latencyMs: Int?) {
        synchronized(lock) { simulator.latencyOverrideMs = latencyMs }
        _settings.update { it.copy(latencyOverrideMs = latencyMs) }
    }

    override fun setTimeScale(scale: Double) {
        require(scale in SimulationSettings.ALLOWED_TIME_SCALES) {
            "Escala de tiempo no permitida: $scale (válidas: ${SimulationSettings.ALLOWED_TIME_SCALES})"
        }
        synchronized(lock) { simulator.setTimeScale(clock.nowMs(), scale) }
        _settings.update { it.copy(timeScale = scale) }
    }

    override fun injectObstacle() = injectIncident { simulator.injectObstacle(it) }
    override fun injectPersonInRestrictedZone() = injectIncident { simulator.injectPerson(it) }

    /** Rechaza la inyección de forma explícita (motivo en `settings`) si no hay enlace o el robot no está en ronda. */
    private fun injectIncident(inject: (Long) -> List<SimOutput>) {
        val reason = injectionBlockedReason()
        if (reason != null) {
            _settings.update { it.copy(lastInjectionRejection = reason, incidentInjectionBlockedReason = reason) }
            return
        }
        _settings.update { it.copy(lastInjectionRejection = null) }
        deliver(synchronized(lock) { inject(clock.nowMs()) })
        refreshInjectionAvailability()
    }
    override fun injectFault() = deliver(synchronized(lock) { simulator.injectFault(clock.nowMs()) })

    override fun setBatteryPercent(percent: Int) = synchronized(lock) { simulator.setBatteryPercent(percent) }
    override fun setTechRoomTempC(celsius: Double) = synchronized(lock) { simulator.setTechRoomTempC(celsius) }
    override fun setExitObstructed(exitId: String, obstructed: Boolean) =
        synchronized(lock) { simulator.setExitObstructed(exitId, obstructed) }

    override fun reset() {
        cancelLinkTimer()
        synchronized(lock) {
            simulator.reset()
            heldEvents.clear()
        }
        // Reiniciar no cambia la velocidad de la demo.
        _settings.value = SimulationSettings(timeScale = _settings.value.timeScale)
        refreshInjectionAvailability()
    }
}
