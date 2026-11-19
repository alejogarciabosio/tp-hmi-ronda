package ar.edu.uade.ronda.ui.misiones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.StateSymbol
import ar.edu.uade.ronda.ui.components.StatusBadge
import ar.edu.uade.ronda.ui.model.StatusBadgeUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.supervision.LastCommandRow
import ar.edu.uade.ronda.ui.telemetria.TelemetriaPreviewData
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun MisionesRoute(viewModel: MisionesViewModel, onNavigate: (Route) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MisionesScreen(state, onNavigate, viewModel::onSelectRoute, viewModel::onStart)
}

@Composable
fun MisionesScreen(
    state: MisionesUiState,
    onNavigate: (Route) -> Unit,
    onSelectRoute: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Misiones", onBack = null, trailing = "Simulación", onTrailingClick = { onNavigate(Route.SIMULACION) })
            StateStrip(state.strip)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.currentMission?.let {
                    Text("EN CURSO", style = RondaType.rotuloSeccion, color = RondaColors.bone400, modifier = Modifier.semantics { heading() })
                    Text(it, style = RondaType.textoEnfasis, color = RondaColors.bone100)
                }
                Text("RUTAS DISPONIBLES", style = RondaType.rotuloSeccion, color = RondaColors.bone400, modifier = Modifier.semantics { heading() })
                state.routes.forEach { RouteCard(it, onSelectRoute) }

                Text(state.preflightTitle, style = RondaType.rotuloSeccion, color = RondaColors.bone400,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                state.preflight.forEach { PreflightRow(it) }

                state.lastStart?.let { LastCommandRow(it) }
                CommandButton(state.start, onStart)
            }
        }
        RondaNavBar(Route.MISIONES, onNavigate)
    }
}

@Composable
private fun RouteCard(card: RouteCardUi, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, if (card.selected) RondaColors.signalAmber else RondaColors.ink700), RondaShapes.r2)
            .background(if (card.selected) RondaColors.ink850 else RondaColors.ink950, RondaShapes.r2)
            .selectable(card.selected, role = Role.RadioButton, onClick = { onSelect(card.id) })
            .semantics { contentDescription = "${card.name}. ${card.specs}" + if (card.selected) ". Elegida" else "" }
            .heightIn(min = 48.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, style = RondaType.textoEnfasis, color = RondaColors.bone100, modifier = Modifier.weight(1f))
            if (card.selected) StatusBadge(StatusBadgeUi(StatusLevel.OK, "Elegida"))
        }
        Text(card.specs, style = RondaType.datoChico, color = RondaColors.bone400)
        Text(card.description, style = RondaType.textoNota, color = RondaColors.bone400)
    }
}

@Composable
private fun PreflightRow(item: PreflightItemUi) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StateSymbol(item.level, size = 16.dp)
            Column(Modifier.weight(1f)) {
                Text(item.label, style = RondaType.textoCuerpo, color = RondaColors.bone100)
                item.detail?.let {
                    Text(it, style = RondaType.textoNota, color = if (item.level == StatusLevel.ERROR) RondaColors.stateError else RondaColors.bone400)
                }
            }
            Text(item.value, style = RondaType.datoChico, color = RondaColors.bone400)
        }
        HorizontalDivider(color = RondaColors.ink700)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1000)
@Composable
private fun MisionesPreview() = PreviewSurface {
    val enBase = TelemetriaPreviewData.enRonda.copy(operationalState = OperationalState.EN_BASE, mission = null)
    MisionesScreen(
        MisionesMapper.map(MisionesInputs(enBase, ConnectionState.CONNECTED, 1_000, emptyList(), RouteId.RONDA_COMPLETA, 1_400, "RONDA-01")),
        {}, {}, {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1000)
@Composable
private fun MisionesBateriaBajaPreview() = PreviewSurface {
    val enBase = TelemetriaPreviewData.enRonda.copy(
        operationalState = OperationalState.EN_BASE, mission = null,
        battery = TelemetriaPreviewData.enRonda.battery.copy(percent = 32),
    )
    MisionesScreen(
        MisionesMapper.map(MisionesInputs(enBase, ConnectionState.CONNECTED, 1_000, emptyList(), RouteId.RONDA_COMPLETA, 1_400, "RONDA-01")),
        {}, {}, {},
    )
}
