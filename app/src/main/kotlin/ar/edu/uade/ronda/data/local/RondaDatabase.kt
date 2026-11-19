package ar.edu.uade.ronda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MissionRecord::class, EventRecord::class], version = 2, exportSchema = false)
abstract class RondaDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun eventDao(): EventDao

    companion object {
        fun create(context: Context): RondaDatabase =
            Room.databaseBuilder(context.applicationContext, RondaDatabase::class.java, "ronda.db")
                // v2 suma la decisión del operador a las órdenes. Es un TP sin datos reales que
                // preservar (y la retención es de 30 días): se recrea la base en vez de migrar.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
