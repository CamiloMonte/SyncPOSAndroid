package co.sena.syncpos.ui.admin.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.DaySummaryDto
import co.sena.syncpos.ui.admin.AdminTopBar
import co.sena.syncpos.ui.admin.ErrorState
import co.sena.syncpos.ui.admin.LoadingState
import co.sena.syncpos.ui.admin.SaleRow
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pestana "Inicio" del administrador (Dashboard).
// Igual que en las pantallas del cajero: esta funcion conoce el ViewModel y el estado,
// y las funciones de abajo solo dibujan.
@Composable
fun DashboardScreen(
    container: AppContainer,
    isOnline: Boolean,
    onLogout: () -> Unit,
    onOpenLowStock: () -> Unit,
    onOpenSales: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val today = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))
    )

    Column(modifier = Modifier.fillMaxSize()) {
        AdminTopBar(
            title = "Dashboard",
            subtitle = today,
            isOnline = isOnline,
            onLogout = onLogout,
            actions = {
                // Boton "Actualizar": muestra un circulo mientras se vuelven a pedir los datos.
                val refreshing = (state as? DashboardUiState.Content)?.isRefreshing == true
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
                        color = SenaGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = DarkSlate)
                    }
                }
            }
        )

        // "when" sobre una sealed interface: debe cubrir los tres estados.
        when (val current = state) {
            DashboardUiState.Loading -> LoadingState()
            is DashboardUiState.Error -> ErrorState(current.message, onRetry = viewModel::load)
            is DashboardUiState.Content -> DashboardContent(
                summary = current.summary,
                onOpenLowStock = onOpenLowStock,
                onOpenSales = onOpenSales
            )
        }
    }
}

@Composable
private fun DashboardContent(
    summary: DaySummaryDto,
    onOpenLowStock: () -> Unit,
    onOpenSales: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // la pantalla se desplaza si no cabe
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cuadricula 2x2 hecha con dos filas; cada tarjeta usa weight(1f) = mitad del ancho.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Ventas de Hoy",
                value = formatCop(summary.salesTotal),
                caption = if (summary.orders == 1) "1 pedido" else "${summary.orders} pedidos",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Ganancia Neta",
                value = formatCop(summary.netProfit),
                caption = "Hoy",
                valueColor = SenaGreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Inversión Inventario",
                value = formatCop(summary.inventoryInvestment),
                caption = "Costo del stock",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Alertas de Stock",
                value = summary.stockAlerts.toString(),
                caption = "productos críticos",
                valueColor = DangerRed,
                modifier = Modifier.weight(1f),
                // Tocar la tarjeta abre Inventario con el filtro "Stock bajo".
                onClick = onOpenLowStock
            )
        }

        SalesProfitChartCard(points = summary.last7Days)

        // Encabezado de la lista con el enlace "Ver todas".
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Últimas ventas", style = MaterialTheme.typography.titleMedium, color = DarkSlate)
            TextButton(onClick = onOpenSales) {
                Text("Ver todas", color = SenaGreen)
            }
        }

        if (summary.latestSales.isEmpty()) {
            Text("Aún no hay ventas.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            summary.latestSales.forEach { sale -> SaleRow(sale) }
        }
    }
}

// Tarjeta de una cifra: etiqueta pequena, valor grande y texto de apoyo.
@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DarkSlate,
    onClick: (() -> Unit)? = null
) {
    val tileModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    AppCard(modifier = tileModifier) {
        Column(
            modifier = Modifier.heightIn(min = 96.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(caption, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}
