package co.sena.syncpos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.sena.syncpos.ui.theme.OfflineAmber
import co.sena.syncpos.ui.theme.OfflineBg
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineGreen
import co.sena.syncpos.ui.theme.OnlineText

// "Pastilla" que muestra el estado de la conexion:
//  - verde "Online" cuando hay internet
//  - ambar "Offline (N pend.)" cuando no hay, con el numero de ventas pendientes
// Se usa en la pantalla de venta y en el historial.
@Composable
fun ConnectionPill(isOnline: Boolean, pendingCount: Int, modifier: Modifier = Modifier) {
    val background = if (isOnline) OnlineBg else OfflineBg
    val dotColor = if (isOnline) OnlineGreen else OfflineAmber
    val textColor = if (isOnline) OnlineText else OfflineText
    val label = if (isOnline) "Online" else "Offline ($pendingCount pend.)"

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Puntito de color
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

// Franja ambar que aparece cuando no hay conexion.
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(OfflineBg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Sin conexión. Las ventas se guardan en el celular y se enviarán al reconectar.",
            style = MaterialTheme.typography.labelSmall,
            color = OfflineText
        )
    }
}
