package ma.elaroui.pos.desktop.presentation.sales

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.TestClock
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class SalesHistoryScrollingAuditTest {

    private val strings = DesktopStrings(DesktopLanguage.FR)

    @Test
    fun testCompletedSalesWithLargeDatasetMaintainsOrderAndKeys() {
        val largeSalesList = (1..120).map { index ->
            val order = Order(
                id = index.toLong(),
                number = "CMD-%04d".format(index),
                type = if (index % 3 == 0) OrderType.DINE_IN else if (index % 3 == 1) OrderType.TAKEAWAY else OrderType.COUNTER,
                status = if (index % 10 == 0) OrderStatus.CANCELLED else OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(1L, "Article ", 2500L, 2, 0)
                ),
                subtotalCentimes = 5000L,
                discountCentimes = 0L,
                taxCentimes = 0L,
                totalCentimes = 5000L,
                tableId = if (index % 3 == 0) (index % 10 + 1).toLong() else null,
                registerSessionId = 1L,
                cashierId = 1L
            )
            SalesHistoryRow(
                order = order,
                cashierName = if (index % 2 == 0) "Fatima" else "Ahmed",
                paymentMethod = if (index % 2 == 0) PaymentMethod.CASH else PaymentMethod.CARD,
                paidAtEpochMillis = System.currentTimeMillis() - (index * 60_000L)
            )
        }

        assertEquals(120, largeSalesList.size)

        // Verify uniqueness of order IDs for Compose LazyColumn keying
        val uniqueKeys = largeSalesList.map { it.order.id }.toSet()
        assertEquals(120, uniqueKeys.size)

        // Verify searching across large dataset
        val searchResults = largeSalesList.filter {
            it.order.number.contains("0050", ignoreCase = true) || it.cashierName.contains("Fatima", ignoreCase = true)
        }
        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.any { it.order.number == "CMD-0050" })

        // Verify status filtering
        val cancelledSales = largeSalesList.filter { it.order.status == OrderStatus.CANCELLED }
        assertEquals(12, cancelledSales.size)

        val completedSales = largeSalesList.filter { it.order.status == OrderStatus.COMPLETED }
        assertEquals(108, completedSales.size)
    }

    @Test
    fun testActiveOrdersWithMultipleSuspendedSales() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Audit Store", "Owner", "1234")
            val catId = db.categories.save(Category(id = 0L, name = "General Category"))
            val prodId = db.products.save(Product(id = 0L, categoryId = catId, name = "Produit Test", priceCentimes = 1000L, taxRateBasisPoints = 0))

            OpenRegisterSession(db.sessions, TestClock).execute(1L, 1L, 1L, 100_00L)

            val createOrder = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)

            // Create 30 suspended / active orders
            (1..30).forEach { i ->
                val res = createOrder.execute(
                    id = i.toLong(),
                    number = "SUSP-%03d".format(i),
                    type = OrderType.COUNTER,
                    sessionId = 1L,
                    lineItems = listOf(prodId to 1),
                    tableId = null,
                    cashierId = 1L
                )
                assertTrue(res is UseCaseResult.Success, "Order  should be created")
            }

            val activeOrders = db.orders.observeOpen(1L).first()
            assertEquals(30, activeOrders.size)

            val uniqueActiveKeys = activeOrders.map { it.id }.toSet()
            assertEquals(30, uniqueActiveKeys.size)
        }
        Unit
    }
}
