package ar.edu.uade.ronda.ui.simulacion

import ar.edu.uade.ronda.core.control.SimScenario
import ar.edu.uade.ronda.core.control.SimulationSettings
import ar.edu.uade.ronda.core.model.Incident
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RobotTelemetry
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState

data class SimulacionInputs(
    val settings: SimulationSettings,
    val telemetry: RobotTelemetry?,
    val incident: Incident?,
    val rejectArmed: Boolean,
)

object SimulacionMapper {
    /** Mayor que el ttl de las órdenes (4 s): así la demora termina en SIN RESPUESTA y no solo en PENDIENTE largo. */
    const val DELAY_MS = 5_000L
    const val TIMED_CUT_MS = 15_000L
    const val BATTERY_STEP = 10

    fun map(i: SimulacionInputs): SimulacionUiState {
        val s = i.settings
        val inRound = i.telemetry?.operationalState == OperationalState.EN_RONDA
        val battery = i.telemetry?.battery?.percent
        return SimulacionUiState(
            scenarios = SimScenario.entries.mapIndexed { idx, sc ->
                ScenarioUi(sc, "${idx + 1} · ${sc.displayName}" + if (sc == s.scenario) " · cargado" else "", sc == s.scenario)
            },
            scenarioHint = hint(s.scenario),
            toggles = listOf(
                SimToggleUi(
                    SimToggle.LINK_CUT, "Cortar enlace",
                    "El robot deja de enviar telemetría y de responder. Al volver llegan los eventos con su hora original.",
                    on = !s.linkUp,
                ),
                SimToggleUi(
                    SimToggle.REJECT_NEXT, "Rechazar próximo comando",
                    if (i.rejectArmed) "Armado: la próxima orden vuelve RECHAZADA. Se desarma solo después de usarse."
                    else "La próxima orden vuelve RECHAZADA.",
                    on = i.rejectArmed,
                    // No hay forma de desarmarlo desde el simulador: se consume con la próxima orden.
                    enabled = !i.rejectArmed,
                ),
                SimToggleUi(
                    SimToggle.DELAY_RESPONSES, "Demorar respuestas 5 s",
                    "Muestra PENDIENTE y, pasados 4 s sin respuesta, SIN RESPUESTA.",
                    on = s.responseDelayMs > 0,
                ),
            ),
            timedCut = CommandButtonUi(
                "Cortar enlace 15 s", CommandKind.SECONDARY,
                if (s.linkUp) CommandUiState.ENABLED else CommandUiState.DISABLED,
                if (s.linkUp) null else "El enlace ya está cortado.",
            ),
            injections = listOf(
                injection(SimInjection.OBSTACLE, "Inyectar obstáculo", "El robot se detiene y pide decisión.", inRound, i.incident, s),
                injection(SimInjection.PERSON, "Inyectar persona", "Alerta crítica en zona restringida; el robot pide decisión.", inRound, i.incident, s),
                // La falla sí se puede inyectar sin enlace: es lo que el escenario 3 usa para mostrar un evento que llega tarde.
                SimInjectionUi(
                    SimInjection.FAULT,
                    CommandButtonUi("Inyectar falla del robot", CommandKind.SECONDARY),
                    if (s.linkUp) "El robot queda en FALLA y pide un técnico."
                    else "Con el enlace cortado, la falla ocurre igual y la app la recibe al volver el enlace, con su hora original.",
                ),
            ),
            batteryTitle = battery?.let { "Batería · $it %" } ?: "Batería · sin dato",
            batteryMinus = batteryButton("Batería −$BATTERY_STEP %", battery, battery != null && battery > 0),
            batteryPlus = batteryButton("Batería +$BATTERY_STEP %", battery, battery != null && battery < 100),
            injectionRejection = s.lastInjectionRejection?.let { "No se inyectó: $it" },
            timeScaleTitle = "Velocidad · ×${s.timeScale.toInt()}",
            timeScales = SimulationSettings.ALLOWED_TIME_SCALES.map { TimeScaleUi(it, "×${it.toInt()}", it == s.timeScale) },
        )
    }

    private fun injection(
        kind: SimInjection, label: String, description: String, inRound: Boolean, incident: Incident?, s: SimulationSettings,
    ): SimInjectionUi {
        val type = if (kind == SimInjection.OBSTACLE) IncidentType.OBSTACLE else IncidentType.PERSON_IN_RESTRICTED_ZONE
        val reason = when {
            !s.linkUp -> "Sin enlace: el robot no puede pedir la decisión. Restablecé el enlace."
            incident?.type == type -> "Ya hay un incidente así abierto."
            incident != null -> "Hay otra decisión pendiente."
            !inRound -> "Solo durante una ronda en curso."
            else -> s.incidentInjectionBlockedReason
        }
        val state = if (reason == null) CommandUiState.ENABLED else CommandUiState.DISABLED
        return SimInjectionUi(kind, CommandButtonUi(label, CommandKind.SECONDARY, state, reason), description)
    }

    private fun batteryButton(label: String, battery: Int?, enabled: Boolean) = CommandButtonUi(
        label, CommandKind.SECONDARY,
        if (enabled) CommandUiState.ENABLED else CommandUiState.DISABLED,
        when {
            battery == null -> "Sin telemetría del robot."
            !enabled -> "Llegó al límite."
            else -> null
        },
    )

    private fun hint(scenario: SimScenario): String = when (scenario) {
        SimScenario.NORMAL ->
            "Robot en base con batería al 100 %. Iniciá «Ronda completa» en Misiones: recorre los 8 puntos y vuelve a la base."
        SimScenario.LOW_BATTERY ->
            "Arranca con 40 % y una celda degradada. Iniciá la ronda: a mitad de camino cruza el 25 % y aparece la alerta; cerca del 15 % el robot propone volver."
        SimScenario.DISCONNECTION ->
            "Iniciá la ronda y tocá «Cortar enlace 15 s»: los datos quedan marcados como viejos y Pausar se deshabilita. Durante el corte, mantené la detención operativa (queda SIN RESPUESTA) e inyectá una falla: al volver el enlace, la falla llega con su hora original y el robot queda en FALLA."
        SimScenario.OBSTACLE ->
            "Iniciá la ronda: al llegar al PC4 un objeto bloquea el pasillo y el robot pide decisión."
        SimScenario.PERSON_IN_RESTRICTED_ZONE ->
            "Iniciá la ronda: en el PC5 (sala técnica) se detecta una persona y el robot pide decisión. No se guarda imagen."
    }
}
