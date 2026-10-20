package co.sena.syncpos.data.remote

import co.sena.syncpos.BuildConfig
import co.sena.syncpos.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// Configuracion de Json compartida: ignora campos que la app no conoce
// (asi si el servidor agrega campos nuevos, la app no se rompe).
val appJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

// Construye el cliente HTTP y la implementacion de SyncPosApi.
object ApiClient {

    fun create(session: SessionManager): SyncPosApi {
        // Interceptor = codigo que se ejecuta en TODAS las peticiones antes de enviarlas.
        val headersInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json") // pedir siempre respuestas JSON
            // Si hay sesion, se agrega el token en cada peticion.
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC // solo linea de peticion y codigo
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(headersInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS) // si no hay servidor, fallar rapido
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(client)
            .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncPosApi::class.java)
    }
}

// Extrae el campo "message" del cuerpo de un error del servidor, por ejemplo
// {"message":"Credenciales incorrectas."}. Si no se puede leer, devuelve el texto por defecto.
fun HttpException.apiMessage(default: String): String {
    return try {
        val body = response()?.errorBody()?.string().orEmpty()
        appJson.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            ?: default
    } catch (e: Exception) {
        default
    }
}
