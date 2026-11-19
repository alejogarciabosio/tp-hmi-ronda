package ar.edu.uade.ronda.ui.historial

import ar.edu.uade.ronda.ui.model.Etiquetas
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.data.local.EventKind
import ar.edu.uade.ronda.data.local.EventRecord
import ar.edu.uade.ronda.data.local.MissionRecord
import ar.edu.uade.ronda.data.local.MissionResult
import ar.edu.uade.ronda.ui.model.StatusBadgeUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.supervision.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HistorialInputs(
    val telemetry: RobotTelemetry?,
    val connection: ConnectionState,
    val lastTelemetryAt: Long?,
    val missions: List<MissionRecord>,
    val events: List<EventRecord>,
    val view: HistoryView,
    val severity: SeverityFilter,
    val period: PeriodFilter,
    val nowMs: Long,
)

internal object HistorialMapper {
    private val zone = ZoneId.systemDefault()
    private val day = DateTimeFormatter.ofPattern("dd/MM").withZone(zone)
    private val hour = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    fun map(i: HistorialInputs): HistorialUiState {
        val strip = SupervisionMapper.map(
            SupervisionInputs(
                i.telemetry, i.connection, i.lastTelemetryAt, null, emptyList(),
                emptyMap<CommandType, CommandAvailability>(), CheckpointLog(), null, null, i.nowMs,
            ),
        ).strip
        val from = i.nowMs - i.period.days * DAY_MS
        val missions = if (i.view == HistoryView.EVENTOS) emptyList() else
            i.missions.filter { it.startedAt >= from }.sortedByDescending { it.startedAt }.map { missionRow(it) }
        val events = if (i.view == HistoryView.MISIONES) emptyList() else
            i.events.filter { it.timestamp >= from && passes(it, i.severity) }.sortedByDescending { it.timestamp }
        val groups = events.groupBy { day.format(Instant.ofEpochMilli(it.timestamp)) }
            .map { (d, list) -> EventGroupUi("EVENTOS · $d", list.map { eventRow(it) }) }
        return HistorialUiState(strip, i.view, i.severity, i.period, missions, groups)
    }

    private fun passes(e: EventRecord, f: SeverityFilter) = when (f) {
        SeverityFilter.TODAS -> true
        SeverityFilter.ADVERTENCIA -> e.severity == "WARNING" || e.severity == "CRITICAL"
        SeverityFilter.CRITICA -> e.severity == "CRITICAL"
    }

    private fun missionRow(m: MissionRecord): MissionRowUi {
        val result = MissionResult.entries.firstOrNull { it.name == m.result }
        val badge = when (result) {
            MissionResult.COMPLETADA -> StatusBadgeUi(StatusLevel.OK, "Completada")
            MissionResult.EN_CURSO -> StatusBadgeUi(StatusLevel.OK, "En curso")
            MissionResult.CANCELADA -> StatusBadgeUi(StatusLevel.WARNING, "Interrumpida")
            MissionResult.FALLA -> StatusBadgeUi(StatusLevel.ERROR, "Falla")
            null -> StatusBadgeUi(StatusLevel.DISABLED, m.result)
        }
        val start = Instant.ofEpochMilli(m.startedAt)
        val period = "${day.format(start)} · ${hour.format(start)}–" + (m.endedAt?.let { hour.format(Instant.ofEpochMilli(it)) } ?: "en curso")
        val anomalies = when (m.anomalies) {
            0 -> "sin observaciones"
            1 -> "1 con observación"
            else -> "${m.anomalies} con observación"
        }
        return MissionRowUi(m.id, m.routeName, badge, period, "${m.checkpointsVerified}/${m.checkpointsTotal} PC · $anomalies")
    }

    private fun eventRow(e: EventRecord): EventRowUi {
        val level = when (e.severity) {
            "CRITICAL" -> StatusLevel.ERROR
            "WARNING" -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val text = when (e.kind) {
            EventKind.COMANDO.name -> {
                val name = CommandType.entries.firstOrNull { it.name == e.type }?.let { Etiquetas.comando(it) } ?: "orden"
                val status = when (e.commandStatus) {
                    "PENDING" -> "pendiente"
                    "CONFIRMED" -> "confirmada"
                    "VERIFIED" -> "verificada"
                    "REJECTED" -> "rechazada"
                    "NO_RESPONSE" -> "sin respuesta"
                    else -> e.commandStatus?.lowercase() ?: ""
                }
                "Orden: $name · $status"
            }
            else -> e.message
        }
        return EventRowUi(e.sourceId, hour.format(Instant.ofEpochMilli(e.timestamp)), level, text, Etiquetas.registro(e.kind, e.type))
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
