package co.sena.syncpos.ui.admin.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.ApiResult
import co.sena.syncpos.domain.PaymentMethod
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la hoja "Detalle de Factura" (la hoja esta cerrada cuando detail == null).
sealed interface SaleDetailState {
    data object Loading : SaleDetailState
    data class Error(val message: String) : SaleDetailState
    data class Content(val sale: SaleDto) : SaleDetailState
}

data class SalesUiState(
    val query: String = "",
    val method: PaymentMethod? = null,   // null = todas
    val sales: List<SaleDto> = emptyList(),
    val page: Int = 0,
    val lastPage: Int = 1,
    val total: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedId: Long? = null,         // factura abierta en la hoja
    val detail: SaleDetailState? = null
) {
    val hasMore: Boolean get() = page < lastPage
}

class SalesViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadFirstPage()
    }

    // Busqueda con debounce de 400 ms (ver InventoryViewModel.onQueryChange: se cancela
    // la espera anterior en cada letra y solo se consulta cuando el usuario se detiene).
    fun onQueryChange(text: String) {
        _uiState.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadFirstPage()
        }
    }

    fun selectMethod(method: PaymentMethod?) {
        if (method == _uiState.value.method) return
        searchJob?.cancel()
        _uiState.update { it.copy(method = method) }
        loadFirstPage()
    }

    fun loadFirstPage() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = fetch(1)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        sales = result.data.data,
                        page = result.data.meta.currentPage,
                        lastPage = result.data.meta.lastPage,
                        total = result.data.meta.total,
                        isLoading = false
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, sales = emptyList(), page = 0, error = result.error.message)
                }
            }
        }
    }

    // Pagina siguiente (15 ventas por pagina) con el boton "Cargar mas".
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch {
            when (val result = fetch(current.page + 1)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        sales = it.sales + result.data.data,
                        page = result.data.meta.currentPage,
                        lastPage = result.data.meta.lastPage,
                        isLoadingMore = false
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    messageChannel.send(result.error.message)
                }
            }
        }
    }

    // ---------- Detalle de factura ----------
    // Al tocar una venta se abre la hoja en estado "cargando" y se pide GET ventas/{id},
    // que si trae las lineas (items) de la factura; la lista solo trae el resumen.
    fun openDetail(id: Long) {
        _uiState.update { it.copy(selectedId = id, detail = SaleDetailState.Loading) }
        loadDetail(id)
    }

    fun retryDetail() {
        val id = _uiState.value.selectedId ?: return
        _uiState.update { it.copy(detail = SaleDetailState.Loading) }
        loadDetail(id)
    }

    fun closeDetail() {
        detailJob?.cancel()
        _uiState.update { it.copy(selectedId = null, detail = null) }
    }

    private fun loadDetail(id: Long) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            when (val result = repository.sale(id)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(detail = SaleDetailState.Content(result.data)) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(detail = SaleDetailState.Error(result.error.message)) }
            }
        }
    }

    private suspend fun fetch(page: Int) = repository.sales(
        search = _uiState.value.query,
        paymentMethod = _uiState.value.method?.apiValue,
        page = page
    )

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SalesViewModel(container.adminRepository) }
        }
    }
}
