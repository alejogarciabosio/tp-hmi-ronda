package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/** Lo que anuncia TalkBack: cambia solo al pasar de SIN DATOS a DESCONECTADO, nunca con los segundos. */
fun staleAnnouncement(ui: StaleBannerUi): String =
    if (ui.secondsWithoutData >= 10) "Desconectado. Lo que ves no es vigente." else "Sin datos del robot. Lo que ves no es vigente."

/**
 * Aviso de datos viejos: menos de 10 s "SIN DATOS", desde 10 s "DESCONECTADO", desde 30 s con la política declarada.
 * La región "Polite" es solo el título: si incluyera los segundos, TalkBack lo anunciaría cada segundo.
 */
@Composable
fun StaleDataBanner(ui: StaleBannerUi, modifier: Modifier = Modifier) {
    val disconnected = ui.secondsWithoutData >= 10
    val title = if (disconnected) "DESCONECTADO" else "SIN DATOS"
    val text = "Sin datos desde hace ${ui.secondsWithoutData} s. Lo que ves es lo último que se recibió, no es vigente."
    Column(
        modifier
            .fillMaxWidth()
            .background(RondaColors.ink850),
    ) {
        HazardStripes(Modifier.fillMaxWidth().height(6.dp))
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StateSymbol(if (disconnected) StatusLevel.ERROR else StatusLevel.STALE, size = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title, style = RondaType.rotuloSeccion, color = RondaColors.bone100,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = staleAnnouncement(ui)
                    },
                )
                // Con foco, TalkBack lee los segundos; no es región viva, así que no interrumpe cada segundo.
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text, style = RondaType.textoCuerpo, color = RondaColors.bone100)
                    ui.policyText?.let { Text(it, style = RondaType.textoNota, color = RondaColors.bone400) }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun StaleDataBannerPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StaleDataBanner(StaleBannerUi(4))
        StaleDataBanner(StaleBannerUi(12))
        StaleDataBanner(StaleBannerUi(30, "Política del robot sin enlace (supuesto declarado): se detiene y espera."))
    }
}
