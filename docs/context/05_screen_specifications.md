# 05. UI Screen Specifications

## 1. UI Architecture & Token Mapping

All screens are styled using the Jetpack Compose tokens defined in `docs/DESIGN.md`:
- `PrimaryCoral` (`#E63946`) for active actions and highlights.
- `NavyIndigo` (`#1D3557`) for top headers, brand bars, and sidebar accents.
- `BackgroundGray` (`#F8F9FA`) for main viewport backgrounds.
- `SurfaceWhite` (`#FFFFFF`) for product cards, dialog surfaces, and ticket panels.
- Font Family: **Inter** / **Plus Jakarta Sans** (Latin) + **Tajawal** (Arabic).

---

## 2. Cashier Screens

### 2.1 POS Main Sales Screen (`/pos`) — 3-Column Layout
The POS main sales screen is the cashier's default landing interface, optimized for high-speed touch ordering.

```
┌──────┬───────────────────────────────────────────┬─────────────────────────┐
│ NAV  │ CATALOG AREA (65%)                       │ TICKET SIDEBAR (35%)    │
│ RAIL │ [ Search Input  🔍 ] [ Active Cashier ]  │ Ticket #ORD-1042        │
│ (70px│                                           │ Table: T-04 (Dine-In)   │
│ )    │ [ Horizontal Category Carousel Tabs ]     ├─────────────────────────┤
│ 🏪   │   [Tous] [Cafés] [Thés] [Jus] [Plats]    │ 2x Café Nous-Nous  30DH │
│ 🪑   │                                           │ 1x Thé Menthe      12DH │
│ 📄   │ [ Product Grid (3-Column Touch Cards) ]   │ 1x Tajine Poulet   55DH │
│ 🍳   │ ┌──────────┐ ┌──────────┐ ┌──────────┐  ├─────────────────────────┤
│ ⚙️   │ │Café Nous │ │Thé Menthe│ │Jus Orange│  │ Sous-Total:     88.18 DH│
│      │ │ 15.00 DH │ │ 12.00 DH │ │ 18.00 DH │  │ TVA (10%):       8.82 DH│
│      │ └──────────┘ └──────────┘ └──────────┘  │ Total:          97.00 DH│
│      │ ┌──────────┐ ┌──────────┐ ┌──────────┐  ├─────────────────────────┤
│ 🚪   │ │Tajine Pou│ │Panaché   │ │Ex. Beldi │  │ [ Espèces ]  [ TPE/CB ] │
│      │ │ 55.00 DH │ │ 22.00 DH │ │ 35.00 DH │  │ [ ENCAISSER 97.00 DH  ] │
│      │ └──────────┘ └──────────┘ └──────────┘  └─────────────────────────┘
└──────┴───────────────────────────────────────────┴─────────────────────────┘
```

#### Key Components:
1. **Left Navigation Rail (70px):** Quick navigation icons (POS 🏪, Active Orders 🪑, Current Session 📄, Close Register 🚪).
2. **Catalog View Area (65% width):**
   - Top Bar: Instant search filter input & logged-in cashier badge.
   - Category Carousel: Scrollable horizontal tabs highlighted in `PrimaryCoralLight` when active.
   - Product Grid: Grid cards with `16.dp` radius, showing product name, price in `DH`, and availability state.
3. **Ticket Sidebar (35% width):**
   - Ticket Header: Order number, order type tag (`DINE_IN`, `TAKEAWAY`, `COUNTER`), table name.
   - Line Item List: Item name, quantity selector (`-`, `+`), price breakdown, note trigger.
   - Summary Footer: Subtotal, TVA breakdown (10%/20%), Total in `DH`.
   - Fast Action Buttons: Quick Cash (`Espèces`), Card (`TPE/CB`), Pay button (`ENCAISSER`).

---

### 2.2 Open Register Screen (`/register/open`)
Shown automatically when a Cashier logs in without an active session.
- **Header:** Register terminal name & date/time.
- **Input Field:** Opening Cash amount (Floating label with `DH` suffix).
- **Quick Amounts:** Buttons for common opening float amounts (`200 DH`, `500 DH`, `1000 DH`).
- **Primary Button:** "Confirm & Open Register".

---

### 2.3 Active Orders Screen (`/orders`)
List view / Grid view of all currently open orders.
- Filter chips: `All`, `Dine-In`, `Takeaway`, `Counter`.
- Order Cards: Order number, elapsed time indicator, table name, item count, current total.
- Card Actions: Tap to reopen order in POS sidebar, or Move to another table.

---

### 2.4 Close Register Screen (`/register/close`)
Cash register end-of-shift reconciliation.
- **Summary Cards:**
  - Opening Cash
  - Cash Sales total
  - Expected Cash calculated (`Opening + Cash Sales`)
- **Input Area:** Cashier enters counted cash drawer total.
- **Variance Result Card:** Highlights difference in green (`0.00 DH`), yellow (`Over`), or red (`Short`).
- **Confirmation Action:** "Confirm & Close Register" button (Prints closing summary).

---

## 3. Owner Management Screens

### 3.1 Owner Dashboard (`/dashboard`)
Overviews daily business performance:
- Metric Cards: Today's Total Revenue (`DH`), Number of Orders, Average Ticket Value, Active Register status.
- Quick Alert Banner: Number of cancelled orders today.
- Quick Action Shortcuts: Manage Catalog, Manage Tables, View Full History.

---

### 3.2 Product & Category Management (`/products`)
- **Category Tab Bar:** Add, edit, or reorder categories.
- **Product List / Table:** Image thumbnail, Name, Category, Price (`DH`), TVA rate (`10%`/`20%`), Availability toggle switch (`Available` / `Disabled`).
- **Add/Edit Modal:** Fields for product name, category selection, price input, TVA dropdown, image path picker.

---

### 3.3 Dining Areas & Table Management (`/tables`)
- **Area Selector Tabs:** Terrace, Ground Floor, First Floor, etc.
- **Bulk Table Generator:** Create multiple tables sequentially (e.g., Prefix `T-`, Range `1` to `10`).
- **Table Grid Display:** Shows active table status (`AVAILABLE` in grey/green, `OCCUPIED` in `PrimaryCoral`).

---

### 3.4 Cashier Management (`/cashiers`)
- Cashier list cards displaying name, role, status (`Active` / `Inactive`).
- Modal to add cashier account, update 4-6 digit PIN, or activate/deactivate account.

---

### 3.5 Sales History & Audit Reports (`/sales`)
- Filter bar: Date range picker, Cashier filter, Payment method filter (`CASH`, `CARD`, `CARNET_CLIENT`).
- Sales Table: Order #, Date/Time, Cashier, Order Type, Total (`DH`), Payment Method, Status (`COMPLETED` / `CANCELLED`).
- Audit Details Drawer: Opens line item snapshot, TVA breakdown, payment info, cancellation reason & approving Owner.

---

### 3.6 Settings & Local Backup (`/settings`)
- **Restaurant Details:** Name, Address, Phone, ICE, IF, Patente.
- **Printer Configuration:** Thermal printer model, connection type (Bluetooth / USB / Network IP), paper size (`80mm`).
- **Database Backup & Restore:**
  - "Export Local Database Backup" (.db file save picker).
  - "Restore Database from Backup" (Warning modal + file picker).
