package co.sena.syncpos

import co.sena.syncpos.data.remote.DaySummaryDto
import co.sena.syncpos.data.remote.ProfitabilityDto
import co.sena.syncpos.data.remote.SaleEnvelope
import co.sena.syncpos.data.remote.appJson
import co.sena.syncpos.domain.buildReceiptText
import co.sena.syncpos.domain.formatSaleTime
import co.sena.syncpos.domain.originLabel
import co.sena.syncpos.domain.shortWeekday
import co.sena.syncpos.ui.navigation.Routes
import co.sena.syncpos.ui.navigation.homeRoute
import co.sena.syncpos.util.parseMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// Pruebas de lectura del JSON de la API de administrador y de funciones de presentacion.
class AdminDataTest {

    // ---------- Dinero ----------
    @Test
    fun parseMoney_readsDecimalText() {
        assertEquals(48500.0, parseMoney("48500.00"), 0.0)
        assertEquals(1250.5, parseMoney(" 1250.5 "), 0.0)
    }

    @Test
    fun parseMoney_badInput_isZero() {
        assertEquals(0.0, parseMoney(null), 0.0)
        assertEquals(0.0, parseMoney(""), 0.0)
        assertEquals(0.0, parseMoney("abc"), 0.0)
    }

    @Test
    fun sale_totalAsString_isParsed() {
        val json = """
            {"data":{"id":7,"numero":"#V-7","cajero":{"id":2,"name":"Ana"},"metodo_pago":"nequi",
            "origen":"offline","total":"48500.00","vendida_en":"2026-10-20T14:30:00-05:00",
            "items":[{"producto_id":1,"nombre":"Arroz","cantidad":2,"precio_unitario":"2500.00",
            "subtotal":"5000.00","costo_unitario":"1800.00"}]}}
        """.trimIndent()
        val sale = appJson.decodeFromString<SaleEnvelope>(json).data
        assertEquals(48500.0, sale.total, 0.0)
        assertEquals("Ana", sale.cashier?.name)
        assertEquals(5000.0, sale.items.single().subtotal, 0.0)
    }

    @Test
    fun report_numbersAsNumbers_areParsed() {
        val json = """
            {"periodo":{"desde":"2026-10-01","hasta":"2026-10-20"},
             "totales":{"ingresos":150000,"costo":90000.5,"ganancia":59999.5,"margen":40},
             "productos":[{"producto_id":1,"nombre":"Arroz","unidades":3,"ingresos":9000,"costo":6000,
             "ganancia":3000,"margen":33.3,"contribucion":5}]}
        """.trimIndent()
        val report = appJson.decodeFromString<ProfitabilityDto>(json)
        assertEquals(150000.0, report.totals.revenue, 0.0)
        assertEquals(90000.5, report.totals.cost, 0.0)
        assertEquals(40.0, report.totals.margin, 0.0)
        assertEquals(3000.0, report.products.single().profit, 0.0)
    }

    @Test
    fun daySummary_mixesNumbersAndStrings() {
        val json = """
            {"ventas_total":120000,"pedidos":4,"ganancia_neta":30000.5,"inversion_inventario":900000,
             "alertas_stock":2,
             "ultimas_ventas":[{"id":1,"numero":"#V-1","metodo_pago":"efectivo","origen":"online","total":"5000.00"}],
             "ultimos_7_dias":[{"fecha":"2026-10-20","ventas":120000,"ganancia":30000.5}]}
        """.trimIndent()
        val summary = appJson.decodeFromString<DaySummaryDto>(json)
        assertEquals(120000.0, summary.salesTotal, 0.0)
        assertEquals(2, summary.stockAlerts)
        assertEquals(5000.0, summary.latestSales.single().total, 0.0)
        assertEquals(30000.5, summary.last7Days.single().profit, 0.0)
    }

    // ---------- Origen de la venta ----------
    @Test
    fun originLabel_mapsOnlineAndOffline() {
        assertEquals("Online", originLabel("online"))
        assertEquals("Sinc. Offline", originLabel("offline"))
        assertEquals("Online", originLabel("cualquier-otro"))
    }

    // ---------- Fechas ----------
    @Test
    fun shortWeekday_isSpanish() {
        assertEquals("Mar", shortWeekday("2026-10-20")) // martes
        assertEquals("Dom", shortWeekday("2026-10-18"))
        assertEquals("-", shortWeekday("no-es-fecha"))
    }

    @Test
    fun saleTime_isConvertedToLocalZone() {
        // 14:30 en UTC-5 son las 19:30 en UTC (07:30 p. m.).
        val text = formatSaleTime("2026-10-20T14:30:00-05:00", ZoneId.of("UTC"))
        assertTrue(text, text.startsWith("07:30"))
        assertEquals("--:--", formatSaleTime(null))
    }

    // ---------- Factura compartida ----------
    @Test
    fun receiptText_listsItemsAndTotal() {
        val json = """
            {"data":{"id":7,"numero":"#V-7","cajero":{"id":2,"name":"Ana"},"metodo_pago":"efectivo",
            "total":"5000.00","vendida_en":"2026-10-20T14:30:00-05:00",
            "items":[{"nombre":"Arroz","cantidad":2,"precio_unitario":"2500.00","subtotal":"5000.00"}]}}
        """.trimIndent()
        val text = buildReceiptText(appJson.decodeFromString<SaleEnvelope>(json).data, ZoneId.of("UTC"))
        assertTrue(text.contains("Factura #V-7"))
        assertTrue(text.contains("Cajero: Ana"))
        assertTrue(text.contains("2 x $ 2.500 = $ 5.000"))
        assertTrue(text.endsWith("TOTAL: $ 5.000"))
    }

    // ---------- Navegacion por rol ----------
    @Test
    fun homeRoute_dependsOnRole() {
        assertEquals(Routes.LOGIN, homeRoute(false, "admin"))
        assertEquals(Routes.ADMIN, homeRoute(true, "admin"))
        assertEquals(Routes.POS, homeRoute(true, "cajero"))
        assertEquals(Routes.POS, homeRoute(true, ""))
    }
}
