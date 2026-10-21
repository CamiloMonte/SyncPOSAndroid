package co.sena.syncpos.domain

import kotlin.random.Random

// Logica pura del formulario de producto (sin Android, facil de probar).

// Ganancia por unidad y margen de un producto.
data class ProfitPreview(val profit: Double, val marginPercent: Double)

// Ganancia = precio de venta - precio de compra.
// Margen = ganancia / precio de venta * 100 (se calcula sobre la VENTA, igual que el servidor).
// Si el precio de venta es 0 o menos el margen es 0 (no se puede dividir entre cero).
fun profitPreview(costPrice: Double, salePrice: Double): ProfitPreview {
    val profit = salePrice - costPrice
    val margin = if (salePrice > 0) profit / salePrice * 100 else 0.0
    return ProfitPreview(profit, margin)
}

// Genera un codigo de barras EAN-13 que empieza por 770 (prefijo GS1 de Colombia).
// Son 12 digitos + 1 digito de control = 13 digitos.
// "random" se recibe como parametro para poder probar la funcion con un valor fijo.
fun generateBarcode(random: Random = Random.Default): String {
    val body = "770" + (1..9).map { random.nextInt(10) }.joinToString("")
    return body + ean13CheckDigit(body)
}

// Digito de control EAN-13: se suman los 12 digitos multiplicando por 1 y por 3 de forma
// alterna (empezando por 1) y el control es lo que falta para llegar a la siguiente decena.
fun ean13CheckDigit(first12: String): Int {
    val sum = first12.mapIndexed { index, char ->
        val digit = char.digitToInt()
        if (index % 2 == 0) digit else digit * 3
    }.sum()
    return (10 - sum % 10) % 10
}
