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
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.seleccion.SeleccionRoute
import ar.edu.uade.ronda.ui.seleccion.SeleccionViewModel
import ar.edu.uade.ronda.ui.supervision.SupervisionRoute
import ar.edu.uade.ronda.ui.supervision.SupervisionViewModel
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
fun RondaNavHost(
    viewModelFactory: ViewModelProvider.Factory,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    startDestination: Route = Route.SUPERVISION,
) {
    NavHost(navController, startDestination.path, modifier) {
        val go: (Route) -> Unit = { navController.navigate(it.path) { launchSingleTop = true } }
        composable(Route.SUPERVISION.path) {
            val vm: SupervisionViewModel = viewModel(factory = viewModelFactory)
            SupervisionRoute(vm, go)
        }
        composable(Route.SELECCION.path) {
            val vm: SeleccionViewModel = viewModel(factory = viewModelFactory)
            SeleccionRoute(vm, go)
        }
        Route.entries.filter { it != Route.SUPERVISION && it != Route.SELECCION }.forEach { route ->
            composable(route.path) { PlaceholderScreen(route, go) }
        }
    }
}

/** Pantalla pendiente de F6, con accesos a las demás para poder recorrer la navegación. */
@Composable
fun PlaceholderScreen(current: Route, onNavigate: (Route) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(current.title, style = RondaType.pantallaTitulo, color = RondaColors.bone100, modifier = Modifier.semantics { heading() })
        Text("Pendiente — F6", style = RondaType.datoNormal, color = RondaColors.signalAmber)
        Text("Ir a:", style = RondaType.rotuloSeccion, color = RondaColors.bone400)
        Route.entries.filter { it != current }.forEach { r ->
            CommandButton(CommandButtonUi(r.title, CommandKind.SECONDARY), { onNavigate(r) })
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14130F, widthDp = 412, heightDp = 900)
@Composable
private fun PlaceholderPreview() = PreviewSurface { PlaceholderScreen(Route.SUPERVISION) {} }
