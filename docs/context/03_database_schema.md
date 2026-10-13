# 03. Database Schema & Persistence

Source of truth: `desktopApp/src/desktopMain/kotlin/ma/elaroui/pos/desktop/persistence/WindowsPosDatabase.kt`
(`schemaV1()` at the end of the file, `migrate()` ~line 2082, `CURRENT_SCHEMA_VERSION = 12` at line ~107).

## 1. Engine, location, pragmas

| Item | Value |
| :--- | :--- |
| Engine | SQLite via `org.xerial:sqlite-jdbc` 3.50.3.0, **one** shared `Connection` |
| Windows file | `%LOCALAPPDATA%\PATISSERIE_POS\data\pos.db` (+ `-wal`, `-shm`) |
| Linux file | `${XDG_DATA_HOME:-~/.local/share}/patisserie-pos/data/pos.db` |
| Pragmas set on open | `foreign_keys=ON`, `journal_mode=WAL`, `busy_timeout=5000`, `synchronous=NORMAL`, `cache_size=-4000`, `temp_store=MEMORY` |
| Concurrency | `ReentrantLock(true)` around every access (`read{}`); nested transactions via `transactionDepth` |
| Money | `INTEGER` centimes. Rates/discounts `INTEGER` basis points (`10000 = 100 %`) |
| Timestamps | epoch **milliseconds** (`created_at`, `updated_at`, `opened_at`, `closed_at`, `pickup_date`, `deleted_at`) |
| Soft delete | `deleted_at INTEGER NULL` on `users`, `categories`, `products`, `dining_areas`, `restaurant_tables` |

Directory layout produced by `DesktopApplicationPathsProvider` (`platform/DesktopPlatform.kt`):

| Purpose | Windows (`root = %LOCALAPPDATA%\PATISSERIE_POS`) | Linux |
| :--- | :--- | :--- |
| Database | `root\data\pos.db` | `~/.local/share/patisserie-pos/data/pos.db` |
| Backups | `root\data\backups\` | `…/data/backups/` |
| Product/category images | `root\data\images\` | `…/data/images/` |
| Logs | `root\data\logs\application.log` (5 MB × 3 rotations) | `…/data/logs/` |
| Licence store | `root\data\secure\license.dat` | `~/.config/patisserie-pos/secure/license.dat` |
| Config / locks | `root\config\` (`application.lock`, `migration.lock`, …) | `~/.config/patisserie-pos/` |

## 2. Migration ledger and rules

```sql
CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL);
```

`migrate()` runs blocks `if (n !in appliedVersions) transactionBlocking { … }` for `n = 1..12`
(they are inline blocks, not separate functions). A fresh database therefore executes v1 (full base
schema) then replays v2…v12 (`addColumnIfMissing`, so already-present columns are skipped).

**Rules:** never edit blocks 1–12; add block 13+ and bump `CURRENT_SCHEMA_VERSION`; use additive DDL
(`addColumnIfMissing`, `CREATE INDEX IF NOT EXISTS`); never drop user tables (v11 rebuilds `products`
with a copy — copy that pattern only with extreme care).

`WindowsPosDatabase.open(path)` order of operations:

1. `applyPendingRestore(path)` — if `pos.db.restore-pending` exists, validate and swap it in (with rollback).
2. `createPreMigrationSafetyBackup(path)` — if the file exists and **not all** versions `1..12` are in
   `schema_migrations`: refuse if the file has a version `> 12` ("schema newer than this application"),
   then `VACUUM INTO data/backups/auto_pre_update_<yyyyMMdd_HHmmss>_<uuid>.db`, run `PRAGMA integrity_check`,
   delete the backup if it fails (and abort).
3. Open connection, set pragmas, `migrate()`, `removeUntouchedLegacyBootstrap()`.

`removeUntouchedLegacyBootstrap()` deletes the old demo bootstrap data (owner `Owner`/PIN `1234`, register
`Main Register`, category sets `General/Food & Grocery/Household/Personal Care` or
`Pâtisserie/Viennoiserie/Gâteaux & Tartes/Boissons`) **only** if `setup_complete != true` and there is no
business data.

### Migration ledger

| Ver | Change (verified in `migrate()`) |
| :-: | :--- |
| 1 | Base schema (`schemaV1()`): `users`, `categories`, `products`, `dining_areas`, `restaurant_tables`, `registers`, `register_sessions`, `cash_movements`, `orders`, `order_items`, `payments`, `settings`, `audit_logs` + indexes (incl. `one_open_session_per_cashier`) |
| 2 | `dining_areas.image_path` |
| 3 | `register_sessions`: `closing_user_id`, `left_in_drawer_centimes`, `removed_amount_centimes`, `remittance_reference`, `remittance_destination`, `closing_note` |
| 4 | `products.sku`, `products.barcode`; `order_items.category_id_snapshot`, `category_name_snapshot`; unique indexes on sku/barcode |
| 5 | `deleted_at` on 5 tables; partial unique indexes (`WHERE deleted_at IS NULL`) |
| 6 | Reporting indexes on `orders`, `payments`, `order_items` |
| 7 | Replace `one_open_session_per_register` by `one_open_session_per_cashier` |
| 8 | `categories.parent_id` (**no** `ON DELETE` clause); `orders.customer_name`, `customer_phone`, `pickup_date`, `preparation_status`, `custom_note`, `deposit_centimes` |
| 9 | `categories.image_path` |
| 10 | `products.name_arabic`, `unit`, `description`; `idx_categories_parent_name` replaces `idx_categories_name` |
| 11 | Rebuild `products` (copy → drop → rename) to enforce `CHECK(price_centimes>0)`, `category_id … ON DELETE SET NULL`; recreate product indexes |
| 12 | `orders.discount_basis_points`; `order_items.item_discount_basis_points`, `recognized_amount_centimes`, `recognized_tax_centimes` (nullable: NULL = historical row) |

## 3. Current tables (effective v12)

### `users`
`id PK`, `name`, `role CHECK IN ('OWNER','CASHIER')`, `pin_hash` (`pbkdf2:<saltHex>:<hashHex>`; legacy rows may
hold an unsalted SHA-256 hex and are still verifiable), `active`, `created_at`, `deleted_at`.
Index `idx_users_pin UNIQUE(pin_hash) WHERE deleted_at IS NULL` — because every hash has a random salt this
index **cannot** detect duplicate PINs; uniqueness is enforced in code by `ensurePinAvailable` (verifies the
candidate PIN against every other user).

### `categories`
`id`, `name COLLATE NOCASE`, `display_order`, `active`, `deleted_at`, `parent_id`, `image_path`.
`UNIQUE(IFNULL(parent_id,0), name NOCASE) WHERE deleted_at IS NULL`.

### `products`
`id`, `category_id → categories ON DELETE SET NULL`, `name`, `price_centimes CHECK(>0)`,
`tax_basis_points CHECK(0..10000)`, `image_path`, `available`, `active`, `display_order`, `sku`, `barcode`,
`name_arabic`, `unit`, `description`, `deleted_at`.
Indexes: `idx_products_category_active(category_id,active,available)`; partial unique `sku`, `barcode`
(ignoring NULL/''); `idx_products_cat_name UNIQUE(IFNULL(category_id,0), name NOCASE) WHERE deleted_at IS NULL`.
**No stock column exists.**

### `registers`, `register_sessions`
`registers(id, name UNIQUE, active)` — the app creates one `Main Register` during setup.
`register_sessions(id, register_id, cashier_id, opened_at, opening_cash_centimes CHECK(>=0), closed_at,
expected_cash_centimes, counted_cash_centimes, difference_centimes, status CHECK IN ('OPEN','CLOSED'),
closing_user_id, left_in_drawer_centimes, removed_amount_centimes, remittance_reference,
remittance_destination, closing_note)`; `UNIQUE(cashier_id) WHERE status='OPEN'`.

### `cash_movements`
`id AUTOINCREMENT`, `session_id → register_sessions ON DELETE CASCADE`, `type IN ('CASH_IN','CASH_OUT')`,
`amount_centimes CHECK(>0)`, `reason`, `description CHECK(len<=200)`, `created_by_user_id`, `created_at`.

### `orders`
`id`, `order_number UNIQUE`, `type` (`DINE_IN|TAKEAWAY|COUNTER|PREORDER`, **no CHECK**), `table_id`,
`cashier_id`, `register_session_id`, `status` (`OPEN|COMPLETED|CANCELLED`, **no CHECK**),
`subtotal_centimes` (gross, before any discount), `discount_centimes` (item + global), `tax_centimes`,
`total_centimes` (net due), `created_at`, `updated_at`, `customer_name`, `customer_phone`, `pickup_date`,
`preparation_status` (`PENDING|IN_PREPARATION|READY|DELIVERED`), `custom_note`, `deposit_centimes`,
`discount_basis_points` (NULL = created before v12).
Indexes: `(register_session_id,status)`, `(updated_at DESC,status)`, `cashier_id`, `table_id`, `register_session_id`.

### `order_items`
`id AUTOINCREMENT`, `order_id → orders ON DELETE CASCADE`, `product_id` (no FK), `product_name`,
`unit_price_centimes`, `tax_basis_points`, `quantity CHECK(>0)`, `line_total_centimes` (= unit × qty, gross),
`category_id_snapshot`, `category_name_snapshot`, `item_discount_basis_points`,
`recognized_amount_centimes` (line net after item **and** global discount), `recognized_tax_centimes`,
`UNIQUE(order_id, product_id)`. `Orders.save` deletes and re-inserts all lines on every save.

### `payments`
`id AUTOINCREMENT`, `order_id → orders ON DELETE CASCADE`, `session_id → register_sessions`, `method`
(`CASH|CARD|CARNET_CLIENT|MOBILE_QR`, no CHECK), `amount_centimes` (amount settled = order total),
`received_centimes`, `change_centimes`, `status` (`COMPLETED|VOIDED`), `submission_token`, `created_at`,
`UNIQUE(order_id, submission_token)`. There is exactly one payment per order in current flows (no split tender).

### `dining_areas`, `restaurant_tables`
Legacy floor-plan tables (`status IN ('AVAILABLE','RESERVED','OCCUPIED')`); soft-deletable; partial
unique names.

### `settings`
`key TEXT PK, value TEXT NOT NULL`. Keys actually used by the desktop code:

| Key | Written/read by | Meaning |
| :--- | :--- | :--- |
| `setup_complete` | `configureInitialSetup`, nav state | `"true"` after wizard |
| `selected_language` | Settings screen | `fr` / `ar` / `en` (wizard's language choice is **not** persisted) |
| `establishment_name`, `establishment_specialty` | setup / Settings | Receipt title |
| `restaurant_address`, `restaurant_phone` | setup / Settings | Receipt header |
| `seller_ice`, `seller_tax_id`, `seller_patente`, `seller_commercial_register` | Settings | Fiscal identifiers (ICE, IF, Patente, RC) |
| `wifi_name`, `wifi_code`, `restaurant_logo_uri`, `print_establishment_name` | Settings | Receipt extras (`"true"/"false"`) |
| `customer_printer` | Printer settings | Windows printer / CUPS queue used for receipts |
| `kitchen_printer` | Printer settings | Reset to `""` on save; legacy |
| `printer_width` | Printer settings | `"80"` or `"58"` (default 80) |
| `cash_drawer_enabled` | Printer settings | `"true"` → pulse drawer on cash sale |
| `auto_print_session_closing_report` | Register closing UI | `"true"/"false"` |
| `customer_display_enabled`, `_connection_mode`, `_port`, `_protocol`, `_baud_rate`, `_data_bits`, `_stop_bits`, `_parity`, `_columns`, `_welcome_line1/2`, `_thankyou_line1/2`, `_thankyou_duration` | `CustomerDisplaySettingsRepository` | VFD configuration |
| `order_cancellation_pin_hash`, `order_cancellation_pin_salt` | `setOrderCancellationPin` | PBKDF2 (salt separate, hash without `pbkdf2:` prefix) |
| `admin_deletion_password_hash`, `admin_deletion_password_salt` | `setAdminDeletionPassword` | PBKDF2 100 000 iterations |
| `license_key`, `license_signature`, `license_status` | **legacy**; only preserved by `factoryResetData` | The live licence is in `secure/license.dat`, not here |

### `audit_logs`
`id`, `action_type`, `acting_user_id → users`, `entity_type`, `entity_id`, `created_at`, `details`.
Written for `ORDER_CANCELLED`, `DATA_DELETION` and via `recordAudit`.

## 4. Backup & restore formats

| Artifact | Created by | Format |
| :--- | :--- | :--- |
| Manual backup | `DesktopBackupService.createBackup` | `PATISSERIE_POS_Backup_<timestamp>.zip` containing `manifest.properties` (`formatVersion`, `applicationId`, `applicationVersion`, `createdAt`, `databaseSchemaVersion`, `platform`, `databaseEntry`, `licenceIncluded=false`) and `data/pos.db`. The DB snapshot is `VACUUM INTO` plus a `backup_managed_files(relative_path, data BLOB)` table holding every file under `data/images`. **The licence file is not included.** |
| Legacy backup | older versions | plain `.db` (still accepted by `validate`) |
| Pre-update backup | `createPreMigrationSafetyBackup` | `data/backups/auto_pre_update_*.db` (only when a migration is pending) |
| Pre-restore backup | `stageRestore` | `data/backups/PATISSERIE_POS_PreRestore_<timestamp>.db`; the newest 5 are kept |
| Restore | `stageRestore` → next startup `applyPendingRestore` | `pos.db.restore-pending` is validated, swapped in, images re-extracted from `backup_managed_files`; on failure rolled back and renamed `pos.db.restore-failed` |

## 5. Deletion behaviour (database level)

See 04 §9 for business meaning. Summary of SQL effects:

| Method | Effect |
| :--- | :--- |
| `deleteSalesHistory` | Deletes payments/items/orders where `status != 'OPEN'`; deletes `cash_movements`, `payments` and the rows of **CLOSED** sessions; for **OPEN** sessions only sets `expected/counted/difference` to NULL. Open orders and open-session cash movements are kept. Audit row `DATA_DELETION`. |
| `deleteProducts` / `deleteCategories` / `deleteCatalogue` | **Soft** delete: `deleted_at = now`, `active = 0` (and `available = 0` for products). Rows stay in the DB and in old orders. |
| `deleteSuspendedSales` | **Hard** delete of `OPEN` orders + their items/payments; resets `OCCUPIED` tables to `AVAILABLE` |
| `deleteTablesAndAreas` | Sets `orders.table_id = NULL`, then **hard** deletes `restaurant_tables` and `dining_areas` |
| `deleteCashiers` | Soft delete (`deleted_at`, `active=0`) of all `CASHIER` users; owner kept |
| `deleteSelective(selection)` / `deleteBusinessData` | Compose the above by `DataGroupSelection` flags (`products`, `categories`, `salesHistory`, `suspendedSales`, `tablesAndAreas`, `cashiers`) |
| `factoryResetData` | Deletes payments, items, movements, orders, sessions, products, categories, tables, areas, **audit_logs**, registers, users and **all settings**; re-inserts `license_key/signature/status`, `admin_deletion_password_*`, `setup_complete=false`; **deletes the `data/images` directory**. The setup wizard runs again on next start. |

None of these takes a backup first (KI-12).
