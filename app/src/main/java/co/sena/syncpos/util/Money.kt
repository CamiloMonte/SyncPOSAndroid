package co.sena.syncpos.util

import kotlin.math.abs
import kotlin.math.roundToLong

// Convierte un valor en pesos colombianos a texto: 46200.0 -> "$ 46.200".
// Se hace a mano (sin NumberFormat) para que el resultado sea igual en todos los
// celulares sin importar su idioma, y para poder probarlo con una prueba unitaria.
fun formatCop(amount: Double): String {
    val rounded = amount.roundToLong()      // COP no usa decimales
    val digits = abs(rounded).toString()    // solo los numeros, sin signo

    val grouped = StringBuilder()
    digits.forEachIndexed { index, char ->
        // Antes de cada grupo de 3 digitos (contando desde la derecha) va un punto.
        if (index > 0 && (digits.length - index) % 3 == 0) grouped.append('.')
        grouped.append(char)
    }

    val sign = if (rounded < 0) "-" else ""
    return "$sign\$ $grouped"
}

// Convierte el dinero que llega de la API a Double.
// El servidor manda el dinero de dos formas distintas:
//   - como TEXTO en ventas y productos: "48500.00"
//   - como NUMERO en los reportes: 48500
// En los dos casos el texto del valor se puede leer con toDoubleOrNull().
// Si viene vacio o con basura se devuelve 0.0 en vez de romper la pantalla.
fun parseMoney(text: String?): Double = text?.trim()?.toDoubleOrNull() ?: 0.0
