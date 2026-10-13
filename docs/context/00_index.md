# 00. Documentation Index

This folder is the **source of truth for AI agents and developers** working on PATISSERIE_POS.
Everything here was verified against the source code (see "How these docs are verified" below).
Where a document disagrees with the code, **the code wins** and the document is a bug — fix the document.

> Status labels used throughout: **Implemented** (works in the shipped desktop app), **Partial**
> (exists but incomplete), **Not implemented** (planned/claimed elsewhere but absent in code),
> **Known issue** (confirmed defect, documented in [`10_known_issues_and_technical_debt.md`](./10_known_issues_and_technical_debt.md)).

## Reading order

| # | File | Read it when you touch… |
| :-: | :--- | :--- |
| 01 | [`01_project_overview.md`](./01_project_overview.md) | Anything — what the product is, what is and is **not** implemented, roles. |
| 02 | [`02_architecture_and_tech_stack.md`](./02_architecture_and_tech_stack.md) | Module boundaries, dependency versions, where code lives, threading model. |
| 03 | [`03_database_schema.md`](./03_database_schema.md) | SQLite schema v12, migrations, `settings` keys, backups/restore file formats. |
| 04 | [`04_workflows_and_business_rules.md`](./04_workflows_and_business_rules.md) | Orders, totals, discounts, VAT, payments, register sessions, cancellation, deletion. **Financial invariants live here.** |
| 05 | [`05_screen_specifications.md`](./05_screen_specifications.md) | `DesktopScreenRoute`, role guards, screen-by-screen behaviour. |
| 06 | [`06_hardware_printing_display.md`](./06_hardware_printing_display.md) | Receipt printing, cash drawer, VFD customer display, barcode scanner. |
| 07 | [`07_licensing_and_security.md`](./07_licensing_and_security.md) | Trial/licence, PIN hashing, admin deletion password, lockout, single-instance lock. |
| 08 | [`08_windows_packaging_and_devops.md`](./08_windows_packaging_and_devops.md) | MSI/DEB packaging, installer data safety, versioning, release checklist, CI. |
| 09 | [`09_testing_and_regression.md`](./09_testing_and_regression.md) | Which tests protect what, how to run them, regression checklist per area. |
| 10 | [`10_known_issues_and_technical_debt.md`](./10_known_issues_and_technical_debt.md) | **Confirmed defects and doc/code gaps. Read before "fixing" anything money-related.** |

Other documentation in the repository:

| File | Purpose |
| :--- | :--- |
| [`/AGENTS.md`](../../AGENTS.md) | Mandatory operating rules for AI agents (entry point). |
| [`/README.md`](../../README.md) | Short project presentation (French). |
| [`/docs/CSV_IMPORT_GUIDE.md`](../CSV_IMPORT_GUIDE.md) | User guide for the 16-column catalogue CSV import. |
| [`/docs/WINDOWS_INSTALLER_DATA_SAFETY.md`](../WINDOWS_INSTALLER_DATA_SAFETY.md) | Detailed procedure for the one-time migration away from the unsafe 1.2.7 MSI and per-release MSI verification. |
| [`/docs/UBUNTU_RELEASE.md`](../UBUNTU_RELEASE.md) | Linux `.deb` build/install/upgrade notes. |
| [`/docs/CI.md`](../CI.md) | What the GitHub Actions workflow runs. |
| [`/docs/DESIGN.md`](../DESIGN.md) | Visual design tokens ("Artisan Boulangerie": terracotta/honey palette). Matches `PosDesignSystem.kt`. |
| [`/desktopApp/README.md`](../../desktopApp/README.md), [`/scripts/README.md`](../../scripts/README.md) | Module / script level notes. |
| [`/docs/archive/`](../archive/) | **Historical** reports and checklists. Not current. Do not cite as fact. |

## Quick facts (all verified)

| Item | Value | Source |
| :--- | :--- | :--- |
| Product | PATISSERIE_POS — offline POS for pastry/bakery shops (Morocco, MAD) | `DesktopBuildInfo.kt` |
| Shipped app | `:desktopApp` (Compose Desktop, Windows MSI/EXE and Linux DEB) | `desktopApp/build.gradle.kts` |
| Shared logic | `:sharedLogic` (Kotlin Multiplatform, `jvm("desktop")` target) | `sharedLogic/build.gradle.kts` |
| Android `:app` | **Legacy / not the shipped product**; still built by CI | `settings.gradle.kts`, `.github/workflows/ci.yml` |
| Version | `1.2.9` (`pos.desktop.version`) | `gradle.properties` |
| DB schema version | `12` | `WindowsPosDatabase.kt` (`CURRENT_SCHEMA_VERSION`) |
| Windows data dir | `%LOCALAPPDATA%\PATISSERIE_POS\` (`data\pos.db`) | `DesktopPlatform.kt` |
| Windows binaries | `%LOCALAPPDATA%\PATISSERIE_POS_APP\` | `desktopApp/build.gradle.kts` |
| Money | `Long` centimes; rates in basis points | `MoneyRules.kt`, `OrderRules.kt` |

## How these docs are verified

Each claim was checked against the file named next to it. When you change behaviour, update the
matching document in the same change and, if you discover a new defect, add it to file 10 instead
of describing it as working. Do not document planned features as implemented.
