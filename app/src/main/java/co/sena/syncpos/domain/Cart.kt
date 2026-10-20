package co.sena.syncpos.domain

import co.sena.syncpos.data.local.ProductEntity

// Una linea del carrito.
data class CartItem(
    val productId: Long,
    val name: String,
    val unitPrice: Double,
    val quantity: Int
) {
    val subtotal: Double get() = unitPrice * quantity
}

// Carrito de compras como clase Kotlin normal (sin nada de Android ni de Compose),
// asi se puede probar con pruebas unitarias rapidas.
//
// Es INMUTABLE: cada operacion devuelve un carrito NUEVO en lugar de modificar el actual.
// Esto es importante porque Compose detecta cambios comparando objetos: si siempre se
// crea uno nuevo, la pantalla se actualiza correctamente.
//
// Nota: NO se valida el stock. La venta ya ocurrio fisicamente en el mostrador
// (el cliente tiene el producto), asi que el app no puede negarla. Si el inventario
// queda en negativo, el servidor lo marca (stock_negativo) para que se revise.
data class Cart(val items: List<CartItem> = emptyList()) {

    // Agrega un producto; si ya estaba en el carrito, suma 1 a su cantidad.
    fun add(product: ProductEntity): Cart {
        val existing = items.find { it.productId == product.id }
        return if (existing == null) {
            Cart(items + CartItem(product.id, product.name, product.salePrice, 1))
        } else {
            changeQuantity(product.id, existing.quantity + 1)
        }
    }

    fun increment(productId: Long): Cart {
        val current = items.find { it.productId == productId } ?: return this
        return changeQuantity(productId, current.quantity + 1)
    }

    // El minimo es 1: para quitar el producto se usa remove() (boton de basura).
    fun decrement(productId: Long): Cart {
        val current = items.find { it.productId == productId } ?: return this
        return if (current.quantity <= 1) this else changeQuantity(productId, current.quantity - 1)
    }

    fun remove(productId: Long): Cart = Cart(items.filterNot { it.productId == productId })

    fun clear(): Cart = Cart()

    private fun changeQuantity(productId: Long, quantity: Int): Cart =
        Cart(items.map { if (it.productId == productId) it.copy(quantity = quantity) else it })

    val isEmpty: Boolean get() = items.isEmpty()

    // Cantidad de productos distintos ("3 productos").
    val productCount: Int get() = items.size

    // Suma de todas las cantidades ("7 unidades").
    val unitCount: Int get() = items.sumOf { it.quantity }

    val total: Double get() = items.sumOf { it.subtotal }
}
