package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.presentation.pos.active.formatOrderCreationDateTime
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import kotlin.test.*

class ActiveOrderDateTimeFormatAndPersistenceTest {

    @Test
    fun testDateFormatPatternProducesExactMMddHHmm() {
        // Test with a fixed date: 2026-09-05 14:32:00
        val targetDateTime = LocalDateTime.of(2026, 9, 5, 14, 32, 0)
        val epochMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val formatted = formatOrderCreationDateTime(epochMillis)
        assertEquals("09/05 14:32", formatted)
    }

    @Test
    fun testDateFormatWithLeadingZeros() {
        // Test with single digit month, day, hour, minute: 2026-01-03 04:08:00
        val targetDateTime = LocalDateTime.of(2026, 1, 3, 4, 8, 0)
        val epochMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val formatted = formatOrderCreationDateTime(epochMillis)
        assertEquals("01/03 04:08", formatted)
    }

    @Test
    fun testDateFormatWithZeroOrNegativeTimestampReturnsPlaceholder() {
        assertEquals("—", formatOrderCreationDateTime(0L))
        assertEquals("—", formatOrderCreationDateTime(-1L))
    }

    @Test
    fun testOrderCreationPersistsCreatedAtAndRemainsUnchangedOnOrderUpdate() = runBlocking {
        val tempDir = Files.createTempDirectory("pos-order-datetime-test")
        val dbFile = tempDir.resolve("pos.db")

        var originalCreatedAt: Long = 0L
        var createdOrderId: Long = 0L

        WindowsPosDatabase.open(dbFile).use { db ->
            db.configureInitialSetup("Store", "Owner", "1234")
            val catId = db.categories.save(ma.elaroui.pos.shared.domain.Category(0, "Cat", true, 0))
            val prodId = db.products.save(ma.elaroui.pos.shared.domain.Product(0, catId, "Item A", 2000, 2000, true, true))

            val session = db.sessions.save(
                ma.elaroui.pos.shared.domain.RegisterSession(
                    id = 1L,
                    status = ma.elaroui.pos.shared.domain.RegisterSessionStatus.OPEN,
                    openingCashCentimes = 10000,
                    registerId = 1,
                    cashierId = 1,
                    openedAtEpochMilliseconds = System.currentTimeMillis(),
                    closedAtEpochMilliseconds = null,
                    expectedCashCentimes = null,
                    countedCashCentimes = null,
                    differenceCentimes = null
                )
            )

            val createResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 0L,
                number = "CMD-001",
                type = OrderType.COUNTER,
                sessionId = session,
                lineItems = listOf(prodId to 2),
                tableId = null,
                cashierId = 1L
            )
            val order = assertIs<UseCaseResult.Success<ma.elaroui.pos.shared.domain.Order>>(createResult).value
            createdOrderId = order.id
            assertTrue(createdOrderId > 0L)

            val loaded = db.orders.findById(createdOrderId)
            assertNotNull(loaded)
            assertTrue(loaded.createdAtEpochMilliseconds > 0L, "Order createdAt must be populated")
            originalCreatedAt = loaded.createdAtEpochMilliseconds

            // Simulate modifying/updating the order (e.g. adding item / changing status / updating)
            Thread.sleep(50) // ensure time advances
            val modifiedOrder = loaded.copy(
                totalCentimes = 5000,
                lines = loaded.lines
            )
            db.orders.save(modifiedOrder)

            val reloadedAfterUpdate = db.orders.findById(createdOrderId)
            assertNotNull(reloadedAfterUpdate)
            assertEquals(
                originalCreatedAt,
                reloadedAfterUpdate.createdAtEpochMilliseconds,
                "Order createdAt must remain strictly identical after update"
            )
        }

        // Reopen database from disk and verify persistence across restart
        WindowsPosDatabase.open(dbFile).use { reopenedDb ->
            val reloadedAfterRestart = reopenedDb.orders.findById(createdOrderId)
            assertNotNull(reloadedAfterRestart)
            assertEquals(
                originalCreatedAt,
                reloadedAfterRestart.createdAtEpochMilliseconds,
                "Order createdAt must remain strictly identical across database reopen/restart"
            )
            val openOrders = reopenedDb.orders.observeOpen(1L).first()
            val openOrder = openOrders.firstOrNull { it.id == createdOrderId }
            assertNotNull(openOrder)
            assertEquals(originalCreatedAt, openOrder.createdAtEpochMilliseconds)
        }
    }
}
