# 09. Testing & Regression Guidelines

> The test counts below were obtained by counting `@Test` annotations in the source tree. **The suites
> were not executed while writing this documentation**; run them before relying on a green state.

## 1. Commands

```powershell
# shared rules, use cases, VFD formatting/protocol (fast, no UI)
.\gradlew.bat :sharedLogic:desktopTest
# persistence, migrations, navigation/UI state, printing, licensing, backup, import
.\gradlew.bat :desktopApp:desktopTest
# what CI runs (adds a packaged-app build); on Linux headless prefix with xvfb-run -a
.\gradlew.bat :sharedLogic:desktopTest :desktopApp:desktopTest --continue --stacktrace --no-configuration-cache
# legacy Android module (CI only)
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Close running development POS instances before rebuilding runtime outputs. Gradle config cache is
enabled in `gradle.properties` but disabled in CI.

## 2. Test inventory

| Source set | Tests (`@Test`) | Files | Scope |
| :--- | :-: | :-: | :--- |
| `sharedLogic/src/commonTest` | 102 | 15 | `OrderRulesTest`, `MoneyRulesTest`, `PaymentRulesTest`, `RegisterRulesTest`, `PreorderRulesTest`, `SessionClosingReportRulesTest`, `PermissionAndValidationRulesTest`, `CategoryHierarchyRulesTest`, `CategoryPopularityRulesTest`, `AuthenticateUserTest`, `TransactionRunnerContractTest`, `PlatformServicesTest`, `ApplicationIdentityTest`, `CustomerDisplayControllerTest`, `VfdFormattingAndProtocolTest` |
| `desktopApp/src/desktopTest` | 535 | 82 | See table below |
| `app/src/test` | 118 | 19 | Legacy Android JVM tests |
| `app/src/androidTest` | 24 | 14 | Legacy Android instrumentation (not run by CI) |

### Desktop suites worth knowing

| Area | Representative files (`desktopApp/src/desktopTest/kotlin/ma/elaroui/pos/desktop/…`) |
| :--- | :--- |
| Persistence & migrations | `persistence/WindowsPosDatabaseTest`, `UpgradeSafetyTest`, `platform/LegacyMigrationTest`, `platform/WindowsUpdatePersistenceTest` |
| Discount / VAT / revenue | `persistence/DiscountRevenueRegressionTest` (v11→v12 migration keeps historical amounts; held discounts survive restart; mixed-category VAT) |
| Order integrity | `persistence/AuditOrderIntegrityTest`, `PerUserRegisterSessionTest`, `ActiveOrderDateTimeFormatAndPersistenceTest` |
| Deletion & data management | `persistence/SalesDeletionComprehensiveTest`, `DataManagementSecurityAndDeletionTest`, `DataManagementDeepAuditTest`, `DataManagementDesktopDispatcherTest`, `SoftDeleteAndEntityActionsTest`, `GeneralRetailSoftDeleteRegressionTest` |
| Catalogue | `persistence/ProductDuplicateValidationTest`, `BarcodeScanningSystemTest`, `importing/CsvImportsTest`, `OptionalProductAndCategoryImageTest`, `ProductImageFlowTest` |
| End-to-end / audits | `GeneralPosEndToEndAuditTest`, `ProductionReadinessDeepWorkflowTest`, `WindowsPosHardcoreTestSuite`, `PreReleaseProductionAuditTest`, `FinalPreDeliveryProductionAuditTest`, `LatestFeaturesIntegratedDeepAuditTest`, `ProductionCandidateReleaseTest` |
| Presentation | `presentation/SaleConfirmationWorkflowTest`, `SaleCompletedDialogInteractionTest`, `NotificationSystemTest`, `components/TouchInputFieldTest`, `TouchScrollingAuditTest` |
| Printing / display / hardware | `display/CustomerDisplayDesktopIntegrationTest`, `WindowsNativeFileDialogTest` (plus print tests inside the audits) |
| Licensing / backup / platform | `license/WindowsLicenseManagerTest`, `LinuxLicensingTest`, `DesktopLicenseStoreFactoryTest`, `backup/DesktopBackupServiceTest`, `platform/DesktopSingleInstanceLockTest`, `LinuxDesktopStartupTest` |

### Fixtures to be aware of

`WindowsPosDatabase.openInMemory()` is **test-only**: it seeds an owner `Owner`/PIN `1234`, register
`Main Register`, categories `Pâtisserie, Viennoiserie, Gâteaux & Tartes, Boissons`, and (via
`seedLegacyCompatibilityFixturesForTests`) legacy categories, 10 tables and 34 `Legacy Item` products.
Production uses `open(path)` and starts empty. Do not "simplify" these seeds without checking the many
suites that depend on them, and never use `openInMemory()` in production code.

## 3. Regression checklist by change type

| If you change… | You must (re)verify |
| :--- | :--- |
| `OrderRules.kt`, `MoneyRules.kt`, `PaymentRules.kt` | `OrderRulesTest`, `MoneyRulesTest`, `PaymentRulesTest`, `DiscountRevenueRegressionTest`; invariants F1–F12 in 04; receipt and VFD totals; historical orders must not change |
| `PosUseCases.kt` (`CreateOrder`, `CompletePayment`, `Close…`) | `AuthenticateUserTest`, `RegisterRulesTest`, `AuditOrderIntegrityTest`, `PerUserRegisterSessionTest`, `SaleConfirmationWorkflowTest`; idempotency token behaviour; transaction atomicity |
| SQL in `WindowsPosDatabase.kt` | `WindowsPosDatabaseTest`, report tests (`OwnerDashboardStatisticsTest`, `DiscountRevenueRegressionTest`); run against a DB that went through every migration; check `deleted_at IS NULL` filters |
| Migrations (new block 13+) | `UpgradeSafetyTest`, `LegacyMigrationTest`, add a fixture DB at v12 → v13; confirm pre-migration backup is created and `CURRENT_SCHEMA_VERSION` bumped; never edit blocks 1–12 |
| Deletion / data management | `SalesDeletionComprehensiveTest`, `DataManagement*Test`; confirm open sessions/orders, audit rows and the credential check |
| Printing / ESC-POS | print tests in the audit suites; preview text for 58 and 80 mm; accents (CP858); deduplication keys; manual paper/printer test |
| VFD / display | `VfdFormattingAndProtocolTest`, `CustomerDisplayControllerTest`, `CustomerDisplayDesktopIntegrationTest`; ASCII-only output; 20-column width |
| Licensing / security | `WindowsLicenseManagerTest`, `LinuxLicensingTest`; never bake test keys into `PUBLIC_KEY` |
| Packaging (`desktopApp/build.gradle.kts`, `gradle.properties`) | `Test-WindowsInstallerDataSafety.ps1`; manual upgrade-over-existing-data test (08 §5) |
| Localization strings | `DesktopLocalizationTest`; FR/EN/AR parity; RTL layouts |

## 4. Writing tests here

- Prefer the real `WindowsPosDatabase` (in-memory or temp-dir file) over mocks for anything touching SQL.
- Pass a fixed `Clock` (`TestClock`, or an object with `now()`) to use cases instead of `System.currentTimeMillis()`.
- Money assertions are in **centimes** (`Long`); never compare formatted strings for logic.
- Use `DesktopNavState(db, dir, printerServiceOverride = …, vfdTransportOverride = …)` to isolate hardware.
- Known defects (file 10) are **not** pinned by tests; when fixing one, add a test that fails before
  the fix. Do not write a test that asserts the buggy behaviour as correct.
- Hardware (real printer, drawer, VFD, scanner) cannot be covered by CI; record manual results in the PR.
