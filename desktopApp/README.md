# Desktop module (Linux and Windows)

This module is the Compose Multiplatform entry point for Linux and Windows.

It intentionally does not depend on the Android `app` module. Android remains
the production implementation while domain, data, and UI code are migrated
incrementally into platform-neutral modules.

Current commands:

```powershell
.\gradlew.bat :desktopApp:run
.\gradlew.bat :desktopApp:desktopTest
.\gradlew.bat :desktopApp:packageExe
.\gradlew.bat :desktopApp:packageMsi
```

Ubuntu production packaging is documented in [`../docs/UBUNTU_RELEASE.md`](../docs/UBUNTU_RELEASE.md). Build the Debian package on Ubuntu with `./gradlew :desktopApp:packageDeb`.

The desktop application opens a production SQLite database at startup under
the current user's application-data directory. Schema migrations, constraints,
transactions, repository adapters, and generic first-install categories are in
place and tested. The shared desktop UI includes initial setup, user/PIN login,
lock/switch user, role-aware navigation, POS/cart/payment, active orders,
register operations, retail catalogue, cashier accounts, reports, settings,
backup, and staged restore. Legacy dining data remains readable but tables are
not part of the new-sale workflow. Desktop licensing uses the Android-compatible
signed payload format, device binding, a seven-day trial, clock rollback checks,
and platform-protected local state. Linux uses XDG directories, encrypted
device-bound licence state and CUPS raw ESC/POS output for 58mm/80mm receipts.
Actual printer/drawer acceptance still requires the target hardware.
