package co.sena.syncpos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.TextSecondary

// Boton principal reutilizable (verde, 56dp de alto, esquinas de 12dp).
// Un @Composable puede recibir parametros, incluso otras funciones (onClick).
// Asi la misma pieza de UI se usa en varias pantallas sin repetir codigo.
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 56.dp
) {
    Button(
        onClick = onClick,
        // Mientras carga se bloquea el boton para evitar toques dobles.
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SenaGreen,
            contentColor = Color.White,
            // Colores cuando esta deshabilitado
            disabledContainerColor = BorderGray,
            disabledContentColor = TextSecondary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer8()
            } else if (icon != null) {
                Icon(imageVector = icon, contentDescription = null)
                Spacer8()
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Pequeno espacio horizontal entre el icono y el texto.
@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
}
