package co.sena.syncpos.data.remote

import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.session.SessionManager

// Esta carpeta (src/demo) solo se compila en el flavor "demo".
// Entrega la API FALSA con datos de ejemplo en memoria (ver FakeSyncPosApi). Tiene la
// misma firma que la funcion de src/full, por eso AppContainer no necesita cambios.
//
// Simulacion sin conexion: se le pasa a la API falsa una funcion que consulta la red REAL
// del celular. Si activas el modo avion, la API falsa lanza IOException como lo haria una
// llamada de verdad, y se ve el flujo real: venta pendiente -> al volver la red se
// sincroniza sola (ver AppContainer.startAutoSync).
@Suppress("UNUSED_PARAMETER") // session no se usa: la API falsa no necesita token
fun provideSyncPosApi(session: SessionManager, connectivity: ConnectivityObserver): SyncPosApi =
    FakeSyncPosApi(isOnline = { connectivity.currentlyOnline() })
