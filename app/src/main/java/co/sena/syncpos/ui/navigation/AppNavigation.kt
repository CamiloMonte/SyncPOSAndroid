package co.sena.syncpos.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.sena.syncpos.SyncPosApp
import co.sena.syncpos.ui.login.LoginScreen

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
        composable(Routes.POS) { Placeholder("Punto de Venta") }
        composable(Routes.HISTORY) { Placeholder("Historial") }
    }
}

// Pantalla temporal; se reemplaza en las siguientes tareas.
@Composable
private fun Placeholder(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}
