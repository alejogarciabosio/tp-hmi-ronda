package ar.edu.uade.ronda.ui.alertas

import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.supervision.SnapshotUi

data class AlertCardUi(
    val id: String,
    val level: StatusLevel,
    /** "CRÍTICA · SIN RECONOCER" */
    val tag: String,
    val title: String,
    val message: String,
    /** Hora en que la levantó el robot, "03:07". */
    val time: String,
    /** Código corto del tipo, "PERSON-ZONE". */
    val code: String,
    val snapshot: SnapshotUi? = null,
    /** null si la alerta ya está reconocida o se resuelve desde la decisión. */
    val acknowledge: CommandButtonUi? = null,
    /** La alerta forma parte de la decisión pendiente: se resuelve eligiendo una opción. */
    val opensDecision: Boolean = false,
)

data class AlertasUiState(
    val strip: StateStripUi,
    /** "ACTIVAS · 2 SIN RECONOCER" */
    val activeTitle: String,
    val active: List<AlertCardUi>,
    val acknowledged: List<AlertCardUi>,
    /** Aviso cuando el reconocimiento no se pudo enviar. */
    val notice: String? = null,
)
