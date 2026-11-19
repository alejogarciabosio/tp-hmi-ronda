package ar.edu.uade.ronda.ui.desconexion

import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.supervision.LastCommandUi
import ar.edu.uade.ronda.ui.supervision.StopButtonUi

/** Evento que el robot informó tarde: se muestra con su hora original y la de llegada. */
data class LateEventUi(val id: String, val text: String, val occurredAt: String, val receivedAt: String)

data class DesconexionUiState(
    val robotId: String,
    val strip: StateStripUi,
    val staleBanner: StaleBannerUi? = null,
    /** true si hoy no hay problema de enlace: la pantalla lo dice y no muestra datos viejos. */
    val linkOk: Boolean,
    /** "ÚLTIMO DATO CONOCIDO · 00:41:12" */
    val lastKnownTitle: String? = null,
    val lastKnown: List<TelemetryReadoutUi> = emptyList(),
    val policy: List<String>,
    val pending: List<LastCommandUi> = emptyList(),
    val lateEvents: List<LateEventUi> = emptyList(),
    val stop: StopButtonUi,
)
