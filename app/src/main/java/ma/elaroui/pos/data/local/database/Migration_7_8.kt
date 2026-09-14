package ma.elaroui.pos.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds an optional managed local image path while preserving every existing dining area. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dining_areas ADD COLUMN imagePath TEXT")
    }
}
