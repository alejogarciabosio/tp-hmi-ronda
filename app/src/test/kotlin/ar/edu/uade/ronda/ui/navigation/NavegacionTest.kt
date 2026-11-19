package ar.edu.uade.ronda.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las pestañas no se apilan: recorrerlas varias veces deja una pila corta y vuelve a la misma Supervisión. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavegacionTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun stack() = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test fun lasPestanasNoSeApilan() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, Route.SELECCION.path) {
                Route.entries.forEach { r -> composable(r.path) { Text(r.title) } }
            }
        }
        rule.runOnIdle { nav.navigateTo(Route.SUPERVISION) }
        rule.waitForIdle()
        val supervision = nav.currentBackStackEntry!!.id
        listOf(Route.MISIONES, Route.ALERTAS, Route.HISTORIAL, Route.SUPERVISION, Route.MISIONES, Route.HISTORIAL)
            .forEach { r -> rule.runOnIdle { nav.navigateTo(r) }; rule.waitForIdle() }
        assertEquals(listOf("seleccion", "supervision", "historial"), stack())

        rule.runOnIdle { nav.navigateTo(Route.SUPERVISION) }
        rule.waitForIdle()
        assertEquals(listOf("seleccion", "supervision"), stack())
        // Es la misma entrada (y por lo tanto el mismo ViewModel): la hoja cerrada no reaparece.
        assertEquals(supervision, nav.currentBackStackEntry!!.id)

        rule.runOnIdle { nav.navigateTo(Route.CONTROL); nav.navigateTo(Route.CONTROL) }
        rule.waitForIdle()
        assertEquals(listOf("seleccion", "supervision", "control"), stack())
    }
}
