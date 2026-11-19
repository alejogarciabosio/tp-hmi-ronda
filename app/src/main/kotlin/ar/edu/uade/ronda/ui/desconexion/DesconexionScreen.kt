package ar.edu.uade.ronda.ui.desconexion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.ui.components.HoldToConfirm
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StaleDataBanner
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.TelemetryReadout
import ar.edu.uade.ronda.data.LinkLog
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.supervision.LastCommandRow
import ar.edu.uade.ronda.ui.telemetria.TelemetriaPreviewData
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun DesconexionRoute(viewModel: DesconexionViewModel, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DesconexionScreen(state, onNavigate, onBack)
}

@Composable
fun DesconexionScreen(state: DesconexionUiState, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Desconexión", onBack, state.robotId)
            StateStrip(state.strip)
            state.staleBanner?.let { StaleDataBanner(it) }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.linkOk) {
                    Text("El enlace con el robot está activo. Esta pantalla muestra qué pasa cuando se corta.",
                        style = RondaType.textoCuerpo, color = RondaColors.bone100)
                }
                state.lastKnownTitle?.let { SectionTitle(it) }
                state.lastKnown.forEach { TelemetryReadout(it) }

                SectionTitle("QUÉ HACE EL ROBOT SIN ENLACE (POLÍTICA DECLARADA)")
                state.policy.forEach { Text(it, style = RondaType.textoCuerpo, color = RondaColors.bone100) }
                Text("Supuesto declarado del simulador: el robot no lo confirma mientras no hay enlace.",
                    style = RondaType.textoNota, color = RondaColors.bone400)

                if (state.pending.isNotEmpty()) {
                    SectionTitle(state.pendingTitle)
                    state.pending.forEach { LastCommandRow(it) }
                }

                if (state.lateEvents.isNotEmpty()) {
                    SectionTitle("EVENTOS RECIBIDOS AL VOLVER EL ENLACE")
                    state.lateEvents.forEach { e ->
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(e.occurredAt, style = RondaType.datoChico, color = RondaColors.bone100)
                                Text(e.text, style = RondaType.textoCuerpo, color = RondaColors.bone100, modifier = Modifier.weight(1f))
                            }
                            Text("Ocurrió ${e.occurredAt}, llegó ${e.receivedAt}", style = RondaType.textoNota, color = RondaColors.bone400)
                        }
                    }
                }

                SectionTitle("ACCIONES")
                Text("La app reintenta sola: apenas llegue telemetría nueva, el estado se actualiza.",
                    style = RondaType.textoNota, color = RondaColors.bone400)
                if (!state.linkOk) {
                    Text(
                        "Sin enlace la detención operativa puede no llegar al robot. Si hay riesgo, usá la parada física.",
                        style = RondaType.textoNota, color = RondaColors.stateError,
                    )
                }
            }
        }
        RondaNavBar(Route.SUPERVISION, onNavigate)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = RondaType.rotuloSeccion, color = RondaColors.bone400,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1100)
@Composable
private fun DesconexionPreview() = PreviewSurface {
    DesconexionScreen(
        DesconexionMapper.map(
            DesconexionInputs(TelemetriaPreviewData.enRonda, ConnectionState.DISCONNECTED, 1_000, emptyList(),
                CommandType.entries.associateWith { CommandAvailability.Enabled }, LinkLog(lastCutDataAt = 1_000), 35_000),
        ),
        {}, {},
    )
}
