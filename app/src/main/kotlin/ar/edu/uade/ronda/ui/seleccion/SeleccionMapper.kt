package ar.edu.uade.ronda.ui.seleccion

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.StatusBadgeUi
import ar.edu.uade.ronda.ui.model.StatusLevel

object SeleccionMapper {
    const val SIMULATED_ID = "RONDA-01"

    fun map(telemetry: RobotTelemetry?, connection: ConnectionState, controlledId: String?): SeleccionUiState {
        val linkOk = connection == ConnectionState.CONNECTED || connection == ConnectionState.DEGRADED
        val battery = telemetry?.takeIf { linkOk }?.battery?.percent?.let { "batería $it %" } ?: "batería sin datos"
        val mine = controlledId == SIMULATED_ID

        val ronda01 = RobotCardUi(
            id = SIMULATED_ID,
            specs = "Go2 EDU · simulado · $battery",
            badge = when {
                mine -> StatusBadgeUi(StatusLevel.OK, "Tu control")
                linkOk -> StatusBadgeUi(StatusLevel.OK, "Disponible")
                else -> StatusBadgeUi(StatusLevel.STALE, "Sin datos")
            },
            detail = when {
                mine -> "Lo controlás vos. Base: garita PB."
                linkOk -> "Nadie tiene el control. Base: garita PB."
                else -> "Sin telemetría reciente: no se puede tomar el control."
            },
            takeControl = when {
                mine -> CommandButtonUi("Ir a Supervisión", CommandKind.PRIMARY)
                linkOk -> CommandButtonUi("Tomar control", CommandKind.PRIMARY)
                else -> CommandButtonUi("Tomar control", CommandKind.PRIMARY, CommandUiState.DISABLED, "Sin enlace con el robot")
            },
        )
        val ronda03 = RobotCardUi(
            id = "RONDA-03",
            specs = "Go2 EDU · simulado · batería 51 %",
            badge = StatusBadgeUi(StatusLevel.WARNING, "En uso"),
            detail = "Control: Operador garita 1° desde 22:10. Podés mirar, no dar órdenes.",
            takeControl = CommandButtonUi("Tomar control", CommandKind.PRIMARY, CommandUiState.DISABLED, "Lo controla otro operador"),
        )
        val ronda02 = RobotCardUi(
            id = "RONDA-02",
            specs = "G1 · humanoide",
            badge = StatusBadgeUi(StatusLevel.DISABLED, "No compatible"),
            detail = "No configurado para este servicio: no tiene la ruta de ronda cargada.",
            takeControl = CommandButtonUi("Tomar control", CommandKind.PRIMARY, CommandUiState.DISABLED, "Robot no compatible con la ronda nocturna"),
            dimmed = true,
        )
        return SeleccionUiState(listOf(ronda01, ronda03, ronda02), controlledRobotId = controlledId)
    }
}
