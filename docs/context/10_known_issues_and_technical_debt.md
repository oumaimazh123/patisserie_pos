# 10. Known Issues & Technical Debt

> **Status: documented, NOT fixed.** These are confirmed by reading the source (line numbers are for the
> working tree at the time of writing and may drift — search by symbol). Do not rely on a "fix" being
> present; do not describe the intended behaviour as implemented. When you fix an item, update this file,
> the relevant context document and add/adjust tests (09).
> Severity: **H** = can produce wrong money/data or loss; **M** = wrong report/UX or security weakness;
> **L** = robustness/maintainability.

## A. Money, reporting and stock

| ID | Sev | Where | Problem | Suggested direction |
| :-: | :-: | :--- | :--- | :--- |
| KI-01 | M | `WindowsPosDatabase.kt` `todaySalesSummary` (~1358), `salesSummary` (~1315) | "Sales today" = `cash + card` payments only. Any `CARNET_CLIENT`/`MOBILE_QR` payment is excluded from the dashboard total but counted in the order count; in `salesSummary` `total` (from `orders`) can exceed `cash + card`. Currently latent because the UI only offers cash/card, but the enum, `PaymentRules` and docs/README advertised more. | Choose one source of truth (payments of all methods) or remove the unused methods from enum/rules/docs. |
| KI-02 | M | `salesSummary` (~1247/1270), `todaySalesSummary` (~1328), analytics, `salesHistory` | Reports filter on `orders.updated_at`; there is no `completed_at`. Any later write to a completed order's row would move it between days. | Filter by `payments.created_at`, or add a `completed_at` column in migration 13. |
| KI-03 | L | `salesSummary` (~1274–1277, ~1255) | Unreachable duplicate `matchingCategoryIds` block (the earlier branch already returns). The category branch `LEFT JOIN payments` per line, so an order with two completed payments would be counted twice. | Delete dead block; aggregate payments in a sub-query. |
| KI-04 | M | `PosUseCases.kt` `CompletePayment`, `DesktopNavState.saveDraft`, `PreorderRules.kt` | **Preorders are scaffolding only**: POS always creates `COUNTER`; no UI for customer/pickup/deposit/status; `CompletePayment` ignores `deposit_centimes` and charges the full total; phone is not validated; `canTransition` allows `PENDING→READY`. | Either build the flow (deposit as a partial payment row, balance at pickup, phone check, UI) or keep it documented as not implemented. |
| KI-05 | M | `DesktopNavState.saveDraft` (`"SALE-${System.currentTimeMillis()}"`) | Order numbers are time-based, not a sequential gap-free series (fiscal receipts usually require one). `order_number` is `UNIQUE`, so two creations in the same millisecond would fail. | Per-day/per-year counter in `settings` incremented in the same transaction as the order insert. |
| KI-06 | M | `importing/CsvImports.kt:338,343` | CSV prices are parsed through `Double` (`toDoubleOrNull`, `priceNumber * 100.0` `roundToLong`), violating the "no floating point for money" rule; values like `1.005` can round differently from `MoneyRules.parseToCentimes`. Percent displays in `DailySalesReportScreen.kt` use `Double` for display only. | Parse with `MoneyRules.parseToCentimes`. |
| KI-07 | — | whole app | **No stock/inventory.** Only `products.available`. Nothing decrements on sale, cancel or import. | Product decision first; then migration 13 + decrement inside `CompletePayment`'s transaction. |
| KI-17 | L | `OrderRules.kt:66` | VAT is extracted with **truncating** integer division per line, not "round HT then subtract". Legacy docs described another formula. Totals are consistent internally, but the policy has not been validated with an accountant; changing it changes historical-vs-new comparisons. | Confirm the fiscal policy, document it, lock with tests. |
| KI-20 | L | `Orders.save` (`WindowsPosDatabase.kt` ~2626), `resumeOrder` (~709) | `line_total_centimes = unit * qty` is an unchecked multiply (rules use `multiplyExact`); `item_discount_basis_points` is written only when `order.discountBasisPoints != null`; `resumeOrder` recomputes bps with integer division (effectively dead for post-v12 orders). | Use exact math; store item discount unconditionally. |

## B. Concurrency, robustness, error handling

| ID | Sev | Where | Problem | Suggested direction |
| :-: | :-: | :--- | :--- | :--- |
| KI-08 | M | `WindowsPosDatabase.Transactions.inTransaction` (~2299–2310) | `ReentrantLock.lock()` is held across a `suspend` block and `unlock()` runs in `finally`. A coroutine resumed on another thread would throw `IllegalMonitorStateException` and leak the lock (deadlock). Safe today only because callers use `runBlocking` on one thread. | Use a coroutine `Mutex` or a single-thread DB dispatcher. |
| KI-09 | M | `DesktopNavGraph.kt` (`saveDraft`, `pay`, `printCompletedSaleReceipt`, `printOperationalTickets`, `loadSalesHistory`, `executeCatalogImport`, …) | `runBlocking` on the UI thread for DB **and printer/drawer I/O**: a slow/offline printer freezes the touch UI mid-sale. | Launch in a scope; update state on completion; keep printing off the UI thread. |
| KI-10 | L | `PosUseCases.kt:162,297`, `DesktopNavState.mapFailureToFrench` | Use cases return `enum.toString()` (`INSUFFICIENT_CASH`, `WITHDRAWAL_EXCEEDS_EXPECTED_CASH`, …) while the UI mapper matches English sentences that are never produced, so raw enum names can reach the cashier. | Typed failures + a single translation `when`. |
| KI-18 | L | `RecordCashMovement`, `CloseRegisterSession`, `cancelOrder` | `RecordCashMovement` reads expected cash then writes (no transaction); `CloseRegisterSession(orders = null)` silently skips the open-order check (only call site passes it); `cancelOrder` throws `IllegalStateException` via `check()` when the order is no longer `OPEN`. | Wrap in transaction; make `orders` required; return typed failure. |
| KI-16 | M | `DesktopNavState.executeCatalogImport` | CSV import is applied **row by row without a transaction** (older docs said atomic); an existing product with the same SKU is overwritten (name, price, tax, category) and re-activated; categories are created as needed. | Single transaction + preview of overwrites; do not reactivate silently. |
| KI-15 | M | `DesktopNavState.login`, `SetupScreen.kt` | Login **auto-opens** a session with `register_id = 1` and opening cash `0` when none exists, so the float declaration screen is bypassed in the normal flow. The setup wizard collects *language* and *register name* but `onSetupComplete` does not pass them (register is always `Main Register`). | Decide intended behaviour; either require the float at first login or persist the wizard data. |
| KI-19 | L | `RecordCashMovement`, parity docs | Owner PIN approval for cash withdrawals is documented elsewhere but **not implemented** (only a label `configureApprovalPin`). `movement()` also always passes `description = null`. | Implement or remove from docs/UI strings. |

## C. Security & data protection

| ID | Sev | Where | Problem | Suggested direction |
| :-: | :-: | :--- | :--- | :--- |
| KI-11 | M | `WindowsPosDatabase.verifyAdminDeletionPassword` (~1638), `AdminDeletionSecurity.kt:16` | Any active **owner PIN (4–6 digits)** unlocks sales deletion and factory reset, contradicting the "separate master password" rule. `DEFAULT_PASSWORD = "admin1234"` is declared but unused (a trap for future code). No attempt limit. | Require the admin password only; delete the constant; add lockout. |
| KI-12 | H | `DataManagementScreen.kt` → `db.deleteSalesHistory/deleteSelective/deleteBusinessData/factoryResetData` | **No automatic backup before destructive deletion**, contrary to earlier docs/README/AGENTS. The screen only links to the Backup page. Factory reset also wipes `audit_logs` and the `data/images` folder. | Call `DesktopBackupService.createBackup` (or `backupTo`) before every deletion and abort if it fails. |
| KI-13 | M | `PinSecurity.kt:15`, `DesktopNavState.handleFailedPin`, `cancelOrder` | 10 000 PBKDF2 iterations on a 4–6 digit PIN = trivially brute-forced from a copy of `pos.db`/backup; failed-attempt counters are in memory (reset on restart); cancellation PIN and deletion password have no lockout; login iterates every user's hash (up to N × PBKDF2 per attempt). | Stronger parameters; persisted counters; store a per-user lookup key. |
| KI-14 | M | `WindowsLicenseManager.kt`, `DeviceFingerprintProvider.kt` | Windows installation id is a **random UUID in `license.dat`**; deleting that file resets the 7-day trial and invalidates the licence. If DPAPI fails the file is written **unprotected**. `clock rollback` tolerance is 5 min on a stored timestamp. Backups do not include the licence. | Product decision (hardware binding vs. recoverability); fail closed if DPAPI errors. |
| KI-24 | L | `BackupRestoreScreen.kt` | Backup ZIP is not encrypted although it contains users' PIN hashes and sales; export help text says `.db` while a `.zip` is produced. | Encrypt or warn; fix copy. |

## D. Structure, duplication, documentation debt

| ID | Sev | Where | Problem |
| :-: | :-: | :--- | :--- |
| KI-21 | L | `DesktopNavGraph.kt` (~2.6k lines), `WindowsPosDatabase.kt` (~2.8k lines) | God object/file with money logic, SQL and UI state mixed; many minified one-liners (`moveOrder`, `sessionQuery`, `upsert`). `PermissionRules` in `sharedLogic` is unused by desktop. Reporting SQL fragments are copy-pasted across `salesSummary`, `todaySalesSummary`, `retailSalesAnalytics`, `salesEvolution`, `sessionHistory`. |
| KI-22 | L | `app/` | Legacy Android module duplicates domain logic (`Step3…Step8UseCases.kt`, `MonetaryUtils.kt`, `SharedBusinessAdapter.kt`) and has its own Room schema; still built in CI and uses the same app id. Risk of drift. |
| KI-23 | L | repo | `vm-deploy/general-pos_1.0.1_amd64.deb` is obsolete; `scripts/README.md` had corrupted emoji (fixed); `tools/license-generator` banner still says "Android"/"MOROCCAN POS"; screenshots/XML dumps at `docs/` root (Android UI dumps) are unreferenced. |

## E. Documentation corrections made in this rewrite (for traceability)

Statements that older docs made and that are **false** in the current code: DB work on `Dispatchers.IO`;
jSerialComm; Ed25519 licences; hardware-derived Windows fingerprint; 2-hour clock guard based on
`MAX(orders.created_at)`; 3-second print dedup; raw Win32 `OpenPrinter`; 6-step vs 4-step setup and
starter categories created by setup; preorder deposit/balance workflow; carnet/QR payment buttons;
owner approval for withdrawals; encrypted/`.db` backups; automatic backup before deletion; "master"
deletion password separate from owner PIN; `order_cancellation_pin_required` / `establishment_ice` /
`printer_paper_width` settings keys; `orders.customNote` column; `categories.parent_id ON DELETE SET NULL`;
Kotlin 2.0.21 / Compose 1.7.0 / sqlite-jdbc 3.46 / coroutines 1.9.0 / JVM 21 toolchain; version also set
in `app/build.gradle.kts` only for Android.
