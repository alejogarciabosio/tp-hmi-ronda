package ar.edu.uade.ronda.ui.operacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.ronda.ui.components.HoldToConfirm
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.components.RondaNavBar
import ar.edu.uade.ronda.ui.components.StateSymbol
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.navigation.Route
import ar.edu.uade.ronda.ui.supervision.StopButtonUi
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Estado observado de la barra y la acción de detener, provistos una sola vez por el NavHost. */
class OperationalBarHandle(val ui: OperationalBarUi, val onStop: () -> Unit)

val LocalOperationalBar = compositionLocalOf<OperationalBarHandle?> { null }

/** Provee la barra a todas las pantallas operativas a partir del ViewModel compartido. */
@Composable
fun ProvideOperationalBar(viewModel: OperationalBarViewModel, content: @Composable () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalOperationalBar provides OperationalBarHandle(ui, viewModel::onOperationalStop), content = content)
}

/**
 * Detención operativa (software) fija y compacta, fuera del scroll, con su aclaración corta.
 * Arriba, si hay una alerta de persona activa, el indicador "persona cerca".
 */
@Composable
fun OperationalStopBar(ui: OperationalBarUi, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(RondaColors.ink900).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ui.personNearby?.let { text ->
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StateSymbol(StatusLevel.WARNING, size = 14.dp)
                Text(text, style = RondaType.textoEnfasis, color = RondaColors.signalAmber)
            }
        }
        HoldToConfirm(OperationalStopTexts.LABEL, onStop, enabled = ui.stop.enabled, disabledReason = ui.stop.reason)
        Text(OperationalStopTexts.NOTE, style = RondaType.textoNota, color = RondaColors.bone400)
    }
}

/** Barra fija de las pantallas operativas: detención operativa encima de la barra de pestañas. */
@Composable
fun OperationalBottomBar(selected: Route, onNavigate: (Route) -> Unit) {
    val handle = LocalOperationalBar.current
    if (handle != null) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(RondaColors.ink700))
        OperationalStopBar(handle.ui, handle.onStop)
    }
    RondaNavBar(selected, onNavigate, alertCount = handle?.ui?.unacknowledgedAlerts ?: 0)
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun OperationalStopBarPreview() = PreviewSurface {
    Column {
        OperationalStopBar(OperationalBarUi(StopButtonUi(true), personNearby = "Persona cerca del robot", unacknowledgedAlerts = 2), {})
        RondaNavBar(Route.SUPERVISION, {}, alertCount = 2)
    }
}
