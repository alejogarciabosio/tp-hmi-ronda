package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.model.CheckpointState
import ar.edu.uade.ronda.ui.model.FloorPlanUi
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

/**
 * Plano de líneas de un piso: muros, ruta punteada, puntos de control numerados y el robot como chevron
 * orientado. Los PC en otro piso no se dibujan. Solo hay planos de PB (0) y 1° (1).
 */
@Composable
fun FloorPlanCanvas(ui: FloorPlanUi, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(
        fontFamily = RondaType.datoChico.fontFamily, fontSize = RondaType.rotuloChico.fontSize,
        color = RondaColors.bone100,
    )
    val here = ui.checkpoints.filter { it.floor == ui.floor }
    val verified = ui.checkpoints.count { it.state == CheckpointState.VERIFIED || it.state == CheckpointState.WITH_ISSUE }
    val robotText = ui.robot?.let {
        if (it.floor == ui.floor) "Robot en este piso, rumbo ${it.headingDeg.toInt()} grados." else "El robot está en otro piso."
    } ?: "Sin posición del robot."
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .semantics {
                contentDescription = "Plano del piso ${ui.floorLabel}. $robotText Puntos de control: $verified de ${ui.checkpoints.size} verificados."
            },
    ) {
        val w = size.width
        val h = size.height
        fun px(x: Float, y: Float) = Offset(x * w, y * h)
        val wall = 1.5.dp.toPx()

        drawRect(RondaColors.bone400, px(0.05f, 0.08f), Size(w * 0.9f, h * 0.84f), style = Stroke(wall))
        // Tabiques interiores y corredor central.
        val tab = RondaColors.ink700
        drawLine(tab, px(0.05f, 0.42f), px(0.62f, 0.42f), wall)
        drawLine(tab, px(0.05f, 0.62f), px(0.62f, 0.62f), wall)
        drawLine(tab, px(0.62f, 0.08f), px(0.62f, 0.42f), wall)
        drawLine(tab, px(0.62f, 0.62f), px(0.62f, 0.92f), wall)
        // Escaleras E (derecha) y O (izquierda).
        stair(px(0.86f, 0.46f), w, RondaColors.bone400)
        stair(px(0.10f, 0.46f), w, RondaColors.bone400)
        drawText(measurer, "E", px(0.88f, 0.60f), label)
        drawText(measurer, "O", px(0.12f, 0.60f), label)
        if (ui.floor == 0) {
            drawRect(RondaColors.bone100, px(0.07f, 0.84f), Size(14.dp.toPx(), 14.dp.toPx()), style = Stroke(wall))
            drawText(measurer, "BASE", px(0.07f, 0.74f), label)
        }

        // Ruta punteada entre PC consecutivos del mismo piso.
        val route = Path()
        here.forEachIndexed { i, c -> if (i == 0) route.moveTo(c.x * w, c.y * h) else route.lineTo(c.x * w, c.y * h) }
        drawPath(route, RondaColors.bone400, style = Stroke(wall, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))

        here.forEach { c ->
            val p = px(c.x, c.y)
            val r = 9.dp.toPx()
            when (c.state) {
                CheckpointState.VERIFIED -> drawCircle(RondaColors.stateOk, r, p)
                CheckpointState.WITH_ISSUE -> drawCircle(RondaColors.stateWarn, r, p)
                CheckpointState.CURRENT -> {
                    drawCircle(RondaColors.signalAmber, r + 3.dp.toPx(), p, style = Stroke(wall))
                    drawCircle(RondaColors.signalAmber, r, p)
                }
                CheckpointState.PENDING -> drawCircle(RondaColors.bone400, r, p, style = Stroke(wall))
                CheckpointState.SKIPPED -> {
                    drawCircle(RondaColors.stateStale, r, p, style = Stroke(wall))
                    drawLine(RondaColors.stateStale, p - Offset(r, -r), p + Offset(r, -r), wall)
                }
            }
            val filled = c.state != CheckpointState.PENDING && c.state != CheckpointState.SKIPPED
            val m = measurer.measure(c.index.toString(), label.copy(color = if (filled) RondaColors.onSignal else RondaColors.bone100))
            drawText(m, topLeft = p - Offset(m.size.width / 2f, m.size.height / 2f))
        }

        ui.robot?.takeIf { it.floor == ui.floor }?.let { robot ->
            val c = px(robot.x, robot.y)
            val s = 11.dp.toPx()
            // Chevron apuntando hacia arriba con rumbo 0.
            rotate(robot.headingDeg, c) {
                val chevron = Path().apply {
                    moveTo(c.x, c.y - s); lineTo(c.x + s * 0.8f, c.y + s * 0.8f)
                    lineTo(c.x, c.y + s * 0.35f); lineTo(c.x - s * 0.8f, c.y + s * 0.8f); close()
                }
                drawPath(chevron, RondaColors.signalAmber)
                drawPath(chevron, RondaColors.ink950, style = Stroke(1.dp.toPx()))
            }
        }
        drawText(measurer, ui.floorLabel, Offset(w * 0.06f, h * 0.10f), label.copy(color = RondaColors.bone400))
    }
}

private fun DrawScope.stair(topLeft: Offset, w: Float, color: androidx.compose.ui.graphics.Color) {
    val step = w * 0.012f
    repeat(5) { i -> drawLine(color, topLeft + Offset(0f, i * step * 2.2f), topLeft + Offset(w * 0.07f, i * step * 2.2f), 1.5.dp.toPx()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412)
@Composable
private fun FloorPlanPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FloorPlanCanvas(Sample.planPb)
        FloorPlanCanvas(Sample.plan1)
        FloorPlanCanvas(Sample.planPb.copy(robot = null))
    }
}
