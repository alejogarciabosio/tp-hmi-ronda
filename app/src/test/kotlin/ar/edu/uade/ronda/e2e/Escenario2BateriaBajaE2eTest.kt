package ar.edu.uade.ronda.e2e

import ar.edu.uade.ronda.core.model.OperationalState
import org.junit.Test

/** Escenario 2: batería baja, cargado desde el panel de simulación. */
class Escenario2BateriaBajaE2eTest : E2eTest() {

    @Test fun avisoDestacadoA25YRegresoManteniendoPresionadoA15() {
        prepararEIniciar("Batería baja")
        assertVisible("EN RONDA")
        assertNoVisible("volvé a la base")

        // A 25 % aparece arriba el aviso destacado con "Regresar a base" como acción principal.
        esperarEnPantalla("volvé a la base mientras alcanza", maxMs = 900_000)
        assertVisible(Regex("Batería baja \\(\\d+ %\\): volvé a la base mientras alcanza"))
        assertVisible("Regresar a base. Mantené presionado 1 s para confirmar.")
        assertVisible("Batería baja")

        // A 15 % el robot propone volver: una sola opción, que se confirma manteniendo presionado.
        esperarRobot("decisión de batería crítica", maxMs = 900_000) { incidenteAbierto() }
        pasar(1_000)
        assertVisible("EL ROBOT ESPERA TU DECISIÓN")
        assertVisible("Batería crítica")
        assertVisible("REQUIERE DECISIÓN")
        // Un toque corto no alcanza.
        mantener("Regresar a la base", ms = 300)
        assertVisible("EL ROBOT ESPERA TU DECISIÓN")
        mantener("Regresar a la base")
        assertNoVisible("EL ROBOT ESPERA TU DECISIÓN")
        esperarRobot("regresando") { estado() == OperationalState.REGRESANDO }
        assertVisible("REGRESANDO")
        pasar(3_000)
        assertNoVisible("EL ROBOT ESPERA TU DECISIÓN")
    }
}
