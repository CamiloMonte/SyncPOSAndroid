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
- [ ] T1 Scaffold project, version catalog, SENA theme, navigation; `assembleDebug` passes (delegated writer)
- [ ] T2 Data layer: Retrofit API, Room (productos, ventas locales + items), session, connectivity, repository (delegated writer)
- [ ] T3 Login + catalog download (delegated writer)
- [ ] T4 POS screen: search, scanner, cart, payment, register sale online/offline (delegated writer)
- [ ] T5 History + sync screen, auto sync on reconnect (delegated writer)
- [ ] T6 Unit tests (cart math, sync result handling) + README; build + tests + emulator smoke test (writer + parent)

## Acceptance criteria
- `gradlew assembleDebug` and `gradlew testDebugUnitTest` pass.
- A sale registered without network is stored as pending and, once synced, the server returns `sincronizada`; re-sending returns `duplicada` and the app marks it synced without duplicating.

## Checks
- TDD: off (no project configuration). Runner: `gradlew testDebugUnitTest`.
- Git: new repository initialized in this folder; one Conventional Commit per task.

## Progress
- 2026-10-20: Environment verified, versions resolved, plan created.

## Next step
T1-T6 via one delegated writer; parent verifies build, tests and an emulator run against the e2e Laravel API.
