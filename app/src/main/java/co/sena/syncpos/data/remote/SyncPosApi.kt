package co.sena.syncpos.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
