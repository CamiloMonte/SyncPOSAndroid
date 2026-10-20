package co.sena.syncpos

import co.sena.syncpos.util.formatCop
import co.sena.syncpos.util.lastSyncText
import org.junit.Assert.assertEquals
import org.junit.Test

// Pruebas unitarias (JVM): corren en el computador, sin emulador, y son muy rapidas.
// Cada funcion con @Test es una prueba. assertEquals(esperado, obtenido) falla si difieren.
class MoneyTest {

    @Test
    fun formatCop_zero() {
        assertEquals("$ 0", formatCop(0.0))
    }

    @Test
    fun formatCop_belowThousand_hasNoSeparator() {
        assertEquals("$ 900", formatCop(900.0))
    }

    @Test
    fun formatCop_thousands_usesDot() {
        assertEquals("$ 9.800", formatCop(9800.0))
        assertEquals("$ 46.200", formatCop(46200.0))
    }

    @Test
    fun formatCop_millions_usesTwoDots() {
        assertEquals("$ 2.450.000", formatCop(2450000.0))
    }

    @Test
    fun formatCop_roundsDecimals() {
        assertEquals("$ 13.500", formatCop(13499.6))
    }

    @Test
    fun lastSyncText_cases() {
        val now = 10_000_000L
        assertEquals("Nunca", lastSyncText(0L, now))
        assertEquals("hace un momento", lastSyncText(now - 30_000, now))
        assertEquals("hace 5 min", lastSyncText(now - 5 * 60_000L, now))
        assertEquals("hace 2 h", lastSyncText(now - 125 * 60_000L, now))
    }
}
