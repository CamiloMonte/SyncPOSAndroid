package co.sena.syncpos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.local.ProductEntity
import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.repository.AuthRepository
import co.sena.syncpos.data.repository.ProductRepository
import co.sena.syncpos.data.repository.SalesRepository
import co.sena.syncpos.domain.Cart
import co.sena.syncpos.domain.PaymentMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado completo de la pantalla de venta.
data class PosUiState(
    val userName: String = "",
    val query: String = "",
    val searchResults: List<ProductEntity> = emptyList(),
    val cart: Cart = Cart(),
    val paymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val isSaving: Boolean = false,
    val isOnline: Boolean = true,
    val pendingCount: Int = 0
)

class PosViewModel(
    private val userName: String,
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
    private val authRepository: AuthRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {

    // Piezas pequenas de estado que cambia el usuario.
    private val query = MutableStateFlow("")
    private val cart = MutableStateFlow(Cart())
    private val paymentMethod = MutableStateFlow(PaymentMethod.EFECTIVO)
    private val isSaving = MutableStateFlow(false)

    // Resultados de busqueda: cada vez que cambia el texto, flatMapLatest cancela la
    // busqueda anterior y consulta Room con el texto nuevo. Sin texto, lista vacia.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults: Flow<List<ProductEntity>> = query.flatMapLatest { text ->
        if (text.isBlank()) flowOf(emptyList()) else productRepository.search(text)
    }

    // Mensajes de una sola vez (para el Snackbar). Un Channel entrega cada mensaje
    // una vez; si fuera parte del estado, se volveria a mostrar al rotar la pantalla.
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    // combine junta varios Flow en uno: cuando CUALQUIERA cambia, se arma un nuevo PosUiState.
    private val userInput = combine(query, searchResults, cart, paymentMethod, isSaving) {
            q, results, c, payment, saving ->
        PosUiState(
            userName = userName,
            query = q,
            searchResults = results,
            cart = c,
            paymentMethod = payment,
            isSaving = saving
        )
    }

    // stateIn convierte el Flow en StateFlow para que la pantalla lo observe.
    // WhileSubscribed(5000): sigue activo 5 s despues de salir (sobrevive a rotaciones).
    val uiState: StateFlow<PosUiState> = combine(
        userInput,
        connectivity.isOnline,
        salesRepository.pendingCount
    ) { state, online, pending ->
        state.copy(isOnline = online, pendingCount = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosUiState(userName = userName))

    // ---------- Busqueda y carrito ----------

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun addProduct(product: ProductEntity) {
        cart.update { it.add(product) }
        query.value = "" // limpiar la busqueda despues de agregar
    }

    fun increment(productId: Long) = cart.update { it.increment(productId) }
    fun decrement(productId: Long) = cart.update { it.decrement(productId) }
    fun remove(productId: Long) = cart.update { it.remove(productId) }
    fun clearCart() = cart.update { it.clear() }

    fun selectPayment(method: PaymentMethod) {
        paymentMethod.value = method
    }

    // ---------- Escaner ----------

    fun onBarcodeScanned(code: String) {
        viewModelScope.launch {
            val product = productRepository.findByBarcode(code)
            if (product != null) {
                cart.update { it.add(product) }
            } else {
                messageChannel.send("Producto no encontrado: $code")
            }
        }
    }

    fun onScanError(message: String?) {
        messageChannel.trySend("No se pudo abrir el escáner: ${message ?: "error desconocido"}")
    }

    // ---------- Registrar venta ----------

    fun registerSale() {
        val currentCart = cart.value
        if (currentCart.isEmpty || isSaving.value) return

        viewModelScope.launch {
            isSaving.value = true
            // Guarda en Room y luego intenta sincronizar (ver SalesRepository).
            val status = salesRepository.registerSale(currentCart, paymentMethod.value)
            cart.value = Cart()
            isSaving.value = false

            messageChannel.send(
                when (status) {
                    SaleStatus.SINCRONIZADA -> "Venta registrada y sincronizada ✅"
                    SaleStatus.PENDIENTE -> "Venta guardada sin conexión. Se sincronizará automáticamente."
                    SaleStatus.RECHAZADA -> "El servidor rechazó la venta. Revisa el historial."
                }
            )
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PosViewModel(
                    userName = container.session.userName,
                    productRepository = container.productRepository,
                    salesRepository = container.salesRepository,
                    authRepository = container.authRepository,
                    connectivity = container.connectivity
                )
            }
        }
    }
}
