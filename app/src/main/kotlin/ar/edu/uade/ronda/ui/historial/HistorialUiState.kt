package ar.edu.uade.ronda.ui.historial

import ar.edu.uade.ronda.ui.model.StatusBadgeUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.StateStripUi

enum class HistoryView(val label: String) { TODOS("Todo"), MISIONES("Misiones"), EVENTOS("Eventos") }

/** Filtro de severidad; se cicla con un toque. */
enum class SeverityFilter(val label: String) { TODAS("Severidad: todas"), ADVERTENCIA("Severidad: advertencia o más"), CRITICA("Severidad: crítica") }

enum class PeriodFilter(val label: String, val days: Int) { HOY("Hoy", 1), SIETE("Últimos 7 días", 7), TREINTA("Últimos 30 días", 30) }

data class MissionRowUi(
    val id: String,
    val route: String,
    val badge: StatusBadgeUi,
    /** "19/11 · 23:38–00:14" */
    val period: String,
    /** "8/8 PC · 1 con observación" */
    val summary: String,
)

data class EventRowUi(
    val id: String,
    val time: String,
    val level: StatusLevel,
    val text: String,
    val code: String,
)

data class EventGroupUi(val title: String, val events: List<EventRowUi>)

data class HistorialUiState(
    val strip: StateStripUi,
    val view: HistoryView = HistoryView.TODOS,
    val severity: SeverityFilter = SeverityFilter.TODAS,
    val period: PeriodFilter = PeriodFilter.SIETE,
    val missions: List<MissionRowUi> = emptyList(),
    val eventGroups: List<EventGroupUi> = emptyList(),
    val retentionNote: String = "Se guardan 30 días en el teléfono. No se guardan imágenes.",
)
