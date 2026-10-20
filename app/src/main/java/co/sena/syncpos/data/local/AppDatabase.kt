package co.sena.syncpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// La base de datos Room. Lista las tablas (entities) y la version del esquema.
// Si algun dia se cambian las tablas, hay que subir "version" y escribir una migracion.
@Database(
    entities = [ProductEntity::class, LocalSaleEntity::class, LocalSaleItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao

    companion object {
        // Crea (o abre) el archivo de base de datos "syncpos.db" en el celular.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "syncpos.db")
                .build()
    }
}
