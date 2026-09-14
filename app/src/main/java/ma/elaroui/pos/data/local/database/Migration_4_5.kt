package ma.elaroui.pos.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add completedByUserId to orders table
        db.execSQL("ALTER TABLE orders ADD COLUMN completedByUserId INTEGER DEFAULT NULL")

        // Add columns to payments table
        db.execSQL("ALTER TABLE payments ADD COLUMN createdByUserId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
        db.execSQL("ALTER TABLE payments ADD COLUMN externalReference TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE payments ADD COLUMN submissionToken TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN note TEXT DEFAULT NULL")

        // Add indices for payments
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_method ON payments(method)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_status ON payments(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_createdAt ON payments(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_createdByUserId ON payments(createdByUserId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payments_orderId_submissionToken ON payments(orderId, submissionToken)")

        // Create audit_logs table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                actionType TEXT NOT NULL,
                actingUserId INTEGER NOT NULL,
                approvedByOwnerId INTEGER DEFAULT NULL,
                entityType TEXT NOT NULL,
                entityId INTEGER DEFAULT NULL,
                timestamp INTEGER NOT NULL,
                details TEXT DEFAULT NULL,
                result TEXT NOT NULL DEFAULT 'SUCCESS'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_actionType ON audit_logs(actionType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_actingUserId ON audit_logs(actingUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_timestamp ON audit_logs(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_entityType ON audit_logs(entityType)")

        // Create printer_settings table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS printer_settings (
                id INTEGER PRIMARY KEY NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 0,
                printerType TEXT NOT NULL DEFAULT 'ANDROID_SYSTEM',
                printerName TEXT DEFAULT NULL,
                printerAddress TEXT DEFAULT NULL,
                paperWidth INTEGER NOT NULL DEFAULT 80,
                copies INTEGER NOT NULL DEFAULT 1,
                autoPrint INTEGER NOT NULL DEFAULT 0,
                printLogo INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
