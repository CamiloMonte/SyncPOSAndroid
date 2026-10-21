package co.sena.syncpos.data.repository

import co.sena.syncpos.data.remote.LoginRequest
import co.sena.syncpos.data.remote.SyncPosApi
import co.sena.syncpos.data.remote.apiMessage
import co.sena.syncpos.data.session.SessionManager
import retrofit2.HttpException
import java.io.IOException

// Resultado del login. "sealed interface" = lista cerrada de posibles resultados,
// asi el ViewModel esta obligado a manejar cada caso.
sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}

// Repositorio = unico lugar que sabe de donde vienen los datos (red, base de datos,
// preferencias). Las pantallas y ViewModels no hablan directo con Retrofit ni Room.
class AuthRepository(
    private val api: SyncPosApi,
    private val session: SessionManager
) {

    // "suspend": se llama desde una corrutina porque hace una peticion de red.
    suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = api.login(LoginRequest(email.trim(), password))
            session.save(response.token, response.user.name, response.user.rol)
            LoginResult.Success
        } catch (e: HttpException) {
            // 422 credenciales incorrectas, 403 usuario inactivo...
            LoginResult.Failure(e.apiMessage("No se pudo iniciar sesión (código ${e.code()})."))
        } catch (e: IOException) {
            LoginResult.Failure("Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo.")
        }
    }

    // Cierra sesion: avisa al servidor "a mejor esfuerzo" (si falla no importa) y
    // siempre borra la sesion local.
    suspend fun logout() {
        try {
            api.logout()
        } catch (e: Exception) {
            // Sin conexion o token vencido: igual cerramos la sesion en el celular.
        }
        session.clear()
    }

    // true si el usuario que inicio sesion es administrador (rol guardado en la sesion).
    val isAdmin: Boolean get() = session.role == "admin"

    // Cierra solo la sesion local (por ejemplo si fallo la descarga del catalogo).
    fun clearLocalSession() = session.clear()
}
