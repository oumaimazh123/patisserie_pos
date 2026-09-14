package ma.elaroui.pos.desktop.presentation.reports

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class SalesReportDateRangeTest {

    private class TestClock(var time: Long) : ma.elaroui.pos.shared.Clock {
        override fun now() = EpochMilliseconds(time)
    }

    private fun setOrderUpdatedAt(db: WindowsPosDatabase, orderId: Long, updatedAt: Long) {
        db.read { c ->
            c.prepareStatement("UPDATE orders SET updated_at = ? WHERE id = ?").use {
                it.setLong(1, updatedAt)
                it.setLong(2, orderId)
                it.executeUpdate()
            }
        }
    }

    @Test
    fun test01_earliestCompletedSaleEpoch_returnsNullOnEmptyDb() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val earliest = db.earliestCompletedSaleEpochMs()
            assertNull(earliest, "Empty database should have null earliestCompletedSaleEpochMs")
        }
    }

    @Test
    fun test02_earliestCompletedSaleEpoch_returnsOldestCompletedSale() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = TestClock(1_700_000_000_000L)
            OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 1_000L)

            // Sale 1 at T1
            val t1 = 1_700_000_100_000L
            clock.time = t1
            val order1Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(101L, "ORD-001", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L)
            val order1 = (order1Res as UseCaseResult.Success).value
            val pay1Res = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(order1.id, PaymentMethod.CASH, order1.totalCentimes, "token-1")
            assertTrue(pay1Res is UseCaseResult.Success, "Payment for order1 should succeed")
            setOrderUpdatedAt(db, order1.id, t1)

            // Sale 2 at T2 (later)
            val t2 = 1_700_000_200_000L
            clock.time = t2
            val order2Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(102L, "ORD-002", OrderType.COUNTER, 1L, listOf(1L to 2), null, 1L)
            val order2 = (order2Res as UseCaseResult.Success).value
            val pay2Res = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(order2.id, PaymentMethod.CARD, order2.totalCentimes, "token-2")
            assertTrue(pay2Res is UseCaseResult.Success, "Payment for order2 should succeed")
            setOrderUpdatedAt(db, order2.id, t2)

            val earliest = db.earliestCompletedSaleEpochMs()
            assertNotNull(earliest)
            assertEquals(t1, earliest, "Earliest sale must correspond to T1 (the first completed sale)")
        }
    }

    @Test
    fun test03_earliestCompletedSaleEpoch_ignoresCancelledOrOpenOrders() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = TestClock(1_700_000_000_000L)
            OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 1_000L)

            // Open order (not completed)
            clock.time = 1_700_000_050_000L
            val ordOpen = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(201L, "ORD-OPEN", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L) as UseCaseResult.Success).value
            setOrderUpdatedAt(db, ordOpen.id, 1_700_000_050_000L)

            // Cancelled order
            clock.time = 1_700_000_060_000L
            val ordCancelled = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(202L, "ORD-CANCEL", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L) as UseCaseResult.Success).value
            db.orders.save(ordCancelled.copy(status = OrderStatus.CANCELLED))
            setOrderUpdatedAt(db, ordCancelled.id, 1_700_000_060_000L)

            // No completed sales yet
            assertNull(db.earliestCompletedSaleEpochMs(), "Cancelled or open orders must not count as completed sales")

            // Now complete a sale at T3
            val t3 = 1_700_000_300_000L
            clock.time = t3
            val ordCompleted = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(203L, "ORD-COMPLETE", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L) as UseCaseResult.Success).value
            val payRes = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(ordCompleted.id, PaymentMethod.CASH, ordCompleted.totalCentimes, "token-3")
            assertTrue(payRes is UseCaseResult.Success, "Payment for completed order should succeed")
            setOrderUpdatedAt(db, ordCompleted.id, t3)

            assertEquals(t3, db.earliestCompletedSaleEpochMs())
        }
    }

    @Test
    fun test04_minDateCalculationLogic() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // Case 1: No sales in DB -> minDate is today
        val noSaleEpoch: Long? = null
        val minDateNoSale = noSaleEpoch?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().coerceAtMost(today)
        } ?: today
        assertEquals(today, minDateNoSale)

        // Case 2: First sale is 15/05/2026
        val targetFirstSale = LocalDate.of(2026, 5, 15)
        val saleEpoch = targetFirstSale.atStartOfDay(zone).toInstant().toEpochMilli()
        val minDateWithSale = Instant.ofEpochMilli(saleEpoch).atZone(zone).toLocalDate().coerceAtMost(today)
        assertEquals(targetFirstSale, minDateWithSale)

        // Case 3: Earliest sale is accidentally in the future -> clamped to today
        val futureSale = today.plusDays(10)
        val futureEpoch = futureSale.atStartOfDay(zone).toInstant().toEpochMilli()
        val minDateFuture = Instant.ofEpochMilli(futureEpoch).atZone(zone).toLocalDate().coerceAtMost(today)
        assertEquals(today, minDateFuture)
    }

    @Test
    fun test05_shortcutsRespectMinDate() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // If minDate is today (e.g. brand new store or first sale today)
        val minDateToday = today
        val yesterdayClamped = today.minusDays(1).coerceAtLeast(minDateToday)
        val last7DaysClamped = today.minusDays(6).coerceAtLeast(minDateToday)
        val thisMonthClamped = today.withDayOfMonth(1).coerceAtLeast(minDateToday)

        assertEquals(today, yesterdayClamped, "Yesterday should clamp to today when minDate is today")
        assertEquals(today, last7DaysClamped, "Last 7 days should clamp to today when minDate is today")
        assertEquals(today, thisMonthClamped, "This month should clamp to today when minDate is today")

        // If minDate is 3 days ago
        val minDate3DaysAgo = today.minusDays(3)
        val last7DaysClampedTo3 = today.minusDays(6).coerceAtLeast(minDate3DaysAgo)
        assertEquals(minDate3DaysAgo, last7DaysClampedTo3, "Last 7 days must clamp to minDate (3 days ago)")
    }

    @Test
    fun test06_customDateRangeQueryRecalculation() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val zone = ZoneId.systemDefault()
            val clock = TestClock(1_700_000_000_000L)
            OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 1_000L)

            val day1 = LocalDate.of(2026, 5, 15)
            val day2 = LocalDate.of(2026, 6, 20)
            val day3 = LocalDate.of(2026, 9, 8)

            // Day 1: Cash sale 200 DH (20_000 centimes)
            val t1 = day1.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val o1 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(301L, "O1", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(o1.id, PaymentMethod.CASH, o1.totalCentimes, "t1")
            setOrderUpdatedAt(db, o1.id, t1)

            // Day 2: Card sale 350 DH (35_000 centimes)
            val t2 = day2.atTime(14, 30).atZone(zone).toInstant().toEpochMilli()
            val o2 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(302L, "O2", OrderType.COUNTER, 1L, listOf(1L to 2), null, 1L) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(o2.id, PaymentMethod.CARD, o2.totalCentimes, "t2")
            setOrderUpdatedAt(db, o2.id, t2)

            // Day 3: Cash sale 150 DH
            val t3 = day3.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            val o3 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(303L, "O3", OrderType.COUNTER, 1L, listOf(1L to 1), null, 1L) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(o3.id, PaymentMethod.CASH, o3.totalCentimes, "t3")
            setOrderUpdatedAt(db, o3.id, t3)

            // Custom Range A: Only Day 1
            val fromA = day1.atStartOfDay(zone).toInstant().toEpochMilli()
            val toA = day1.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val summaryA = db.salesSummary(fromA, toA)
            assertEquals(1, summaryA.completedOrders)
            assertEquals(o1.totalCentimes, summaryA.salesCentimes)
            assertEquals(o1.totalCentimes, summaryA.cashCentimes)
            assertEquals(0L, summaryA.cardCentimes)

            // Custom Range B: Day 1 to Day 2 (inclusive) -> O1 + O2
            val fromB = day1.atStartOfDay(zone).toInstant().toEpochMilli()
            val toB = day2.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val summaryB = db.salesSummary(fromB, toB)
            assertEquals(2, summaryB.completedOrders)
            assertEquals(o1.totalCentimes + o2.totalCentimes, summaryB.salesCentimes)
            assertEquals(o1.totalCentimes, summaryB.cashCentimes)
            assertEquals(o2.totalCentimes, summaryB.cardCentimes)

            // Custom Range C: Full Range Day 1 to Day 3 -> O1 + O2 + O3
            val fromC = day1.atStartOfDay(zone).toInstant().toEpochMilli()
            val toC = day3.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val summaryC = db.salesSummary(fromC, toC)
            assertEquals(3, summaryC.completedOrders)
            assertEquals(o1.totalCentimes + o2.totalCentimes + o3.totalCentimes, summaryC.salesCentimes)
            assertEquals(o1.totalCentimes + o3.totalCentimes, summaryC.cashCentimes)
            assertEquals(o2.totalCentimes, summaryC.cardCentimes)
        }
    }
}
