package ar.edu.uade.ronda.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.OperationalState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Escenario 5: persona en la sala técnica (PC5). */
class Escenario5PersonaE2eTest : E2eTest() {

    private val hoja = "EL ROBOT ESPERA TU DECISIÓN"

    @Test fun enviarVigilanteUnaSolaVezSinQueLaHojaVuelva() {
        prepararEIniciar("Persona en zona restringida")
        esperarRobot("decisión por persona", maxMs = 900_000) { incidenteAbierto() }
        pasar(1_000)
        assertVisible(hoja)
        assertVisible("Persona en zona restringida · PC5")
        assertVisible("Foto de la cámara frontal del robot")
        assertVisible("Persona autorizada (registrar)")
        assertVisible("Enviar vigilante")
        assertVisible("Continuar ronda")
        assertVisible("Persona cerca del robot")

        // Doble toque sobre "Enviar vigilante": el segundo llega antes de que la hoja se redibuje.
        settle()
        val boton = rule.onAllNodes(hasText("Enviar vigilante", ignoreCase = true) and hasClickAction()).onFirst()
        boton.performSemanticsAction(SemanticsActions.OnClick)
        runCatching { boton.performSemanticsAction(SemanticsActions.OnClick) }
        app.scheduler.runCurrent()
        settle()
        assertNoVisible(hoja)

        esperarRobot("pausado esperando al vigilante") { estado() == OperationalState.PAUSADA }
        pasar(5_000)
        assertNoVisible(hoja)
        assertVisible("Vigilante en camino · el robot espera tu orden para seguir")
        assertVisible("PAUSADA")

        // Una sola orden: lo dice el registro de Control.
        tocar("Más controles")
        assertEquals(
            "Una sola orden 'Enviar vigilante' en el registro: ${textos()}",
            1, textos().count { it.contains("Reconocer alerta · Enviar vigilante") && !it.startsWith("Última") },
        )
        assertEquals(1, app.container.robotRepository.commands.value.count { it.command.type == CommandType.ACKNOWLEDGE_ALERT })
        tocar("Volver")

        // Historial: la decisión queda registrada y no se guarda la foto.
        pestana("Historial")
        assertVisible("Orden: Reconocer alerta · Enviar vigilante")
        assertVisible("Persona en zona restringida")
        assertNoVisible("Foto de la cámara")

        // Vuelve a Supervisión: la hoja sigue cerrada y el operador reanuda.
        pestana("Supervisión")
        assertNoVisible(hoja)
        tocar("Reanudar")
        esperarRobot("en ronda") { estado() == OperationalState.EN_RONDA }
        pasar(2_000)
        assertNoVisible(hoja)
        assertNoVisible("Vigilante en camino")
    }
}
