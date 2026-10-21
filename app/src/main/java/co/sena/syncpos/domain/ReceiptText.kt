package co.sena.syncpos.domain

import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.util.formatCop
import java.time.ZoneId

// Arma el texto plano de una factura para compartirlo (WhatsApp, correo...).
// Funcion pura: recibe la venta y devuelve un String. Mas adelante se podria generar un
// PDF de la factura reutilizando la misma idea que el reporte de rentabilidad.
fun buildReceiptText(sale: SaleDto, zone: ZoneId = ZoneId.systemDefault()): String {
    val lines = mutableListOf<String>()
    lines += "SyncPOS - Factura ${sale.number}"
    lines += "Cajero: ${sale.cashier?.name ?: "Sin cajero"}"
    lines += "Fecha: ${formatSaleDateTime(sale.soldAt, zone)}"
    lines += "Método de pago: ${PaymentMethod.fromApiValue(sale.paymentMethod).label}"
    lines += "----------------------------"
    sale.items.forEach { item ->
        lines += item.name ?: "Producto"
        lines += "  ${item.quantity} x ${formatCop(item.unitPrice)} = ${formatCop(item.subtotal)}"
    }
    lines += "----------------------------"
    lines += "TOTAL: ${formatCop(sale.total)}"
    return lines.joinToString("\n")
}
