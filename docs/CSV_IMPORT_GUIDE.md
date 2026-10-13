# PATISSERIE_POS — Catalogue CSV import

Verified against `desktopApp/src/desktopMain/kotlin/ma/elaroui/pos/desktop/importing/CsvImports.kt` and
`DesktopNavState.executeCatalogImport` (`DesktopNavGraph.kt`). The importer is available to the **owner**
from the product and category management screens (dialog `CatalogImportDialog.kt`).

> The previous version of this guide described `name;category;price;tva;…` files. That format is **not**
> supported. Only the 16-column catalogue format below is accepted.

## File rules

- UTF-8 (a BOM is stripped), extension `.csv`.
- Delimiter is **auto-detected from the header line**: `;` if it contains more semicolons than commas,
  otherwise `,`. Standard CSV quoting (`"…"`, `""`) is supported; blank rows are ignored.
- The header row must contain all 16 columns (case-insensitive; `_` and `-` are treated like spaces):

| # | Column | Required | Notes |
| :-: | :--- | :-: | :--- |
| 1 | `Product ID` | **yes** | Stored as the product **SKU**. Must be unique within the file (case-insensitive). If a product with this SKU already exists it is **updated** (see below). |
| 2 | `Main Category` | **yes** | Level-1 category (created if missing) |
| 3 | `Main Category Image` | no | Path (absolute, or relative to the CSV file) or `http(s)` URL |
| 4–9 | `Sub-Category 1`, `…Image`, `Sub-Category 2`, `…Image`, `Sub-Category 3`, `…Image` | no | Up to 3 sub-levels. **No gaps**: Sub-Category 2 requires Sub-Category 1, etc. |
| 10 | `Product Name (French)` | **yes** | Product name |
| 11 | `Product Name (Arabic)` | no | `name_arabic` |
| 12 | `Product Image` | no | Path or URL |
| 13 | `Unit / Packaging` | no | e.g. `Pièce`, `Kg` |
| 14 | `Price (MAD)` | **yes** | Single fixed positive price in dirhams (`6.50` or `6,50`); ranges (`10-12`, `10 à 12`) are rejected. Converted to centimes. |
| 15 | `TVA` | **yes** | Exactly `0`, `10`, `20` (optionally with `%`) → 0 / 1000 / 2000 basis points |
| 16 | `Description` | no | |

An official sample can be generated with `CsvImports.generateOfficialTemplate()` (Moroccan bakery examples).

## Processing

1. **Analysis (preview)**: every row is validated; errors (missing mandatory field, duplicate Product ID,
   level gap, invalid price or TVA) make `canImport = false`; missing image files are **warnings** only.
   The preview shows the category tree, counts by level, TVA breakdown and images found/missing.
2. **Import** (only when the analysis has no errors and at least one valid row): for each row the category
   path is resolved/created, images are copied into the managed `data/images` folder, then the product is
   created — or, when a product with the same SKU exists, **overwritten** (category, name, price, tax;
   `active` and `available` are set back to `true`; Arabic name/unit/description/image are kept when the CSV
   cell is empty).
3. Rows are applied **one by one, not in a single transaction**: a failure on one row is reported and the
   import continues; rows already applied stay applied.

## Known caveats (see `docs/context/10_known_issues_and_technical_debt.md`)

- KI-06: the price is parsed through `Double` (`toDoubleOrNull`) and multiplied by 100.0; verify unusual
  values after import.
- KI-16: existing products with the same SKU are silently overwritten and re-activated; back up first.
- The category/product uniqueness rules of the catalogue still apply (duplicate name in the same category,
  duplicate barcode); the CSV has no barcode column — set barcodes in Product management afterwards.
