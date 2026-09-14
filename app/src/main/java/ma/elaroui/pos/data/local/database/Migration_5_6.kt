package ma.elaroui.pos.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenEnabled INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenPrinterType TEXT NOT NULL DEFAULT 'ETHERNET_ESCPOS'"
        )
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenPrinterName TEXT"
        )
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenPrinterAddress TEXT"
        )
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenPaperWidth INTEGER NOT NULL DEFAULT 80"
        )
        database.execSQL(
            "ALTER TABLE printer_settings ADD COLUMN kitchenAutoPrint INTEGER NOT NULL DEFAULT 1"
        )
    }
}
