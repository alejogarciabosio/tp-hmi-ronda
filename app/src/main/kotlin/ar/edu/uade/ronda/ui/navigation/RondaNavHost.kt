package ar.edu.uade.ronda.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.ronda.ui.components.NavBarRoutes
import ar.edu.uade.ronda.ui.operacion.OperationalBarViewModel
import ar.edu.uade.ronda.ui.operacion.ProvideOperationalBar
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.alertas.AlertasRoute
import ar.edu.uade.ronda.ui.alertas.AlertasViewModel
import ar.edu.uade.ronda.ui.control.ControlRoute
import ar.edu.uade.ronda.ui.control.ControlViewModel
import ar.edu.uade.ronda.ui.desconexion.DesconexionRoute
import ar.edu.uade.ronda.ui.desconexion.DesconexionViewModel
import ar.edu.uade.ronda.ui.historial.HistorialRoute
import ar.edu.uade.ronda.ui.historial.HistorialViewModel
import ar.edu.uade.ronda.ui.misiones.MisionesRoute
import ar.edu.uade.ronda.ui.misiones.MisionesViewModel
import ar.edu.uade.ronda.ui.seleccion.SeleccionRoute
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.simulacion.SimulacionRoute
import ar.edu.uade.ronda.ui.simulacion.SimulacionViewModel
import ar.edu.uade.ronda.ui.telemetria.TelemetriaRoute
import ar.edu.uade.ronda.ui.telemetria.TelemetriaViewModel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun RondaNavHost(
    viewModelFactory: ViewModelProvider.Factory,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    startDestination: Route = Route.SUPERVISION,
) {
    // La barra fija (detención operativa e insignia de alertas) es una sola para todas las pantallas.
    val bar: OperationalBarViewModel = viewModel(factory = viewModelFactory)
    ProvideOperationalBar(bar) { RondaNavGraph(viewModelFactory, navController, modifier, startDestination) }
}

@Composable
private fun RondaNavGraph(
    viewModelFactory: ViewModelProvider.Factory,
    navController: NavHostController,
    modifier: Modifier,
    startDestination: Route,
) {
    NavHost(navController, startDestination.path, modifier) {
        val go: (Route) -> Unit = { navController.navigateTo(it) }
        val back: () -> Unit = { navController.popBackStack() }
        composable(Route.SUPERVISION.path) {
            val vm: SupervisionViewModel = viewModel(factory = viewModelFactory)
            SupervisionRoute(vm, go)
        }
        composable(Route.SELECCION.path) {
            val vm: SeleccionViewModel = viewModel(factory = viewModelFactory)
            SeleccionRoute(vm, go)
        }
        composable(Route.TELEMETRIA.path) {
            val vm: TelemetriaViewModel = viewModel(factory = viewModelFactory)
            TelemetriaRoute(vm, go, back)
        }
        composable(Route.MISIONES.path) {
            val vm: MisionesViewModel = viewModel(factory = viewModelFactory)
            MisionesRoute(vm, go)
        }
        composable(Route.CONTROL.path) {
            val vm: ControlViewModel = viewModel(factory = viewModelFactory)
            ControlRoute(vm, go, back)
        }
        composable(Route.ALERTAS.path) {
            val vm: AlertasViewModel = viewModel(factory = viewModelFactory)
            AlertasRoute(vm, go)
        }
        composable(Route.HISTORIAL.path) {
            val vm: HistorialViewModel = viewModel(factory = viewModelFactory)
            HistorialRoute(vm, go)
        }
        composable(Route.DESCONEXION.path) {
            val vm: DesconexionViewModel = viewModel(factory = viewModelFactory)
            DesconexionRoute(vm, go, back)
        }
        composable(Route.SIMULACION.path) {
            val vm: SimulacionViewModel = viewModel(factory = viewModelFactory)
            SimulacionRoute(vm, back)
        }
    }
}

/**
 * Las pestañas no se apilan: se vuelve a Supervisión (sin sacarla) y se guarda el estado de la
 * pestaña que se deja, como pide la navegación inferior de Material. El resto se apila una vez.
 */
fun NavHostController.navigateTo(route: Route) {
    if (route in NavBarRoutes) {
        navigate(route.path) {
            popUpTo(Route.SUPERVISION.path) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route.path) { launchSingleTop = true }
    }
}

