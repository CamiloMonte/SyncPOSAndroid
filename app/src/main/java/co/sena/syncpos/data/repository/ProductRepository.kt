package co.sena.syncpos.data.repository

import co.sena.syncpos.data.local.ProductDao
import co.sena.syncpos.data.local.ProductEntity
import co.sena.syncpos.data.remote.ProductDto
import co.sena.syncpos.data.remote.SyncPosApi
import co.sena.syncpos.data.remote.apiMessage
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException

// Resultado de descargar el catalogo.
sealed interface CatalogResult {
    data class Success(val total: Int) : CatalogResult
    data class Failure(val message: String) : CatalogResult
}

class ProductRepository(
    private val api: SyncPosApi,
    private val productDao: ProductDao
) {

    // Busqueda local en Room (funciona sin internet).
    fun search(query: String): Flow<List<ProductEntity>> = productDao.search(query.trim())

    suspend fun findByBarcode(barcode: String): ProductEntity? = productDao.findByBarcode(barcode.trim())

    suspend fun hasProducts(): Boolean = productDao.count() > 0

    // Descarga TODAS las paginas de productos activos y las guarda en Room.
    // La API pagina los resultados: se pide la pagina 1, 2, 3... hasta llegar a la ultima.
    suspend fun downloadCatalog(): CatalogResult {
        return try {
            val all = mutableListOf<ProductEntity>()
            var page = 1
            do {
                val response = api.products(onlyActive = 1, page = page)
                all += response.data.map { it.toEntity() }
                val last = response.meta.lastPage
                page++
            } while (page <= last)

            // Hasta aqui todo salio bien: ahora si se reemplaza el catalogo local.
            productDao.replaceAll(all)
            CatalogResult.Success(all.size)
        } catch (e: HttpException) {
            CatalogResult.Failure(e.apiMessage("No se pudo descargar el catálogo (código ${e.code()})."))
        } catch (e: IOException) {
            CatalogResult.Failure("Sin conexión con el servidor. No se pudo descargar el catálogo.")
        }
    }

    // Convierte el DTO de la red en la entidad de la base de datos.
    private fun ProductDto.toEntity() = ProductEntity(
        id = id,
        barcode = barcode,
        name = name,
        category = category?.nombre ?: "Sin categoría",
        salePrice = salePrice.toDoubleOrNull() ?: 0.0, // "13500.00" -> 13500.0
        stock = stock,
        minStock = minStock
    )
}
