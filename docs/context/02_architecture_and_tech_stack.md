# 02. Architecture & Technology Stack

## 1. Layering (as it really is)

```
┌──────────────────────────────────────────────────────────────────────┐
│ desktopApp/presentation   Compose screens + DesktopNavState            │
│   DesktopNavGraph.kt (~2.6k lines) = router + UI state + orchestration │
├──────────────────────────────────────────────────────────────────────┤
│ sharedLogic               rules/*  application/PosUseCases.kt          │
│                           domain/* (models, repository contracts)      │
│                           display/* (VFD state machine, formatter)     │
├──────────────────────────────────────────────────────────────────────┤
│ desktopApp/persistence    WindowsPosDatabase.kt (~2.8k lines)          │
│   single JDBC connection, ReentrantLock, SQL strings, migrations,      │
│   repository implementations (inner classes), reports, deletion        │
├──────────────────────────────────────────────────────────────────────┤
│ desktopApp/{print,display,license,backup,security,platform}            │
└──────────────────────────────────────────────────────────────────────┘
```

Important realities (do not rely on idealised "clean architecture" descriptions):

1. **`DesktopNavState` is a god object.** It holds Compose state (`cart`, `currentRoute`, `session`,
   `currentUser`, `discountBasisPoints`, `itemDiscountsBasisPoints`, `pendingOrder`, …), calls the DB and
   use cases directly, drives printing and the customer display, and performs navigation.
   Not every business action goes through a `sharedLogic` use case: cancellation, move-table, deletion,
   reports, PIN management and CSV import call `WindowsPosDatabase` or run inline code in the NavGraph.
2. **Threading model:** most `DesktopNavState` actions (`saveDraft`, `pay`, `printCompletedSaleReceipt`,
   `printOperationalTickets`, `loadSalesHistory`, CSV import…) wrap their work in
   `kotlinx.coroutines.runBlocking`, i.e. they run **synchronously on the UI thread**, including printer
   I/O. A few screens (data management, cancel order, some settings) use `withContext(Dispatchers.IO)`.
   Older documentation claiming "all DB work runs on `Dispatchers.IO`" was wrong. See file 10 (KI-08, KI-09).
3. **Database access** is one shared `java.sql.Connection` guarded by `ReentrantLock(true)`.
   `Transactions.inTransaction` holds that lock across a suspend block (safe today only because callers
   use `runBlocking` on one thread). `transactionBlocking` implements nested transactions with a depth
   counter.
4. **Persistence is hand-written SQL.** There is no ORM; many repository methods are single-line
   minified functions (`moveOrder`, `sessionQuery`, `upsert` …).

## 2. Module layout

```text
patisserie_pos/
├── sharedLogic/src/commonMain/kotlin/ma/elaroui/pos/shared/
│   ├── PlatformContracts.kt            Clock, UUID, file/logger/preferences contracts
│   ├── application/PosUseCases.kt      AuthenticateUser, CreateOrder, CompletePayment,
│   │                                   OpenRegisterSession, CloseRegisterSession,
│   │                                   RecordCashMovement, SaveProduct, ChangeTableStatus
│   ├── domain/DomainModels.kt          User, Category, Product, Order(Line), Payment, RegisterSession…
│   ├── domain/RepositoryContracts.kt   Repository + TransactionRunner interfaces
│   ├── rules/                          MoneyRules, OrderRules, PaymentRules, RegisterRules,
│   │                                   PreorderRules, SessionClosingReportRules,
│   │                                   CategoryHierarchyRules, CategoryPopularityRules,
│   │                                   PermissionAndValidationRules
│   └── display/                        CustomerDisplayController/Models, VfdMessageFormatter,
│                                       VfdProtocolDrivers, VfdTransport
├── desktopApp/src/desktopMain/kotlin/ma/elaroui/pos/desktop/
│   ├── Main.kt, DesktopPosApp.kt, DesktopBuildInfo.kt, DesktopLocalization.kt,
│   │   DesktopPlatformServices.kt        (JvmPlatformLogger → data/logs/application.log)
│   ├── persistence/WindowsPosDatabase.kt, DataManagementModels.kt
│   ├── presentation/                    navigation/, pos/, payment/, register/, sales/, reports/,
│   │                                   management/, settings/, setup/, auth/, dashboard/, license/,
│   │                                   components/ (touch widgets, virtual keyboard, design system)
│   ├── print/                           ESC/POS formatting + Windows/Linux printer services
│   ├── display/                         serial VFD transport (JNA Kernel32 on Windows), settings repo
│   ├── license/                         trial + signature verification, secure stores
│   ├── backup/DesktopBackupService.kt   ZIP backup / staged restore
│   ├── importing/CsvImports.kt          16-column catalogue import analysis
│   ├── security/PinSecurity.kt, AdminDeletionSecurity.kt
│   └── platform/                        paths, single-instance lock, barcode controller, file dialogs
├── app/                                 legacy Android module (not shipped)
├── scripts/                             PowerShell/bash helpers (installer safety test, data cleaning, signing)
├── tools/license-generator/             offline licence signing tool (private key must stay out of Git)
├── examples/import/                     sample CSV material
└── vm-deploy/                           Ubuntu VM helpers and a stale `general-pos_1.0.1` .deb
```

## 3. Technology stack (from `gradle/libs.versions.toml` and module build files)

| Component | Version | Notes |
| :--- | :--- | :--- |
| Kotlin | 2.2.10 | `kotlin-multiplatform`, `kotlin-compose` plugins |
| Compose Multiplatform | 1.9.3 | `compose.desktop.currentOs`, `compose.material3` |
| JVM toolchain | 17 (`jvmToolchain(17)`) | CI also installs 21 |
| SQLite JDBC | `org.xerial:sqlite-jdbc` 3.50.3.0 | WAL, `foreign_keys=ON`, `busy_timeout=5000`, `synchronous=NORMAL` |
| JNA / jna-platform | 5.17.0 | Windows DPAPI (`Crypt32Util`) and serial COM access (`Kernel32`) |
| kotlinx-coroutines | 1.10.1 (core, swing, test) | |
| Packaging | Compose Gradle plugin → jpackage | MSI/EXE (needs WiX 3.x on the build host), DEB. `packageVersion = pos.desktop.version` |
| Bundled JRE modules | `jdk.accessibility`, `java.sql` (+ Compose defaults) | `desktopApp/build.gradle.kts` `modules(...)` |
| Packaged JVM args | `-Xms64m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -DgeneralPos.version=<ver>` | |
| Android module | AGP 9.2.1, Room 2.8.4, Hilt 2.60.1, KSP 2.3.9 | legacy |

**Not used (despite older documentation):** `com.fazecast:jSerialComm` (the VFD uses JNA `Kernel32` on
Windows and a plain file/device stream on Linux), Ed25519 (licences are verified with JDK `Signature`
`SHA256withECDSA`; RSA is accepted if the embedded key is RSA), a pinned WiX version.

## 4. Data flow for a sale (verified)

1. POS screen mutates `DesktopNavState.cart` (`productId → qty`), `discountBasisPoints`,
   `itemDiscountsBasisPoints`. Each change calls `syncCustomerDisplayCart()` → VFD `DuringSale`.
2. `createOrder()` → `saveDraft(true)` runs `CreateOrder.execute(...)` (validates session, products,
   computes totals via `OrderCalculationRules.calculate`, stores per-line recognized amount/tax) →
   `orders.save()` writes `orders` + `order_items` in one transaction. Order type is always `COUNTER`
   for new sales and the number is `SALE-<epochMillis>`.
3. Payment screen → `pay(method, received)` → `CompletePayment.execute` inside
   `db.transactions.inTransaction`: dedupe on `(orderId, token)`, `PaymentRules.calculate`,
   insert `payments` row, `orders.save(status = COMPLETED)`.
4. UI then optionally pulses the drawer (cash + `cash_drawer_enabled`), shows the completed-sale
   dialog, and prints the receipt via `submitAutomaticDocument` (deduplicated by key).
5. VFD `PaymentCompleted` ("thank you") reverts to `Idle` after `thankYouDurationSeconds` (default 5, clamped 1..60).
