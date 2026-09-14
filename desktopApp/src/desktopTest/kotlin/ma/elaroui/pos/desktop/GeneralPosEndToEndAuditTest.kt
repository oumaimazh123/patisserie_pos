package ma.elaroui.pos.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.print.EscPosPrintRequest
import ma.elaroui.pos.desktop.print.PrintJobDeduplicator
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.DiscrepancyKind
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.RegisterClosingRules
import java.nio.file.Files
import kotlin.test.*

class GeneralPosEndToEndAuditTest {

    private class AuditClock(var time: Long) : ma.elaroui.pos.shared.Clock {
        override fun now() = EpochMilliseconds(time)
    }

    @Test
    fun testCompleteGeneralPosEndToEndAuditFlow() = runBlocking {
        val clock = AuditClock(1_700_000_000_000L)
        val tempDir = Files.createTempDirectory("general_pos_audit")
        val dbPath = tempDir.resolve("audit_pos.db")

        // 1. Initial Setup & Configuration
        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("General Store", "Admin Owner", "1234")
            db.settings.put(AppSetting("establishment_name", "General Store"))
            db.settings.put(AppSetting("restaurant_address", "123 Commercial Boulevard"))
            db.settings.put(AppSetting("restaurant_phone", "0522001122"))
            db.settings.put(AppSetting("seller_ice", "001234567000089"))
            db.settings.put(AppSetting("setup_complete", "true"))
            db.settings.put(AppSetting("print_establishment_name", "true"))
            db.settings.put(AppSetting("cash_drawer_enabled", "true"))

            val authUser = db.authentication.authenticate("1234")
            assertNotNull(authUser)
            assertEquals("Admin Owner", authUser.name)
            assertEquals(UserRole.OWNER, authUser.role)
        }

        // 2. Open Register Session & Create Retail Products
        WindowsPosDatabase.open(dbPath).use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(
                id = 1L,
                registerId = 1L,
                cashierId = 1L,
                openingCashCentimes = 50_000L // 500.00 DH
            )
            assertTrue(openRes is UseCaseResult.Success)
            val session = openRes.value
            assertEquals(RegisterSessionStatus.OPEN, session.status)
            assertEquals(50_000L, session.openingCashCentimes)

            // Categories
            val catBeverages = db.categories.save(Category(id = 10L, name = "Boissons Fraîches", active = true))
            val catSnacks = db.categories.save(Category(id = 20L, name = "Snacks & Confiserie", active = true))
            val catInactive = db.categories.save(Category(id = 30L, name = "Saisonnier Inactif", active = false))

            // Products
            val prod1 = db.products.save(
                Product(
                    id = 101L,
                    categoryId = 10L,
                    name = "Jus d'Orange 33cl",
                    priceCentimes = 1500L,
                    taxRateBasisPoints = 1000,
                    barcode = "6111234567890",
                    sku = "JUS-ORG-33",
                    active = true,
                    available = true
                )
            )
            val prod2 = db.products.save(
                Product(
                    id = 102L,
                    categoryId = 20L,
                    name = "Chips Sel & Vinaigre",
                    priceCentimes = 800L,
                    taxRateBasisPoints = 1000,
                    barcode = "6111987654321",
                    sku = "CHP-SAL-VN",
                    active = true,
                    available = true
                )
            )
            val prodInactive = db.products.save(
                Product(
                    id = 103L,
                    categoryId = 20L,
                    name = "Produit Désactivé",
                    priceCentimes = 500L,
                    taxRateBasisPoints = 1000,
                    barcode = "6111000000000",
                    sku = "DIS-001",
                    active = false,
                    available = true
                )
            )

            // Barcode search
            assertEquals(101L, db.findProductByBarcode("6111234567890")?.id)
            assertEquals(102L, db.findProductByBarcode("6111987654321")?.id)
            assertNull(db.findProductByBarcode("9999999999999"))

            // 3. Create General Retail Order (Counter Sale)
            val orderRes = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 201L,
                number = "POS-201",
                type = OrderType.COUNTER,
                sessionId = session.id,
                lineItems = listOf(101L to 2, 102L to 1),
                tableId = null,
                cashierId = 1L
            )
            assertTrue(orderRes is UseCaseResult.Success)
            val order = orderRes.value
            assertEquals(OrderStatus.OPEN, order.status)
            assertEquals(OrderType.COUNTER, order.type)
            assertNull(order.tableId)
            assertEquals(3800L, order.totalCentimes)

            // 4. Complete Cash Payment
            val payRes = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = order.id,
                method = PaymentMethod.CASH,
                receivedCentimes = 4000L,
                token = "pay-token-201"
            )
            assertTrue(payRes is UseCaseResult.Success)
            val completedOrder = db.orders.findById(order.id)
            assertNotNull(completedOrder)
            assertEquals(OrderStatus.COMPLETED, completedOrder.status)

            // 5. Deduplication verification
            val deduplicator = PrintJobDeduplicator()
            val printKey = "receipt:${completedOrder.id}:pay-token-201"
            assertTrue(deduplicator.acquire(printKey))
            assertFalse(deduplicator.acquire(printKey), "Duplicate print request must be guarded")

            // 6. Thermal Print Request Verification (Customer receipt, no table/kitchen)
            val company = ReceiptCompany(
                name = "General Store",
                address = "123 Commercial Boulevard",
                phone = "0522001122",
                ice = "001234567000089",
                printEstablishmentName = true
            )
            val printRequest = EscPosPrintRequest(
                order = completedOrder,
                company = company,
                kind = TicketKind.CUSTOMER,
                paperWidth = 80,
                isReprint = false,
                paymentMethod = PaymentMethod.CASH,
                receivedCentimes = 4000L,
                changeCentimes = 200L,
                cashierName = "Admin Owner"
            )
            assertEquals(TicketKind.CUSTOMER, printRequest.kind)
            assertNull(printRequest.tableLabel)
            assertNull(printRequest.areaLabel)

            // 7. Add Cash Movements
            RecordCashMovement(db.sessions, db.payments, db.cashMovements, clock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_IN,
                amountCentimes = 10_000L,
                reason = "Apport de monnaie",
                description = null,
                userId = 1L
            )
            RecordCashMovement(db.sessions, db.payments, db.cashMovements, clock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_OUT,
                amountCentimes = 5_000L,
                reason = "Achat fournitures",
                description = null,
                userId = 1L
            )

            // 8. Close Register Session
            // Opening: 50,000 + Cash Sale: 3,800 + In: 10,000 - Out: 5,000 = 58,800
            val expectedCash = session.openingCashCentimes +
                db.payments.totalCashForSession(session.id) +
                db.cashMovements.totalCashIn(session.id) -
                db.cashMovements.totalCashOut(session.id)
            assertEquals(58_800L, expectedCash)

            val closeInput = RegisterClosingInput(
                countedCashCentimes = 58_800L,
                leftInDrawerCentimes = 20_000L,
                removedAmountCentimes = 38_800L,
                remittanceReference = "ENV-001",
                remittanceDestination = "Coffre",
                closingNote = "Clôture conforme",
                closingUserId = 1L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closeInput)
            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(58_800L, closed.expectedCashCentimes)
            assertEquals(58_800L, closed.countedCashCentimes)
            assertEquals(0L, closed.differenceCentimes)
            assertEquals(DiscrepancyKind.EXACT, RegisterClosingRules.classifyDifference(closed.differenceCentimes ?: 0L))
        }

        // 9. Reopen Database and Verify Persistence
        WindowsPosDatabase.open(dbPath).use { db ->
            assertEquals("General Store", db.settings.get("establishment_name"))
            assertEquals("true", db.settings.get("print_establishment_name"))
            assertEquals("true", db.settings.get("cash_drawer_enabled"))

            val allProducts = db.products.observeAll().first()
            assertEquals(3, allProducts.size)
            val visibleProducts = allProducts.filter { it.active && it.available }
            assertEquals(2, visibleProducts.size)

            val closedSession = db.sessions.findById(1L)
            assertNotNull(closedSession)
            assertEquals(RegisterSessionStatus.CLOSED, closedSession.status)
            assertEquals(58_800L, closedSession.countedCashCentimes)
        }
    }
}
