package ar.edu.uade.ronda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mission: MissionRecord)

    @Query("SELECT * FROM missions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<MissionRecord>>

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun byId(id: String): MissionRecord?

    @Query("DELETE FROM missions WHERE startedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventRecord)

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EventRecord>>

    @Query("SELECT * FROM events WHERE kind = :kind ORDER BY timestamp DESC")
    fun observeByKind(kind: String): Flow<List<EventRecord>>

    @Query("SELECT * FROM events WHERE missionId = :missionId ORDER BY timestamp ASC")
    fun observeByMission(missionId: String): Flow<List<EventRecord>>

    @Query("SELECT * FROM events WHERE sourceId = :id")
    suspend fun byId(id: String): EventRecord?

    @Query("DELETE FROM events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
