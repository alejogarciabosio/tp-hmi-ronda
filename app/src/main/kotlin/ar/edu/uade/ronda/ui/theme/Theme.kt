package ar.edu.uade.ronda.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightScheme = darkColorScheme(
    primary = RondaColors.signalAmber,
    onPrimary = RondaColors.onSignal,
    secondary = RondaColors.bone100,
    onSecondary = RondaColors.ink950,
    background = RondaColors.ink950,
    onBackground = RondaColors.bone100,
    surface = RondaColors.ink900,
    onSurface = RondaColors.bone100,
    surfaceVariant = RondaColors.ink850,
    onSurfaceVariant = RondaColors.bone400,
    outline = RondaColors.ink700,
    outlineVariant = RondaColors.ink700,
    error = RondaColors.stateError,
    onError = RondaColors.ink950,
    scrim = RondaColors.scrim,
)

/** Tema noche: es el principal. El tema día del Figma es solo muestra y no se implementa. */
@Composable
fun RondaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightScheme,
        typography = MaterialTypography,
        shapes = MaterialShapes,
        content = content,
    )
}
