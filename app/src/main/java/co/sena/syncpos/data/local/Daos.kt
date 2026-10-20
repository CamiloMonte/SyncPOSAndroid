package co.sena.syncpos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Un @Dao (Data Access Object) es una interfaz con las consultas a la base de datos.
// Room escribe el codigo real durante la compilacion.
//
// Dos tipos de funciones:
//  - "suspend fun": se ejecuta una vez en una corrutina (sin bloquear la pantalla).
//  - funciones que devuelven Flow: emiten un valor NUEVO cada vez que la tabla cambia,
//    asi la pantalla se actualiza sola.

@Dao
interface ProductDao {

    // Busca por nombre o por codigo de barras (LIKE con % = "contiene").
    @Query(
        """
        SELECT * FROM products
        WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT 30
        """
    )
    fun search(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    // REPLACE: si el producto ya existe (mismo id) se reemplaza por el nuevo.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    // Reemplaza todo el catalogo de golpe. @Transaction = todo o nada: si algo falla
    // a la mitad, no queda el catalogo vacio o incompleto.
    @Transaction
    suspend fun replaceAll(products: List<ProductEntity>) {
        deleteAll()
        insertAll(products)
    }

    @Query("UPDATE products SET stock = stock - :quantity WHERE id = :productId")
    suspend fun decrementStock(productId: Long, quantity: Int)
}

@Dao
interface SaleDao {

    @Insert
    suspend fun insertSale(sale: LocalSaleEntity): Long   // devuelve el localId generado

    @Insert
    suspend fun insertItems(items: List<LocalSaleItemEntity>)

    // Ventas de un rango de tiempo (el dia de hoy), las mas nuevas primero.
    // @Transaction es necesario porque Room hace dos consultas (ventas e items).
    @Transaction
    @Query("SELECT * FROM local_sales WHERE soldAt >= :from AND soldAt < :until ORDER BY soldAt DESC")
    fun observeBetween(from: Long, until: Long): Flow<List<SaleWithItems>>

    // Cantidad de ventas pendientes; se muestra en la pastilla "Offline (N pend.)".
    @Query("SELECT COUNT(*) FROM local_sales WHERE status = 'PENDIENTE'")
    fun observePendingCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM local_sales WHERE status = 'PENDIENTE' ORDER BY soldAt ASC")
    suspend fun getPending(): List<SaleWithItems>

    @Query("SELECT status FROM local_sales WHERE offlineReference = :reference")
    suspend fun getStatus(reference: String): SaleStatus?

    @Query(
        """
        UPDATE local_sales
        SET status = :status, serverId = :serverId, message = :message
        WHERE offlineReference = :reference
        """
    )
    suspend fun updateStatus(reference: String, status: SaleStatus, serverId: Long?, message: String?)
}
