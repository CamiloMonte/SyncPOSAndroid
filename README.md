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
ui/        Pantallas Compose + ViewModel (StateFlow)   login, pos, history
domain/    Lógica pura: Cart, mapSyncResults, SyncSummary
data/
  local/       Room: ProductEntity, LocalSaleEntity, LocalSaleItemEntity, DAOs
  remote/      Retrofit: SyncPosApi, DTOs, ApiClient
  repository/  AuthRepository, ProductRepository, SalesRepository
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
