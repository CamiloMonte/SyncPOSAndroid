package co.sena.syncpos.data.session

import android.content.Context
import androidx.core.content.edit

// Guarda la sesion del cajero en SharedPreferences: un archivo pequeno de pares
// clave/valor que sobrevive aunque se cierre la app. Sirve para no pedir login otra vez.
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("syncpos_session", Context.MODE_PRIVATE)

    // Token de la API (se envia en la cabecera Authorization: Bearer <token>).
    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    val userName: String get() = prefs.getString(KEY_NAME, "") ?: ""
    val role: String get() = prefs.getString(KEY_ROLE, "") ?: ""

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    // Momento (milisegundos) de la ultima sincronizacion exitosa; 0 = nunca.
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNC, value) }

    fun save(token: String, userName: String, role: String) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_NAME, userName)
            putString(KEY_ROLE, role)
        }
    }

    // Cierra la sesion. NO borra las ventas pendientes (estan en Room): no se pierden.
    fun clear() {
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_NAME)
            remove(KEY_ROLE)
        }
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_NAME = "user_name"
        const val KEY_ROLE = "role"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
