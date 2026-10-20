package co.sena.syncpos

import android.content.Context
import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.local.AppDatabase
import co.sena.syncpos.data.remote.ApiClient
import co.sena.syncpos.data.repository.AuthRepository
import co.sena.syncpos.data.repository.ProductRepository
import co.sena.syncpos.data.repository.SalesRepository
import co.sena.syncpos.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Contenedor de dependencias MANUAL. En vez de usar una libreria de inyeccion (Hilt),
// aqui se crean una sola vez los objetos compartidos y las pantallas los piden de aqui.
// "by lazy" = se crea la primera vez que se usa, no antes.
class AppContainer(context: Context) {

    val session = SessionManager(context)
    val connectivity = ConnectivityObserver(context)

    private val database: AppDatabase by lazy { AppDatabase.create(context) }
    private val api by lazy { ApiClient.create(session) }

    val authRepository by lazy { AuthRepository(api, session) }
    val productRepository by lazy { ProductRepository(api, database.productDao()) }
    val salesRepository by lazy { SalesRepository(database, api, session) }

    // Alcance de corrutinas ligado a la vida de la app (no a una pantalla).
    // SupervisorJob: si una tarea falla, no cancela a las demas.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Sincronizacion automatica: cada vez que el celular pasa a estar ONLINE, se intenta
    // enviar las ventas pendientes. Si no hay pendientes, syncPending() no hace nada.
    fun startAutoSync() {
        appScope.launch {
            connectivity.isOnline
                .filter { online -> online }
                .collect {
                    if (session.isLoggedIn) salesRepository.syncPending()
                }
        }
    }
}
