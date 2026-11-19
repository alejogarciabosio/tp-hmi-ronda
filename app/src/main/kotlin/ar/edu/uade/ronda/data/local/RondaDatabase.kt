package ar.edu.uade.ronda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MissionRecord::class, EventRecord::class], version = 1, exportSchema = false)
abstract class RondaDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun eventDao(): EventDao

    companion object {
        fun create(context: Context): RondaDatabase =
            Room.databaseBuilder(context.applicationContext, RondaDatabase::class.java, "ronda.db").build()
    }
}
