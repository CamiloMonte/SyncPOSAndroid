package co.sena.syncpos.ui.admin.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.AdminProductDto
import co.sena.syncpos.ui.admin.AdminTopBar
import co.sena.syncpos.ui.admin.ErrorState
import co.sena.syncpos.ui.admin.LoadingState
import co.sena.syncpos.ui.admin.TextPill
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerBg
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SenaGreenSoft
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop

// Pestana "Inventario".
// lowStockRequest: contador que sube cuando el Dashboard pide abrir con "Stock bajo".
@Composable
fun InventoryScreen(
    container: AppContainer,
    isOnline: Boolean,
    lowStockRequest: Int,
    onLogout: () -> Unit,
    onNewProduct: () -> Unit,
    onEditProduct: (Long) -> Unit
) {
    val viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mensajes de una sola vez -> Snackbar (la barrita negra de abajo).
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // LaunchedEffect(lowStockRequest) se ejecuta cada vez que CAMBIA ese numero.
    LaunchedEffect(lowStockRequest) {
        viewModel.handleLowStockRequest(lowStockRequest)
    }

    // Dialogo de confirmacion para activar / desactivar.
    state.pendingToggle?.let { product ->
        val deactivating = product.active
        AlertDialog(
            onDismissRequest = viewModel::dismissToggle,
            title = { Text(if (deactivating) "¿Desactivar producto?" else "¿Activar producto?") },
            text = {
                Text(
                    if (deactivating) {
                        "\"${product.name}\" dejará de aparecer para vender en el punto de venta."
                    } else {
                        "\"${product.name}\" volverá a estar disponible para vender."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmToggle) {
                    Text(if (deactivating) "Desactivar" else "Activar", color = if (deactivating) DangerRed else SenaGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissToggle) { Text("Cancelar", color = TextSecondary) }
            }
        )
    }

    // Scaffold interno: aporta los "huecos" para la barra superior, el Snackbar y el boton flotante.
    Scaffold(
        containerColor = BackgroundGray,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdminTopBar(
                title = "Inventario",
                subtitle = "${state.total} productos",
                isOnline = isOnline,
                onLogout = onLogout
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProduct,
                containerColor = SenaGreen,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SearchAndFilters(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSelectFilter = viewModel::selectFilter
            )

            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::retry)
                else -> ProductList(
                    state = state,
                    onLoadMore = viewModel::loadMore,
                    onEdit = onEditProduct,
                    onToggle = viewModel::askToggle
                )
            }
        }
    }
}

@Composable
private fun SearchAndFilters(
    state: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onSelectFilter: (InventoryFilter) -> Unit
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar por nombre o código...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SenaGreen,
                unfocusedBorderColor = BorderGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = SenaGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Chips: Todas, Stock bajo y una por cada categoria. LazyRow permite desplazarlas
        // de lado cuando son muchas.
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { FilterOption("Todas", state.filter == InventoryFilter.All) { onSelectFilter(InventoryFilter.All) } }
            item {
                FilterOption("Stock bajo", state.filter == InventoryFilter.LowStock) {
                    onSelectFilter(InventoryFilter.LowStock)
                }
            }
            items(state.categories, key = { it.id }) { category ->
                val filter = InventoryFilter.Category(category.id)
                FilterOption(category.nombre, state.filter == filter) { onSelectFilter(filter) }
            }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SenaGreen,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun ProductList(
    state: InventoryUiState,
    onLoadMore: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (AdminProductDto) -> Unit
) {
    if (state.products.isEmpty()) {
        Text(
            text = "No hay productos para mostrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(24.dp)
        )
        return
    }

    // LazyColumn solo dibuja las filas visibles (como RecyclerView): sirve para listas largas.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 88dp abajo para que el boton flotante no tape la ultima fila.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // key = id estable de cada fila: Compose reutiliza bien las filas al cambiar la lista.
        items(state.products, key = { it.id }) { product ->
            ProductCard(product = product, onClick = { onEdit(product.id) }, onToggle = { onToggle(product) })
        }

        // Ultimo elemento: boton "Cargar mas" (o el circulo mientras se pide la pagina).
        if (state.hasMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(color = SenaGreen, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        OutlinedButton(onClick = onLoadMore, shape = RoundedCornerShape(12.dp)) {
                            Text("Cargar más", color = SenaGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: AdminProductDto, onClick: () -> Unit, onToggle: () -> Unit) {
    // Un producto inactivo se ve "apagado" (50 % de opacidad).
    AppCard(modifier = Modifier.alpha(if (product.active) 1f else 0.5f).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Miniatura: cuadro verde suave con el icono de inventario.
            Box(
                modifier = Modifier.size(44.dp).background(SenaGreenSoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Inventory, contentDescription = null, tint = SenaGreen)
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.barcode ?: "-"} · ${product.category?.nombre ?: "Sin categoría"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Costo ${formatCop(product.costPrice)} · Venta ${formatCop(product.salePrice)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${product.stock} und.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (product.lowStock) DangerRed else DarkSlate
                )
                // Un solo estado visible por producto; "Inactivo" tiene prioridad.
                when {
                    !product.active -> TextPill("Inactivo", BorderGray, TextSecondary)
                    product.lowStock -> TextPill("Stock Bajo", DangerBg, DangerRed)
                    else -> TextPill("Normal", OnlineBg, OnlineText)
                }
            }

            // Boton pequeno para activar / desactivar (abre el dialogo de confirmacion).
            IconButton(onClick = onToggle) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = if (product.active) "Desactivar" else "Activar",
                    tint = if (product.active) TextSecondary else SenaGreen
                )
            }
        }
    }
}
