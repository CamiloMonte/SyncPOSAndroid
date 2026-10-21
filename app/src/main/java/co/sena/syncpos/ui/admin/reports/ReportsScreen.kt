package co.sena.syncpos.ui.admin.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.ProfitProductDto
import co.sena.syncpos.data.remote.ProfitabilityDto
import co.sena.syncpos.domain.RangeOption
import co.sena.syncpos.ui.admin.AdminTopBar
import co.sena.syncpos.ui.admin.ErrorState
import co.sena.syncpos.ui.admin.LoadingState
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop
import co.sena.syncpos.util.formatPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Pestana "Reportes": rentabilidad del periodo elegido, exportacion y ranking de productos.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(container: AppContainer, isOnline: Boolean, onLogout: () -> Unit) {
    val viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Genera el archivo en un hilo de segundo plano (Dispatchers.IO = hilo para disco/red)
    // y luego, de vuelta en el hilo principal, abre la hoja para compartirlo.
    fun export(kind: String) {
        val report = state.report ?: return
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    if (kind == "pdf") ReportFiles.writePdf(context, report, state.range)
                    else ReportFiles.writeCsv(context, report, state.range)
                }
                ReportFiles.share(context, file, if (kind == "pdf") ReportFiles.MIME_PDF else ReportFiles.MIME_CSV)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("No se pudo generar el archivo.")
            }
        }
    }

    // Dialogo con el calendario de rango (Material 3).
    if (state.showRangePicker) {
        val pickerState = rememberDateRangePickerState()
        val start = pickerState.selectedStartDateMillis
        val end = pickerState.selectedEndDateMillis
        DatePickerDialog(
            onDismissRequest = viewModel::dismissRangePicker,
            confirmButton = {
                // Solo se puede aceptar cuando ya hay fecha de inicio Y de fin.
                TextButton(
                    enabled = start != null && end != null,
                    onClick = { if (start != null && end != null) viewModel.applyCustomRange(start, end) }
                ) { Text("Aceptar", color = SenaGreen) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRangePicker) { Text("Cancelar", color = TextSecondary) }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(500.dp))
        }
    }

    Scaffold(
        containerColor = BackgroundGray,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdminTopBar(
                title = "Reportes",
                subtitle = "${state.range.from.format(dateFormat)} - ${state.range.to.format(dateFormat)}",
                isOnline = isOnline,
                onLogout = onLogout
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            RangeSelector(
                selected = state.option,
                onSelect = viewModel::selectOption,
                modifier = Modifier.padding(16.dp)
            )

            val report = state.report
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                report != null -> ReportContent(
                    report = report,
                    onExportPdf = { export("pdf") },
                    onExportCsv = { export("csv") }
                )
            }
        }
    }
}

// Botones segmentados: solo uno puede estar activo a la vez (seleccion unica).
@Composable
private fun RangeSelector(selected: RangeOption, onSelect: (RangeOption) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        RangeOption.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, RangeOption.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = SenaGreen,
                    activeContentColor = Color.White
                )
            ) {
                Text(option.label)
            }
        }
    }
}

@Composable
private fun ReportContent(report: ProfitabilityDto, onExportPdf: () -> Unit, onExportCsv: () -> Unit) {
    val totals = report.totals
    // Que porcentaje de los ingresos se fue en costo (si no hubo ingresos, 0 para no dividir entre cero).
    val costPercent = if (totals.revenue > 0) totals.cost / totals.revenue * 100 else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Ingresos por Ventas",
                value = formatCop(totals.revenue),
                caption = null,
                captionColor = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Costo Mercancía",
                value = formatCop(totals.cost),
                caption = "${formatPercent(costPercent)} de ingresos",
                captionColor = OfflineText,
                modifier = Modifier.weight(1f)
            )
        }
        MetricTile(
            label = "Ganancia Neta",
            value = formatCop(totals.profit),
            caption = "Margen ${formatPercent(totals.margin)}",
            captionColor = SenaGreen,
            valueColor = SenaGreen,
            modifier = Modifier.fillMaxWidth()
        )

        // Botones de exportacion (secundarios: borde, sin relleno).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExportButton("Exportar PDF", Icons.Filled.PictureAsPdf, onExportPdf, Modifier.weight(1f))
            ExportButton("Excel (CSV)", Icons.Filled.TableChart, onExportCsv, Modifier.weight(1f))
        }

        Text(
            "Rentabilidad por producto",
            style = MaterialTheme.typography.titleMedium,
            color = DarkSlate,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (report.products.isEmpty()) {
            Text(
                "No hay ventas en este periodo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            report.products.forEach { ProductProfitCard(it) }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    caption: String?,
    captionColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = DarkSlate
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = captionColor)
            }
        }
    }
}

@Composable
private fun ExportButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Icon(icon, contentDescription = null, tint = DarkSlate, modifier = Modifier.size(20.dp))
        Text("  $text", color = DarkSlate)
    }
}

// Tarjeta del ranking: nombre + ganancia, barra de progreso y detalle.
@Composable
private fun ProductProfitCard(product: ProfitProductDto) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatCop(product.profit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SenaGreen
                )
            }
            // La barra se llena segun el porcentaje de la ganancia total que aporta el producto
            // (contribucion 0..100 -> progreso 0.0..1.0).
            LinearProgressIndicator(
                progress = { (product.contribution / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = SenaGreen,
                trackColor = BorderGray
            )
            Text(
                text = "${product.units} und. · Margen ${formatPercent(product.margin)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

