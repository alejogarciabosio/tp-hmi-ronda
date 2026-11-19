package ar.edu.uade.ronda.ui

import ar.edu.uade.ronda.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesTest {
    @Test fun hayNueveRutasConPathUnico() {
        assertEquals(9, Route.entries.size)
        assertEquals(9, Route.entries.map { it.path }.toSet().size)
    }
}
