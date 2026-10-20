package co.sena.syncpos

import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.remote.SyncResultDto
import co.sena.syncpos.domain.SyncSummary
import co.sena.syncpos.domain.mapSyncResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pruebas de como se traduce la respuesta de /sync/ventas a estados locales.
class SyncMappingTest {

    @Test
    fun sincronizada_becomesSynced_withServerId() {
        val updates = mapSyncResults(listOf(SyncResultDto("ref-1", "sincronizada", saleId = 55)))

        assertEquals(1, updates.size)
        assertEquals(SaleStatus.SINCRONIZADA, updates[0].status)
        assertEquals(55L, updates[0].serverId)
        assertEquals("ref-1", updates[0].offlineReference)
    }

    @Test
    fun duplicada_isTreatedAsSuccess() {
        val updates = mapSyncResults(listOf(SyncResultDto("ref-2", "duplicada", saleId = 56)))

        assertEquals(SaleStatus.SINCRONIZADA, updates[0].status)
        assertEquals(56L, updates[0].serverId)
    }

    @Test
    fun rechazada_becomesRejected_withMessage() {
        val updates = mapSyncResults(
            listOf(SyncResultDto("ref-3", "rechazada", message = "Producto inactivo"))
        )

        assertEquals(SaleStatus.RECHAZADA, updates[0].status)
        assertEquals("Producto inactivo", updates[0].message)
        assertNull(updates[0].serverId)
    }

    @Test
    fun unknownState_isIgnored_soSaleStaysPending() {
        val updates = mapSyncResults(listOf(SyncResultDto("ref-4", "algo-raro")))

        assertEquals(0, updates.size)
    }

    @Test
    fun mixedResults_keepOrder() {
        val updates = mapSyncResults(
            listOf(
                SyncResultDto("a", "sincronizada", saleId = 1),
                SyncResultDto("b", "rechazada", message = "x"),
                SyncResultDto("c", "duplicada", saleId = 3)
            )
        )

        assertEquals(listOf("a", "b", "c"), updates.map { it.offlineReference })
        assertEquals(
            listOf(SaleStatus.SINCRONIZADA, SaleStatus.RECHAZADA, SaleStatus.SINCRONIZADA),
            updates.map { it.status }
        )
    }

    @Test
    fun summaryMessages() {
        assertEquals("3 ventas sincronizadas", SyncSummary(synced = 3).toUserMessage())
        assertEquals("1 venta sincronizada", SyncSummary(synced = 1).toUserMessage())
        assertEquals("Sin conexión con el servidor", SyncSummary(offline = true).toUserMessage())
        assertEquals(
            "1 venta rechazada: Producto inactivo",
            SyncSummary(rejected = 1, firstRejectMessage = "Producto inactivo").toUserMessage()
        )
        assertNull(SyncSummary(nothingToSync = true).toUserMessage())
    }
}
