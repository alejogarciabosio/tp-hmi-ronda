package ar.edu.uade.ronda.ui.navigation

/** Las 9 pantallas del plan (§8). La decisión requerida es una hoja modal sobre Supervisión, no una ruta. */
enum class Route(val path: String, val title: String) {
    SELECCION("seleccion", "Selección de robot"),
    SUPERVISION("supervision", "Supervisión"),
    TELEMETRIA("telemetria", "Telemetría"),
    MISIONES("misiones", "Misiones"),
    CONTROL("control", "Control"),
    ALERTAS("alertas", "Alertas"),
    HISTORIAL("historial", "Historial"),
    DESCONEXION("desconexion", "Desconexión"),
    SIMULACION("simulacion", "Panel de simulación"),
}
