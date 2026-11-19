package ar.edu.uade.ronda.ui.components

import ar.edu.uade.ronda.ui.theme.RondaShapes

import androidx.compose.foundation.border

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Franja de estado fija: estado operativo grande, conexión con antigüedad y "¿Tenés que actuar?". */
@Composable
fun StateStrip(ui: StateStripUi, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription =
                    (ui.simulationBadge?.let { "$it, tiempo acelerado. " } ?: "") +
                    "Estado ${ui.stateLabel}. ${ui.connectionLabel}. ¿Tenés que actuar? ${ui.actionText}"
            },
    ) {
        Column(
            Modifier.fillMaxWidth().background(RondaColors.ink900).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StateSymbol(ui.level, size = 24.dp)
                Text(ui.stateLabel.uppercase(), style = RondaType.estadoTitulo, color = RondaColors.bone100, modifier = Modifier.weight(1f))
                ui.simulationBadge?.let {
                    Text(
                        it, style = RondaType.datoChico, color = RondaColors.signalAmber,
                        modifier = Modifier.border(1.dp, RondaColors.signalAmber, RondaShapes.r2).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateSymbol(ui.connectionLevel, size = 12.dp)
                Text(ui.connectionLabel.uppercase(), style = RondaType.datoChico, color = ui.connectionLevel.color())
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (ui.needsAction) RondaColors.ink850 else RondaColors.ink900)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("¿TENÉS QUE ACTUAR?", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
            Text(
                ui.actionText, style = RondaType.textoEnfasis,
                color = if (ui.needsAction) RondaColors.signalAmber else RondaColors.bone100,
                modifier = Modifier.weight(1f),
            )
        }
        if (ui.needsAction) HazardStripes(Modifier.fillMaxWidth().height(6.dp))
        else Box(Modifier.fillMaxWidth().height(1.dp).background(RondaColors.ink700))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun StateStripPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StateStrip(StateStripUi("En ronda", StatusLevel.OK, false, "No", "Conectado · 0,4 s", StatusLevel.OK))
        StateStrip(StateStripUi("Requiere decisión", StatusLevel.WARNING, true, "Sí: decidí cómo seguir en PC4", "Conectado · 0,5 s", StatusLevel.OK))
        StateStrip(StateStripUi("Detenido", StatusLevel.ERROR, true, "Sí: reanudar o regresar a base", "Sin datos · hace 12 s", StatusLevel.STALE))
        StateStrip(StateStripUi("En base", StatusLevel.DISABLED, false, "No", "Conectado · 0,4 s", StatusLevel.OK))
    }
}
