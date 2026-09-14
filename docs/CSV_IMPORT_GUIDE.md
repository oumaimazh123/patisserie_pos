# PATISSERIE_POS CSV import

CSV import is offline. Files must be UTF-8 and may use either semicolons or commas. For French decimal values, semicolons are recommended.

## Import order

1. Sign in as the owner.
2. Open **Management → Categories** and choose **Import CSV**.
3. Select the category CSV and review the import summary.
4. Open **Management → Products** and choose **Import CSV**.
5. Select the product CSV and review imported, skipped, and failed rows.

Product import only accepts categories that already exist and are active, so categories must be imported first.

## Category format

```csv
name;display_order;active
Clothing;1;true
Accessories;2;true
```

| Column | Required | Format |
|---|---|---|
| `name` | Yes | Unique category name |
| `display_order` | No | Whole number, starting at 0 or 1 |
| `active` | No | `true/false`, `1/0`, `yes/no`, or `oui/non`; default is `true` |

French headers `nom`, `ordre`, and `actif` are also accepted.

## Product format

```csv
name;category;price;tva;available;active;sku;barcode;image
Cotton T-shirt;Clothing;149,90;20;true;true;TSHIRT-BLK-M;6110000000012;images/tshirt-black.jpg
USB-C cable;Accessories;79,00;20;true;true;USB-C-1M;6110000000013;
```

| Column | Required | Format |
|---|---|---|
| `name` | Yes | Product name; duplicate name in the same category is skipped |
| `category` | Yes | Exact name of an existing active category |
| `price` | Yes | Positive amount such as `18,50` or `18.50`, in MAD—not centimes |
| `tva` | No | Percentage such as `20` or `20%`; default is `10` |
| `available` | No | Boolean; default is `true` |
| `active` | No | Boolean; default is `true` |
| `sku` | No | Unique internal reference |
| `barcode` | No | Unique EAN/UPC/scanner value; keep it as text in spreadsheet software |
| `image` | No | Absolute image path or a path relative to the product CSV |

Accepted image formats are PNG, JPG/JPEG, and WebP. Images are validated and copied into PATISSERIE_POS managed storage, so the original import folder can be removed after a successful import and backup.

## Easy bulk image upload

Keep the CSV and its images together:

```text
patisserie-pos-import/
├── categories.csv
├── products.csv
└── images/
    ├── croissant.jpg
    └── eclair-chocolat.webp
```

Enter `images/croissant.jpg` in the product row's `image` column. PATISSERIE_POS resolves it relative to `products.csv`, validates it, copies it to the application data folder, and links it to the imported product automatically.

For one product, use **Management → Products → Add/Edit → Add image**. This opens the native Ubuntu file picker and shows a preview before saving.

## Spreadsheet notes

- Export as **CSV UTF-8**.
- Preserve barcodes as text so leading zeroes are not removed and scientific notation is not used.
- Quote fields containing the selected delimiter or quotation marks.
- A malformed row does not cancel valid rows; the summary lists failures by line number.
- Existing products are not overwritten by CSV import.
