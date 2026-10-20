package co.sena.syncpos.data.repository

import androidx.room.withTransaction
import co.sena.syncpos.data.local.AppDatabase
import co.sena.syncpos.data.local.LocalSaleEntity
import co.sena.syncpos.data.local.LocalSaleItemEntity
import co.sena.syncpos.data.local.SaleDao
import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.local.SaleWithItems
import co.sena.syncpos.data.remote.SyncItemDto
import co.sena.syncpos.data.remote.SyncPosApi
import co.sena.syncpos.data.remote.SyncRequest
import co.sena.syncpos.data.remote.SyncSaleDto
import co.sena.syncpos.data.session.SessionManager
import co.sena.syncpos.domain.Cart
import co.sena.syncpos.domain.PaymentMethod
import co.sena.syncpos.domain.SyncSummary
import co.sena.syncpos.domain.mapSyncResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class SalesRepository(
    private val db: AppDatabase,
    private val api: SyncPosApi,
    private val session: SessionManager
) {
    private val saleDao: SaleDao = db.saleDao()
    private val productDao = db.productDao()

    // Mutex = "candado": evita que dos sincronizaciones corran al mismo tiempo
    // (por ejemplo la automatica al reconectar y la de una venta nueva).
    private val syncMutex = Mutex()

    // Maximo de ventas por peticion que acepta la API.
    private val chunkSize = 100

    val pendingCount: Flow<Int> = saleDao.observePendingCount()

    fun observeSalesBetween(from: Long, until: Long): Flow<List<SaleWithItems>> =
        saleDao.observeBetween(from, until)

    // Registra una venta. Estrategia "primero guardar, despues sincronizar":
    //  1. Se guarda SIEMPRE en el celular (Room) como PENDIENTE. Asi la venta nunca se
    //     pierde aunque no haya internet o la app se cierre.
    //  2. Luego se intenta enviar al servidor. Si falla, queda pendiente y se reintenta.
    // Devuelve el estado final de esa venta (SINCRONIZADA o PENDIENTE...).
    suspend fun registerSale(cart: Cart, paymentMethod: PaymentMethod): SaleStatus {
        // UUID = identificador unico universal. Se genera en el celular, sin consultar al
        // servidor, y casi nunca se repite. El servidor lo usa para no duplicar la venta
        // si el mismo envio llega dos veces (por ejemplo, se cayo la red tras enviar).
        val reference = UUID.randomUUID().toString()

        // withTransaction: o se guarda todo (venta + items + stock) o no se guarda nada.
        db.withTransaction {
            val localId = saleDao.insertSale(
                LocalSaleEntity(
                    offlineReference = reference,
                    paymentMethod = paymentMethod.apiValue,
                    soldAt = System.currentTimeMillis(),
                    total = cart.total, // solo informativo: el servidor recalcula con sus precios
                    status = SaleStatus.PENDIENTE
                )
            )
            saleDao.insertItems(
                cart.items.map {
                    LocalSaleItemEntity(
                        saleLocalId = localId,
                        productId = it.productId,
                        name = it.name,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice
                    )
                }
            )
            // Descontar stock local es solo un ESTIMADO para que la pantalla se vea
            // coherente sin internet. La fuente de verdad es el servidor; el catalogo se
            // vuelve a descargar en el siguiente inicio de sesion.
            cart.items.forEach { productDao.decrementStock(it.productId, it.quantity) }
        }

        syncPending()
        return saleDao.getStatus(reference) ?: SaleStatus.PENDIENTE
    }

    // Envia TODAS las ventas pendientes al servidor y actualiza sus estados.
    suspend fun syncPending(): SyncSummary = syncMutex.withLock {
        val pending = saleDao.getPending()
        if (pending.isEmpty()) return@withLock SyncSummary(nothingToSync = true)

        var synced = 0
        var rejected = 0
        var firstRejectMessage: String? = null

        try {
            for (chunk in pending.chunked(chunkSize)) {
                val response = api.syncSales(SyncRequest(chunk.map { it.toDto() }))

                // Funcion pura (probada en tests) que traduce la respuesta a estados locales.
                for (update in mapSyncResults(response.resultados)) {
                    saleDao.updateStatus(update.offlineReference, update.status, update.serverId, update.message)
                    if (update.status == SaleStatus.SINCRONIZADA) {
                        synced++
                    } else {
                        rejected++
                        if (firstRejectMessage == null) firstRejectMessage = update.message
                    }
                }
                session.lastSyncAt = System.currentTimeMillis()
            }
            SyncSummary(synced = synced, rejected = rejected, firstRejectMessage = firstRejectMessage)
        } catch (e: IOException) {
            // Sin red o servidor caido: las ventas siguen PENDIENTES y se reintentan luego.
            SyncSummary(synced, rejected, firstRejectMessage, offline = true)
        } catch (e: HttpException) {
            val text = if (e.code() == 401) {
                "Sesión vencida. Inicia sesión de nuevo para sincronizar."
            } else {
                "Error del servidor (código ${e.code()}). Se reintentará más tarde."
            }
            SyncSummary(synced, rejected, firstRejectMessage, errorMessage = text)
        }
    }

    // Convierte una venta local al formato JSON que espera la API.
    // Solo se envia producto_id y cantidad; los precios los pone el servidor.
    private fun SaleWithItems.toDto(): SyncSaleDto {
        // Fecha ISO-8601 con zona horaria, ej: 2026-10-20T10:42:00-05:00
        val soldAtText = OffsetDateTime
            .ofInstant(Instant.ofEpochMilli(sale.soldAt), ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return SyncSaleDto(
            offlineReference = sale.offlineReference,
            paymentMethod = sale.paymentMethod,
            soldAt = soldAtText,
            items = items.map { SyncItemDto(it.productId, it.quantity) }
        )
    }
}
