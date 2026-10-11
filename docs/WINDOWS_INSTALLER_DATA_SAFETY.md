# Windows installer data safety

## Future updates

The application keeps using `%LOCALAPPDATA%\PATISSERIE_POS\data\pos.db`. The same customer-owned tree contains `data\secure\license.dat`, images, backups, logs, and `config` (including the device seed). Settings and users are in SQLite. The MSI owns only `%LOCALAPPDATA%\PATISSERIE_POS_APP`; uninstall and future MSI updates must remove or replace only that binary tree. The installer neither creates nor copies a database. Opening the application uses the existing database in the customer-owned tree.

Before a schema migration, application startup creates a consistent SQLite snapshot in `data\backups` and validates it. If backup fails, migration does not start. Reopening a current schema does not seed or duplicate data.

Before distributing every future MSI, run desktop tests and inspect the actual package:

```powershell
powershell -NoProfile -File scripts/Test-WindowsInstallerDataSafety.ps1 -MsiPath "<new.msi>" -PreviousMsiPath "<previous safe.msi>"
```

The previous package must itself use the safe `PATISSERIE_POS_APP` path. The script checks the install root, UpgradeCode, recursive removal target, and absence of packaged database/license files. The automated desktop test simulates install, binary replacement, uninstall, and reinstall, then reopens the database and checks files and record counts. These checks do not execute Windows Installer. Test an actual update and uninstall in an isolated Windows machine before client rollout.

## One-time transition from the old unsafe MSI

The shipped `PATISSERIE_POS-1.2.7.msi` used `%LOCALAPPDATA%\PATISSERIE_POS` as `INSTALLDIR` and can recursively delete it during uninstall or major upgrade. **A manual backup does not make that old MSI safe. Do not install the new MSI over it.** Perform these steps for each Windows user/profile with customer data:

1. Close PATISSERIE_POS. Confirm in Task Manager that its process has exited. Do not reopen it until restoration.
2. Choose a unique backup folder on an external drive or another location outside both `%LOCALAPPDATA%\PATISSERIE_POS` and `%LOCALAPPDATA%\PATISSERIE_POS_APP`. Copy the **entire** `%LOCALAPPDATA%\PATISSERIE_POS` directory there, including `data\pos.db`, any `pos.db-wal`/`pos.db-shm`, `data\images`, `data\backups`, `data\secure\license.dat`, and `config`. Verify the copied database is nonempty and the license, images, and config are present. An archive from the app's Backup screen is useful as an additional independent copy, but does not replace the full-directory copy.
3. Run SQLite `PRAGMA integrity_check` on the copied database. If WAL files exist, keep them alongside the copied database during verification and restoration. Stop if the copy or integrity check fails.
4. Uninstall the old MSI through Windows Installed Apps. It may delete the original customer-data directory. Confirm the old app is uninstalled.
5. Install the new safe MSI under the **same Windows account** and confirm its binaries are in `%LOCALAPPDATA%\PATISSERIE_POS_APP`. Do not launch it yet. If it was launched accidentally and created an empty database, close it and move that new data directory aside; never merge it into or overwrite the verified backup.
6. Restore the entire copied `PATISSERIE_POS` directory to `%LOCALAPPDATA%\PATISSERIE_POS`. If the destination contains customer files, stop and investigate rather than merging or overwriting. Keep the external backup intact. The Windows license is protected for the Windows user, so use the same account.
7. Start the new app. Check the original store, users, products, categories, sales, settings, images, and license. If a schema migration ran, check `data\backups` for its pre-migration snapshot. Resume sales only after these checks, and retain the external backup.

For steps 2 and 6, this PowerShell example uses `E:` as the external drive; replace it with the actual backup location. Run the backup commands only after the app has fully closed:

```powershell
$source = Join-Path $env:LOCALAPPDATA 'PATISSERIE_POS'
$backup = 'E:\PATISSERIE_POS_transition_backup'
if (-not (Test-Path -LiteralPath (Join-Path $source 'data\pos.db'))) { throw 'Source database missing' }
if (Test-Path -LiteralPath $backup) { throw 'Choose a new, empty external backup directory' }
robocopy $source $backup /E /COPY:DAT /DCOPY:DAT /R:1 /W:1
if ($LASTEXITCODE -ge 8) { throw 'Backup copy failed' }
Get-Item -LiteralPath (Join-Path $backup 'data\pos.db') | Select-Object FullName,Length
sqlite3.exe (Join-Path $backup 'data\pos.db') 'PRAGMA integrity_check;'
```

The integrity check must print `ok`. Use a trusted SQLite tool if `sqlite3.exe` is not installed; do not proceed without verification. After old-MSI uninstall and new-MSI install, and **before starting the new app**, restore:

```powershell
$target = Join-Path $env:LOCALAPPDATA 'PATISSERIE_POS'
if (Test-Path -LiteralPath $target) { throw 'Destination already exists; investigate before restoring' }
robocopy $backup $target /E /COPY:DAT /DCOPY:DAT /R:1 /W:1
if ($LASTEXITCODE -ge 8) { throw 'Restore copy failed' }
sqlite3.exe (Join-Path $target 'data\pos.db') 'PRAGMA integrity_check;'
```

If the copied database has WAL sidecars, the commands copy them along with `pos.db`; keep the app closed throughout. Do not run the example against live customer data without first choosing and verifying the external backup location.

The old MSI is deliberately **not** accepted as a safe previous package by `Test-WindowsInstallerDataSafety.ps1`; passing it as `-PreviousMsiPath` correctly fails. This one-time transition is manual and is not automated by the application.
