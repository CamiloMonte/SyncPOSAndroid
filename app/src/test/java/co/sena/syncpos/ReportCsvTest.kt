package co.sena.syncpos

import co.sena.syncpos.data.remote.ProfitProductDto
import co.sena.syncpos.domain.PROFIT_CSV_HEADER
import co.sena.syncpos.domain.buildProfitabilityCsv
import co.sena.syncpos.domain.csvField
import co.sena.syncpos.util.formatPercent
import co.sena.syncpos.util.plainNumber
import org.junit.Assert.assertEquals
import org.junit.Test

// Pruebas del CSV de rentabilidad y de los formatos de numero.
class ReportCsvTest {

    private fun product(name: String) = ProfitProductDto(
        productId = 1, name = name, units = 1, revenue = 0.0, cost = 0.0,
        profit = 0.0, margin = 0.0, contribution = 0.0
    )

    @Test
    fun header_isExact() {
        assertEquals(
            "Producto,Unidades,Ingresos,Costo,Ganancia,Margen %,Contribución %",
            PROFIT_CSV_HEADER
        )
        assertEquals(PROFIT_CSV_HEADER + "\n", buildProfitabilityCsv(emptyList()))
    }

    @Test
    fun row_hasAllColumns() {
        val csv = buildProfitabilityCsv(
            listOf(
                ProfitProductDto(
                    productId = 3, name = "Arroz", units = 12, revenue = 48500.0, cost = 36000.0,
                    profit = 12500.0, margin = 25.8, contribution = 40.0
                )
            )
        )
        assertEquals(PROFIT_CSV_HEADER + "\nArroz,12,48500,36000,12500,25.8,40\n", csv)
    }

    @Test
    fun nameWithComma_isQuoted() {
        val csv = buildProfitabilityCsv(listOf(product("Gaseosa Postobón 1,5 L")))
        assertEquals("\"Gaseosa Postobón 1,5 L\",1,0,0,0,0,0", csv.lines()[1])
    }

    @Test
    fun nameWithQuotes_areDoubled() {
        assertEquals("\"Leche \"\"Entera\"\"\"", csvField("Leche \"Entera\""))
    }

    @Test
    fun plainName_isNotQuoted() {
        assertEquals("Arroz", csvField("Arroz"))
    }

    @Test
    fun numbers_haveNoThousandsSeparator() {
        assertEquals("1234567", plainNumber(1234567.0))
        assertEquals("1234567.89", plainNumber(1234567.891))
        assertEquals("12.5", plainNumber(12.5))
        assertEquals("0", plainNumber(0.0))
        assertEquals("0", plainNumber(-0.001))
    }

    @Test
    fun percent_usesCommaDecimal() {
        assertEquals("26,7%", formatPercent(26.67))
        assertEquals("30%", formatPercent(30.0))
    }
}
