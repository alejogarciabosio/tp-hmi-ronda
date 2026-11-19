package ar.edu.uade.ronda.ui.telemetria

import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi

data class TelemetriaUiState(
    val strip: StateStripUi,
    val staleBanner: StaleBannerUi? = null,
    /** "Actualizado hace 0,4 s" o "Sin datos". */
    val updated: String,
    /** "ROBOT · RONDA-01 · GO2 EDU" */
    val robotTitle: String = "ROBOT",
    val robot: List<TelemetryReadoutUi> = emptyList(),
    val service: List<TelemetryReadoutUi> = emptyList(),
)
