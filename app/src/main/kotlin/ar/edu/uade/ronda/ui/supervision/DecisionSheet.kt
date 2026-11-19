package ar.edu.uade.ronda.ui.supervision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.HoldToConfirm
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Hoja modal con las opciones que ofrece el robot; no es una ruta (plan §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionSheet(ui: DecisionUi, onOption: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RondaColors.ink900,
        contentColor = RondaColors.bone100,
        scrimColor = RondaColors.scrim.copy(alpha = 0.7f),
    ) {
        DecisionSheetContent(ui, onOption)
    }
}

@Composable
fun DecisionSheetContent(ui: DecisionUi, onOption: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("EL ROBOT ESPERA TU DECISIÓN", style = RondaType.rotuloSeccion, color = RondaColors.signalAmber)
        Text(ui.title, style = RondaType.pantallaTitulo, color = RondaColors.bone100, modifier = Modifier.semantics { heading() })
        Text(ui.message, style = RondaType.textoCuerpo, color = RondaColors.bone100)
        ui.options.forEachIndexed { idx, o ->
            if (o.requiresHold) {
                HoldToConfirm(o.label, { onOption(o.id) }, enabled = o.enabled, disabledReason = o.reason)
            } else {
                CommandButton(
                    CommandButtonUi(
                        o.label,
                        if (idx == 0) CommandKind.PRIMARY else CommandKind.SECONDARY,
                        if (o.enabled) CommandUiState.ENABLED else CommandUiState.DISABLED,
                        o.reason,
                    ),
                    { onOption(o.id) },
                )
            }
        }
    }
}

private val obstacle = DecisionUi(
    "Obstáculo en el pasillo · PC4", "Un objeto bloquea el Pasillo A. El robot se detuvo.",
    listOf(
        DecisionOptionUi("WAIT_30S", "Esperar 30 s", false, true),
        DecisionOptionUi("SKIP_CHECKPOINT", "Saltar el PC4", false, true),
        DecisionOptionUi("RETURN_TO_SAFE_POINT", "Regresar a la base", true, true),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun DecisionObstaclePreview() = PreviewSurface { DecisionSheetContent(obstacle, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun DecisionPersonPreview() = PreviewSurface {
    DecisionSheetContent(
        DecisionUi(
            "Persona en zona restringida · PC5", "Persona en zona restringida (Sala técnica) fuera de horario.",
            listOf(
                DecisionOptionUi("AUTHORIZED_PERSON", "Persona autorizada (registrar)", false, true),
                DecisionOptionUi("SEND_GUARD", "Enviar vigilante", false, true),
                DecisionOptionUi("CONTINUE_ROUND", "Continuar ronda", false, true),
            ),
        ),
        {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun DecisionDisabledPreview() = PreviewSurface {
    DecisionSheetContent(
        obstacle.copy(options = obstacle.options.map { it.copy(enabled = false, reason = "Sin conexión con el robot: no se pueden enviar órdenes") }),
        {},
    )
}
