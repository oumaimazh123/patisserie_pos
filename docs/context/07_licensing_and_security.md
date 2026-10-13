# 07. Licensing & Security

Sources: `desktopApp/.../license/{WindowsLicenseManager,DeviceFingerprintProvider,DesktopLicenseStoreFactory}.kt`,
`desktopApp/.../security/{PinSecurity,AdminDeletionSecurity}.kt`,
`WindowsPosDatabase.kt` (`PinHasher`, `verifyAdminDeletionPassword`, `cancelOrder`), `DesktopNavGraph.kt`
(`login`, licence gate), `tools/license-generator/LicenseGeneratorCli.kt`.

## 1. Licensing

### 1.1 Model
- Offline, **per-installation** licence. The app embeds only a **public** key
  (`WindowsLicenseManager.PUBLIC_KEY`, an EC P-256 key). The private key must stay outside the repository
  and outside client packages (root `.gitignore` excludes `*.jks`, `*.keystore`, `*.p12`, `*.pfx`,
  `**/private_key.pem`, `tools/license-generator/generated/`).
- Verification uses the JDK: `Signature.getInstance("SHA256withECDSA")` when the key algorithm is `EC`,
  `SHA256withRSA` otherwise. **It is not Ed25519** (earlier docs were wrong).
- `APP_ID = ma.elaroui.generalpos`, `PRODUCT_ID = GENERAL_POS_V1`, trial = 7 days (`TRIAL_MS = 604_800_000`).

### 1.2 Identifiers and request code
| Platform | Installation id | Where stored |
| :--- | :--- | :--- |
| Windows | **Random** `WIN-<UUID>` created on first use (`WindowsDeviceFingerprintProvider`) — *not* derived from motherboard/BIOS/disk | `installation_id` key inside the secure store |
| Linux | `LINUX-` + SHA-256 of `/etc/machine-id` (or `/var/lib/dbus/machine-id`) and `/sys/class/dmi/id/product_uuid`; falls back to a random seed file `config/secure/device-id.seed` | derived |

`requestCode()` = Base64 of JSON `{"appId","productId","version":"1.0","installationId"}`.

### 1.3 Licence key format
`<base64(payloadJson)>.<base64(signature)>`. Payload fields read with regexes: `appId`, `productId`,
`installationId` (case-insensitive compare), optional `expirationDateMs` (absent = permanent),
`customerName`. `verify` returns `VALID`, `INVALID` (bad signature/format/app/product), `WRONG_DEVICE`,
or `EXPIRED`.

### 1.4 State machine (`evaluateState`)
1. Read `last_trusted`; if `now < last_trusted − 300 000 ms` (5 minutes) → `CLOCK_ROLLBACK`.
2. Persist `last_trusted = max(last, now)`.
3. If a `license` entry exists → `verify` it.
4. Else trial: `first_launch` (written on first evaluation) + 7 days → `TRIAL_ACTIVE(daysRemaining)` or
   `TRIAL_EXPIRED`.
5. Any exception → `INVALID`.

`allowsUse` is true for `TRIAL_ACTIVE` and `VALID`. In `DesktopNavGraph` the statuses
`TRIAL_EXPIRED, EXPIRED, INVALID, WRONG_DEVICE, CLOCK_ROLLBACK` block the app behind the licence screen
(`isLicenseBlocking`). The clock-rollback guard uses a stored timestamp, **not** the latest order date.

### 1.5 Storage
| Platform | File | Protection |
| :--- | :--- | :--- |
| Windows | `data\secure\license.dat` | Windows DPAPI (`Crypt32Util.cryptProtectData`) of `key=base64(value)` lines. If DPAPI fails the file is written/read **unprotected** (plain lines). Keys: `installation_id`, `first_launch`, `last_trusted`, `license`. |
| Linux | `~/.config/patisserie-pos/secure/license.dat` | AES-GCM, key = SHA-256 of fingerprint material; chmod 600 |

The `license_key / license_signature / license_status` rows in the SQLite `settings` table are a legacy
carry-over that `factoryResetData` preserves; the live licence is **not** read from SQLite.
Backups do not contain the licence (`licenceIncluded=false`).

### 1.6 Generating a licence
`tools/license-generator/LicenseGeneratorCli.kt` (standalone, not part of the Gradle build): takes a
PKCS#8 private-key path (argument or `POS_LICENSE_PRIVATE_KEY`), the customer's request code, customer
name and validity, and prints the licence key. Its banner still says "Android"/"MOROCCAN POS".

## 2. User authentication

| Item | Value (verified) |
| :--- | :--- |
| PIN format | 4–6 digits (`PinValidationRules`) |
| Hash | PBKDF2WithHmacSHA256, **10 000 iterations**, 256-bit, 16-byte random salt (`PinSecurity.kt`) |
| Stored form | `users.pin_hash = "pbkdf2:<saltHex>:<hashHex>"`; legacy unsalted SHA-256 hex still verifies (`PinHasher.verify`) but is **not** automatically upgraded on login (earlier docs claimed it was) |
| Comparison | `MessageDigest.isEqual` |
| PIN uniqueness | Enforced in code by `ensurePinAvailable` (the DB unique index on `pin_hash` is ineffective because of salts) |
| Login | Select user + PIN; failure counter **per user in memory**: 5 failures → 30 s lock (`DesktopNavState.handleFailedPin`); reset on restart |
| Authorisation | Route-level `isOwnerRoute` + `navigateTo` redirect (05 §1). No per-action checks. |

PIN-only secrets are low entropy (10⁴–10⁶ values): anyone holding a copy of `pos.db`/a backup can recover
PINs offline in seconds regardless of the iteration count (KI-13).

## 3. Cancellation PIN
`order_cancellation_pin_hash/_salt` (PBKDF2, same 10 000 iterations). Required only from **non-owner**
users and only when configured. No lockout/attempt counter. Owner sets/removes it in Settings.

## 4. Admin deletion password (data management)

- Stored as `admin_deletion_password_hash/_salt` using `AdminDeletionSecurity` (PBKDF2, **100 000**
  iterations, min length 8). Set from Settings (owner).
- `WindowsPosDatabase.verifyAdminDeletionPassword(input)` returns true if the input matches **any active
  owner's PIN hash** *or* the admin deletion password. So an owner PIN is enough to wipe sales history or
  factory-reset (KI-11). There is no separate "master" secret and no built-in default password in effect
  (`AdminDeletionSecurity.DEFAULT_PASSWORD = "admin1234"` is declared but unused — KI-11).
- No lockout counter on this check.

## 5. Process & file safety
- **Single instance**: `DesktopSingleInstanceLock` locks `config/application.lock`
  (`%LOCALAPPDATA%\PATISSERIE_POS\config\application.lock`), acquired in `Main.kt`. Legacy-data
  migration uses `config/migration.lock` and the old `config/application.lock` of legacy roots.
- **Logs**: `data/logs/application.log`, rotated at 5 MB, 3 files. Do not log PINs/passwords/licence strings.
- **Legacy data migration** (`DesktopApplicationPaths.migrateLegacyDataIfPresent`): if no `pos.db` exists
  and a `PatisseriePOS` or `patisserie-pos` sibling root has one, it is snapshotted with `VACUUM INTO`,
  integrity-checked, image paths rewritten, licence/images/backups copied, then moved into place.

## 6. Security rules for contributors
1. Never commit private keys, licence-signing material, certificates (`*.jks, *.p12, *.pfx`) or real
   customer databases.
2. Do not weaken PIN/password checks or add "default" credentials; do not log secrets.
3. Any new destructive operation must require the owner role **and** a credential check, and should
   write an audit row (`logDeletionAudit` / `recordAudit`).
4. Do not trust UI hiding for authorisation; re-check `currentUser.role` in the action.
