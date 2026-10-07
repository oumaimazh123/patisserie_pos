package ma.elaroui.pos.desktop.presentation.reports

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules

class DailySalesReportFiltersAndAnalyticsTest {

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
    fun test01_filterByCashier() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = TestClock(1_700_000_000_000L)
            db.createCashier("Cashier 1", "5678")
            db.createCashier("Cashier 2", "9999")
            val cashier1 = db.allUsers().first { it.name == "Cashier 1" }
            val cashier2 = db.allUsers().first { it.name == "Cashier 2" }

            val catId = db.categories.save(Category(0L, "CatCashierTest", true, 1))
            val prodId = db.products.save(Product(0L, catId, "Croissant", 15_00L, 0))

            // Session 1: Cashier 1
            val sess1 = (OpenRegisterSession(db.sessions, clock).execute(10L, 1L, cashier1.id, 100_00L) as UseCaseResult.Success).value
            val order1Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(101L, "ORD-001", OrderType.COUNTER, sess1.id, listOf(prodId to 2), null, cashier1.id)
            val order1 = (order1Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(order1.id, PaymentMethod.CASH, order1.totalCentimes, "token-1")
            setOrderUpdatedAt(db, order1.id, 1_700_000_100_000L)

            // Session 2: Cashier 2
            val sess2 = (OpenRegisterSession(db.sessions, clock).execute(20L, 1L, cashier2.id, 100_00L) as UseCaseResult.Success).value
            val order2Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(102L, "ORD-002", OrderType.COUNTER, sess2.id, listOf(prodId to 4), null, cashier2.id)
            val order2 = (order2Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(order2.id, PaymentMethod.CARD, order2.totalCentimes, "token-2")
            setOrderUpdatedAt(db, order2.id, 1_700_000_200_000L)

            // Test summary with no cashier filter
            val allSummary = db.salesSummary()
            assertEquals(2, allSummary.completedOrders)
            assertEquals(90_00L, allSummary.salesCentimes) // 2*15 + 4*15 = 30 + 60 = 90

            // Test summary with Cashier 1 filter
            val cashier1Summary = db.salesSummary(cashierId = cashier1.id)
            assertEquals(1, cashier1Summary.completedOrders)
            assertEquals(30_00L, cashier1Summary.salesCentimes)
            assertEquals(30_00L, cashier1Summary.cashCentimes)
            assertEquals(0L, cashier1Summary.cardCentimes)

            // Test summary with Cashier 2 filter
            val cashier2Summary = db.salesSummary(cashierId = cashier2.id)
            assertEquals(1, cashier2Summary.completedOrders)
            assertEquals(60_00L, cashier2Summary.salesCentimes)
            assertEquals(0L, cashier2Summary.cashCentimes)
            assertEquals(60_00L, cashier2Summary.cardCentimes)
        }
    }

    @Test
    fun test02_filterByOrderType() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = TestClock(1_700_000_000_000L)
            val owner = db.allUsers().first()

            val catId = db.categories.save(Category(0L, "CatOrderTypeTest", true, 1))
            val prodId = db.products.save(Product(0L, catId, "Café", 10_00L, 0))

            val sess = (OpenRegisterSession(db.sessions, clock).execute(10L, 1L, owner.id, 100_00L) as UseCaseResult.Success).value

            // Order 1: COUNTER
            val o1 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(201L, "ORD-CTR", OrderType.COUNTER, sess.id, listOf(prodId to 1), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o1.id, PaymentMethod.CASH, o1.totalCentimes, "tk-1")
            setOrderUpdatedAt(db, o1.id, 1_700_000_100_000L)

            // Order 2: TAKEAWAY
            val o2 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(202L, "ORD-TKW", OrderType.TAKEAWAY, sess.id, listOf(prodId to 2), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o2.id, PaymentMethod.CASH, o2.totalCentimes, "tk-2")
            setOrderUpdatedAt(db, o2.id, 1_700_000_200_000L)

            // Order 3: DINE_IN
            val o3 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(203L, "ORD-DIN", OrderType.DINE_IN, sess.id, listOf(prodId to 3), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o3.id, PaymentMethod.CARD, o3.totalCentimes, "tk-3")
            setOrderUpdatedAt(db, o3.id, 1_700_000_300_000L)

            val counterSummary = db.salesSummary(orderType = OrderType.COUNTER)
            assertEquals(1, counterSummary.completedOrders)
            assertEquals(10_00L, counterSummary.salesCentimes)

            val takeawaySummary = db.salesSummary(orderType = OrderType.TAKEAWAY)
            assertEquals(1, takeawaySummary.completedOrders)
            assertEquals(20_00L, takeawaySummary.salesCentimes)

            val dineInSummary = db.salesSummary(orderType = OrderType.DINE_IN)
            assertEquals(1, dineInSummary.completedOrders)
            assertEquals(30_00L, dineInSummary.salesCentimes)

            val allSummary = db.salesSummary(orderType = null)
            assertEquals(3, allSummary.completedOrders)
            assertEquals(60_00L, allSummary.salesCentimes)
        }
    }

    @Test
    fun test03_filterByCategoryHierarchy() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = TestClock(1_700_000_000_000L)
            val owner = db.allUsers().first()

            // Hierarchy: Root "CatHierarchyRoot" -> "CatHierarchyMid" -> "CatHierarchyLeaf"
            // Independent: "CatHierarchyOther"
            val catRoot = db.categories.save(Category(0L, "CatHierarchyRoot", true, 1, parentId = null))
            val catGateaux = db.categories.save(Category(0L, "CatHierarchyMid", true, 2, parentId = catRoot))
            val catTartes = db.categories.save(Category(0L, "CatHierarchyLeaf", true, 3, parentId = catGateaux))
            val catBoulangerie = db.categories.save(Category(0L, "CatHierarchyOther", true, 4, parentId = null))

            val allCats = db.categories.observeAll().first()
            // Verify hierarchy helper
            val rootDescendants = CategoryHierarchyRules.getAllDescendantIds(catRoot, allCats)
            assertEquals(setOf(catGateaux, catTartes), rootDescendants)

            val prodTarte = db.products.save(Product(0L, catTartes, "Tarte Citron", 25_00L, 0))
            val prodBaguette = db.products.save(Product(0L, catBoulangerie, "Baguette", 5_00L, 0))

            val sess = (OpenRegisterSession(db.sessions, clock).execute(10L, 1L, owner.id, 100_00L) as UseCaseResult.Success).value

            // Order 1: contains Tarte Citron (leaf of CatHierarchyRoot)
            val o1 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(301L, "ORD-CAT1", OrderType.COUNTER, sess.id, listOf(prodTarte to 2), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o1.id, PaymentMethod.CASH, o1.totalCentimes, "tk-c1")
            setOrderUpdatedAt(db, o1.id, 1_700_000_100_000L)

            // Order 2: contains Baguette (CatHierarchyOther)
            val o2 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(302L, "ORD-CAT2", OrderType.COUNTER, sess.id, listOf(prodBaguette to 4), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o2.id, PaymentMethod.CARD, o2.totalCentimes, "tk-c2")
            setOrderUpdatedAt(db, o2.id, 1_700_000_200_000L)

            // Filtering by Root (and its descendants: catRoot, catGateaux, catTartes)
            val patisserieMatchingIds = setOf(catRoot) + rootDescendants
            val patisserieSummary = db.salesSummary(matchingCategoryIds = patisserieMatchingIds)
            assertEquals(1, patisserieSummary.completedOrders)
            assertEquals(50_00L, patisserieSummary.salesCentimes)

            val patisserieAnalytics = db.retailSalesAnalytics(0L, Long.MAX_VALUE, matchingCategoryIds = patisserieMatchingIds)
            assertEquals(1, patisserieAnalytics.byProduct.size)
            assertEquals("Tarte Citron", patisserieAnalytics.byProduct.first().label)
            assertEquals(2, patisserieAnalytics.byProduct.first().quantity)
            assertEquals(50_00L, patisserieAnalytics.byProduct.first().amountCentimes)

            // Filtering by Other
            val otherMatchingIds = setOf(catBoulangerie) + CategoryHierarchyRules.getAllDescendantIds(catBoulangerie, allCats)
            val boulangerieSummary = db.salesSummary(matchingCategoryIds = otherMatchingIds)
            assertEquals(1, boulangerieSummary.completedOrders)
            assertEquals(20_00L, boulangerieSummary.salesCentimes)

            val boulangerieAnalytics = db.retailSalesAnalytics(0L, Long.MAX_VALUE, matchingCategoryIds = otherMatchingIds)
            assertEquals(1, boulangerieAnalytics.byProduct.size)
            assertEquals("Baguette", boulangerieAnalytics.byProduct.first().label)
            assertEquals(4, boulangerieAnalytics.byProduct.first().quantity)
            assertEquals(20_00L, boulangerieAnalytics.byProduct.first().amountCentimes)
        }
    }

    @Test
    fun test04_salesEvolution_hourlyAndDaily() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val zone = ZoneId.of("UTC")
            val baseDay = LocalDate.of(2026, 10, 7)
            val dayStart = baseDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = baseDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

            val clock = TestClock(dayStart)
            val owner = db.allUsers().first()
            val catId = db.categories.save(Category(0L, "TestCat", true, 1))
            val prodId = db.products.save(Product(0L, catId, "Eclair", 20_00L, 0))

            val sess = (OpenRegisterSession(db.sessions, clock).execute(10L, 1L, owner.id, 100_00L) as UseCaseResult.Success).value

            // Order at 10:30 UTC
            val t10h30 = baseDay.atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
            val o1 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(401L, "ORD-10H", OrderType.COUNTER, sess.id, listOf(prodId to 1), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o1.id, PaymentMethod.CASH, o1.totalCentimes, "tk-ev1")
            setOrderUpdatedAt(db, o1.id, t10h30)

            // Order at 14:15 UTC
            val t14h15 = baseDay.atTime(14, 15).atZone(zone).toInstant().toEpochMilli()
            val o2 = (CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(402L, "ORD-14H", OrderType.COUNTER, sess.id, listOf(prodId to 3), null, owner.id) as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(o2.id, PaymentMethod.CASH, o2.totalCentimes, "tk-ev2")
            setOrderUpdatedAt(db, o2.id, t14h15)

            // 1. Hourly evolution
            val hourlyPoints = db.salesEvolution(
                fromEpoch = dayStart,
                toEpoch = dayEnd,
                isHourly = true,
                zoneId = zone
            )

            assertTrue(hourlyPoints.isNotEmpty())
            val pt10 = hourlyPoints.firstOrNull { it.label == "10h" }
            assertNotNull(pt10)
            assertEquals(20_00L, pt10.amountCentimes)
            assertEquals(1, pt10.orderCount)

            val pt14 = hourlyPoints.firstOrNull { it.label == "14h" }
            assertNotNull(pt14)
            assertEquals(60_00L, pt14.amountCentimes)
            assertEquals(1, pt14.orderCount)

            // 2. Daily evolution over 3 days
            val threeDaysEnd = baseDay.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val dailyPoints = db.salesEvolution(
                fromEpoch = dayStart,
                toEpoch = threeDaysEnd,
                isHourly = false,
                zoneId = zone
            )

            assertEquals(3, dailyPoints.size)
            assertEquals("07/10", dailyPoints[0].label)
            assertEquals(80_00L, dailyPoints[0].amountCentimes)
            assertEquals(2, dailyPoints[0].orderCount)

            assertEquals("08/10", dailyPoints[1].label)
            assertEquals(0L, dailyPoints[1].amountCentimes)
            assertEquals(0, dailyPoints[1].orderCount)
        }
    }
}
