# SyncPOS Android — Demo flavor (no backend)

## Objective
A second installable build, "SyncPOS Demo", that shows every real screen (cashier and admin) with in-memory sample data and full navigation, without Laravel running. Used to present the mobile app.

## Why
The user must present the mobile views without a backend connection (request on 2026-10-21).

## Scope (authorized: "hazme una version movil con solo las vistas con navegabilidad entre pestanas ... sin estar conectado en back ... que se vean el admin y el cajero y que se pueda navegar")
- Product flavors: `full` (current behaviour, unchanged) and `demo` (applicationIdSuffix `.demo`, app name "SyncPOS Demo").
- Demo replaces ONLY the network layer: a `FakeSyncPosApi` implementing the existing Retrofit interface with in-memory data. Repositories, Room, ViewModels and screens stay the same code.
- Login (demo only): buttons "Entrar como Administrador" and "Entrar como Cajero".
- Offline demo: when the device has no network, the fake API throws IOException so the real offline/pending/auto-sync flow is visible.

## Constraints
- Do not change the `full` behaviour; `assembleFullDebug` and existing tests must still pass.
- Comments in Spanish WITHOUT accents, `// HOOK:` prefix where hooks/compose state are explained; UI text with accents.

## Tasks
- [x] D1 Flavors (full/demo), app name + launcher label, BuildConfig flag; full unchanged (delegated writer)
- [x] D2 FakeSyncPosApi with sample data (products, categories, sales over 7 days, two cashiers) covering every endpoint used (delegated writer)
- [x] D3 Demo login buttons + offline simulation via connectivity (delegated writer)
- [x] D4 Tests for the fake API (sale totals, stock, idempotent sync) + build both flavors (writer) + parent emulator walkthrough of admin and cashier tabs without Laravel

## Acceptance criteria
- With Laravel stopped, the demo APK logs in as admin and cashier and every tab shows data.
- Cashier sale updates history; airplane mode -> pending; reconnect -> synced.

## Progress
- 2026-10-21: Plan created.
- 2026-10-21: D1-D4 implemented by the writer. Flavors full/demo (resValue app_name, BuildConfig.DEMO), `provideSyncPosApi()` per source set (src/full real, src/demo fake), `FakeSyncPosApi` (in-memory, 10 products, 25 sales over 7 days, 422/404 errors, idempotent sync, IOException when offline), demo login buttons, 11 JVM tests in `src/testDemo`, README section. Observed: assembleFullDebug + assembleDemoDebug OK; testFullDebugUnitTest and testDemoDebugUnitTest 0 failures; accent check on comments empty. Decision: login/logout/cashier catalog do NOT require network in the fake (demo opens without Wi-Fi); all other endpoints throw IOException offline. Emulator walkthrough (admin + cashier + airplane mode) still pending (parent).
