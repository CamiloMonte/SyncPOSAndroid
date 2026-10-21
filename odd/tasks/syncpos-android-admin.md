# SyncPOS Android — Admin screens

## Objective
Give the admin role its own mobile screens (Figma "Android Admin" row): Dashboard, Inventario, Reportes, Historial de Ventas + invoice detail bottom sheet, with a bottom navigation bar.

## Why
Today every user lands on the cashier POS after login, whatever the role. The admin needs the same information as the web panel from the phone.

## Scope (authorized by the user on 2026-10-20: "haz las pantallas de administrador en android")
- Role-based navigation after login and on app start: `rol == "admin"` -> admin shell; `cajero` -> POS (unchanged).
- Admin screens are ONLINE-ONLY (they read live server data); show a clear offline/error state with "Reintentar". No Room caching for admin data.
- Out of scope: user management, categories CRUD, editing sales.

## API contract (read from the Laravel source on 2026-10-20)
- `GET reportes/resumen-dia?fecha=` -> `{ ventas_total, pedidos, ganancia_neta, inversion_inventario, alertas_stock, ultimas_ventas: [VentaResource], ultimos_7_dias: [{ fecha, ventas, ganancia }] }` (numbers).
- `GET productos?buscar=&categoria_id=&stock_bajo=1&page=` (paginated 20, `meta.current_page/last_page/total`), `GET categorias`, `POST productos`, `PUT productos/{id}`, `PATCH productos/{id}/estado`; 422 `{ message, errors: { campo: [..] } }`.
- `GET reportes/rentabilidad?desde=&hasta=` -> `{ periodo:{desde,hasta}, totales:{ingresos,costo,ganancia,margen}, productos:[{producto_id,nombre,unidades,ingresos,costo,ganancia,margen,contribucion}] }`.
- `GET ventas?buscar=&metodo_pago=&origen=&desde=&hasta=&page=` (paginated 15) and `GET ventas/{id}` -> VentaResource `{ id, numero, cajero:{id,name}, metodo_pago, origen, total:"48500.00", vendida_en, sincronizada_en, items:[{producto_id,nombre,cantidad,precio_unitario,subtotal,costo_unitario?}] }`.

## Constraints
- Same style as the cashier app: Compose + ViewModel(StateFlow) + repository, manual DI in AppContainer, comments in Spanish WITHOUT accents, UI text with accents, identifiers in English.
- Reuse the existing theme, ConnectionPill, PrimaryButton, formatCop.

## Tasks
- [x] A1 Role routing + admin shell with bottom navigation (Inicio, Inventario, Reportes, Ventas) + AdminRepository/DTOs (delegated writer)
- [x] A2 Dashboard: 2x2 stat tiles, 7-day sales vs profit chart (Canvas), últimas ventas (delegated writer)
- [x] A3 Inventario: search, chips (Todas / Stock bajo / categories), paged list, toggle activo, FAB -> create/edit form with 422 field errors (delegated writer)
- [x] A4 Reportes: Día/Semana/Mes/Rango, 3 tiles, export CSV + PDF via share sheet, profitability ranking (delegated writer)
- [x] A5 Historial: search, Efectivo/Nequi filters, paged list, invoice detail bottom sheet (delegated writer)
- [x] A6 Unit tests + build (writer done); emulator run as admin still pending (parent)

## Acceptance criteria
- Admin login lands on the Dashboard with live numbers; cashier login still lands on POS.
- Creating a product with precio_venta < precio_compra shows the server's field error; a valid one appears in the list.
- Reportes CSV opens the Android share sheet with a valid file.
- `assembleDebug` + `testDebugUnitTest` pass.

## Checks
- TDD: off. Runner: `gradlew testDebugUnitTest`. One Conventional Commit per task.

## Progress
- 2026-10-20: API shapes read, plan created.
- 2026-10-20: A1-A6 implemented in 6 commits (5f04818..cc8975b). Observed: assembleDebug OK; testDebugUnitTest 48 tests, 0 failures (new: 29); accent check on comments empty. NOT yet verified on emulator/real server (parent).

- 2026-10-21: Parent emulator run (API 37, adb reverse) against Laravel on a temporary SQLite DB with 3 online + 1 offline sale created through the API. Observed: admin login -> Dashboard (ventas $ 112.000 / 3 pedidos, ganancia $ 31.200, inversion $ 1.519.600, 4 alertas, 7-day Canvas chart, latest sales) - numbers match a manual calculation; Inventario lists products with live stock and Stock Bajo pills; form: empty name caught locally, precio_venta < precio_compra -> server 422 shown under "Precio de venta", valid product -> 201 and appears in the list (dashboard + list refreshed); Reportes Mes: ingresos $ 127.600, ganancia $ 36.000, ranking; CSV export opens the Android share sheet, file has BOM and quotes "Gaseosa Postobon 1,5 L"; PDF generated (valid %PDF header, 65 KB); Ventas: 4 sales ordered by vendida_en, detail bottom sheet for #V-2 with items and total; logout -> cashier login still lands on POS with catalog download.
- Not exercised: Rango DateRangePicker, 401 auto-logout, product edit, toggle activo, "Compartir" receipt, PDF visual content. Emulator note: disabled Gboard stylus handwriting on the emulator (`settings put secure stylus_handwriting_enabled 0`) because its tutorial captured adb text input.

## Next step
Optional polish (edit/toggle/401 checks on a real device), then continue the React connection (step 3, POS web).
