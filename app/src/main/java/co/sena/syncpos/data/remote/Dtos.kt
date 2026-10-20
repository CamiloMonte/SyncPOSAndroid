package co.sena.syncpos.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO = Data Transfer Object: clases que representan el JSON que viaja por la red.
// @Serializable le dice a kotlinx.serialization que genere el codigo para convertir
// entre JSON y estas clases. @SerialName indica el nombre exacto del campo en el JSON.

// ---------- Login ----------
@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val rol: String // "admin" o "cajero"
)

@Serializable
data class LoginResponse(val token: String, val user: UserDto)

// ---------- Productos ----------
@Serializable
data class CategoryDto(val id: Long, val nombre: String)

@Serializable
data class ProductDto(
    val id: Long,
    @SerialName("codigo_barras") val barcode: String? = null,
    @SerialName("nombre") val name: String,
    @SerialName("categoria") val category: CategoryDto? = null,
    // La API manda los precios como TEXTO ("13500.00"); luego se convierten a Double.
    @SerialName("precio_venta") val salePrice: String,
    val stock: Int = 0,
    @SerialName("stock_minimo") val minStock: Int = 0
)

@Serializable
data class PageMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    val total: Int = 0
)

@Serializable
data class ProductsResponse(val data: List<ProductDto>, val meta: PageMetaDto)

// ---------- Sincronizacion de ventas ----------
// Solo se envia producto_id y cantidad: el servidor calcula los precios (nunca confia
// en los precios que manda el celular).
@Serializable
data class SyncItemDto(
    @SerialName("producto_id") val productId: Long,
    @SerialName("cantidad") val quantity: Int
)

@Serializable
data class SyncSaleDto(
    @SerialName("offline_referencia") val offlineReference: String,
    @SerialName("metodo_pago") val paymentMethod: String,
    @SerialName("vendida_en") val soldAt: String,
    // "online": la venta se envio apenas se registro (habia internet).
    // "offline": la venta espero en el celular y se envio despues, al volver la conexion.
    @SerialName("origen") val origin: String,
    val items: List<SyncItemDto>
)

@Serializable
data class SyncRequest(val ventas: List<SyncSaleDto>)

@Serializable
data class SyncResultDto(
    @SerialName("offline_referencia") val offlineReference: String,
    val estado: String, // "sincronizada" | "duplicada" | "rechazada"
    @SerialName("venta_id") val saleId: Long? = null,
    @SerialName("stock_negativo") val negativeStock: Boolean? = null,
    @SerialName("mensaje") val message: String? = null
)

@Serializable
data class SyncResponse(val resultados: List<SyncResultDto>)
