package co.sena.syncpos

import co.sena.syncpos.data.local.ProductEntity
import co.sena.syncpos.domain.Cart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pruebas de la logica del carrito (clase Kotlin pura, sin Android).
class CartTest {

    private val milk = ProductEntity(1, "7701", "Leche", "Lacteos", 4000.0, 10, 2)
    private val bread = ProductEntity(2, "7702", "Pan", "Panaderia", 2500.0, 10, 2)

    @Test
    fun addSameProductTwice_increasesQuantity() {
        val cart = Cart().add(milk).add(milk)

        assertEquals(1, cart.productCount)
        assertEquals(2, cart.items.first().quantity)
        assertEquals(8000.0, cart.total, 0.001)
    }

    @Test
    fun addProductsWithoutStockLimit() {
        // El stock de "milk" es 10, pero el carrito permite 15: la venta ya ocurrio
        // fisicamente y el servidor se encarga de marcar el stock negativo.
        var cart = Cart()
        repeat(15) { cart = cart.add(milk) }

        assertEquals(15, cart.unitCount)
    }

    @Test
    fun totalsAndUnits() {
        val cart = Cart().add(milk).add(milk).add(bread)

        assertEquals(2, cart.productCount)
        assertEquals(3, cart.unitCount)
        assertEquals(10500.0, cart.total, 0.001)
    }

    @Test
    fun incrementAndDecrement_neverBelowOne() {
        var cart = Cart().add(milk)
        cart = cart.increment(1).increment(1)
        assertEquals(3, cart.items.first().quantity)

        cart = cart.decrement(1).decrement(1).decrement(1)
        assertEquals(1, cart.items.first().quantity)
    }

    @Test
    fun remove_deletesOnlyThatProduct() {
        val cart = Cart().add(milk).add(bread).remove(1)

        assertEquals(1, cart.productCount)
        assertEquals(2L, cart.items.first().productId)
    }

    @Test
    fun clear_emptiesCart() {
        val cart = Cart().add(milk).add(bread).clear()

        assertTrue(cart.isEmpty)
        assertEquals(0.0, cart.total, 0.001)
    }

    @Test
    fun cartIsImmutable_addReturnsNewInstance() {
        val original = Cart()
        val changed = original.add(milk)

        assertTrue(original.isEmpty)
        assertEquals(1, changed.productCount)
    }
}
