---
name: Artisan Boulangerie POS
colors:
  surface: '#fbf9f6'
  surface-dim: '#dbdad7'
  surface-bright: '#fbf9f6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f0'
  surface-container: '#efeeeb'
  surface-container-high: '#eae8e5'
  surface-container-highest: '#e4e2df'
  on-surface: '#1b1c1a'
  on-surface-variant: '#54433b'
  inverse-surface: '#30312f'
  inverse-on-surface: '#f2f0ed'
  outline: '#87736a'
  outline-variant: '#d9c2b7'
  surface-tint: '#924b22'
  primary: '#8f491f'
  on-primary: '#ffffff'
  primary-container: '#ad6135'
  on-primary-container: '#fffbff'
  inverse-primary: '#ffb690'
  secondary: '#825500'
  on-secondary: '#ffffff'
  secondary-container: '#ffbd5b'
  on-secondary-container: '#744c00'
  tertiary: '#894955'
  on-tertiary: '#ffffff'
  tertiary-container: '#a5616d'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbca'
  primary-fixed-dim: '#ffb690'
  on-primary-fixed: '#341100'
  on-primary-fixed-variant: '#74340b'
  secondary-fixed: '#ffddb3'
  secondary-fixed-dim: '#fcba58'
  on-secondary-fixed: '#291800'
  on-secondary-fixed-variant: '#624000'
  tertiary-fixed: '#ffd9de'
  tertiary-fixed-dim: '#ffb2be'
  on-tertiary-fixed: '#390916'
  on-tertiary-fixed-variant: '#6f3440'
  background: '#fbf9f6'
  on-background: '#1b1c1a'
  surface-variant: '#e4e2df'
typography:
  display-currency:
    fontFamily: epilogue
    fontSize: 44px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: epilogue
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: epilogue
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: epilogue
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: workSans
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 22px
  body-md:
    fontFamily: workSans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: workSans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: workSans
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: workSans
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: workSans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  keypad-num:
    fontFamily: epilogue
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 28px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  touch-min: 48px
  touch-standard: 56px
  touch-lg: 64px
  pad-xs: 4px
  pad-sm: 8px
  pad-md: 12px
  pad-lg: 16px
  pad-xl: 24px
  pad-2xl: 32px
  gutter-terminal: 12px
  panel-ticket-w-compact: 340px
  panel-ticket-w-regular: 420px
---

## Brand & Style

The visual narrative blends the heritage craft of traditional French artisanal baking with the precision and speed demanded by modern retail environments. The interface evokes warmth, olfactory memory (caramelized crusts, buttery laminated dough, roasted grains), and supreme operational clarity under rush-hour conditions.

### Design Movement
**Artisanal Modern Touch**: A functional fusion of warm minimal editorial aesthetics with high-efficiency point-of-sale engineering. It departs from sterile, clinical blue utility software by embracing baked-earth tones, tactile surface delineations, and clean geometric structures.

### Personality & Values
- **Chaleureux & Artisanal**: Rich earthen tones, natural textures, and buttery accents evoke pride of craft.
- **Opérationnel & Efficace**: Optimized for rapid cashier muscle memory, rush-hour volume, split-second visual categorization, and zero operational lag.
- **Lisible & Contrasté**: High contrast typography and deliberate hierarchy prevent checkout fatigue under variable bakery overhead lighting.

### Target Environment
- Designed explicitly for 15" to 22" touch-screen POS counter terminals (1024x768 XGA minimum up to 1920x1080 Full HD running on touch-optimized Linux/Ubuntu systems).
- Operable by flour-dusted hands, peripheral vision, and rapid one-tap workflows.

## Colors

The palette derives from the Maillard reaction—caramelization, golden crusts, flour washes, and gentle fruit or confectionery accents.

### Color Tiers & Roles
- **Primary (`#B5673B`, `#A65A2E`, `#8B5A3C`)**: Warm Terracotta and Roasted Pastry Brown. Used for primary execution targets (e.g., "Encaisser", "Valider"), active category tabs, focused active states, and core brand anchoring.
- **Secondary (`#D89B3C`, `#E2A94F`)**: Honey and Miel Doré. Reserved for high-priority modifier callouts, express loyalty badges, special artisan seasonal highlights, and payment success affirmations.
- **Tertiary (`#C77D8A`)**: Subtle Confectionery Pink. Used selectively for sweet pastry/entremets category demarcation, special dietary indicators (e.g., Sans Gluten), or delicate item modifiers.
- **Neutrals & Surfaces**:
  - Base Shell Canvas: `#F7F4F0`
  - Workspace Canvas / Order Panes: `#FAF8F5`
  - Active Elevated Cards & Surface Tiles: `#FFFFFF`
  - Hairline Boundaries & Dividing Lines: `#E6E0DA`
- **Text & Typography Tiers**:
  - High Emphasis (`#252525`): Primary product titles, cart totals, payment keypad values.
  - Medium Emphasis (`#4B5563`): Modifiers, tax summaries, ticket item quantities, system time.
  - Subtle Muted (`#8C827A`): Barcode numbers, inactive states, placeholder tokens.
- **Functional Semantics**:
  - Success / Validate: `#2E7D32` (Pistachio Green)
  - Danger / Void / Delete: `#C62828` (Groseille Red)
  - Alert / Waiting / Hold: `#D89B3C` (Honey Amber)

## Typography

The type system balances structured French editorial warmth with clinical terminal legibility.

- **Headlines & Numbers (`Epilogue`)**: Offers characterful, geometric authority with robust proportions. It handles large monetary numbers, totals, order numbers, and category headers with unmistakable clarity.
- **Body, Modifiers & UI Elements (`Work Sans`)**: Delivers clear, open apertures and distinct letterforms that remain crisp on lower DPI industrial touchscreens without visual artifacting or blurring.
- **Financial & Monetary Standards**:
  - Currency figures always present decimals clearly (e.g., `12,80 €`).
  - Numbers in transactional panels maintain tabular lining figures (`font-variant-numeric: tabular-nums`) to prevent horizontal jitter during rapid cart updates.

## Layout & Spacing

The terminal layout follows an asymmetric two-pane and three-pane fixed-fluid structure calibrated for landscape touchscreen hardware.

### Layout Model
- **Left / Center (Catalog & Touch Matrix)**: Fluid grid occupying 60–70% of horizontal viewport width.
  - Top category selector horizontal scroll/rack: Fixed 56px height per selector.
  - Product Card Matrix: Grid with `minmax(130px, 1fr)` auto-fill; cards maintain a minimum 1:1 or 4:3 touch aspect ratio.
- **Right (Ticket / Commande Pane)**: Fixed width (340px on 1024x768, 420px on 1920x1080) pinned to the right edge. Hosts the active order lines, order subtotal breakdown, discount triggers, and primary "ENCAISSER" action block.
- **Bottom Shelf / Utility Bar**: 52px fixed strip for operator profile, drawer trigger, kitchen ticket hold, and network status.

### Ergonomics & Touch Targets
- Zero critical interactive elements below 48px in height or width; standard checkout items scale to 56px.
- 12px spatial gutters prevent accidental adjacent taps when ringing up fast orders.
- Destructive actions (Annuler la vente, Supprimer la ligne) are distanced from frequent validation paths to avoid accidental taps.

## Elevation & Depth

To maximize responsiveness and eliminate sluggish performance on embedded POS chipsets, the interface uses surface tonal layering and crisp borders instead of heavy multi-layered drop shadows.

- **Level 0 (App Canvas)**: `#F7F4F0` solid base.
- **Level 1 (Structural Panes & Default Tiles)**: `#FFFFFF` with a crisp `1px solid #E6E0DA` boundary.
- **Level 2 (Active Touch & Pressed States)**: Inset micro-shading (`inset 0 2px 4px rgba(37, 37, 37, 0.08)`) with border shift to `#B5673B`.
- **Level 3 (Modals & Numeric Keypads / Popovers)**: `#FFFFFF` with a single high-clarity ambient boundary: `0 8px 24px rgba(75, 85, 99, 0.12)`, framed with `1px solid #E6E0DA`.
- Visual layering is communicated via perimeter boundaries and contrasting surface values, guaranteeing immediate readability under bright bakery spot lighting.

## Shapes

The shape architecture pairs soft corners with functional precision:

- **Product & Category Cards**: Standardized at `12px` to `16px` border-radius. This maintains an organic feel without wasting touchable interior screen space.
- **Action Buttons & Keypad Keys**: Radius `12px` ensures clear optical target separation between individual keys.
- **Numeric Badges & Quantity Indicators**: Fully circular or `pill` containers (`rounded-full`) for instant glanceable comprehension.
- **Ticket Containers**: Outer framing uses `16px` radius; internal order row items use clean square cuts with `1px` subtle divider borders to maximize line-item density.

## Components

### 1. Product Cards (Tuiles Produits)
- **Dimensions**: Min-height 110px, min-width 120px.
- **Anatomy**:
  - Image/Icon container (optional upper half or top-left).
  - Item Title: `Work Sans` 14px bold `#252525`, max 2 lines, clamped with clean ellipsis.
  - Price Tag: `Epilogue` 15px bold `#B5673B` positioned at bottom right.
  - Inventory / Freshness indicator badge (e.g., "Chaud", "Rupture", "4 restants"): Top-left pill, 10px font size.
- **Feedback**: Instant `#F7F4F0` fill flash and 0.98 scale micro-compression on touch contact.

### 2. Category Navigator (Réglette Catégories)
- Height: 54px touch bar with pill-shaped tabs.
- Inactive: `#FFFFFF` fill, `1px solid #E6E0DA`, text `#4B5563`.
- Active: `#B5673B` fill, text `#FFFFFF`, bold weight with subtle honey dot accent underneath.

### 3. Active Ticket (Panier / Commande en cours)
- **Line Items**: Fixed height 52px per row.
  - Left: Quantity stepper (+/- buttons 36x36px with bold numerical badge).
  - Center: Product designation with applied modifiers beneath (e.g., "Baguette Tradition - Bien cuite").
  - Right: Total row price formatted in tabular numerals.
  - Swipe or dedicated red ghost trash icon for line deletion.
- **Summary Footer**:
  - Subtotals: Sous-total HT, TVA (5.5% / 10% / 20%), Total TTC.
  - Total Display: High-impact container with `#FAF8F5` surface, displaying `Epilogue` 36px bold currency text in `#252525`.

### 4. Primary Touch Action Buttons
- **Bouton Encaisser (Cash Out)**: Full-width spanning bar, 64px height. Solid `#B5673B` background, `#FFFFFF` text, `Epilogue` 20px semi-bold.
- **Quick Payment Splits**: Dedicated immediate action buttons: "Espèces", "Carte Bancaire", "Sans Contact", "Ticket Resto" (Height: 50px, neutral surface `#FFFFFF`, border `1px solid #E6E0DA`).

### 5. Quick Numeric Keypad (Pavé Numérique)
- Grid: 3x4 layout for rapid decimal price/quantity entry.
- Keys: 56px minimum height, `#FFFFFF` surface, `Epilogue` 22px digits, `1px solid #E6E0DA`.
- Immediate haptic/visual color fill on tap to confirm digit entry.

### 6. Search & Input Fields
- Height: 50px.
- Surface: `#FFFFFF`, border `1.5px solid #E6E0DA`, interior iconography (loupe) in `#8C827A`.
- Active State: Border `#B5673B`, soft honey ring glow `0 0 0 3px rgba(216, 155, 60, 0.2)`.

### 7. Bakery Modifier Sheet (Modal de Cuisson & Découpe)
- Fast overlay drawer containing pre-set chips: "Bien cuit", "Doré", "Blanc", "Tranché (Épais)", "Tranché (Fin)", "Sans sel".
- Chip Target Size: 48px height, rounded-lg, toggling between `#FFFFFF` unselected and `#D89B3C` selected state.