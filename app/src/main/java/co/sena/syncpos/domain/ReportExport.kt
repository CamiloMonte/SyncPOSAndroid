package co.sena.syncpos.domain

import co.sena.syncpos.data.remote.ProfitProductDto
import co.sena.syncpos.util.plainNumber

// Cabecera del CSV de rentabilidad.
const val PROFIT_CSV_HEADER = "Producto,Unidades,Ingresos,Costo,Ganancia,Margen %,Contribución %"

// Construye el texto CSV (valores separados por comas) del reporte de rentabilidad.
// Funcion pura: recibe la lista y devuelve un String; escribir el archivo es otro paso.
// Los numeros van SIN separador de miles ("48500", no "48.500") para que Excel / Google
// Sheets los reconozcan como numeros.
fun buildProfitabilityCsv(products: List<ProfitProductDto>): String {
    val lines = mutableListOf(PROFIT_CSV_HEADER)
    products.forEach { p ->
        lines += listOf(
            csvField(p.name),
            p.units.toString(),
            plainNumber(p.revenue),
            plainNumber(p.cost),
            plainNumber(p.profit),
            plainNumber(p.margin, 1),
            plainNumber(p.contribution, 1)
        ).joinToString(",")
    }
    return lines.joinToString("\n") + "\n"
}

// Prepara un texto para ponerlo en una celda CSV. Reglas del formato:
//  - si contiene coma, comillas o salto de linea, se encierra entre comillas dobles
//  - dentro de un campo entre comillas, cada comilla se escribe doble ("")
// Ejemplo: Gaseosa Postobon 1,5 L  ->  "Gaseosa Postobon 1,5 L"
fun csvField(text: String): String {
    val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
