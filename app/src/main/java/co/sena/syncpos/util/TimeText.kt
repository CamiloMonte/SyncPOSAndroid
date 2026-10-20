package co.sena.syncpos.util

// Texto relativo para "Ultima sincronizacion: hace X min".
// Es una funcion pura (recibe "now" como parametro) para poder probarla facilmente.
fun lastSyncText(lastSyncAt: Long, now: Long): String {
    if (lastSyncAt <= 0L) return "Nunca"
    val minutes = ((now - lastSyncAt) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "hace un momento"
        minutes < 60 -> "hace $minutes min"
        minutes < 60 * 24 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (60 * 24)} d"
    }
}
