package ar.edu.uade.ronda.ui.a11y

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ar.edu.uade.ronda.ui.theme.RondaColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contraste WCAG 2.1 de los pares de color que usa la app sobre el fondo de noche. */
class ContrasteTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun assertAtLeast(min: Double, fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        assertTrue("$what: ${"%.2f".format(r)}:1 < $min:1", r >= min)
    }

    @Test fun textoNormalCumpleAA() {
        // 4,5:1 para texto de cuerpo (WCAG 1.4.3).
        assertAtLeast(4.5, RondaColors.bone100, RondaColors.ink950, "texto principal")
        assertAtLeast(4.5, RondaColors.bone400, RondaColors.ink950, "texto secundario")
        assertAtLeast(4.5, RondaColors.bone400, RondaColors.ink900, "texto secundario sobre panel")
        assertAtLeast(4.5, RondaColors.bone400, RondaColors.ink850, "texto secundario sobre tarjeta elegida")
        assertAtLeast(4.5, RondaColors.onSignal, RondaColors.signalAmber, "etiqueta de botón primario")
    }

    @Test fun coloresDeEstadoCumplenAA() {
        assertAtLeast(4.5, RondaColors.signalAmber, RondaColors.ink950, "advertencia")
        assertAtLeast(4.5, RondaColors.stateError, RondaColors.ink950, "error")
        assertAtLeast(4.5, RondaColors.stateOk, RondaColors.ink950, "normal")
    }

    @Test fun datoViejoYDeshabilitadoSuperanElMinimoNoTextual() {
        // Gris a propósito (dato no vigente / control deshabilitado). Se exige 3:1 y no 4,5:1 porque
        // nunca va solo: lo acompañan el símbolo de estado, el tachado y el texto del motivo.
        assertAtLeast(3.0, RondaColors.stateStale, RondaColors.ink950, "dato viejo / deshabilitado")
    }
}
