package ma.elaroui.pos.desktop.persistence

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.TestClock
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneralRetailSoftDeleteRegressionTest {
    @Test
    fun `bulk catalogue and cashier deletion preserves rows and sale attribution`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val categoryId = db.categories.save(Category(0L, "Épicerie", true, 10))
            val productId = db.products.save(Product(0L, categoryId, "Farine", 1250L, 0, true, true))
            val cashierId = db.createCashier("Caissier détail", "2468")
            OpenRegisterSession(db.sessions, TestClock).execute(1L, 1L, cashierId, 5_000L)
            val created = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(9_001L, "GEN-SOFT-001", OrderType.COUNTER, 1L, listOf(productId to 1), null, cashierId)
            val order = assertIs<UseCaseResult.Success<Order>>(created).value

            assertTrue(db.deleteProducts(cashierId) >= 1)
            assertTrue(db.deleteCategories(cashierId) >= 1)
            assertTrue(db.deleteCashiers(1L) >= 1)

            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertFalse(db.users.observeActive().first().any { it.id == cashierId })
            assertTrue(db.deletedProducts().any { it.id == productId })
            assertTrue(db.deletedCategories().any { it.id == categoryId })
            assertTrue(db.deletedUsers().any { it.id == cashierId })
            assertEquals(cashierId, db.orders.findById(order.id)?.cashierId)
            assertNotNull(db.orders.findById(order.id))
            Unit
        }
    }

    @Test
    fun `restore collision is rejected without hiding the active replacement`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val categoryId = db.categories.save(Category(0L, "Hygiène", true, 20))
            val archivedId = db.products.save(Product(0L, categoryId, "Savon", 900L, 0, true, true, sku = "SAV-1", barcode = "6110001"))
            db.softDeleteProduct(archivedId)
            val replacementId = db.products.save(Product(0L, categoryId, "Savon", 950L, 0, true, true, sku = "SAV-1", barcode = "6110001"))

            assertFailsWith<DesktopValidationException> { db.restoreProduct(archivedId) }
            assertTrue(db.products.observeAll().first().any { it.id == replacementId })
            assertTrue(db.deletedProducts().any { it.id == archivedId })
        }
    }
}
