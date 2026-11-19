package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.core.model.*
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.core.repository.RobotRepository
import ar.edu.uade.ronda.core.repository.TrackedCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Repositorio falso: los tests mueven los flujos a mano y miran qué comandos se enviaron. */
internal class FakeRobotRepository : RobotRepository {
    override val telemetry = MutableStateFlow<RobotTelemetry?>(null)
    override val connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val lastTelemetryAt = MutableStateFlow<Long?>(null)
    override val events: SharedFlow<RobotEvent> = MutableSharedFlow()
    override val activeIncident = MutableStateFlow<Incident?>(null)
    override val commands = MutableStateFlow<List<TrackedCommand>>(emptyList())
    override val availability = MutableStateFlow<Map<CommandType, CommandAvailability>>(emptyMap())

    val sent = mutableListOf<Pair<CommandType, CommandParams>>()

    override fun send(type: CommandType, params: CommandParams): String {
        sent += type to params
        return "msg-${sent.size}"
    }

    fun publish(t: RobotTelemetry, connection: ConnectionState = ConnectionState.CONNECTED) {
        telemetry.value = t
        lastTelemetryAt.value = t.timestamp
        this.connection.value = connection
    }
}

internal fun enabledAll(vararg except: Pair<CommandType, String>): Map<CommandType, CommandAvailability> =
    CommandType.entries.associateWith { type ->
        except.firstOrNull { it.first == type }?.let { CommandAvailability.disabled(it.second) } ?: CommandAvailability.Enabled
    }

internal fun tele(
    ts: Long,
    state: OperationalState = OperationalState.EN_RONDA,
    index: Int = 3,
    verified: Int = 2,
    issues: Int = 0,
    batteryPct: Int = 62,
    pose: Pose = Pose(0, 25.0, 10.0, 0.0, "Hall PB"),
    alerts: List<Alert> = emptyList(),
    missionId: String? = "m1",
    exits: List<EmergencyExit> = listOf(
        EmergencyExit("SE-PB", "Salida PB", ExitStatus.LIBRE),
        EmergencyExit("SE-1", "Salida 1°", ExitStatus.LIBRE),
    ),
) = RobotTelemetry(
    messageId = "t$ts", robotId = "RONDA-01", timestamp = ts, latencyMs = 180,
    battery = Battery(batteryPct, 48, false), operationalState = state,
    mission = missionId?.let { MissionProgress(it, "Ronda completa", index, 8, "En camino") },
    pose = pose, motion = Motion(0.5, MotionMode.CAMINANDO), alerts = alerts,
    service = ServiceTelemetry(verified, issues, 24.5, TempTrend.STABLE, exits),
)

internal fun incident(type: IncidentType = IncidentType.OBSTACLE) = Incident(
    "i1", type, 4, "Un objeto bloquea el pasillo.",
    listOf(
        IncidentOption("WAIT_30S", "Esperar 30 s"),
        IncidentOption("SKIP_CHECKPOINT", "Saltar el PC4"),
        IncidentOption("RETURN_TO_SAFE_POINT", "Regresar a la base", SafePointId.BASE),
    ),
)

internal fun tracked(id: String, type: CommandType, status: CommandStatus, sentAt: Long, updatedAt: Long = sentAt) =
    TrackedCommand(Command(id, "RONDA-01", sentAt, "garita", type), status, sentAt, updatedAt)
