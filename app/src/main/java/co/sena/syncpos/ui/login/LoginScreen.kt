package co.sena.syncpos.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.ui.components.PrimaryButton
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SenaGreenSoft
import co.sena.syncpos.ui.theme.TextSecondary

// Pantalla de login. Se divide en dos funciones composable:
//  - LoginScreen: "contenedor", conoce el ViewModel y navega.
//  - LoginContent: "presentacion", solo dibuja datos y avisa eventos (facil de reutilizar).
@Composable
fun LoginScreen(container: AppContainer, onLoggedIn: () -> Unit) {
    // viewModel(factory) obtiene (o crea) el ViewModel asociado a esta pantalla.
    val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(container))

    // collectAsStateWithLifecycle convierte el StateFlow en un "State" de Compose:
    // cuando el flujo cambia, esta funcion se vuelve a ejecutar y la UI se redibuja.
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // LaunchedEffect ejecuta codigo una vez cuando cambia la "llave" (state.loggedIn).
    // Se usa para eventos de una sola vez, como navegar a otra pantalla.
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLogin = viewModel::login
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    // Modifier = lista de "ajustes" que se encadenan (tamano, margen, fondo, clic...).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .imePadding() // sube el contenido cuando aparece el teclado
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(32.dp))

            // Logo: cuadro verde con esquinas redondeadas y un icono de base de datos.
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SenaGreen, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("SyncPOS", style = MaterialTheme.typography.headlineMedium, color = DarkSlate)
            Text("Punto de Venta Móvil", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Spacer(Modifier.height(32.dp))

            // Campo de correo. leadingIcon = icono dentro del campo, a la izquierda.
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Campo de contrasena: PasswordVisualTransformation muestra puntos en vez del texto.
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // Mensaje de error (solo si existe).
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error,
                    color = DangerRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = if (state.isLoading) state.loadingText else "Iniciar Sesión",
                onClick = onLogin,
                loading = state.isLoading
            )

            Spacer(Modifier.height(20.dp))

            // Caja informativa verde suave.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SenaGreenSoft, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Al iniciar sesión se descarga el catálogo de productos en el dispositivo para vender aunque no haya conexión.",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnlineText
                )
            }

            Spacer(Modifier.height(32.dp))
            Text("v1.0.0 · Proyecto SENA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// Colores de los campos de texto (borde gris, borde verde al enfocar).
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SenaGreen,
    unfocusedBorderColor = BorderGray,
    focusedLabelColor = SenaGreen,
    cursorColor = SenaGreen
)
