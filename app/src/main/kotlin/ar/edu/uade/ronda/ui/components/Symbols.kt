package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding

fun StatusLevel.color(): Color = when (this) {
    StatusLevel.OK -> RondaColors.stateOk
    StatusLevel.WARNING -> RondaColors.stateWarn
    StatusLevel.ERROR -> RondaColors.stateError
    StatusLevel.STALE -> RondaColors.stateStale
    StatusLevel.DISABLED -> RondaColors.stateStale
}

fun StatusLevel.spokenName(): String = when (this) {
    StatusLevel.OK -> "correcto"
    StatusLevel.WARNING -> "advertencia"
    StatusLevel.ERROR -> "error"
    StatusLevel.STALE -> "sin datos"
    StatusLevel.DISABLED -> "deshabilitado"
}

/** Símbolo de estado: círculo lleno, triángulo, rombo, círculo punteado o círculo tachado. */
@Composable
fun StateSymbol(level: StatusLevel, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val color = level.color()
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.12f
        when (level) {
            StatusLevel.OK -> drawCircle(color, radius = w / 2f)
            StatusLevel.WARNING -> drawPath(
                Path().apply {
                    moveTo(w / 2f, 0f); lineTo(w, w); lineTo(0f, w); close()
                },
                color,
            )
            StatusLevel.ERROR -> drawPath(
                Path().apply {
                    moveTo(w / 2f, 0f); lineTo(w, w / 2f); lineTo(w / 2f, w); lineTo(0f, w / 2f); close()
                },
                color,
            )
            StatusLevel.STALE -> drawCircle(
                color, radius = w / 2f - stroke / 2f,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.18f, w * 0.14f))),
            )
            StatusLevel.DISABLED -> {
                drawCircle(color, radius = w / 2f - stroke / 2f, style = Stroke(stroke))
                drawLine(color, Offset(w * 0.15f, w * 0.85f), Offset(w * 0.85f, w * 0.15f), stroke)
            }
        }
    }
}

/** Franjas de señalización: diagonales a 45°, 8 de trazo cada 16, ámbar. */
@Composable
fun HazardStripes(modifier: Modifier = Modifier, color: Color = RondaColors.signalAmber) {
    Canvas(modifier) {
        val step = 16.dp.toPx()
        val stroke = 8.dp.toPx()
        clipRect {
            var x = -size.height
            while (x < size.width) {
                drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
                x += step
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F)
@Composable
private fun SymbolsPreview() = RondaTheme {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusLevel.entries.forEach { StateSymbol(it, size = 24.dp) }
        HazardStripes(Modifier.size(64.dp, 16.dp))
    }
}
