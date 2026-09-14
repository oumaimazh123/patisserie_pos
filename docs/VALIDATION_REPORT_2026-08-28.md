# POS_CR Android and Windows validation — 2026-08-28

## Environment

- Host: Windows 11, build 22631.
- Android runtime: API 35 `POS_CR_Medium_Tablet_API35` AVD, 2560 × 1600 landscape.
- Android clean state: application data cleared before setup.
- Windows clean state: isolated `LOCALAPPDATA` under `.validation/desktop-clean-localappdata`.
- Windows existing state: existing `%LOCALAPPDATA%/CafeRestaurantPOS/data/pos.db` preserved.

## Automated results

| Suite | Passed | Failed | Skipped |
|---|---:|---:|---:|
| Shared KMP tests | 29 | 0 | 0 |
| Android JVM unit tests | 113 | 0 | 0 |
| Android connected instrumentation/Compose/Room tests | 22 | 0 | 0 |
| Windows desktop/database/licensing/printing tests | 21 | 0 | 0 |
| Total | 185 | 0 | 0 |

Additional successful tasks:

- Android debug lint: successful; 0 errors, 117 warnings, 1 hint.
- Android debug APK assembly.
- Windows unpacked distributable.
- Windows EXE packaging.
- Windows MSI packaging.

The lint warnings are non-blocking: 75 unused-resource warnings, 29 `UseKtx`
suggestions, dependency/tooling update notices, one old-target notice, and small
style hints. Room compilation also recommends adding an index for
`RegisterSession.openedByUserId`; this is a performance risk rather than a
functional failure and needs a deliberate schema migration.

## Runtime results

- Android cold clean launch: passed.
- Android first setup and owner creation: passed in French.
- Android 4-digit PIN login and owner dashboard: passed.
- Android existing-data cold restart: passed.
- Android seven-day demo banner: displayed 7 days on clean start.
- Windows isolated clean launch: passed and created SQLite plus secure licence store.
- Windows current build with existing database: passed.
- Windows unpacked native runtime launch: initially failed because `java.sql` was absent; fixed and passed after rebuild.

## Bugs and fixes

| Bug | Severity | Result |
|---|---|---|
| Native Windows runtime omitted `java.sql`, so SQLite startup caused “Failed to launch JVM”/headless launcher processes | Blocker | Fixed in `desktopApp/build.gradle.kts`; distributable, EXE and MSI rebuilt and runtime launch verified |
| Android backup test did not explicitly prove device-bound licence preferences were excluded | High | Regression assertion added; connected test passed |
| Windows licensing lacked explicit restart/trial expiry/corruption/clock rollback coverage | High | Regression test added; desktop suite passed |
| Android setup language selection did not apply English; setup remained French and summary displayed `Langue: FR` | High | Confirmed, not changed in this broad validation turn; requires focused setup localization/state fix and UI test |

## Android / Windows parity

| Feature | Android | Windows | Logic difference | UI difference | Severity | Fix status |
|---|---|---|---|---|---|---|
| Clean launch and initial setup | Pass | Pass | Windows setup stores fewer optional legal fields directly from the wizard | Layouts are similar but not identical | Medium | Partial parity |
| Language selection | FR works; EN selection failed during setup | FR/EN/AR string layer and RTL direction exist | Android setup does not persist the selected EN value observed at runtime | Both setup wizards still need full manual AR/EN review | High | Open |
| Owner creation and 4–6 digit PIN | Pass; unit/UI coverage | Pass; shared rules and desktop tests | No intended difference | Desktop keypad/form presentation differs | Low | Core parity |
| User selection/login/lock | Pass | Pass | Equivalent owner/cashier role gate | Visual density differs | Low | Core parity |
| Demo period and persistence | Pass | Pass | Separate platform secure stores and device identifiers | Banner styling differs | Low | Tested |
| Valid/invalid/wrong-device/expired/corrupt licence | Pass | Pass | Android has more granular expiring-soon state | Management screens differ | Medium | Logic tested |
| Clock rollback | Pass | Pass | Same five-minute tolerance intent | No material UI comparison completed | Low | Tested |
| Backup must not clone licence | Pass; explicit assertion | Pass by design: backup is database-only, secure licence file is outside DB | Platform backup formats differ | File pickers differ | Low | Tested |
| Starter café catalogue | 7 categories, 34 products, area and 10 tables tested | Equivalent SQLite seed tested | Separate persistence implementations | Product management UI remains less polished on Windows | Medium | Core data parity |
| Active product/category filtering | Pass | Pass | Shared validation consumed by Windows; Android remains reference | Card layouts differ | Low | Tested |
| Product images and fallback | Pass | Pass | Android URI handling vs Windows copied file paths | Windows image picker/preview differs | Medium | Functional parity |
| Cart quantities/removal | Pass | Pass | No known rule difference | Windows cart has fewer secondary details | Low | Tested |
| Product notes | Pass | Incomplete | Windows order line model/UI does not yet expose full notes workflow | Missing Windows notes control | Medium | Open |
| Dine-in without table | Pass | Pass | No difference | Selection dialogs differ | Low | Tested |
| AVAILABLE-only tables and occupancy | Pass | Pass | No known difference | Windows table cards differ | Low | Tested |
| Hold/resume active order | Pass | Pass | Windows uses SQLite order persistence and restores cart | Windows resume presentation differs | Low | Tested |
| Cash/card payment and change | Pass | Pass | Shared money rules; platform transactions differ | Payment layouts differ | Low | Tested |
| Duplicate payment protection | Pass | Pass | Separate Room/SQLite transactions | No material difference | Blocker | Tested |
| Cancellation with reason | Pass | Pass core | Windows preset/description parity is incomplete | Dialog content differs | Medium | Partial |
| Cash entry/withdrawal | Pass | Pass core | Windows approval PIN is configurable; reason-list parity remains incomplete | Dialogs differ | Medium | Partial |
| Register open/close/expected cash | Pass | Pass | Separate persistence adapters | Windows detail/history is less extensive | Medium | Partial |
| Sales/history/report date filters and stored TVA | Pass | Pass | Windows lacks some ranking/export functions | Report screen structure differs | Medium | Partial |
| Category/product/user/table management | Pass | Pass core | Windows search/bulk controls are incomplete in some screens | Android is more complete/polished | Medium | Partial |
| Settings/company/invoice fields | Pass | Pass core | Windows setup wizard does not collect every field, but settings screen persists them | Different form grouping | Medium | Partial |
| Receipt preview | Pass | Pass | Separate renderers | Layouts are not pixel-identical | Medium | Core parity |
| Physical 58/80 mm printing | Not exercised in this run | Not exercised in this run | Hardware adapters differ | N/A | High | Hardware QA required |
| Database reopen/migration | Room tests pass | SQLite reopen/transaction/upgrade foundation tests pass | Different database technologies | N/A | Medium | Automated foundation passed |
| Windows installer upgrade over older version | N/A | Not executed; older installed launcher was broken, current rebuilt distributable works | Installer-over-installer preservation remains unproven | N/A | High | Open acceptance test |
| Accessibility/keyboard/full RTL workflow | Partial automated coverage | Partial automated coverage | Different platform focus systems | Manual audit incomplete | Medium | Open |

## Licence coverage

Android tests cover valid activation, signature tampering, wrong device, expired subscription,
new seven-day trial, expiring-soon state, trial expiry without data reset, clock rollback,
embedded public key, activation persistence, malformed activation, and corrupted stored licence.

Windows tests cover seven-day trial creation, stable installation ID across restart, valid device-bound
activation and persistence, wrong device, expiration, trial expiration, corrupted storage, and clock rollback.

Not fully executed: a real signed production licence generated by the external private key, moving an
installed customer database between two physical Windows machines, and installer-over-installer licence
preservation. Automated backup coverage confirms that the Android licence preference is not restored from
a business-data backup; Windows backups exclude the secure licence file by design.

## Acceptance verdict

Android is suitable for a controlled client acceptance test, with the setup-language defect disclosed.

Windows is suitable for internal and controlled client acceptance testing using the rebuilt artifacts,
but is not ready for unrestricted production delivery until the Android/Windows parity gaps above,
physical printer testing, Windows 10 testing, installer upgrade testing, and Authenticode signing are complete.
