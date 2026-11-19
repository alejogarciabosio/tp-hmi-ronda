package ar.edu.uade.ronda.e2e

import ar.edu.uade.ronda.core.model.OperationalState
import org.junit.Assert.assertTrue
import org.junit.Test

/** Escenario 1 de la demo: la ronda completa de punta a punta, desde Selección hasta el Historial. */
class Escenario1MisionNormalE2eTest : E2eTest() {

    @Test fun rondaCompletaDesdeSeleccionHastaHistorial() {
        tomarControl()
        assertVisible("EN BASE")

        // Antes del escenario, el panel de simulación pasa a ×8 y Supervisión lo avisa.
        enPanel { elegirVelocidad8() }
        assertVisible("SIMULACIÓN ×8")

        pestana("Misiones")
        assertVisible("ANTES DE SALIR · RONDA COMPLETA")
        assertVisible("Batería suficiente")
        assertVisible("Conexión estable")
        assertVisible("Sin alertas críticas activas")
        assertVisible("Tenés el control del robot")
        assertNoVisible("No tenés el control")
        assertHabilitado("Iniciar ronda")

        // La orden pasa por pendiente y confirmada antes de verificarse.
        tocar("Iniciar ronda")
        assertVisible("PENDIENTE")
        val vistos = mutableSetOf<String>()
        var t = 0L
        while ("VERIFICADA" !in vistos && t < 5_000) {
            advance(100); t += 100
            settle()
            textos().forEach { s -> listOf("PENDIENTE", "CONFIRMADA", "VERIFICADA").filter { s.contains(it) }.forEach { vistos += it } }
        }
        assertTrue("La orden tiene que verse CONFIRMADA antes de VERIFICADA: $vistos", "CONFIRMADA" in vistos)
        assertTrue("La orden termina VERIFICADA: $vistos", "VERIFICADA" in vistos)

        // Supervisión avanza punto por punto.
        pestana("Supervisión")
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        assertVisible("EN RONDA")
        assertVisible("RONDA COMPLETA · PC 1/8")
        esperarRobot("3 puntos verificados") { verificados() >= 3 }
        assertVisible("RONDA COMPLETA · PC 4/8")
        esperarRobot("6 puntos verificados") { verificados() >= 6 }
        assertVisible("RONDA COMPLETA · PC 7/8")

        // Termina COMPLETADA y vuelve sola a la base.
        esperarRobot("ronda completada") { estado() == OperationalState.COMPLETADA }
        assertVisible("COMPLETADA")
        assertVisible("PC 8/8")
        esperarRobot("regreso a la base") { estado() == OperationalState.REGRESANDO }
        assertVisible("REGRESANDO")
        esperarRobot("en base", maxMs = 300_000) { estado() == OperationalState.EN_BASE }
        assertVisible("EN BASE")

        // Historial: la ronda quedó completada, no interrumpida.
        pestana("Historial")
        assertVisible("Ronda completa")
        assertVisible("Completada")
        assertVisible("8/8 PC")
        assertNoVisible("Interrumpida")
    }
}
