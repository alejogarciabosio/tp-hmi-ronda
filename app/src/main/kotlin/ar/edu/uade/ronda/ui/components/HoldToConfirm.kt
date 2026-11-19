package ar.edu.uade.ronda.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaShapes
import ar.edu.uade.ronda.ui.theme.RondaType
import kotlinx.coroutines.launch

object OperationalStopTexts {
    const val LABEL = "Detención operativa (software)"
    const val NOTE = "No reemplaza la parada física del robot."
}

/** Aspecto sin estado: la barra se llena con franjas de señalización según [progress] (0..1). */
@Composable
fun HoldToConfirmContent(
    label: String,
    progress: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = if (enabled) RondaColors.stateError else RondaColors.ink700
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RondaShapes.r2)
            .border(BorderStroke(1.dp, accent), RondaShapes.r2),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize()) {
            HazardStripes(
                Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)),
                color = RondaColors.stateError.copy(alpha = 0.55f),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = RondaType.botonEtiqueta, color = if (enabled) RondaColors.bone100 else RondaColors.stateStale, textAlign = TextAlign.Center)
            Text(
                if (progress > 0f) "Seguí manteniendo…" else "Mantené presionado 1 s",
                style = RondaType.textoNota, color = RondaColors.bone400,
            )
        }
    }
}

/** Comando destructivo: solo se confirma manteniendo el toque [holdMillis]. Soltar antes cancela. */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 1000,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    HoldToConfirmContent(
        label, progress,
        modifier
            .semantics {
                contentDescription = "$label. Mantené presionado un segundo para confirmar."
                if (enabled) onLongClick(label = "Confirmar $label") { onConfirmed(); true }
            }
            .pointerInput(enabled, holdMillis) {
                detectTapGestures(onPress = {
                    if (!enabled) return@detectTapGestures
                    val job = scope.launch {
                        val anim = Animatable(0f)
                        anim.animateTo(1f, tween(holdMillis, easing = LinearEasing)) { progress = value }
                        onConfirmed()
                        progress = 0f
                    }
                    tryAwaitRelease()
                    job.cancel()
                    progress = 0f
                })
            },
        enabled,
    )
}

/** Detención operativa (software), siempre con la aclaración de que no reemplaza la parada física. */
@Composable
fun OperationalStopButton(onConfirmed: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HoldToConfirm(OperationalStopTexts.LABEL, onConfirmed, enabled = enabled)
        Text(OperationalStopTexts.NOTE, style = RondaType.textoNota, color = RondaColors.bone400)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 360)
@Composable
private fun HoldToConfirmPreview() = PreviewSurface {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HoldToConfirmContent("Cancelar misión", 0f)
        HoldToConfirmContent("Cancelar misión", 0.5f)
        HoldToConfirmContent("Cancelar misión", 1f)
        HoldToConfirmContent("Cancelar misión", 0f, enabled = false)
        OperationalStopButton({})
    }
}
