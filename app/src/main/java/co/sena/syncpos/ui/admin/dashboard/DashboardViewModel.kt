package co.sena.syncpos.ui.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.DaySummaryDto
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Estado de la pantalla como "sealed interface": la pantalla esta SIEMPRE en uno (y solo
// uno) de estos tres casos. Asi no puede pasar que se muestre "cargando" y "error" a la vez,
// y el compilador obliga a dibujar los tres con un "when".
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Error(val message: String) : DashboardUiState

    // isRefreshing = true cuando se pidio actualizar pero aun se muestran los datos viejos.
    data class Content(val summary: DaySummaryDto, val isRefreshing: Boolean = false) : DashboardUiState
}

class DashboardViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
        // Si se crea/edita un producto, las alertas de stock y la inversion cambian:
        // se vuelve a pedir el resumen (en silencio, sin pantalla de carga).
        viewModelScope.launch {
            repository.productsChanged.collect { load() }
        }
    }

    // Pide el resumen al servidor. Si ya hay datos en pantalla se conservan mientras llegan
    // los nuevos (no se parpadea a "cargando").
    fun load() {
        val current = _uiState.value
        _uiState.value = if (current is DashboardUiState.Content) {
            current.copy(isRefreshing = true)
        } else {
            DashboardUiState.Loading
        }

        viewModelScope.launch {
            when (val result = repository.daySummary()) {
                is ApiResult.Success -> _uiState.value = DashboardUiState.Content(result.data)
                is ApiResult.Failure -> {
                    // Si ya habia datos, se dejan en pantalla; si no, se muestra el error.
                    val old = _uiState.value
                    _uiState.value = if (old is DashboardUiState.Content) {
                        old.copy(isRefreshing = false)
                    } else {
                        DashboardUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DashboardViewModel(container.adminRepository) }
        }
    }
}
