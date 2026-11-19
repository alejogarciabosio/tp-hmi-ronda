package ar.edu.uade.ronda.ui.misiones

import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.supervision.LastCommandUi

data class RouteCardUi(
    val id: String,
    val name: String,
    /** "8 PC · PB y 1° · necesita 40 %" */
    val specs: String,
    /** Recorrido o propósito de la ruta. */
    val description: String,
    val selected: Boolean,
)

/** Un ítem de la verificación previa: siempre con símbolo, texto y valor. */
data class PreflightItemUi(
    val label: String,
    val value: String,
    val level: StatusLevel,
    val detail: String? = null,
)

data class MisionesUiState(
    val strip: StateStripUi,
    val routes: List<RouteCardUi>,
    /** "ANTES DE SALIR · RONDA COMPLETA" */
    val preflightTitle: String,
    val preflight: List<PreflightItemUi>,
    val start: CommandButtonUi,
    /** Ronda en curso, si la hay: "Ronda completa · PC 3/8". */
    val currentMission: String? = null,
    val lastStart: LastCommandUi? = null,
)
