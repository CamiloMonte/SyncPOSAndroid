package co.sena.syncpos.ui.admin.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.AdminProductDto
import co.sena.syncpos.data.remote.CategoryDto
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.ApiResult
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

// Filtro activo de la lista. Es una "sealed interface": o es Todas, o Stock bajo, o una
// categoria concreta (que ademas trae el id de la categoria).
sealed interface InventoryFilter {
    data object All : InventoryFilter
    data object LowStock : InventoryFilter
    data class Category(val id: Long) : InventoryFilter
}

data class InventoryUiState(
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.All,
    val categories: List<CategoryDto> = emptyList(),
    val products: List<AdminProductDto> = emptyList(),
    val page: Int = 0,           // ultima pagina cargada (0 = ninguna todavia)
    val lastPage: Int = 1,       // total de paginas segun el servidor
    val total: Int = 0,
    val isLoading: Boolean = true,      // cargando la PRIMERA pagina
    val isLoadingMore: Boolean = false, // cargando paginas siguientes ("Cargar mas")
    val error: String? = null,          // error de la primera carga (muestra "Reintentar")
    val pendingToggle: AdminProductDto? = null // producto esperando confirmacion de activar/desactivar
) {
    // Hay mas paginas por pedir mientras no hayamos llegado a la ultima.
    val hasMore: Boolean get() = page < lastPage
}

class InventoryViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // Mensajes de una sola vez para el Snackbar (errores al cargar mas, al activar...).
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    // Job = "control remoto" de una corrutina: permite cancelarla.
    private var loadJob: Job? = null    // la peticion de lista que esta en curso
    private var searchJob: Job? = null  // la espera (debounce) del texto de busqueda

    // Ultimo pedido del Dashboard ("abrir con Stock bajo") ya atendido.
    private var handledLowStockRequest = 0

    init {
        loadCategories()
        loadFirstPage()
        // Si el formulario guarda un producto, la lista se recarga sola.
        viewModelScope.launch {
            repository.productsChanged.collect { loadFirstPage() }
        }
    }

    // ---------- Eventos de la pantalla ----------

    // DEBOUNCE: sin esto se haria una peticion al servidor por CADA letra escrita.
    // Cada vez que cambia el texto se cancela la espera anterior y se empieza otra de
    // 400 ms; solo si el usuario deja de escribir 400 ms la espera termina y se busca.
    fun onQueryChange(text: String) {
        _uiState.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadFirstPage()
        }
    }

    fun selectFilter(filter: InventoryFilter) {
        if (filter == _uiState.value.filter) return
        searchJob?.cancel() // si habia una busqueda en espera, ya no hace falta
        _uiState.update { it.copy(filter = filter) }
        loadFirstPage()
    }

    // El Dashboard sube un contador cada vez que se toca "Alertas de Stock". Solo se
    // aplica el filtro si el contador es nuevo (si no, al volver a esta pestana se
    // forzaria el filtro otra vez aunque el usuario ya lo hubiera cambiado).
    fun handleLowStockRequest(request: Int) {
        if (request > handledLowStockRequest) {
            handledLowStockRequest = request
            selectFilter(InventoryFilter.LowStock)
        }
    }

    fun retry() = loadFirstPage()

    // Pide la pagina 1 y REEMPLAZA la lista.
    fun loadFirstPage() {
        loadJob?.cancel() // si habia otra peticion en curso, su respuesta ya no importa
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = fetch(page = 1)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        products = result.data.data,
                        page = result.data.meta.currentPage,
                        lastPage = result.data.meta.lastPage,
                        total = result.data.meta.total,
                        isLoading = false
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, products = emptyList(), page = 0, error = result.error.message)
                }
            }
        }
    }

    // PAGINACION con boton "Cargar mas": se elige este metodo por ser el mas simple (el
    // usuario controla cuando se pide la siguiente pagina). La alternativa es pedirla
    // sola al llegar al final de la lista, pero requiere vigilar el scroll.
    // Pide la pagina siguiente y la AGREGA al final de la lista.
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch {
            when (val result = fetch(page = current.page + 1)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        products = it.products + result.data.data,
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

    // Activar / desactivar: primero se pide confirmacion con un AlertDialog.
    fun askToggle(product: AdminProductDto) = _uiState.update { it.copy(pendingToggle = product) }

    fun dismissToggle() = _uiState.update { it.copy(pendingToggle = null) }

    fun confirmToggle() {
        val product = _uiState.value.pendingToggle ?: return
        _uiState.update { it.copy(pendingToggle = null) }
        viewModelScope.launch {
            when (val result = repository.toggleProduct(product.id)) {
                is ApiResult.Success -> {
                    // Se cambia SOLO ese producto de la lista (sin recargar todo).
                    val updated = result.data
                    _uiState.update { state ->
                        state.copy(products = state.products.map { if (it.id == updated.id) updated else it })
                    }
                    messageChannel.send(
                        if (updated.active) "Producto activado." else "Producto desactivado."
                    )
                }
                is ApiResult.Failure -> messageChannel.send(result.error.message)
            }
        }
    }

    // ---------- Privado ----------

    private suspend fun fetch(page: Int) = repository.products(
        search = _uiState.value.query,
        categoryId = (_uiState.value.filter as? InventoryFilter.Category)?.id,
        lowStock = _uiState.value.filter == InventoryFilter.LowStock,
        page = page
    )

    private fun loadCategories() {
        viewModelScope.launch {
            val result = repository.categories()
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(categories = result.data) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { InventoryViewModel(container.adminRepository) }
        }
    }
}
