# SyncPOS (Android)

Punto de venta móvil para el cajero, con ventas sin conexión que se sincronizan con la API Laravel.

## Cómo ejecutarlo

1. Levanta la API Laravel en el computador: `php artisan serve` (puerto 8000).
2. Abre el proyecto en Android Studio y arranca el emulador (o conecta tu celular por USB).
3. Conecta el puerto 8000 del celular con el del computador (hay que repetirlo cada vez que arrancas el emulador o reconectas el celular):

   ```
   adb reverse tcp:8000 tcp:8000
   ```

   La app usa `http://127.0.0.1:8000/api/` (`BuildConfig.API_URL` en `app/build.gradle.kts`).
   **¿Por qué no `10.0.2.2`?** Desde Android 17 (API 37) las apps tienen bloqueado por defecto el acceso a la red local, y `10.0.2.2` cuenta como red local. Con `adb reverse` el tráfico va por el propio celular (`127.0.0.1`) y no hace falta pedir permisos extra.
4. Ejecuta la app e inicia sesión con un usuario cajero (`cajero@syncpos.co` / `password`). Se descarga el catálogo a la base de datos local (Room).

Por línea de comandos (Windows, con el JDK de Android Studio):

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Arquitectura (MVVM simple)

```
ui/        Pantallas Compose + ViewModel (StateFlow)   login, pos, history, admin
domain/    Lógica pura: Cart, mapSyncResults, SyncSummary
data/
  local/       Room: ProductEntity, LocalSaleEntity, LocalSaleItemEntity, DAOs
  remote/      Retrofit: SyncPosApi, DTOs, ApiClient
  repository/  AuthRepository, ProductRepository, SalesRepository, AdminRepository
  session/     SessionManager (SharedPreferences)
  connectivity/ConnectivityObserver (Flow<Boolean>)
AppContainer   Inyección de dependencias manual (creado en SyncPosApp)
```

## Flujo de una venta

1. Se guarda en Room como `PENDIENTE` con un UUID (`offline_referencia`).
2. Se intenta enviar a `POST /api/sync/ventas` con `origen: "online"`. Si no hay red, queda pendiente.
3. Al volver la conexión se envían automáticamente todas las pendientes con `origen: "offline"` (o con el botón "Forzar Sincronización Ahora").
4. `sincronizada` y `duplicada` se marcan como `SINCRONIZADA`; `rechazada` guarda el motivo.

El servidor recalcula los precios: la app solo envía `producto_id` y `cantidad`.

## Pantallas de administrador

Usuario de prueba: `admin@syncpos.co` / `password`. Al iniciar sesión, la app mira el rol: `admin` entra al panel de administrador y `cajero` al punto de venta. Las pantallas de admin son **solo en línea** (leen los datos del servidor, no usan Room) y por eso el login del admin no descarga el catálogo. Si el servidor responde 401 (sesión vencida) la app vuelve sola al login.

Barra inferior con 4 pestañas (`ui/admin/`):

| Pestaña | Qué hace |
|---|---|
| Inicio | Dashboard: ventas de hoy, ganancia neta, inversión en inventario, alertas de stock (tocarla abre Inventario con "Stock bajo"), gráfico de ventas vs. ganancias de 7 días dibujado con `Canvas` y las últimas 5 ventas. |
| Inventario | Búsqueda con retardo (debounce de 400 ms), filtros Todas / Stock bajo / categorías, lista paginada con "Cargar más", activar/desactivar productos con confirmación y formulario de producto (nuevo/editar) que muestra los errores 422 del servidor bajo cada campo. |
| Reportes | Rentabilidad por Día / Semana / Mes / Rango (calendario), totales, ranking de productos y exportación a **CSV** y **PDF** que se comparte con la hoja de Android (`FileProvider`). |
| Ventas | Historial de todas las ventas con búsqueda (N° de factura o cajero), filtro por método de pago, paginación y hoja inferior "Detalle de Factura" con opción de compartir como texto. |

Archivos nuevos por capa: `data/remote/AdminDtos.kt` y `MoneySerializer.kt` (el dinero llega como número o como texto), `data/repository/AdminRepository.kt` (errores de red, 401, 403 y 422 ya traducidos), `domain/` (rango de fechas, CSV, factura, ganancia del producto) y una carpeta por pestaña en `ui/admin/`.
