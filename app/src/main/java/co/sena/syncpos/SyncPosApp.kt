package co.sena.syncpos

import android.app.Application

// La clase Application se crea UNA sola vez cuando arranca el proceso de la app,
// antes que cualquier pantalla. Esta registrada en el AndroidManifest (android:name).
// Es el lugar ideal para crear objetos compartidos (base de datos, red, sesion).
class SyncPosApp : Application() {

    // lateinit: se asigna en onCreate (no en el constructor) porque necesita el contexto.
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Escucha la conexion y sincroniza solo al volver el internet.
        container.startAutoSync()
    }
}
