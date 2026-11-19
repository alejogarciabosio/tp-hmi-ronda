package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationControl
import ar.edu.uade.ronda.core.control.SimulationSettings
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.RejectReason
import ar.edu.uade.ronda.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
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

    private val heldEvents = ArrayDeque<String>()

    init {
        scope.launch {
            while (isActive) {
                delay(tickMs)
                val outputs = synchronized(lock) { simulator.tick(clock.nowMs()) }
                deliver(outputs)
            }
        }
    }

    override suspend fun send(text: String) {
        val outputs = synchronized(lock) {
            if (!_settings.value.linkUp) return
            simulator.onMessage(clock.nowMs(), text)
        }
        val acks = outputs.filter { it.kind == OutputKind.ACK }
        val delayMs = _settings.value.responseDelayMs
        if (delayMs > 0) {
            scope.launch {
                delay(delayMs)
                deliver(acks)
            }
        } else {
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

    override fun loadScenario(scenario: SimScenario) {
        reset()
        synchronized(lock) {
            when (scenario) {
                // Con 45 % y consumo doble cruza el 25 % a mitad de ronda sin quedar bloqueada al iniciar.
                SimScenario.LOW_BATTERY -> {
                    simulator.setBatteryPercent(45)
                    simulator.drainFactor = 2.0
                }
                SimScenario.OBSTACLE -> simulator.planIncident("PC4", IncidentType.OBSTACLE)
                SimScenario.PERSON_IN_RESTRICTED_ZONE -> simulator.planIncident("PC5", IncidentType.PERSON_IN_RESTRICTED_ZONE)
                SimScenario.NORMAL, SimScenario.DISCONNECTION -> {}
            }
        }
        _settings.update { it.copy(scenario = scenario, drainFactor = simulator.drainFactor) }
    }

    override fun setLinkUp(up: Boolean) {
        val flushed = synchronized(lock) {
            if (_settings.value.linkUp == up) return
            _settings.update { it.copy(linkUp = up) }
            simulator.setLinkUp(clock.nowMs(), up)
            if (up) heldEvents.toList().also { heldEvents.clear() } else emptyList()
        }
        flushed.forEach { _incoming.tryEmit(it) }
    }

    override fun cutLinkFor(durationMs: Long) {
        setLinkUp(false)
        scope.launch {
            delay(durationMs)
            setLinkUp(true)
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

    override fun injectObstacle() = deliver(synchronized(lock) { simulator.injectObstacle(clock.nowMs()) })
    override fun injectPersonInRestrictedZone() = deliver(synchronized(lock) { simulator.injectPerson(clock.nowMs()) })
    override fun injectFault() = deliver(synchronized(lock) { simulator.injectFault(clock.nowMs()) })

    override fun setBatteryPercent(percent: Int) = synchronized(lock) { simulator.setBatteryPercent(percent) }
    override fun setTechRoomTempC(celsius: Double) = synchronized(lock) { simulator.setTechRoomTempC(celsius) }
    override fun setExitObstructed(exitId: String, obstructed: Boolean) =
        synchronized(lock) { simulator.setExitObstructed(exitId, obstructed) }

    override fun reset() {
        synchronized(lock) {
            simulator.reset()
            heldEvents.clear()
        }
        _settings.value = SimulationSettings()
    }
}
