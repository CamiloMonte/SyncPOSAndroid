package co.sena.syncpos.data.remote

import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.session.SessionManager

// Esta carpeta (src/demo) solo se compila en el flavor "demo".
// Entrega la API FALSA con datos de ejemplo en memoria (ver FakeSyncPosApi). Tiene la
// misma firma que la funcion de src/full, por eso AppContainer no necesita cambios.
@Suppress("UNUSED_PARAMETER") // session no se usa: la API falsa no necesita token
fun provideSyncPosApi(session: SessionManager, connectivity: ConnectivityObserver): SyncPosApi =
    FakeSyncPosApi(isOnline = { true })
