package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.RouteId
import kotlin.math.ceil

/**
 * Estima si la batería alcanza para volver a la base o para completar una ruta.
 * Los coeficientes son supuestos del simulador, no mediciones del Go2.
 */
class BatteryPolicy(
    private val percentPerMeter: Double = 0.010,
    private val percentPerCheckpoint: Double = 1.0,
    /** Reserva que debe quedar al llegar: cubre escaleras, esperas y error de estimación. */
    val reservePercent: Double = 15.0,
) {
    /** Batería necesaria para recorrer `distanceM` hasta la base, sin la reserva. */
    fun percentToReturn(distanceM: Double): Double = distanceM.coerceAtLeast(0.0) * percentPerMeter

    fun canReturnToBase(batteryPercent: Int, distanceToBaseM: Double): Boolean =
        batteryPercent - percentToReturn(distanceToBaseM) >= reservePercent

    /** Batería mínima para arrancar la ruta: los puntos de control más la reserva (ronda completa: 40 %). */
    fun percentToStart(route: RouteId): Int =
        ceil(route.checkpointCount * percentPerCheckpoint + reservePercent + START_MARGIN_PERCENT).toInt()

    fun canStart(batteryPercent: Int, route: RouteId): Boolean = batteryPercent >= percentToStart(route)

    private companion object {
        // Regla operativa: la ronda completa (8 PC) sale con 40 % aunque el cálculo físico alcance con ~23 %; ver docs/supuestos-simulacion.md §2.6.
        const val START_MARGIN_PERCENT = 17.0
    }
}
