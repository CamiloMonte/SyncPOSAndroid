package co.sena.syncpos.domain

import co.sena.syncpos.data.local.SaleStatus
import co.sena.syncpos.data.remote.SyncResultDto

// Cambio que se debe aplicar a una venta local segun la respuesta del servidor.
data class SaleStatusUpdate(
    val offlineReference: String,
    val status: SaleStatus,
    val serverId: Long?,
    val message: String?
)

// Funcion PURA: solo transforma datos de entrada en datos de salida, sin tocar la base de
// datos ni la red. Por eso es facil de probar con una prueba unitaria.
//
//  - "sincronizada" y "duplicada" son EXITO. Duplicada significa que el servidor ya tenia
//    esa venta (por ejemplo, la respuesta anterior se perdio): no hay que reenviarla.
//  - "rechazada" se marca RECHAZADA y se conserva el mensaje.
//  - Un estado desconocido se ignora: la venta sigue PENDIENTE y se reintenta despues.
fun mapSyncResults(results: List<SyncResultDto>): List<SaleStatusUpdate> =
    results.mapNotNull { result ->
        when (result.estado) {
            "sincronizada", "duplicada" -> SaleStatusUpdate(
                offlineReference = result.offlineReference,
                status = SaleStatus.SINCRONIZADA,
                serverId = result.saleId,
                message = null
            )
            "rechazada" -> SaleStatusUpdate(
                offlineReference = result.offlineReference,
                status = SaleStatus.RECHAZADA,
                serverId = null,
                message = result.message ?: "Venta rechazada por el servidor"
            )
            else -> null
        }
    }

// Resumen de un intento de sincronizacion, para mostrarlo al usuario.
data class SyncSummary(
    val synced: Int = 0,
    val rejected: Int = 0,
    val firstRejectMessage: String? = null,
    val offline: Boolean = false,       // no se pudo llegar al servidor
    val errorMessage: String? = null,   // otro error (por ejemplo sesion vencida)
    val nothingToSync: Boolean = false  // no habia ventas pendientes
) {
    // Texto para el Snackbar. Devuelve null si no hay nada que decir.
    fun toUserMessage(): String? = when {
        nothingToSync -> null
        errorMessage != null && synced == 0 && rejected == 0 -> errorMessage
        offline && synced == 0 && rejected == 0 -> "Sin conexión con el servidor"
        rejected > 0 -> {
            val rejectedText = if (rejected == 1) "1 venta rechazada" else "$rejected ventas rechazadas"
            val detail = firstRejectMessage?.let { ": $it" } ?: ""
            if (synced > 0) "${syncedText()} · $rejectedText$detail" else "$rejectedText$detail"
        }
        synced > 0 -> syncedText()
        else -> null
    }

    private fun syncedText(): String =
        if (synced == 1) "1 venta sincronizada" else "$synced ventas sincronizadas"
}
