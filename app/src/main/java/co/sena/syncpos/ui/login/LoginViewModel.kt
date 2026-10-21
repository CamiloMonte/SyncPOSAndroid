package co.sena.syncpos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.repository.AuthRepository
import co.sena.syncpos.data.repository.CatalogResult
import co.sena.syncpos.data.repository.LoginResult
import co.sena.syncpos.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Todo lo que la pantalla necesita para dibujarse, en un solo objeto.
// La pantalla NO guarda estado propio importante: solo "pinta" este objeto.
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loadingText: String = "Ingresando...",
    val error: String? = null,
    val loggedIn: Boolean = false
)

// ViewModel: guarda el estado de la pantalla y la logica. Sobrevive a rotaciones de pantalla
// (la Activity se destruye y se recrea, pero el ViewModel sigue vivo).
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    // StateFlow = valor observable. _uiState es privado y modificable; uiState es la
    // version de solo lectura que ve la pantalla. Cuando el valor cambia, Compose redibuja.
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    // Solo lo usan los botones de la version demo: rellena el correo (con una clave
    // cualquiera, la API falsa acepta todas) y ejecuta el login normal. Asi la
    // navegacion por rol es exactamente la misma que con el formulario.
    fun loginAs(email: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(email = email, password = "demo", error = null) }
        login()
    }

    fun login() {
        val current = _uiState.value
        if (current.isLoading) return
        if (current.email.isBlank() || current.password.isBlank()) {
            _uiState.update { it.copy(error = "Escribe tu correo y tu contraseña.") }
            return
        }

        // viewModelScope.launch inicia una corrutina: trabajo en segundo plano que se
        // cancela solo si el ViewModel se destruye. Asi no se congela la pantalla.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Ingresando...", error = null) }

            when (val result = authRepository.login(current.email, current.password)) {
                is LoginResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                LoginResult.Success -> {
                    if (authRepository.isAdmin) {
                        // El administrador NO descarga el catalogo: sus pantallas son solo en
                        // linea (leen los datos directo del servidor), no usan Room ni venden
                        // sin conexion. Asi el ingreso es inmediato.
                        _uiState.update { it.copy(isLoading = false, loggedIn = true) }
                    } else {
                        downloadCatalogAfterLogin()
                    }
                }
            }
        }
    }

    // Despues del login se baja el catalogo a Room para poder vender sin internet.
    private suspend fun downloadCatalogAfterLogin() {
        _uiState.update { it.copy(loadingText = "Descargando catálogo...") }

        when (val catalog = productRepository.downloadCatalog()) {
            is CatalogResult.Success -> {
                _uiState.update { it.copy(isLoading = false, loggedIn = true) }
            }
            is CatalogResult.Failure -> {
                if (productRepository.hasProducts()) {
                    // Ya hay un catalogo viejo en el celular: se puede trabajar con el.
                    _uiState.update { it.copy(isLoading = false, loggedIn = true) }
                } else {
                    // Sin catalogo no se puede vender: se cancela el ingreso.
                    authRepository.clearLocalSession()
                    _uiState.update { it.copy(isLoading = false, error = catalog.message) }
                }
            }
        }
    }

    companion object {
        // Fabrica: le ensena a Android como construir este ViewModel con sus dependencias.
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LoginViewModel(container.authRepository, container.productRepository) }
        }
    }
}
