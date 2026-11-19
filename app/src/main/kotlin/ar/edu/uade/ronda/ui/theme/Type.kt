package ar.edu.uade.ronda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ar.edu.uade.ronda.R

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

@OptIn(ExperimentalTextApi::class)
private fun plexSans(weight: Int) = Font(
    R.font.ibm_plex_sans_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(100f)),
)

val ArchivoFamily = FontFamily(archivo(600, 100f), archivo(700, 100f), archivo(800, 100f), archivo(900, 100f))

/** Archivo Narrow se obtiene con el eje de ancho de Archivo (wdth 75). */
val ArchivoNarrowFamily = FontFamily(archivo(500, 75f), archivo(600, 75f))

val PlexSansFamily = FontFamily(plexSans(400), plexSans(600))

val PlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Estilos de texto de design/FIGMA.md. Las mayúsculas se aplican en el componente (`.uppercase()`). */
object RondaType {
    val estadoTitulo = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.02.em)
    val pantallaTitulo = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
    val rotuloSeccion = TextStyle(fontFamily = ArchivoNarrowFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em)
    val rotuloChico = TextStyle(fontFamily = ArchivoNarrowFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em)
    val botonEtiqueta = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.04.em)
    val datoGrande = TextStyle(fontFamily = PlexMonoFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum")
    val datoNormal = TextStyle(fontFamily = PlexMonoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
    val datoChico = TextStyle(fontFamily = PlexMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")
    val textoCuerpo = TextStyle(fontFamily = PlexSansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val textoEnfasis = TextStyle(fontFamily = PlexSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    val textoNota = TextStyle(fontFamily = PlexSansFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}

internal val MaterialTypography = Typography(
    displaySmall = RondaType.estadoTitulo,
    titleLarge = RondaType.pantallaTitulo,
    titleMedium = RondaType.textoEnfasis,
    labelLarge = RondaType.botonEtiqueta,
    labelMedium = RondaType.rotuloSeccion,
    labelSmall = RondaType.rotuloChico,
    bodyLarge = RondaType.textoCuerpo,
    bodyMedium = RondaType.textoCuerpo,
    bodySmall = RondaType.textoNota,
)
