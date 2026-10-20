package co.sena.syncpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.sena.syncpos.data.local.ProductEntity
import co.sena.syncpos.domain.Cart
import co.sena.syncpos.domain.CartItem
import co.sena.syncpos.domain.PaymentMethod
import co.sena.syncpos.ui.components.PrimaryButton
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerBg
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary
import co.sena.syncpos.util.formatCop

// Tarjeta con borde gris y esquinas de 12dp (estilo comun de las "cards" del diseno).
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        content()
    }
}

// Fila de un producto en los resultados de busqueda. Toda la fila es tocable.
@Composable
fun ProductResultRow(product: ProductEntity, onClick: () -> Unit) {
    // Stock bajo (o agotado): se pinta en rojo.
    val lowStock = product.stock <= product.minStock

    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = product.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCop(product.salePrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Text(
                    text = "Stock: ${product.stock}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lowStock) DangerRed else TextSecondary
                )
            }
        }
    }
}

// Tarjeta de un producto dentro del carrito.
@Composable
fun CartItemCard(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    AppCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Fila 1: nombre, precio unitario y boton de eliminar
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatCop(item.unitPrice)} c/u",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                SquareButton(
                    onClick = onRemove,
                    background = DangerBg,
                    border = null
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Quitar del carrito", tint = DangerRed)
                }
            }

            // Fila 2: selector de cantidad y subtotal
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareButton(onClick = onDecrement, background = SurfaceWhite, border = BorderGray) {
                    Icon(Icons.Filled.Remove, contentDescription = "Restar uno", tint = DarkSlate)
                }
                Text(
                    text = item.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkSlate,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                SquareButton(onClick = onIncrement, background = SenaGreen, border = null) {
                    Icon(Icons.Filled.Add, contentDescription = "Sumar uno", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatCop(item.subtotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            }
        }
    }
}

// Boton cuadrado de 40dp (usado en el selector de cantidad y en "eliminar").
@Composable
private fun SquareButton(
    onClick: () -> Unit,
    background: Color,
    border: Color?,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(background)
            .let { if (border != null) it.border(1.dp, border, shape) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// Barra inferior fija: total, metodo de pago y boton de registrar.
@Composable
fun CheckoutBar(
    cart: Cart,
    paymentMethod: PaymentMethod,
    isSaving: Boolean,
    onSelectPayment: (PaymentMethod) -> Unit,
    onRegister: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .border(1.dp, BorderGray)
            .navigationBarsPadding() // deja espacio para la barra de navegacion del sistema
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Total a Cobrar",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Text(
                    "${plural(cart.productCount, "producto", "productos")} · " +
                        plural(cart.unitCount, "unidad", "unidades"),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = formatCop(cart.total),
                style = MaterialTheme.typography.titleLarge,
                color = DarkSlate
            )
        }

        // Selector de metodo de pago: dos botones, el elegido se pinta de verde.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentMethod.entries.forEach { method ->
                val selected = method == paymentMethod
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) SenaGreen else SurfaceWhite)
                        .border(1.dp, if (selected) SenaGreen else BorderGray, RoundedCornerShape(12.dp))
                        .clickable { onSelectPayment(method) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else DarkSlate
                    )
                }
            }
        }

        PrimaryButton(
            text = "REGISTRAR VENTA",
            onClick = onRegister,
            enabled = !cart.isEmpty,
            loading = isSaving
        )
    }
}

// "1 producto" / "3 productos"
fun plural(count: Int, one: String, many: String): String =
    if (count == 1) "1 $one" else "$count $many"
