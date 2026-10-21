package co.sena.syncpos.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Retrofit convierte esta interfaz en llamadas HTTP reales. Cada funcion es un endpoint.
// "suspend" significa que se llama desde una corrutina: espera la respuesta sin
// congelar la pantalla. Si el servidor responde con error (401, 422...) Retrofit
// lanza HttpException; si no hay red lanza IOException.
interface SyncPosApi {

    // POST /api/login (la URL base ya termina en /api/)
    @POST("login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // Response<Unit>: no nos interesa el cuerpo, solo cerrar la sesion en el servidor.
    @POST("logout")
    suspend fun logout(): Response<Unit>

    // GET /api/productos?solo_activos=1&page=N
    @GET("productos")
    suspend fun products(
        @Query("solo_activos") onlyActive: Int = 1,
        @Query("page") page: Int
    ): ProductsResponse

    // Envia hasta 100 ventas por llamada.
    @POST("sync/ventas")
    suspend fun syncSales(@Body body: SyncRequest): SyncResponse

    // ---------- Endpoints de ADMINISTRADOR (solo en linea) ----------

    // Dashboard: ventas, ganancia, inventario, alertas, ultimas ventas y ultimos 7 dias.
    @GET("reportes/resumen-dia")
    suspend fun daySummary(): DaySummaryDto

    // Inventario paginado (20 por pagina). Los parametros null NO se envian.
    // lowStock = 1 filtra los productos con stock <= stock minimo.
    @GET("productos")
    suspend fun adminProducts(
        @Query("buscar") search: String?,
        @Query("categoria_id") categoryId: Long?,
        @Query("stock_bajo") lowStock: Int?,
        @Query("page") page: Int
    ): AdminProductsResponse

    // {id} se reemplaza por el valor del parametro marcado con @Path.
    @GET("productos/{id}")
    suspend fun adminProduct(@Path("id") id: Long): ProductEnvelope

    @GET("categorias")
    suspend fun categories(): CategoriesResponse

    @POST("productos")
    suspend fun createProduct(@Body body: ProductRequest): ProductEnvelope

    @PUT("productos/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductRequest): ProductEnvelope

    // Activa o desactiva el producto (el servidor invierte el valor actual).
    @PATCH("productos/{id}/estado")
    suspend fun toggleProduct(@Path("id") id: Long): ProductEnvelope

    // Rentabilidad entre dos fechas con formato "2026-10-20".
    @GET("reportes/rentabilidad")
    suspend fun profitability(
        @Query("desde") from: String,
        @Query("hasta") to: String
    ): ProfitabilityDto

    // Historial de ventas de TODOS los cajeros (15 por pagina).
    @GET("ventas")
    suspend fun sales(
        @Query("buscar") search: String?,
        @Query("metodo_pago") paymentMethod: String?,
        @Query("page") page: Int
    ): SalesResponse

    @GET("ventas/{id}")
    suspend fun sale(@Path("id") id: Long): SaleEnvelope
}
