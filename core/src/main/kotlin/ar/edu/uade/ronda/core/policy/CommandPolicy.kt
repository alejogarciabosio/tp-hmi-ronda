package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.Severity

/** Resultado de evaluar un comando: la UI solo pinta `enabled` y `reason`. */
data class CommandAvailability(val enabled: Boolean, val reason: String? = null) {
    companion object {
        val Enabled = CommandAvailability(true)
        fun disabled(reason: String) = CommandAvailability(false, reason)
    }
}

/** Foto del sistema que la política necesita para decidir. */
data class CommandContext(
    val state: OperationalState,
    val connection: ConnectionState,
    val batteryPercent: Int,
    /** Hay un comando de misión enviado y sin respuesta. */
    val hasPendingCommand: Boolean,
    val alerts: List<Alert> = emptyList(),
    /** Ruta seleccionada, para validar la batería al iniciar. */
    val selectedRoute: RouteId = RouteId.RONDA_COMPLETA,
    /** La telemetría trae una misión (`mission != null`). Sin misión no hay nada que reanudar ni cancelar. */
    val hasMission: Boolean = true,
    /** Hay un incidente esperando decisión (p. ej. el robot quedó DETENIDO mientras se la pedía). */
    val pendingDecision: Boolean = false,
)

/** Matriz de habilitación del plan §3.3. Es la única fuente de verdad. */
class CommandPolicy(private val battery: BatteryPolicy = BatteryPolicy()) {

    fun evaluate(command: CommandType, ctx: CommandContext): CommandAvailability {
        // La detención operativa se envía siempre que tenga sentido, incluso sin enlace o con otro pendiente.
        if (command == CommandType.OPERATIONAL_STOP) return evaluateStop(ctx)

        if (ctx.connection.isStale) {
            return CommandAvailability.disabled("Sin conexión con el robot: no se pueden enviar órdenes")
        }
        if (ctx.hasPendingCommand && command != CommandType.ACKNOWLEDGE_ALERT) {
            return CommandAvailability.disabled("Hay una orden pendiente de respuesta")
        }
        if (ctx.state == OperationalState.FALLA && command != CommandType.ACKNOWLEDGE_ALERT) {
            return evaluateFault(command, ctx)
        }
        return when (command) {
            CommandType.START_MISSION -> evaluateStart(ctx)
            CommandType.PAUSE_MISSION -> requireState(ctx, "Solo se puede pausar durante la ronda", OperationalState.EN_RONDA)
            CommandType.RESUME_MISSION -> evaluateResume(ctx)
            CommandType.CANCEL_MISSION ->
                if (!ctx.hasMission) CommandAvailability.disabled("No hay una misión para cancelar")
                else requireState(
                    ctx, "No hay una misión para cancelar",
                    OperationalState.EN_RONDA, OperationalState.PAUSADA,
                    OperationalState.REQUIERE_DECISION, OperationalState.DETENIDO,
                )
            CommandType.RETURN_TO_SAFE_POINT -> evaluateReturn(ctx)
            CommandType.ACKNOWLEDGE_ALERT ->
                if (ctx.alerts.any { !it.acknowledged }) CommandAvailability.Enabled
                else CommandAvailability.disabled("No hay alertas sin reconocer")
            CommandType.OPERATIONAL_STOP -> error("resuelto arriba")
        }
    }

    fun evaluateAll(ctx: CommandContext): Map<CommandType, CommandAvailability> =
        CommandType.entries.associateWith { evaluate(it, ctx) }

    private val RESUMABLE = setOf(OperationalState.PAUSADA, OperationalState.DETENIDO, OperationalState.REQUIERE_DECISION)

    private fun evaluateStop(ctx: CommandContext): CommandAvailability = when {
        // Con el enlace caído el estado que vemos es viejo: mejor dejar enviar la orden.
        ctx.connection.isStale -> CommandAvailability.Enabled
        ctx.state.inMotion -> CommandAvailability.Enabled
        else -> CommandAvailability.disabled("El robot no está en movimiento")
    }

    private fun evaluateStart(ctx: CommandContext): CommandAvailability = when {
        ctx.state != OperationalState.EN_BASE -> CommandAvailability.disabled("El robot tiene que estar en la base para iniciar")
        ctx.connection != ConnectionState.CONNECTED -> CommandAvailability.disabled("Conexión degradada: esperá a que se estabilice para iniciar")
        !battery.canStart(ctx.batteryPercent, ctx.selectedRoute) -> CommandAvailability.disabled(
            "Batería insuficiente para ${ctx.selectedRoute.displayName.lowercase()} (necesita ${battery.percentToStart(ctx.selectedRoute)} %)",
        )
        else -> CommandAvailability.Enabled
    }

    private fun evaluateResume(ctx: CommandContext): CommandAvailability {
        // Con REQUIERE_DECISION, reanudar lleva la decisión del operador (esperar o saltar el PC).
        if (ctx.state !in RESUMABLE || !ctx.hasMission) {
            return CommandAvailability.disabled("No hay una misión pausada o detenida para reanudar")
        }
        // Detenido durante una decisión: reanudar vuelve a pedir la decisión, no mueve al robot.
        if (ctx.state == OperationalState.DETENIDO && ctx.pendingDecision) return CommandAvailability.Enabled
        val criticalPending = ctx.alerts.any { !it.acknowledged && it.severity == Severity.CRITICAL }
        return if (criticalPending) CommandAvailability.disabled("Reconocé la alerta antes de reanudar")
        else CommandAvailability.Enabled
    }

    /**
     * Con FALLA solo queda una salida: reconocer la alerta de falla y ordenar el regreso a la base,
     * donde lo atiende el técnico. En el simulador, "Reiniciar" también la despeja.
     */
    private fun evaluateFault(command: CommandType, ctx: CommandContext): CommandAvailability {
        val faults = ctx.alerts.filter { it.type == AlertType.ROBOT_FAULT }
        return when {
            command != CommandType.RETURN_TO_SAFE_POINT || faults.isEmpty() ->
                CommandAvailability.disabled("El robot tiene una falla: requiere técnico")
            faults.any { !it.acknowledged } ->
                CommandAvailability.disabled("Reconocé la falla para habilitar el regreso a la base")
            else -> CommandAvailability.Enabled
        }
    }

    private fun evaluateReturn(ctx: CommandContext): CommandAvailability = when (ctx.state) {
        OperationalState.EN_BASE -> CommandAvailability.disabled("El robot ya está en la base")
        OperationalState.REGRESANDO -> CommandAvailability.disabled("El robot ya está regresando")
        else -> CommandAvailability.Enabled
    }

    private fun requireState(ctx: CommandContext, reason: String, vararg allowed: OperationalState) =
        if (ctx.state in allowed) CommandAvailability.Enabled else CommandAvailability.disabled(reason)
}
