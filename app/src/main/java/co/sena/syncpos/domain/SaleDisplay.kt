package co.sena.syncpos.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Funciones PURAS (sin Android) con la logica de presentacion de ventas. Al ser puras
// se pueden probar con pruebas unitarias en el computador.

// Texto de la pastilla de origen de una venta.
//  - "offline": la venta se hizo sin internet y se sincronizo despues -> "Sinc. Offline"
//  - cualquier otro valor ("online") -> "Online"
fun originLabel(origin: String): String = if (isOfflineOrigin(origin)) "Sinc. Offline" else "Online"

fun isOfflineOrigin(origin: String): Boolean = origin.equals("offline", ignoreCase = true)

private val spanishLocale = Locale.forLanguageTag("es-CO")

// Convierte la fecha ISO 8601 de la API ("2026-10-20T14:30:00-05:00") a la hora local
// del celular con formato "02:30 p. m.". Si el texto no se puede leer devuelve "--:--"
// en vez de romper la pantalla.
fun formatSaleTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    parseSaleDateTime(iso, zone)?.format(DateTimeFormatter.ofPattern("hh:mm a", spanishLocale)) ?: "--:--"

// Igual pero con fecha y hora: "20/10/2026 02:30 p. m.".
fun formatSaleDateTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    parseSaleDateTime(iso, zone)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", spanishLocale))
        ?: "--"

private fun parseSaleDateTime(iso: String?, zone: ZoneId) = try {
    if (iso.isNullOrBlank()) null else OffsetDateTime.parse(iso).atZoneSameInstant(zone)
} catch (e: Exception) {
    null
}

// Nombre corto del dia de la semana en espanol ("Lun", "Mar"...) para el grafico.
// Se arma a mano para que no dependa del idioma del celular.
fun shortWeekday(isoDate: String): String {
    val date = try {
        LocalDate.parse(isoDate)
    } catch (e: Exception) {
        return "-"
    }
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Lun"
        DayOfWeek.TUESDAY -> "Mar"
        DayOfWeek.WEDNESDAY -> "Mié"
        DayOfWeek.THURSDAY -> "Jue"
        DayOfWeek.FRIDAY -> "Vie"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
    }
}

