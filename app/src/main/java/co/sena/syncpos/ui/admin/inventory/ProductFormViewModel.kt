package co.sena.syncpos.ui.admin.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.sena.syncpos.AppContainer
import co.sena.syncpos.data.remote.CategoryDto
import co.sena.syncpos.data.remote.ProductRequest
import co.sena.syncpos.data.repository.AdminRepository
import co.sena.syncpos.data.repository.ApiResult
import co.sena.syncpos.domain.generateBarcode
import co.sena.syncpos.util.plainNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Nombres de los campos tal como los manda el servidor en el error 422. Se usan como
// llave del mapa de errores para saber bajo que campo mostrar cada mensaje.
object ProductField {
    const val BARCODE = "codigo_barras"
    const val NAME = "nombre"
    const val CATEGORY = "categoria_id"
    const val COST = "precio_compra"
    const val SALE = "precio_venta"
    const val STOCK = "stock"
    const val MIN_STOCK = "stock_minimo"
}

// Estado del formulario. Los numeros se guardan como TEXTO (lo que escribe el usuario)
// y solo se convierten a numero al guardar.
data class ProductFormUiState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = true,      // cargando categorias (y el producto si se edita)
    val loadError: String? = null,
    val categories: List<CategoryDto> = emptyList(),
    val barcode: String = "",
    val name: String = "",
    val categoryId: Long? = null,
    val costPrice: String = "",
    val salePrice: String = "",
    val stock: String = "",
    val minStock: String = "",
    val fieldErrors: Map<String, String> = emptyMap(), // campo -> primer error
    val generalError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false // true cuando se guardo bien: la pantalla vuelve atras
)

class ProductFormViewModel(
    private val repository: AdminRepository,
    private val productId: Long? // null = producto nuevo; con valor = editar
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState(isEdit = productId != null))
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    // Carga las categorias (para el desplegable) y, si se edita, los datos del producto.
    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val categories = repository.categories()
            if (categories is ApiResult.Failure) {
                _uiState.update { it.copy(isLoading = false, loadError = categories.error.message) }
                return@launch
            }
            val list = (categories as ApiResult.Success).data

            if (productId == null) {
                _uiState.update { it.copy(isLoading = false, categories = list) }
                return@launch
            }

            when (val result = repository.product(productId)) {
                is ApiResult.Success -> {
                    val p = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = list,
                            barcode = p.barcode.orEmpty(),
                            name = p.name,
                            categoryId = p.category?.id,
                            costPrice = plainNumber(p.costPrice),
                            salePrice = plainNumber(p.salePrice),
                            stock = p.stock.toString(),
                            minStock = p.minStock.toString()
                        )
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, loadError = result.error.message) }
            }
        }
    }

    // ---------- Cambios en los campos ----------
    // Al escribir en un campo se borra SU error (el usuario ya lo esta corrigiendo).
    fun onBarcodeChange(v: String) = edit(ProductField.BARCODE) { it.copy(barcode = v.filter(Char::isDigit)) }
    fun onNameChange(v: String) = edit(ProductField.NAME) { it.copy(name = v) }
    fun onCategoryChange(id: Long) = edit(ProductField.CATEGORY) { it.copy(categoryId = id) }
    fun onCostChange(v: String) = edit(ProductField.COST) { it.copy(costPrice = decimalOnly(v)) }
    fun onSaleChange(v: String) = edit(ProductField.SALE) { it.copy(salePrice = decimalOnly(v)) }
    fun onStockChange(v: String) = edit(ProductField.STOCK) { it.copy(stock = v.filter(Char::isDigit)) }
    fun onMinStockChange(v: String) = edit(ProductField.MIN_STOCK) { it.copy(minStock = v.filter(Char::isDigit)) }

    fun generateBarcodeValue() = edit(ProductField.BARCODE) { it.copy(barcode = generateBarcode()) }

    private fun edit(field: String, change: (ProductFormUiState) -> ProductFormUiState) {
        _uiState.update { change(it).copy(fieldErrors = it.fieldErrors - field, generalError = null) }
    }

    // Deja solo digitos y un unico punto decimal ("12.5"); la coma se toma como punto.
    private fun decimalOnly(text: String): String {
        val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        val firstDot = cleaned.indexOf('.')
        return if (firstDot < 0) cleaned
        else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
    }

    // ---------- Guardar ----------
    fun save() {
        val s = _uiState.value
        if (s.isSaving) return

        // 1) Validacion local minima: campos vacios o numeros ilegibles. Las reglas de
        //    negocio (precio de venta >= compra, codigo repetido...) las valida el SERVIDOR.
        val required = "Este campo es obligatorio."
        val errors = buildMap {
            if (s.barcode.isBlank()) put(ProductField.BARCODE, required)
            if (s.name.isBlank()) put(ProductField.NAME, required)
            if (s.categoryId == null) put(ProductField.CATEGORY, "Elige una categoría.")
            if (s.costPrice.toDoubleOrNull() == null) put(ProductField.COST, "Escribe un número válido.")
            if (s.salePrice.toDoubleOrNull() == null) put(ProductField.SALE, "Escribe un número válido.")
            if (s.stock.toIntOrNull() == null) put(ProductField.STOCK, "Escribe un número entero.")
            if (s.minStock.toIntOrNull() == null) put(ProductField.MIN_STOCK, "Escribe un número entero.")
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, generalError = "Revisa los campos marcados en rojo.") }
            return
        }

        val request = ProductRequest(
            barcode = s.barcode.trim(),
            name = s.name.trim(),
            categoryId = s.categoryId!!, // ya validado arriba
            costPrice = s.costPrice.toDouble(),
            salePrice = s.salePrice.toDouble(),
            stock = s.stock.toInt(),
            minStock = s.minStock.toInt()
        )

        // 2) Enviar al servidor.
        _uiState.update { it.copy(isSaving = true, fieldErrors = emptyMap(), generalError = null) }
        viewModelScope.launch {
            when (val result = repository.saveProduct(productId, request)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, saved = true) }
                is ApiResult.Failure -> _uiState.update {
                    // 422: cada campo recibe su mensaje del servidor. Otros errores: mensaje general.
                    val fieldErrors = result.error.fieldErrors
                    it.copy(
                        isSaving = false,
                        fieldErrors = fieldErrors,
                        generalError = if (fieldErrors.isNotEmpty()) {
                            "Revisa los campos marcados en rojo."
                        } else {
                            result.error.message
                        }
                    )
                }
            }
        }
    }

    companion object {
        // La ruta "producto?id={id}" guarda el id en un SavedStateHandle; el ViewModel lo
        // lee de ahi. -1 significa "producto nuevo" (valor por defecto del argumento).
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                val id = createSavedStateHandle().get<Long>("id")
                ProductFormViewModel(container.adminRepository, id?.takeIf { it > 0 })
            }
        }
    }
}

