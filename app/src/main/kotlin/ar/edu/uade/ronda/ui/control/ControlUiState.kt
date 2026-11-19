package ar.edu.uade.ronda.ui.control

import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.supervision.StopButtonUi

/** Una fila del registro de órdenes de la sesión. */
data class CommandLogRowUi(
    val id: String,
    /** Hora de envío, "00:12:40". */
    val time: String,
    val label: String,
    val chip: CommandChipUi,
    val detail: String? = null,
)

data class ControlUiState(
    val robotId: String,
    val strip: StateStripUi,
    val stop: StopButtonUi,
    val pause: CommandButtonUi,
    val resume: CommandButtonUi,
    val returnToBase: CommandButtonUi,
    val safePoint: CommandButtonUi,
    val skipCheckpoint: CommandButtonUi,
    /** Cortar la misión es destructivo: se confirma manteniendo apretado. */
    val cancel: CommandButtonUi,
    val log: List<CommandLogRowUi> = emptyList(),
    /** Batería baja activa con regresar habilitado: se destaca el regreso arriba (como en Supervisión). */
    val lowBatteryReturn: String? = null,
    /** "Vigilante en camino · el robot espera tu orden para seguir". */
    val guardNotice: String? = null,
)
