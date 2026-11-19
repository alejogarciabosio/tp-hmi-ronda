package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.CheckpointState
import ar.edu.uade.ronda.ui.model.CheckpointUi
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

internal fun CheckpointState.text() = when (this) {
    CheckpointState.PENDING -> "pendiente"
    CheckpointState.CURRENT -> "en curso"
    CheckpointState.VERIFIED -> "verificado"
    CheckpointState.WITH_ISSUE -> "con observación"
    CheckpointState.SKIPPED -> "salteado"
}

@Composable
private fun CheckpointMark(state: CheckpointState) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val sw = 1.5.dp.toPx()
        when (state) {
            CheckpointState.PENDING -> drawCircle(RondaColors.bone400, w / 2 - sw, style = Stroke(sw))
            CheckpointState.CURRENT -> {
                drawCircle(RondaColors.signalAmber, w / 2 - sw, style = Stroke(sw))
                drawCircle(RondaColors.signalAmber, w / 4)
            }
            CheckpointState.VERIFIED -> {
                drawCircle(RondaColors.stateOk, w / 2)
                val tick = Path().apply { moveTo(w * 0.25f, w * 0.52f); lineTo(w * 0.43f, w * 0.7f); lineTo(w * 0.76f, w * 0.32f) }
                drawPath(tick, RondaColors.onSignal, style = Stroke(sw * 1.4f))
            }
            CheckpointState.WITH_ISSUE -> {
                val tri = Path().apply { moveTo(w / 2, 0f); lineTo(w, w); lineTo(0f, w); close() }
                drawPath(tri, RondaColors.stateWarn)
                drawLine(RondaColors.onSignal, Offset(w / 2, w * 0.4f), Offset(w / 2, w * 0.68f), sw * 1.4f)
            }
            CheckpointState.SKIPPED -> {
                drawCircle(RondaColors.stateStale, w / 2 - sw, style = Stroke(sw))
                drawLine(RondaColors.stateStale, Offset(w * 0.2f, w * 0.8f), Offset(w * 0.8f, w * 0.2f), sw)
            }
        }
    }
}

/** Planilla vertical de puntos de control con hora de verificación. El avance se cuenta en PC, nunca en porcentaje. */
@Composable
fun CheckpointRail(items: List<CheckpointUi>, modifier: Modifier = Modifier) {
    val verified = items.count { it.state == CheckpointState.VERIFIED || it.state == CheckpointState.WITH_ISSUE }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Puntos de control: $verified de ${items.size} verificados" },
    ) {
        items.forEach { cp ->
            val current = cp.state == CheckpointState.CURRENT
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (current) RondaColors.ink850 else RondaColors.ink950)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Punto de control ${cp.index}, ${cp.name}: ${cp.state.text()}" +
                            (cp.time?.let { ", a las $it" } ?: "")
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(cp.time ?: "--:--", style = RondaType.datoChico, color = RondaColors.bone400, modifier = Modifier.width(44.dp))
                CheckpointMark(cp.state)
                Column(Modifier.weight(1f)) {
                    Text(
                        "PC ${cp.index} · ${cp.name}",
                        style = if (current) RondaType.textoEnfasis else RondaType.textoCuerpo,
                        color = if (cp.state == CheckpointState.SKIPPED) RondaColors.stateStale else RondaColors.bone100,
                        textDecoration = if (cp.state == CheckpointState.SKIPPED) TextDecoration.LineThrough else null,
                    )
                    Text(cp.state.text().uppercase(), style = RondaType.rotuloChico, color = RondaColors.bone400)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(RondaColors.ink700))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun CheckpointRailPreview() = PreviewSurface {
    CheckpointRail(
        listOf(
            CheckpointUi(1, "Hall de acceso", CheckpointState.VERIFIED, "23:41"),
            CheckpointUi(2, "Salida PB", CheckpointState.WITH_ISSUE, "23:47"),
            CheckpointUi(3, "Escalera E", CheckpointState.CURRENT),
            CheckpointUi(4, "Pasillo 1°A", CheckpointState.SKIPPED),
            CheckpointUi(5, "Sala técnica", CheckpointState.PENDING),
        ),
    )
}
