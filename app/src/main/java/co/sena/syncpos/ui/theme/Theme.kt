package co.sena.syncpos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Esquema de colores de Material 3 (solo tema claro).
private val LightColors = lightColorScheme(
    primary = SenaGreen,
    onPrimary = SurfaceWhite,
    background = BackgroundGray,
    onBackground = DarkSlate,
    surface = SurfaceWhite,
    onSurface = DarkSlate,
    outline = BorderGray,
    error = DangerRed,
    errorContainer = DangerBg
)

// @Composable marca una funcion que "dibuja" UI. Compose la vuelve a ejecutar
// automaticamente cuando cambia el estado que lee.
// SyncPosTheme envuelve toda la app: todo lo que este en "content" hereda colores y letras.
@Composable
fun SyncPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
