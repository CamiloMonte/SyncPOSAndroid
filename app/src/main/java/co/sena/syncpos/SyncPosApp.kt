package co.sena.syncpos

import android.app.Application

// La clase Application se crea UNA sola vez cuando arranca el proceso de la app,
// antes que cualquier pantalla. Esta registrada en el AndroidManifest (android:name).
// Es el lugar ideal para crear objetos compartidos (base de datos, red, sesion).
class SyncPosApp : Application()
