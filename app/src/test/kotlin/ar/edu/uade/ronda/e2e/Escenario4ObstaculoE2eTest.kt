package ar.edu.uade.ronda.e2e

import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import org.junit.Test

/** Escenario 4: obstáculo en el pasillo antes del PC4. */
class Escenario4ObstaculoE2eTest : E2eTest() {

    private val hoja = "EL ROBOT ESPERA TU DECISIÓN"

    private fun hastaLaDecision() {
        prepararEIniciar("Obstáculo en el pasillo")
        esperarRobot("decisión por obstáculo", maxMs = 900_000) { incidenteAbierto() }
        pasar(1_000)
        assertVisible(hoja)
        assertVisible("Obstáculo en el pasillo · PC4")
        assertVisible("Foto de la cámara frontal del robot")
        assertVisible("Esperar 30 s")
        assertVisible("Saltar el PC4")
        assertVisible("Regresar a la base")
    }

    @Test fun saltarElPc4CierraLaHojaYLaRondaSigue() {
        hastaLaDecision()
        tocar("Saltar el PC4")
        assertNoVisible(hoja)
        pasar(1_000)
        // El PC4 queda salteado y la ronda sigue hacia el PC5.
        assertVisible("Punto de control 4, Pasillo 1°A: salteado")
        assertVisible("RONDA COMPLETA · PC 5/8")
        assertVisible("EN RONDA")

        // La hoja no vuelve: ni con el tiempo ni al ir y volver de otra pestaña.
        pasar(5_000)
        assertNoVisible(hoja)
        pestana("Alertas")
        pestana("Supervisión")
        assertNoVisible(hoja)
        assertNoVisible("Decidir cómo seguir")

        esperarRobot("PC5 verificado") { verificados() >= 4 }
        assertVisible("Punto de control 5, Sala técnica 1°: verificado")
        assertNoVisible(hoja)
    }

    @Test fun detencionConLaHojaAbiertaYAlReanudarLaDecisionVuelve() {
        hastaLaDecision()
        // La hoja tapa la barra: el operador la cierra con Atrás y mantiene la detención operativa.
        atras()
        assertNoVisible(hoja)
        assertVisible("Decidir cómo seguir")
        mantener(OperationalStopTexts.LABEL)
        esperarRobot("detenido") { estado() == OperationalState.DETENIDO }
        assertVisible("DETENIDO")
        assertNoVisible(hoja)

        tocar("Reanudar")
        esperarRobot("vuelve a pedir la decisión") { estado() == OperationalState.REQUIERE_DECISION }
        pasar(1_000)
        assertVisible("REQUIERE DECISIÓN")
        assertVisible(hoja)
        assertVisible("Saltar el PC4")
        tocar("Saltar el PC4")
        assertNoVisible(hoja)
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        pasar(2_000)
        assertNoVisible(hoja)
    }
}
