package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.ui.model.CheckpointUi
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.FloorPlanUi
import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi

data class AlertRowUi(val id: String, val text: String, val level: StatusLevel, val age: String)

data class FloorTabUi(val floor: Int, val label: String, val selected: Boolean)

data class LastCommandUi(val label: String, val chip: CommandChipUi, val detail: String? = null)

data class StopButtonUi(val enabled: Boolean, val reason: String? = null)

data class DecisionOptionUi(
    val id: String,
    val label: String,
    /** Regresar a la base es la opción que corta la ronda: se confirma manteniendo apretado. */
    val requiresHold: Boolean,
    val enabled: Boolean,
    val reason: String? = null,
)

data class DecisionUi(val title: String, val message: String, val options: List<DecisionOptionUi>)

data class SupervisionUiState(
    val strip: StateStripUi,
    val staleBanner: StaleBannerUi? = null,
    val hasData: Boolean = false,
    val plan: FloorPlanUi,
    val floorTabs: List<FloorTabUi> = emptyList(),
    /** Aclaración cuando el robot del plano es la última posición conocida. */
    val planCaption: String? = null,
    val progressTitle: String = "Sin misión en curso",
    val checkpoints: List<CheckpointUi> = emptyList(),
    val readouts: List<TelemetryReadoutUi> = emptyList(),
    val alerts: List<AlertRowUi> = emptyList(),
    val pause: CommandButtonUi,
    val resume: CommandButtonUi,
    val returnToBase: CommandButtonUi,
    val stop: StopButtonUi,
    val lastCommand: LastCommandUi? = null,
    val decision: DecisionUi? = null,
    /** Hay una decisión pendiente cuya hoja se cerró: se ofrece un botón para reabrirla. */
    val decisionDismissed: Boolean = false,
    /** Aviso legible cuando una acción no se pudo enviar (p. ej. falta la alerta a reconocer). */
    val notice: String? = null,
)
