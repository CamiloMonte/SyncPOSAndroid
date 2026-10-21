package co.sena.syncpos.ui.admin.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.ProfitabilityDto
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.ApiResult
import co.sena.syncpos.domain.DateRange
import co.sena.syncpos.domain.RangeOption
import co.sena.syncpos.domain.rangeFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ReportsUiState(
    val option: RangeOption = RangeOption.MONTH,
    val customRange: DateRange? = null,
    val range: DateRange,                 // periodo que se esta consultando
    val showRangePicker: Boolean = false, // dialogo de calendario abierto
    val report: ProfitabilityDto? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class ReportsViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReportsUiState(range = rangeFor(RangeOption.MONTH, LocalDate.now()))
    )
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectOption(option: RangeOption) {
        if (option == RangeOption.CUSTOM) {
            // "Rango" no consulta todavia: primero abre el calendario para elegir fechas.
            _uiState.update { it.copy(showRangePicker = true) }
            return
        }
        _uiState.update {
            it.copy(option = option, range = rangeFor(option, LocalDate.now()))
        }
        load()
    }

    fun dismissRangePicker() = _uiState.update { it.copy(showRangePicker = false) }

    // El DateRangePicker entrega las fechas como milisegundos a medianoche UTC. Se
    // convierten a LocalDate usando UTC tambien (si se usara la zona local, en Colombia
    // UTC-5 el dia saldria corrido uno hacia atras).
    fun applyCustomRange(startMillis: Long, endMillis: Long) {
        val from = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val to = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val custom = DateRange(from, to)
        _uiState.update {
            it.copy(
                option = RangeOption.CUSTOM,
                customRange = custom,
                range = rangeFor(RangeOption.CUSTOM, LocalDate.now(), custom),
                showRangePicker = false
            )
        }
        load()
    }

    fun load() {
        loadJob?.cancel() // si el usuario cambia de periodo rapido, solo cuenta la ultima peticion
        val range = _uiState.value.range
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.profitability(range.from, range.to)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoading = false, report = result.data) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, report = null, error = result.error.message) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ReportsViewModel(container.adminRepository) }
        }
    }
}
