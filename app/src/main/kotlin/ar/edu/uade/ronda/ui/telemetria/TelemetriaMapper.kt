package ar.edu.uade.ronda.ui.telemetria

import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.ExitStatus
import ar.edu.uade.ronda.core.model.MotionMode
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.core.model.Severity
import ar.edu.uade.ronda.core.model.TempTrend
import ar.edu.uade.ronda.core.policy.BatteryPolicy
import ar.edu.uade.ronda.core.policy.CommandAvailability
import ar.edu.uade.ronda.ui.model.StatusLevel
import ar.edu.uade.ronda.ui.model.TelemetryReadoutUi
import ar.edu.uade.ronda.ui.model.Trend
import ar.edu.uade.ronda.ui.supervision.BuildingPlan
import ar.edu.uade.ronda.ui.supervision.CheckpointLog
import ar.edu.uade.ronda.ui.supervision.SupervisionInputs
import ar.edu.uade.ronda.ui.supervision.SupervisionMapper

/**
 * Detalle de todas las variables. La franja de estado se reutiliza de Supervisión para que
 * las dos pantallas digan exactamente lo mismo sobre conexión y acción requerida.
 */
internal object TelemetriaMapper {
    private val battery = BatteryPolicy()

    fun map(telemetry: RobotTelemetry?, connection: ConnectionState, lastAt: Long?, nowMs: Long): TelemetriaUiState {
        val base = SupervisionMapper.map(
            SupervisionInputs(
                telemetry, connection, lastAt, null, emptyList(),
                emptyMap<CommandType, CommandAvailability>(), CheckpointLog(), null, null, nowMs,
            ),
        )
        val ageMs = lastAt?.let { (nowMs - it).coerceAtLeast(0) }
        val updated = if (ageMs == null) "Sin datos" else "Actualizado ${SupervisionMapper.ageText(ageMs)}"
        if (telemetry == null) return TelemetriaUiState(base.strip, base.staleBanner, updated)

        val stale = connection.isStale
        val age = SupervisionMapper.ageText(ageMs ?: 0)
        return TelemetriaUiState(
            strip = base.strip,
            staleBanner = base.staleBanner,
            updated = updated,
            robotTitle = "ROBOT · ${telemetry.robotId} · GO2 EDU",
            robot = robotReadouts(telemetry, connection, stale, age),
            service = serviceReadouts(telemetry, stale, age),
        )
    }

    private fun robotReadouts(t: RobotTelemetry, conn: ConnectionState, stale: Boolean, age: String): List<TelemetryReadoutUi> {
        fun lvl(l: StatusLevel) = if (stale) StatusLevel.STALE else l
        val connText = when (conn) {
            ConnectionState.CONNECTED -> "Conectado · latencia del último mensaje"
            ConnectionState.DEGRADED -> "Enlace degradado · latencia del último mensaje"
            ConnectionState.NO_DATA -> "Sin datos recientes · última latencia conocida"
            ConnectionState.DISCONNECTED -> "Desconectado · última latencia conocida"
        }
        val connLevel = when (conn) {
            ConnectionState.CONNECTED -> StatusLevel.OK
            ConnectionState.DEGRADED -> StatusLevel.WARNING
            else -> StatusLevel.STALE
        }

        val pct = t.battery.percent
        val canReturn = battery.canReturnToBase(pct, BuildingPlan.distanceToBaseM(t.pose.floor, t.pose.x, t.pose.y))
        val batteryLevel = when {
            !canReturn || pct <= 15 -> StatusLevel.ERROR
            pct <= 25 -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val batteryDetail = "Alcanza para volver a base: ${if (canReturn) "sí" else "no"} · ${t.battery.minutesLeft} min restantes" +
            if (t.battery.charging) " · cargando" else ""

        val mode = when (t.motion.mode) {
            MotionMode.CAMINANDO -> "Caminando"
            MotionMode.DETENIDO -> "Detenido"
            MotionMode.ESCALERA -> "En escalera"
            MotionMode.GIRANDO -> "Girando"
        }
        val mission = t.mission
        return listOf(
            TelemetryReadoutUi("Conexión", t.latencyMs.toString(), "ms", connLevel, null, age, connText),
            TelemetryReadoutUi(
                "Batería", pct.toString(), "%", lvl(batteryLevel),
                if (t.battery.charging) Trend.RISING else null, age, batteryDetail,
            ),
            TelemetryReadoutUi(
                "Posición", BuildingPlan.floorLabel(t.pose.floor), "", lvl(StatusLevel.OK), null, age,
                "${t.pose.zone} · rumbo ${t.pose.headingDeg.toInt()}°",
            ),
            TelemetryReadoutUi("Movimiento", SupervisionMapper.dec1(t.motion.speedMps), "m/s", lvl(StatusLevel.OK), null, age, mode),
            if (mission != null) {
                TelemetryReadoutUi(
                    "Misión", "PC ${mission.checkpointIndex}/${mission.checkpointsTotal}", "", lvl(StatusLevel.OK), null, age,
                    "${mission.name} · ${mission.stage}",
                )
            } else {
                TelemetryReadoutUi("Misión", "Sin misión", "", lvl(StatusLevel.DISABLED), null, age, SupervisionMapper.stateLabel(t.operationalState))
            },
        )
    }

    private fun serviceReadouts(t: RobotTelemetry, stale: Boolean, age: String): List<TelemetryReadoutUi> {
        fun lvl(l: StatusLevel) = if (stale) StatusLevel.STALE else l
        val s = t.service
        val total = t.mission?.checkpointsTotal ?: 8
        val temp = s.techRoomTempC
        val tempLevel = when {
            temp >= 32.0 -> StatusLevel.ERROR
            temp > 27.0 -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        val tempDetail = when {
            temp >= 32.0 -> "Sensor externo · crítico desde 32 °C"
            temp > 27.0 -> "Sensor externo · advertencia sobre 27 °C"
            else -> "Sensor externo · normal hasta 27 °C"
        }
        val exits = s.emergencyExitsClear.map { e ->
            val (value, level, detail) = when (e.status) {
                ExitStatus.LIBRE -> Triple("Libre", StatusLevel.OK, "Verificada en la ronda")
                ExitStatus.OBSTRUIDA -> Triple("Obstruida", StatusLevel.ERROR, "Avisar a seguridad")
                ExitStatus.NO_VERIFICADA -> Triple("Sin verificar", StatusLevel.WARNING, "Se verifica al pasar por el punto de control")
            }
            TelemetryReadoutUi("Salida de emergencia ${e.name.removePrefix("Salida ").trim()}", value, "", lvl(level), null, age, detail)
        }
        val unack = t.unacknowledgedAlerts
        val alertLevel = when {
            unack.any { it.severity == Severity.CRITICAL } -> StatusLevel.ERROR
            unack.isNotEmpty() -> StatusLevel.WARNING
            else -> StatusLevel.OK
        }
        return listOf(
            TelemetryReadoutUi(
                "Puntos verificados", "${s.checkpointsVerified}/$total", "",
                lvl(if (s.checkpointsWithIssues > 0) StatusLevel.WARNING else StatusLevel.OK), null, age,
                if (s.checkpointsWithIssues > 0) "${s.checkpointsWithIssues} con observación" else "Sin observaciones",
            ),
            TelemetryReadoutUi(
                "Sala técnica", SupervisionMapper.dec1(temp), "°C", lvl(tempLevel),
                when (s.techRoomTempTrend) { TempTrend.RISING -> Trend.RISING; TempTrend.FALLING -> Trend.FALLING; TempTrend.STABLE -> Trend.STABLE },
                age, tempDetail,
            ),
        ) + exits + TelemetryReadoutUi(
            "Alertas sin reconocer", unack.size.toString(), "", lvl(alertLevel), null, age,
            if (unack.isEmpty()) null else "Revisalas en Alertas",
        )
    }
}
