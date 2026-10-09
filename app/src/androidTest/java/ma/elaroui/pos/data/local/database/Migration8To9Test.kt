package ma.elaroui.pos.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class Migration8To9Test {
    @Test
    fun retailMigrationPreservesPricesAndAddsNullableIdentifiersAndSnapshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audit-retail-migration-${System.nanoTime()}.db"
        fun helper(version: Int) = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY, name TEXT NOT NULL, priceCentimes INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY, totalCentimes INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE order_items(id INTEGER PRIMARY KEY, productNameSnapshot TEXT NOT NULL)")
                        db.execSQL("INSERT INTO products VALUES(1,'Cake',1500),(2,'Tea',1000)")
                        db.execSQL("INSERT INTO orders VALUES(1,2500)")
                        db.execSQL("INSERT INTO order_items VALUES(1,'Original cake')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        MIGRATION_8_9.migrate(db)
                    }
                }).build()
        )
        try {
            helper(8).use { it.writableDatabase }
            helper(9).use { migrated ->
                val db = migrated.writableDatabase
                db.query("SELECT name,priceCentimes,sku,barcode FROM products ORDER BY id").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Cake", it.getString(0))
                    assertEquals(1500L, it.getLong(1))
                    assertTrue(it.isNull(2)); assertTrue(it.isNull(3))
                    assertEquals(2, it.count)
                }
                db.query("SELECT totalCentimes,discountCentimes FROM orders").use {
                    assertTrue(it.moveToFirst()); assertEquals(2500L, it.getLong(0)); assertEquals(0L, it.getLong(1))
                }
                db.query("SELECT productNameSnapshot,categoryIdSnapshot,categoryNameSnapshot FROM order_items").use {
                    assertTrue(it.moveToFirst()); assertEquals("Original cake", it.getString(0))
                    assertTrue(it.isNull(1)); assertTrue(it.isNull(2))
                }
                db.execSQL("UPDATE products SET barcode='123' WHERE id=1")
                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    db.execSQL("UPDATE products SET barcode='123' WHERE id=2")
                }
                db.query("PRAGMA integrity_check").use {
                    assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
