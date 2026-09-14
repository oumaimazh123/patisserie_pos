package ma.elaroui.pos.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds optional buyer invoice identity fields without changing existing orders.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN buyerCompanyName TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN buyerAddress TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN buyerIce TEXT")
    }
}
