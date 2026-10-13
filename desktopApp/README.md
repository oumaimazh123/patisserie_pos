# `:desktopApp` — the shipped PATISSERIE_POS application

Compose Multiplatform Desktop application (Windows 10/11 and Linux). It depends on `:sharedLogic` and
**not** on the legacy Android `:app` module. This is the product that is built, packaged and delivered.

Entry point: `ma.elaroui.pos.desktop.MainKt` (`src/desktopMain/kotlin/ma/elaroui/pos/desktop/Main.kt`).

## Commands

```powershell
.\gradlew.bat :desktopApp:run                 # run in development
.\gradlew.bat :desktopApp:desktopTest         # tests (needs :sharedLogic tests green too)
.\gradlew.bat :desktopApp:createDistributable # unpacked self-contained app (CI)
.\gradlew.bat :desktopApp:packageMsi          # Windows installer
.\gradlew.bat :desktopApp:packageExe          # Windows EXE
./gradlew :desktopApp:packageDeb              # Linux DEB (on Ubuntu)
```

After building an MSI always run `scripts/Test-WindowsInstallerDataSafety.ps1` (see
`docs/context/08_windows_packaging_and_devops.md`).

## What lives here

| Package (`ma.elaroui.pos.desktop.…`) | Content |
| :--- | :--- |
| `persistence` | `WindowsPosDatabase` — SQLite schema v12, migrations, repositories, reports, deletion |
| `presentation` | Screens and `DesktopNavState` / `DesktopNavGraph` (router + UI state) |
| `print` | ESC/POS formatting, Windows (`javax.print`) and Linux (CUPS) printer services |
| `display` | Serial VFD transport and settings |
| `license` | 7-day trial, ECDSA-signed licence verification, DPAPI/AES secure stores |
| `backup`, `importing`, `security`, `platform` | ZIP backup/restore, CSV import, PIN/password hashing, paths/locks/scanner |

Data directories, settings keys, tables and behaviour are documented in `docs/context/` — start with
`docs/context/00_index.md` and the rules in `/AGENTS.md`.

Hardware (printer, cash drawer, VFD) acceptance requires the target hardware; automated tests use fakes.
