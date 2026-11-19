package ar.edu.uade.ronda.ui.supervision

import ar.edu.uade.ronda.ui.model.CheckpointState
import ar.edu.uade.ronda.ui.model.CheckpointUi
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandChipStatus
import ar.edu.uade.ronda.ui.model.CommandChipUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.model.FloorPlanUi
import ar.edu.uade.ronda.ui.model.RobotPoseUi
import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.model.StateStripUi
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.model.Trend

/** Estados de ejemplo para los @Preview de la pantalla. */
internal object SupervisionPreviewData {
    private val stops = BuildingPlan.all
    private fun cps(vararg states: CheckpointState) = stops.mapIndexed { i, s ->
        CheckpointUi(
            i + 1, s.name, states.getOrElse(i) { CheckpointState.PENDING },
            if (states.getOrNull(i).let { it == CheckpointState.VERIFIED || it == CheckpointState.WITH_ISSUE }) "23:${41 + i}" else null,
            s.floor, BuildingPlan.normX(s.x), BuildingPlan.normY(s.y),
        )
    }

    private val inRound = cps(CheckpointState.VERIFIED, CheckpointState.VERIFIED, CheckpointState.CURRENT)
    private val plan = FloorPlanUi(0, "PB", inRound, RobotPoseUi(0, 0.6f, 0.3f, 90f))

    private fun readouts(level: StatusLevel = StatusLevel.OK, battery: String = "62", batteryLevel: StatusLevel = StatusLevel.OK, age: String = "hace 0,4 s") = listOf(
        TelemetryReadoutUi("Batería", battery, "%", if (level == StatusLevel.STALE) level else batteryLevel, null, age, "Alcanza para volver: sí · 48 min restantes"),
        TelemetryReadoutUi("Conexión", "180", "ms", level, null, age, "Latencia del último mensaje"),
        TelemetryReadoutUi("Sala técnica", "24,5", "°C", level, Trend.STABLE, age, "Sensor externo"),
        TelemetryReadoutUi("Salidas libres", "2/2", "", level, null, age),
    )

    private val pause = CommandButtonUi("Pausar", CommandKind.SECONDARY)
    private val resumeOff = CommandButtonUi("Reanudar", CommandKind.SECONDARY, CommandUiState.DISABLED, "No hay una misión pausada o detenida para reanudar")
    private val back = CommandButtonUi("Regresar a base", CommandKind.PRIMARY)

    fun enRonda() = SupervisionUiState(
        strip = StateStripUi("En ronda", StatusLevel.OK, false, "No", "Conectado · 0,4 s", StatusLevel.OK),
        hasData = true, plan = plan,
        floorTabs = listOf(FloorTabUi(0, "PB", true), FloorTabUi(1, "1°", false)),
        planCaption = "Zona: Salida de emergencia PB",
        progressTitle = "Ronda completa · PC 3/8",
        checkpoints = inRound.take(4),
        readouts = readouts(),
        pause = pause, resume = resumeOff, returnToBase = back, stop = StopButtonUi(true),
        lastCommand = LastCommandUi("Pausar", CommandChipUi(CommandChipStatus.VERIFIED), "hace 12 s"),
    )

    fun bateriaBaja() = enRonda().copy(
        strip = StateStripUi("En ronda", StatusLevel.OK, false, "No", "Conectado · 0,5 s", StatusLevel.OK),
        readouts = readouts(battery = "24", batteryLevel = StatusLevel.WARNING),
        alerts = listOf(AlertRowUi("a1", "Batería baja: 24 %. Considerá regresar a la base.", StatusLevel.WARNING, "hace 8 s")),
        pause = CommandButtonUi("Pausar", CommandKind.SECONDARY, CommandUiState.PENDING, "Esperando respuesta del robot"),
        lastCommand = LastCommandUi("Pausar", CommandChipUi(CommandChipStatus.PENDING), "hace 1,2 s"),
        lowBatteryReturn = "Batería baja (24 %): volvé a la base mientras alcanza",
    )

    fun sinDatos() = enRonda().copy(
        strip = StateStripUi("En ronda (último conocido)", StatusLevel.STALE, true, "Sí: sin datos del robot, su estado actual es desconocido", "Sin datos · hace 12 s", StatusLevel.STALE),
        staleBanner = StaleBannerUi(12),
        planCaption = "Última posición conocida, no vigente: Salida de emergencia PB, hace 12 s",
        readouts = readouts(StatusLevel.STALE, age = "hace 12 s"),
        pause = CommandButtonUi("Pausar", CommandKind.SECONDARY, CommandUiState.DISABLED, "Sin conexión con el robot: no se pueden enviar órdenes"),
        returnToBase = CommandButtonUi("Regresar a base", CommandKind.PRIMARY, CommandUiState.DISABLED, "Sin conexión con el robot: no se pueden enviar órdenes"),
        lastCommand = LastCommandUi("Pausar", CommandChipUi(CommandChipStatus.NO_RESPONSE), "no se sabe si se ejecutó"),
    )

    fun enBase() = SupervisionUiState(
        strip = StateStripUi("En base", StatusLevel.DISABLED, false, "No", "Conectado · 0,4 s", StatusLevel.OK),
        hasData = true,
        plan = FloorPlanUi(0, "PB", cps(), RobotPoseUi(0, 0.07f, 0.08f, 0f)),
        floorTabs = listOf(FloorTabUi(0, "PB", true), FloorTabUi(1, "1°", false)),
        planCaption = "Zona: Garita PB",
        readouts = readouts(battery = "96"),
        pause = CommandButtonUi("Pausar", CommandKind.SECONDARY, CommandUiState.DISABLED, "Solo se puede pausar durante la ronda"),
        resume = resumeOff,
        returnToBase = CommandButtonUi("Regresar a base", CommandKind.PRIMARY, CommandUiState.DISABLED, "El robot ya está en la base"),
        stop = StopButtonUi(false, "El robot no está en movimiento"),
    )

    fun decisionDescartada() = enRonda().copy(
        strip = StateStripUi("Requiere decisión", StatusLevel.WARNING, true, "Sí: decidí cómo seguir en PC4", "Conectado · 0,5 s", StatusLevel.OK),
        alerts = listOf(AlertRowUi("a2", "Objeto detectado en el pasillo, a 2 m.", StatusLevel.WARNING, "hace 3 s")),
        resume = CommandButtonUi("Reanudar", CommandKind.SECONDARY, CommandUiState.DISABLED, "Elegí una opción de la decisión pendiente"),
        decision = DecisionUi(
            "Obstáculo en el pasillo · PC4", "Un objeto bloquea el Pasillo A. El robot se detuvo.",
            listOf(DecisionOptionUi("WAIT_30S", "Esperar 30 s", false, true)),
        ),
        decisionDismissed = true,
    )
}
