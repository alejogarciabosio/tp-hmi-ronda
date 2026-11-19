package ar.edu.uade.ronda.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.CommandChip
import ar.edu.uade.ronda.ui.components.HoldToConfirm
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.ScreenHeader
import ar.edu.uade.ronda.ui.components.StateStrip
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.telemetria.TelemetriaPreviewData
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun ControlRoute(viewModel: ControlViewModel, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ControlScreen(
        state, onNavigate, onBack,
        onOperationalStop = viewModel::onOperationalStop,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onReturnToBase = viewModel::onReturnToBase,
        onSafePoint = viewModel::onSafePoint,
        onSkipCheckpoint = viewModel::onSkipCheckpoint,
        onCancelMission = viewModel::onCancelMission,
        onReleaseControl = { onNavigate(Route.SELECCION) },
    )
}

@Composable
fun ControlScreen(
    state: ControlUiState,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    onOperationalStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReturnToBase: () -> Unit,
    onSafePoint: () -> Unit,
    onSkipCheckpoint: () -> Unit,
    onCancelMission: () -> Unit,
    onReleaseControl: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RondaColors.ink950)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Control", onBack, state.robotId)
            StateStrip(state.strip)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("DETENCIÓN")
                HoldToConfirm(
                    OperationalStopTexts.LABEL, onOperationalStop,
                    enabled = state.stop.enabled, disabledReason = state.stop.reason,
                )
                Text(OperationalStopTexts.NOTE, style = RondaType.textoNota, color = RondaColors.stateError)

                SectionTitle("ÓRDENES")
                CommandButton(state.pause, onPause)
                CommandButton(state.resume, onResume)
                HoldToConfirm(
                    state.returnToBase.label, onReturnToBase,
                    enabled = state.returnToBase.state != CommandUiState.DISABLED && state.returnToBase.state != CommandUiState.PENDING,
                    disabledReason = state.returnToBase.reason,
                )
                if (state.returnToBase.state != CommandUiState.DISABLED) {
                    state.returnToBase.reason?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
                }
                CommandButton(state.safePoint, onSafePoint)
                CommandButton(state.skipCheckpoint, onSkipCheckpoint)
                HoldToConfirm(
                    state.cancel.label, onCancelMission,
                    enabled = state.cancel.state != CommandUiState.DISABLED && state.cancel.state != CommandUiState.PENDING,
                    disabledReason = state.cancel.reason,
                )
                if (state.cancel.state != CommandUiState.DISABLED) {
                    state.cancel.reason?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
                }
                CommandButton(CommandButtonUi("Liberar control", CommandKind.SECONDARY), onReleaseControl)

                SectionTitle("REGISTRO DE ÓRDENES · ESTA SESIÓN")
                if (state.log.isEmpty()) {
                    Text("Todavía no se envió ninguna orden.", style = RondaType.textoCuerpo, color = RondaColors.bone400)
                }
                state.log.forEach { LogRow(it) }
                Text(
                    "Enviada no es ejecutada: una orden se da por cumplida recién cuando la telemetría muestra el efecto (VERIFICADA).",
                    style = RondaType.textoNota, color = RondaColors.bone400,
                )
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

@Composable
private fun LogRow(row: CommandLogRowUi) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(row.time, style = RondaType.datoChico, color = RondaColors.bone400)
            Column(Modifier.weight(1f)) {
                Text(row.label, style = RondaType.textoCuerpo, color = RondaColors.bone100)
                row.detail?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
            }
            CommandChip(row.chip)
        }
        HorizontalDivider(color = RondaColors.ink700)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 1300)
@Composable
private fun ControlPreview() = PreviewSurface {
    val all = CommandType.entries.associateWith { CommandAvailability.Enabled } +
        (CommandType.RESUME_MISSION to CommandAvailability.disabled("No hay una misión pausada o detenida para reanudar"))
    ControlScreen(
        ControlMapper.map(ControlInputs(TelemetriaPreviewData.enRonda, ConnectionState.CONNECTED, 1_000, null, emptyList(), all, 1_400)),
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )
}
