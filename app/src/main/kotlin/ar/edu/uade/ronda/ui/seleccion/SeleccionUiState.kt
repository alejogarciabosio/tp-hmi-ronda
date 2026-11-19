package ar.edu.uade.ronda.ui.seleccion

import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.StatusBadgeUi

data class RobotCardUi(
    val id: String,
    /** "Go2 EDU · simulado · batería 88 %" */
    val specs: String,
    val badge: StatusBadgeUi,
    /** Quién tiene el control o por qué no se puede usar. */
    val detail: String,
    val takeControl: CommandButtonUi,
    val dimmed: Boolean = false,
)

data class SeleccionUiState(
    val robots: List<RobotCardUi>,
    val operator: String = "Operador garita PB · turno 22:00–06:00",
    /** Id del robot que controla este operador, si ya tomó el control. */
    val controlledRobotId: String? = null,
)
