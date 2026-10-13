# Scripts

| Script | Platform | Purpose |
| :--- | :--- | :--- |
| `Test-WindowsInstallerDataSafety.ps1` | Windows | Audits an MSI so an install/upgrade/uninstall cannot touch customer data. **Run it for every MSI** (see `docs/context/08_windows_packaging_and_devops.md`). |
| `sign-windows-release.ps1` | Windows | Authenticode-signs `PATISSERIE_POS-<ver>.exe` and `.msi` (needs certificate, timestamp URL, Windows SDK `signtool`). |
| `clean_pos_data.ps1` / `clean_pos_data.sh` | Windows / Linux | **Developer/test reset** of a POS data directory (below). |
| `verify_upgrade.sh`, `ubuntu-packaged-smoke-test.sh`, `test_deb_launch.sh`, `launch_on_vm.sh` | Linux | `.deb` smoke / upgrade checks and VM helpers. |

## `clean_pos_data` (destructive — development and test machines only)

Both scripts first copy the data directory to a timestamped backup folder, then apply one of two modes.
They work directly on the files, **outside** the application's own safeguards (no admin password, no
audit entry, the application must be closed).

| Mode | Effect (as implemented in `clean_pos_data.ps1`) |
| :--- | :--- |
| **1 — sales data only** | With `sqlite3` or Python: `DELETE` from `payments`, `order_items`, `orders`, `cash_movements`, `register_sessions` (**all sessions, including an open one**) and `audit_logs`, then `VACUUM`; removes WAL/SHM and cache. Keeps products, categories, users, settings, licence. |
| **2 — factory reset** | Wipes the database, images, logs and caches so the setup wizard appears again. |

> The licence store (`data\secure\license.dat` on Windows) is **not** removed by mode 1; check mode 2's
> effect on it before running it on a machine whose licence you need to keep.
> These scripts are not a substitute for the in-app *Data management* screen, which requires the owner
> role and a credential (and, today, takes no automatic backup — see KI-12).

```powershell
# interactive
.\scripts\clean_pos_data.ps1
# non-interactive
.\scripts\clean_pos_data.ps1 -Mode 1
.\scripts\clean_pos_data.ps1 -Mode 2
```

```bash
chmod +x scripts/clean_pos_data.sh
./scripts/clean_pos_data.sh
```

Note: `clean_pos_data.sh` mode 2 also removes the legacy directories
`~/.local/share/caferestaurantpos` and `~/.config/caferestaurantpos` (old product name).
