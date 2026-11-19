package ar.edu.uade.ronda.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object RondaSpacing {
    val s0 = 0.dp
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
}

object RondaShapes {
    val r0 = RoundedCornerShape(0.dp)
    val r2 = RoundedCornerShape(2.dp)
    val r4 = RoundedCornerShape(4.dp)
}

/** Radio por defecto de todo: 2 dp. */
internal val MaterialShapes = Shapes(
    extraSmall = RondaShapes.r2,
    small = RondaShapes.r2,
    medium = RondaShapes.r2,
    large = RondaShapes.r4,
    extraLarge = RondaShapes.r4,
)

val MinTouchTarget = 48.dp
val RuleWidth = 1.dp
