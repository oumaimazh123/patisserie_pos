# DESIGN.md — System Design Specification
**Project:** Moroccan Café & Restaurant Point of Sale (POS) Architecture  
**Target Platform:** Android (Jetpack Compose) & Desktop (Windows/JVM)  
**Target Market:** Morocco (Cafés, Restaurants, Fast Food, Snack Outlets)  
**Document Version:** 1.0.0  
**Last Updated:** July 2026  

---

## 1. Executive Summary & Design Principles

This document specifies the UI/UX architecture, layout taxonomy, design tokens, and state management conventions for a high-performance **Moroccan Café & Restaurant POS system**.

### Key Design Pillars
1. **Speed & Ergonomics:** Touch-optimized, high-contrast layouts designed for ultra-fast cashier ordering during peak service hours (3-touch order entry limit).
2. **Moroccan Business Context:** Native multi-currency/unit formatting (DH/MAD), dual TVA (Tax) tiering (10%/20%), local payment workflows (Cash, TPE/Carte, Carnet/On-Account, QR Pay), and French/Arabic bilingual support.
3. **Adaptive Component Architecture:** Single codebase using **Jetpack Compose** tailored for Android POS tablets, dual-screen customer displays, and Windows desktop terminals.

---

## 2. Visual Identity & Design Tokens

### 2.1 Color Palette

| Token Name | Hex Code | RGB | Role / Usage |
| :--- | :--- | :--- | :--- |
| `PrimaryCoral` | `#E63946` | `rgb(230, 57, 70)` | Active buttons, primary highlights, cart accent |
| `PrimaryCoralLight` | `#FFF0F1` | `rgb(255, 240, 241)` | Active category backgrounds, badge fills |
| `NavyIndigo` | `#1D3557` | `rgb(29, 53, 87)` | Sidebar backgrounds, headers, brand accents |
| `BackgroundGray` | `#F8F9FA` | `rgb(248, 249, 250)` | Main application viewport canvas |
| `SurfaceWhite` | `#FFFFFF` | `rgb(255, 255, 255)` | Product cards, dialogs, ticket sidebar surface |
| `TextPrimary` | `#1E293B` | `rgb(30, 41, 59)` | Headings, item titles, prices |
| `TextMuted` | `#64748B` | `rgb(100, 116, 139)` | Options, labels, inactive tabs |
| `BorderGray` | `#E2E8F0` | `rgb(226, 232, 240)` | Card borders, table grid lines, dividers |

---

### 2.2 Typography Scale

The primary typeface is **Inter** or **Plus Jakarta Sans** for Latin scripts, paired with **Tajawal** for Arabic text rendering.

```
• Display / Title:  20sp - Bold (28pt Line Height) - Section Headers & Totals
• Subtitle / H2:    16sp - SemiBold (22pt Line Height) - Product Names & Modifiers
• Body Main:        14sp - Medium (20pt Line Height) - Search Input, Cart Items
• Caption / Tag:    12sp - Regular (16pt Line Height) - Secondary Modifiers, TVA
```

---

### 2.3 Radius & Elevation Standards

* **Cards (Product/Category):** `16.dp` Corner Radius, `1.dp` Elevation (Default) / `4.dp` (Hovered/Active)
* **Action Buttons:** `12.dp` Corner Radius, `0.dp` Elevation (Flat Modern Style)
* **Inputs & Controls:** `10.dp` Corner Radius
* **Modal Dialogs:** `24.dp` Corner Radius, `8.dp` Elevation

---

## 3. UI Screen Architecture (3-Column Layout)

The viewport is divided into three fixed structural zones to minimize navigation depth:

```
┌──────┬───────────────────────────────────────────┬─────────────────────────┐
│ NAV  │ CATALOG / MENU AREA (65% W)              │ TICKET / BILLING (35%)  │
│ RAIL │                                           │                         │
│ (70px│ [ Search Bar / Cashier Profile ]          │ [ Ticket Header #1042 ] │
│ )    │                                           │                         │
│      │ [ Horizontal Category Carousel ]          │ [ Scrollable Item List ]│
│ 🏪   │                                           │   • 2x Nous-Nous  30DH │
│ 🪑   │ [ Product Grid (3-Column Card Layout) ]   │   • 1x Thé Menthe 12DH │
│ 📄   │ ┌──────────┐ ┌──────────┐ ┌──────────┐  │                         │
│ 🍳   │ │Café Nous │ │Thé Menthe│ │Jus Orange│  │ ----------------------- │
│ ⚙️   │ │ 15.00 DH │ │ 12.00 DH │ │ 18.00 DH │  │ Sous-Total:     42.00 DH│
│      │ └──────────┘ └──────────┘ └──────────┘  │ TVA (10%):       4.20 DH│
│      │ ┌──────────┐ ┌──────────┐ ┌──────────┐  │ Total:          46.20 DH│
│ 🚪   │ │Tajine Pou│ │Panaché   │ │Ex. Beldi │  │                         │
│      │ │ 55.00 DH │ │ 22.00 DH │ │ 35.00 DH │  │ [ Espèces ] [ TPE / CB ]│
│      │ └──────────┘ └──────────┘ └──────────┘  │ [ ENCAISSER 46.20 DH  ] │
└──────┴───────────────────────────────────────────┴─────────────────────────┘
```

---

## 4. Jetpack Compose UI Token Mapping

### 4.1 Theme Tokens (`Theme.kt`)

```kotlin
package com.pos.morocco.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PrimaryCoral = Color(0xFFE63946)
val PrimaryCoralLight = Color(0xFFFFF0F1)
val NavyIndigo = Color(0xFF1D3557)
val BackgroundGray = Color(0xFFF8F9FA)
val SurfaceWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1E293B)
val TextMuted = Color(0xFF64748B)
val BorderGray = Color(0xFFE2E8F0)

val POSTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = TextMuted
    )
)
```

---

## 5. Domain & State Architecture

### 5.1 Data Domain Models

```kotlin
data class MenuItem(
    val id: String,
    val name: String,
    val priceDh: Double,
    val categoryId: String,
    val tvaRate: Double = 0.10 // 10% for food/catering, 20% standard
)

data class CartLineItem(
    val item: MenuItem,
    val quantity: Int = 1,
    val selectedOptions: List<String> = emptyList()
) {
    val lineSubtotal: Double get() = item.priceDh * quantity
    val lineTva: Double get() = lineSubtotal * item.tvaRate
    val lineTotal: Double get() = lineSubtotal + lineTva
}

enum class PaymentMethod {
    CASH,          // Espèces
    TPE_CARD,      // Carte Bancaire / TPE
    CARNET_CLIENT, // On-Account / Client Fidèle
    MOBILE_QR      // CIH Pay / Cash Plus / Barid Pay
}
```

---

## 6. Moroccan Fiscal & Operations Requirements

1. **Dual TVA Calculations:**
   * Catering / Dine-In Food & Teas: **10% TVA**
   * Commercial Bottled Goods / Alcohol / Specific Services: **20% TVA**
2. **Thermal Receipt Printing Standard (80mm):**
   * Must render **ICE (Identifiant Commun de l'Entreprise)**, **IF (Identifiant Fiscal)**, and **Patente** numbers.
   * Dual language receipt header (*Bonjour / Marhaba*).
3. **Local Payment Workflows:**
   * Support for **Carnet Client** (deferred payment tracking for regular patrons).
   * Fast cash exact-change buttons (`20 DH`, `50 DH`, `100 DH`, `200 DH`).

---

## 7. Responsive Breakpoints

| Breakpoint | Target Device | Layout Adjustments |
| :--- | :--- | :--- |
| **Tablet (10" - 12")** | Android POS Terminal (1280x800) | Standard 3-Column layout, 3-grid product view |
| **Desktop / All-in-One** | Windows POS Terminal (1920x1080) | Expanded 4-grid product view, persistent floor plan panel |
| **Mobile Companion** | Android Handheld Terminal | Tabbed navigation: Catalog View ↔ Billing View |
