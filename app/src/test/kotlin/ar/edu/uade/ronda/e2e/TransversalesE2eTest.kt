package ar.edu.uade.ronda.e2e

import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.session.ControlSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que vale en toda la demo: control exclusivo, Atrás, rotación y textos sin enums crudos. */
class TransversalesE2eTest : E2eTest() {

    private val hoja = "EL ROBOT ESPERA TU DECISIÓN"

    private fun detencionDisponible(pantalla: String) {
        assertTrue("$pantalla: la detención operativa tiene que estar habilitada", puedeMantener(OperationalStopTexts.LABEL))
        assertVisible(OperationalStopTexts.NOTE)
    }

    @Test fun sinControlLasOrdenesSeDeshabilitanPeroLaDetencionNo() {
        prepararEIniciar()
        tocar("Más controles")
        assertVisible("Control")
        // Liberar lleva a Selección; con Atrás el operador vuelve a mirar la ronda sin control.
        tocar("Liberar control")
        assertVisible("Selección de robot")
        assertVisible("Nadie tiene el control")
        atras()
        assertVisible("Control")
        assertVisible(ControlSession.NO_CONTROL_REASON)
        assertDeshabilitado("Pausar")
        assertFalse("Cancelar misión no se puede mantener sin control", puedeMantener("Cancelar misión"))
        assertVisible("Cancelar misión. No disponible. ${ControlSession.NO_CONTROL_REASON}")
        detencionDisponible("Control")
        tocar("Volver")

        assertVisible("Supervisión")
        assertDeshabilitado("Pausar")
        assertDeshabilitado("Reanudar")
        assertVisible(ControlSession.NO_CONTROL_REASON)
        detencionDisponible("Supervisión")

        tocar("Batería")
        assertVisible("Telemetría")
        detencionDisponible("Telemetría")
        tocar("Volver")

        pestana("Misiones")
        assertDeshabilitado("Iniciar ronda")
        assertVisible("No tenés el control del robot")
        assertVisible("Tomá el control en Selección")
        detencionDisponible("Misiones")

        pestana("Alertas")
        detencionDisponible("Alertas")
        pestana("Historial")
        detencionDisponible("Historial")

        // Detener siempre gana: sin control, la detención sale y el robot se detiene.
        pestana("Supervisión")
        mantener(OperationalStopTexts.LABEL)
        esperarRobot("detenido") { estado() == OperationalState.DETENIDO }
        assertVisible("DETENIDO")
        assertDeshabilitado("Reanudar")
        assertVisible(ControlSession.NO_CONTROL_REASON)

        // Desconexión: con el enlace caído la detención sigue disponible.
        enPanel { tocar("Cortar enlace") }
        pasar(5_000)
        tocar("SIN DATOS")
        assertVisible("Desconexión")
        detencionDisponible("Desconexión")
    }

    @Test fun atrasDesdeUnaPestanaVuelveASupervision() {
        tomarControl()
        listOf("Misiones", "Alertas", "Historial", "Misiones", "Alertas", "Historial").forEach { pestana(it) }
        assertVisible("Historial, pestaña actual")
        atras()
        assertVisible("Supervisión, pestaña actual")
        assertVisible("¿TENÉS QUE ACTUAR?")
        // Un Atrás más sale de Supervisión a Selección: no recorre las pestañas visitadas.
        atras()
        assertVisible("Selección de robot")
        assertNoVisible("pestaña actual")
    }

    @Test fun rotarConLaDecisionAbiertaNoPierdeEstado() {
        prepararEIniciar("Obstáculo en el pasillo")
        esperarRobot("decisión por obstáculo", maxMs = 900_000) { incidenteAbierto() }
        pasar(1_000)
        assertVisible(hoja)

        rotar()
        assertVisible(hoja)
        assertVisible("Obstáculo en el pasillo · PC4")
        assertVisible("Saltar el PC4")
        assertVisible("REQUIERE DECISIÓN")
        assertVisible("SIMULACIÓN ×8")
        assertNoVisible(ControlSession.NO_CONTROL_REASON)

        // Cerrada por el operador, sigue cerrada después de rotar.
        atras()
        assertNoVisible(hoja)
        rotar()
        assertNoVisible(hoja)
        assertVisible("Decidir cómo seguir")
        assertVisible("Supervisión, pestaña actual")

        tocar("Decidir cómo seguir")
        assertVisible(hoja)
        tocar("Saltar el PC4")
        assertNoVisible(hoja)
        rotar()
        assertNoVisible(hoja)
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        assertVisible("EN RONDA")
        assertVisible("Punto de control 4, Pasillo 1°A: salteado")
    }

    @Test fun ningunaPantallaMuestraNombresCrudosDeEnums() {
        val hallazgos = mutableListOf<String>()
        fun revisar(pantalla: String) {
            settle()
            textos().filter { enumCrudo.containsMatchIn(it) }.forEach { hallazgos += "$pantalla: «$it»" }
        }

        revisar("Selección")
        tomarControl(); revisar("Supervisión en base")
        tocar("Simulación"); revisar("Panel de simulación")
        elegirVelocidad8(); cargarEscenario("Obstáculo en el pasillo"); revisar("Panel con escenario")
        tocar("Volver")
        pasar(2_000)
        pestana("Misiones"); revisar("Misiones")
        tocar("Iniciar ronda"); revisar("Misiones con la orden pendiente")
        pasar(2_000); revisar("Misiones con la orden verificada")
        pestana("Supervisión"); revisar("Supervisión en ronda")
        tocar("Batería"); revisar("Telemetría"); tocar("Volver")
        tocar("Más controles"); revisar("Control"); tocar("Volver")

        esperarRobot("obstáculo", maxMs = 900_000) { incidenteAbierto() }
        pasar(1_000); revisar("Hoja de decisión por obstáculo")
        atras()
        pestana("Alertas"); revisar("Alertas con obstáculo")
        pestana("Supervisión")
        tocar("Decidir cómo seguir")
        tocar("Saltar el PC4"); revisar("Supervisión con la orden pendiente")
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        pasar(1_000); revisar("Supervisión tras saltar")

        // Orden rechazada a propósito.
        enPanel { tocar("Rechazar próximo comando"); revisar("Panel con rechazo armado") }
        tocar("Pausar")
        pasar(1_000); revisar("Supervisión con orden rechazada")

        // Persona: decisión, vigilante y aviso.
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        enPanel { tocar("Inyectar persona") }
        esperarRobot("persona", maxMs = 60_000) { incidenteAbierto() }
        pasar(1_000); revisar("Hoja de decisión por persona")
        tocar("Enviar vigilante")
        pasar(2_000); revisar("Supervisión con vigilante en camino")
        pestana("Alertas"); revisar("Alertas con persona")
        pestana("Historial"); revisar("Historial")
        tocar("Eventos"); revisar("Historial · eventos")
        tocar("Severidad: todas"); revisar("Historial · advertencia o más")
        tocar("Misiones"); revisar("Historial · misiones")

        // Corte de enlace, falla durante el corte y vuelta.
        pestana("Supervisión")
        tocar("Reanudar")
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        enPanel { tocar("Cortar enlace"); revisar("Panel sin enlace") }
        pasar(12_000); revisar("Supervisión desconectada")
        tocar("DESCONECTADO"); revisar("Desconexión"); tocar("Volver")
        enPanel { tocar("Inyectar falla del robot"); tocar("Cortar enlace") }
        pasar(3_000); revisar("Supervisión con falla")
        pestana("Alertas"); revisar("Alertas con falla")
        pestana("Historial"); revisar("Historial al final")
        pestana("Misiones"); revisar("Misiones con falla")

        assertTrue("Textos con nombres crudos de enums:\n" + hallazgos.distinct().joinToString("\n"), hallazgos.isEmpty())
    }
}
