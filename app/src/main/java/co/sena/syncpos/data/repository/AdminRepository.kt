package co.sena.syncpos.data.repository

import co.sena.syncpos.data.remote.AdminProductDto
import co.sena.syncpos.data.remote.AdminProductsResponse
import co.sena.syncpos.data.remote.CategoryDto
import co.sena.syncpos.data.remote.DaySummaryDto
import co.sena.syncpos.data.remote.ProductRequest
import co.sena.syncpos.data.remote.ProfitabilityDto
import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.data.remote.SalesResponse
import co.sena.syncpos.data.remote.SyncPosApi
import co.sena.syncpos.data.remote.appJson
import co.sena.syncpos.data.session.SessionEvents
import co.sena.syncpos.data.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

// Tipos de error que las pantallas de admin saben distinguir.
enum class ApiErrorKind { NETWORK, UNAUTHORIZED, FORBIDDEN, VALIDATION, OTHER }

// Un error ya "traducido" a algo que se puede mostrar al usuario.
// fieldErrors solo se llena con el codigo 422: campo -> primer mensaje de ese campo.
data class ApiError(
    val kind: ApiErrorKind,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap()
)

// Resultado de una llamada: o salio bien (Success con los datos) o fallo (Failure con el error).
// "sealed interface" obliga al ViewModel a manejar los dos casos con un "when".
// "out T" permite que ApiResult<Nothing> (los errores) sirva para cualquier ApiResult<T>.
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

// Repositorio de las pantallas de administrador. Todas las funciones devuelven ApiResult
// y NUNCA lanzan excepciones de red: los errores ya vienen convertidos en mensajes.
class AdminRepository(
    private val api: SyncPosApi,
    private val session: SessionManager,
    private val sessionEvents: SessionEvents
) {

    // Aviso "la lista de productos cambio" (se creo/edito/activo/desactivo uno).
    // El inventario y el dashboard lo escuchan para recargarse solos.
    private val _productsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val productsChanged: SharedFlow<Unit> = _productsChanged.asSharedFlow()

    // ---------- Dashboard ----------
    // LocalDate.now() usa la zona horaria del celular: es el "hoy" que ve el administrador
    suspend fun daySummary(): ApiResult<DaySummaryDto> = call { api.daySummary(LocalDate.now().toString()) }

    // ---------- Inventario ----------
    suspend fun products(
        search: String,
        categoryId: Long?,
        lowStock: Boolean,
        page: Int
    ): ApiResult<AdminProductsResponse> = call {
        api.adminProducts(
            search = search.trim().ifBlank { null }, // texto vacio = no filtrar
            categoryId = categoryId,
            lowStock = if (lowStock) 1 else null,
            page = page
        )
    }

    suspend fun product(id: Long): ApiResult<AdminProductDto> = call { api.adminProduct(id).data }

    suspend fun categories(): ApiResult<List<CategoryDto>> = call { api.categories().data }

    // id == null -> crear (POST); id != null -> editar (PUT).
    suspend fun saveProduct(id: Long?, request: ProductRequest): ApiResult<AdminProductDto> {
        val result = call {
            if (id == null) api.createProduct(request).data else api.updateProduct(id, request).data
        }
        if (result is ApiResult.Success) _productsChanged.tryEmit(Unit)
        return result
    }

    suspend fun toggleProduct(id: Long): ApiResult<AdminProductDto> =
        call { api.toggleProduct(id).data }

    // ---------- Reportes ----------
    suspend fun profitability(from: LocalDate, to: LocalDate): ApiResult<ProfitabilityDto> =
        call { api.profitability(from.toString(), to.toString()) } // LocalDate.toString() = "2026-10-20"

    // ---------- Ventas ----------
    suspend fun sales(search: String, paymentMethod: String?, page: Int): ApiResult<SalesResponse> =
        call { api.sales(search.trim().ifBlank { null }, paymentMethod, page) }

    suspend fun sale(id: Long): ApiResult<SaleDto> = call { api.sale(id).data }

    // ---------- Manejo de errores comun ----------
    // Ejecuta una llamada de red y convierte cualquier fallo en un ApiResult.Failure.
    // El parametro "block" es una funcion suspend: puede llamar a las funciones de Retrofit.
    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: HttpException) {
            ApiResult.Failure(mapHttpError(e))
        } catch (e: IOException) {
            // Sin internet, servidor apagado, tiempo agotado...
            ApiResult.Failure(ApiError(ApiErrorKind.NETWORK, "Sin conexión con el servidor."))
        } catch (e: SerializationException) {
            // El JSON no tiene la forma que espera la app.
            ApiResult.Failure(ApiError(ApiErrorKind.OTHER, "Respuesta inesperada del servidor."))
        }
    }

    private fun mapHttpError(e: HttpException): ApiError {
        // OJO: el cuerpo de un error solo se puede leer UNA vez (es un stream). Por eso se
        // lee aqui y se reutiliza el texto, en vez de llamar a e.apiMessage() y a otra
        // funcion que lean el cuerpo por separado.
        val body = try {
            e.response()?.errorBody()?.string().orEmpty()
        } catch (ex: Exception) {
            ""
        }
        val json = try {
            appJson.parseToJsonElement(body).jsonObject
        } catch (ex: Exception) {
            null
        }
        val serverMessage = json?.get("message")?.jsonPrimitive?.contentOrNull

        return when (e.code()) {
            401 -> {
                // Token vencido o revocado: se borra la sesion y se avisa a la navegacion.
                session.clear()
                sessionEvents.notifyExpired()
                ApiError(ApiErrorKind.UNAUTHORIZED, "Tu sesión expiró. Inicia sesión de nuevo.")
            }
            403 -> ApiError(ApiErrorKind.FORBIDDEN, "No tienes permiso para esta acción.")
            422 -> ApiError(
                kind = ApiErrorKind.VALIDATION,
                message = serverMessage ?: "Revisa los campos marcados en rojo.",
                fieldErrors = parseFieldErrors(json)
            )
            else -> ApiError(
                ApiErrorKind.OTHER,
                serverMessage ?: "Error del servidor (código ${e.code()})."
            )
        }
    }

    // El 422 de Laravel viene asi: {"message":"...","errors":{"campo":["error 1","error 2"]}}
    // Se queda con el PRIMER error de cada campo.
    private fun parseFieldErrors(json: JsonObject?): Map<String, String> {
        val errors = json?.get("errors") as? JsonObject ?: return emptyMap()
        return errors.mapNotNull { (field, value) ->
            val first = (value as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
            if (first == null) null else field to first
        }.toMap()
    }
}
