package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

/**
 * Bloque destacado arriba de la pantalla: lo que el operador tiene que hacer ahora, con
 * símbolo y texto (nunca solo color). [action] es la orden que resuelve la situación, si la hay.
 */
@Composable
fun ActionCallout(
    text: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(RondaColors.ink850, RondaShapes.r2)
            .border(2.dp, level.color(), RondaShapes.r2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StateSymbol(level, size = 20.dp)
            Text(text, style = RondaType.textoEnfasis, color = RondaColors.bone100, modifier = Modifier.weight(1f))
        }
        action?.invoke()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 360)
@Composable
private fun ActionCalloutPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCallout("Vigilante en camino · el robot espera tu orden para seguir", StatusLevel.WARNING)
        ActionCallout("Batería baja (24 %): volvé a la base mientras alcanza", StatusLevel.WARNING) {
            HoldToConfirm("Regresar a base", {}, primary = true)
        }
    }
}
