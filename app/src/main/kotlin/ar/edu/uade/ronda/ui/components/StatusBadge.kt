package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import ar.edu.uade.ronda.ui.model.StatusBadgeUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType

/** Insignia de estado: símbolo + texto + color. No es interactiva. */
@Composable
fun StatusBadge(ui: StatusBadgeUi, modifier: Modifier = Modifier, large: Boolean = false) {
    val color = ui.level.color()
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = "Estado ${ui.level.spokenName()}: ${ui.label}" }
            .border(BorderStroke(1.dp, color), RondaShapes.r2)
            .heightIn(min = if (large) 32.dp else 24.dp)
            .padding(horizontal = if (large) 12.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StateSymbol(ui.level, size = if (large) 18.dp else 14.dp)
        Text(
            ui.label.uppercase(),
            style = if (large) RondaType.rotuloSeccion else RondaType.rotuloChico,
            color = color,
            textDecoration = if (ui.level == StatusLevel.DISABLED) TextDecoration.LineThrough else null,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F)
@Composable
private fun StatusBadgePreviewSmall() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBadge(StatusBadgeUi(StatusLevel.OK, "Normal"))
        StatusBadge(StatusBadgeUi(StatusLevel.WARNING, "Batería baja"))
        StatusBadge(StatusBadgeUi(StatusLevel.ERROR, "Falla"))
        StatusBadge(StatusBadgeUi(StatusLevel.STALE, "Sin datos"))
        StatusBadge(StatusBadgeUi(StatusLevel.DISABLED, "No disponible"))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F)
@Composable
private fun StatusBadgePreviewLarge() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusLevel.entries.forEach { StatusBadge(StatusBadgeUi(it, it.spokenName()), large = true) }
    }
}
