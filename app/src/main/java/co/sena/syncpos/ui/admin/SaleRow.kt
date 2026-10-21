package co.sena.syncpos.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.domain.PaymentMethod
import co.sena.syncpos.domain.formatSaleTime
import co.sena.syncpos.domain.isOfflineOrigin
import co.sena.syncpos.domain.originLabel
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OfflineBg
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop

// Tarjeta de una venta. La usan el Dashboard ("Ultimas ventas") y la pestana Ventas.
// onClick es opcional: en el Dashboard no hace nada, en Ventas abre el detalle.
@Composable
fun SaleRow(sale: SaleDto, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val method = PaymentMethod.fromApiValue(sale.paymentMethod).label
    val cashier = sale.cashier?.name ?: "Sin cajero"
    val offline = isOfflineOrigin(sale.origin)

    // Si hay onClick la tarjeta es tocable; si no, se deja tal cual.
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    AppCard(modifier = rowModifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sale.number,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Text(
                    text = "$cashier · ${formatSaleTime(sale.soldAt)} · $method",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatCop(sale.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                // Verde si se vendio con internet, ambar si espero y se sincronizo despues.
                TextPill(
                    text = originLabel(sale.origin),
                    background = if (offline) OfflineBg else OnlineBg,
                    textColor = if (offline) OfflineText else OnlineText
                )
            }
        }
    }
}
