package co.sena.syncpos.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Opciones del selector de periodo de la pestana Reportes.
enum class RangeOption(val label: String) {
    DAY("Día"),
    WEEK("Semana"),
    MONTH("Mes"),
    CUSTOM("Rango")
}

// Periodo de fechas, ambos extremos incluidos.
data class DateRange(val from: LocalDate, val to: LocalDate)

// Calcula el periodo a consultar. Es una funcion PURA: "today" llega como parametro (en
// vez de leer la fecha del sistema adentro) para poder probarla con cualquier dia.
//   Dia    = hoy..hoy
//   Semana = lunes de esta semana..hoy
//   Mes    = dia 1 del mes..hoy
//   Rango  = el periodo elegido por el usuario (si aun no eligio, hoy..hoy)
fun rangeFor(option: RangeOption, today: LocalDate, custom: DateRange? = null): DateRange =
    when (option) {
        RangeOption.DAY -> DateRange(today, today)
        // previousOrSame(LUNES): si hoy ya es lunes se queda hoy; si no, retrocede hasta el lunes.
        RangeOption.WEEK -> DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
        RangeOption.MONTH -> DateRange(today.withDayOfMonth(1), today)
        RangeOption.CUSTOM -> custom ?: DateRange(today, today)
    }
