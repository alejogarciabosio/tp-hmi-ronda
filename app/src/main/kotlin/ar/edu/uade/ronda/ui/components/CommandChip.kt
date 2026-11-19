package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

private data class ChipLook(val text: String, val level: StatusLevel, val spoken: String)

private fun CommandChipStatus.look() = when (this) {
    CommandChipStatus.PENDING -> ChipLook("PENDIENTE", StatusLevel.STALE, "Pendiente: enviada, sin confirmación")
    CommandChipStatus.CONFIRMED -> ChipLook("CONFIRMADA", StatusLevel.OK, "Confirmada por el robot, todavía no ejecutada")
    CommandChipStatus.VERIFIED -> ChipLook("VERIFICADA", StatusLevel.OK, "Verificada: la telemetría refleja el efecto")
    CommandChipStatus.REJECTED -> ChipLook("RECHAZADA", StatusLevel.ERROR, "Rechazada por el robot")
    CommandChipStatus.NO_RESPONSE -> ChipLook("SIN RESPUESTA", StatusLevel.WARNING, "Sin respuesta: no se sabe si se ejecutó")
}

/** Estado de un comando enviado. "Enviada" no es "ejecutada": lo dice el texto. */
@Composable
fun CommandChip(ui: CommandChipUi, modifier: Modifier = Modifier) {
    val look = if (ui.withoutAck && ui.status == CommandChipStatus.VERIFIED) {
        ChipLook("VERIFICADA · SIN CONFIRMACIÓN", StatusLevel.OK, "Verificada por telemetría (sin confirmación)")
    } else ui.status.look()
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = look.spoken }
            .border(BorderStroke(1.dp, look.level.color()), RondaShapes.r2)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StateSymbol(look.level, size = 12.dp)
        Text(look.text, style = RondaType.rotuloChico, color = look.level.color(), maxLines = 1, softWrap = false)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F)
@Composable
private fun CommandChipPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandChipStatus.entries.forEach { CommandChip(CommandChipUi(it)) }
        CommandChip(CommandChipUi(CommandChipStatus.VERIFIED, withoutAck = true))
    }
}
