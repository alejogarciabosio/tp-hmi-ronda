package ar.edu.uade.ronda.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.model.CommandUiState
import ar.edu.uade.ronda.ui.theme.RondaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Estados visibles de los comandos (R9): el motivo se lee y el toque no pasa cuando no corresponde. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandButtonUiTest {
    @get:Rule val rule = createComposeRule()

    private fun show(ui: CommandButtonUi, onClick: () -> Unit) = rule.setContent { RondaTheme { CommandButton(ui, onClick) } }

    @Test fun deshabilitadoMuestraElMotivoYNoEnvia() {
        var clicks = 0
        show(CommandButtonUi("Reanudar", CommandKind.SECONDARY, CommandUiState.DISABLED, "No hay misión pausada")) { clicks++ }
        rule.onNodeWithText("No hay misión pausada").assertExists()
        rule.onNodeWithContentDescription("Reanudar. No disponible", substring = true).assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test fun pendienteNoSePuedeRepetir() {
        var clicks = 0
        show(CommandButtonUi("Pausar", CommandKind.SECONDARY, CommandUiState.PENDING, "Esperando confirmación del robot")) { clicks++ }
        rule.onNodeWithContentDescription("Estado de la orden: pendiente", substring = true).assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test fun rechazadaMuestraElMotivoYSePuedeReintentar() {
        var clicks = 0
        show(CommandButtonUi("Iniciar ronda", CommandKind.PRIMARY, CommandUiState.REJECTED, "Rechazada: batería insuficiente")) { clicks++ }
        rule.onNodeWithText("Rechazada: batería insuficiente").assertExists()
        rule.onNodeWithContentDescription("Iniciar ronda", substring = true).assertIsEnabled().assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test fun sinRespuestaAvisaQueNoSeSabeSiSeEjecuto() {
        show(CommandButtonUi("Regresar a base", CommandKind.PRIMARY, CommandUiState.NO_RESPONSE, "Sin respuesta: no se sabe si se ejecutó")) {}
        rule.onNodeWithContentDescription("Estado de la orden: sin respuesta", substring = true).assertIsEnabled()
        rule.onNodeWithText("Sin respuesta: no se sabe si se ejecutó").assertExists()
    }
}
