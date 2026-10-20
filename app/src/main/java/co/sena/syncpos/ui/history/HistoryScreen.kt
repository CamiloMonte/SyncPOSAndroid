package co.sena.syncpos.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.local.SaleWithItems
import co.sena.syncpos.domain.PaymentMethod
import co.sena.syncpos.ui.components.ConnectionPill
import co.sena.syncpos.ui.components.PrimaryButton
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerBg
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OfflineAmber
import co.sena.syncpos.ui.theme.OfflineBg
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineGreen
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop
import co.sena.syncpos.util.lastSyncText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanishLocale = Locale.forLanguageTag("es-CO")

// Pantalla "Historial y Sincronizacion".
@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = BackgroundGray,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HistoryTopBar(
                isOnline = state.isOnline,
                pendingCount = state.allPendingCount,
                onBack = onBack
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    text = if (state.isSyncing) "Sincronizando..." else "Forzar Sincronización Ahora",
                    icon = Icons.Filled.Refresh,
                    enabled = state.allPendingCount > 0,
                    loading = state.isSyncing,
                    onClick = viewModel::forceSync
                )
                Text(
                    text = "Última sincronización: ${lastSyncText(state.lastSyncAt, state.now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SummaryCard(state) }

            // Chips de filtro
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.entries.forEach { option ->
                        FilterChip(
                            selected = state.filter == option,
                            onClick = { viewModel.selectFilter(option) },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SenaGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (state.sales.isEmpty()) {
                item {
                    Text(
                        text = "No hay ventas para mostrar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.sales, key = { it.sale.localId }) { saleWithItems ->
                    SaleCard(saleWithItems)
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(isOnline: Boolean, pendingCount: Int, onBack: () -> Unit) {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanishLocale))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = DarkSlate)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Ventas de Hoy", style = MaterialTheme.typography.titleMedium, color = DarkSlate)
            Text(today, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        ConnectionPill(isOnline = isOnline, pendingCount = pendingCount)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderGray))
}

// Tarjeta oscura con el resumen del dia.
@Composable
private fun SummaryCard(state: HistoryUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSlate, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Total vendido hoy", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
        Text(
            formatCop(state.totalToday),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = if (state.salesCount == 1) "1 venta" else "${state.salesCount} ventas",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCBD5E1)
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("${state.syncedCount} sincronizadas", OnlineBg, OnlineText)
            StatusPill("${state.pendingCount} pendientes", OfflineBg, OfflineText)
        }
    }
}

@Composable
private fun StatusPill(text: String, background: Color, textColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Tarjeta de una venta del historial.
@Composable
private fun SaleCard(item: SaleWithItems) {
    val sale = item.sale
    val method = PaymentMethod.fromApiValue(sale.paymentMethod)
    val time = Instant.ofEpochMilli(sale.soldAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("hh:mm a", spanishLocale))

    // Segun el estado cambian el icono, el color y la etiqueta.
    val icon = when (sale.status) {
        SaleStatus.SINCRONIZADA -> Icons.Filled.CheckCircle
        SaleStatus.PENDIENTE -> Icons.Filled.Schedule
        SaleStatus.RECHAZADA -> Icons.Filled.ErrorOutline
    }
    val iconColor = when (sale.status) {
        SaleStatus.SINCRONIZADA -> OnlineGreen
        SaleStatus.PENDIENTE -> OfflineAmber
        SaleStatus.RECHAZADA -> DangerRed
    }
    val badgeBg = when (sale.status) {
        SaleStatus.SINCRONIZADA -> OnlineBg
        SaleStatus.PENDIENTE -> OfflineBg
        SaleStatus.RECHAZADA -> DangerBg
    }
    val label = when (sale.status) {
        SaleStatus.SINCRONIZADA -> "✅ Sincronizada"
        SaleStatus.PENDIENTE -> "⏳ Pendiente (Offline)"
        SaleStatus.RECHAZADA -> "Rechazada"
    }
    val labelColor = when (sale.status) {
        SaleStatus.SINCRONIZADA -> OnlineText
        SaleStatus.PENDIENTE -> OfflineText
        SaleStatus.RECHAZADA -> DangerRed
    }

    AppCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Insignia circular con el icono del estado
                Box(
                    modifier = Modifier.size(40.dp).background(badgeBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // %04d = numero con ceros a la izquierda hasta 4 digitos (0007)
                    Text(
                        text = "Venta #L-%04d".format(sale.localId),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        text = "$time · ${method.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCop(sale.total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    StatusPill(label, badgeBg, labelColor)
                }
            }
            // Motivo del rechazo (si lo hay)
            if (sale.status == SaleStatus.RECHAZADA && sale.message != null) {
                Text(
                    text = sale.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = DangerRed
                )
            }
        }
    }
}
