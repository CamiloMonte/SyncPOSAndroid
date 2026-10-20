# SyncPOS Android (cashier POS app)

## Objective
Native Android POS app for the cashier that sells online or offline and syncs pending sales with the Laravel API (`POST /api/sync/ventas`).

## Why
The offline-first sales flow is the project's key differentiator: sales must never be lost when the store has no internet.

## Scope (authorized by the user on 2026-10-20: "empieza a hacer el movil en android studio")
- Screens from the Figma design (360x800): Login, Punto de Venta (online/offline), Historial y Sincronizacion.
- Admin mobile screens are out of scope for this first iteration.

## Stack (versions verified online on 2026-10-20)
- AGP 9.4.1 (built-in Kotlin: no `org.jetbrains.kotlin.android` plugin), Gradle 9.8.2, Kotlin Compose compiler plugin 2.4.21, KSP 2.3.12, Room 2.8.5, Compose BOM 2026.09.00.
- Installed: Android Studio (JBR JDK 25), SDK platform android-37, build-tools 36.0.0, emulator + system images.
- Networking: Retrofit + kotlinx.serialization. Barcode: Google Code Scanner (Play Services, no camera permission). Session: SharedPreferences. DI: manual (Application container).

## Constraints
- Beginner-friendly MVVM (screen + ViewModel + repository). Comments in Spanish WITHOUT accent marks (user preference). UI text in Spanish with accents. Identifiers in English.
- Never trust client prices: the server computes totals; the app only sends producto_id + cantidad.
- Emulator reaches the host Laravel at `http://10.0.2.2:8000/api/` (debug cleartext allowed only for that host).

## Decisions
- Every sale is stored in Room first with a UUID `offline_referencia` and status `PENDIENTE`, then the app immediately tries `POST /sync/ventas` with that single sale. Using the idempotent sync endpoint for online sales too means one code path and no duplicates if the response is lost (`duplicada` is treated as success).
- Catalog cached in Room after login (all pages of `GET /productos?solo_activos=1`) so the POS works offline.
- Auto sync when connectivity returns + manual "Forzar Sincronización Ahora".

## Tasks
- [x] T1 Scaffold project, version catalog, SENA theme, navigation; `assembleDebug` passes (delegated writer)
- [x] T2 Data layer: Retrofit API, Room (productos, ventas locales + items), session, connectivity, repository (delegated writer)
- [x] T3 Login + catalog download (delegated writer)
- [x] T4 POS screen: search, scanner, cart, payment, register sale online/offline (delegated writer)
- [x] T5 History + sync screen, auto sync on reconnect (delegated writer)
- [x] T6 Unit tests (cart math, sync result handling) + README; build + tests done by writer; emulator smoke test pending (parent)

## Acceptance criteria
- `gradlew assembleDebug` and `gradlew testDebugUnitTest` pass.
- A sale registered without network is stored as pending and, once synced, the server returns `sincronizada`; re-sending returns `duplicada` and the app marks it synced without duplicating.

## Checks
- TDD: off (no project configuration). Runner: `gradlew testDebugUnitTest`.
- Git: new repository initialized in this folder; one Conventional Commit per task.

## Progress
- 2026-10-20: Environment verified, versions resolved, plan created.
- 2026-10-20: T1-T6 implemented by the writer. Observed: `./gradlew assembleDebug` BUILD SUCCESSFUL (app/build/outputs/apk/debug/app-debug.apk); `./gradlew testDebugUnitTest` 19 tests, 0 failures (CartTest 7, MoneyTest 6, SyncMappingTest 6); no accents in code comments (rg check empty). Commits: one per task (T1 scaffold, T2 data, T3 auth, T4 pos, T5 history; T6 tests+README+this doc). Emulator run NOT done (parent).
- Versions used: AGP 9.4.1, Gradle 9.8.2, Kotlin/Compose/serialization plugins 2.4.21, KSP 2.3.12, Room 2.8.5, Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, navigation-compose 2.10.2, Retrofit 3.0.0, OkHttp 5.5.0, kotlinx-serialization 1.12.0, coroutines 1.11.0, code-scanner 16.1.0, material-icons-extended 1.7.8. compileSdk/targetSdk 37, minSdk 26.
- Design notes: sale statuses flow PENDIENTE -> SINCRONIZADA/RECHAZADA; unknown server `estado` is ignored (sale stays pending); Cart is immutable and has no stock limit (server flags negative stock); stepper minimum is 1 (trash removes); if catalog download fails after login but Room has an old catalog, the app continues with it.

- 2026-10-20: Parent emulator run (Medium Phone API 37) against Laravel served on a temporary SQLite DB. Found and fixed: (1) API 37 blocks local-network access, so `10.0.2.2` timed out -> dev URL switched to `127.0.0.1` + `adb reverse tcp:8000 tcp:8000` (README updated); (2) search field kept focus and the keyboard covered the checkout bar -> `focusManager.clearFocus()` after picking a product; (3) every sale reached the server as `origen: offline` -> app now sends `origen` (online for the sale sent right after registering, offline for queued ones) and Laravel `sync/ventas` accepts the optional field (2 new feature tests, 32 passing). Observed end to end: login + catalog download; online sale -> server `#V-2 origen online`; offline sale -> app "Offline (1 pend.)" + history "Pendiente (Offline)"; reconnect -> auto sync, server `#V-3 origen offline`, rice stock 8 -> 6; re-sending the same UUID -> `duplicada`, still 3 sales, stock 6. `assembleDebug` + `testDebugUnitTest` pass after fixes; no accents in comments.
- Not exercised: barcode scanner (needs a camera/barcode in front of the emulator), "Forzar Sincronización Ahora" button (auto sync covered the same path), logout.

## Next step
Admin mobile screens (Dashboard, Inventario, Reportes, Historial) when the user asks; or continue the React connection (step 3, POS web).
