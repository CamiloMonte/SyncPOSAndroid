package co.sena.syncpos.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Avisa si el celular tiene conexion a internet.
// Un Flow es una "tuberia" de valores en el tiempo: cada vez que cambia la conexion
// se emite true (hay red) o false (no hay red).
//
// OJO: "hay red" no garantiza que el servidor responda. Por eso el repositorio
// tambien maneja los errores de red (IOException) al sincronizar.
class ConnectivityObserver(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // callbackFlow convierte un "callback" del sistema Android en un Flow.
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        // Valor inicial: como esta la red justo ahora.
        trySend(currentlyOnline())
        manager.registerDefaultNetworkCallback(callback)

        // awaitClose se ejecuta cuando nadie escucha mas el Flow: dejamos de observar.
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged() // no repetir el mismo valor dos veces seguidas

    private fun currentlyOnline(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
