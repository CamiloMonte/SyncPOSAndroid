package co.sena.syncpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.sena.syncpos.ui.navigation.AppNavigation
import co.sena.syncpos.ui.theme.SyncPosTheme

// Una Activity es una "ventana" de Android. Esta app usa una sola Activity y
// cambia de pantalla con Navigation Compose.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // setContent: aqui empieza la UI hecha con Compose (en vez de archivos XML).
        setContent {
            SyncPosTheme {
                AppNavigation()
            }
        }
    }
}
