package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.model.RouteId
import kotlin.math.hypot

/** Punto del plano del edificio. Piso 0 = PB, piso 1 = 1°. Coordenadas en metros. */
data class Waypoint(val id: String, val name: String, val zone: String, val floor: Int, val x: Double, val y: Double)

/** Plano simulado del plan §1.6. Los datos son inventados pero coherentes entre sí. */
object SimMap {
    val base = Waypoint("BASE", "Base (garita PB)", "Garita PB", 0, 0.0, 0.0)
    val restPoint = Waypoint("DESCANSO_ESCALERA_E", "Descanso escalera E", "Escalera E", 1, 60.0, 35.0)

    val checkpoints: List<Waypoint> = listOf(
        Waypoint("PC1", "Hall PB", "Hall PB", 0, 25.0, 10.0),
        Waypoint("PC2", "Salida de emergencia PB", "Salida de emergencia PB", 0, 60.0, 10.0),
        Waypoint("PC3", "Escalera E (subida)", "Escalera E", 1, 60.0, 35.0),
        Waypoint("PC4", "Pasillo 1°A", "Pasillo A", 1, 35.0, 35.0),
        Waypoint("PC5", "Sala técnica 1°", "Sala técnica", 1, 10.0, 35.0),
        Waypoint("PC6", "Salida de emergencia 1°", "Salida de emergencia 1°", 1, 10.0, 55.0),
        Waypoint("PC7", "Pasillo 1°B", "Pasillo B", 1, 40.0, 55.0),
        Waypoint("PC8", "Escalera O (bajada)", "Escalera O", 1, 5.0, 45.0),
    )

    private fun pc(vararg ids: String) = ids.map { id -> checkpoints.first { it.id == id } }

    fun stops(route: RouteId): List<Waypoint> = when (route) {
        RouteId.RONDA_COMPLETA -> checkpoints
        RouteId.SOLO_SALA_TECNICA -> pc("PC1", "PC3", "PC5")
        RouteId.SALIDAS_EMERGENCIA -> pc("PC2", "PC6")
    }

    private const val STAIRS_EXTRA_M = 12.0

    /** Largo de un tramo; cambiar de piso suma el recorrido de la escalera. */
    fun legLengthM(a: Waypoint, b: Waypoint): Double = legLengthM(a.x, a.y, a.floor, b)

    fun legLengthM(x: Double, y: Double, floor: Int, to: Waypoint): Double =
        hypot(to.x - x, to.y - y) + if (floor != to.floor) STAIRS_EXTRA_M else 0.0
}
