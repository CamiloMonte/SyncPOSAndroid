package co.sena.syncpos.domain

// Metodos de pago. "apiValue" es el texto que espera el servidor; "label" es el que ve el usuario.
enum class PaymentMethod(val apiValue: String, val label: String) {
    EFECTIVO("efectivo", "Efectivo"),
    NEQUI("nequi", "Nequi");

    companion object {
        // Convierte el texto guardado en la base de datos de vuelta al enum.
        fun fromApiValue(value: String): PaymentMethod =
            entries.find { it.apiValue == value } ?: EFECTIVO
    }
}
