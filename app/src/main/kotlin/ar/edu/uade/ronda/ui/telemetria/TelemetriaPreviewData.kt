package ar.edu.uade.ronda.ui.telemetria

import ar.edu.uade.ronda.core.model.Battery
import ar.edu.uade.ronda.core.model.EmergencyExit
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.MissionProgress
import ar.edu.uade.ronda.core.model.Motion
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.Pose
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.ServiceTelemetry
import ar.edu.uade.ronda.core.model.TempTrend

/** Telemetría de ejemplo para los @Preview (la misma situación que el Figma). */
internal object TelemetriaPreviewData {
    val enRonda = RobotTelemetry(
        messageId = "preview", robotId = "RONDA-01", timestamp = 1_000, latencyMs = 180,
        battery = Battery(62, 48, false), operationalState = OperationalState.EN_RONDA,
        mission = MissionProgress("m1", "Ronda completa", 4, 8, "Pasillo 1°A"),
        pose = Pose(1, 30.0, 20.0, 270.0, "Pasillo 1°A"), motion = Motion(0.6, MotionMode.CAMINANDO),
        alerts = emptyList(),
        service = ServiceTelemetry(
            3, 1, 28.1, TempTrend.RISING,
            listOf(
                EmergencyExit("SE-PB", "Salida PB", ExitStatus.LIBRE),
                EmergencyExit("SE-1", "Salida 1°", ExitStatus.NO_VERIFICADA),
            ),
        ),
    )
}
