package ar.edu.uade.ronda.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDaoTest {
    private lateinit var db: RondaDatabase
    private lateinit var repo: HistoryRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RondaDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomHistoryRepository(db.missionDao(), db.eventDao())
    }

    @After fun tearDown() = db.close()

    private fun mission(id: String, start: Long) =
        MissionRecord(id, "RONDA-01", "Ronda completa", start, null, "EN_CURSO", 0, 8, 0)

    private fun event(id: String, ts: Long, kind: EventKind, mission: String? = null) =
        EventRecord(id, ts, kind.name, "X", "INFO", "msg", mission)

    @Test fun misionesVienenDeLaMasRecienteYSeActualizan() = runTest {
        repo.saveMission(mission("a", 10))
        repo.saveMission(mission("b", 20))
        repo.saveMission(mission("a", 10).copy(result = "COMPLETADA", endedAt = 15))
        val all = repo.observeMissions().first()
        assertEquals(listOf("b", "a"), all.map { it.id })
        assertEquals("COMPLETADA", all.last().result)
    }

    @Test fun eventosFiltranPorTipoYMision() = runTest {
        repo.saveEvent(event("1", 1, EventKind.ALERTA, "m"))
        repo.saveEvent(event("2", 2, EventKind.COMANDO, "m"))
        repo.saveEvent(event("3", 3, EventKind.COMANDO))
        assertEquals(listOf("3", "2"), repo.observeEvents(EventKind.COMANDO).first().map { it.sourceId })
        assertEquals(listOf("1", "2"), repo.observeMissionEvents("m").first().map { it.sourceId })
        assertEquals(3, repo.observeEvents().first().size)
    }

    @Test fun purgaBorraLoAnterior() = runTest {
        repo.saveMission(mission("old", 1))
        repo.saveMission(mission("new", 100))
        repo.saveEvent(event("1", 1, EventKind.ALERTA))
        assertEquals(2, repo.purgeOlderThan(50))
        assertEquals(listOf("new"), repo.observeMissions().first().map { it.id })
    }
}
