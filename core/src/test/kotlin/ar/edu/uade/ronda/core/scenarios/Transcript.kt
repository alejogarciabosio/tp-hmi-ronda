package ar.edu.uade.ronda.core.scenarios

import ar.edu.uade.ronda.core.model.CommandParams
import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.RobotEvent
import ar.edu.uade.ronda.core.model.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import java.io.File

/**
 * Registra en texto legible lo que pasa en un escenario: hora virtual, telemetría resumida,
 * comandos con su estado, eventos y las decisiones del operador.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Transcript(private val scope: TestScope, private val rig: ScenarioRig, title: String, description: String) {
    private val lines = mutableListOf<String>()
    private var lastSummaryAt = Long.MIN_VALUE
    private var lastKey = ""
    private val startMs = rig.clock.nowMs()

    init {
        lines += "# $title"
        lines += ""
        lines += description
        lines += ""
        lines += "Generado por `ScenarioTranscriptsTest` con tiempo virtual y semilla fija. La hora es el tiempo transcurrido desde el inicio del escenario (mm:ss.s)."
        lines += ""
        lines += "```"
        scope.backgroundScope.launch {
            rig.repo.telemetry.collect { t -> if (t != null) onTelemetry(t) }
        }
        scope.backgroundScope.launch {
            var seen = emptyMap<String, CommandStatus>()
            rig.repo.commands.collect { list ->
                for (c in list) if (seen[c.id] != c.status) {
                    val motivo = c.reasonText?.let { " - motivo: $it" }.orEmpty()
                    log("COMANDO  ${c.id} ${c.command.type} -> ${label(c.status)}$motivo")
                }
                seen = list.associate { it.id to it.status }
            }
        }
        scope.backgroundScope.launch {
            rig.repo.events.collect { e ->
                when (e) {
                    is RobotEvent.AlertRaised -> log("EVENTO   alerta ${e.alert.type} ${e.alert.severity} (emitida a ${at(e.timestamp)}): ${e.alert.message}${foto(e.alert.snapshot)}")
                    is RobotEvent.DecisionRequired -> log(
                        "EVENTO   decisión requerida ${e.incident.type} en PC ${e.incident.checkpointIndex} (emitida a ${at(e.timestamp)}): " +
                            "${e.incident.message} Opciones: ${e.incident.options.joinToString { it.label }}${foto(e.incident.snapshot)}",
                    )
                }
            }
        }
        scope.backgroundScope.launch {
            rig.repo.connection.collect { log("CONEXION $it") }
        }
        scope.runCurrent()
    }

    private fun foto(s: Snapshot?): String =
        s?.let { " [foto adjunta: ${it.id}, ${it.mediaType}, ${if (it.data == null) "por id" else "embebida"}]" }.orEmpty()

    private fun at(epochMs: Long): String = stamp(epochMs - startMs)

    private fun stamp(ms: Long): String {
        val m = ms / 60_000
        val s = (ms % 60_000) / 1000.0
        return "%02d:%04.1f".format(m, s)
    }

    private fun now() = stamp(scope.currentTime)

    private fun label(s: CommandStatus) = when (s) {
        CommandStatus.PENDING -> "PENDIENTE"
        CommandStatus.CONFIRMED -> "CONFIRMADA (el robot aceptó la orden)"
        CommandStatus.REJECTED -> "RECHAZADA"
        CommandStatus.NO_RESPONSE -> "SIN RESPUESTA"
        CommandStatus.VERIFIED -> "VERIFICADA (la telemetría refleja el efecto)"
    }

    fun log(text: String) {
        lines += "${now()}  $text"
    }

    /** Una línea de telemetría cuando cambia lo importante, y como máximo una cada 30 s si no. */
    private fun onTelemetry(t: ar.edu.uade.ronda.core.model.RobotTelemetry) {
        val key = "${t.operationalState}/${t.service.checkpointsVerified}/${t.alerts.size}/${t.alerts.count { it.acknowledged }}"
        val due = scope.currentTime - lastSummaryAt >= 30_000
        if (key == lastKey && !due) return
        lastKey = key
        lastSummaryAt = scope.currentTime
        val mission = t.mission?.let { "PC ${it.checkpointIndex}/${it.checkpointsTotal} (${it.stage})" } ?: "sin misión"
        log(
            "TELEMETRIA ${t.operationalState} | $mission | PC verificados ${t.service.checkpointsVerified}, con observación ${t.service.checkpointsWithIssues} | " +
                "batería ${t.battery.percent} % | ${t.pose.zone} (piso ${t.pose.floor}) ${t.motion.mode} ${t.motion.speedMps} m/s | " +
                "sala técnica ${t.service.techRoomTempC} °C | alertas sin reconocer ${t.unacknowledgedAlerts.size}",
        )
    }

    fun note(text: String) = log("NOTA     $text")

    /** Acción del operador sobre la app: queda escrita y se envía el comando. */
    fun operator(action: String, type: CommandType, params: CommandParams = CommandParams()): String {
        log("OPERADOR $action")
        return rig.send(type, params)
    }

    fun operatorPanel(action: String, block: () -> Unit) {
        log("PANEL DE SIMULACION $action")
        block()
        scope.runCurrent()
    }

    fun save(fileName: String) {
        val dir = System.getProperty(DIR_PROPERTY) ?: return
        File(dir).mkdirs()
        File(dir, fileName).writeText((lines + "```").joinToString("\n") + "\n")
    }

    companion object {
        const val DIR_PROPERTY = "ronda.transcripts.dir"
    }
}
