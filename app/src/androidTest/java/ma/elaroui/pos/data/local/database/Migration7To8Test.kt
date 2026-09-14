package ma.elaroui.pos.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    @Test
    fun migrationPreservesExistingDiningAreasAndAddsNullableImagePath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-area-image-${System.nanoTime()}.db"
        fun helper(version: Int, onUpgrade: ((SupportSQLiteDatabase) -> Unit)? = null) =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE dining_areas(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,displayOrder INTEGER NOT NULL,active INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
                            db.execSQL("INSERT INTO dining_areas(name,displayOrder,active,createdAt,updatedAt) VALUES('Terrasse',0,1,1,1)")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            onUpgrade?.invoke(db)
                        }
                    }).build()
            )

        helper(7).use { it.writableDatabase }
        helper(8) { MIGRATION_7_8.migrate(it) }.use { migrated ->
            migrated.writableDatabase.query("SELECT name,imagePath FROM dining_areas").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Terrasse", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        }
        context.deleteDatabase(name)
    }
}
