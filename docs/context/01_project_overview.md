# 01. Project Overview & Scope Definition

## 1. Objective & Vision
The **Café & Restaurant POS (Minimal V1)** is a high-performance, offline-first Point of Sale application tailored specifically for cafés, restaurants, fast food, and snack outlets in Morocco (and adaptable globally).

The primary goal of V1 is to deliver a streamlined, resilient system focused strictly on core daily operations:
- Taking and customizing orders with an ergonomic 3-touch entry limit.
- Managing dining areas and table occupancy (Dine-in) or quick-service orders (Takeaway / Counter).
- Receiving and processing local payments (Cash, TPE/Card, Carnet Client, QR Pay).
- Managing daily register opening and closing sessions with expected cash verification.
- Managing categories, products, prices, and cashier accounts.
- Viewing basic daily sales reports, order history, and register session summaries.

---

## 2. V1 Scope Boundaries

### Explicitly INCLUDED in V1
- **100% Offline Operation:** Runs on local Room database. Zero cloud dependencies.
- **First Installation Wizard:** Initial restaurant setup, currency selection, tax defaults, and Owner PIN creation.
- **Role-Based Access Control:** Strict division between Owner and Cashier roles.
- **Category & Product Management:** Product catalog, pricing, availability toggling, category ordering, dual TVA tax configuration.
- **Dining Area & Table Management:** Floor plan setup (Terrace, Ground Floor, First Floor, etc.), bulk table creation, status tracking (`AVAILABLE`, `OCCUPIED`).
- **Register Sessions:** Opening cash declaration, sales tracking, closing cash count, variance calculation.
- **Order Processing:** Dine-in, Takeaway, Counter orders.
- **Payments:** Cash (with fast exact-change buttons), Card (TPE), Carnet Client, QR Pay.
- **Order Cancellation:** Pre-payment item/order removal with mandatory reason and Owner PIN approval.
- **Thermal Receipt Printing (80mm):** Local receipt rendering including fiscal IDs (ICE, IF, Patente) and dual-language header.
- **Bilingual & i18n Support:** French, Arabic (Tajawal font rendering), and English.
- **Local Data Management:** Local database backup and restore.

### Explicitly EXCLUDED from V1
To keep V1 lightweight and rock-solid, the following features are strictly out of scope:
- Inventory & stock tracking
- Suppliers and purchasing
- Customer accounts and loyalty points program
- Table reservations & booking management
- Delivery dispatching and driver tracking
- Kitchen Display System (KDS) / Kitchen printers integration
- Online ordering & cloud sync
- Multi-restaurant / multi-branch central management
- Split bills & table merging
- Advanced accounting and P&L statements
- Employee shift attendance / payroll
- Automatic cloud backups or subscription billing

---

## 3. Roles & Permissions Matrix

The system enforces strict Role-Based Access Control (RBAC):

| Capability / Feature | Owner | Cashier | Notes |
| :--- | :---: | :---: | :--- |
| First Installation & Restaurant Setup | ✅ | ❌ | Setup screen hidden post-setup |
| Configure Restaurant Info, ICE/IF/Patente | ✅ | ❌ | Owner settings only |
| Create / Edit / Delete Products & Categories | ✅ | ❌ | Cashiers cannot alter prices |
| Create / Configure / Disable Tables & Areas | ✅ | ❌ | Floor plan configuration |
| Create & Manage Cashier Accounts / Reset PINs | ✅ | ❌ | Cashier credentials management |
| View Global Dashboard & All Historical Sales | ✅ | ❌ | Full sales reporting |
| View Register Sessions (All Cashiers) | ✅ | ❌ | Full register audit |
| Back Up and Restore Database | ✅ | ❌ | Local file export/import |
| Open & Close Cash Register Session | ✅* | ✅ | *Owner uses Cashier profile to sell |
| Take Orders (Dine-in, Takeaway, Counter) | ✅* | ✅ | Primary Cashier workflow |
| Process Payments (Cash, Card, Carnet) | ✅* | ✅ | Primary Cashier workflow |
| Print Receipts | ✅* | ✅ | Primary Cashier workflow |
| Cancel Order before payment | Approved | Requested | Requires Owner PIN prompt |
| Mark completed order as Refunded | ✅ | ❌ | Owner audit action with reason |

---

## 4. Market & Fiscal Context (Morocco)

- **Currency:** Moroccan Dirham (`DH` / `MAD`).
- **Fiscal Identifiers on Receipts:** Identifiant Commun de l'Entreprise (`ICE`), Identifiant Fiscal (`IF`), and `Patente`.
- **Tax Tiering (TVA):**
  - **10% TVA:** Catering, food items, hot beverages (coffee, tea).
  - **20% TVA:** Commercial bottled goods, specific services, standard commercial items.
- **Language / Script Support:**
  - French (Primary business script)
  - Arabic (Tajawal font, native right-to-left layout alignment where applicable)
  - English (Optional UI language)
