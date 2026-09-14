# 02. Software Architecture & Technology Stack

## 1. Architecture Overview
The application follows Android's recommended **Clean Architecture** and **Unidirectional Data Flow (UDF)** patterns.

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                         │
│  Jetpack Compose UI  ◄─────── StateFlow ───────  ViewModels     │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                        DOMAIN LAYER                             │
│        Use Cases  ◄───────  Models  ───────  Repository Interfaces│
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                              │
│   Repositories  ──►  Room DAOs  ──►  SQLite Database Engine     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Technology Stack & Dependencies

| Component | Technology / Library | Purpose |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.x | Main development language |
| **UI Framework** | Jetpack Compose (Material 3) | Declarative touch UI |
| **Architecture** | ViewModel + StateFlow | State management & UI events |
| **Local Database** | Room Database (SQLite) | Offline persistence & source of truth |
| **Navigation** | Navigation Compose | Declarative screen routing |
| **Typography Fonts** | Inter / Plus Jakarta Sans & Tajawal | Latin & Arabic UI rendering |
| **Async & Streams** | Kotlin Coroutines & Flow | Async database queries & UI state stream |

---

## 3. Detailed Package Structure

Root package: `ma.elaroui` (or `com.pos.morocco` / `ma.elaroui.pos`)

```
ma/elaroui/pos/
├── core/
│   ├── designsystem/          # Theme tokens, colors, typography, shapes
│   │   ├── Color.kt           # PrimaryCoral, NavyIndigo, BackgroundGray, SurfaceWhite, etc.
│   │   ├── Type.kt            # POSTypography (Tajawal + Inter)
│   │   └── Theme.kt           # POSTheme definition
│   ├── components/            # Reusable UI components
│   │   ├── POSButton.kt
│   │   ├── ProductCard.kt
│   │   ├── CategoryTab.kt
│   │   ├── NumericKeypad.kt
│   │   └── ConfirmationDialog.kt
│   └── util/
│       ├── CurrencyFormatter.kt # DH/MAD decimal formatting
│       ├── DateFormatter.kt
│       └── ReceiptPrinter.kt  # 80mm thermal receipt generator
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── POSDatabase.kt
│   │   │   └── Converters.kt   # BigDecimal <-> Long, Date <-> Long
│   │   ├── dao/
│   │   │   ├── UserDao.kt
│   │   │   ├── ProductDao.kt
│   │   │   ├── TableDao.kt
│   │   │   ├── OrderDao.kt
│   │   │   ├── RegisterDao.kt
│   │   │   └── PaymentDao.kt
│   │   └── entity/
│   │       ├── UserEntity.kt
│   │       ├── CategoryEntity.kt
│   │       ├── ProductEntity.kt
│   │       ├── DiningAreaEntity.kt
│   │       ├── TableEntity.kt
│   │       ├── RegisterEntity.kt
│   │       ├── RegisterSessionEntity.kt
│   │       ├── OrderEntity.kt
│   │       ├── OrderItemEntity.kt
│   │       └── PaymentEntity.kt
│   ├── mapper/                 # Entity <-> Domain mappers
│   └── repository/            # Repository implementation classes
│
├── domain/
│   ├── model/                  # Pure Kotlin domain data classes & enums
│   │   ├── User.kt
│   │   ├── Category.kt
│   │   ├── Product.kt
│   │   ├── RestaurantTable.kt
│   │   ├── Order.kt
│   │   ├── OrderItem.kt
│   │   ├── RegisterSession.kt
│   │   ├── Payment.kt
│   │   └── Enums.kt
│   ├── repository/            # Clean interfaces for repositories
│   └── usecase/               # Business logic operations
│       ├── auth/               # LoginUser, VerifyOwnerPin, Logout
│       ├── register/           # OpenRegisterSession, CloseRegisterSession, CalculateExpectedCash
│       ├── order/              # CreateOrder, AddOrderItem, UpdateQuantity, MoveOrderToTable, CancelOrder
│       ├── payment/            # ProcessPayment, GenerateReceipt
│       └── admin/              # ManageProducts, ManageTables, ManageUsers, GetSalesReports
│
└── presentation/
    ├── navigation/             # NavHost, AppScreens, BottomNavRail
    ├── auth/                   # Cashier Pin login screen
    ├── setup/                  # First installation wizard screen
    ├── pos/                    # 3-Column POS Ordering main screen
    ├── orders/                 # Active orders & order details screen
    ├── register/               # Open register / Close register screens
    ├── products/               # Owner Product & Category management
    ├── tables/                 # Owner Dining Areas & Table management
    ├── cashiers/               # Owner Cashier accounts management
    ├── reports/                # Owner Sales & Session history screens
    └── settings/               # Owner Restaurant info, print & backup settings
```

---

## 4. UI Layer & Navigation Architecture

The app uses two primary navigation flows determined by user role:

### 1. Cashier Navigation Rail (70px sidebar)
Default landing page after login: **POS Screen**
- **POS Screen (`/pos`):** 3-Column layout (Nav Rail + Category/Catalog Grid + Order Ticket Sidebar)
- **Active Orders (`/orders`):** List of open dine-in / takeaway orders
- **Current Session (`/session`):** Cash opening info, sales counter, expected cash preview
- **Close Register (`/close_register`):** Counted cash entry, variance calculator, session closing

### 2. Owner Navigation Drawer / Rail
Default landing page after login: **Dashboard Screen**
- **Dashboard (`/dashboard`):** Today's sales metrics, order counts, active register overview
- **Products & Categories (`/products`):** Catalog management, pricing, TVA configuration
- **Tables & Areas (`/tables`):** Floor plan creation, bulk table generator
- **Cashiers (`/cashiers`):** Account creation, PIN assignment, active toggle
- **Sales History (`/sales`):** Detailed order logs, payment filter, cancellation audit
- **Register Sessions (`/sessions`):** Opening vs. counted cash history per cashier
- **Settings (`/settings`):** ICE/IF/Patente info, printer test, database backup/restore
