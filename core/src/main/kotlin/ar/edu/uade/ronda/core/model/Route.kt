package ar.edu.uade.ronda.core.model

/** Rutas del selector de misiones (plan §1.6). */
enum class RouteId(val displayName: String, val checkpointCount: Int) {
    RONDA_COMPLETA("Ronda completa", 8),
    SOLO_SALA_TECNICA("Solo sala técnica", 3),
    SALIDAS_EMERGENCIA("Salidas de emergencia", 2),
}

enum class SafePointId(val displayName: String) {
    BASE("Base"),
    DESCANSO_ESCALERA_E("Descanso escalera E"),
}
