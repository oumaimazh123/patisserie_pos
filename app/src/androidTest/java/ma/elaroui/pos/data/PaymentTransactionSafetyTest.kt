package ma.elaroui.pos.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.data.local.database.POSDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentTransactionSafetyTest {

    @Test
    fun transactionRollsBackAndUniqueTokenPreventsDuplicatePayment() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, POSDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sql = database.openHelper.writableDatabase
        try {
            sql.execSQL(
                "CREATE TABLE payment_safety_probe (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "orderId INTEGER NOT NULL, token TEXT NOT NULL)"
            )
            sql.execSQL(
                "CREATE UNIQUE INDEX payment_safety_token " +
                    "ON payment_safety_probe(orderId, token)"
            )

            runCatching {
                database.withTransaction {
                    sql.execSQL(
                        "INSERT INTO payment_safety_probe(orderId, token) VALUES(1, 'rollback')"
                    )
                    error("simulated persistence failure")
                }
            }
            assertEquals(0, count(sql, "rollback"))

            database.withTransaction {
                sql.execSQL(
                    "INSERT INTO payment_safety_probe(orderId, token) VALUES(1, 'same-token')"
                )
            }
            runCatching {
                database.withTransaction {
                    sql.execSQL(
                        "INSERT INTO payment_safety_probe(orderId, token) VALUES(1, 'same-token')"
                    )
                }
            }
            assertEquals(1, count(sql, "same-token"))
        } finally {
            database.close()
        }
    }

    private fun count(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        token: String
    ): Int = database.query(
        "SELECT COUNT(*) FROM payment_safety_probe WHERE token = ?",
        arrayOf(token)
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
