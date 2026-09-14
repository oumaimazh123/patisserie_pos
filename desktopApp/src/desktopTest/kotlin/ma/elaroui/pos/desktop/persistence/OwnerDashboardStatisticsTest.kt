package ma.elaroui.pos.desktop.persistence

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OwnerDashboardStatisticsTest {

    private class TestClock(var time: Long) : Clock {
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
    fun `empty database returns zeros for today statistics`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val summary = db.todaySalesSummary()
            assertEquals(0, summary.completedOrders)
            assertEquals(0L, summary.salesCentimes)
            assertEquals(0L, summary.cashCentimes)
            assertEquals(0L, summary.cardCentimes)
        }
    }

    @Test
    fun `yesterday sales are strictly excluded from today statistics`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin Test", "Owner", "9999")
            val cashierId = db.createCashier("Amina", "1234")

            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.now(zone)
            val yesterdayDate = todayDate.minusDays(1)

            val yesterdayMiddayMs = yesterdayDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val todayMiddayMs = todayDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

            val clock = TestClock(yesterdayMiddayMs)
            assertIs<UseCaseResult.Success<*>>(OpenRegisterSession(db.sessions, clock).execute(10L, 1L, cashierId, 0L))

            val catId = db.categories.findByName("Boissons")?.id ?: db.categories.save(Category(0L, "Cat", true, 1))
            val p1 = db.products.save(Product(0L, catId, "Article 1", 5_000L, 0)) // 50 DH
            val p2 = db.products.save(Product(0L, catId, "Article 2", 7_000L, 0)) // 70 DH

            // 1. Yesterday Cash Order: 50 DH
            val orderYesterdayCash = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    101L, "YEST-CASH", OrderType.COUNTER, 10L, listOf(p1 to 1), null, cashierId
                )
            ).value
            assertIs<UseCaseResult.Success<*>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    orderYesterdayCash.id, PaymentMethod.CASH, 5_000L, "token-y-cash", cashierId
                )
            )
            setOrderUpdatedAt(db, orderYesterdayCash.id, yesterdayMiddayMs)

            // 2. Yesterday Card Order: 70 DH
            val orderYesterdayCard = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    102L, "YEST-CARD", OrderType.COUNTER, 10L, listOf(p2 to 1), null, cashierId
                )
            ).value
            assertIs<UseCaseResult.Success<*>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    orderYesterdayCard.id, PaymentMethod.CARD, null, "token-y-card", cashierId
                )
            )
            setOrderUpdatedAt(db, orderYesterdayCard.id, yesterdayMiddayMs)

            // Verify: At todayMidday, todaySalesSummary MUST be strictly 0 (yesterday's sales excluded)
            val todaySummary = db.todaySalesSummary(zoneId = zone, nowEpochMs = todayMiddayMs)
            assertEquals(0, todaySummary.completedOrders, "Yesterday orders must not be counted in today's completedOrders")
            assertEquals(0L, todaySummary.salesCentimes, "Yesterday sales must not appear in today's salesCentimes")
            assertEquals(0L, todaySummary.cashCentimes, "Yesterday cash must not appear in today's cashCentimes")
            assertEquals(0L, todaySummary.cardCentimes, "Yesterday card must not appear in today's cardCentimes")

            // But historical report for yesterday still accurately preserves the sales (Rule 9)
            val yesterdayStart = yesterdayDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val yesterdayEnd = yesterdayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val yesterdaySummary = db.salesSummary(yesterdayStart, yesterdayEnd)
            assertEquals(2, yesterdaySummary.completedOrders)
            assertEquals(12_000L, yesterdaySummary.salesCentimes)
            assertEquals(5_000L, yesterdaySummary.cashCentimes)
            assertEquals(7_000L, yesterdaySummary.cardCentimes)
        }
    }

    @Test
    fun `today statistics include only paid completed orders and sum cash plus card`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin Test", "Owner", "9999")
            val cashierId = db.createCashier("Amina", "1234")

            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.now(zone)
            val today10am = todayDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val today14pm = todayDate.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
            val today16pm = todayDate.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()

            val clock = TestClock(today10am)
            assertIs<UseCaseResult.Success<*>>(OpenRegisterSession(db.sessions, clock).execute(20L, 1L, cashierId, 0L))

            val catId = db.categories.findByName("Boissons")?.id ?: db.categories.save(Category(0L, "Cat", true, 1))
            val p1 = db.products.save(Product(0L, catId, "Cafe", 2_000L, 0)) // 20 DH
            val p2 = db.products.save(Product(0L, catId, "Plat", 6_000L, 0)) // 60 DH

            // Order 1: 2x Cafe = 40 DH, paid CASH at 10:00
            val order1 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    201L, "TODAY-1", OrderType.COUNTER, 20L, listOf(p1 to 2), null, cashierId
                )
            ).value
            assertIs<UseCaseResult.Success<*>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order1.id, PaymentMethod.CASH, 4_000L, "token-today-1", cashierId
                )
            )
            setOrderUpdatedAt(db, order1.id, today10am)

            // Order 2: 1x Plat = 60 DH, paid CARD at 14:00
            clock.time = today14pm
            val order2 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    202L, "TODAY-2", OrderType.COUNTER, 20L, listOf(p2 to 1), null, cashierId
                )
            ).value
            assertIs<UseCaseResult.Success<*>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order2.id, PaymentMethod.CARD, null, "token-today-2", cashierId
                )
            )
            setOrderUpdatedAt(db, order2.id, today14pm)

            // Order 3: 1x Cafe + 1x Plat = 80 DH, paid CASH at 16:00
            clock.time = today16pm
            val order3 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    203L, "TODAY-3", OrderType.COUNTER, 20L, listOf(p1 to 1, p2 to 1), null, cashierId
                )
            ).value
            assertIs<UseCaseResult.Success<*>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order3.id, PaymentMethod.CASH, 10_000L, "token-today-3", cashierId
                )
            )
            setOrderUpdatedAt(db, order3.id, today16pm)

            // Order 4: Unpaid OPEN order
            CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                204L, "TODAY-OPEN", OrderType.COUNTER, 20L, listOf(p2 to 2), null, cashierId
            )

            // Order 5: Cancelled order
            val order5 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    205L, "TODAY-CANCELLED", OrderType.COUNTER, 20L, listOf(p1 to 1), null, cashierId
                )
            ).value
            db.orders.save(order5.copy(status = OrderStatus.CANCELLED))

            // Query todaySalesSummary at 18:00 today
            val today18pm = todayDate.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            val summary = db.todaySalesSummary(zoneId = zone, nowEpochMs = today18pm)

            // Rule 4: number of orders counts only paid/completed orders today (3 orders)
            assertEquals(3, summary.completedOrders)
            // Rule 5: Espèces = uniquement les paiements Cash d'aujourd'hui (40 DH + 80 DH = 120 DH)
            assertEquals(12_000L, summary.cashCentimes)
            // Rule 6: Carte / TPE = uniquement les paiements Carte/TPE d'aujourd'hui (60 DH)
            assertEquals(6_000L, summary.cardCentimes)
            // Rule 3: Ventes Aujourd'hui = somme Cash + Carte/TPE d'aujourd'hui (120 DH + 60 DH = 180 DH)
            assertEquals(18_000L, summary.salesCentimes)
            assertEquals(summary.cashCentimes + summary.cardCentimes, summary.salesCentimes)

            // Rule 8: Au changement de journée, les compteurs repartent sur les ventes du nouveau jour
            val tomorrowMiddayMs = todayDate.plusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val tomorrowSummary = db.todaySalesSummary(zoneId = zone, nowEpochMs = tomorrowMiddayMs)
            assertEquals(0, tomorrowSummary.completedOrders, "On tomorrow's date, completedOrders must reset to 0")
            assertEquals(0L, tomorrowSummary.salesCentimes, "On tomorrow's date, salesCentimes must reset to 0")
            assertEquals(0L, tomorrowSummary.cashCentimes, "On tomorrow's date, cashCentimes must reset to 0")
            assertEquals(0L, tomorrowSummary.cardCentimes, "On tomorrow's date, cardCentimes must reset to 0")
        }
    }
}
