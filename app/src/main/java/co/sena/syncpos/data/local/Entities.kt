package co.sena.syncpos.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// Una @Entity es una clase que Room convierte en una TABLA de SQLite.
// Cada propiedad es una columna y cada objeto es una fila.

// Producto del catalogo descargado del servidor (tabla "products").
@Entity(tableName = "products", indices = [Index("barcode")])
data class ProductEntity(
    // Usamos el mismo id del servidor: asi al vender enviamos el producto_id correcto.
    @PrimaryKey val id: Long,
    val barcode: String?,
    val name: String,
    val category: String,
    val salePrice: Double,
    val stock: Int,
    val minStock: Int
)

// Estados posibles de una venta local.
enum class SaleStatus {
    PENDIENTE,      // guardada en el celular, aun no llega al servidor
    SINCRONIZADA,   // el servidor la acepto (o ya la tenia)
    RECHAZADA       // el servidor la rechazo; se guarda el motivo en "message"
}

// Venta hecha en el celular (tabla "local_sales").
@Entity(
    tableName = "local_sales",
    // Indice unico: no pueden existir dos ventas con la misma referencia.
    indices = [Index(value = ["offlineReference"], unique = true)]
)
data class LocalSaleEntity(
    // autoGenerate: Room asigna 1, 2, 3... solo. Se muestra como "Venta #L-0001".
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    // UUID generado en el celular. El servidor lo usa para detectar duplicados:
    // si reenviamos la misma venta, responde "duplicada" y no la cuenta dos veces.
    val offlineReference: String,
    val paymentMethod: String,          // "efectivo" o "nequi"
    val soldAt: Long,                   // fecha y hora en milisegundos desde 1970
    val total: Double,                  // solo para mostrar; el servidor recalcula el total real
    val status: SaleStatus,
    val serverId: Long? = null,         // id de la venta en el servidor (cuando ya se sincronizo)
    val message: String? = null         // motivo del rechazo, si lo hubo
)

// Linea de una venta: un producto con su cantidad (tabla "local_sale_items").
@Entity(
    tableName = "local_sale_items",
    // Clave foranea: cada item pertenece a una venta; si se borra la venta se borran sus items.
    foreignKeys = [
        ForeignKey(
            entity = LocalSaleEntity::class,
            parentColumns = ["localId"],
            childColumns = ["saleLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleLocalId")]
)
data class LocalSaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleLocalId: Long,
    val productId: Long,
    val name: String,       // se copia el nombre por si el catalogo cambia despues
    val quantity: Int,
    val unitPrice: Double
)

// No es una tabla: es el resultado de unir una venta con sus items.
// @Embedded incluye las columnas de la venta; @Relation busca los items relacionados.
data class SaleWithItems(
    @Embedded val sale: LocalSaleEntity,
    @Relation(parentColumn = "localId", entityColumn = "saleLocalId")
    val items: List<LocalSaleItemEntity>
)
