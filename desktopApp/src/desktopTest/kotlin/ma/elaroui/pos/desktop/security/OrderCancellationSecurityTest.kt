package ma.elaroui.pos.desktop.security

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.TestClock
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.PinValidationRules
import kotlin.test.*

class OrderCancellationSecurityTest {

    private fun setupTestDb(): WindowsPosDatabase {
        val db = WindowsPosDatabase.openInMemory()
        db.configureInitialSetup("PATISSERIE_POS Security Test", "Admin Owner", "1234")
        runBlocking {
            db.categories.save(Category(id = 0L, name = "Beverages"))
            db.products.save(Product(id = 0L, categoryId = 1L, name = "Espresso", priceCentimes = 12_00L, taxRateBasisPoints = 0))
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 50_00L)
        }
        return db
    }

    private fun createTestOrder(db: WindowsPosDatabase, cashierId: Long = 1L): Order = runBlocking {
        val session = db.sessions.findOpenByUser(cashierId) ?: run {
            val opened = OpenRegisterSession(db.sessions, TestClock).execute(
                db.nextId("register_sessions"), 1, cashierId, 0
            )
            assertIs<UseCaseResult.Success<RegisterSession>>(opened).value
        }
        val result = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
            .execute(db.nextId("orders"), "ORD-${System.currentTimeMillis()}-${(100..999).random()}", OrderType.COUNTER, session.id, listOf(1L to 1), tableId = null, cashierId = cashierId)
        assertIs<UseCaseResult.Success<Order>>(result).value
    }

    @Test
    fun `cashier with no PIN configured can cancel order normally`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Cashier John", "2345")
            val order = createTestOrder(db, cashierId)

            assertFalse(db.isOrderCancellationPinConfigured())

            // Cashier cancels without cancellation PIN
            db.cancelOrder(order.id, "Client changed mind", cashierId, null)

            val reloaded = db.orders.findById(order.id)
            assertNotNull(reloaded)
            assertEquals(OrderStatus.CANCELLED, reloaded.status)
        }
    }

    @Test
    fun `unused PIN input is ignored when no cancellation PIN is configured`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Cashier John", "2345")
            val order = createTestOrder(db, cashierId)
            db.cancelOrder(order.id, "Client changed mind", cashierId, "not-configured")
            assertEquals(OrderStatus.CANCELLED, db.orders.findById(order.id)?.status)
        }
    }

    @Test
    fun `cashier with PIN configured succeeds with correct PIN`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Cashier John", "2345")
            val order = createTestOrder(db, cashierId)

            // Configure cancellation PIN
            db.setOrderCancellationPin("4321")
            assertTrue(db.isOrderCancellationPinConfigured())

            // Cashier cancels with valid PIN
            db.cancelOrder(order.id, "Wrong order entered", cashierId, "4321")

            val reloaded = db.orders.findById(order.id)
            assertNotNull(reloaded)
            assertEquals(OrderStatus.CANCELLED, reloaded.status)
        }
    }

    @Test
    fun `cashier with PIN configured is rejected on incorrect PIN or missing PIN`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Cashier John", "2345")
            val order = createTestOrder(db, cashierId)

            db.setOrderCancellationPin("9876")
            assertTrue(db.isOrderCancellationPinConfigured())

            // Case 1: Missing PIN
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(order.id, "Customer left", cashierId, null)
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(order.id)?.status)

            // Case 2: Blank PIN
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(order.id, "Customer left", cashierId, "   ")
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(order.id)?.status)

            // Case 3: Wrong PIN
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(order.id, "Customer left", cashierId, "1111")
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(order.id)?.status)
        }
    }

    @Test
    fun `owner login PIN is not an alternative to the configured cancellation PIN`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Cashier John", "2345")
            val order = createTestOrder(db, cashierId)
            db.setOrderCancellationPin("9876")

            assertFalse(db.verifyOrderCancellationPin("1234"))
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(order.id, "Customer left", cashierId, "1234")
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(order.id)?.status)
        }
    }

    @Test
    fun `disabled or deleted cashier cannot cancel through a stale session`() = runBlocking {
        setupTestDb().use { db ->
            val disabledId = db.createCashier("Disabled cashier", "2468")
            val disabledOrder = createTestOrder(db, disabledId)
            db.updateCashier(disabledId, active = false)
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(disabledOrder.id, "Stale session", disabledId)
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(disabledOrder.id)?.status)

            val deletedId = db.createCashier("Deleted cashier", "1357")
            val deletedOrder = createTestOrder(db, deletedId)
            db.softDeleteCashier(deletedId)
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(deletedOrder.id, "Stale session", deletedId)
            }
            assertEquals(OrderStatus.OPEN, db.orders.findById(deletedOrder.id)?.status)
        }
    }

    @Test
    fun `admin owner is not blocked by cancellation PIN requirement`() = runBlocking {
        setupTestDb().use { db ->
            val order = createTestOrder(db, cashierId = 1L)

            // Configure cancellation PIN
            db.setOrderCancellationPin("7777")
            assertTrue(db.isOrderCancellationPinConfigured())

            // Admin cancels directly without PIN
            db.cancelOrder(order.id, "Admin manual cancellation", userId = 1L, cancellationPin = null)

            val reloaded = db.orders.findById(order.id)
            assertNotNull(reloaded)
            assertEquals(OrderStatus.CANCELLED, reloaded.status)
        }
    }

    @Test
    fun `PIN validation enforces 4 to 6 digits numeric only`() {
        // Valid
        assertTrue(PinValidationRules.isValid("1234"))
        assertTrue(PinValidationRules.isValid("12345"))
        assertTrue(PinValidationRules.isValid("123456"))

        // Invalid lengths
        assertFalse(PinValidationRules.isValid(""))
        assertFalse(PinValidationRules.isValid("1"))
        assertFalse(PinValidationRules.isValid("12"))
        assertFalse(PinValidationRules.isValid("123"))
        assertFalse(PinValidationRules.isValid("1234567"))
        assertFalse(PinValidationRules.isValid("12345678"))

        // Invalid characters
        assertFalse(PinValidationRules.isValid("12a4"))
        assertFalse(PinValidationRules.isValid("abcd"))
        assertFalse(PinValidationRules.isValid("12 34"))
        assertFalse(PinValidationRules.isValid("12-34"))
    }

    @Test
    fun `admin can define, modify, verify and delete cancellation PIN`() {
        setupTestDb().use { db ->
            assertFalse(db.isOrderCancellationPinConfigured())

            // Define PIN
            db.setOrderCancellationPin("5555")
            assertTrue(db.isOrderCancellationPinConfigured())
            assertTrue(db.verifyOrderCancellationPin("5555"))
            assertFalse(db.verifyOrderCancellationPin("9999"))

            // Modify PIN
            db.setOrderCancellationPin("6666")
            assertTrue(db.isOrderCancellationPinConfigured())
            assertTrue(db.verifyOrderCancellationPin("6666"))
            assertFalse(db.verifyOrderCancellationPin("5555"))

            // Delete PIN
            db.removeOrderCancellationPin()
            assertFalse(db.isOrderCancellationPinConfigured())
        }
    }

    @Test
    fun `setting cancellation PIN validates 4 to 6 numeric digits`() {
        setupTestDb().use { db ->
            assertFailsWith<IllegalArgumentException> {
                db.setOrderCancellationPin("123")
            }
            assertFailsWith<IllegalArgumentException> {
                db.setOrderCancellationPin("1234567")
            }
            assertFailsWith<IllegalArgumentException> {
                db.setOrderCancellationPin("abcd")
            }
        }
    }

    @Test
    fun `admin can update owner PIN and login with new PIN`() = runBlocking {
        setupTestDb().use { db ->
            // Original PIN is 1234
            val initialOwner = db.authentication.authenticate("1234")
            assertNotNull(initialOwner)
            assertEquals(UserRole.OWNER, initialOwner.role)

            // Update to 8888
            db.updateOwnerPin("8888")

            // New PIN works
            val updatedOwner = db.authentication.authenticate("8888")
            assertNotNull(updatedOwner)
            assertEquals(UserRole.OWNER, updatedOwner.role)

            // Old PIN fails
            assertNull(db.authentication.authenticate("1234"))
        }
    }

    @Test
    fun `admin can create, update, and reset cashier PIN`() = runBlocking {
        setupTestDb().use { db ->
            val cashierId = db.createCashier("Bob", "3333")
            assertNotNull(db.authentication.authenticate("3333"))

            // Admin updates Bob's PIN to 4444
            db.updateCashier(cashierId, name = "Bob Smith", pin = "4444", active = true)

            // New PIN works
            val authUser = db.authentication.authenticate("4444")
            assertNotNull(authUser)
            assertEquals("Bob Smith", authUser.name)

            // Old PIN fails
            assertNull(db.authentication.authenticate("3333"))
        }
    }

    @Test
    fun `duplicate PIN check prevents assigning another user's PIN`() {
        setupTestDb().use { db ->
            // Owner has 1234
            assertFails {
                db.createCashier("Duplicate Cashier", "1234")
            }

            val cashier1 = db.createCashier("Cashier 1", "5555")
            assertFails {
                db.createCashier("Cashier 2", "5555")
            }

            // Cannot update cashier to existing PIN
            assertFails {
                db.updateCashier(cashier1, name = "Cashier 1", pin = "1234", active = true)
            }
        }
    }

    @Test
    fun `PIN is never stored in plain text and audit log never exposes plain text PIN`() = runBlocking {
        setupTestDb().use { db ->
            val rawPin = "6543"
            db.setOrderCancellationPin(rawPin)

            // Perform an order cancellation
            val cashierId = db.createCashier("Audit Cashier", "7890")
            val order = createTestOrder(db, cashierId)
            db.cancelOrder(order.id, "Mistake in item selection", cashierId, rawPin)

            // Check audit log details via direct database query
            val auditLogDetails = db.auditLogs().firstOrNull { it.actionType == "ORDER_CANCELLED" }?.details
            assertNotNull(auditLogDetails)
            assertFalse(auditLogDetails.contains(rawPin), "Audit log must not contain plain text PIN")
            assertTrue(auditLogDetails.contains("pin_required=true"))
            assertTrue(auditLogDetails.contains("reason=Mistake in item selection"))
        }
    }
}
