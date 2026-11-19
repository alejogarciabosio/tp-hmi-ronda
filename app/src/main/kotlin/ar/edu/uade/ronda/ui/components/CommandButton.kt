package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

/**
 * Botón de comando. Deshabilitado = contorno tachado + motivo escrito.
 * Tras el toque muestra el estado de la orden (pendiente, rechazada, sin respuesta) con [CommandChip].
 */
@Composable
fun CommandButton(ui: CommandButtonUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Solo DISABLED bloquea el toque; PENDING aún espera respuesta. REJECTED y NO_RESPONSE se pueden reintentar.
    val disabled = ui.state == CommandUiState.DISABLED
    val clickable = ui.state != CommandUiState.DISABLED && ui.state != CommandUiState.PENDING
    val enabled = !disabled
    val accent = when (ui.kind) {
        CommandKind.PRIMARY -> RondaColors.signalAmber
        CommandKind.SECONDARY -> RondaColors.bone100
        CommandKind.DESTRUCTIVE -> RondaColors.stateError
    }
    val container = if (ui.state == CommandUiState.ENABLED && ui.kind == CommandKind.PRIMARY) RondaColors.signalAmber else RondaColors.ink950
    val content = when {
        disabled -> RondaColors.stateStale
        ui.kind == CommandKind.PRIMARY && ui.state == CommandUiState.ENABLED -> RondaColors.onSignal
        else -> accent
    }
    val chip = when (ui.state) {
        CommandUiState.PENDING -> CommandChipStatus.PENDING
        CommandUiState.CONFIRMED -> CommandChipStatus.CONFIRMED
        CommandUiState.REJECTED -> CommandChipStatus.REJECTED
        CommandUiState.NO_RESPONSE -> CommandChipStatus.NO_RESPONSE
        else -> null
    }
    val desc = buildString {
        append(ui.label)
        if (disabled) append(". No disponible")
        chip?.let { append(". Estado de la orden: ${it.name.lowercase().replace('_', ' ')}") }
        ui.reason?.let { append(". $it") }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(container, RondaShapes.r2)
                .border(BorderStroke(1.dp, if (enabled) accent else RondaColors.ink700), RondaShapes.r2)
                .clickable(enabled = clickable, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = desc
                    if (!clickable) disabled()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    ui.label.uppercase(), style = RondaType.botonEtiqueta, color = content,
                    textDecoration = if (disabled) TextDecoration.LineThrough else null,
                )
                chip?.let { CommandChip(CommandChipUi(it)) }
            }
        }
        ui.reason?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 360)
@Composable
private fun CommandButtonPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CommandButton(CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY), {})
        CommandButton(CommandButtonUi("Pausar", CommandKind.SECONDARY), {})
        CommandButton(CommandButtonUi("Cancelar misión", CommandKind.DESTRUCTIVE), {})
        CommandButton(CommandButtonUi("Reanudar", CommandKind.SECONDARY, CommandUiState.DISABLED, "No hay misión pausada"), {})
        CommandButton(CommandButtonUi("Regresar a base", CommandKind.PRIMARY, CommandUiState.PENDING, "Esperando confirmación del robot"), {})
        CommandButton(CommandButtonUi("Pausar", CommandKind.SECONDARY, CommandUiState.CONFIRMED), {})
        CommandButton(CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.REJECTED, "Rechazada: batería insuficiente"), {})
        CommandButton(CommandButtonUi("Regresar a base", CommandKind.PRIMARY, CommandUiState.NO_RESPONSE, "Sin respuesta: no se sabe si se ejecutó"), {})
    }
}
