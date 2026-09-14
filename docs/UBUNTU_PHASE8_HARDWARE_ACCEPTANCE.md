# Phase 8 — Ubuntu Hardware Acceptance and Delivery Readiness

Date: 2026-08-30  
Application version: `1.0.2`  
Artifact: `desktopApp/build/compose/binaries/main/deb/general-pos_1.0.2_amd64.deb`  
Size: 76,686,754 bytes  
SHA-256: `5296003C3482F43C804A818A04846E2FDA4AC214B3F2DC1C1B2E850C95D224D2`

## Final status

**NOT READY FOR CLIENT DELIVERY**

Essential client hardware and interactive production workflows have not been tested on a real or equivalent Ubuntu Desktop POS. Per the acceptance policy, automated formatter/transport tests and a headless WSL smoke test cannot be reported as physical hardware acceptance.

## Environment actually available

| Item | Observed |
|---|---|
| Operating system | Ubuntu 24.04.1 LTS under WSL2 |
| Kernel | `5.15.146.1-microsoft-standard-WSL2` |
| Architecture | `x86_64` |
| Installed package | `general-pos 1.0.2` |
| Ubuntu desktop/launcher session | Not available |
| CUPS / `lp` / `lpstat` | Not available |
| Receipt printer | Not available |
| Kitchen printer | Not available |
| Cash drawer | Not available |
| Barcode scanner | Not available |
| Touchscreen | Not available |
| Removable USB test storage | Not available to the Ubuntu test user |

## Acceptance matrix

| Test | Status | Evidence / reason |
|---|---|---|
| Clean `.deb` installation | PASS | Package installed successfully in disposable Ubuntu 24.04 WSL environment; normal user data requires no permission workaround |
| Launch from Ubuntu Applications | BLOCKED | No GNOME/Ubuntu desktop application launcher available |
| Packaged executable startup | PASS | Installed application stayed alive under Xvfb/software rendering; startup logged twice successfully |
| Licensing/device fingerprint rules | PASS (automated) | Linux deterministic fingerprint, valid, expired, corrupted, missing/wrong-device and persistence tests pass |
| Licence import on target machine | BLOCKED | No interactive Ubuntu Desktop and no matching hardware acceptance licence |
| Owner/cashier login and PIN rules | PASS (automated) | 4–6 digit and duplicate PIN coverage passes |
| Owner/cashier packaged UI login | BLOCKED | No real interactive Ubuntu desktop session |
| Product/order calculations | PASS (automated) | Desktop/shared repository and business tests pass |
| Full packaged order workflow | BLOCKED | Interactive POS acceptance unavailable |
| Dine-in/takeaway | PASS (automated) / BLOCKED (manual) | Rules covered; real UI workflow not exercised |
| Cash/card payments | PASS (automated) / BLOCKED (manual) | Calculations/persistence covered; operator workflow not exercised |
| Duplicate-payment protection | PASS (automated) | Duplicate transaction guards covered by tests |
| Register opening/closing and totals | PASS (automated) / BLOCKED (manual) | Repository/calculation coverage passes; packaged shift closure not exercised |
| Kitchen printer | BLOCKED | No CUPS or physical printer |
| Cashier receipt printer | BLOCKED | No CUPS or 58/80 mm thermal printer |
| ESC/POS formatting | PASS (automated) | Receipt, kitchen, 58/80 mm, encoding and command byte tests pass |
| Cash drawer | BLOCKED | Pulse bytes/deduplication pass automatically, but no physical printer-connected drawer was available |
| Barcode scanner | BLOCKED | No scanner hardware; no barcode feature implementation was found in current app sources |
| Touchscreen usability | BLOCKED | No touchscreen or real desktop display |
| Printer disconnect/reconnect | BLOCKED | No CUPS/printer environment |
| Application restart | PASS (smoke) | Packaged executable launched repeatedly and retained generated data/logs |
| Ubuntu reboot | BLOCKED | WSL2 is not an equivalent booted Ubuntu Desktop POS |
| Local backup/restore | PASS (automated) | Archive validation, restore and rollback tests use disposable directories |
| USB backup/restore | BLOCKED | No removable Ubuntu test media |
| Offline operation | PASS (architecture/tests) / BLOCKED (manual) | Local SQLite/offline licence logic covered; full packaged UI plus printing not exercised offline |
| Package upgrade without data loss | PASS | Disposable upgrade preserved database, images, backups, settings and licence hashes |
| Licence/settings/printer persistence after upgrade | PASS for files / BLOCKED for printer hardware | Stored files survived; configured real printer could not be exercised |

## Automated regression result

| Suite | Passed | Failed | Skipped |
|---|---:|---:|---:|
| Shared KMP | 32 | 0 | 0 |
| Desktop | 125 | 0 | 0 |
| Android | 113 | 0 | 0 |
| Total | 270 | 0 | 0 |

Additional build validation:

- Android debug APK: PASS
- Desktop Linux compilation: PASS
- Debian packaging: PASS
- Installed-package smoke test: PASS
- Windows/shared regression: PASS

## Defects in Phase 8

No new reproducible application defect was found within the portion that could actually be executed. Consequently, no Phase 8 source modification or package rebuild was justified. The Phase 7 startup-log defect remains fixed and its packaged retest passes.

The missing hardware is a test-environment blocker, not a passing result and not a defect that can safely be fixed in code without reproducing a failure.

## Required client-equivalent hardware pass

Delivery approval still requires one clean Ubuntu 24.04 Desktop x86_64 POS with:

- touchscreen/display matching the client resolution;
- configured CUPS cashier and kitchen thermal printers;
- 58/80 mm paper as applicable;
- printer-connected cash drawer;
- barcode scanner if barcode operation is a required client feature;
- USB storage for backup/restore;
- network disconnect capability;
- a matching device-bound test licence.

On that system, execute the complete setup/login/order/payment/register/print/drawer/backup/reboot/upgrade workflow and record real results. Any BLOCKER or CRITICAL issue must be fixed, the `.deb` rebuilt, and both the targeted scenario and Windows/shared regression suites rerun before changing this report to ready.
