package ar.edu.uade.ronda.ui.alertas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.StateSymbol
import ar.edu.uade.ronda.ui.components.color
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.supervision.SnapshotImage
import ar.edu.uade.ronda.ui.telemetria.TelemetriaPreviewData
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun AlertasRoute(viewModel: AlertasViewModel, onNavigate: (Route) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertasScreen(state, onNavigate, viewModel::onAcknowledge, viewModel::onNoticeDismissed)
}

@Composable
fun AlertasScreen(
    state: AlertasUiState,
    onNavigate: (Route) -> Unit,
    onAcknowledge: (String) -> Unit,
    onNoticeDismissed: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Alertas", onBack = null, trailing = "Simulación", onTrailingClick = { onNavigate(Route.SIMULACION) })
            StateStrip(state.strip)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.notice?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateSymbol(StatusLevel.WARNING, size = 18.dp)
                        Text(it, style = RondaType.textoCuerpo, color = RondaColors.bone100, modifier = Modifier.weight(1f))
                    }
                    CommandButton(CommandButtonUi("Entendido", CommandKind.SECONDARY), onNoticeDismissed)
                }
                SectionTitle(state.activeTitle)
                if (state.active.isEmpty()) {
                    Text("Sin alertas activas.", style = RondaType.textoCuerpo, color = RondaColors.bone400)
                }
                state.active.forEach { card ->
                    AlertCard(card, onAcknowledge)
                    if (card.opensDecision) {
                        CommandButton(
                            CommandButtonUi("Ver opciones de la decisión", CommandKind.PRIMARY),
                            { onNavigate(Route.SUPERVISION) },
                        )
                    }
                }
                if (state.acknowledged.isNotEmpty()) {
                    SectionTitle("RECONOCIDAS")
                    state.acknowledged.forEach { AlertCard(it, onAcknowledge) }
                }
            }
        }
        RondaNavBar(Route.ALERTAS, onNavigate)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = RondaType.rotuloSeccion, color = RondaColors.bone400,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun AlertCard(card: AlertCardUi, onAcknowledge: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(RondaColors.ink900)) {
        // Borde con el color del nivel; el nivel también va en símbolo y texto.
        Box(Modifier.width(4.dp).fillMaxHeight().background(card.level.color()))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateSymbol(card.level, size = 14.dp)
                Text(card.tag, style = RondaType.rotuloChico, color = card.level.color(), modifier = Modifier.weight(1f))
                Text(card.time, style = RondaType.datoChico, color = RondaColors.bone100)
            }
            Text(card.code, style = RondaType.datoChico, color = RondaColors.bone400)
            Text(card.title, style = RondaType.textoEnfasis, color = RondaColors.bone100)
            Text(card.message, style = RondaType.textoNota, color = RondaColors.bone400)
            card.snapshot?.let { SnapshotImage(it, Modifier.fillMaxWidth(0.6f)) }
            card.acknowledge?.let { CommandButton(it, { onAcknowledge(card.id) }) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1100)
@Composable
private fun AlertasPreview() = PreviewSurface {
    val t = TelemetriaPreviewData.enRonda.copy(
        alerts = listOf(
            Alert("a1", AlertType.LOW_BATTERY, Severity.WARNING, "Alcanza para volver a base. Se recomienda regresar.", 900, false),
            Alert("a2", AlertType.OBSTACLE, Severity.WARNING, "Se saltó el PC4 por decisión del operador.", 500, true),
        ),
    )
    AlertasScreen(
        AlertasMapper.map(
            AlertasInputs(t, ConnectionState.CONNECTED, 1_000, null, emptyList(),
                CommandType.entries.associateWith { CommandAvailability.Enabled }, 1_400),
        ),
        {}, {}, {},
    )
}
