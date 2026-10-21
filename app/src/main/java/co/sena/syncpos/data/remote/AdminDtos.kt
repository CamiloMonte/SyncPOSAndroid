package co.sena.syncpos.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs de las pantallas de ADMINISTRADOR. Igual que en Dtos.kt, cada clase representa
// un trozo de JSON. Los nombres de campo de la API estan en espanol (@SerialName) y los
// nombres en Kotlin en ingles. Todo el dinero usa MoneySerializer porque a veces llega
// como numero y a veces como texto.

// ---------- Dashboard: GET reportes/resumen-dia ----------
@Serializable
data class DaySummaryDto(
    @SerialName("ventas_total") @Serializable(with = MoneySerializer::class) val salesTotal: Double = 0.0,
    @SerialName("pedidos") val orders: Int = 0,
    @SerialName("ganancia_neta") @Serializable(with = MoneySerializer::class) val netProfit: Double = 0.0,
    @SerialName("inversion_inventario") @Serializable(with = MoneySerializer::class) val inventoryInvestment: Double = 0.0,
    @SerialName("alertas_stock") val stockAlerts: Int = 0,
    @SerialName("ultimas_ventas") val latestSales: List<SaleDto> = emptyList(),
    @SerialName("ultimos_7_dias") val last7Days: List<DayPointDto> = emptyList()
)

// Un punto del grafico: ventas y ganancia de un dia ("fecha" = "2026-10-20").
@Serializable
data class DayPointDto(
    @SerialName("fecha") val date: String,
    @SerialName("ventas") @Serializable(with = MoneySerializer::class) val sales: Double = 0.0,
    @SerialName("ganancia") @Serializable(with = MoneySerializer::class) val profit: Double = 0.0
)

// ---------- Ventas: GET ventas y GET ventas/{id} ----------
@Serializable
data class SaleDto(
    val id: Long,
    @SerialName("numero") val number: String,
    @SerialName("cajero") val cashier: CashierDto? = null,
    @SerialName("metodo_pago") val paymentMethod: String = "",
    @SerialName("origen") val origin: String = "online",
    @SerialName("total") @Serializable(with = MoneySerializer::class) val total: Double = 0.0,
    // Fecha ISO 8601 con zona horaria, por ejemplo "2026-10-20T14:30:00-05:00".
    @SerialName("vendida_en") val soldAt: String? = null,
    // Solo viene en el detalle (GET ventas/{id}); en la lista queda vacio.
    val items: List<SaleItemDto> = emptyList()
)

@Serializable
data class CashierDto(val id: Long, val name: String)

@Serializable
data class SaleItemDto(
    @SerialName("nombre") val name: String? = null,
    @SerialName("cantidad") val quantity: Int = 0,
    @SerialName("precio_unitario") @Serializable(with = MoneySerializer::class) val unitPrice: Double = 0.0,
    @SerialName("subtotal") @Serializable(with = MoneySerializer::class) val subtotal: Double = 0.0
)

@Serializable
data class SalesResponse(val data: List<SaleDto>, val meta: PageMetaDto)

// Un recurso suelto (GET ventas/{id}) llega envuelto en {"data": {...}}.
@Serializable
data class SaleEnvelope(val data: SaleDto)

// ---------- Inventario ----------
@Serializable
data class AdminProductDto(
    val id: Long,
    @SerialName("codigo_barras") val barcode: String? = null,
    @SerialName("nombre") val name: String,
    @SerialName("categoria") val category: CategoryDto? = null,
    @SerialName("precio_compra") @Serializable(with = MoneySerializer::class) val costPrice: Double = 0.0,
    @SerialName("precio_venta") @Serializable(with = MoneySerializer::class) val salePrice: Double = 0.0,
    @SerialName("ganancia_unitaria") @Serializable(with = MoneySerializer::class) val unitProfit: Double = 0.0,
    val stock: Int = 0,
    @SerialName("stock_minimo") val minStock: Int = 0,
    @SerialName("stock_bajo") val lowStock: Boolean = false,
    @SerialName("activo") val active: Boolean = true
)

@Serializable
data class AdminProductsResponse(val data: List<AdminProductDto>, val meta: PageMetaDto)

@Serializable
data class ProductEnvelope(val data: AdminProductDto)

@Serializable
data class CategoriesResponse(val data: List<CategoryDto>)

// Cuerpo de POST productos y PUT productos/{id}.
@Serializable
data class ProductRequest(
    @SerialName("codigo_barras") val barcode: String,
    @SerialName("nombre") val name: String,
    @SerialName("categoria_id") val categoryId: Long,
    @SerialName("precio_compra") val costPrice: Double,
    @SerialName("precio_venta") val salePrice: Double,
    val stock: Int,
    @SerialName("stock_minimo") val minStock: Int
)

// ---------- Reportes: GET reportes/rentabilidad ----------
@Serializable
data class ProfitabilityDto(
    @SerialName("periodo") val period: PeriodDto? = null,
    @SerialName("totales") val totals: ProfitTotalsDto = ProfitTotalsDto(),
    @SerialName("productos") val products: List<ProfitProductDto> = emptyList()
)

@Serializable
data class PeriodDto(@SerialName("desde") val from: String, @SerialName("hasta") val to: String)

@Serializable
data class ProfitTotalsDto(
    @SerialName("ingresos") @Serializable(with = MoneySerializer::class) val revenue: Double = 0.0,
    @SerialName("costo") @Serializable(with = MoneySerializer::class) val cost: Double = 0.0,
    @SerialName("ganancia") @Serializable(with = MoneySerializer::class) val profit: Double = 0.0,
    @SerialName("margen") val margin: Double = 0.0
)

@Serializable
data class ProfitProductDto(
    @SerialName("producto_id") val productId: Long,
    @SerialName("nombre") val name: String,
    @SerialName("unidades") val units: Int = 0,
    @SerialName("ingresos") @Serializable(with = MoneySerializer::class) val revenue: Double = 0.0,
    @SerialName("costo") @Serializable(with = MoneySerializer::class) val cost: Double = 0.0,
    @SerialName("ganancia") @Serializable(with = MoneySerializer::class) val profit: Double = 0.0,
    @SerialName("margen") val margin: Double = 0.0,
    // Porcentaje de la ganancia total que aporta este producto.
    @SerialName("contribucion") val contribution: Double = 0.0
)
