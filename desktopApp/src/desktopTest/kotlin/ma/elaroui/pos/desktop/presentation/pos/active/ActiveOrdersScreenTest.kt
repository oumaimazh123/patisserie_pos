package ma.elaroui.pos.desktop.presentation.pos.active

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.application.TestClock
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class ActiveOrdersScreenTest {

    @Test
    fun `cancelOrder without owner PIN cancels open order successfully`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("PATISSERIE_POS test", "Owner", "1234")
            db.categories.save(Category(id = 0L, name = "Boissons Fraiches"))
            db.products.save(Product(id = 0L, categoryId = 1L, name = "Jus d Orange", priceCentimes = 15_00L, taxRateBasisPoints = 0))

            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 100_00L)

            val created = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "SALE-20260902-101", OrderType.COUNTER, 1, listOf(1L to 2), tableId = null, cashierId = 1)
            val order = assertIs<UseCaseResult.Success<Order>>(created).value

            assertEquals(OrderStatus.OPEN, order.status)
            assertEquals(OrderStatus.OPEN, db.orders.findById(order.id)?.status)

            // Cancel order without PIN (passing only the cancellation reason)
            db.cancelOrder(orderId = order.id, reason = "Client parti", userId = 1L)

            // Verify order status is CANCELLED
            val reloadedOrder = db.orders.findById(order.id)
            assertNotNull(reloadedOrder)
            assertEquals(OrderStatus.CANCELLED, reloadedOrder.status)
        }
        Unit
    }

    @Test
    fun `cancelOrder requires non-blank reason`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("PATISSERIE_POS test", "Owner", "1234")
            db.categories.save(Category(id = 0L, name = "Snacks"))
            db.products.save(Product(id = 0L, categoryId = 1L, name = "Croissant", priceCentimes = 8_00L, taxRateBasisPoints = 0))

            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 100_00L)

            val created = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "SALE-20260902-102", OrderType.COUNTER, 1, listOf(1L to 1), tableId = null, cashierId = 1)
            val order = assertIs<UseCaseResult.Success<Order>>(created).value

            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(orderId = order.id, reason = "", userId = 1L)
            }

            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(orderId = order.id, reason = "   ", userId = 1L)
            }

            // Order remains OPEN
            val reloaded = db.orders.findById(order.id)
            assertEquals(OrderStatus.OPEN, reloaded?.status)
        }
        Unit
    }
}
