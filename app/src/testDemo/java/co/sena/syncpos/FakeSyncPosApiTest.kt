package co.sena.syncpos

import co.sena.syncpos.data.remote.FakeSyncPosApi
import co.sena.syncpos.data.remote.LoginRequest
import co.sena.syncpos.data.remote.ProductRequest
import co.sena.syncpos.data.remote.SaleDto
import co.sena.syncpos.data.remote.SyncItemDto
import co.sena.syncpos.data.remote.SyncRequest
import co.sena.syncpos.data.remote.SyncSaleDto
import co.sena.syncpos.data.remote.appJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Pruebas de la API falsa de la demo. Corren en la JVM (sin emulador) porque la clase no
// depende de Android. Se usa un reloj FIJO para que "hoy" siempre sea el mismo dia y los
// resultados no cambien segun cuando se ejecute la prueba.
// Esta carpeta (src/testDemo) solo se ejecuta con testDemoDebugUnitTest.
class FakeSyncPosApiTest {

    private val zone = ZoneOffset.ofHours(-5)
    private val clock = Clock.fixed(Instant.parse("2026-10-21T20:00:00Z"), zone) // 15:00 local
    private val today = LocalDate.of(2026, 10, 21)

    private fun api(online: Boolean = true) =
        FakeSyncPosApi(isOnline = { online }, clock = clock, delayMillis = 0)

    // Recorre todas las paginas del historial y devuelve las ventas (sin items).
    private suspend fun allSales(api: FakeSyncPosApi): List<SaleDto> {
        val all = mutableListOf<SaleDto>()
        var page = 1
        do {
            val response = api.sales(null, null, page)
            all += response.data
            page++
        } while (page <= response.meta.lastPage)
        return all
    }

    private fun syncRequest(reference: String, productId: Long, quantity: Int) = SyncRequest(
        listOf(
            SyncSaleDto(
                offlineReference = reference,
                paymentMethod = "efectivo",
                soldAt = OffsetDateTime.now(clock).toString(),
                origin = "online",
                items = listOf(SyncItemDto(productId, quantity))
            )
        )
    )

    @Test
    fun login_adminAndCashierGetTheirRoles_anyPasswordWorks() = runTest {
        val api = api()
        val admin = api.login(LoginRequest("admin@syncpos.co", "cualquier-cosa"))
        assertEquals("admin", admin.user.rol)
        assertEquals("Andrés Díaz", admin.user.name)
        val cashier = api.login(LoginRequest("cajero@syncpos.co", "x"))
        assertEquals("cajero", cashier.user.rol)
        assertEquals("Laura Gómez", cashier.user.name)
    }

    @Test
    fun login_unknownEmail_is422WithMessage() = runTest {
        try {
            api().login(LoginRequest("nadie@syncpos.co", "x"))
            fail("Debia lanzar HttpException 422")
        } catch (e: HttpException) {
            assertEquals(422, e.code())
            val body = appJson.parseToJsonElement(e.response()!!.errorBody()!!.string()).jsonObject
            assertEquals("Credenciales incorrectas.", body["message"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun catalog_hasTenActiveProductsWithSequentialIds() = runTest {
        val response = api().products(onlyActive = 1, page = 1)
        assertEquals((1L..10L).toList(), response.data.map { it.id }.sorted())
        assertEquals(1, response.meta.lastPage)
        assertEquals(10, response.meta.total)
    }

    @Test
    fun syncSale_decrementsStock_thenRepeatedReferenceIsDuplicate() = runTest {
        val api = api()
        assertEquals(42, api.adminProduct(1).data.stock) // Cafe Sello Rojo
        val salesBefore = allSales(api).size

        val first = api.syncSales(syncRequest("ref-1", productId = 1, quantity = 3)).resultados.single()
        assertEquals("sincronizada", first.estado)
        assertNotNull(first.saleId)
        assertEquals(39, api.adminProduct(1).data.stock)
        assertEquals(salesBefore + 1, allSales(api).size)

        // Mismo envio otra vez: no crea venta ni vuelve a descontar stock.
        val second = api.syncSales(syncRequest("ref-1", productId = 1, quantity = 3)).resultados.single()
        assertEquals("duplicada", second.estado)
        assertEquals(first.saleId, second.saleId)
        assertEquals(39, api.adminProduct(1).data.stock)
        assertEquals(salesBefore + 1, allSales(api).size)

        // La venta nueva usa el precio del servidor (13500 x 3) y se ve en el detalle.
        val detail = api.sale(first.saleId!!).data
        assertEquals(40500.0, detail.total, 0.001)
        assertEquals(1, detail.items.size)
    }

    @Test
    fun syncSale_unknownProduct_isRejected() = runTest {
        val result = api().syncSales(syncRequest("ref-x", productId = 999, quantity = 1)).resultados.single()
        assertEquals("rechazada", result.estado)
        assertNotNull(result.message)
    }

    @Test
    fun profitability_totalsEqualSumOfProducts_andRevenueMatchesAllSales() = runTest {
        val api = api()
        val report = api.profitability(today.minusDays(6).toString(), today.toString())
        assertTrue(report.products.isNotEmpty())
        assertEquals(report.totals.revenue, report.products.sumOf { it.revenue }, 0.01)
        assertEquals(report.totals.cost, report.products.sumOf { it.cost }, 0.01)
        assertEquals(report.totals.profit, report.products.sumOf { it.profit }, 0.01)
        assertEquals(report.totals.revenue - report.totals.cost, report.totals.profit, 0.01)
        assertEquals(100.0, report.products.sumOf { it.contribution }, 0.5) // redondeos de 1 decimal
        // Los siete dias cubren las 25 ventas de ejemplo.
        assertEquals(allSales(api).sumOf { it.total }, report.totals.revenue, 0.01)
        // Ranking de mayor a menor ganancia.
        assertEquals(
            report.products.sortedByDescending { it.profit }.map { it.productId },
            report.products.map { it.productId }
        )
    }

    @Test
    fun daySummary_matchesSalesOfToday() = runTest {
        val api = api()
        val summary = api.daySummary(LocalDate.now(clock).toString())
        val todays = allSales(api).filter {
            OffsetDateTime.parse(it.soldAt!!).atZoneSameInstant(zone).toLocalDate() == today
        }
        assertEquals(5, todays.size)
        assertEquals(todays.size, summary.orders)
        assertEquals(todays.sumOf { it.total }, summary.salesTotal, 0.01)

        val last = summary.last7Days
        assertEquals(7, last.size)
        assertEquals(today.toString(), last.last().date)
        assertEquals(summary.salesTotal, last.last().sales, 0.01)
        assertEquals(summary.netProfit, last.last().profit, 0.01)

        // Alertas = productos activos con stock <= minimo (Arroz, Leche, Jabon, Atun).
        assertEquals(4, summary.stockAlerts)
        assertEquals(5, summary.latestSales.size)
    }

    @Test
    fun salesHistory_isPaginatedBy15_andFiltersByPaymentMethod() = runTest {
        val api = api()
        val page1 = api.sales(null, null, 1)
        val page2 = api.sales(null, null, 2)
        assertEquals(25, page1.meta.total)
        assertEquals(2, page1.meta.lastPage)
        assertEquals(15, page1.data.size)
        assertEquals(10, page2.data.size)

        val nequi = api.sales(null, "nequi", 1)
        assertTrue(nequi.data.isNotEmpty())
        assertTrue(nequi.data.all { it.paymentMethod == "nequi" })

        // El historial viene de la mas reciente a la mas antigua.
        val dates = page1.data.map { OffsetDateTime.parse(it.soldAt!!).toInstant() }
        assertEquals(dates.sortedDescending(), dates)

        val offline = api.filteredSales(null, null, "offline", null, null, 1)
        assertTrue(offline.data.isNotEmpty() && offline.data.all { it.origin == "offline" })
    }

    @Test
    fun createProduct_salePriceBelowCost_returns422WithFieldError() = runTest {
        val api = api()
        val request = ProductRequest("7700000000001", "Producto nuevo", 1, 5000.0, 4000.0, 10, 2)
        try {
            api.createProduct(request)
            fail("Debia lanzar HttpException 422")
        } catch (e: HttpException) {
            assertEquals(422, e.code())
            val body = appJson.parseToJsonElement(e.response()!!.errorBody()!!.string()).jsonObject
            val errors = body["errors"] as JsonObject
            val first = (errors["precio_venta"] as JsonArray).first().jsonPrimitive.content
            assertEquals("El precio de venta no puede ser menor al precio de compra.", first)
        }
        // No se creo nada.
        assertEquals(10, api.adminProducts(null, null, null, 1).meta.total)
    }

    @Test
    fun productCreateUpdateToggle_mutateTheInMemoryList() = runTest {
        val api = api()
        val created = api.createProduct(ProductRequest("7700000000002", "Pan Bimbo", 5, 3000.0, 4500.0, 20, 5)).data
        assertEquals(11L, created.id)
        assertEquals(1500.0, created.unitProfit, 0.001)

        val updated = api.updateProduct(
            created.id, ProductRequest("7700000000002", "Pan Bimbo Grande", 5, 3000.0, 5000.0, 2, 5)
        ).data
        assertEquals("Pan Bimbo Grande", updated.name)
        assertTrue(updated.lowStock) // stock 2 <= minimo 5

        assertFalse(api.toggleProduct(created.id).data.active)
        assertEquals(10, api.products(onlyActive = 1, page = 1).meta.total) // el desactivado no baja al catalogo
        assertEquals(11, api.adminProducts(null, null, null, 1).meta.total)

        // Filtros: texto sin tildes, stock bajo y categoria.
        assertEquals(1, api.adminProducts("cafe", null, null, 1).data.size)
        assertTrue(api.adminProducts(null, null, 1, 1).data.all { it.lowStock })
        assertTrue(api.adminProducts(null, 1L, null, 1).data.all { it.category?.id == 1L })
    }

    @Test
    fun noNetwork_throwsIOException_exceptLogin() = runTest {
        val api = api(online = false)
        assertEquals("admin", api.login(LoginRequest("admin@syncpos.co", "x")).user.rol)
        try {
            api.daySummary(LocalDate.now(clock).toString())
            fail("Debia lanzar IOException")
        } catch (e: IOException) {
            assertEquals("Sin conexion (demo)", e.message)
        }
        try {
            api.syncSales(syncRequest("ref-off", 1, 1))
            fail("Debia lanzar IOException")
        } catch (e: IOException) {
            // esperado: la venta sigue pendiente en el celular
        }
    }
}
