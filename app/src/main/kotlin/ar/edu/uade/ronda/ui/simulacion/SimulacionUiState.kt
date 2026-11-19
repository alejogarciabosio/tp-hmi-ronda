package ar.edu.uade.ronda.ui.simulacion

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.ui.model.CommandButtonUi

data class ScenarioUi(
    val scenario: SimScenario,
    val label: String,
    val selected: Boolean,
)

enum class SimToggle { LINK_CUT, REJECT_NEXT, DELAY_RESPONSES }

data class SimToggleUi(
    val toggle: SimToggle,
    val title: String,
    val description: String,
    val on: Boolean,
    val enabled: Boolean = true,
)

enum class SimInjection { OBSTACLE, PERSON, FAULT }

data class SimInjectionUi(
    val injection: SimInjection,
    val button: CommandButtonUi,
    val description: String,
)

/** Una opción del selector de velocidad: ×1, ×4 u ×8. */
data class TimeScaleUi(val scale: Double, val label: String, val selected: Boolean)

data class SimulacionUiState(
    val scenarios: List<ScenarioUi>,
    /** Qué hacer después de cargar el escenario elegido para que se vea en la app. */
    val scenarioHint: String,
    val toggles: List<SimToggleUi>,
    val timedCut: CommandButtonUi,
    val injections: List<SimInjectionUi>,
    val batteryTitle: String,
    val batteryMinus: CommandButtonUi,
    val batteryPlus: CommandButtonUi,
    /** "Velocidad · ×4" */
    val timeScaleTitle: String = "Velocidad · ×1",
    val timeScales: List<TimeScaleUi> = emptyList(),
)
