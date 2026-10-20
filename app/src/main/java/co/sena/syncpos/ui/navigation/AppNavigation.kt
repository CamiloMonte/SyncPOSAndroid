package co.sena.syncpos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.sena.syncpos.SyncPosApp
import co.sena.syncpos.ui.history.HistoryScreen
import co.sena.syncpos.ui.login.LoginScreen
import co.sena.syncpos.ui.pos.PosScreen

// Las 3 rutas (pantallas) de la app. Una ruta es solo un texto que identifica la pantalla.
object Routes {
    const val LOGIN = "login"
    const val POS = "pos"
    const val HISTORY = "historial"
}

// NavHost es el "contenedor" que muestra la pantalla de la ruta actual.
// NavController es el control remoto para cambiar de ruta (navigate / popBackStack).
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Obtenemos el contenedor de dependencias creado en la clase Application.
    val container = (LocalContext.current.applicationContext as SyncPosApp).container

    // Si ya hay sesion guardada se entra directo al POS (el catalogo ya esta en Room).
    val start = if (container.session.isLoggedIn) Routes.POS else Routes.LOGIN

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(Routes.POS) {
                        // Quita el login de la pila: el boton "atras" no vuelve a el.
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.POS) {
            PosScreen(
                container = container,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        // Vacia la pila: desde el login "atras" no regresa al POS.
                        popUpTo(Routes.POS) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                container = container,
                onBack = { navController.popBackStack() } // vuelve a la pantalla anterior (POS)
            )
        }
    }
}
