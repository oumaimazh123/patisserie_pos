# 04. Workflows & Business Rules

## 1. Primary Workflows

### 1.1 First Installation Workflow
App launch → Check persistent setup flag in `SharedPreferences`/DataStore.
- **If Not Configured:**
  1. Language Selection (French / Arabic / English).
  2. Enter Restaurant Info (Name, Address, Phone, ICE, IF, Patente, Default Currency DH).
  3. Create Owner Account & 4-6 digit PIN.
  4. Create Default Categories (e.g., Hot Beverages, Cold Beverages, Food) and Products.
  5. Create Dining Areas (e.g., Terrace, Main Room) & Bulk Tables.
  6. Create Initial Cashier Account.
  7. Flag application as `IS_SETUP_COMPLETE = true`.
- **If Configured:** Go straight to Cashier Select / PIN Login screen.

```
App Start
   │
   ├──► [Setup Complete = False] ──► Installation Wizard ──► Save Config ──► Set Complete = True ──┐
   │                                                                                                │
   └──► [Setup Complete = True] ───────────────────────────────────────────────────────────────────┴──► Cashier Login Screen
```

---

### 1.2 Cashier Login & Session Check Workflow
1. Select Cashier profile from active cashier grid/list.
2. Enter Cashier PIN.
3. System checks for an active (`OPEN`) `RegisterSession` for this cashier / register terminal.
   - **No Open Session:** Direct to **Open Register** screen.
   - **Active Session Exists:** Direct straight to **POS Sales Screen**.

---

### 1.3 Register Opening & Closing Workflows

#### Register Opening Workflow
1. Cashier enters opening cash amount (`openingCash`).
2. System confirms and creates a new `RegisterSession` with `status = "OPEN"`.
3. POS Sales Screen opens for order taking.

#### Register Closing Workflow
1. Cashier clicks **Close Register**.
2. System checks for any `OPEN` unpaid orders in this session.
   - **If unpaid orders exist:** Block closing and display warning modal listing unpaid orders.
3. System calculates `Expected Cash`:
   $$\text{Expected Cash} = \text{Opening Cash} + \text{Cash Sales} - \text{Cash Refunds}$$
4. Cashier counts cash in drawer and enters `Counted Cash`.
5. System computes `Difference`:
   $$\text{Difference} = \text{Counted Cash} - \text{Expected Cash}$$
6. Cashier confirms closing → Session status set to `"CLOSED"`, `closedAt` timestamp saved.
7. Print session closing summary receipt on 80mm printer.

---

### 1.4 Order Creation & Lifecycle Workflows

#### Order Type Selection & Order Creation
```
                             ┌──► Select Area ──► Select Available Table ──► Create Order (Dine-in)
                             │
New Order ──► Select Type ───┼──► Add Items ──► Review ──► Save / Pay (Takeaway)
                             │
                             └──► Add Items ──► Review ──► Fast Pay (Counter)
```

1. **Dine-In:** Select Dining Area → Select Table (must be `AVAILABLE`) → Table status changes to `OCCUPIED`.
2. **Takeaway / Counter:** Immediately opens POS ticket.

#### Item Addition & Modification (3-Touch Ergonomics)
- Touch 1: Select Category (horizontal carousel or grid tab).
- Touch 2: Tap Product Card → Added to Cart Sidebar.
- Touch 3 (Optional): Tap item quantity `+` / `-` or add note (e.g. "Sans Sucre", "Bien cuit").

#### Price Snapshotting Policy
When an item is added to an order, `productNameSnapshot`, `unitPriceSnapshot`, and `tvaRateSnapshot` are copied directly into the `OrderItem` line record. Modifying product prices in management screens later will **never** alter historical order line totals.

---

### 1.5 Payment Workflow
1. Cashier taps **Pay** button on POS screen ticket footer.
2. Payment modal presents available payment methods:
   - `CASH` (Espèces)
   - `CARD` / `TPE` (Carte Bancaire)
   - `CARNET_CLIENT` (On-Account / Deferred)
   - `MOBILE_QR` (CIH Pay, Barid Pay, Cash Plus)
3. For Cash payments:
   - System displays Quick Cash buttons: `Exact Amount`, `20 DH`, `50 DH`, `100 DH`, `200 DH`.
   - Displays computed `Change Amount`.
4. Tap **Confirm Payment**:
   - Save `Payment` record to database.
   - Set `Order.status = "COMPLETED"` and save `completedAt` timestamp.
   - If Dine-in order, set `Table.status = "AVAILABLE"`.
   - Trigger 80mm Thermal Receipt printing.

---

### 1.6 Order Cancellation & Refund Workflow

#### Pre-Payment Cancellation
1. Cashier selects **Cancel Order** from ticket menu.
2. Select or enter cancellation reason (e.g., "Customer Changed Mind", "Wrong Table").
3. Prompt for **Owner PIN Approval**:
   - Owner enters PIN to authorize cancellation.
4. Set `Order.status = "CANCELLED"`, record `cancelledAt` timestamp and `cancellationReason`.
5. Free occupied table (if Dine-in).
6. Orders are **never deleted** from Room DB for audit compliance.

#### Post-Payment Refunds
Cashiers cannot directly delete or cancel completed sales. Only the Owner can initiate a formal audit refund entry from the Sales History view.

---

## 2. Essential Business Rules

1. **Active Register Session Constraint:** Every order must belong to an active (`OPEN`) register session. Cashiers cannot sell without opening a register.
2. **Immutable Price Snapshots:** Product name, price, and TVA rate must be copied directly into order line item records.
3. **Table Occupancy Limit:** A table can have only one active `OPEN` order at any time.
4. **Single Active Register Session per User:** Only one active session can exist per cashier/terminal at a time.
5. **Session Closing Blocker:** A cash register session **cannot** be closed while unpaid (`OPEN`) orders exist for that session.
6. **No Hard Deletions:** Cancelled orders must remain in the Room database marked as `CANCELLED` with a mandatory reason and timestamp.
7. **Strict Role Separation:** Cashiers cannot modify product catalog, table layout, prices, tax rates, or delete historical sales.
8. **Monetary Precision:** All monetary amounts must be calculated using exact decimal representations (stored in minor units / centimes).
9. **80mm Receipt Formatting:** Receipts must output restaurant name, address, ICE, IF, Patente numbers, order number, cashier name, line item breakdown with TVA rates, and total in DH.
