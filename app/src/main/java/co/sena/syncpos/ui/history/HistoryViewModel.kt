package co.sena.syncpos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.connectivity.ConnectivityObserver
import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.local.SaleWithItems
import co.sena.syncpos.data.repository.SalesRepository
import co.sena.syncpos.data.session.SessionManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// Filtros de la lista.
enum class HistoryFilter(val label: String) {
    ALL("Todas"),
    SYNCED("Sincronizadas"),
    PENDING("Pendientes")
}

data class HistoryUiState(
    val sales: List<SaleWithItems> = emptyList(),     // ya filtradas
    val filter: HistoryFilter = HistoryFilter.ALL,
    val totalToday: Double = 0.0,
    val salesCount: Int = 0,
    val syncedCount: Int = 0,
    val pendingCount: Int = 0,         // pendientes de HOY (tarjeta resumen)
    val allPendingCount: Int = 0,      // pendientes de cualquier dia (boton y pastilla)
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val lastSyncAt: Long = 0L,
    val now: Long = System.currentTimeMillis()
)

class HistoryViewModel(
    private val salesRepository: SalesRepository,
    private val session: SessionManager,
    connectivity: ConnectivityObserver
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val isSyncing = MutableStateFlow(false)

    // Mensajes de una sola vez para el Snackbar.
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    // Rango del dia de hoy: de las 00:00 de hoy a las 00:00 de manana (en milisegundos).
    private val zone = ZoneId.systemDefault()
    private val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private val endOfDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // Emite la hora actual cada 30 s para que "hace X min" se vaya actualizando.
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    private val salesAndFilter = combine(
        salesRepository.observeSalesBetween(startOfDay, endOfDay),
        filter,
        isSyncing,
        ticker
    ) { allSales, selected, syncing, now ->
        // Los totales se calculan con TODAS las ventas de hoy; el filtro solo afecta la lista.
        val visible = when (selected) {
            HistoryFilter.ALL -> allSales
            HistoryFilter.SYNCED -> allSales.filter { it.sale.status == SaleStatus.SINCRONIZADA }
            HistoryFilter.PENDING -> allSales.filter { it.sale.status == SaleStatus.PENDIENTE }
        }
        HistoryUiState(
            sales = visible,
            filter = selected,
            totalToday = allSales.sumOf { it.sale.total },
            salesCount = allSales.size,
            syncedCount = allSales.count { it.sale.status == SaleStatus.SINCRONIZADA },
            pendingCount = allSales.count { it.sale.status == SaleStatus.PENDIENTE },
            isSyncing = syncing,
            now = now
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        salesAndFilter,
        connectivity.isOnline,
        salesRepository.pendingCount // cambia cuando termina una sincronizacion
    ) { state, online, allPending ->
        state.copy(isOnline = online, allPendingCount = allPending, lastSyncAt = session.lastSyncAt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun selectFilter(value: HistoryFilter) {
        filter.value = value
    }

    // Boton "Forzar Sincronizacion Ahora".
    fun forceSync() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            val summary = salesRepository.syncPending()
            isSyncing.value = false
            messageChannel.send(summary.toUserMessage() ?: "No hay ventas pendientes por sincronizar")
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HistoryViewModel(container.salesRepository, container.session, container.connectivity)
            }
        }
    }
}
