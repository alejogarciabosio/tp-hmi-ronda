package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.core.model.RouteId

/**
 * Geometría del plano para dibujar. Repite los datos del plano del simulador (plan §1.6) porque
 * la UI no puede importar el paquete del simulador; si el plano pasa a `core.model`, esto se reemplaza.
 */
internal object BuildingPlan {
    data class Stop(val id: String, val name: String, val floor: Int, val xM: Double, val yM: Double)

    const val BASE_X_M = 0.0
    const val BASE_Y_M = 0.0
    const val STAIRS_EXTRA_M = 12.0

    val all = listOf(
        Stop("PC1", "Hall PB", 0, 25.0, 10.0),
        Stop("PC2", "Salida de emergencia PB", 0, 60.0, 10.0),
        Stop("PC3", "Escalera E (subida)", 1, 60.0, 35.0),
        Stop("PC4", "Pasillo 1°A", 1, 35.0, 35.0),
        Stop("PC5", "Sala técnica 1°", 1, 10.0, 35.0),
        Stop("PC6", "Salida de emergencia 1°", 1, 10.0, 55.0),
        Stop("PC7", "Pasillo 1°B", 1, 40.0, 55.0),
        Stop("PC8", "Escalera O (bajada)", 1, 5.0, 45.0),
    )

    fun stops(route: RouteId): List<Stop> = when (route) {
        RouteId.RONDA_COMPLETA -> all
        RouteId.SOLO_SALA_TECNICA -> pick("PC1", "PC3", "PC5")
        RouteId.SALIDAS_EMERGENCIA -> pick("PC2", "PC6")
    }

    private fun pick(vararg ids: String) = ids.map { id -> all.first { it.id == id } }

    fun normX(xM: Double): Float = ((xM + 5.0) / 70.0).coerceIn(0.0, 1.0).toFloat()
    fun normY(yM: Double): Float = ((yM + 5.0) / 65.0).coerceIn(0.0, 1.0).toFloat()

    fun floorLabel(floor: Int) = if (floor == 0) "PB" else "$floor°"

    /** Distancia aproximada en línea recta a la base, más la escalera si está en otro piso. */
    fun distanceToBaseM(floor: Int, xM: Double, yM: Double): Double =
        Math.hypot(xM - BASE_X_M, yM - BASE_Y_M) + if (floor != 0) STAIRS_EXTRA_M else 0.0
}
