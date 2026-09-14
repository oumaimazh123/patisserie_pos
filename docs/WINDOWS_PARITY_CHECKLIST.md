# Android / Windows Feature Parity Checklist

Status values: **Completed**, **Partial**, **Missing**, **Hardware validation**.

| Feature / workflow | Android | Windows | Missing | Completed |
|---|---|---|---|---|
| First-run establishment and owner setup | Complete | Complete | Expanded invoice fields in setup; Android EN selection bug confirmed | Yes, core |
| User selection and PIN login | Complete | Complete | Numeric keypad polish | Yes, core |
| Lock and switch user | Complete | Complete | — | Yes |
| Owner/cashier route guards | Complete | Complete | Per-action denial messages | Yes, core |
| Seven-day trial and activation | Complete | Complete | Activation copy/localization | Yes, core |
| Device-bound offline validation | Complete | Complete | Cross-machine acceptance test | Yes, automated |
| Owner dashboard | Complete | Partial | Dashboard cards and shortcuts | No |
| POS active category/product filtering | Complete | Complete | — | Yes, inactive/unavailable records rejected by UI and use case |
| Product search and category filter | Complete | Complete | Keyboard search action | Yes, core |
| Cart quantities and removal | Complete | Complete | Item notes | No |
| Dine-in/takeaway/counter | Complete | Complete | — | Yes |
| Optional table selection | Complete | Complete | — | Yes, optional dine-in and AVAILABLE-only selection tested |
| Hold/resume active order | Complete | Complete | — | Yes, SQLite persistence and cart restoration tested across reopen |
| Active orders search/type filters | Complete | Complete | Date/cashier filters are history-only | Yes |
| Move active order to table | Complete | Complete | — | Yes, tested |
| Cancel order with required reason | Complete | Complete | Preset-reason localization | Yes, core |
| Cash/card payment | Complete | Complete | Payment success/detail screen | Yes, core |
| Duplicate payment prevention | Complete | Complete | — | Yes, tested |
| Completed sales history | Complete | Complete | — | Yes, tested |
| Sale detail | Complete | Partial | Persisted cancellation reason in detail | Core detail completed |
| Receipt preview/reprint | Complete | Partial | Reprint from historical-sale detail | Preview completed |
| Category CRUD/search/toggle | Complete | Partial | Search | Edit/toggle completed |
| Product CRUD/search/filter/toggle | Complete | Partial | Search plus availability/TVA controls | No |
| Catalogue preview | Complete | POS grid only | Dedicated owner preview | Partial |
| Dining-area CRUD | Complete | Complete | — | Yes |
| Table CRUD/bulk create | Complete | Partial | Edit/toggle/bulk UI | No |
| Cashier create/reset PIN/toggle | Complete | Complete | Fine-grained custom permissions (roles remain enforced) | Yes, core |
| Register opening | Complete | Complete | Cashier selector behavior | Yes, core |
| Current-session cash summary | Complete | Partial | Live cash sales/in/out/expected breakdown | No |
| Cash movement reasons/descriptions | Complete | Partial | Preset lists and Other-description parity | Withdrawal approval PIN enforced and configurable |
| Register closing and difference | Complete | Complete | Closing note/owner approval detail | Partial |
| Register-session history | Complete | Partial | Separate cashier dropdown and detail dialog | Search/state/detail summary completed |
| Daily sales report | Complete | Partial | Product ranking/export | Date presets now filter persisted orders/payments and use stored TVA; tested |
| Establishment/invoice settings | Complete | Complete | — | Yes, persistence tested |
| Customer/kitchen printer configuration | Complete | Complete foundation | Physical printer validation/test buttons | Hardware validation |
| 58mm/80mm receipt/kitchen output | Complete | Complete foundation | Physical hardware QA | Layout/preview tested |
| Backup and restore | Complete | Complete | File-picker UX and restart prompt | Yes, core |
| Product images | Complete | Complete | — | Yes, image selection, storage in dataDir, POS card header display, and invalid-path fallback |
| Dining area images | Complete | Complete | — | Parity Complete — select, managed local copy, persistence, preview, replace/remove, fallback, restart and backup/restore |
| French localization | Complete | Complete | Manual full-workflow copy review | User-facing desktop labels routed through centralized strings |
| English localization | Complete | Complete | Manual full-workflow copy review | User-facing desktop labels routed through centralized strings |
| Arabic and RTL | Complete | Partial | Manual bidi QA for every dialog/printer preview | Arabic strings and RTL layout direction implemented and automated switching tested |
| Empty/loading/error/success states | Complete | Partial | Per-screen empty/loading and dialogs | No |
| Keyboard/touch accessibility | Complete | Partial | Focus order, shortcuts, 48dp audit | No |
| Windows SQLite upgrades | N/A | Complete foundation | Multi-version upgrade fixtures | Partial |
| Windows 10 acceptance | N/A | Not tested | Windows 10 VM/hardware | External: unavailable on host |
| Windows 11 acceptance | N/A | Build 22631 | Full manual workflow matrix | Automated tests/build passed |
| Signed EXE/MSI | N/A | Signing pipeline prepared | Authenticode certificate/timestamp URL | External |

## Definition of parity

A row is only marked fully completed when the Windows workflow has equivalent
validation, permission enforcement, persistence, error handling, and tests—not
merely a visible navigation entry.

## External acceptance evidence — 2026-08-28

- Build host: Windows 11 Home 10.0.22631.
- Windows printer enumeration is unavailable on this host (`Win32_Printer` is
  not installed/exposed), so no real 58/80 mm printer claim is made.
- No Current User code-signing certificate is installed. The guarded script
  `scripts/sign-windows-release.ps1` signs and verifies EXE/MSI only after a
  certificate and timestamp service are supplied.
- Database, settings, and user preservation across close/reopen and
  backup/restore are automated. A genuine installer-over-installer run still
  requires an older installed MSI and cannot be inferred from a unit test.
- Blocking Antigravity findings were regression-tested: optional table dine-in,
  AVAILABLE-only tables, held-order restoration, catalogue validation, filtered
  reports with stored TVA, and explicit (non-fake) repository implementations.
- Full validation found and fixed a native-runtime blocker: the packaged JRE
  omitted `java.sql`, preventing SQLite startup. The rebuilt distributable,
  EXE, and MSI include it and the unpacked runtime now launches successfully.
- Detailed evidence and the requested seven-column parity matrix are in
  `docs/VALIDATION_REPORT_2026-08-28.md`.
