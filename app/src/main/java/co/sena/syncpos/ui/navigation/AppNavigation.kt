package co.sena.syncpos.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) { Placeholder("Login") }
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
