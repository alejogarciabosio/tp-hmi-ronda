package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.Alert
import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.CommandType.ACKNOWLEDGE_ALERT
import ar.edu.uade.ronda.core.model.CommandType.CANCEL_MISSION
import ar.edu.uade.ronda.core.model.CommandType.OPERATIONAL_STOP
import ar.edu.uade.ronda.core.model.CommandType.PAUSE_MISSION
import ar.edu.uade.ronda.core.model.CommandType.RESUME_MISSION
import ar.edu.uade.ronda.core.model.CommandType.RETURN_TO_SAFE_POINT
import ar.edu.uade.ronda.core.model.CommandType.START_MISSION
import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.ConnectionState.CONNECTED
import ar.edu.uade.ronda.core.model.ConnectionState.DEGRADED
import ar.edu.uade.ronda.core.model.ConnectionState.DISCONNECTED
import ar.edu.uade.ronda.core.model.ConnectionState.NO_DATA
import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.core.model.OperationalState.CANCELADA
import ar.edu.uade.ronda.core.model.OperationalState.COMPLETADA
import ar.edu.uade.ronda.core.model.OperationalState.DETENIDO
import ar.edu.uade.ronda.core.model.OperationalState.EN_BASE
import ar.edu.uade.ronda.core.model.OperationalState.EN_RONDA
import ar.edu.uade.ronda.core.model.OperationalState.FALLA
import ar.edu.uade.ronda.core.model.OperationalState.PAUSADA
import ar.edu.uade.ronda.core.model.OperationalState.PREPARANDO
import ar.edu.uade.ronda.core.model.OperationalState.REGRESANDO
import ar.edu.uade.ronda.core.model.OperationalState.REQUIERE_DECISION
import ar.edu.uade.ronda.core.model.RouteId
import ar.edu.uade.ronda.core.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CommandPolicyTest {
    private val policy = CommandPolicy()

    private fun alert(severity: Severity, acknowledged: Boolean = false) =
        Alert("a-1", AlertType.OBSTACLE, severity, "x", 0L, acknowledged)

    private fun ctx(
        state: OperationalState = EN_BASE,
        connection: ConnectionState = CONNECTED,
        battery: Int = 80,
        pending: Boolean = false,
        alerts: List<Alert> = emptyList(),
        route: RouteId = RouteId.RONDA_COMPLETA,
    ) = CommandContext(state, connection, battery, pending, alerts, route)

    class Case(val name: String, val command: CommandType, val ctx: CommandContext, val enabled: Boolean, val reason: String?) {
        override fun toString() = name
    }

    companion object {
        private val t = CommandPolicyTest()

        private fun case(name: String, command: CommandType, ctx: CommandContext, enabled: Boolean, reason: String? = null) =
            Arguments.of(Case(name, command, ctx, enabled, reason))

        @JvmStatic
        fun matrix() = listOf(
            // Iniciar
            case("iniciar en base con 80 %", START_MISSION, t.ctx(), true),
            case("iniciar con 40 % justo", START_MISSION, t.ctx(battery = 40), true),
            case("iniciar con 39 %", START_MISSION, t.ctx(battery = 39), false, "Batería insuficiente para ronda completa (necesita 40 %)"),
            case("iniciar ruta corta con 35 %", START_MISSION, t.ctx(battery = 35, route = RouteId.SOLO_SALA_TECNICA), true),
            case("iniciar en ronda", START_MISSION, t.ctx(EN_RONDA), false, "El robot tiene que estar en la base para iniciar"),
            case("iniciar con enlace degradado", START_MISSION, t.ctx(connection = DEGRADED), false, "Conexión degradada: esperá a que se estabilice para iniciar"),
            case("iniciar con comando pendiente", START_MISSION, t.ctx(pending = true), false, "Hay una orden pendiente de respuesta"),
            case("iniciar con falla", START_MISSION, t.ctx(FALLA), false, "El robot tiene una falla: requiere técnico"),
            case("iniciar sin datos", START_MISSION, t.ctx(connection = NO_DATA), false, "Sin conexión con el robot: no se pueden enviar órdenes"),
            // Pausar
            case("pausar en ronda", PAUSE_MISSION, t.ctx(EN_RONDA), true),
            case("pausar con enlace degradado", PAUSE_MISSION, t.ctx(EN_RONDA, connection = DEGRADED), true),
            case("pausar pausada", PAUSE_MISSION, t.ctx(PAUSADA), false, "Solo se puede pausar durante la ronda"),
            case("pausar en base", PAUSE_MISSION, t.ctx(EN_BASE), false, "Solo se puede pausar durante la ronda"),
            case("pausar con pendiente", PAUSE_MISSION, t.ctx(EN_RONDA, pending = true), false, "Hay una orden pendiente de respuesta"),
            case("pausar desconectado", PAUSE_MISSION, t.ctx(EN_RONDA, connection = DISCONNECTED), false, "Sin conexión con el robot: no se pueden enviar órdenes"),
            // Reanudar
            case("reanudar pausada", RESUME_MISSION, t.ctx(PAUSADA), true),
            case("reanudar detenida", RESUME_MISSION, t.ctx(DETENIDO), true),
            case("reanudar con decisión pendiente", RESUME_MISSION, t.ctx(REQUIERE_DECISION), true),
            case("reanudar en ronda", RESUME_MISSION, t.ctx(EN_RONDA), false, "No hay una misión pausada o detenida para reanudar"),
            case("reanudar con alerta crítica sin reconocer", RESUME_MISSION, t.ctx(DETENIDO, alerts = listOf(t.alert(Severity.CRITICAL))), false, "Reconocé la alerta antes de reanudar"),
            case("reanudar con alerta crítica reconocida", RESUME_MISSION, t.ctx(DETENIDO, alerts = listOf(t.alert(Severity.CRITICAL, acknowledged = true))), true),
            case("reanudar con alerta de advertencia sin reconocer", RESUME_MISSION, t.ctx(PAUSADA, alerts = listOf(t.alert(Severity.WARNING))), true),
            // Cancelar
            case("cancelar en ronda", CANCEL_MISSION, t.ctx(EN_RONDA), true),
            case("cancelar pausada", CANCEL_MISSION, t.ctx(PAUSADA), true),
            case("cancelar con decisión pendiente", CANCEL_MISSION, t.ctx(REQUIERE_DECISION), true),
            case("cancelar detenida", CANCEL_MISSION, t.ctx(DETENIDO), true),
            case("cancelar en base", CANCEL_MISSION, t.ctx(EN_BASE), false, "No hay una misión para cancelar"),
            case("cancelar regresando", CANCEL_MISSION, t.ctx(REGRESANDO), false, "No hay una misión para cancelar"),
            // Regresar
            case("regresar en ronda", RETURN_TO_SAFE_POINT, t.ctx(EN_RONDA), true),
            case("regresar pausada", RETURN_TO_SAFE_POINT, t.ctx(PAUSADA), true),
            case("regresar completada", RETURN_TO_SAFE_POINT, t.ctx(COMPLETADA), true),
            case("regresar cancelada", RETURN_TO_SAFE_POINT, t.ctx(CANCELADA), true),
            case("regresar detenida", RETURN_TO_SAFE_POINT, t.ctx(DETENIDO), true),
            case("regresar desde la base", RETURN_TO_SAFE_POINT, t.ctx(EN_BASE), false, "El robot ya está en la base"),
            case("regresar ya regresando", RETURN_TO_SAFE_POINT, t.ctx(REGRESANDO), false, "El robot ya está regresando"),
            case("regresar con falla", RETURN_TO_SAFE_POINT, t.ctx(FALLA), false, "El robot tiene una falla: requiere técnico"),
            // Detención operativa
            case("detener en ronda", OPERATIONAL_STOP, t.ctx(EN_RONDA), true),
            case("detener preparando", OPERATIONAL_STOP, t.ctx(PREPARANDO), true),
            case("detener regresando", OPERATIONAL_STOP, t.ctx(REGRESANDO), true),
            case("detener con decisión pendiente", OPERATIONAL_STOP, t.ctx(REQUIERE_DECISION), true),
            case("detener con comando pendiente", OPERATIONAL_STOP, t.ctx(EN_RONDA, pending = true), true),
            case("detener sin datos", OPERATIONAL_STOP, t.ctx(EN_RONDA, connection = NO_DATA), true),
            case("detener desconectado aunque el estado visto sea pausada", OPERATIONAL_STOP, t.ctx(PAUSADA, connection = DISCONNECTED), true),
            case("detener en base", OPERATIONAL_STOP, t.ctx(EN_BASE), false, "El robot no está en movimiento"),
            case("detener pausada con enlace", OPERATIONAL_STOP, t.ctx(PAUSADA), false, "El robot no está en movimiento"),
            // Reconocer alerta
            case("reconocer con alerta sin reconocer", ACKNOWLEDGE_ALERT, t.ctx(EN_RONDA, alerts = listOf(t.alert(Severity.WARNING))), true),
            case("reconocer sin alertas", ACKNOWLEDGE_ALERT, t.ctx(EN_RONDA), false, "No hay alertas sin reconocer"),
            case("reconocer con alertas ya reconocidas", ACKNOWLEDGE_ALERT, t.ctx(EN_RONDA, alerts = listOf(t.alert(Severity.INFO, true))), false, "No hay alertas sin reconocer"),
            case("reconocer con comando pendiente", ACKNOWLEDGE_ALERT, t.ctx(EN_RONDA, pending = true, alerts = listOf(t.alert(Severity.WARNING))), true),
            case("reconocer desconectado", ACKNOWLEDGE_ALERT, t.ctx(EN_RONDA, connection = DISCONNECTED, alerts = listOf(t.alert(Severity.WARNING))), false, "Sin conexión con el robot: no se pueden enviar órdenes"),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    fun `matriz de habilitacion`(case: Case) {
        val result = policy.evaluate(case.command, case.ctx)
        assertEquals(case.enabled, result.enabled)
        assertEquals(case.reason, result.reason)
    }

    @Test
    fun `todo comando deshabilitado trae motivo y todo habilitado no`() {
        for (state in OperationalState.entries) for (conn in ConnectionState.entries) for (pending in listOf(false, true)) {
            val c = ctx(state, conn, 50, pending, listOf(alert(Severity.CRITICAL)))
            for ((cmd, av) in policy.evaluateAll(c)) {
                if (av.enabled) assertNull(av.reason, "$cmd/$state/$conn") else assertNotNull(av.reason, "$cmd/$state/$conn")
            }
        }
    }

    @Test
    fun `sin conexion solo queda la detencion operativa de las ordenes de movimiento`() {
        for (conn in listOf(NO_DATA, DISCONNECTED)) {
            val habilitados = policy.evaluateAll(ctx(EN_RONDA, conn, alerts = listOf(alert(Severity.WARNING))))
                .filterValues { it.enabled }.keys
            assertEquals(setOf(OPERATIONAL_STOP), habilitados)
        }
    }

    @Test
    fun `la detencion nunca queda bloqueada por otro pendiente`() {
        assertTrue(policy.evaluate(OPERATIONAL_STOP, ctx(EN_RONDA, pending = true)).enabled)
        assertFalse(policy.evaluate(PAUSE_MISSION, ctx(EN_RONDA, pending = true)).enabled)
    }
}
