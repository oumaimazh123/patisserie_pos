package ma.elaroui.pos.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive retail migration. Restaurant compatibility tables and IDs are left
 * untouched so existing installations and historical receipts remain valid.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN sku TEXT")
        db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")
        db.execSQL("ALTER TABLE orders ADD COLUMN discountCentimes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE order_items ADD COLUMN categoryIdSnapshot INTEGER")
        db.execSQL("ALTER TABLE order_items ADD COLUMN categoryNameSnapshot TEXT")
    }
}
