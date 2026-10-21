package co.sena.syncpos.ui.admin.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.sena.syncpos.AppContainer
import co.sena.syncpos.domain.profitPreview
import co.sena.syncpos.ui.admin.ErrorState
import co.sena.syncpos.ui.admin.LoadingState
import co.sena.syncpos.ui.components.PrimaryButton
import co.sena.syncpos.ui.theme.BackgroundGray
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DangerRed
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.OnlineText
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.SenaGreenSoft
import co.sena.syncpos.ui.theme.SurfaceWhite
import co.sena.syncpos.util.formatCop
import co.sena.syncpos.util.formatPercent

// Pantalla completa para crear o editar un producto (se abre encima de las pestanas,
// por eso la barra inferior desaparece). Tiene flecha de volver.
@Composable
fun ProductFormScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ProductFormViewModel = viewModel(factory = ProductFormViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Cuando se guarda bien, se vuelve a la lista (que ya se esta recargando sola).
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        containerColor = BackgroundGray,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = DarkSlate)
                }
                Text(
                    text = if (state.isEdit) "Editar Producto" else "Nuevo Producto",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkSlate
                )
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            state.loadError != null -> ErrorState(
                message = state.loadError!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(innerPadding)
            )
            else -> FormContent(state, viewModel, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun FormContent(state: ProductFormUiState, vm: ProductFormViewModel, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding() // sube el contenido cuando aparece el teclado
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Codigo de barras + enlace "Generar" dentro del mismo campo (trailingIcon).
        FormField(
            label = "Código de barras",
            value = state.barcode,
            onValueChange = vm::onBarcodeChange,
            error = state.fieldErrors[ProductField.BARCODE],
            keyboardType = KeyboardType.Number,
            trailing = { TextButton(onClick = vm::generateBarcodeValue) { Text("Generar", color = SenaGreen) } }
        )

        FormField(
            label = "Nombre",
            value = state.name,
            onValueChange = vm::onNameChange,
            error = state.fieldErrors[ProductField.NAME]
        )

        CategoryDropdown(state = state, onSelect = vm::onCategoryChange)

        FormField(
            label = "Precio de compra",
            value = state.costPrice,
            onValueChange = vm::onCostChange,
            error = state.fieldErrors[ProductField.COST],
            keyboardType = KeyboardType.Decimal
        )
        FormField(
            label = "Precio de venta",
            value = state.salePrice,
            onValueChange = vm::onSaleChange,
            error = state.fieldErrors[ProductField.SALE],
            keyboardType = KeyboardType.Decimal
        )

        // Caja verde con la ganancia calculada EN VIVO: se recalcula cada vez que cambia
        // el estado (Compose vuelve a ejecutar esta funcion con los valores nuevos).
        val preview = profitPreview(
            costPrice = state.costPrice.toDoubleOrNull() ?: 0.0,
            salePrice = state.salePrice.toDoubleOrNull() ?: 0.0
        )
        Text(
            text = "Ganancia por unidad: ${formatCop(preview.profit)} · Margen ${formatPercent(preview.marginPercent)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnlineText,
            modifier = Modifier
                .fillMaxWidth()
                .background(SenaGreenSoft, RoundedCornerShape(12.dp))
                .padding(14.dp)
        )

        FormField(
            label = "Stock",
            value = state.stock,
            onValueChange = vm::onStockChange,
            error = state.fieldErrors[ProductField.STOCK],
            keyboardType = KeyboardType.Number
        )
        FormField(
            label = "Stock mínimo",
            value = state.minStock,
            onValueChange = vm::onMinStockChange,
            error = state.fieldErrors[ProductField.MIN_STOCK],
            keyboardType = KeyboardType.Number
        )

        // Mensaje general (debajo de los campos, encima del boton).
        if (state.generalError != null) {
            Text(state.generalError, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = if (state.isSaving) "Guardando..." else "Guardar Producto",
            icon = Icons.Filled.Check,
            loading = state.isSaving,
            onClick = vm::save
        )
        Spacer(Modifier.height(16.dp))
    }
}

// Campo de texto con borde, estilo del diseno y soporte de error:
//  - isError = true pinta el borde y la etiqueta de rojo
//  - supportingText es el texto pequeno de ayuda/error debajo del campo
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = formColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

// Lista desplegable de categorias. ExposedDropdownMenuBox junta un campo de solo lectura
// con un menu flotante; "expanded" dice si el menu esta abierto.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(state: ProductFormUiState, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.categories.firstOrNull { it.id == state.categoryId }?.nombre.orEmpty()
    val error = state.fieldErrors[ProductField.CATEGORY]

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {}, // solo lectura: el texto lo pone el menu
            readOnly = true,
            label = { Text("Categoría") },
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = formColors(),
            // menuAnchor une el campo con el menu para que este se abra pegado a el.
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.nombre) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun formColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SenaGreen,
    unfocusedBorderColor = BorderGray,
    focusedLabelColor = SenaGreen,
    cursorColor = SenaGreen,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite
)
