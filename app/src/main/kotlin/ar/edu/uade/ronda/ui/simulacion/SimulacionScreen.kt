package ar.edu.uade.ronda.ui.simulacion

import androidx.compose.ui.semantics.liveRegion

import androidx.compose.ui.semantics.LiveRegionMode

import ar.edu.uade.ronda.ui.theme.RondaShapes

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.foundation.selection.selectableGroup

import androidx.compose.foundation.selection.selectable

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.border

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationSettings
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.HazardStripes
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.telemetria.TelemetriaPreviewData
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun SimulacionRoute(viewModel: SimulacionViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SimulacionScreen(
        state, onBack,
        onLoadScenario = viewModel::onLoadScenario,
        onToggle = viewModel::onToggle,
        onTimedCut = viewModel::onTimedCut,
        onInject = viewModel::onInject,
        onBatteryStep = viewModel::onBatteryStep,
        onReset = viewModel::onReset,
        onTimeScale = viewModel::onTimeScale,
    )
}

@Composable
fun SimulacionScreen(
    state: SimulacionUiState,
    onBack: () -> Unit,
    onLoadScenario: (SimScenario) -> Unit,
    onToggle: (SimToggle, Boolean) -> Unit,
    onTimedCut: () -> Unit,
    onInject: (SimInjection) -> Unit,
    onBatteryStep: (Int) -> Unit,
    onReset: () -> Unit,
    onTimeScale: (Double) -> Unit = {},
) {
    // Las franjas arriba y abajo marcan que esto no es parte de la interfaz de operación.
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        HazardStripes(Modifier.fillMaxWidth().height(8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Panel de simulación", onBack, trailing = "Solo demo")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SimTag()
                Text(
                    "Herramienta de la demo. Nada de esto existe en una ronda real: fuerza los escenarios para mostrar cómo responde la interfaz.",
                    style = RondaType.textoNota, color = RondaColors.bone400,
                )

                SectionTitle("Escenarios")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.scenarios.forEach { sc -> ScenarioRow(sc) { onLoadScenario(sc.scenario) } }
                }
                Text(state.scenarioHint, style = RondaType.textoCuerpo, color = RondaColors.bone100)
                Text("Cargar un escenario reinicia el robot en la base.", style = RondaType.textoNota, color = RondaColors.bone400)

                SectionTitle("Fallas a inyectar")
                Column {
                    state.toggles.forEach { t ->
                        ToggleRow(t) { onToggle(t.toggle, it) }
                        HorizontalDivider(thickness = 1.dp, color = RondaColors.ink700)
                    }
                }
                CommandButton(state.timedCut, onTimedCut)
                state.injections.forEach { inj ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CommandButton(inj.button, { onInject(inj.injection) })
                        Text(inj.description, style = RondaType.textoNota, color = RondaColors.bone400)
                    }
                }

                state.injectionRejection?.let {
                    Text(it, style = RondaType.textoCuerpo, color = RondaColors.stateError, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }

                SectionTitle(state.batteryTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommandButton(state.batteryMinus, { onBatteryStep(-SimulacionMapper.BATTERY_STEP) }, Modifier.weight(1f))
                    CommandButton(state.batteryPlus, { onBatteryStep(SimulacionMapper.BATTERY_STEP) }, Modifier.weight(1f))
                }

                SectionTitle(state.timeScaleTitle)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.timeScales.forEach { ts -> TimeScaleChip(ts, Modifier.weight(1f)) { onTimeScale(ts.scale) } }
                }
                Text(
                    "Acelera el movimiento del robot para la demo. Fuera de ×1, Supervisión muestra «SIMULACIÓN ×N» en la franja de estado.",
                    style = RondaType.textoNota, color = RondaColors.bone400,
                )

                SectionTitle("Simulación")
                CommandButton(CommandButtonUi("Reiniciar simulación", CommandKind.SECONDARY), onReset)
            }
        }
        HazardStripes(Modifier.fillMaxWidth().height(8.dp))
    }
}

/** El escenario cargado se distingue por borde, relleno, marca y texto «· cargado», no solo por color. */
@Composable
private fun ScenarioRow(sc: ScenarioUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(if (sc.selected) 2.dp else 1.dp, if (sc.selected) RondaColors.signalAmber else RondaColors.ink700, RondaShapes.r2)
            .background(if (sc.selected) RondaColors.ink850 else RondaColors.ink950, RondaShapes.r2)
            .selectable(sc.selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (sc.selected) "◉" else "○", style = RondaType.textoEnfasis, color = if (sc.selected) RondaColors.signalAmber else RondaColors.bone400)
        Text(sc.label.uppercase(), style = RondaType.botonEtiqueta, color = if (sc.selected) RondaColors.signalAmber else RondaColors.bone100)
    }
}

@Composable
private fun TimeScaleChip(ui: TimeScaleUi, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .border(if (ui.selected) 2.dp else 1.dp, if (ui.selected) RondaColors.signalAmber else RondaColors.ink700, RondaShapes.r2)
            .background(if (ui.selected) RondaColors.ink850 else RondaColors.ink950, RondaShapes.r2)
            .selectable(ui.selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Velocidad ${ui.label}" + if (ui.selected) ", actual" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ui.label + if (ui.selected) " · actual" else "",
            style = RondaType.botonEtiqueta, color = if (ui.selected) RondaColors.signalAmber else RondaColors.bone100,
        )
    }
}

@Composable
private fun SimTag() {
    Row(
        Modifier
            .drawBehind {
                drawRect(
                    RondaColors.signalAmber,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SIMULACIÓN", style = RondaType.botonEtiqueta, color = RondaColors.signalAmber)
        Text("solo demo", style = RondaType.datoChico, color = RondaColors.bone400)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), style = RondaType.rotuloSeccion, color = RondaColors.bone400,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun ToggleRow(ui: SimToggleUi, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = ui.on, enabled = ui.enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ui.title, style = RondaType.textoEnfasis, color = RondaColors.bone100)
            Text(ui.description, style = RondaType.textoNota, color = RondaColors.bone400)
        }
        Text(
            if (ui.on) "SÍ" else "NO", style = RondaType.rotuloChico,
            color = if (ui.on) RondaColors.signalAmber else RondaColors.bone400,
        )
        // El Switch no recibe toques propios: la fila entera es el control accesible.
        Switch(
            checked = ui.on, onCheckedChange = null, enabled = ui.enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = RondaColors.signalAmber,
                checkedThumbColor = RondaColors.ink950,
                checkedBorderColor = RondaColors.signalAmber,
                uncheckedTrackColor = RondaColors.ink850,
                uncheckedThumbColor = RondaColors.bone400,
                uncheckedBorderColor = RondaColors.ink700,
                disabledCheckedTrackColor = RondaColors.signalAmber.copy(alpha = 0.6f),
                disabledCheckedThumbColor = RondaColors.ink950,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1400)
@Composable
private fun SimulacionPreview() = PreviewSurface {
    SimulacionScreen(
        SimulacionMapper.map(SimulacionInputs(SimulationSettings(), TelemetriaPreviewData.enRonda, null, rejectArmed = true)),
        onBack = {}, onLoadScenario = {}, onToggle = { _, _ -> }, onTimedCut = {}, onInject = {}, onBatteryStep = {}, onReset = {},
    )
}
