package co.sena.syncpos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografia: se usa la fuente por defecto del sistema (Roboto en Android).
// Mapeamos los estilos del diseno a los "slots" de Material 3.
val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),   // H1
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),    // H2
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),     // cuerpo
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),     // caption
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),       // total
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)        // boton
)
