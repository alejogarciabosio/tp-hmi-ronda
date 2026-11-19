package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.theme.MinTouchTarget
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Encabezado de las pantallas secundarias: volver, título y un dato a la derecha (antigüedad, modo). */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Volver" },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", style = RondaType.pantallaTitulo, color = RondaColors.bone100)
            }
        } else {
            Box(Modifier.padding(start = 12.dp))
        }
        Text(
            title, style = RondaType.pantallaTitulo, color = RondaColors.bone100,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.let {
            val clickable = if (onTrailingClick != null) {
                Modifier.heightIn(min = MinTouchTarget).widthIn(min = MinTouchTarget).clickable(role = Role.Button, onClick = onTrailingClick).padding(horizontal = 8.dp)
            } else Modifier
            Box(clickable, contentAlignment = Alignment.Center) {
                Text(it, style = RondaType.rotuloChico, color = RondaColors.bone400)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun ScreenHeaderPreview() = PreviewSurface { ScreenHeader("Telemetría", {}, "Actualizado hace 0,4 s") }
