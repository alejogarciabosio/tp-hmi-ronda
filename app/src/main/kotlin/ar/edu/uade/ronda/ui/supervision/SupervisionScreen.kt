package ar.edu.uade.ronda.ui.supervision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.ui.components.ActionCallout
import ar.edu.uade.ronda.ui.components.CheckpointRail
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.CommandChip
import ar.edu.uade.ronda.ui.components.FloorPlanCanvas
import ar.edu.uade.ronda.ui.components.HoldToConfirm
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.StaleDataBanner
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.components.StateSymbol
import ar.edu.uade.ronda.ui.components.TelemetryReadout
import ar.edu.uade.ronda.ui.components.color
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

/** Conecta el ViewModel con la pantalla: la UI solo observa el estado y manda eventos. */
@Composable
fun SupervisionRoute(viewModel: SupervisionViewModel, onNavigate: (Route) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SupervisionScreen(
        state = state,
        onNavigate = onNavigate,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onReturnToBase = viewModel::onReturnToBase,
        onOperationalStop = viewModel::onOperationalStop,
        onSelectFloor = viewModel::onSelectFloor,
        onDecisionOption = viewModel::onDecisionOption,
        onDismissDecision = viewModel::onDismissDecision,
        onReopenDecision = viewModel::onReopenDecision,
        onNoticeDismissed = viewModel::onNoticeDismissed,
        modifier = modifier,
    )
}

@Composable
fun SupervisionScreen(
    state: SupervisionUiState,
    onNavigate: (Route) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReturnToBase: () -> Unit,
    onOperationalStop: () -> Unit,
    onSelectFloor: (Int) -> Unit,
    onDecisionOption: (String) -> Unit,
    onDismissDecision: () -> Unit,
    onReopenDecision: () -> Unit,
    onNoticeDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopRow(onSimulation = { onNavigate(Route.SIMULACION) })
            StateStrip(state.strip)
            state.guardNotice?.let { ActionCallout(it, StatusLevel.WARNING, Modifier.padding(horizontal = 16.dp)) }
            state.staleBanner?.let {
                StaleDataBanner(
                    it,
                    Modifier.clickable(role = Role.Button, onClickLabel = "Ver detalle de la desconexión") { onNavigate(Route.DESCONEXION) },
                )
            }

            Section("PLANO") {
                Column(Modifier.alpha(if (state.strip.connectionLevel == StatusLevel.STALE) 0.6f else 1f)) {
                    FloorPlanCanvas(state.plan, Modifier.fillMaxWidth())
                }
                FloorTabs(state.floorTabs, onSelectFloor)
                state.planCaption?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
            }

            Section(state.progressTitle.uppercase()) {
                if (state.checkpoints.isNotEmpty()) {
                    CheckpointRail(state.checkpoints)
                } else {
                    Text("Todavía no se inició una ronda.", style = RondaType.textoCuerpo, color = RondaColors.bone400)
                }
            }

            if (state.readouts.isNotEmpty()) {
                Section("TELEMETRÍA") {
                    state.readouts.forEach { r ->
                        TelemetryReadout(
                            r,
                            Modifier.clickable(role = Role.Button, onClickLabel = "Abrir telemetría") { onNavigate(Route.TELEMETRIA) },
                        )
                    }
                }
            }

            Section("ALERTAS ACTIVAS") {
                if (state.alerts.isEmpty()) {
                    Text("Sin alertas activas.", style = RondaType.textoCuerpo, color = RondaColors.bone400)
                } else {
                    state.alerts.forEach { AlertRow(it, onClick = { onNavigate(Route.ALERTAS) }) }
                }
            }

            Section("ÓRDENES") {
                state.notice?.let { msg ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StateSymbol(StatusLevel.WARNING, size = 18.dp)
                            Text(msg, style = RondaType.textoCuerpo, color = RondaColors.bone100, modifier = Modifier.weight(1f))
                        }
                        CommandButton(CommandButtonUi("Entendido", CommandKind.SECONDARY), onNoticeDismissed)
                    }
                }
                state.lastCommand?.let { LastCommandRow(it) }
                if (state.decision != null && state.decisionDismissed) {
                    CommandButton(CommandButtonUi("Decidir cómo seguir", CommandKind.PRIMARY), onReopenDecision)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandButton(state.pause, onPause, Modifier.weight(1f))
                    CommandButton(state.resume, onResume, Modifier.weight(1f))
                }
                HoldToConfirm(
                    state.returnToBase.label, onReturnToBase,
                    enabled = state.returnToBase.state != CommandUiState.DISABLED && state.returnToBase.state != CommandUiState.PENDING,
                    disabledReason = state.returnToBase.reason,
                )
                if (state.returnToBase.state != CommandUiState.DISABLED) {
                    state.returnToBase.reason?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
                }
                HoldToConfirm(
                    OperationalStopTexts.LABEL, onOperationalStop,
                    enabled = state.stop.enabled, disabledReason = state.stop.reason,
                )
                Text(OperationalStopTexts.NOTE, style = RondaType.textoNota, color = RondaColors.bone400)
                CommandButton(CommandButtonUi("Más controles", CommandKind.SECONDARY), { onNavigate(Route.CONTROL) })
            }
        }
        RondaNavBar(Route.SUPERVISION, onNavigate)
    }

    val decision = state.decision
    if (decision != null && !state.decisionDismissed) {
        DecisionSheet(decision, onDecisionOption, onDismissDecision)
    }
}

@Composable
private fun TopRow(onSimulation: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Supervisión", style = RondaType.pantallaTitulo, color = RondaColors.bone100, modifier = Modifier.semantics { heading() })
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Abrir el panel de simulación", onClick = onSimulation)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Simulación", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = RondaType.rotuloSeccion, color = RondaColors.bone400, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun FloorTabs(tabs: List<FloorTabUi>, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            Text(
                "Piso ${tab.label}",
                style = if (tab.selected) RondaType.botonEtiqueta else RondaType.rotuloSeccion,
                color = if (tab.selected) RondaColors.signalAmber else RondaColors.bone400,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .border(1.dp, if (tab.selected) RondaColors.signalAmber else RondaColors.ink700, RondaShapes.r2)
                    .selectable(tab.selected, role = Role.Tab, onClick = { onSelect(tab.floor) })
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics { contentDescription = "Piso ${tab.label}" + if (tab.selected) ", seleccionado" else "" },
            )
        }
    }
}

@Composable
internal fun AlertRow(ui: AlertRowUi, onClick: () -> Unit) {
    val spoken = when (ui.level) {
        StatusLevel.ERROR -> "crítica"
        StatusLevel.WARNING -> "advertencia"
        else -> "informativa"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(RondaColors.ink900, RondaShapes.r2)
            .border(1.dp, ui.level.color(), RondaShapes.r2)
            .clickable(role = Role.Button, onClickLabel = "Abrir alertas", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Alerta $spoken: ${ui.text}. ${ui.age}" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateSymbol(ui.level, size = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(ui.text, style = RondaType.textoCuerpo, color = RondaColors.bone100)
            Text(ui.age, style = RondaType.datoChico, color = RondaColors.bone400)
        }
    }
}

@Composable
internal fun LastCommandRow(ui: LastCommandUi) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Última orden:", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
        Text(ui.label, style = RondaType.textoEnfasis, color = RondaColors.bone100)
        CommandChip(ui.chip)
    }
    ui.detail?.let { Text(it, style = RondaType.datoChico, color = RondaColors.bone400) }
}

// ---- Previews ----

private fun preview(state: SupervisionUiState) = @Composable {
    PreviewSurface {
        SupervisionScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1500)
@Composable
private fun SupervisionEnRondaPreview() = preview(SupervisionPreviewData.enRonda())()

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1500)
@Composable
private fun SupervisionBateriaBajaPreview() = preview(SupervisionPreviewData.bateriaBaja())()

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1500)
@Composable
private fun SupervisionSinDatosPreview() = preview(SupervisionPreviewData.sinDatos())()

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1500)
@Composable
private fun SupervisionEnBasePreview() = preview(SupervisionPreviewData.enBase())()

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1500)
@Composable
private fun SupervisionDecisionDescartadaPreview() = preview(SupervisionPreviewData.decisionDescartada())()
