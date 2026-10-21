package co.sena.syncpos.ui.admin.sales

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.data.remote.SaleItemDto
import co.sena.syncpos.domain.PaymentMethod
import co.sena.syncpos.domain.buildReceiptText
import co.sena.syncpos.domain.formatSaleDateTime
import co.sena.syncpos.domain.isOfflineOrigin
import co.sena.syncpos.domain.originLabel
import co.sena.syncpos.ui.admin.AdminTopBar
import co.sena.syncpos.ui.admin.ErrorState
import co.sena.syncpos.ui.admin.LoadingState
import co.sena.syncpos.ui.admin.SaleRow
import co.sena.syncpos.ui.admin.TextPill
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OfflineBg
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop

// Pestana "Ventas" (historial de todas las ventas) + hoja de detalle de factura.
@Composable
fun SalesScreen(container: AppContainer, isOnline: Boolean, onLogout: () -> Unit) {
    val viewModel: SalesViewModel = viewModel(factory = SalesViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // La hoja inferior solo existe en pantalla mientras haya una factura seleccionada.
    state.detail?.let { detail ->
        InvoiceSheet(
            detail = detail,
            onDismiss = viewModel::closeDetail,
            onRetry = viewModel::retryDetail
        )
    }

    Scaffold(
        containerColor = BackgroundGray,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdminTopBar(
                title = "Historial de Ventas",
                subtitle = "${state.total} ventas",
                isOnline = isOnline,
                onLogout = onLogout
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("N° factura o cajero...") },
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

                // Chips de metodo de pago: null = todas.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    MethodChip("Todas", state.method == null) { viewModel.selectMethod(null) }
                    PaymentMethod.entries.forEach { method ->
                        MethodChip(method.label, state.method == method) { viewModel.selectMethod(method) }
                    }
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::loadFirstPage)
                state.sales.isEmpty() -> Text(
                    "No hay ventas para mostrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(24.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.sales, key = { it.id }) { sale ->
                        SaleRow(sale = sale, onClick = { viewModel.openDetail(sale.id) })
                    }
                    if (state.hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(
                                        color = SenaGreen,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::loadMore,
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Cargar más", color = SenaGreen) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

// ModalBottomSheet = hoja que sube desde abajo y bloquea lo de atras. Se cierra
// deslizando hacia abajo o tocando fuera (onDismissRequest).
// skipPartiallyExpanded = true: se abre completa directamente (sin parada a media altura).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceSheet(detail: SaleDetailState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Detalle de Factura", style = MaterialTheme.typography.titleMedium, color = DarkSlate)

            when (detail) {
                SaleDetailState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = SenaGreen) }

                is SaleDetailState.Error -> Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    ErrorState(detail.message, onRetry = onRetry)
                }

                is SaleDetailState.Content -> InvoiceContent(detail.sale)
            }
        }
    }
}

@Composable
private fun InvoiceContent(sale: SaleDto) {
    val context = LocalContext.current
    val offline = isOfflineOrigin(sale.origin)

    // Encabezado: numero, cajero, fecha y pastillas de metodo / origen.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(sale.number, style = MaterialTheme.typography.titleLarge, color = DarkSlate)
        Text(
            "Cajero: ${sale.cashier?.name ?: "Sin cajero"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            "Fecha: ${formatSaleDateTime(sale.soldAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextPill(PaymentMethod.fromApiValue(sale.paymentMethod).label, BorderGray, DarkSlate)
            TextPill(
                originLabel(sale.origin),
                if (offline) OfflineBg else OnlineBg,
                if (offline) OfflineText else OnlineText
            )
        }
    }

    HorizontalDivider(color = BorderGray)

    sale.items.forEach { InvoiceItemRow(it) }

    HorizontalDivider(color = BorderGray)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Total", style = MaterialTheme.typography.titleMedium, color = DarkSlate, modifier = Modifier.weight(1f))
        Text(
            formatCop(sale.total),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SenaGreen
        )
    }

    // Compartir la factura como texto plano. Mas adelante se podria generar un PDF de la
    // factura (como en Reportes), pero un texto ya se puede enviar por WhatsApp o correo.
    OutlinedButton(
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildReceiptText(sale))
                putExtra(Intent.EXTRA_SUBJECT, "Factura ${sale.number}")
            }
            context.startActivity(Intent.createChooser(send, "Compartir factura"))
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Filled.Share, contentDescription = null, tint = DarkSlate, modifier = Modifier.size(20.dp))
        Text("  Compartir", color = DarkSlate)
    }
}

@Composable
private fun InvoiceItemRow(item: SaleItemDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name ?: "Producto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Text(
                "${item.quantity} × ${formatCop(item.unitPrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Text(
            formatCop(item.subtotal),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
    }
}
