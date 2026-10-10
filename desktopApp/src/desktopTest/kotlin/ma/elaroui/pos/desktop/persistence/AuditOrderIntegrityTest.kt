package ma.elaroui.pos.desktop.persistence

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class AuditOrderIntegrityTest {
    @Test
    fun paymentFailureRollsBackPaymentAndLeavesOrderOpen() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1)
            val original = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                    .execute(1, "AUDIT-ROLLBACK", OrderType.DINE_IN, 1, listOf(1L to 1), 1, 1)
            ).value
            val failingOrders = object : OrderRepository by db.orders {
                override suspend fun save(order: Order): Long {
                    db.orders.save(order)
                    error("Failure after order and payment writes")
                }
            }
            assertFailsWith<IllegalStateException> {
                CompletePayment(db.sessions, failingOrders, db.payments, db.transactions)
                    .execute(1, PaymentMethod.CASH, original.totalCentimes, "rollback", 1)
            }
            assertNull(db.payments.findBySubmissionToken(1, "rollback"))
            assertEquals(0L, db.payments.totalCashForSession(1))
            assertEquals(OrderStatus.OPEN, db.orders.findById(1)?.status)
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(1)?.status)
        }
    }

    @Test
    fun completedOrderCannotBeReopenedAndPaidAgain() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1)
            val create = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
            val original = assertIs<UseCaseResult.Success<Order>>(
                create.execute(1, "AUDIT-1", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            ).value
            assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                    .execute(1, PaymentMethod.CASH, original.totalCentimes, "audit-paid", 1)
            )
            assertIs<UseCaseResult.Failure>(
                create.execute(1, "AUDIT-1", OrderType.COUNTER, 1, listOf(1L to 2), null, 1)
            )
            assertEquals(OrderStatus.COMPLETED, db.orders.findById(1)?.status)
            assertFailsWith<IllegalArgumentException> {
                db.orders.save(original)
            }
            assertEquals(original.totalCentimes, db.payments.totalCashForSession(1))
        }
    }

    @Test
    fun editingOpenOrderPreservesItsPriceNameAndTaxSnapshots() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1)
            val create = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
            val original = assertIs<UseCaseResult.Success<Order>>(
                create.execute(1, "AUDIT-2", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            ).value
            val product = assertNotNull(db.products.findById(1))
            db.products.save(product.copy(name = "Renamed", priceCentimes = 9999, taxRateBasisPoints = 2000))
            val updated = assertIs<UseCaseResult.Success<Order>>(
                create.execute(1, "AUDIT-2", OrderType.COUNTER, 1, listOf(1L to 2), null, 1)
            ).value
            val updatedLine = updated.lines.single()
            assertEquals(original.lines.single().copy(
                quantity = 2,
                recognizedAmountCentimes = 2_000L,
                recognizedTaxCentimes = 181L
            ), updatedLine)
            assertEquals(original.totalCentimes * 2, updated.totalCentimes)
        }
    }

    @Test
    fun anotherCashierCannotOverwriteAnExistingOrder() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1)
            val create = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
            assertIs<UseCaseResult.Success<Order>>(
                create.execute(1, "AUDIT-3", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            )
            val cashier = db.createCashier("Other", "6789")
            OpenRegisterSession(db.sessions, TestClock).execute(2, 1, cashier)
            assertIs<UseCaseResult.Failure>(
                create.execute(1, "AUDIT-3", OrderType.COUNTER, 2, listOf(1L to 2), null, cashier)
            )
            assertEquals(1L, db.orders.findById(1)?.cashierId)
        }
    }

    @Test
    fun openingRegisterReturnsThePersistedGeneratedId() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val opened = assertIs<UseCaseResult.Success<RegisterSession>>(
                OpenRegisterSession(db.sessions, TestClock).execute(0, 1, 1)
            ).value
            assertTrue(opened.id > 0)
            assertEquals(opened, db.sessions.findById(opened.id))
        }
    }
}
