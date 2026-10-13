# 04. Workflows, Business Rules & Financial Invariants

All statements below were verified in code. Primary sources:
`sharedLogic/src/commonMain/kotlin/ma/elaroui/pos/shared/rules/*.kt`,
`.../application/PosUseCases.kt`,
`desktopApp/.../presentation/navigation/DesktopNavGraph.kt` (class `DesktopNavState`),
`desktopApp/.../persistence/WindowsPosDatabase.kt`.

## 0. Critical financial invariants (must hold after every change)

| # | Invariant | Enforced by |
| :-: | :--- | :--- |
| F1 | All money is `Long` **centimes**; rates and discounts are **basis points** (0..10 000). Never `Float`/`Double` (see KI-06 for the one place that violates this). | `MoneyRules`, `OrderRules` |
| F2 | Arithmetic uses overflow-checked helpers (`MathRules.addExact/subtractExact/multiplyExact/divideHalfUp`). | `MoneyRules.kt` |
| F3 | `order.total = subtotal − (Σ itemDiscount + globalDiscount)`; `subtotal` is the gross Σ(unit × qty). | `OrderCalculationRules.calculate` |
| F4 | Σ over lines of `recognized_amount_centimes` = `order.total_centimes` (the global discount is allocated to lines by largest remainder so it reconciles exactly). | `OrderCalculationRules.allocate` |
| F5 | `order.tax_centimes = Σ recognized_tax_centimes`; tax is **extracted** from the VAT-inclusive net: `tax = ⌊net × rate / (10 000 + rate)⌋` (truncation). | `OrderRules.kt:66` |
| F6 | A payment's `amount_centimes` equals the order total at payment time; for cash, `change = received − total` and `received ≥ total`. | `PaymentRules.calculate` |
| F7 | Only an `OPEN` order can be edited, paid or cancelled; `COMPLETED`/`CANCELLED` are terminal. | `OrderTransitionRules`, `Orders.save` (`require(previous.status == OPEN)`) |
| F8 | `(order_id, submission_token)` is unique → a replayed payment returns the existing payment instead of double-charging. | `payments` UNIQUE, `CompletePayment` |
| F9 | Order lines snapshot name, unit price, tax rate and category; later catalogue edits must not change history. | `order_items` columns |
| F10 | Expected cash = `opening + cash payments + cash-in − cash-out`; difference = `counted − expected`. | `RegisterCashRules.calculateExpected` |
| F11 | Cash totals for a session are `SUM(payments.amount_centimes WHERE method='CASH' AND status='COMPLETED')` (**net of change**). | `Payments.totalCashForSession` |
| F12 | One open session per cashier (partial unique index). | `one_open_session_per_cashier` |

## 1. First-run setup wizard (`presentation/setup/SetupScreen.kt`)

Shown while `settings.setup_complete != "true"`. Six steps: **Welcome → Language → Establishment
(name required, address, phone) → Owner account (name, PIN 4–6 digits + confirmation) → First register
name → Summary**.

What is actually persisted by `DesktopNavState.setup(...)` → `WindowsPosDatabase.configureInitialSetup`:

- `users`: owner (`role=OWNER`, PBKDF2 PIN hash). If an owner row already exists it is updated.
- `registers`: `id=1, name='Main Register'` if no register exists.
- `settings`: `establishment_name`, `setup_complete=true`, `restaurant_address`, `restaurant_phone`.

Not persisted although collected: the **language** choice and the **register name** (the callback
receives only name/address/phone/owner/PIN). The wizard does **not** insert starter categories or
products (older docs said it did) — a fresh installation has an empty catalogue.
ICE/IF/Patente/RC are entered later in Settings.

## 2. Authentication, lock & session start (`DesktopNavState.login`)

1. `USER_SELECTION` lists active, non-deleted users; the cashier picks a name and types a PIN.
2. `AuthenticateUser` validates 4–6 digits, then `Authentication.authenticate` iterates **all** active
   users and verifies the PIN against each hash (PBKDF2, 10 000 iterations each). The result must be
   the *selected* user, otherwise it counts as a failure.
3. Lockout: 5 failures for that user → locked 30 s. State is in memory (`failedPinAttempts`,
   `pinLockoutUntil`) and is lost on restart.
4. On success: if the user has no open session, one is **created automatically** with
   `register_id = 1` and `opening cash = 0` (`OpenRegisterSession`). The `OPEN_REGISTER` screen exists
   (`openRegister(value)`), but normal login never needs it; consequently the opening float is 0
   unless the user reaches that screen another way (KI-15).
5. Owners land on `DASHBOARD`, cashiers on `POS_MAIN`. A resumed open session shows a notice.
6. `lock()` (manual lock, or after closing the register) clears cart, pending order and display, and
   returns to `USER_SELECTION`. The register session stays `OPEN` unless explicitly closed.

## 3. Totals, discounts and VAT (`OrderRules.kt`, object `OrderCalculationRules`)

Inputs: lines (`unitPriceCentimes`, `quantity`, `taxRateBasisPoints`, `productId`),
`discountBasisPoints` (global, 0..10 000) and `itemDiscountsBasisPoints: Map<productId, bp>`.

```
lineTotal      = unit × qty
lineDiscount   = divideHalfUp(lineTotal × itemBp, 10 000)
lineNet        = lineTotal − lineDiscount
subtotal       = Σ lineTotal
afterItemDisc  = Σ lineNet
globalDiscount = divideHalfUp(afterItemDisc × discountBp, 10 000)
totalDiscount  = Σ lineDiscount + globalDiscount
total          = subtotal − totalDiscount
share[i]       = allocate(globalDiscount, weights = lineNet)   // largest-remainder, sums exactly
net[i]         = lineNet[i] − share[i]                          // = recognized amount
tax[i]         = ⌊ net[i] × rate[i] / (10 000 + rate[i]) ⌋      // truncated, NOT half-up
order.tax      = Σ tax[i]
```

Facts to remember:

- `divideHalfUp` rounds the half **up** (`remainder ≥ (denominator+1)/2`) and requires non-negative inputs.
- The VAT rounding is **truncation** of the extracted tax, and there is no "remainder to the last item"
  step; reconciliation is guaranteed only for the discount allocation. Older documentation claimed
  `net − round(net·10000/(10000+R))` — that is **not** what the code does.
- Item discounts are keyed by **productId**; `CreateOrder` rejects duplicate product lines, so this is
  one discount per line.
- Validation (`require`): quantity > 0, unit price ≥ 0, tax rate 0..10 000, discounts 0..10 000.
- A 100 % discount yields a total of 0; `PaymentRules` then accepts any `received ≥ 0` for cash.
- Customer display and the cart UI call the same `OrderCalculationRules.calculate` (`getCartTotals()`).

`CreateOrder` (`PosUseCases.kt:31`) additionally requires: open session owned by the cashier, non-empty
cart, no duplicate product, product `active && available` and passing `ProductValidationRules`
(name, price > 0, tax 0..10 000, category active), table rules for dine-in. When editing an existing
`OPEN` order it **keeps the previous line's unit price/name snapshot** and only changes the quantity.

## 4. Payment (`PaymentRules.kt`, `CompletePayment`)

| Method | UI | Rule |
| :--- | :---: | :--- |
| `CASH` | ✅ | `received ≥ total`; `change = received − total`; otherwise `INSUFFICIENT_CASH` |
| `CARD` | ✅ | Always confirms (no terminal integration; `cardApproved` defaults to true) |
| `CARNET_CLIENT` | ❌ | Rules accept it as fully paid; **no UI, no customer account/ledger** |
| `MOBILE_QR` | ❌ | Same as above |

`CompletePayment.execute` (inside one DB transaction): load order → session must be `OPEN` and owned by
the acting cashier → existing payment for token returns as success → order must be `OPEN` →
`PaymentRules.calculate` → insert payment (`COMPLETED`) → `orders.save(status = COMPLETED)`.
The UI token is `"<orderId>-<System.nanoTime()>"` and `pay()` is guarded by `isPaymentProcessing`.
There is **no split tender, no partial payment, no refund/return/void flow** (`PaymentStatus.VOIDED`
exists but nothing sets it).

After success the NavGraph: opens the cash drawer (cash + `cash_drawer_enabled` + printer configured,
deduplicated by `drawer:<orderId>:<paymentId>`), stores `CompletedSaleConfirmation`, clears the cart,
sets the VFD to "thank you", refreshes state. Receipt printing is performed on user action
(`printAndFinishSale`) or automatically through `submitAutomaticDocument` (see 06).

## 5. Held orders, cancellation, tables

- **Hold** = `saveDraft(false)`: the order stays `OPEN` with its lines; shown in `ACTIVE_ORDERS`.
- **Resume** = `resumeOrder(order)`: reloads lines, item discounts and global discount into the cart.
  Orders saved before v12 with a discount but without `discount_basis_points` cannot be edited; they
  go straight to payment with the stored total.
- **Cancel** (`WindowsPosDatabase.cancelOrder`): reason required; actor must be an active user; owners
  need no PIN; cashiers need the **cancellation PIN only if one is configured**
  (`order_cancellation_pin_hash` present). Sets `status='CANCELLED'`, frees the table, writes an
  `ORDER_CANCELLED` audit row. Only `OPEN` orders can be cancelled — paid sales cannot be cancelled.
- **Move table** (`moveOrder`): legacy dine-in feature; the target table must be active and not `OCCUPIED`.

## 6. Register sessions & cash (`RegisterRules.kt`, `PosUseCases.kt`)

- **Open**: `OpenRegisterSession` — opening cash ≥ 0, one open session per user. (Auto-created at login with 0, see §2.)
- **Cash movement** (`RecordCashMovement`): only by the session's cashier; amount > 0; reason required;
  description ≤ 200 chars and mandatory when reason is "Autre"; a `CASH_OUT` may not exceed the current
  expected cash. The desktop `movement()` passes `description = null`. **No approval PIN** exists.
- **Close** (`CloseRegisterSession`, one transaction): requires session `OPEN`, **no `OPEN` orders in the
  session**, counted/left/removed ≥ 0, `left + removed = counted` when all three are given, note ≤ 500
  chars. Stores `expected`, `counted`, `difference`, `closing_user_id`, drawer split, remittance
  reference (made unique by `nextUniqueRemittanceReference`), note; status `CLOSED`.
- **Report**: `SessionClosingReportRules` + `SessionClosingReportEscPosFormatter`; auto-printed after
  close when `auto_print_session_closing_report = true` and a printer is set; a failed print leaves a
  persistent message and `pendingClosingReportRetrySessionId`.

## 7. Preorders — what exists and what does not

| Piece | Status |
| :--- | :--- |
| Columns `customer_name`, `customer_phone`, `pickup_date`, `preparation_status`, `custom_note`, `deposit_centimes` | Exist (v8) |
| `OrderType.PREORDER`, `PreparationStatus`, `PreorderRules` (`validatePreorder`, `calculateBalanceDue`, `recommendedDepositCentimes`, `canTransition`) | Exist in `sharedLogic` |
| `CreateOrder` validation for `PREORDER` (customer name required, total > 0, pickup not in the past, `0 ≤ deposit ≤ total`) | Exists. **Phone is not validated.** |
| UI to create a preorder, enter customer/pickup/deposit, change preparation status, collect balance | **Absent**: `saveDraft` always creates `COUNTER` and only forwards preorder fields of an existing order |
| Deposit as partial payment; balance collection at pickup | **Absent**: `CompletePayment` charges the full total in one payment and ignores `deposit_centimes` |
| `PreorderRules.canTransition` | Allows `PENDING→IN_PREPARATION`, `PENDING→READY`, `IN_PREPARATION→READY`, `READY→DELIVERED` (not strictly sequential); nothing in the UI calls it |

Treat preorders as **scaffolding**. Do not describe them as a working feature.

## 8. Reporting rules

- Sales reports use `orders.status='COMPLETED'` and filter by **`orders.updated_at`** (there is no
  `completed_at`) — see KI-02.
- `salesSummary` (no category filter): count/total/tax from `orders`; cash and card from `payments` by
  method, so sales paid with any other method would not appear in `cash`/`card` (KI-01).
- `todaySalesSummary`: `sales = cash + card` payments of today's completed orders (KI-01).
- Per-line analytics (`retailSalesAnalytics`, category filter) use `recognized_amount_centimes` /
  `recognized_tax_centimes`, falling back to proportional allocation for pre-v12 rows
  (`recognizedLineSql`).
- Session history (`sessionHistory`) computes cash/card/total per session from `payments.session_id`;
  "card sales" there is `method <> 'CASH'`.

## 9. Data deletion (owner only, `DataManagementScreen.kt`)

1. Screen route `DATA_MANAGEMENT` is owner-only.
2. Each destructive action asks for a credential checked by `verifyAdminDeletionPassword`: accepted
   values are **(a) any active owner's PIN, or (b) the configured admin deletion password**
   (≥ 8 chars). The `AdminDeletionSecurity.DEFAULT_PASSWORD` constant is **not** consulted by this
   function (KI-11).
3. Actions: sales history, selective groups, business data, factory reset (see 03 §5 for SQL effects).
4. Sales deletion removes completed/cancelled orders and **closed** sessions with their cash movements
   and payments; for **open** sessions it only nulls `expected/counted/difference`. Cash movements and
   opening cash of an open session are **kept**, so its expected cash is not "reset to zero".
5. **No automatic safety backup** is created before any deletion (older docs claimed one). The screen
   only links to the Backup screen (KI-12). Always ask the user to run a backup first.
6. Each deletion writes a `DATA_DELETION` audit row (except factory reset, which wipes `audit_logs`).

## 10. Catalogue rules

- Product: name required, price > 0, tax 0..10 000 bp, unique name per category (case-insensitive),
  unique SKU and barcode among non-deleted products. Delete = soft delete + restore.
- Category: unique name per parent; up to 4 levels via CSV import (`Main Category` + `Sub-Category 1..3`).
- The sellable list (`Products.observeSellable`) contains only `active && available` products; `CreateOrder`
  re-checks both flags and the category's `active` flag at order time.
- Barcode scan (`onBarcodeScanned`): looks in the in-memory list (`barcode` exact, then `sku` case-
  insensitive), then queries the DB; inactive/unavailable products are rejected; otherwise quantity +1.
- CSV import (`importing/CsvImports.kt`, `DesktopNavState.executeCatalogImport`): see
  [`docs/CSV_IMPORT_GUIDE.md`](../CSV_IMPORT_GUIDE.md). Existing products are matched by **SKU =
  Product ID** and **overwritten** (name, price, tax, category) and re-activated. The import is executed
  row by row, **not** in a single transaction; prices are parsed via `Double` (KI-06).
