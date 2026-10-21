package co.sena.syncpos.data.remote

import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.session.SessionManager

// Esta carpeta (src/demo) solo se compila en el flavor "demo".
// Paso intermedio: por ahora sigue usando la API real; la API falsa se conecta despues.
@Suppress("UNUSED_PARAMETER")
fun provideSyncPosApi(session: SessionManager, connectivity: ConnectivityObserver): SyncPosApi =
    ApiClient.create(session)
