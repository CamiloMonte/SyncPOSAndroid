package co.sena.syncpos.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OfflineAmber
import co.sena.syncpos.ui.theme.OfflineBg
import co.sena.syncpos.ui.theme.OfflineText
import co.sena.syncpos.ui.theme.OnlineBg
import co.sena.syncpos.ui.theme.OnlineGreen
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.ui.theme.TextSecondary

// Piezas de UI que comparten las 4 pantallas del administrador.

// Barra superior propia (en vez de TopAppBar de Material) para copiar el diseno de Figma:
// titulo + subtitulo a la izquierda, pastilla de conexion, acciones extra y boton de salir.
// "actions" es un parametro composable con receptor RowScope: quien llama puede poner
// ahi cualquier cantidad de botones (por ejemplo "Actualizar") dentro de la misma fila.
@Composable
fun AdminTopBar(
    title: String,
    subtitle: String,
    isOnline: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // deja libre la franja de la hora/bateria
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = DarkSlate)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            AdminStatusPill(isOnline = isOnline)
            actions()
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Cerrar sesión",
                    tint = DarkSlate
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderGray))
    }
}

// Pastilla de conexion del admin: verde "MySQL" (hay conexion con la base de datos del
// servidor) o ambar "Sin conexion".
@Composable
fun AdminStatusPill(isOnline: Boolean, modifier: Modifier = Modifier) {
    val background = if (isOnline) OnlineBg else OfflineBg
    val dotColor = if (isOnline) OnlineGreen else OfflineAmber
    val textColor = if (isOnline) OnlineText else OfflineText

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(
            text = if (isOnline) "MySQL" else "Sin conexión",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

// Pastilla de texto generica (estado de stock, origen de la venta, metodo de pago...).
@Composable
fun TextPill(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Estado "cargando": un circulo de progreso centrado.
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SenaGreen)
    }
}

// Estado "error": mensaje + boton Reintentar. Lo usan todas las pantallas cuando la
// primera carga falla (por ejemplo sin internet).
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = DangerRed,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SenaGreen, contentColor = Color.White)
        ) {
            Text("Reintentar")
        }
    }
}
