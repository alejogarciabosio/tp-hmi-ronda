package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.RouteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BatteryPolicyTest {
    private val policy = BatteryPolicy()

    @ParameterizedTest(name = "{0} % a {1} m -> {2}")
    @CsvSource(
        "25, 100, true",   // cuesta 1 %: quedan 24, sobre la reserva de 15
        "16, 100, true",   // quedan 15 exactos: el borde alcanza
        "15, 100, false",  // quedan 14: no alcanza
        "25, 1500, false", // cuesta 15 %: quedan 10
        "15, 0, true",     // ya en la base
        "14, 0, false",    // por debajo de la reserva aun sin distancia
    )
    fun `alcanza para volver segun distancia y reserva`(percent: Int, distance: Double, expected: Boolean) {
        assertEquals(expected, policy.canReturnToBase(percent, distance))
    }

    @Test
    fun `distancia negativa se trata como cero`() {
        assertEquals(0.0, policy.percentToReturn(-5.0))
    }

    @Test
    fun `la ronda completa necesita 40 por ciento`() {
        assertEquals(40, policy.percentToStart(RouteId.RONDA_COMPLETA))
        assertFalse(policy.canStart(39, RouteId.RONDA_COMPLETA))
        assertTrue(policy.canStart(40, RouteId.RONDA_COMPLETA))
    }

    @Test
    fun `las rutas cortas necesitan menos bateria`() {
        assertTrue(policy.percentToStart(RouteId.SOLO_SALA_TECNICA) < policy.percentToStart(RouteId.RONDA_COMPLETA))
        assertTrue(policy.percentToStart(RouteId.SALIDAS_EMERGENCIA) < policy.percentToStart(RouteId.SOLO_SALA_TECNICA))
        assertTrue(policy.canStart(35, RouteId.SOLO_SALA_TECNICA))
        assertFalse(policy.canStart(34, RouteId.SOLO_SALA_TECNICA))
    }
}
