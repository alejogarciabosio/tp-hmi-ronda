package ar.edu.uade.ronda.core.sim

import ar.edu.uade.ronda.core.model.BuildingLayout
import ar.edu.uade.ronda.core.model.RouteId

typealias Waypoint = ar.edu.uade.ronda.core.model.Waypoint

/** El plano vive en `core.model.BuildingLayout`; el simulador lo usa a través de este alias. */
object SimMap {
    val base get() = BuildingLayout.base
    val restPoint get() = BuildingLayout.restPoint
    val checkpoints get() = BuildingLayout.checkpoints
    fun stops(route: RouteId) = BuildingLayout.stops(route)
    fun legLengthM(a: Waypoint, b: Waypoint) = BuildingLayout.legLengthM(a, b)
    fun legLengthM(x: Double, y: Double, floor: Int, to: Waypoint) = BuildingLayout.legLengthM(x, y, floor, to)
}
