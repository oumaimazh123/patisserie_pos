# 05. Screens, Routes & Navigation

Source: `desktopApp/src/desktopMain/kotlin/ma/elaroui/pos/desktop/presentation/navigation/DesktopNavGraph.kt`
(`enum class DesktopScreenRoute` ~line 95, `isOwnerRoute` ~line 102, `DesktopNavState`, root composable
`DesktopNavGraph`). Screens live in sibling packages under `presentation/`.

## 1. Navigation model

- There is no navigation library. `DesktopNavState.currentRoute` is a Compose state; the root composable
  `when`s on the gating conditions first and then on `currentRoute`:
  1. licence not allowing use → licence gate;
  2. `!setupComplete` → `SetupScreen`;
  3. `currentUser == null` → `UserSelectionScreen`;
  4. otherwise the screen for `currentRoute`.
- `navigateTo(route)` redirects to `POS_MAIN` when `isOwnerRoute(route)` and the user is not an owner.
  Direct assignments to `currentRoute` bypass this guard — new code must use `navigateTo`.
- The licence gate is reachable by owners as route `LICENSE_GATE` (listed as an owner route).

### Route inventory (`DesktopScreenRoute`, 25 values)

| Route | Owner-only | Screen file (under `presentation/`) | Purpose (verified) |
| :--- | :---: | :--- | :--- |
| `SETUP` | no | `setup/SetupScreen.kt` | 6-step first-run wizard (04 §1) |
| `LICENSE_GATE` | **yes** | `license/LicenseManagementScreen.kt` | Trial status, request code, licence activation |
| `USER_SELECTION` | no | `auth/UserSelectionScreen.kt` | User grid + numeric keypad PIN entry |
| `DASHBOARD` | **yes** | `dashboard/DashboardScreen.kt` | Today's sales / cash / card tiles and shortcuts |
| `POS_MAIN` | no | `pos/main/POSMainScreen.kt` | Category tabs/sub-categories, product grid, search (name/barcode), cart and discounts |
| `ACTIVE_ORDERS` | no | `pos/active/ActiveOrdersScreen.kt` | Held (`OPEN`) orders of the session: search by number, resume, cancel |
| `PAYMENT` | no | `payment/PaymentScreen.kt` | Cash (keypad + "Montant exact", 50/100/200 DH quick buttons, change) or card |
| `RECEIPT_PREVIEW` | no | `sales/ReceiptPreviewScreen.kt` | Text preview and print of a customer/preparation ticket |
| `OPEN_REGISTER` | no | `register/open/OpenRegisterScreen.kt` | Declare opening float (normal login auto-opens a 0-float session, 04 §2) |
| `CURRENT_SESSION` | no | `register/current/CurrentSessionScreen.kt` | Live session totals, cash in/out |
| `CLOSE_REGISTER` | no | `register/close/CloseRegisterScreen.kt` | Count drawer, split left/removed, remittance reference, note, auto-print option |
| `REGISTER_HISTORY` | **yes** | `register/history/RegisterSessionsHistoryScreen.kt` | Past sessions, closing reports (reprint) |
| `PRODUCT_MGMT` | **yes** | `management/products/ProductManagementScreen.kt` | Product CRUD, images, SKU/barcode, soft delete/restore, CSV import dialog |
| `CATALOGUE_PREVIEW` | **yes** | `management/products/CataloguePreviewScreen.kt` | Read-only preview of the sellable grid |
| `CATEGORY_MGMT` | **yes** | `management/categories/CategoryManagementScreen.kt` | Category/sub-category CRUD, images |
| `TABLE_MGMT` | **yes** | `management/tables/TableManagementScreen.kt` | Legacy dining areas/tables |
| `CASHIER_MGMT` | **yes** | `cashiers/CashierManagementScreen.kt` | Create cashier, change PIN, activate/deactivate, soft delete/restore |
| `DAILY_REPORT` | **yes** | `reports/DailySalesReportScreen.kt` | Date-range sales summary and breakdowns |
| `COMPLETED_SALES` | **yes** | `sales/CompletedSalesScreen.kt` | History with search/filters (payment method filter CASH/CARD) |
| `SALE_DETAIL` | **yes** | `sales/SaleDetailScreen.kt` | Line items, totals, payment info; can open `RECEIPT_PREVIEW` to reprint |
| `SETTINGS` | **yes** | `settings/SettingsScreen.kt` | Establishment and fiscal info, logo, language, cancellation PIN, admin deletion password, closing-report auto print |
| `PRINTER_SETTINGS` | **yes** | `settings/PrinterSettingsScreen.kt` | Printer selection, paper width 58/80, cash drawer, test print |
| `CUSTOMER_DISPLAY_SETTINGS` | **yes** | `settings/CustomerDisplaySettingsScreen.kt` | VFD port/protocol/baud/…/messages, connection test |
| `BACKUP_RESTORE` | **yes** | `settings/BackupRestoreScreen.kt` | ZIP backup, validate + stage restore (applies on restart) |
| `DATA_MANAGEMENT` | **yes** | `settings/DataManagementScreen.kt` | Selective deletion, factory reset (04 §9) |

Other screens not in the enum: `diagnostics/TouchDiagnosticScreen.kt` (touch debugging),
`management/catalog/CatalogImportDialog.kt` (CSV analysis/preview dialog used by product/category screens).

## 2. Touch & input conventions

- Components in `presentation/components/`: `NumericKeypad`, `VirtualKeyboard` (on-screen keyboard),
  `TouchInputField`, `TouchScroll` (drag scrolling), `TouchDatePickerModal`, `SafeImage` (loads local/URL
  images with fallback), `PosDesignSystem` (colors/dimensions — terracotta/honey palette, see
  [`docs/DESIGN.md`](../DESIGN.md)), alert/empty-state/badge helpers.
- Touch-first targets and large keypads are a design goal; there is an automated audit
  (`TouchScrollingAuditTest`, `TouchInputFieldTest`) but no formal 48 dp enforcement in code.
- Barcode scanner input is handled on the POS screen by `BarcodeScannerController` (06 §4); the search box
  is cleared when a scan completes.
- Linux has extra input workarounds (`platform/LinuxTouchInputCompatibility.kt`).

## 3. Localization

`DesktopLocalization.kt` provides `DesktopStrings` for French (default), English and Arabic
(`DesktopLanguage`). Arabic switches layout direction to RTL. Most screens inline
`strings.text("fr", "en", "ar")` triples. Receipts are rendered in Latin text only — Arabic text is
rejected by the Linux formatter (`UNSUPPORTED_CHARACTERS`) (06 §1).

## 4. Screens/behaviours that older docs described but do not exist

| Claimed | Reality |
| :--- | :--- |
| Payment methods "Carnet Client" and "Mobile QR" buttons | Only Cash and Card are rendered in `PaymentScreen.kt` |
| Preorder cards with deposit/balance, status chips, "Encaisser le solde" | No such UI; `ACTIVE_ORDERS` lists open orders with search by number only |
| "Refund audit" in sales history | No refund feature exists |
| "Encrypted backup" | ZIP is **not** encrypted |
| Owner PIN approval for withdrawals | Not implemented |
| `.db` backup file in backup screen | Backups are `.zip` (legacy `.db` can still be restored). The export card's help text still says "(.db)" — cosmetic inconsistency |
