package ar.edu.uade.ronda.ui.historial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.data.local.EventRecord
import ar.edu.uade.ronda.data.local.MissionRecord
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.operacion.OperationalBottomBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.StateSymbol
import ar.edu.uade.ronda.ui.components.StatusBadge
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun HistorialRoute(viewModel: HistorialViewModel, onNavigate: (Route) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistorialScreen(state, onNavigate, viewModel::onSelectView, viewModel::onCycleSeverity, viewModel::onCyclePeriod)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistorialScreen(
    state: HistorialUiState,
    onNavigate: (Route) -> Unit,
    onSelectView: (HistoryView) -> Unit,
    onCycleSeverity: () -> Unit,
    onCyclePeriod: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Historial", onBack = null, trailing = "Simulación", onTrailingClick = { onNavigate(Route.SIMULACION) })
            StateStrip(state.strip)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryView.entries.forEach { v -> FilterChip(v.label, state.view == v, Role.Tab) { onSelectView(v) } }
                    FilterChip(state.severity.label, false, Role.Button, onCycleSeverity)
                    FilterChip(state.period.label, false, Role.Button, onCyclePeriod)
                }
                if (state.view != HistoryView.EVENTOS) {
                    SectionTitle("MISIONES")
                    if (state.missions.isEmpty()) Empty("No hay misiones en el período elegido.")
                    state.missions.forEach { MissionRow(it) }
                }
                if (state.view != HistoryView.MISIONES) {
                    if (state.eventGroups.isEmpty()) {
                        SectionTitle("EVENTOS")
                        Empty("No hay eventos con estos filtros.")
                    }
                    state.eventGroups.forEach { g ->
                        SectionTitle(g.title)
                        g.events.forEach { EventRow(it) }
                    }
                }
                Text(state.retentionNote, style = RondaType.textoNota, color = RondaColors.bone400)
            }
        }
        OperationalBottomBar(Route.HISTORIAL, onNavigate)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, role: Role, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .border(BorderStroke(1.dp, if (selected) RondaColors.signalAmber else RondaColors.ink700), RondaShapes.r2)
            .selectable(selected, role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (selected) RondaType.botonEtiqueta else RondaType.rotuloSeccion,
            color = if (selected) RondaColors.signalAmber else RondaColors.bone100)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = RondaType.rotuloSeccion, color = RondaColors.bone400,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun Empty(text: String) = Text(text, style = RondaType.textoCuerpo, color = RondaColors.bone400)

@Composable
private fun MissionRow(m: MissionRowUi) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.route, style = RondaType.textoEnfasis, color = RondaColors.bone100, modifier = Modifier.weight(1f))
                StatusBadge(m.badge)
            }
            Text(m.period, style = RondaType.datoChico, color = RondaColors.bone400)
            Text(m.summary, style = RondaType.textoNota, color = RondaColors.bone400)
        }
        HorizontalDivider(color = RondaColors.ink700)
    }
}

@Composable
private fun EventRow(e: EventRowUi) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(e.time, style = RondaType.datoChico, color = RondaColors.bone400)
            StateSymbol(e.level, size = 14.dp)
            Column(Modifier.weight(1f)) {
                Text(e.text, style = RondaType.textoCuerpo, color = RondaColors.bone100)
                Text(e.code, style = RondaType.datoChico, color = RondaColors.bone400)
            }
        }
        HorizontalDivider(color = RondaColors.ink700)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1000)
@Composable
private fun HistorialPreview() = PreviewSurface {
    val now = 1_763_600_000_000L
    val missions = listOf(
        MissionRecord("m1", "RONDA-01", "Ronda completa", now - 3_600_000, now - 1_400_000, "COMPLETADA", 8, 8, 1),
        MissionRecord("m2", "RONDA-01", "Ronda completa", now - 90_000_000, now - 87_000_000, "CANCELADA", 6, 8, 0),
    )
    val events = listOf(
        EventRecord("e1", now - 1_400_000, "ALERTA", "PERSON_DETECTED", "CRITICAL", "Persona en zona restringida · PC5"),
        EventRecord("e2", now - 1_500_000, "INCIDENTE", "OBSTACLE", "WARNING", "Obstáculo en PC4"),
    )
    HistorialScreen(
        HistorialMapper.map(
            HistorialInputs(null, ConnectionState.CONNECTED, null, missions, events,
                HistoryView.TODOS, SeverityFilter.TODAS, PeriodFilter.SIETE, now),
        ),
        {}, {}, {}, {},
    )
}
