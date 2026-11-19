package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.Trend
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

private fun Trend.glyph() = when (this) {
    Trend.RISING -> "▲ sube"
    Trend.STABLE -> "● estable"
    Trend.FALLING -> "▼ baja"
}

/** Lectura de telemetría. Con SIN DATOS el valor se tacha y se rotula como último valor conocido. */
@Composable
fun TelemetryReadout(ui: TelemetryReadoutUi, modifier: Modifier = Modifier) {
    val stale = ui.level == StatusLevel.STALE
    val valueColor = if (stale) RondaColors.stateStale else RondaColors.bone100
    val desc = buildString {
        append("${ui.label}: ${ui.value} ${ui.unit}. Estado ${ui.level.spokenName()}. ${ui.age}.")
        if (stale) append(" Último valor conocido, no vigente.")
        ui.trend?.let { append(" Tendencia ${it.glyph()}.") }
        ui.detail?.let { append(" $it.") }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateSymbol(ui.level, size = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(ui.label.uppercase(), style = RondaType.rotuloChico, color = RondaColors.bone400)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    ui.value, style = RondaType.datoGrande, color = valueColor,
                    textDecoration = if (stale) TextDecoration.LineThrough else null,
                )
                if (ui.unit.isNotEmpty()) Text(ui.unit, style = RondaType.datoNormal, color = RondaColors.bone400)
            }
            ui.detail?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(ui.age, style = RondaType.datoChico, color = if (stale) RondaColors.stateWarn else RondaColors.bone400)
            if (stale) Text("ÚLTIMO VALOR", style = RondaType.rotuloChico, color = RondaColors.stateStale)
            ui.trend?.let { Text(it.glyph(), style = RondaType.datoChico, color = RondaColors.bone400) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun TelemetryReadoutPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp)) {
        TelemetryReadout(TelemetryReadoutUi("Batería", "62", "%", StatusLevel.OK, Trend.FALLING, "hace 0,4 s", "Alcanza para volver: sí"))
        TelemetryReadout(TelemetryReadoutUi("Batería", "24", "%", StatusLevel.WARNING, Trend.FALLING, "hace 0,5 s", "Alcanza para volver: sí"))
        TelemetryReadout(TelemetryReadoutUi("Sala técnica", "33", "°C", StatusLevel.ERROR, Trend.RISING, "hace 0,5 s", "Sensor externo"))
        TelemetryReadout(TelemetryReadoutUi("Salidas libres", "2/2", "", StatusLevel.OK, null, "hace 0,4 s"))
        TelemetryReadout(TelemetryReadoutUi("Batería", "62", "%", StatusLevel.STALE, null, "hace 12 s"))
    }
}
