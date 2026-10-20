package co.sena.syncpos.ui.pos

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.ui.components.ConnectionPill
import co.sena.syncpos.ui.components.OfflineBanner
import co.sena.syncpos.ui.components.PrimaryButton
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// Pantalla principal de venta (Punto de Venta).
@Composable
fun PosScreen(
    container: AppContainer,
    onOpenHistory: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val viewModel: PosViewModel = viewModel(factory = PosViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // remember: guarda un objeto entre redibujados. Sin remember, el SnackbarHostState se
    // crearia de nuevo cada vez que Compose redibuja y se perderian los mensajes.
    val snackbarHostState = remember { SnackbarHostState() }

    // Escucha los mensajes del ViewModel y los muestra como Snackbar.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = BackgroundGray,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosTopBar(
                userName = state.userName,
                isOnline = state.isOnline,
                pendingCount = state.pendingCount,
                onOpenHistory = onOpenHistory,
                onLogout = { viewModel.logout(onLoggedOut) }
            )
        },
        bottomBar = {
            CheckoutBar(
                cart = state.cart,
                paymentMethod = state.paymentMethod,
                isSaving = state.isSaving,
                onSelectPayment = viewModel::selectPayment,
                onRegister = viewModel::registerSale
            )
        }
    ) { innerPadding ->
        // LazyColumn = lista con scroll que solo dibuja lo que se ve en pantalla.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.isOnline) {
                item { OfflineBanner() }
            }

            // Campo de busqueda
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Buscar producto por nombre...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SenaGreen,
                        unfocusedBorderColor = BorderGray,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        cursorColor = SenaGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Resultados de la busqueda (key = id para que Compose identifique cada fila)
            items(state.searchResults, key = { "result-${it.id}" }) { product ->
                ProductResultRow(product = product, onClick = { viewModel.addProduct(product) })
            }

            if (state.query.isNotBlank() && state.searchResults.isEmpty()) {
                item {
                    Text(
                        "No hay productos que coincidan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Boton gigante del escaner
            item {
                PrimaryButton(
                    text = "ACTIVAR ESCÁNER DE CÓDIGO",
                    icon = Icons.Filled.QrCodeScanner,
                    height = 72.dp,
                    onClick = {
                        startBarcodeScan(
                            context = context,
                            onResult = viewModel::onBarcodeScanned,
                            onError = viewModel::onScanError
                        )
                    }
                )
            }

            // Encabezado del carrito
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Carrito (${plural(state.cart.productCount, "producto", "productos")})",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkSlate,
                        modifier = Modifier.weight(1f)
                    )
                    if (!state.cart.isEmpty) {
                        TextButton(onClick = viewModel::clearCart) {
                            Text("Vaciar", color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.cart.isEmpty) {
                item {
                    Text(
                        text = "Escanea o busca un producto para empezar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.cart.items, key = { "cart-${it.productId}" }) { item ->
                    CartItemCard(
                        item = item,
                        onIncrement = { viewModel.increment(item.productId) },
                        onDecrement = { viewModel.decrement(item.productId) },
                        onRemove = { viewModel.remove(item.productId) }
                    )
                }
            }
        }
    }
}

// Barra superior: tienda, cajero, estado de conexion y acciones.
@Composable
private fun PosTopBar(
    userName: String,
    isOnline: Boolean,
    pendingCount: Int,
    onOpenHistory: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .statusBarsPadding() // evita que quede debajo de la barra de estado del sistema
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tienda Centro", style = MaterialTheme.typography.titleMedium, color = DarkSlate)
            Text(
                "Cajero: $userName",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ConnectionPill(isOnline = isOnline, pendingCount = pendingCount)
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Filled.History, contentDescription = "Historial", tint = DarkSlate)
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión", tint = DarkSlate)
        }
    }
    // Linea separadora
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderGray)
    )
}

// Abre el escaner de Google (Google Code Scanner). Viene con Google Play Services, por lo
// que NO necesita permiso de camara ni pantalla propia. Es asincrono: se registran
// "listeners" que se llaman cuando termina bien o mal.
private fun startBarcodeScan(
    context: Context,
    onResult: (String) -> Unit,
    onError: (String?) -> Unit
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128
        )
        .build()

    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            barcode.rawValue?.let(onResult)
        }
        .addOnFailureListener { error ->
            // Por ejemplo: el celular no tiene Google Play Services.
            onError(error.message)
        }
        // Si el usuario cierra el escaner sin leer nada, simplemente no se hace nada.
}
