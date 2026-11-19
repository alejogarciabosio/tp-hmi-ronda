package ar.edu.uade.ronda.ui.components

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import ar.edu.uade.ronda.ui.theme.RondaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Órdenes destructivas: un toque accidental no alcanza (riesgo de orden accidental). */
@RunWith(RobolectricTestRunner::class)
// Application vacía: RondaApp arma AppContainer con Room real y sus trabajos siguen corriendo entre tests.
@Config(sdk = [34], application = Application::class)
class HoldToConfirmUiTest {
    @get:Rule val rule = createComposeRule()

    private var confirmed = 0

    private fun showStop(enabled: Boolean = true) =
        rule.setContent { RondaTheme { OperationalStopButton({ confirmed++ }, enabled = enabled) } }

    private val stop get() = rule.onNodeWithContentDescription(OperationalStopTexts.LABEL, substring = true)

    @Test fun siempreMuestraQueNoReemplazaLaParadaFisica() {
        showStop()
        rule.onNodeWithText(OperationalStopTexts.NOTE).assertExists()
    }

    @Test fun unToqueCortoNoConfirma() {
        showStop()
        stop.performTouchInput {
            down(center)
            advanceEventTime(300)
            up()
        }
        rule.mainClock.advanceTimeBy(1_500)
        assertEquals(0, confirmed)
    }

    @Test fun mantenerUnSegundoConfirma() {
        rule.mainClock.autoAdvance = false
        showStop()
        stop.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_200)
        stop.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, confirmed)
    }

    @Test fun lectorDePantallaConfirmaConAccionDeMantener() {
        showStop()
        stop.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, confirmed)
    }

    @Test fun deshabilitadoNoConfirma() {
        showStop(enabled = false)
        stop.performTouchInput {
            down(center)
            advanceEventTime(1_500)
            up()
        }
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(0, confirmed)
    }
}
