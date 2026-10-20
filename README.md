# SyncPOS (Android)

Punto de venta móvil para el cajero, con ventas sin conexión que se sincronizan con la API Laravel.

## Cómo ejecutarlo

1. Levanta la API Laravel en el computador: `php artisan serve` (puerto 8000).
2. Abre el proyecto en Android Studio y ejecútalo en un emulador (usa `http://10.0.2.2:8000/api/`, definido en `app/build.gradle.kts` como `BuildConfig.API_URL`).
3. Inicia sesión con un usuario cajero. Se descarga el catálogo a la base de datos local (Room).

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
2. Se intenta enviar a `POST /api/sync/ventas`. Si no hay red, queda pendiente.
3. Al volver la conexión se envían automáticamente todas las pendientes (o con el botón "Forzar Sincronización Ahora").
4. `sincronizada` y `duplicada` se marcan como `SINCRONIZADA`; `rechazada` guarda el motivo.

El servidor recalcula los precios: la app solo envía `producto_id` y `cantidad`.
