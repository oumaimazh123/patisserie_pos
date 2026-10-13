# 08. Packaging, Installer Data Safety, Versioning & CI

Sources: `desktopApp/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
`scripts/*.ps1|sh`, `.github/workflows/ci.yml`, `docs/WINDOWS_INSTALLER_DATA_SAFETY.md`,
`docs/UBUNTU_RELEASE.md`, `docs/CI.md`.

## 1. Packages and where files go

| Item | Windows | Linux |
| :--- | :--- | :--- |
| Gradle tasks | `:desktopApp:packageMsi`, `:desktopApp:packageExe`, `:desktopApp:createDistributable` | `:desktopApp:packageDeb` |
| Artifact | `desktopApp/build/compose/binaries/main/msi/PATISSERIE_POS-<ver>.msi` (and `.../exe/…exe`) | `desktopApp/build/compose/binaries/main/deb/patisserie-pos_<ver>_amd64.deb` |
| Install type | **per-user** (`perUserInstall = true`), no directory chooser | system package |
| Binaries | `%LOCALAPPDATA%\PATISSERIE_POS_APP\` (`installationPath = "PATISSERIE_POS_APP"`) | jpackage default (`/opt/patisserie-pos`; not configured explicitly in the repo) |
| Customer data | `%LOCALAPPDATA%\PATISSERIE_POS\` (never owned by the MSI) | `~/.local/share/patisserie-pos`, `~/.config/patisserie-pos` |
| Upgrade identity | `upgradeUuid = 46788696-3912-4d20-9f7d-220b62935d31` (**must never change**, otherwise upgrades install side-by-side) | package name `patisserie-pos` |
| Start-menu group | `PATISSERIE_POS` | `Office` |
| Runtime | jpackage-bundled trimmed JRE (`modules("jdk.accessibility","java.sql")` + Compose defaults); no system Java needed | same |
| Build host requirements | JDK 17 toolchain and WiX Toolset 3.x on the build machine (jpackage requirement; **the WiX version is not pinned in the repo**) | Ubuntu x86_64 |

Target formats declared: `TargetFormat.Exe, Msi, Deb`; jpackage only builds the formats supported by
the host OS.

## 2. Version management

- **Desktop version**: `pos.desktop.version` in `gradle.properties` (currently `1.2.9`). It feeds
  `packageVersion`, the `-DgeneralPos.version` JVM arg (read by `DesktopBuildInfo.version`) and
  `scripts/sign-windows-release.ps1`. It must be a dotted numeric version (MSI/DEB requirement).
- The Android module has its own `versionName`/`versionCode` in `app/build.gradle.kts` (`1.2.9` / `20`).
  Keep them aligned only if the Android module is still built for a release.
- Do **not** bump versions as part of unrelated changes; a release is a deliberate act.

## 3. Customer data safety (non-negotiable)

History: the MSI up to 1.2.7 used `%LOCALAPPDATA%\PATISSERIE_POS` as `INSTALLDIR` and could recursively
delete customer data on uninstall/major upgrade. Since 1.2.8 the install root is
`PATISSERIE_POS_APP` and the data tree is untouched by the installer.

Rules for anyone touching packaging:

1. Never change `installationPath`, `perUserInstall` or `upgradeUuid` without running the safety script
   with `-PreviousMsiPath` against the last released MSI.
2. Never add a WiX fragment, jpackage resource, Gradle task or script that removes, overwrites or ships
   files under `%LOCALAPPDATA%\PATISSERIE_POS\` (database, images, backups, licence, config, logs).
3. Never package a `pos.db`, licence file or developer data into the MSI.
4. Do not "clean" data in installer custom actions. Data cleaning is the job of the explicit scripts below.

### Automated MSI audit

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Test-WindowsInstallerDataSafety.ps1 `
  -MsiPath ".\desktopApp\build\compose\binaries\main\msi\PATISSERIE_POS-<ver>.msi" `
  [-PreviousMsiPath "<previous safe msi>"]
```

The script reads the MSI database and fails (`throw "UNSAFE: …"`) if: `INSTALLDIR` overlaps the customer
data directory; recursive removal targets anything but the install root; a `RemoveFile` row targets a
directory outside the app/Start Menu; the MSI packages database/licence files; `UpgradeCode` differs from
`{46788696-3912-4D20-9F7D-220B62935D31}`; or (with `-PreviousMsiPath`) the upgrade changes UpgradeCode or
the previous package is the unsafe legacy layout. A passing run does **not** execute Windows Installer —
test a real upgrade/uninstall on an isolated machine before a client rollout
(`docs/WINDOWS_INSTALLER_DATA_SAFETY.md` contains the full manual procedure, including the one-time
transition off the 1.2.7 MSI).

## 4. In-app data protection mechanisms (summary — details in 03 §2 and §4)

| Mechanism | Trigger | Output |
| :--- | :--- | :--- |
| Pre-migration backup | App start with a pending migration | `data/backups/auto_pre_update_<ts>_<uuid>.db` (VACUUM INTO + integrity_check; startup aborts if it fails) |
| Downgrade guard | DB has `schema_migrations.version > 12` | Startup refuses ("schema is newer than this application") |
| Pre-restore backup | Staging a restore | `data/backups/PATISSERIE_POS_PreRestore_<ts>.db` (newest 5 kept) |
| Atomic restore | Next start after staging | Validate → swap → rollback on failure (`*.restore-failed` kept) |
| Legacy-root migration | No `pos.db`, but `PatisseriePOS`/`patisserie-pos` root exists | Snapshot copy, never move |

**Not present:** an automatic backup before data deletion/factory reset (KI-12).

## 5. Release checklist (Windows)

1. `git status` clean; version bumped intentionally in `gradle.properties`.
2. `.\gradlew.bat :sharedLogic:desktopTest :desktopApp:desktopTest` — all green (09).
3. `.\gradlew.bat :desktopApp:packageMsi` (and `packageExe` if shipped).
4. Run `Test-WindowsInstallerDataSafety.ps1` (with `-PreviousMsiPath` of the last release).
5. Optional signing: `scripts/sign-windows-release.ps1 -CertificatePath … -CertificatePassword <SecureString> -TimestampUrl …`
   (expects `PATISSERIE_POS-<ver>.exe` **and** `.msi`; refuses if missing or `signtool.exe` absent).
6. Manual: install over previous version on a machine with real data; verify store, users, products,
   sales, images, licence, settings; verify a pre-migration backup was created if schema changed.
7. Manual hardware pass (printer 58/80, cash drawer, VFD, scanner) — no recorded acceptance exists for 1.2.x.
8. Never distribute `.msi/.exe` from the repository (they are git-ignored); never commit secrets.

## 6. Helper scripts (`scripts/`)

| Script | Purpose | Caveat |
| :--- | :--- | :--- |
| `Test-WindowsInstallerDataSafety.ps1` | MSI audit above | Needs Windows (reads MSI via COM) |
| `sign-windows-release.ps1` | Authenticode signing of EXE+MSI | Requires cert, timestamp URL, Windows SDK |
| `clean_pos_data.ps1` / `clean_pos_data.sh` | Development/test reset: mode 1 = sales data, mode 2 = full wipe; the `.ps1` copies the data directory to a timestamped backup folder on the Desktop first (the `.sh` states the same intent; not re-verified) | **Destructive.** Never run on a customer machine without a verified external backup; operates on the live files outside the app's own safeguards |
| `verify_upgrade.sh`, `ubuntu-packaged-smoke-test.sh`, `test_deb_launch.sh`, `launch_on_vm.sh` | Linux package smoke/upgrade tests | Developer tools |

## 7. Continuous integration (`.github/workflows/ci.yml`, summary of `docs/CI.md`)

- Desktop job on `windows` and `ubuntu-24.04`: sets up Temurin JDK 17+21, virtual printer and virtual
  customer display, runs `:sharedLogic:desktopTest :desktopApp:desktopTest`, builds `:desktopApp:createDistributable`,
  verifies packaged image-cache classes, uploads reports and an unpacked app archive.
- Android job: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- CI does not build/sign MSI, does not run the installer safety script and does not test real hardware.
