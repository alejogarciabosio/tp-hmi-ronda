package ar.edu.uade.ronda.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Misión registrada; `result` es el nombre de [MissionResult]. */
@Entity(tableName = "missions", indices = [Index("startedAt")])
data class MissionRecord(
    @PrimaryKey val id: String,
    val robotId: String,
    val routeName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val result: String,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
    val anomalies: Int,
)

enum class MissionResult { EN_CURSO, COMPLETADA, CANCELADA, FALLA }

enum class EventKind { ALERTA, INCIDENTE, COMANDO }

/**
 * Fila del registro. `sourceId` es el messageId del evento o del comando: un comando
 * se actualiza en el lugar cuando cambia su estado, así que hay una fila por orden.
 * `commandOption` es la decisión del operador en crudo (`SEND_GUARD`, `SKIP_CHECKPOINT`...)
 * y `checkpointIndex` el punto de la ruta donde la tomó; la UI los traduce al mostrarlos.
 * Privacidad: no se guardan imágenes ni datos de personas, solo el texto del evento.
 */
@Entity(
    tableName = "events",
    indices = [Index("timestamp"), Index("missionId")],
)
data class EventRecord(
    @PrimaryKey val sourceId: String,
    val timestamp: Long,
    val kind: String,
    val type: String,
    val severity: String,
    val message: String,
    val missionId: String? = null,
    val commandType: String? = null,
    val commandStatus: String? = null,
    val commandOption: String? = null,
    val checkpointIndex: Int? = null,
)
