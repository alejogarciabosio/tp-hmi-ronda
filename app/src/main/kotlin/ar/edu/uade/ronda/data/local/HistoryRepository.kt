package ar.edu.uade.ronda.data.local

import kotlinx.coroutines.flow.Flow

/** Historial persistente. La UI y los ViewModels solo conocen esta interfaz. */
interface HistoryRepository {
    fun observeMissions(): Flow<List<MissionRecord>>
    fun observeEvents(kind: EventKind? = null): Flow<List<EventRecord>>
    fun observeMissionEvents(missionId: String): Flow<List<EventRecord>>
    suspend fun saveMission(mission: MissionRecord)
    suspend fun saveEvent(event: EventRecord)

    /** Retención de 30 días (plan §4.2, riesgo 5). Devuelve filas borradas. */
    suspend fun purgeOlderThan(before: Long): Int
}

class RoomHistoryRepository(
    private val missions: MissionDao,
    private val events: EventDao,
) : HistoryRepository {
    override fun observeMissions() = missions.observeAll()

    override fun observeEvents(kind: EventKind?) =
        if (kind == null) events.observeAll() else events.observeByKind(kind.name)

    override fun observeMissionEvents(missionId: String) = events.observeByMission(missionId)

    override suspend fun saveMission(mission: MissionRecord) = missions.upsert(mission)

    override suspend fun saveEvent(event: EventRecord) = events.upsert(event)

    override suspend fun purgeOlderThan(before: Long) =
        missions.deleteOlderThan(before) + events.deleteOlderThan(before)
}
