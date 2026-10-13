# 06. Hardware: Receipt Printing, Cash Drawer, Customer Display, Barcode Scanner

> Hardware behaviour is covered by automated tests with fakes/virtual devices only. **Real-printer,
> real-drawer and real-VFD acceptance has not been recorded for the current version** (historical
> reports in `docs/archive/` concern 1.0.x). Treat all hardware paths as "implemented, needs hardware
> validation".

## 1. Receipt / ticket printing

### 1.1 Pipeline (files under `desktopApp/.../print/`)

```
Order + ReceiptCompany + payment info
   → EscPosPrintRequest (PrinterService.kt / EscPosProductionFormatter.kt)
   → EscPosFormatterFactory.create(platform)
        WINDOWS → LegacyWindowsEscPosFormatter → ThermalTicketRenderer.render(...)   (WindowsThermalPrinter.kt)
        LINUX   → LinuxEscPosFormatter (preview text → CP858, rejects Arabic/unencodable text)
   → PrinterService.printRaw(printerName, bytes, role)
        WINDOWS → WindowsPrinterService → WindowsThermalPrinter.print → javax.print (raw byte flavor)
        LINUX   → LinuxPrinterService (CUPS tools: lpstat for health, lp-family for submission)
```

- **Windows does not use the Win32 spooler API directly** (`OpenPrinter/StartDocPrinter` is not used);
  `WindowsThermalPrinter.print` looks the queue up through `PrintServiceLookup`, picks the first
  supported `DocFlavor.BYTE_ARRAY` flavor (`AUTOSENSE`, then `TEXT_PLAIN_US_ASCII`, `TEXT_PLAIN_UTF_8`)
  and sends `SimpleDoc(bytes)`; up to 2 attempts, re-resolving the queue each time.
- Virtual printers (PDF, XPS, OneNote, Fax, "Document Writer", "Root Print Queue"…) are filtered
  out (`WindowsThermalPrinter.isIgnored/isVirtual`) and cannot be selected for tickets.
- Ticket kinds (`TicketKind`): `CUSTOMER`, `KITCHEN`, `PREPARATION`. The desktop flow currently prints
  `CUSTOMER` receipts and `PREPARATION` tickets (`printOperationalTickets`); `kitchen_printer` is reset to
  `""` whenever printer settings are saved, so a separate kitchen printer is effectively not used.
- **Paper width**: `printer_width` = `80` (48 text columns) or `58` (32 columns); anything else
  throws `Unsupported paper width`.
- **Logo**: `restaurant_logo_uri` image is converted to a monochrome raster (max 420×185 px for 80 mm,
  280×140 for 58 mm) and printed on customer tickets.

### 1.2 Encoding

| Aspect | Value (verified) |
| :--- | :--- |
| Init sequence | `ESC @`, `FS .` (cancel Chinese mode), `ESC t 19` (select CP858) — `EscPosCommands.INIT_CP858` |
| Charset | `CP858` (falls back to `IBM00858`) — `FrenchEscPosEncoder.CHARSET` |
| Cut | feed 7 lines then `GS V 0` (`EscPosCommands.FEED_AND_CUT`) |
| Drawer pulse | `ESC p pin t1 t2` (`cashDrawerPulse`, default pin 0, 50/250) sent to the customer printer |
| Arabic text | Not printable: Linux formatter returns `UNSUPPORTED_CHARACTERS`; Windows renderer does not shape Arabic |
| Currency text | `"<dh>.<cc> DH"` (`ThermalTicketRenderer.money`) |

### 1.3 Duplicate-print protection

`PrintJobDeduplicator` (`print/PrintJobDeduplicator.kt`) stores keys for **10 minutes**
(`retentionMillis = 10 * 60 * 1000`), not 3 seconds. Keys in use: `receipt:<orderId>:<paymentId>`,
`preparation:<orderId>:<lines.hashCode()>`, `drawer:<orderId>:<paymentId>`,
`session-closing-report:<sessionId>`. `releaseAfterFailure(key)` is called when a print fails so it can be
retried. Manual reprints from `RECEIPT_PREVIEW` use `isReprint = true` and their own cool-down
message ("Print already submitted. Wait before reprinting.").

### 1.4 Printer health & settings

- Windows: `WindowsPrinterService.checkPrinterHealth` = queue exists in `PrintServiceLookup`. It cannot
  detect paper-out or cover-open.
- Startup check: `DesktopNavState.checkStartupPrinterHealth()` (on the IO dispatcher) shows a message if the
  configured printer is missing.
- `getEffectiveCustomerPrinter()` falls back to a discovered physical printer when the saved one is
  absent. Settings keys: `customer_printer`, `printer_width`, `cash_drawer_enabled` (see 03 §3).

## 2. Customer pole display (VFD)

### 2.1 State machine (`sharedLogic/.../display/CustomerDisplayController.kt`, `CustomerDisplayModels.kt`)

| Trigger (NavGraph) | Controller call | State | Line 1 | Line 2 |
| :--- | :--- | :--- | :--- | :--- |
| Cart empty / total ≤ 0, lock, after sale | `showIdle()` | `Idle` | `welcomeLine1` (default `BIENVENUE`) centred | `welcomeLine2` (blank/legacy `HYPER CAISSE` → establishment name) centred |
| Cart changes | `updateCart(subtotal, due)` | `DuringSale` | `TOTAL` = **gross subtotal** | `A PAYER` = **net due** |
| Payment screen opened | `paymentStarted(subtotal, due)` | `PaymentStarted` | `TOTAL` gross | `A PAYER` net |
| Cash amount typed | `cashReceived(received, total)` | `CashPayment` | `RECU` received | `MONNAIE` change, or `A PAYER` remainder if short, or `MONNAIE 0.00` |
| Card selected | `nonCashPaymentSelected("CARTE / TPE", total)` | `NonCashPayment` | `TOTAL` | `CARTE / TPE` amount |
| Sale paid | `paymentCompleted()` | `PaymentCompleted` | `thankYouLine1` (default `MERCI POUR VOTRE`) | `thankYouLine2` (default `VISITE`), then `Idle` after `thankYouDurationSeconds` (default 5, coerced 1..60) |

Notes:

- Line 1 shows the gross total **even without a discount** (then both lines show the same number).
- Amounts are `MoneyRules.formatFixed` → `"150.00"`; the currency suffix passed by the NavGraph is empty
  (`CustomerDisplayController(currencySuffix = "")`), so **no "DH" is displayed**.
- `columns` defaults to 20 (`customer_display_columns`, min 10). Texts are cut/padded to the width.
- The gross/net behaviour (`updateCart(subtotal, due)` / `paymentStarted(total, due)`) is present in the
  working tree; check `git log`/`git status` to see whether it has been committed.

### 2.2 Text sanitising (`VfdMessageFormatter`)

`transliterateAccents` maps French accents/ligatures (`é è ê ë à â ä î ï ô ö ù û ü ç œ æ` and capitals),
curly apostrophes and `€` (→ `E`) to ASCII; any other char outside `32..126` becomes a space.
Always run text through `fitLine/centerLine/formatKeyValue` (they transliterate).

### 2.3 Protocol drivers (`VfdProtocolDrivers.kt`) — exact bytes

| Protocol | init | clear | line positioning / framing |
| :--- | :--- | :--- | :--- |
| `ESC_POS` | `1B 40` | `0C` | clear, then `1F 24 01 01` + line 1, `1F 24 01 02` + line 2 (US `$` x y) |
| `CD5220` | `1B 40` | `0C` | clear, `1B 6C 01 01` + line 1, `1B 6C 01 02` + line 2 (ESC `l` x y) |
| `DSP800` | `04 01 49 03` | `04 01 43 03` | `04 01 41` + line 1 + `03`, `04 01 42` + line 2 + `03` |
| `UTC_STANDARD` | `1B 74 00` | `0C` | clear, `1F 01 01` + line 1 + `0D`, `1F 01 02` + line 2 + `0D` |
| `PLAIN_TEXT` | `0D 0A` | `0C` | clear, line 1 + `0D 0A`, line 2 + `0D 0A` |

Older documentation listed different byte sequences (e.g. `05 43 31` for DSP800); the table above is
what the code emits. `VfdDriverRegistry` falls back to ESC/POS for unknown protocols.

### 2.4 Transport (`desktopApp/.../display/DesktopSerialVfdTransport.kt`)

- **Windows:** if `portName` is an existing regular file it is appended to; otherwise
  `CreateFile("\\\\.\\COMx")` via JNA `Kernel32`, `SetCommState` (baud, data/stop bits, parity) and 100 ms
  timeouts; falls back to `FileOutputStream("\\\\.\\COMx")`. **No jSerialComm.**
- **Linux:** opens the device file (e.g. `/dev/ttyUSB0`), configures with `stty` when available.
- Discovery (`CustomerDisplayDeviceDetector`): Windows registry `HARDWARE\DEVICEMAP\SERIALCOMM` then a
  COM1..COMn probe; Linux `/dev/ttyUSB*`, `ttyACM*`, `ttyS*`.
- Config enums: baud 2400…115200, data bits 7/8, stop bits 1/2, parity none/even/odd, connection mode
  `SERIAL`/`USB` (label only).
- `DesktopNavState` accepts `vfdTransportOverride` for tests.

## 3. Cash drawer

Only through the receipt printer's RJ11 port: after a successful **cash** payment, if
`cash_drawer_enabled == "true"` and a customer printer is configured, `printerService.openCashDrawer`
sends the pulse above. No separate drawer device is supported.

## 4. Barcode scanner

- Keyboard-wedge (HID) scanners only. `BarcodeScannerController` (POS screen) buffers printable
  characters; **Enter, NumPad Enter or Tab** terminates a scan; the buffer is dropped if more than
  **800 ms** pass between keys.
- `DesktopNavState.onBarcodeScanned(code)`: exact `barcode` match, then `sku` (case-insensitive), then a DB
  lookup. Unknown → warning; inactive/unavailable → warning; otherwise quantity **+1** and an info
  message. No sound is played.
