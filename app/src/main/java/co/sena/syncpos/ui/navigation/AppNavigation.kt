package co.sena.syncpos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.sena.syncpos.SyncPosApp
import co.sena.syncpos.ui.admin.AdminShell
import co.sena.syncpos.ui.history.HistoryScreen
import co.sena.syncpos.ui.login.LoginScreen
import co.sena.syncpos.ui.pos.PosScreen

// Las rutas (pantallas) de la app. Una ruta es solo un texto que identifica la pantalla.
object Routes {
    const val LOGIN = "login"
    const val POS = "pos"           // cajero
    const val HISTORY = "historial" // cajero
    const val ADMIN = "admin"       // administrador (4 pestanas dentro)
}

// NavHost es el "contenedor" que muestra la pantalla de la ruta actual.
// NavController es el control remoto para cambiar de ruta (navigate / popBackStack).
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Obtenemos el contenedor de dependencias creado en la clase Application.
    val container = (LocalContext.current.applicationContext as SyncPosApp).container

    // Ruta de inicio segun la sesion guardada:
    //  - sin sesion            -> Login
    //  - sesion de admin       -> pantallas de administrador
    //  - sesion de cajero      -> POS (el catalogo ya esta en Room)
    val start = homeRoute(container.session.isLoggedIn, container.session.role)

    // Si en cualquier pantalla el servidor responde 401 (token vencido), el repositorio
    // borra la sesion y emite este aviso: aqui se lleva al usuario al Login.
    LaunchedEffect(Unit) {
        container.sessionEvents.expired.collect {
            navController.navigate(Routes.LOGIN) {
                // Vacia TODA la pila (hasta la raiz del grafo) para que "atras" no regrese.
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onLoggedIn = {
                    // El rol se guardo en la sesion durante el login: decide a donde ir.
                    navController.navigate(homeRoute(true, container.session.role)) {
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
        composable(Routes.ADMIN) {
            AdminShell(
                container = container,
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ADMIN) { inclusive = true }
                    }
                },
                onNewProduct = { },
                onEditProduct = { }
            )
        }
    }
}

// Decide la pantalla principal segun la sesion. Funcion pura (facil de probar).
fun homeRoute(isLoggedIn: Boolean, role: String): String = when {
    !isLoggedIn -> Routes.LOGIN
    role == "admin" -> Routes.ADMIN
    else -> Routes.POS
}
