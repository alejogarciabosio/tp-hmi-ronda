package ar.edu.uade.ronda.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private fun holdSeconds(ms: Int): String {
    val s = ms / 1000.0
    return if (ms % 1000 == 0) "${ms / 1000} s" else "${"%.1f".format(java.util.Locale.forLanguageTag("es-AR"), s)} s"
}

/** Aspecto sin estado: la barra se llena con franjas de señalización según [progress] (0..1). */
@Composable
fun HoldToConfirmContent(
    label: String,
    progress: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 1000,
    disabledReason: String? = null,
    primary: Boolean = false,
) {
    // Acción principal: relleno ámbar con texto oscuro, como el botón primario.
    val filled = primary && enabled
    val accent = when {
        !enabled -> RondaColors.ink700
        primary -> RondaColors.signalAmber
        else -> RondaColors.stateError
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RondaShapes.r2)
            .background(if (filled) RondaColors.signalAmber else Color.Transparent)
            .border(BorderStroke(if (primary) 2.dp else 1.dp, accent), RondaShapes.r2),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize()) {
            HazardStripes(
                Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)),
                color = if (filled) RondaColors.onSignal.copy(alpha = 0.35f) else RondaColors.stateError.copy(alpha = 0.55f),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label.uppercase(), style = RondaType.botonEtiqueta, textAlign = TextAlign.Center,
                color = when {
                    !enabled -> RondaColors.stateStale
                    filled -> RondaColors.onSignal
                    else -> RondaColors.bone100
                },
            )
            Text(
                when {
                    !enabled -> disabledReason ?: "No disponible"
                    progress > 0f -> "Seguí manteniendo…"
                    else -> "Mantené presionado ${holdSeconds(holdMillis)}"
                },
                style = RondaType.textoNota, color = if (filled) RondaColors.onSignal else RondaColors.bone400,
            )
        }
    }
}

/**
 * Comando crítico: solo se confirma manteniendo el toque [holdMillis]. Soltar antes cancela.
 * [primary] lo pinta como acción principal (p. ej. regresar a la base con batería baja).
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 1000,
    disabledReason: String? = null,
    primary: Boolean = false,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // El gesto vive más que una composición: siempre se llama a la última acción recibida.
    val confirm by rememberUpdatedState(onConfirmed)
    HoldToConfirmContent(
        label, progress,
        modifier
            .semantics {
                contentDescription = if (enabled) "$label. Mantené presionado ${holdSeconds(holdMillis)} para confirmar."
                else "$label. No disponible. ${disabledReason.orEmpty()}"
                if (enabled) onLongClick(label = "Confirmar $label") { confirm(); true }
            }
            .pointerInput(enabled, holdMillis) {
                detectTapGestures(onPress = {
                    if (!enabled) return@detectTapGestures
                    var released = false
                    val job = scope.launch {
                        val anim = Animatable(0f)
                        anim.animateTo(1f, tween(holdMillis, easing = LinearEasing)) { progress = value }
                        // Si el dedo se levantó o el gesto se canceló mientras terminaba, no se confirma.
                        if (!released) confirm()
                        progress = 0f
                    }
                    // finally: si el gesto se cancela (p. ej. cambia `enabled`), tryAwaitRelease lanza
                    // CancellationException y el llenado no debe seguir hasta confirmar solo.
                    try {
                        tryAwaitRelease()
                    } finally {
                        released = true
                        job.cancel()
                        progress = 0f
                    }
                })
            },
        enabled,
        holdMillis,
        disabledReason,
        primary,
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
        HoldToConfirmContent("Cancelar misión", 0f, enabled = false, disabledReason = "No hay misión en curso")
        HoldToConfirmContent("Regresar a base", 0f, primary = true)
        HoldToConfirmContent("Regresar a base", 0.5f, primary = true)
        OperationalStopButton({})
    }
}
