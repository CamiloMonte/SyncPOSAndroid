package co.sena.syncpos

import android.content.Context
import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.local.AppDatabase
import co.sena.syncpos.data.remote.provideSyncPosApi
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.AuthRepository
import co.sena.syncpos.data.repository.ProductRepository
import co.sena.syncpos.data.repository.SalesRepository
import co.sena.syncpos.data.session.SessionEvents
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

    // Avisos de sesion (por ejemplo "el token vencio") que la navegacion escucha.
    val sessionEvents = SessionEvents()

    private val database: AppDatabase by lazy { AppDatabase.create(context) }
    // Inversion de dependencias, explicado simple: el resto de la app solo conoce la
    // INTERFAZ SyncPosApi ("que se puede pedir"), no QUIEN la implementa. Quien la
    // implementa se decide aqui, en un solo lugar:
    // provideSyncPosApi() existe dos veces, una en la carpeta src/full (devuelve el
    // cliente Retrofit real) y otra en src/demo (devuelve una API falsa en memoria).
    // Gradle compila solo la carpeta del flavor elegido, asi el flavor full queda igual.
    private val api by lazy { provideSyncPosApi(session, connectivity) }

    val authRepository by lazy { AuthRepository(api, session) }
    val productRepository by lazy { ProductRepository(api, database.productDao()) }
    val salesRepository by lazy { SalesRepository(database, api, session) }

    // Repositorio del administrador: solo habla con la API (no usa Room, es en linea).
    val adminRepository by lazy { AdminRepository(api, session, sessionEvents) }

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
                    // Solo el cajero tiene ventas offline; si hubiera pendientes de un cajero y
                    // ahora entra un admin, NO se deben enviar con el token del admin.
                    if (session.isLoggedIn && session.role != "admin") salesRepository.syncPending()
                }
        }
    }
}
