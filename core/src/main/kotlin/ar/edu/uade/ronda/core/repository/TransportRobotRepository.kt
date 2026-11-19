package ar.edu.uade.ronda.core.repository

import ar.edu.uade.ronda.core.contract.nextUuidV4
import kotlin.random.Random
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.core.contract.ContractCodec
import ar.edu.uade.ronda.core.contract.ContractException
import ar.edu.uade.ronda.core.contract.ContractMessage
import ar.edu.uade.ronda.core.model.Command
import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.policy.CommandContext
import ar.edu.uade.ronda.core.policy.CommandPolicy
import ar.edu.uade.ronda.core.policy.ConnectionEvaluator
import ar.edu.uade.ronda.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Repositorio sobre cualquier [Transport]: parsea el contrato, deriva la conexión y sigue los comandos. */
class TransportRobotRepository(
    private val transport: Transport,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val robotId: String = "RONDA-01",
    private val operatorId: String = "garita-pb",
    private val evaluator: ConnectionEvaluator = ConnectionEvaluator(),
    private val policy: CommandPolicy = CommandPolicy(),
    private val housekeepingMs: Long = 250,
    private val idRandom: Random = Random.Default,
) : RobotRepository {

    private val tracker = CommandTracker()

    private val _telemetry = MutableStateFlow<RobotTelemetry?>(null)
    override val telemetry: StateFlow<RobotTelemetry?> = _telemetry.asStateFlow()

    private val _lastTelemetryAt = MutableStateFlow<Long?>(null)
    override val lastTelemetryAt: StateFlow<Long?> = _lastTelemetryAt.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<RobotEvent> = _events.asSharedFlow()

    private val _incident = MutableStateFlow<Incident?>(null)
    override val activeIncident: StateFlow<Incident?> = _incident.asStateFlow()

    override val commands: StateFlow<List<TrackedCommand>> = tracker.commands

    override val availability: StateFlow<Map<CommandType, CommandAvailability>> =
        combine(_telemetry, _connection, tracker.commands) { t, conn, _ ->
            val ctx = CommandContext(
                state = t?.operationalState ?: OperationalState.EN_BASE,
                connection = conn,
                batteryPercent = t?.battery?.percent ?: 0,
                hasPendingCommand = tracker.hasPendingMissionCommand(),
                alerts = t?.alerts.orEmpty(),
            )
            policy.evaluateAll(ctx)
        }.stateIn(scope, SharingStarted.Eagerly, policy.evaluateAll(CommandContext(OperationalState.EN_BASE, ConnectionState.DISCONNECTED, 0, false)))

    init {
        // UNDISPATCHED: la suscripción queda lista antes de que el transporte emita el primer mensaje.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { handle(it) }
        }
        scope.launch {
            while (isActive) {
                delay(housekeepingMs)
                val now = clock.nowMs()
                tracker.expire(now)
                refreshConnection(now)
            }
        }
    }

    override fun send(type: CommandType, params: CommandParams): String {
        val now = clock.nowMs()
        val command = Command(
            messageId = idRandom.nextUuidV4(),
            robotId = robotId,
            issuedAt = now,
            operatorId = operatorId,
            type = type,
            params = params,
        )
        tracker.register(command, now)
        // El estado PENDIENTE ya es visible cuando el mensaje sale.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { transport.send(ContractCodec.encode(command)) }
        return command.messageId
    }

    private fun handle(text: String) {
        val msg = try {
            ContractCodec.decode(text)
        } catch (e: ContractException) {
            return // un mensaje corrupto no puede tirar abajo el repositorio
        }
        val now = clock.nowMs()
        when (msg) {
            is ContractMessage.Telemetry -> {
                _telemetry.value = msg.value
                _lastTelemetryAt.value = now
                if (msg.value.operationalState != OperationalState.REQUIERE_DECISION) _incident.value = null
                tracker.onTelemetry(msg.value, now)
                refreshConnection(now)
            }
            is ContractMessage.Ack -> tracker.onAck(msg.value, now)
            is ContractMessage.Event -> {
                val event = msg.value
                if (event is RobotEvent.DecisionRequired) _incident.value = event.incident
                _events.tryEmit(event)
            }
            is ContractMessage.CommandMsg -> {} // el robot no envía comandos
        }
    }

    private fun refreshConnection(now: Long) {
        _connection.update { evaluator.evaluate(_lastTelemetryAt.value, _telemetry.value?.latencyMs, now) }
    }
}
