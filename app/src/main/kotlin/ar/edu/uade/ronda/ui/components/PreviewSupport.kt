package ar.edu.uade.ronda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.edu.uade.ronda.ui.model.*
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaTheme

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) = RondaTheme {
    Box(Modifier.background(RondaColors.ink950)) { content() }
}

internal object Sample {
    val checkpoints = listOf(
        CheckpointUi(1, "Hall de acceso", CheckpointState.VERIFIED, "23:41", 0, 0.20f, 0.80f),
        CheckpointUi(2, "Salida de emergencia PB", CheckpointState.VERIFIED, "23:47", 0, 0.80f, 0.80f),
        CheckpointUi(3, "Escalera E", CheckpointState.CURRENT, null, 0, 0.80f, 0.30f),
        CheckpointUi(4, "Pasillo 1°A", CheckpointState.PENDING, null, 1, 0.50f, 0.50f),
        CheckpointUi(5, "Sala técnica", CheckpointState.PENDING, null, 1, 0.20f, 0.30f),
        CheckpointUi(6, "Salida de emergencia 1°", CheckpointState.PENDING, null, 1, 0.80f, 0.70f),
        CheckpointUi(7, "Aula 12", CheckpointState.PENDING, null, 1, 0.30f, 0.75f),
        CheckpointUi(8, "Vuelta a base", CheckpointState.PENDING, null, 0, 0.20f, 0.30f),
    )
    val planPb = FloorPlanUi(0, "PB", checkpoints, RobotPoseUi(0, 0.70f, 0.55f, 45f))
    val plan1 = FloorPlanUi(1, "1°", checkpoints, RobotPoseUi(1, 0.45f, 0.50f, 180f))
}
