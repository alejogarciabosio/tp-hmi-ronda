package ar.edu.uade.ronda.ui.telemetria

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StaleDataBanner
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.TelemetryReadout
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun TelemetriaRoute(viewModel: TelemetriaViewModel, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TelemetriaScreen(state, onNavigate, onBack)
}

@Composable
fun TelemetriaScreen(state: TelemetriaUiState, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Telemetría", onBack, state.updated)
            StateStrip(state.strip)
            state.staleBanner?.let { StaleDataBanner(it) }
            if (state.robot.isEmpty()) {
                Text(
                    "Todavía no llegó telemetría del robot.", style = RondaType.textoCuerpo, color = RondaColors.bone400,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                ReadoutSection(state.robotTitle, state.robot)
                ReadoutSection("SERVICIO DE RONDA", state.service)
            }
        }
        RondaNavBar(Route.SUPERVISION, onNavigate)
    }
}

@Composable
private fun ReadoutSection(title: String, readouts: List<TelemetryReadoutUi>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = RondaType.rotuloSeccion, color = RondaColors.bone400, modifier = Modifier.semantics { heading() })
        readouts.forEach { TelemetryReadout(it) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1200)
@Composable
private fun TelemetriaPreview() = PreviewSurface {
    TelemetriaScreen(
        TelemetriaMapper.map(TelemetriaPreviewData.enRonda, ConnectionState.CONNECTED, 1_000, 1_400),
        {}, {},
    )
}
