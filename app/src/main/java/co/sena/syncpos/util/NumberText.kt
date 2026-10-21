package co.sena.syncpos.util

import java.math.BigDecimal
import java.math.RoundingMode

// Convierte un numero a texto SIN separador de miles y con punto decimal, quitando los
// ceros sobrantes: 48500.0 -> "48500", 12.50 -> "12.5", 1234567.891 (2 dec.) -> "1234567.89".
// Es el formato que los programas de hojas de calculo leen sin problema (CSV) y el que
// se muestra en los campos de precio al editar un producto.
// BigDecimal se usa porque redondea de forma exacta (HALF_UP = 0.5 sube) y porque
// toPlainString() nunca usa notacion cientifica (4.85E+4).
fun plainNumber(value: Double, maxDecimals: Int = 2): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val rounded = BigDecimal(value).setScale(maxDecimals, RoundingMode.HALF_UP)
    val plain = rounded.stripTrailingZeros().toPlainString()
    // "-0" puede aparecer al redondear un negativo muy pequeno.
    return if (plain == "-0") "0" else plain
}

// Porcentaje para mostrar al usuario: 26.67 -> "26,7%" (coma decimal, como en Colombia).
fun formatPercent(value: Double): String = plainNumber(value, 1).replace('.', ',') + "%"
