# 01. Project Overview & Scope

## 1. What the product is

**PATISSERIE_POS** is an offline-first point-of-sale application for pastry shops, bakeries and similar
counter-service businesses in Morocco. It runs as a Compose Multiplatform **desktop** application on
Windows 10/11 (primary target, MSI/EXE) and Linux (DEB), uses a local SQLite database, prints ESC/POS
receipts, drives a serial customer pole display (VFD) and reads USB barcode scanners (keyboard wedge).

Identity constants (`desktopApp/src/desktopMain/kotlin/ma/elaroui/pos/desktop/DesktopBuildInfo.kt`):
`APPLICATION_NAME = PATISSERIE_POS`, `APPLICATION_ID = ma.elaroui.generalpos`,
`PRODUCT_ID = GENERAL_POS_V1` (licence product id), `LINUX_PACKAGE_NAME = patisserie-pos`,
`WINDOWS_UPGRADE_UUID = 46788696-3912-4d20-9f7d-220b62935d31`.

> The application id and licence product id still say `generalpos` / `GENERAL_POS_V1` for compatibility
> with licences and installations created under the former "General POS" name. Do not rename these
> without a migration plan.

## 2. Repository modules

| Module | Role | Notes |
| :--- | :--- | :--- |
| `:sharedLogic` | Domain models, business rules, use cases, VFD formatting/protocol drivers | KMP, target `jvm("desktop")`, JVM toolchain 17. No UI, no JDBC. |
| `:desktopApp` | The shipped application: UI, SQLite persistence, printing, licensing, packaging | Compose Desktop, JVM toolchain 17. |
| `:app` | Old Android/Room implementation | Own Room schema (v9), own copy of domain logic (`Step3…Step8UseCases.kt`, `MonetaryUtils.kt`) and a `SharedBusinessAdapter.kt`. **Not shipped; do not assume it mirrors desktop behaviour.** |

## 3. Feature status (verified against code)

| Feature | Status | Where / notes |
| :--- | :---: | :--- |
| First-run setup wizard (6 steps) | Implemented | `presentation/setup/SetupScreen.kt`; see 04 §1 for what is actually persisted |
| PIN login, owner/cashier roles, route guards | Implemented | `DesktopNavGraph.kt` (`isOwnerRoute`, `login`) |
| Counter sale: cart, per-item and global % discount, inclusive VAT | Implemented | `OrderRules.kt`, `PosUseCases.kt` (`CreateOrder`) |
| Hold / resume sales ("active orders") | Implemented | `ActiveOrdersScreen.kt`, `DesktopNavState.holdOrder/resumeOrder` |
| Payment: **cash** (change) and **card** (manual TPE confirmation) | Implemented | `PaymentScreen.kt`, `PaymentRules.kt` |
| Payment: Carnet client / Mobile-QR | **Not implemented in UI** | Enum values exist (`PaymentMethod`) and `PaymentRules` accepts them, but `PaymentScreen.kt` only offers CASH and CARD. No customer ledger exists. |
| Preorders (customer, pickup date, deposit, preparation status) | **Partial** | DB columns, `PreorderRules.kt` and `CreateOrder` validation exist; the desktop POS screen never creates a `PREORDER` order and `CompletePayment` ignores deposits. See 04 §7 and file 10. |
| Dine-in tables / dining areas | Partial (legacy) | Table/area CRUD and optional table on dine-in orders exist; new sales created by the POS screen are always `COUNTER` (`saveDraft`). |
| Register sessions, cash in/out, closing with count, closing report | Implemented | `PosUseCases.kt`, `RegisterRules.kt`, `SessionClosingReportRules.kt` |
| Owner PIN approval for cash withdrawals | **Not implemented** | Only a localized label exists (`DesktopLocalization.kt: configureApprovalPin`). |
| Order cancellation with reason (+ optional cancellation PIN for cashiers) | Implemented | `WindowsPosDatabase.cancelOrder` |
| Catalogue: categories (up to 4 levels via CSV), products, images, SKU/barcode | Implemented | `CategoryManagementScreen.kt`, `ProductManagementScreen.kt`, `importing/CsvImports.kt` |
| Barcode scanner (keyboard wedge) | Implemented | `platform/BarcodeScannerController.kt`, `DesktopNavState.onBarcodeScanned` |
| **Stock / inventory quantities** | **Not implemented** | Only the boolean `products.available` ("out of stock" toggle). Nothing is decremented on sale. |
| Receipt printing (80/58 mm), cash-drawer pulse, preparation ticket | Implemented | `print/*` (Windows: `javax.print`; Linux: CUPS) |
| VFD customer display (5 protocols, serial) | Implemented (needs hardware validation) | `sharedLogic/.../display/*`, `desktopApp/.../display/*` |
| Reports: daily sales, analytics by product/category/cashier, session history | Implemented | `DailySalesReportScreen.kt`, `WindowsPosDatabase.salesSummary/retailSalesAnalytics/salesEvolution` |
| Backup (ZIP) / staged restore, pre-migration auto-backup | Implemented | `backup/DesktopBackupService.kt`, `WindowsPosDatabase.kt` |
| Selective data deletion + factory reset (admin password or owner PIN) | Implemented | `DataManagementScreen.kt`, `WindowsPosDatabase.delete*`. **No automatic safety backup is taken** (file 10, KI-12). |
| Offline licence: 7-day trial + signed licence | Implemented | `license/*` — ECDSA P-256 (not Ed25519), see 07 |
| Languages: French, Arabic (RTL), English | Implemented | `DesktopLocalization.kt` |
| Cloud sync, multi-store, loyalty, purchasing, delivery-app integrations | **Not implemented / out of scope** | — |

## 4. Roles & permissions (as enforced)

Enforcement is **route-level** in `DesktopNavState.navigateTo` using `isOwnerRoute(route)`; there is
no per-action permission check in the desktop app. (`PermissionRules` / `Permission` in
`sharedLogic/.../rules/PermissionAndValidationRules.kt` exist but are **not used** by `:desktopApp`.)

| Capability | OWNER | CASHIER | Enforcement |
| :--- | :---: | :---: | :--- |
| POS sale, hold/resume, payment, receipt preview/print | ✅ | ✅ | Non-owner routes |
| Open / close own register session, cash in/out | ✅ | ✅ | Non-owner routes; `RecordCashMovement` requires `session.cashierId == userId` |
| Cancel an open order | ✅ (no PIN) | ✅ (needs cancellation PIN **if one is configured**) | `WindowsPosDatabase.cancelOrder` |
| Dashboard, products, categories, tables, cashiers | ✅ | ❌ | `isOwnerRoute` |
| Reports, completed sales, sale detail, register history | ✅ | ❌ | `isOwnerRoute` |
| Settings, printers, customer display, backup/restore, data management, licence | ✅ | ❌ | `isOwnerRoute` (includes `LICENSE_GATE`) |

Because the guard is only on navigation, any new **action** reachable from a non-owner screen must
re-check `currentUser.role` itself.

## 5. Market & fiscal context

- Currency MAD (`DH`), stored as centimes (`1 DH = 100`).
- Prices are **VAT-inclusive**; VAT is extracted per line (see 04 §3). The CSV importer accepts only
  `0`, `10`, `20` % (`0`, `1000`, `2000` basis points); product CRUD accepts any `0..10000` bp
  (`ProductValidationRules`).
- Receipt header fields come from `settings`: `establishment_name`, `establishment_specialty`,
  `restaurant_address`, `restaurant_phone`, `seller_ice`, `seller_tax_id` (IF), `seller_patente`,
  `seller_commercial_register` (RC), `wifi_name`, `wifi_code`, `restaurant_logo_uri`,
  `print_establishment_name` (`DesktopNavGraph.kt`, `getCompany`, ~line 2430).
