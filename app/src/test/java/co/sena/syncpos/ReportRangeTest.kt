package co.sena.syncpos

import co.sena.syncpos.domain.DateRange
import co.sena.syncpos.domain.RangeOption
import co.sena.syncpos.domain.rangeFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

// Pruebas de rangeFor: calcula "desde" y "hasta" de cada opcion del selector de Reportes.
// Referencia del calendario 2026: el 12 de octubre es lunes, el 18 es domingo, el 19 es lunes.
class ReportRangeTest {

    @Test
    fun day_isTodayOnly() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(DateRange(today, today), rangeFor(RangeOption.DAY, today))
    }

    @Test
    fun week_onSunday_startsOnTheMondayBefore() {
        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals(
            DateRange(LocalDate.of(2026, 10, 12), sunday),
            rangeFor(RangeOption.WEEK, sunday)
        )
    }

    @Test
    fun week_onMonday_isJustToday() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(DateRange(monday, monday), rangeFor(RangeOption.WEEK, monday))
    }

    @Test
    fun week_midWeek_startsOnMonday() {
        val tuesday = LocalDate.of(2026, 10, 20)
        assertEquals(
            DateRange(LocalDate.of(2026, 10, 19), tuesday),
            rangeFor(RangeOption.WEEK, tuesday)
        )
    }

    @Test
    fun week_canStartInThePreviousMonth() {
        // Jueves 1 de octubre de 2026: su lunes es el 28 de septiembre.
        val thursday = LocalDate.of(2026, 10, 1)
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 28), thursday),
            rangeFor(RangeOption.WEEK, thursday)
        )
    }

    @Test
    fun month_startsOnTheFirstDay() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(
            DateRange(LocalDate.of(2026, 10, 1), today),
            rangeFor(RangeOption.MONTH, today)
        )
    }

    @Test
    fun custom_usesTheChosenRange() {
        val custom = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15))
        assertEquals(custom, rangeFor(RangeOption.CUSTOM, LocalDate.of(2026, 10, 20), custom))
    }

    @Test
    fun custom_withoutChoice_fallsBackToToday() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(DateRange(today, today), rangeFor(RangeOption.CUSTOM, today))
    }
}
