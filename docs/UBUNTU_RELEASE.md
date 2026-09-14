# PATISSERIE_POS Ubuntu/Linux release

## Production identity and version

- Display name: `PATISSERIE_POS`
- Application binary: `PATISSERIE_POS`
- Debian package: `patisserie-pos`
- Application ID: `ma.elaroui.generalpos`
- Licence product ID: `GENERAL_POS_V1`
- Current version: `1.0.1`
- Architecture: Ubuntu/Linux `amd64` (`x86_64`) only until ARM64 is separately built and tested.

Change `pos.desktop.version` in the root `gradle.properties` before a release. The value must be a Debian-compatible dotted numeric version such as `1.0.2` or `1.1.0`. It is used by native distribution metadata and exposed in the Settings diagnostic footer.

## Build

Run on an Ubuntu x86_64 build machine with JDK 17:

```bash
./gradlew clean :sharedLogic:allTests :desktopApp:desktopTest :desktopApp:packageDeb
```

The Compose task is `:desktopApp:packageDeb`; `:desktopApp:packageReleaseDeb` is the optimized alternative. The artifact is written below:

```text
desktopApp/build/compose/binaries/main/deb/
```

Use the actual filename emitted by jpackage. Confirm it with:

```bash
find desktopApp/build/compose/binaries/main/deb -maxdepth 1 -name '*.deb' -print
dpkg-deb --info desktopApp/build/compose/binaries/main/deb/*.deb
dpkg-deb --contents desktopApp/build/compose/binaries/main/deb/*.deb
```

## Install and upgrade

```bash
sudo apt install ./desktopApp/build/compose/binaries/main/deb/general-pos_1.0.1_amd64.deb
```

If jpackage emits a differently styled filename, pass that filename to `apt` instead. Run the POS normally from Ubuntu Applications; never run it using `sudo`.

Package files contain only program binaries and the bundled Java runtime. Mutable data remains in the user's XDG directories:

```text
${XDG_DATA_HOME:-~/.local/share}/general-pos/
${XDG_CONFIG_HOME:-~/.config}/general-pos/
${XDG_CACHE_HOME:-~/.cache}/general-pos/
```

Installing a newer package therefore preserves the database, images, backups, settings, printer configuration and device-bound licence. Normal `apt remove general-pos` also leaves these user-owned files in place. Reinstalling recovers them. No package script deletes customer data.

Downgrades are unsupported once a newer application has migrated the database schema. Restore a compatible backup before intentionally returning to an older version.

## Runtime dependencies

The Java runtime, Compose/Skia, SQLite JDBC and its x86_64 Linux native library are bundled in the application image. Printer discovery and printing require a working CUPS client configuration:

```bash
sudo apt install cups cups-client fonts-dejavu-core
```

The POS itself does not need root. The logged-in POS user must have normal permission to print through CUPS. Direct USB/serial drawer access is not used; the cash drawer is pulsed through the configured ESC/POS cashier printer.

## Clean Ubuntu 24.04 acceptance checklist

- [ ] Build from a clean Git checkout; confirm there are no test licences, databases, secrets or developer paths.
- [ ] Inspect package metadata and contents using `dpkg-deb`.
- [ ] Install the package with `sudo apt install ./<actual-file>.deb`.
- [ ] Launch PATISSERIE_POS from Ubuntu Applications without a terminal.
- [ ] Confirm the branded icon, Office category and launcher name.
- [ ] Confirm the bundled runtime starts without an separately installed JDK.
- [ ] Complete first-run setup and verify XDG directories are created as the normal user.
- [ ] Copy the displayed device ID and activate a Linux licence.
- [ ] Create products with optional SKU/barcode, categories and users.
- [ ] Confirm a new sale opens directly as `Products → Cart → Payment → Receipt`, with no table selection.
- [ ] Open a register, perform discounted cash/card sales and close the register.
- [ ] Verify sales history filters and product/category/cashier reports.
- [ ] Discover CUPS printers and save the receipt-printer selection.
- [ ] Print a test page and generic 58/80 mm store receipt.
- [ ] Trigger the printer-connected cash drawer once from an authorized workflow.
- [ ] Create and validate a backup on local storage and a mounted USB drive.
- [ ] Restart Ubuntu and verify database, images, settings, licence and printer choices.
- [ ] Build/install `1.0.2` over `1.0.1`; verify all existing data again.
- [ ] Run `sudo apt remove general-pos`, reinstall, and verify user data remains.
- [ ] Confirm logs are written under the XDG application log directory.
- [ ] Test CUPS missing, printer offline, read-only USB and insufficient disk space errors.

Do not publish the package until the real printer, drawer, upgrade and reinstall checks pass on a clean Ubuntu machine.

This duplicated product has an independent package, XDG data directory, Android
application ID and licence product ID. It does not overwrite the original restaurant
POS installation or accept licences issued for that product.
