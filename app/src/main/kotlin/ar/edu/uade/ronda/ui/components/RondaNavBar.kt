package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Destinos de la barra inferior; el resto de las pantallas se alcanza desde ellas. */
val NavBarRoutes = listOf(Route.SUPERVISION, Route.MISIONES, Route.ALERTAS, Route.HISTORIAL)

private fun Route.tabLabel() = when (this) {
    Route.SUPERVISION -> "Supervisión"
    Route.MISIONES -> "Misiones"
    Route.ALERTAS -> "Alertas"
    Route.HISTORIAL -> "Historial"
    else -> title
}

/** La pestaña activa se marca con barra superior, color y texto en negrita: no solo color. */
@Composable
fun RondaNavBar(selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(RondaColors.ink900)) {
        NavBarRoutes.forEach { route ->
            val active = route == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(route) })
                    .semantics { contentDescription = route.tabLabel() + if (active) ", pestaña actual" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) RondaColors.signalAmber else RondaColors.ink900))
                // Alto mínimo fijo en vez de weight: la barra va en bottomBar y no tiene alto máximo acotado.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 53.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        route.tabLabel(),
                        style = if (active) RondaType.botonEtiqueta else RondaType.rotuloSeccion,
                        color = if (active) RondaColors.signalAmber else RondaColors.bone400,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun NavBarSupervisionPreview() = PreviewSurface { RondaNavBar(Route.SUPERVISION, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun NavBarHistorialPreview() = PreviewSurface { RondaNavBar(Route.HISTORIAL, {}) }
