package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.core.model.BuildingLayout
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.Waypoint

/** Adaptación del plano de `core.model.BuildingLayout` a coordenadas normalizadas para dibujar. */
internal object BuildingPlan {
    val all: List<Waypoint> get() = BuildingLayout.checkpoints

    fun stops(route: RouteId): List<Waypoint> = BuildingLayout.stops(route)

    fun normX(xM: Double): Float = ((xM + 5.0) / 70.0).coerceIn(0.0, 1.0).toFloat()
    fun normY(yM: Double): Float = ((yM + 5.0) / 65.0).coerceIn(0.0, 1.0).toFloat()

    fun floorLabel(floor: Int) = if (floor == 0) "PB" else "$floor°"

    fun distanceToBaseM(floor: Int, xM: Double, yM: Double): Double =
        BuildingLayout.legLengthM(xM, yM, floor, BuildingLayout.base)
}
