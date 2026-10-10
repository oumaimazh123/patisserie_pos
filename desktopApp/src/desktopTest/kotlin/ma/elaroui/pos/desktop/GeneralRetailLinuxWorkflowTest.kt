package ma.elaroui.pos.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.Payment
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

class GeneralRetailLinuxWorkflowTest {
    private object ClockAtNoon : Clock {
        override fun now() = EpochMilliseconds(1_800_000_000_000L)
    }

    @Test
    fun freshLinuxStoreHasNoMockDataAndPersistsRetailIdentifiers() = runBlocking {
        val databaseFile = Files.createTempDirectory("general-pos-linux-").resolve("pos.db")
        WindowsPosDatabase.open(databaseFile).use { db ->
            assertEquals(12, db.schemaVersion())
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.areas().isEmpty())
            assertTrue(db.allTables().isEmpty())
            val categoryId = db.categories.save(Category(id = 0L, name = "Electronics", displayOrder = 1))
            db.products.save(
                Product(
                    id = 0L,
                    categoryId = categoryId,
                    name = "USB Cable",
                    priceCentimes = 2_500L,
                    taxRateBasisPoints = 2_000,
                    sku = "ELEC-USB-1",
                    barcode = "6110000000012"
                )
            )
        }
        WindowsPosDatabase.open(databaseFile).use { reopened ->
            val product = reopened.products.observeAll().first().single()
            assertEquals("ELEC-USB-1", product.sku)
            assertEquals("6110000000012", product.barcode)
        }
    }

    @Test
    fun counterSaleDiscountPaymentAnalyticsAndOwnerCancellationAreTransactional() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val session = assertIs<UseCaseResult.Success<ma.elaroui.pos.shared.domain.RegisterSession>>(
                OpenRegisterSession(db.sessions, ClockAtNoon).execute(1L, 1L, 1L, 10_000L)
            ).value
            val order = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    id = 5_001L,
                    number = "SALE-LINUX-1",
                    type = OrderType.COUNTER,
                    sessionId = session.id,
                    lineItems = listOf(1L to 2),
                    tableId = null,
                    cashierId = 1L,
                    discountBasisPoints = 1_000
                )
            ).value
            assertEquals(2_000L, order.subtotalCentimes)
            assertEquals(200L, order.discountCentimes)
            assertEquals(1_800L, order.totalCentimes)
            assertEquals("Café", order.lines.single().categoryNameSnapshot)

            val payment = assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                    .execute(order.id, PaymentMethod.CARD, null, "linux-sale-token")
            ).value
            val duplicate = assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                    .execute(order.id, PaymentMethod.CARD, null, "linux-sale-token")
            ).value
            assertEquals(payment.id, duplicate.id)

            val analytics = db.retailSalesAnalytics(0L, Long.MAX_VALUE)
            assertEquals(1_800L, analytics.byProduct.first { it.label == "Legacy Item" }.amountCentimes)
            assertEquals(1_800L, analytics.byCashier.single().amountCentimes)

            val held = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    5_002L, "SALE-LINUX-2", OrderType.COUNTER, session.id, listOf(1L to 1), null, 1L
                )
            ).value
            db.cancelOrder(held.id, "Incorrect item", 1L, null)
            assertEquals(1, db.retailSalesAnalytics(0L, Long.MAX_VALUE).cancelledSales)
        }
    }
}
