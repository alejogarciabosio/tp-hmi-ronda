package ar.edu.uade.ronda.ui.seleccion

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.StatusBadge
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.theme.MinTouchTarget
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun SeleccionRoute(viewModel: SeleccionViewModel, onNavigate: (Route) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SeleccionScreen(state, onTakeControl = { id ->
        if (viewModel.onTakeControl(id)) onNavigate(Route.SUPERVISION)
    }, onNavigate = onNavigate)
}

@Composable
fun SeleccionScreen(state: SeleccionUiState, onTakeControl: (String) -> Unit, onNavigate: (Route) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Selección de robot", style = RondaType.pantallaTitulo, color = RondaColors.bone100,
                modifier = Modifier.weight(1f).semantics { heading() })
            Box(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .widthIn(min = MinTouchTarget)
                    .clickable(role = Role.Button, onClickLabel = "Abrir el panel de simulación") { onNavigate(Route.SIMULACION) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Simulación", style = RondaType.rotuloChico, color = RondaColors.bone400)
            }
        }
        Text("Elegí el robot para la ronda nocturna. Un solo operador puede dar órdenes a cada robot.",
            style = RondaType.textoCuerpo, color = RondaColors.bone100)
        Text("ROBOTS · TURNO NOCHE", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
        state.robots.forEach { RobotCard(it, onTakeControl) }
        Text("QUIÉN SOS", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
        Text(state.operator, style = RondaType.textoEnfasis, color = RondaColors.bone100)
        Text("Al tomar el control, el resto de los operadores solo puede mirar. El control se libera al cerrar la sesión o desde Control.",
            style = RondaType.textoNota, color = RondaColors.bone400)
    }
}

@Composable
private fun RobotCard(card: RobotCardUi, onTakeControl: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, RondaColors.ink700, RondaShapes.r2).padding(16.dp)
            .alpha(if (card.dimmed) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.id, style = RondaType.estadoTitulo, color = RondaColors.bone100, modifier = Modifier.weight(1f))
            StatusBadge(card.badge)
        }
        Text(card.specs, style = RondaType.datoChico, color = RondaColors.bone400)
        Text(card.detail, style = RondaType.textoNota, color = RondaColors.bone100)
        CommandButton(card.takeControl, onClick = { onTakeControl(card.id) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 900)
@Composable
private fun SeleccionPreview() = PreviewSurface {
    SeleccionScreen(SeleccionMapper.map(null, ConnectionState.CONNECTED, null), {}, {})
}
