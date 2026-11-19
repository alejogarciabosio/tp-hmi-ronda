package ar.edu.uade.ronda.e2e

import ar.edu.uade.ronda.core.model.OperationalState
import ar.edu.uade.ronda.ui.components.OperationalStopTexts
import ar.edu.uade.ronda.ui.session.ControlSession
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Escenario 3: corte de enlace durante la ronda y recuperación. */
class Escenario3DesconexionE2eTest : E2eTest() {

    /** "Conectado" como palabra suelta: "Desconectado" no cuenta. */
    private val conectado = Regex("(?i)(?<!des)conectado")
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    private fun nuncaConectado() = assertNoVisible(conectado)

    @Test fun corteDeEnlaceYRecuperacion() {
        prepararEIniciar("Desconexión")
        esperarRobot("primer punto verificado") { verificados() >= 1 }
        assertVisible(Regex("(?i)conectado ·"))

        val corte = app.clock.nowMs()
        enPanel { tocar("Cortar enlace") }

        // La telemetría llega cada 0,5 s: hasta 1,5 s sin mensajes la app no puede saber que hubo un
        // corte y muestra "Conectado" con la antigüedad real. Desde ahí, nunca más "Conectado".
        pasar(2_000)
        assertVisible("ENLACE DEGRADADO")
        nuncaConectado()
        pasar(3_000)
        assertVisible(Regex("Sin datos desde hace \\d+ s"))
        assertVisible(Regex("SIN DATOS · HACE"))
        nuncaConectado()

        // Pasados 10 s: desconectado, también con la antigüedad.
        repeat(7) { pasar(1_000); nuncaConectado() }
        assertVisible("DESCONECTADO")
        assertVisible(Regex("Sin datos desde hace 1\\d s"))
        assertVisible("(último conocido)")

        // Órdenes deshabilitadas con su motivo; la detención operativa sigue disponible.
        assertDeshabilitado("Pausar")
        assertDeshabilitado("Reanudar")
        assertVisible("Sin conexión con el robot: no se pueden enviar órdenes")
        assertTrue("La detención operativa tiene que poder mantenerse", puedeMantener(OperationalStopTexts.LABEL))
        assertVisible(OperationalStopTexts.NOTE)

        // Otras pestañas durante el corte: tampoco dicen "Conectado".
        pestana("Alertas"); nuncaConectado()
        assertNoVisible("Enlace perdido")
        pestana("Misiones"); nuncaConectado()
        assertDeshabilitado("Iniciar ronda")
        pestana("Supervisión")

        // Pantalla Desconexión desde el aviso de sin datos.
        tocar("DESCONECTADO")
        assertVisible("Desconexión")
        assertVisible("ÚLTIMO DATO CONOCIDO")
        assertVisible("Se detiene donde está.")
        assertTrue(puedeMantener(OperationalStopTexts.LABEL))
        nuncaConectado()
        tocar("Volver")

        // Restaurar el enlace desde el panel.
        enPanel { tocar("Cortar enlace") }
        pasar(2_000)
        assertVisible(Regex("CONECTADO ·"))
        assertNoVisible("Sin datos desde hace")

        // La alerta emitida durante el corte llega ahora, con su hora original.
        pestana("Alertas")
        assertVisible("Enlace perdido: me detuve en")
        assertVisible(hhmm.format(Instant.ofEpochMilli(corte)))
        pestana("Supervisión")

        // El robot quedó detenido esperando al operador: se puede reanudar.
        assertVisible("DETENIDO")
        assertTrue(ControlSession.NO_CONTROL_REASON.isNotEmpty())
        tocar("Reanudar")
        esperarRobot("en ronda otra vez") { estado() == OperationalState.EN_RONDA }
        assertVisible("EN RONDA")
    }
}
