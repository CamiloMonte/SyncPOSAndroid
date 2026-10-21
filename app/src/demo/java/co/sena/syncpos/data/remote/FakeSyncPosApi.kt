package co.sena.syncpos.data.remote

import kotlinx.coroutines.delay
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// =====================================================================================
// API FALSA para la version de demostracion.
//
// Idea de fondo ("inversion de dependencias", explicada simple):
//   El resto de la app (repositorios, ViewModels, pantallas) NO sabe si habla con un
//   servidor real o con otra cosa. Solo conoce la interfaz SyncPosApi, que es una lista
//   de "pedidos posibles" (login, productos, ventas...). Esta clase cumple esa MISMA
//   interfaz, pero en vez de hacer peticiones HTTP responde con datos que viven en la
//   memoria del celular. Como la forma es identica, nada mas del codigo cambia.
//
// Este archivo esta en src/demo: solo existe en el flavor "demo". El flavor "full" ni
// siquiera lo compila.
//
// Los datos viven SOLO en memoria: mientras el proceso de la app este vivo se conservan
// (se puede crear una venta y verla en el Historial), pero si Android cierra la app o
// se desinstala, vuelven a los datos de ejemplo. Room (la base local del cajero) si
// guarda en disco como siempre.
//
// Los calculos (totales, ganancia, rentabilidad, paginacion, filtros) se hacen igual
// que los hace la API Laravel, para que las pantallas muestren numeros coherentes.
//
// Parametros (todos tienen valor por defecto salvo isOnline, para poder probarla en
// una prueba unitaria sin Android):
//   isOnline    -> funcion que dice si hay red; si no hay, se lanza IOException.
//   clock       -> el "reloj"; en las pruebas se fija para que el resultado no cambie.
//   delayMillis -> retraso artificial para que se vean los indicadores de carga.
// =====================================================================================
class FakeSyncPosApi(
    private val isOnline: () -> Boolean,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val delayMillis: Long = 300L
) : SyncPosApi {

    // ---------- Modelos internos (lo que "guardaria la base de datos del servidor") ----------

    private class DemoProduct(
        val id: Long,
        var barcode: String,
        var name: String,
        var categoryId: Long,
        var costPrice: Double,
        var salePrice: Double,
        var stock: Int,
        var minStock: Int,
        var active: Boolean = true
    ) {
        // Mismo criterio que la API: stock bajo = stock menor o igual al minimo.
        val isLow: Boolean get() = stock <= minStock
    }

    private class DemoUser(val id: Long, val name: String, val email: String, val role: String)

    // El costo unitario se guarda en cada linea ("snapshot"): si luego el producto
    // cambia de precio de compra, las ventas viejas conservan su ganancia original.
    private class DemoItem(
        val productId: Long,
        val name: String,
        val quantity: Int,
        val unitPrice: Double,
        val unitCost: Double
    ) {
        val subtotal: Double get() = unitPrice * quantity
        val cost: Double get() = unitCost * quantity
    }

    private class DemoSale(
        val id: Long,
        val reference: String,
        val cashier: DemoUser,
        val method: String,
        val origin: String,
        val soldAt: OffsetDateTime,
        val syncedAt: OffsetDateTime,
        val items: List<DemoItem>
    ) {
        val total: Double get() = items.sumOf { it.subtotal }
        val profit: Double get() = items.sumOf { it.subtotal - it.cost }
    }

    // ---------- Datos en memoria ----------

    private val lock = Any() // las pantallas llaman desde varias corrutinas: se protege el estado

    private val categories = listOf(
        CategoryDto(1, "Bebidas"), CategoryDto(2, "Granos"), CategoryDto(3, "Aceites"),
        CategoryDto(4, "Lácteos"), CategoryDto(5, "Snacks"), CategoryDto(6, "Aseo"),
        CategoryDto(7, "Frescos"), CategoryDto(8, "Enlatados")
    )

    private val admin = DemoUser(1, "Andrés Díaz", "admin@syncpos.co", "admin")
    private val laura = DemoUser(2, "Laura Gómez", "cajero@syncpos.co", "cajero")
    private val carlos = DemoUser(3, "Carlos Pérez", "carlos@syncpos.co", "cajero")
    private val users = listOf(admin, laura, carlos)

    // La API real sabe quien es el usuario por el token. Aqui se recuerda al ultimo que
    // inicio sesion para asignarle las ventas nuevas.
    private var currentUser: DemoUser = laura

    private val products = mutableListOf<DemoProduct>()
    private val sales = mutableListOf<DemoSale>()
    private var nextProductId = 1L
    private var nextSaleId = 1L

    init {
        seedProducts()
        seedSales()
    }

    // =================================================================================
    // Endpoints de la interfaz SyncPosApi
    // =================================================================================

    // Login y logout NO exigen red: asi la demo se puede abrir y mostrar aunque el
    // celular no tenga Wi-Fi. El resto de endpoints si simula la falta de conexion.
    override suspend fun login(body: LoginRequest): LoginResponse = respond(needsNetwork = false) {
        val user = users.firstOrNull { it.email.equals(body.email.trim(), ignoreCase = true) }
            ?: throw validationError("Credenciales incorrectas.", emptyMap())
        currentUser = user
        // Cualquier contrasena sirve: es una demostracion.
        LoginResponse("demo-token-${user.id}", UserDto(user.id, user.name, user.email, user.role))
    }

    override suspend fun logout(): Response<Unit> = respond(needsNetwork = false) {
        Response.success(Unit)
    }

    // Catalogo del cajero. Tampoco exige red (se descarga justo despues del login).
    override suspend fun products(onlyActive: Int, page: Int): ProductsResponse =
        respond(needsNetwork = false) {
            val list = products.filter { onlyActive != 1 || it.active }
            val (pageItems, meta) = paginate(sortedByName(list), page, PRODUCTS_PER_PAGE)
            ProductsResponse(pageItems.map { it.toCatalogDto() }, meta)
        }

    override suspend fun syncSales(body: SyncRequest): SyncResponse = respond {
        SyncResponse(body.ventas.map { registerSale(it) })
    }

    override suspend fun daySummary(): DaySummaryDto = respond {
        val today = LocalDate.now(clock)
        val todaySales = sales.filter { it.date() == today }
        val active = products.filter { it.active }
        DaySummaryDto(
            salesTotal = todaySales.sumOf { it.total },
            orders = todaySales.size,
            netProfit = todaySales.sumOf { it.profit },
            inventoryInvestment = active.sumOf { it.costPrice * it.stock.coerceAtLeast(0) },
            stockAlerts = active.count { it.isLow },
            latestSales = newestFirst(sales).take(5).map { it.toDto(withItems = false) },
            last7Days = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                val daySales = sales.filter { it.date() == day }
                DayPointDto(day.toString(), daySales.sumOf { it.total }, daySales.sumOf { it.profit })
            }
        )
    }

    override suspend fun adminProducts(
        search: String?,
        categoryId: Long?,
        lowStock: Int?,
        page: Int
    ): AdminProductsResponse = respond {
        filteredProducts(search, categoryId, lowStock == 1, onlyActive = false, page = page)
    }

    override suspend fun adminProduct(id: Long): ProductEnvelope = respond {
        ProductEnvelope(findProduct(id).toAdminDto())
    }

    override suspend fun categories(): CategoriesResponse = respond { CategoriesResponse(categories) }

    override suspend fun createProduct(body: ProductRequest): ProductEnvelope = respond {
        validate(body, editing = null)
        val product = DemoProduct(
            id = nextProductId++,
            barcode = body.barcode.trim(),
            name = body.name.trim(),
            categoryId = body.categoryId,
            costPrice = body.costPrice,
            salePrice = body.salePrice,
            stock = body.stock,
            minStock = body.minStock
        )
        products += product
        ProductEnvelope(product.toAdminDto())
    }

    override suspend fun updateProduct(id: Long, body: ProductRequest): ProductEnvelope = respond {
        val product = findProduct(id)
        validate(body, editing = product)
        product.barcode = body.barcode.trim()
        product.name = body.name.trim()
        product.categoryId = body.categoryId
        product.costPrice = body.costPrice
        product.salePrice = body.salePrice
        product.stock = body.stock
        product.minStock = body.minStock
        ProductEnvelope(product.toAdminDto())
    }

    // Igual que el servidor: invierte el valor actual de "activo".
    override suspend fun toggleProduct(id: Long): ProductEnvelope = respond {
        val product = findProduct(id)
        product.active = !product.active
        ProductEnvelope(product.toAdminDto())
    }

    override suspend fun profitability(from: String, to: String): ProfitabilityDto = respond {
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        val inRange = sales.filter { it.date() in start..end }

        // Se agrupan TODAS las lineas de las ventas del periodo por producto.
        val lines = inRange.flatMap { it.items }.groupBy { it.productId }
        val revenue = inRange.sumOf { it.total }
        val cost = inRange.sumOf { sale -> sale.items.sumOf { it.cost } }
        val profit = revenue - cost

        val rows = lines.map { (productId, items) ->
            val itemRevenue = items.sumOf { it.subtotal }
            val itemCost = items.sumOf { it.cost }
            val itemProfit = itemRevenue - itemCost
            ProfitProductDto(
                productId = productId,
                name = items.first().name,
                units = items.sumOf { it.quantity },
                revenue = itemRevenue,
                cost = itemCost,
                profit = itemProfit,
                margin = percentage(itemProfit, itemRevenue),
                contribution = percentage(itemProfit, profit)
            )
        }.sortedByDescending { it.profit } // el ranking va de mas a menos ganancia

        ProfitabilityDto(
            period = PeriodDto(from, to),
            totals = ProfitTotalsDto(revenue, cost, profit, percentage(profit, revenue)),
            products = rows
        )
    }

    override suspend fun sales(search: String?, paymentMethod: String?, page: Int): SalesResponse =
        filteredSales(search, paymentMethod, origin = null, from = null, to = null, page = page)

    override suspend fun sale(id: Long): SaleEnvelope = respond {
        val sale = this@FakeSyncPosApi.sales.firstOrNull { it.id == id }
            ?: throw httpError(404, """{"message":"Venta no encontrada."}""")
        SaleEnvelope(sale.toDto(withItems = true))
    }

    // =================================================================================
    // Filtros completos (la interfaz solo expone algunos; la API Laravel acepta mas)
    // =================================================================================

    // buscar: texto en el numero de factura o el nombre del cajero.
    // desde / hasta: fechas incluidas ("2026-10-20"). origen: "online" | "offline".
    suspend fun filteredSales(
        search: String?,
        paymentMethod: String?,
        origin: String?,
        from: String?,
        to: String?,
        page: Int
    ): SalesResponse = respond {
        val query = search?.let(::normalize)?.takeIf { it.isNotBlank() }
        val start = from?.let(LocalDate::parse)
        val end = to?.let(LocalDate::parse)
        val list = newestFirst(sales).filter { sale ->
            (query == null ||
                normalize(sale.numberText()).contains(query) ||
                normalize(sale.cashier.name).contains(query)) &&
                (paymentMethod == null || sale.method == paymentMethod) &&
                (origin == null || sale.origin == origin) &&
                (start == null || sale.date() >= start) &&
                (end == null || sale.date() <= end)
        }
        val (pageItems, meta) = paginate(list, page, SALES_PER_PAGE)
        SalesResponse(pageItems.map { it.toDto(withItems = false) }, meta)
    }

    // buscar: nombre o codigo de barras. soloActivos: oculta los productos desactivados.
    suspend fun filteredAdminProducts(
        search: String?,
        categoryId: Long?,
        lowStock: Boolean,
        onlyActive: Boolean,
        page: Int
    ): AdminProductsResponse = respond {
        filteredProducts(search, categoryId, lowStock, onlyActive, page)
    }

    private fun filteredProducts(
        search: String?,
        categoryId: Long?,
        lowStock: Boolean,
        onlyActive: Boolean,
        page: Int
    ): AdminProductsResponse {
        val query = search?.let(::normalize)?.takeIf { it.isNotBlank() }
        val list = products.filter { p ->
            (query == null || normalize(p.name).contains(query) || p.barcode.contains(query)) &&
                (categoryId == null || p.categoryId == categoryId) &&
                (!lowStock || p.isLow) &&
                (!onlyActive || p.active)
        }
        val (pageItems, meta) = paginate(sortedByName(list), page, PRODUCTS_PER_PAGE)
        return AdminProductsResponse(pageItems.map { it.toAdminDto() }, meta)
    }

    // =================================================================================
    // Registro de ventas (sincronizacion)
    // =================================================================================

    private fun registerSale(dto: SyncSaleDto): SyncResultDto {
        // Idempotencia: si esa referencia ya existe, NO se crea otra venta ni se vuelve
        // a descontar stock. Se responde "duplicada" con el id de la venta original.
        sales.firstOrNull { it.reference == dto.offlineReference }?.let {
            return SyncResultDto(dto.offlineReference, "duplicada", it.id)
        }

        if (dto.items.isEmpty()) {
            return SyncResultDto(dto.offlineReference, "rechazada", message = "La venta no tiene productos.")
        }
        val missing = dto.items.firstOrNull { item -> products.none { it.id == item.productId } }
        if (missing != null) {
            return SyncResultDto(
                dto.offlineReference, "rechazada",
                message = "El producto ${missing.productId} ya no existe."
            )
        }

        val now = OffsetDateTime.now(clock)
        // El servidor NO confia en los precios del celular: usa los del catalogo.
        val items = dto.items.map { item ->
            val p = products.first { it.id == item.productId }
            DemoItem(p.id, p.name, item.quantity, p.salePrice, p.costPrice)
        }
        var negativeStock = false
        dto.items.forEach { item ->
            val p = products.first { it.id == item.productId }
            p.stock -= item.quantity
            if (p.stock < 0) negativeStock = true
        }
        val sale = DemoSale(
            id = nextSaleId++,
            reference = dto.offlineReference,
            cashier = currentUser,
            method = dto.paymentMethod,
            origin = dto.origin,
            soldAt = runCatching { OffsetDateTime.parse(dto.soldAt) }.getOrDefault(now),
            syncedAt = now,
            items = items
        )
        sales += sale
        return SyncResultDto(
            dto.offlineReference, "sincronizada", sale.id,
            negativeStock = if (negativeStock) true else null
        )
    }

    // =================================================================================
    // Validacion de productos (respuesta 422 como Laravel)
    // =================================================================================

    private fun validate(body: ProductRequest, editing: DemoProduct?) {
        val errors = linkedMapOf<String, String>()
        if (body.name.isBlank()) errors["nombre"] = "El nombre es obligatorio."
        if (body.barcode.isBlank()) {
            errors["codigo_barras"] = "El código de barras es obligatorio."
        } else if (products.any { it.barcode == body.barcode.trim() && it !== editing }) {
            errors["codigo_barras"] = "El código de barras ya está registrado."
        }
        if (categories.none { it.id == body.categoryId }) errors["categoria_id"] = "La categoría no existe."
        if (body.costPrice < 0) errors["precio_compra"] = "El precio de compra no puede ser negativo."
        if (body.salePrice < body.costPrice) {
            errors["precio_venta"] = "El precio de venta no puede ser menor al precio de compra."
        }
        if (body.stock < 0) errors["stock"] = "El stock no puede ser negativo."
        if (body.minStock < 0) errors["stock_minimo"] = "El stock mínimo no puede ser negativo."
        if (errors.isNotEmpty()) throw validationError(errors.values.first(), errors)
    }

    // =================================================================================
    // Utilidades
    // =================================================================================

    // Todas las respuestas pasan por aqui:
    //  1) si se pide red y no hay, lanza IOException (igual que una llamada real sin internet);
    //  2) espera un momento para que se vean los indicadores de "cargando";
    //  3) ejecuta la logica protegida por el candado.
    private suspend fun <T> respond(needsNetwork: Boolean = true, block: () -> T): T {
        if (needsNetwork && !isOnline()) throw IOException("Sin conexion (demo)")
        delay(delayMillis)
        return synchronized(lock) { block() }
    }

    // Construye una excepcion HTTP como la que lanzaria Retrofit con un error del servidor.
    private fun httpError(code: Int, json: String): HttpException =
        HttpException(Response.error<Any>(code, json.toResponseBody("application/json".toMediaType())))

    // Error 422 con el formato de Laravel:
    //   { "message": "...", "errors": { "campo": ["mensaje"] } }
    private fun validationError(message: String, errors: Map<String, String>): HttpException {
        val json = buildJsonObject {
            put("message", message)
            put("errors", buildJsonObject {
                errors.forEach { (field, text) -> put(field, buildJsonArray { add(text) }) }
            })
        }
        return httpError(422, json.toString())
    }

    private fun findProduct(id: Long): DemoProduct =
        products.firstOrNull { it.id == id }
            ?: throw httpError(404, """{"message":"Producto no encontrado."}""")

    private fun sortedByName(list: List<DemoProduct>) = list.sortedWith(compareBy { normalize(it.name) })

    private fun newestFirst(list: List<DemoSale>) =
        list.sortedWith(compareByDescending<DemoSale> { it.soldAt.toInstant() }.thenByDescending { it.id })

    // Porcentaje con 1 decimal; si el divisor es 0 devuelve 0 (evita dividir por cero).
    private fun percentage(part: Double, whole: Double): Double =
        if (whole == 0.0) 0.0 else Math.round(part / whole * 1000.0) / 10.0

    // Minusculas y sin tildes, para que "cafe" encuentre "Cafe" con tilde.
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    // Paginacion como Laravel: current_page / last_page / total.
    private fun <T> paginate(list: List<T>, page: Int, perPage: Int): Pair<List<T>, PageMetaDto> {
        val lastPage = maxOf(1, (list.size + perPage - 1) / perPage)
        val current = page.coerceAtLeast(1)
        val items = list.drop((current - 1) * perPage).take(perPage)
        return items to PageMetaDto(current, lastPage, list.size)
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun DemoSale.date(): LocalDate = soldAt.atZoneSameInstant(clock.zone).toLocalDate()

    private fun DemoSale.numberText() = "#V-$id"

    private fun DemoSale.toDto(withItems: Boolean) = SaleDto(
        id = id,
        number = numberText(),
        cashier = CashierDto(cashier.id, cashier.name),
        paymentMethod = method,
        origin = origin,
        total = total,
        soldAt = soldAt.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        items = if (withItems) items.map { SaleItemDto(it.name, it.quantity, it.unitPrice, it.subtotal) }
        else emptyList()
    )

    private fun DemoProduct.categoryDto() = categories.firstOrNull { it.id == categoryId }

    private fun DemoProduct.toCatalogDto() = ProductDto(
        id = id,
        barcode = barcode,
        name = name,
        category = categoryDto(),
        salePrice = money(salePrice),
        stock = stock,
        minStock = minStock
    )

    private fun DemoProduct.toAdminDto() = AdminProductDto(
        id = id,
        barcode = barcode,
        name = name,
        category = categoryDto(),
        costPrice = costPrice,
        salePrice = salePrice,
        unitProfit = salePrice - costPrice,
        stock = stock,
        minStock = minStock,
        lowStock = isLow,
        active = active
    )

    // =================================================================================
    // Datos de ejemplo (tienda colombiana, precios en COP)
    // =================================================================================

    private fun seedProducts() {
        fun add(barcode: String, name: String, category: Long, cost: Double, price: Double, stock: Int, min: Int) {
            products += DemoProduct(nextProductId++, barcode, name, category, cost, price, stock, min)
        }
        add("7702004003508", "Café Sello Rojo 500 g", 1, 9800.0, 13500.0, 42, 10)
        add("7702007012345", "Arroz Diana 1 kg", 2, 3600.0, 4900.0, 8, 15)
        add("7702011045678", "Aceite Gourmet 900 ml", 3, 11200.0, 15900.0, 26, 8)
        add("7702354001122", "Leche Alquería 1 L", 4, 3100.0, 4200.0, 4, 12)
        add("7702189023456", "Azúcar Manuelita 1 kg", 2, 3900.0, 5200.0, 55, 10)
        add("7702025109876", "Galletas Festival x12", 5, 5400.0, 7800.0, 31, 10)
        add("7702133000874", "Jabón Rey x3", 6, 4700.0, 6900.0, 3, 6)
        add("7702001048720", "Huevos AA x30", 7, 14500.0, 18900.0, 18, 5)
        add("7702535011234", "Gaseosa Postobón 1,5 L", 1, 2900.0, 4500.0, 64, 12)
        add("7702048123987", "Atún Van Camps 170 g", 8, 4100.0, 5900.0, 6, 10)
    }

    // 25 ventas repartidas en los ultimos 7 dias (la mas reciente es de HOY, antes de "ahora").
    // Todo es determinista (sin azar) para que la demo siempre se vea igual.
    // Las ventas historicas NO descuentan stock: el stock de ejemplo ya es el actual.
    private fun seedSales() {
        val now = OffsetDateTime.now(clock)
        val zone = clock.zone
        val perDay = listOf(3, 3, 3, 4, 3, 4, 5) // desde hace 6 dias hasta hoy = 25 ventas
        var index = 0
        perDay.forEachIndexed { position, count ->
            val daysAgo = perDay.size - 1 - position
            val startOfDay = now.toLocalDate().minusDays(daysAgo.toLong()).atStartOfDay(zone).toOffsetDateTime()
            for (j in 0 until count) {
                // Hoy se reparten entre las 00:00 y "ahora"; los otros dias entre 8:30 y 18:30.
                val moment = if (daysAgo == 0) {
                    val elapsed = Duration.between(startOfDay, now)
                    startOfDay.plus(elapsed.dividedBy((count + 1).toLong()).multipliedBy((j + 1).toLong()))
                } else {
                    startOfDay.plusMinutes(8 * 60L + 30 + j * (600L / count))
                }
                val offline = index % 5 == 2
                val itemCount = 1 + index % 3
                val items = (0 until itemCount).map { k ->
                    val p = products[(index * 3 + k * 4) % products.size]
                    DemoItem(p.id, p.name, 1 + (index + k) % 4, p.salePrice, p.costPrice)
                }
                sales += DemoSale(
                    id = nextSaleId++,
                    reference = "demo-seed-${index + 1}",
                    cashier = if (index % 2 == 0) laura else carlos,
                    method = if (index % 3 == 1) "nequi" else "efectivo",
                    origin = if (offline) "offline" else "online",
                    soldAt = moment,
                    // Las ventas offline se sincronizaron un rato despues de hacerse.
                    syncedAt = if (offline) minOf(moment.plusMinutes(12), now) else moment,
                    items = items
                )
                index++
            }
        }
    }

    private companion object {
        const val PRODUCTS_PER_PAGE = 20 // inventario y catalogo
        const val SALES_PER_PAGE = 15    // historial de ventas
    }
}
