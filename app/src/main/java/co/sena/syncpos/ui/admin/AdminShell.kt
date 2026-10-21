package co.sena.syncpos.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.sena.syncpos.AppContainer
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SenaGreenIndicator
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

// Las 4 pestanas del administrador: texto, icono y posicion en la barra inferior.
// "enum class" = lista cerrada de valores; "entries" da todos en orden.
enum class AdminTab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Dashboard),
    INVENTORY("Inventario", Icons.Filled.Inventory),
    REPORTS("Reportes", Icons.Filled.BarChart),
    SALES("Ventas", Icons.AutoMirrored.Filled.ReceiptLong)
}

// "Cascaron" (shell) del administrador: una barra inferior fija con 4 pestanas y, encima,
// el contenido de la pestana elegida.
//
// DECISION DE DISENO: en vez de un segundo NavHost anidado se usa un simple estado
// "pestana seleccionada" + un "when". Es mas facil de entender y aqui no hace falta nada
// mas: las pestanas no tienen subpantallas ni argumentos. El formulario de producto SI
// es una ruta del NavHost principal (se abre encima de todo y esconde la barra inferior).
//
// Los ViewModel de cada pestana se crean con viewModel(), que los asocia a la entrada de
// navegacion del admin (no al "when"). Por eso, al cambiar de pestana y volver, la lista,
// el texto de busqueda y el scroll de datos siguen ahi sin volver a pedirlos al servidor.
@Composable
fun AdminShell(
    container: AppContainer,
    onLoggedOut: () -> Unit,
    onNewProduct: () -> Unit,
    onEditProduct: (Long) -> Unit
) {
    // rememberSaveable (a diferencia de remember) sobrevive a la rotacion de pantalla y a
    // que Android destruya y recree la Activity. Se guarda el indice porque es un Int.
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = AdminTab.entries[selectedIndex]

    // Cada vez que este numero sube, el inventario aplica el filtro "Stock bajo".
    // Asi el Dashboard puede pedirlo al tocar la tarjeta roja de alertas.
    var lowStockRequest by rememberSaveable { mutableIntStateOf(0) }

    val isOnline by container.connectivity.isOnline.collectAsStateWithLifecycle(initialValue = true)

    // rememberCoroutineScope da una corrutina ligada a esta pantalla para el cierre de sesion.
    val scope = rememberCoroutineScope()
    val logout: () -> Unit = {
        scope.launch {
            container.authRepository.logout() // avisa al servidor y borra la sesion local
            onLoggedOut()
        }
    }

    Scaffold(
        containerColor = BackgroundGray,
        // Cada pestana dibuja su propia barra superior y maneja la franja de estado,
        // por eso aqui se quitan los insets (si no, el espacio se contaria dos veces).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = SurfaceWhite) {
                AdminTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SenaGreen,
                            selectedTextColor = SenaGreen,
                            indicatorColor = SenaGreenIndicator,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // innerPadding ya incluye la altura de la barra inferior: se aplica al contenido.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                AdminTab.HOME -> PlaceholderTab("Inicio", isOnline, logout)
                AdminTab.INVENTORY -> PlaceholderTab("Inventario", isOnline, logout)
                AdminTab.REPORTS -> PlaceholderTab("Reportes", isOnline, logout)
                AdminTab.SALES -> PlaceholderTab("Ventas", isOnline, logout)
            }
        }
    }
}

// Contenido temporal de cada pestana (se reemplaza por la pantalla real en las
// siguientes tareas).
@Composable
private fun PlaceholderTab(title: String, isOnline: Boolean, onLogout: () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        AdminTopBar(title = title, subtitle = "Próximamente", isOnline = isOnline, onLogout = onLogout)
        Box(modifier = Modifier.fillMaxSize().padding(PaddingValues(16.dp))) {
            Text("En construcción")
        }
    }
}
