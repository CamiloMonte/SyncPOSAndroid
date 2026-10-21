package co.sena.syncpos.data.remote

import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.session.SessionManager

// Esta carpeta (src/full) solo se compila en el flavor "full".
// Entrega la implementacion REAL de la API: el cliente Retrofit que llama al servidor
// Laravel. El flavor "demo" tiene un archivo con el mismo nombre y la misma firma en
// src/demo, pero devuelve una API falsa. AppContainer solo llama a esta funcion.
@Suppress("UNUSED_PARAMETER") // connectivity solo lo usa la version demo
fun provideSyncPosApi(session: SessionManager, connectivity: ConnectivityObserver): SyncPosApi =
    ApiClient.create(session)
