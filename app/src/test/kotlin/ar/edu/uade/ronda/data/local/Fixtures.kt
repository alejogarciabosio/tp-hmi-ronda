package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.*

internal fun telemetry(
    ts: Long,
    state: OperationalState,
    mission: MissionProgress? = null,
    verified: Int = 0,
    issues: Int = 0,
) = RobotTelemetry(
    messageId = "t$ts", robotId = "RONDA-01", timestamp = ts, latencyMs = 40,
    battery = Battery(80, 120, false), operationalState = state, mission = mission,
    pose = Pose(0, 0.0, 0.0, 0.0, "PB"), motion = Motion(0.0, MotionMode.DETENIDO),
    alerts = emptyList(),
    service = ServiceTelemetry(verified, issues, 22.0, TempTrend.STABLE, emptyList()),
)

internal fun progress(id: String = "m1", total: Int = 8) =
    MissionProgress(id, "Ronda completa", 0, total, "En camino")

internal fun command(id: String, type: CommandType = CommandType.START_MISSION) =
    Command(id, "RONDA-01", 1000, "garita", type)

internal fun alertEvent(id: String = "e1", ts: Long = 2000) = RobotEvent.AlertRaised(
    id, "RONDA-01", ts, Severity.WARNING,
    Alert("a1", AlertType.LOW_BATTERY, Severity.WARNING, "Batería baja", ts, false),
)

internal class FakeHistory : HistoryRepository {
    val missions = mutableMapOf<String, MissionRecord>()
    val events = mutableMapOf<String, EventRecord>()
    override fun observeMissions() = throw NotImplementedError()
    override fun observeEvents(kind: EventKind?) = throw NotImplementedError()
    override fun observeMissionEvents(missionId: String) = throw NotImplementedError()
    override suspend fun saveMission(mission: MissionRecord) { missions[mission.id] = mission }
    override suspend fun saveEvent(event: EventRecord) { events[event.sourceId] = event }
    override suspend fun purgeOlderThan(before: Long) = 0
}
