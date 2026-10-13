# AGENTS.md — Operating instructions for AI coding agents

PATISSERIE_POS is an **offline point-of-sale for pastry shops** (Morocco, MAD). It handles real money,
fiscal receipts and customer data. Read this file fully, then the context documents relevant to your task,
**before** inspecting or changing code. The code is the source of truth; the documents were verified
against it, and where they disagree the code wins (and the document must be fixed).

## 1. What this repository is (60-second version)

- **Shipped product:** `:desktopApp` — Compose Multiplatform Desktop (Windows MSI/EXE, Linux DEB),
  SQLite via JDBC, ESC/POS printing, serial VFD display, keyboard-wedge barcode scanner, offline licence.
- **Shared logic:** `:sharedLogic` — Kotlin Multiplatform rules and use cases (no UI, no JDBC).
- **Legacy / not shipped:** `:app` (old Android + Room). Do not port behaviour from it and do not assume it
  matches desktop.
- Version `1.2.9` (`gradle.properties`), DB schema version `12`, Kotlin 2.2.10, Compose MP 1.9.3, JVM 17.
- Biggest files: `desktopApp/.../presentation/navigation/DesktopNavGraph.kt` (router + UI state + much
  orchestration) and `desktopApp/.../persistence/WindowsPosDatabase.kt` (all SQL, migrations, reports).

## 2. Mandatory reading map

Start with [`docs/context/00_index.md`](./docs/context/00_index.md). Then, by task:

| Your task touches… | Read first |
| :--- | :--- |
| Anything / orientation | `01_project_overview.md` (what is and is **not** implemented) |
| Module boundaries, threading, dependencies | `02_architecture_and_tech_stack.md` |
| SQL, schema, migrations, `settings` keys, backup/restore | `03_database_schema.md` |
| Totals, discounts, VAT, payments, sessions, cancellation, deletion | `04_workflows_and_business_rules.md` (**financial invariants F1–F12**) |
| Routes, role guards, screens | `05_screen_specifications.md` |
| Printing, drawer, VFD, scanner | `06_hardware_printing_display.md` |
| Licence, PIN/password handling, secrets | `07_licensing_and_security.md` |
| MSI/DEB, installer, versioning, release | `08_windows_packaging_and_devops.md` (+ `docs/WINDOWS_INSTALLER_DATA_SAFETY.md`) |
| Writing/running tests, regression scope | `09_testing_and_regression.md` |
| **Before touching anything money/security related** | `10_known_issues_and_technical_debt.md` |
| CSV import | `docs/CSV_IMPORT_GUIDE.md` |
| Colours / visual style | `docs/DESIGN.md` |

`docs/archive/` is **history, not truth** — never use it to justify behaviour.

## 3. Non-negotiable rules

### 3.1 Customer data safety
- Customer data: `%LOCALAPPDATA%\PATISSERIE_POS\` (`data\pos.db`, `data\images`, `data\backups`,
  `data\secure\license.dat`, `config\`). Binaries: `%LOCALAPPDATA%\PATISSERIE_POS_APP\`.
- **Never** add installer directives, Gradle tasks, scripts or app code that delete, overwrite or package
  anything under the customer-data tree. Never change `installationPath`, `perUserInstall` or
  `upgradeUuid` (`46788696-3912-4d20-9f7d-220b62935d31`) without the safety script (08 §3).
- After building any MSI run:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\Test-WindowsInstallerDataSafety.ps1 -MsiPath "<msi-path>" [-PreviousMsiPath "<previous-safe-msi>"]
  ```
- Never run `scripts/clean_pos_data.*` against real customer data; never commit databases, licences,
  private keys or certificates.

### 3.2 Money
- Only `Long`/`Int` **centimes**; rates and discounts in **basis points**. No `Float`/`Double` in money
  paths (a known violation exists in CSV import — do not copy it, KI-06).
- Use `MoneyRules`, `MathRules` (overflow-checked) and `OrderCalculationRules`; never re-implement
  totals/VAT in UI code. The cart, VFD, order creation and receipts must all use the same calculation.
- Invariants F1–F12 (04 §0) must hold; historical rows (pre-v12 with NULL `recognized_*`) must keep
  reporting the same amounts.
- Do not "fix" rounding/VAT behaviour silently: it changes fiscal totals. See KI-17.

### 3.3 Database & migrations
- Never edit migration blocks `1..12` in `WindowsPosDatabase.migrate()`. A schema change is a **new**
  block 13+, bumps `CURRENT_SCHEMA_VERSION`, writes `schema_migrations`, is additive/backwards compatible
  and never drops user tables/data. The pre-migration `VACUUM INTO` backup logic must keep working.
- Update `docs/context/03_database_schema.md` and add an upgrade test (`UpgradeSafetyTest` style) in the
  same change.
- All access goes through the shared connection/lock; do not open a second `Connection` to `pos.db`.

### 3.4 Access control
- Owner-only screens are listed in `isOwnerRoute(route)`; navigate with `navigateTo` (it enforces the
  guard), never by assigning `currentRoute` for owner routes.
- The guard is route-level only. New **actions** must re-check the role. Cashiers must never change
  prices, see owner reports, change settings or delete data.
- Destructive operations must require a credential and write an audit row; and (target behaviour, KI-12)
  should be preceded by a backup. Do not weaken `verifyAdminDeletionPassword` further.

### 3.5 Hardware text
- VFD output is ASCII, 20 columns ×2 lines: always go through `VfdMessageFormatter`
  (`transliterateAccents/fitLine/centerLine/formatKeyValue`).
- Receipts: CP858 via `FrenchEscPosEncoder` / `EscPosCommands.INIT_CP858`; Arabic is not printable on the
  raw ESC/POS path.
- Printing goes through `PrinterService` and the `PrintJobDeduplicator`; do not call printers directly.

## 4. Do not assume these exist (they do not)

Stock/inventory quantities · a working preorder UI/deposit flow · carnet/QR payment UI or customer
ledger · refunds/returns/voids · split payments · owner PIN approval for cash withdrawals · automatic
backup before data deletion · sequential receipt numbers · hardware-derived Windows device id · Ed25519
licences · jSerialComm · `Dispatchers.IO` for most DB work. Details and ids: file 10.

## 5. How to work

1. Identify the area (table above) and read the matching documents and the source files they cite.
2. Make the **smallest** change that fits the surrounding style (the existing code is dense; match its
   idioms and naming; French/English/Arabic UI strings use the `strings.text("fr","en","ar")` pattern).
3. Keep business logic in `sharedLogic` rules/use cases where it already lives; do not add more logic to
   `DesktopNavState` unless unavoidable.
4. Add or update tests (09 §3 tells you which). Prefer real in-memory `WindowsPosDatabase` over mocks.
5. Run, and report honestly (including failures):
   ```powershell
   .\gradlew.bat :sharedLogic:desktopTest
   .\gradlew.bat :desktopApp:desktopTest
   ```
   For a Windows package: `.\gradlew.bat :desktopApp:packageMsi` then the installer safety script.
6. **Update the documentation in the same change** (the matching `docs/context/*` file; and file 10 when you
   discover or fix a defect). Never document planned behaviour as implemented. Never state test results you
   did not obtain.
7. Do not bump the application version, change Gradle/build configuration, or touch unrelated files
   unless the task is explicitly that.

## 6. Conventions

- Language: Kotlin, 4-space indentation, JVM 17 toolchain. Package root `ma.elaroui.pos` (`.shared`,
  `.desktop`).
- Timestamps are epoch milliseconds (`Long`). IDs are `Long`; new ids via `db.nextId(table)` /
  autoincrement.
- Soft-delete (`deleted_at`) is the norm for catalogue/user entities; always filter `deleted_at IS NULL`.
- Settings are string key/values in `settings`; new keys must be listed in `03_database_schema.md`.
- Errors shown to cashiers should be localized; do not surface enum names or stack traces (KI-10).
- Secrets and PINs: never log, never put in URLs/exception messages.
- The working tree may contain **uncommitted changes made by others**; check `git status`/`git diff`
  and never revert or reformat files you were not asked to touch.

## 7. Quick command reference

| Purpose | Command |
| :--- | :--- |
| Run app (dev) | `.\gradlew.bat :desktopApp:run` |
| Shared tests | `.\gradlew.bat :sharedLogic:desktopTest` |
| Desktop tests | `.\gradlew.bat :desktopApp:desktopTest` |
| Windows MSI / EXE | `.\gradlew.bat :desktopApp:packageMsi` / `:desktopApp:packageExe` |
| Unpacked app (CI) | `.\gradlew.bat :desktopApp:createDistributable` |
| Linux DEB | `./gradlew :desktopApp:packageDeb` |
| MSI data-safety audit | `scripts\Test-WindowsInstallerDataSafety.ps1 -MsiPath <msi> [-PreviousMsiPath <msi>]` |
