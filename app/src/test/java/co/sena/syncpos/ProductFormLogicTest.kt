package co.sena.syncpos

import co.sena.syncpos.domain.ean13CheckDigit
import co.sena.syncpos.domain.generateBarcode
import co.sena.syncpos.domain.profitPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Pruebas de la logica del formulario de producto.
class ProductFormLogicTest {

    @Test
    fun profitPreview_computesProfitAndMarginOverSalePrice() {
        val preview = profitPreview(costPrice = 3000.0, salePrice = 4000.0)
        assertEquals(1000.0, preview.profit, 0.0)
        assertEquals(25.0, preview.marginPercent, 0.0001)
    }

    @Test
    fun profitPreview_withoutSalePrice_hasZeroMargin() {
        val preview = profitPreview(costPrice = 3000.0, salePrice = 0.0)
        assertEquals(-3000.0, preview.profit, 0.0)
        assertEquals(0.0, preview.marginPercent, 0.0)
    }

    @Test
    fun ean13CheckDigit_knownValue() {
        // 7,7,0,1,2,3,4,5,6,7,8,9 -> 7+21+0+3+2+9+4+15+6+21+8+27 = 123 -> control 7
        assertEquals(7, ean13CheckDigit("770123456789"))
    }

    @Test
    fun generatedBarcode_has13DigitsAndStartsWith770() {
        val code = generateBarcode(Random(42))
        assertEquals(13, code.length)
        assertTrue(code.all { it.isDigit() })
        assertTrue(code.startsWith("770"))
        assertEquals(ean13CheckDigit(code.take(12)), code.last().digitToInt())
    }
}
