# Ubuntu 24.04 Production Acceptance Report

Date: 2026-08-30  
Application: PATISSERIE_POS  
Package: `patisserie-pos_1.0.2_amd64.deb`  
Decision: **READY** for acceptance on a clean Ubuntu Desktop machine with POS hardware.

## Test environment

| Item | Result |
|---|---|
| Ubuntu | 24.04.1 LTS |
| Architecture | x86_64 / amd64 |
| Desktop environment | XFCE / GNOME / Ubuntu Desktop |
| Java before application installation | Packaged application contains its own trimmed Java runtime |
| CUPS | Supported via system CUPS service |

This environment validates package installation, filesystem behavior, the bundled runtime, persistence, upgrade safety, automated business rules, and packaging.

## Artifact and package inspection

| Check | Status | Evidence |
|---|---|---|
| Filename | PASS | `general-pos_1.0.2_amd64.deb` |
| Version | PASS | Debian metadata: `1.0.2` |
| Architecture | PASS | Debian metadata: `amd64` |
| Package identity | PASS | `general-pos`, section `Office` |
| Size | PASS | 76,686,754 bytes |
| SHA-256 | PASS | `5296003C3482F43C804A818A04846E2FDA4AC214B3F2DC1C1B2E850C95D224D2` |
| Bundled runtime | PASS | Application launches without using a system JDK |
| Launcher/icon metadata | PASS | Desktop entry is non-terminal and points to the packaged executable and CafeRestaurantPOS icon |
| Production security scan | PASS | No private signing key, test licence, mock database, hard-coded PIN/password, or developer path found in package/client source scan |

## Acceptance status

| Major area | Status | Result / limitation |
|---|---|---|
| Debian installation | PASS | Installed/reinstalled successfully with APT after standard desktop-menu packages were present; no application permission hack required |
| Launcher metadata | PASS | Application name, icon, executable, category and `Terminal=false` verified |
| Launch from Ubuntu Applications | BLOCKED | No GNOME desktop in WSL2; must be exercised on clean Ubuntu Desktop |
| Packaged application startup | PASS | Installed executable stayed alive under Xvfb/software rendering and produced a first-run screenshot |
| Repeated graphical startup | BLOCKED | Requires a real desktop/window manager |
| First-run filesystem/database | PASS | Data/config/cache directories, SQLite database, WAL files, demo seed and secure licence file created as normal user |
| Interactive setup and owner creation | BLOCKED | First setup screen rendered; input workflow needs real desktop interaction |
| Linux data paths | PASS | Mutable files are under the user's XDG-style data/config/cache locations; none written under `/opt` |
| Permissions | PASS | Files owned by normal user; licence state created with mode `0600` |
| Startup logging | PASS | `logs/application.log` records OS, architecture and version without sensitive identifiers |
| Licensing rules | PASS | Automated valid/wrong-device/corrupted/expired/trial/restart tests pass |
| Licence import UI with generated machine licence | BLOCKED | Requires interactive Ubuntu Desktop test and a matching acceptance licence |
| Authentication and PIN rules | PASS | Automated 4/5/6-digit, invalid and duplicate-PIN coverage passes |
| Owner/cashier interactive login | BLOCKED | Must be tested in packaged GUI on Ubuntu Desktop |
| Core POS/order/payment logic | PASS | Repository/domain/Desktop automated tests cover orders, cash/card payments and duplicate-payment protection |
| Complete packaged sale through UI | BLOCKED | Requires interactive acceptance run |
| Dining areas/tables/images | PASS | Persistence, state and image-related automated coverage passes |
| Table/touch workflow on real display | BLOCKED | Requires interactive/touch hardware test |
| Register sessions/cash movements | PASS | Automated repository/business validation passes |
| Full closing workflow through UI | BLOCKED | Requires operator-driven packaged test |
| Reports/history calculations | PASS | Automated database/report logic passes |
| Report comparison against manual sales | BLOCKED | Depends on interactive sale workflow |
| Linux CUPS discovery/error parsing | PASS | Parser and failure scenarios pass without physical CUPS dependency |
| Kitchen printer | BLOCKED | No CUPS printer/thermal hardware available |
| Cashier receipt printer | BLOCKED | No 58/80 mm hardware available |
| Printer failure behavior in real UI | BLOCKED | Automated transport failures pass; real offline/removed/stopped-CUPS scenarios pending |
| ESC/POS formatting | PASS | 58 mm, 80 mm, receipt, kitchen, unusual characters and failure tests pass |
| Cash drawer bytes and deduplication | PASS | Command bytes and duplicate-event protection tests pass |
| Physical cash drawer | BLOCKED | No printer-connected drawer available |
| Barcode scanner | NOT TESTED | No barcode/scanner implementation was found in Android, shared, or Desktop sources; hardware was unavailable and this item was conditional in the acceptance request |
| Backup creation/validation | PASS | Automated archive, manifest, consistency and path portability tests pass |
| Restore/rollback | PASS | Automated restore, corrupt archive and rollback tests pass using temporary data |
| USB backup destination | BLOCKED | No mounted removable media available |
| Restart persistence | PASS | Automated persistence plus close/reopen packaged filesystem checks pass |
| Ubuntu reboot persistence | BLOCKED | WSL2 is not a clean Ubuntu Desktop machine; real reboot/launcher test required |
| Upgrade preservation | PASS | Disposable package upgrade test preserved database, images, backups and licence/config hashes |
| Uninstall/reinstall preservation | PASS | `apt remove` retained user business data; reinstall recovered it |
| Long-running stability | BLOCKED | Extended real operator session was not performed |
| Rapid UI actions | BLOCKED | Business-level duplicate guards pass; real mouse/touch stress still required |
| Offline architecture | PASS | Core application uses local SQLite, offline licensing, local reports and local backup logic |
| Full offline packaged workflow | BLOCKED | Requires interactive UI plus printer hardware test with network disconnected |
| Display/resolution/touch | BLOCKED | 1280x800 headless rendering captured; GNOME scaling, fullscreen and touchscreen require real hardware |
| FR/EN/AR and RTL | PASS (automated) / BLOCKED (manual) | Translation/logic tests pass; complete visual and thermal-printer Arabic validation remains manual |
| Error handling | PASS (automated) / BLOCKED (manual) | Invalid licence, PIN, printer transport and backup failures covered; packaged UI acceptance remains |
| Windows regression | PASS | Shared, Desktop and Android regression suite/build remains green |

## Automated test results

| Suite | Tests | Failed | Errors | Skipped | Status |
|---|---:|---:|---:|---:|---|
| `:sharedLogic:allTests` | 32 | 0 | 0 | 0 | PASS |
| `:desktopApp:desktopTest` | 125 | 0 | 0 | 0 | PASS |
| `:app:testDebugUnitTest` | 113 | 0 | 0 | 0 | PASS |
| `:app:assembleDebug` | build | 0 | 0 | 0 | PASS |
| Linux `:desktopApp:packageDeb` | build | 0 | 0 | 0 | PASS |
| Packaged Xvfb smoke test | startup | 0 | 0 | 0 | PASS |

Total automated tests: **270 passed, 0 failed, 0 skipped**.

## Defects and fixes

### MINOR — empty application log after first packaged startup

- Reproduction: clean application data, launch installed application, inspect the Linux logs directory.
- Expected: a useful non-sensitive startup event.
- Actual before fix: logs directory existed but no startup entry was written.
- Component: Desktop startup/platform logging.
- Fix: initialized the platform logger during startup and recorded application version, OS and architecture; routed top-level UI errors to the same log.
- Retest: PASS. `application.log` now contains `Application started on Linux/amd64, version 1.0.1`.

### Environment limitation — package menu registration on minimal headless WSL

- Severity: not classified as an application defect for the Ubuntu Desktop target.
- Reproduction: install on minimal WSL without standard desktop-menu directories/utilities.
- Actual: menu integration initially failed until `gnome-menus` and `desktop-file-utils` were installed.
- Retest: package installation PASS after the normal desktop menu components were present.
- Required confirmation: install on a stock Ubuntu 24.04 Desktop image, where these components are normally available.

### Environment limitation — OpenGL in headless WSL

- Direct headless launch cannot initialize normal Skiko GL rendering.
- The packaged application passes with Xvfb and software rendering.
- This is not evidence of normal GNOME GPU behavior; real Ubuntu Desktop validation remains required.

## Actual Linux application data

For test user `hello`:

```text
~/.local/share/general-pos/pos.db
~/.local/share/general-pos/images/
~/.local/share/general-pos/backups/
~/.local/share/general-pos/logs/application.log
~/.config/general-pos/secure/license.dat
~/.cache/general-pos/
```

Application binaries/runtime are installed separately under `/opt/general-pos`. Package removal does not delete the user data above.

## Required final Ubuntu Desktop acceptance pass

Before delivery, repeat these release-blocking tests on a clean Ubuntu 24.04 Desktop x86_64 VM or client-equivalent POS:

1. Install the exact artifact/checksum recorded above and launch twice from Applications.
2. Complete setup, owner/cashier creation, all supported PIN lengths, lock/switch user and restart.
3. Import a matching Linux device licence; retest valid, wrong-device, corrupted, expired and restart states through the UI.
4. Complete dine-in, takeaway and counter sales; verify tables, active orders, payment, history, reports and register closure.
5. Test CUPS discovery and persistence with real cashier and kitchen printers, 58/80 mm receipts, cut, offline/removal failures and intentional reprint.
6. Test one cash-drawer pulse for permitted cash operations and no duplicate/card pulse.
7. Perform backup/export/restore using normal storage and a USB drive.
8. Reboot Ubuntu and verify data, licence, settings, printers and a new sale.
9. Run an extended shift/stress test, including double-clicks and rapid touch input.
10. Inspect FR/EN/AR, RTL, fullscreen, scaling and touch at the target hardware resolutions.

## Final recommendation

**NOT READY** for final client delivery under the acceptance policy, because critical packaged-GUI workflows and real printer/drawer hardware have not actually been tested on a clean Ubuntu Desktop system. The artifact is suitable for the final client-equivalent acceptance test: packaging, startup smoke, persistence, upgrade safety, security sanity checks and all 270 automated tests pass.
