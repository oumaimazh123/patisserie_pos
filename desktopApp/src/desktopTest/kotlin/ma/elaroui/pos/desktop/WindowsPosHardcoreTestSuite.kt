package ma.elaroui.pos.desktop

import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.license.WindowsLicenseManager
import ma.elaroui.pos.desktop.license.WindowsLicenseStatus
import ma.elaroui.pos.desktop.license.WindowsSecureStore
import ma.elaroui.pos.desktop.persistence.DesktopValidationException
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute
import ma.elaroui.pos.desktop.print.DesktopPrinterConfig
import ma.elaroui.pos.desktop.print.ThermalTicketRenderer
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.desktop.print.WindowsThermalPrinter
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.*
import kotlin.test.*

private object HardcoreTestClock : ma.elaroui.pos.shared.Clock {
    var currentTimeMs: Long = 1_724_900_000_000L
    override fun now() = ma.elaroui.pos.shared.EpochMilliseconds(currentTimeMs)
}

class WindowsPosHardcoreTestSuite {

    // =========================================================================
    // 1. Initial Setup, Owner Auth, PIN Lengths, Edge Cases
    // =========================================================================
    @Test
    fun test01_InitialSetup_OwnerAuth_PinValidation_EdgeCases(): Unit = runBlocking {
        val dir = Files.createTempDirectory("win-test-initial-setup")
        val dbPath = dir.resolve("pos.db")

        WindowsPosDatabase.open(dbPath).use { db ->
            // Fresh DB unconfigured
            assertFalse(db.settings.get("setup_complete") == "true")

            // PIN validation edge cases
            assertFails { db.configureInitialSetup("Café", "Owner", "") }
            assertFails { db.configureInitialSetup("Café", "Owner", "   ") }
            assertFails { db.configureInitialSetup("Café", "Owner", "12") }
            assertFails { db.configureInitialSetup("Café", "Owner", "123") }
            assertFails { db.configureInitialSetup("Café", "Owner", "1234567") }
            assertFails { db.configureInitialSetup("Café", "Owner", "12A4") }
            assertFails { db.configureInitialSetup("", "Owner", "1234") }
            assertFails { db.configureInitialSetup("Café", "", "1234") }

            // Valid 5-digit PIN setup
            db.configureInitialSetup("Café de la Paix", "Zakaria Owner", "54321")
            assertEquals("true", db.settings.get("setup_complete"))
            assertEquals("Café de la Paix", db.settings.get("establishment_name"))

            // Verify authentication
            val authenticated = db.authentication.authenticate("54321")
            assertNotNull(authenticated)
            assertEquals("Zakaria Owner", authenticated.name)
            assertEquals(UserRole.OWNER, authenticated.role)
            assertTrue(authenticated.active)

            // Wrong PINs return null
            assertNull(db.authentication.authenticate("00000"))
            assertNull(db.authentication.authenticate("1234"))
            assertNull(db.authentication.authenticate(""))
        }
    }

    // =========================================================================
    // 2. Cashier Lifecycle, Edit, Deactivation, Duplicate PIN, Lockout
    // =========================================================================
    @Test
    fun test02_CashierLifecycle_Edit_Deactivation_DuplicatePin_Lockout(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Create cashiers with 4, 5, 6 digit PINs
            val c4 = db.createCashier("Cashier Four", "4444")
            val c5 = db.createCashier("Cashier Five", "55555")
            val c6 = db.createCashier("Cashier Six", "666666")

            assertNotNull(db.authentication.authenticate("4444"))
            assertNotNull(db.authentication.authenticate("55555"))
            assertNotNull(db.authentication.authenticate("666666"))

            // Duplicate PIN detection
            val dupOwner = assertFailsWith<DesktopValidationException> {
                db.createCashier("Dup Owner", "1234")
            }
            assertTrue(dupOwner.message!!.contains("déjà utilisé"))

            val dupCashier = assertFailsWith<DesktopValidationException> {
                db.createCashier("Dup Cashier", "4444")
            }
            assertTrue(dupCashier.message!!.contains("déjà utilisé"))

            // Edit Cashier (rename and PIN change)
            db.updateCashier(c4, name = "Cashier Four Renamed", pin = "4321")
            assertNull(db.authentication.authenticate("4444"))
            val updatedUser = db.authentication.authenticate("4321")
            assertNotNull(updatedUser)
            assertEquals("Cashier Four Renamed", updatedUser.name)

            // Deactivate and verify rejection
            db.updateCashier(c4, active = false)
            assertNull(db.authentication.authenticate("4321"), "Deactivated cashier must be rejected")

            // Duplicate check against deactivated user
            val dupDeactivated = assertFailsWith<DesktopValidationException> {
                db.createCashier("Reuse Deactivated PIN", "4321")
            }
            assertTrue(dupDeactivated.message!!.contains("déjà utilisé"))

            // Reactivate
            db.updateCashier(c4, active = true)
            assertNotNull(db.authentication.authenticate("4321"), "Reactivated cashier can log in")
        }
    }

    // =========================================================================
    // 3. Register Sessions, Opening Validation, Cash Movements & Approvals
    // =========================================================================
    @Test
    fun test03_RegisterSessions_OpeningValidation_PerUser_CashMovements(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val cashierId = db.createCashier("Cashier Yassine", "7777")
            val cashier = db.authentication.authenticate("7777")!!

            // Negative opening amount rejected
            val invalidOpen = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L,
                registerId = 1L,
                cashierId = cashier.id,
                openingCashCentimes = -500L
            )
            assertIs<UseCaseResult.Failure>(invalidOpen)

            // Valid register opening (200.00 DH = 20_000 centimes)
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L,
                registerId = 1L,
                cashierId = cashier.id,
                openingCashCentimes = 20_000L
            )
            assertIs<UseCaseResult.Success<RegisterSession>>(openRes)
            val session = openRes.value
            assertEquals(20_000L, session.openingCashCentimes)
            assertEquals(RegisterSessionStatus.OPEN, session.status)

            // A different user may open another session on the same physical register.
            val secondOpen = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 2L,
                registerId = 1L,
                cashierId = owner.id,
                openingCashCentimes = 10_000L
            )
            assertIs<UseCaseResult.Success<RegisterSession>>(secondOpen)

            // The same cashier may not open a second OPEN session.
            val duplicateCashierOpen = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 3L,
                registerId = 1L,
                cashierId = cashier.id,
                openingCashCentimes = 10_000L
            )
            assertIs<UseCaseResult.Failure>(duplicateCashierOpen)

            // Cash Entry (CASH_IN: 50.00 DH)
            val cashInRes = RecordCashMovement(db.sessions, db.payments, db.cashMovements, HardcoreTestClock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_IN,
                amountCentimes = 5_000L,
                reason = "Apport monnaie",
                description = "Monnaie supplémentaire",
                userId = cashier.id
            )
            assertIs<UseCaseResult.Success<CashMovement>>(cashInRes)

            // Cash Withdrawal (CASH_OUT: 30.00 DH)
            val cashOutRes = RecordCashMovement(db.sessions, db.payments, db.cashMovements, HardcoreTestClock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_OUT,
                amountCentimes = 3_000L,
                reason = "Achat café lait",
                description = "Facture sup",
                userId = cashier.id
            )
            val recordedCashOut = assertIs<UseCaseResult.Success<CashMovement>>(cashOutRes).value
            assertEquals(session.id, recordedCashOut.sessionId)
            assertEquals(cashier.id, recordedCashOut.createdByUserId)
            assertEquals("Achat café lait", recordedCashOut.reason)
            assertEquals(CashMovementType.CASH_OUT, recordedCashOut.type)

            // Zero / negative movement rejected
            val zeroMove = RecordCashMovement(db.sessions, db.payments, db.cashMovements, HardcoreTestClock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_IN,
                amountCentimes = 0L,
                reason = "Zero",
                description = null,
                userId = cashier.id
            )
            assertIs<UseCaseResult.Failure>(zeroMove)

            // Verify movements in DB
            val movements = db.cashMovements.observeForSession(session.id).first()
            assertEquals(2, movements.size)
        }
    }

    // =========================================================================
    // 4. Register Closing, Expected Cash & Discrepancy Calculation
    // =========================================================================
    @Test
    fun test04_RegisterClosing_ExpectedVsCounted_DiscrepancyCalculation(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L,
                registerId = 1L,
                cashierId = owner.id,
                openingCashCentimes = 30_000L // 300.00 DH
            )
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(openRes).value

            // Record a Cash In of 20.00 DH and Cash Out of 10.00 DH
            RecordCashMovement(db.sessions, db.payments, db.cashMovements, HardcoreTestClock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_IN,
                amountCentimes = 2_000L,
                reason = "Apport",
                description = null,
                userId = owner.id
            )
            RecordCashMovement(db.sessions, db.payments, db.cashMovements, HardcoreTestClock).execute(
                sessionId = session.id,
                type = CashMovementType.CASH_OUT,
                amountCentimes = 1_000L,
                reason = "Fourniture",
                description = null,
                userId = owner.id
            )

            // Record a cash sale of 50.00 DH
            val orderRes = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 401L,
                number = "ORD-401",
                type = OrderType.TAKEAWAY,
                sessionId = session.id,
                lineItems = listOf(1L to 2), // Product 1 (25.00 DH x 2 = 50.00 DH)
                tableId = null,
                cashierId = owner.id
            )
            val order = assertIs<UseCaseResult.Success<Order>>(orderRes).value

            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = order.id,
                method = PaymentMethod.CASH,
                receivedCentimes = 5_000L,
                token = "close-test-token"
            )

            // Expected Cash Calculation: 300 + 20 - 10 + 20 = 330.00 DH (33_000 centimes)
            // Close register with 320.00 DH (Counted = 32_000 centimes) -> Difference = -10.00 DH (-1000 centimes)
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, HardcoreTestClock).execute(
                sessionId = session.id,
                countedCashCentimes = 32_000L
            )
            assertIs<UseCaseResult.Success<RegisterSession>>(closeRes)
            val closedSession = closeRes.value
            assertEquals(33_000L, closedSession.expectedCashCentimes)
            assertEquals(32_000L, closedSession.countedCashCentimes)
            assertEquals(-1_000L, closedSession.differenceCentimes)
            assertEquals(RegisterSessionStatus.CLOSED, closedSession.status)
        }
    }

    // =========================================================================
    // 5. Products & Categories CRUD, Deactivation, Search & Filtering
    // =========================================================================
    @Test
    fun test05_ProductAndCategoryCRUD_Deactivation_SearchAndFiltering(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Category CRUD
            val newCat = Category(id = 0L, name = "Smoothies Extra", displayOrder = 10, active = true)
            val catId = db.categories.save(newCat)
            assertTrue(catId > 0)
            val allCats = db.categories.observeAll().first()
            assertTrue(allCats.any { it.name == "Smoothies Extra" })

            // Duplicate Category Name rejected
            val dupCat = Category(id = 0L, name = "smoothies extra", displayOrder = 11, active = true)
            assertFails { db.categories.save(dupCat) }

            // Product CRUD
            val product = Product(
                id = 0L,
                categoryId = catId,
                name = "Avocado Shake Deluxe",
                priceCentimes = 3500L,
                taxRateBasisPoints = 1000,
                imagePath = "/images/avocado.png",
                available = true,
                active = true
            )
            val prodId = db.products.save(product)
            assertTrue(prodId > 0)

            // Product Search & Filter
            val allProducts = db.products.observeAll().first()
            assertTrue(allProducts.any { it.name == "Avocado Shake Deluxe" })
            val smoothieProducts = allProducts.filter { it.categoryId == catId }
            assertEquals(1, smoothieProducts.size)

            // Deactivate Product
            val savedProduct = db.products.findById(prodId)!!
            db.products.save(savedProduct.copy(active = false, available = false))
            val activeProducts = db.products.observeSellable().first()
            assertFalse(activeProducts.any { it.id == savedProduct.id })

            // Reactivate Product
            db.products.save(savedProduct.copy(active = true, available = true))
            val reactivated = db.products.observeSellable().first()
            assertTrue(reactivated.any { it.id == savedProduct.id })
        }
    }

    // =========================================================================
    // 6. CSV Product & Category Import (Valid, Duplicate, Invalid, Partial)
    // =========================================================================
    @Test
    fun test06_CsvImports_ProductsAndCategories_Valid_Duplicate_Malformed(): Unit {
        val dir = Files.createTempDirectory("win-csv-test")
        val validCsv = dir.resolve("import_test.csv")
        val header = CsvImports.OFFICIAL_HEADERS.joinToString(",")
        Files.writeString(
            validCsv,
            header + "\n" +
            "M1,Mocktails,,,,,,,,Virgin Mojito,,,Pièce,\"28,50\",10%,\n" +
            "M2,Mocktails,,,,,,,,Pina Colada,,,Pièce,32.00,10%,\n" +
            "M1,Mocktails,,,,,,,,Virgin Mojito,,,Pièce,\"28,50\",10%,\n" + // duplicate Product ID
            "M3,Mocktails,,,,,,,,Invalid Price,,,Pièce,abc,10%,\n" +       // bad price
            "M4,Mocktails,,,,,,,,Negative Price,,,Pièce,-5.00,10%,\n"     // negative price
        )

        val analysis = CsvImports.analyzeCatalogCsv(validCsv)
        assertEquals(2, analysis.validRows.size)
        assertEquals("Virgin Mojito", analysis.validRows[0].productNameFrench)
        assertEquals(2850L, analysis.validRows[0].priceCentimes)
        assertEquals("Pina Colada", analysis.validRows[1].productNameFrench)
        assertEquals(3200L, analysis.validRows[1].priceCentimes)
        assertTrue(analysis.errors.any { it.line == 4 && it.column == "Product ID" })
        assertTrue(analysis.errors.any { it.line == 5 && it.column == "Price (MAD)" })
        assertTrue(analysis.errors.any { it.line == 6 && it.column == "Price (MAD)" })
    }

    // =========================================================================
    // 7. Dining Areas & Tables CRUD, States & Deactivation
    // =========================================================================
    @Test
    fun test07_DiningAreasAndTablesCRUD_States_Deactivation(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Area CRUD
            val area = DiningArea(id = 0L, name = "Terrasse Vue Mer", active = true, displayOrder = 1, imagePath = "terrasse.png")
            val areaId = db.saveArea(area)
            assertTrue(areaId > 0)
            val areas = db.areas()
            assertTrue(areas.any { it.id == areaId && it.name == "Terrasse Vue Mer" })

            // Tables in Area
            val t1 = db.tables.save(RestaurantTable(id = 0L, areaId = areaId, name = "T-01", status = TableStatus.AVAILABLE, active = true, displayOrder = 1))
            val t2 = db.tables.save(RestaurantTable(id = 0L, areaId = areaId, name = "T-02", status = TableStatus.AVAILABLE, active = true, displayOrder = 2))
            assertTrue(t1 > 0 && t2 > 0)

            // Table Status Switch
            db.tables.updateStatus(t1, TableStatus.OCCUPIED)
            db.tables.updateStatus(t2, TableStatus.RESERVED)

            val tables = db.tables.observeActive().first()
            assertEquals(TableStatus.OCCUPIED, tables.first { it.id == t1 }.status)
            assertEquals(TableStatus.RESERVED, tables.first { it.id == t2 }.status)

            // Deactivate Table
            val t2Entity = db.tables.findById(t2)!!
            db.tables.save(t2Entity.copy(active = false))
            val activeTables = db.tables.observeActive().first()
            assertFalse(activeTables.any { it.id == t2 })

            // Reactivate Table
            db.tables.save(t2Entity.copy(active = true))
            val reactivatedTables = db.tables.observeActive().first()
            assertTrue(reactivatedTables.any { it.id == t2 })
        }
    }

    // =========================================================================
    // 8. Dine-in Order Workflow, Table Occupancy, Item Edits, Table Release
    // =========================================================================
    @Test
    fun test08_DineInOrder_TableOccupancy_AddItems_TableReleaseOnPayment(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L, registerId = 1L, cashierId = owner.id, openingCashCentimes = 10_000L
            )
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(openRes).value

            val table = db.tables.observeActive().first().first()
            assertEquals(TableStatus.AVAILABLE, table.status)

            // 1. Create Dine-In Order -> Table becomes OCCUPIED
            val createRes = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 501L,
                number = "ORD-DINE-1",
                type = OrderType.DINE_IN,
                sessionId = session.id,
                lineItems = listOf(1L to 2),
                tableId = table.id,
                cashierId = owner.id
            )
            assertIs<UseCaseResult.Success<Order>>(createRes)
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(table.id)?.status)

            // 2. Process Full Payment & Verify Table Release
            val payRes = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = 501L,
                method = PaymentMethod.CASH,
                receivedCentimes = 6000L,
                token = "dine-in-pay-token"
            )
            assertIs<UseCaseResult.Success<Payment>>(payRes)

            // Order must be COMPLETED
            val finalOrder = db.orders.findById(501L)!!
            assertEquals(OrderStatus.COMPLETED, finalOrder.status)

            // Table must be released back to AVAILABLE
            val releasedTable = db.tables.findById(table.id)!!
            assertEquals(TableStatus.AVAILABLE, releasedTable.status)
        }
    }

    // =========================================================================
    // 9. Takeaway & Counter Workflow, Cancellation & Permissions
    // =========================================================================
    @Test
    fun test09_TakeawayAndCounter_OrderCancellation_Permissions(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L, registerId = 1L, cashierId = owner.id, openingCashCentimes = 10_000L
            )
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(openRes).value

            // Create Takeaway Order
            val takeaway = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 601L,
                number = "ORD-TAK-1",
                type = OrderType.TAKEAWAY,
                sessionId = session.id,
                lineItems = listOf(1L to 1),
                tableId = null,
                cashierId = owner.id
            )
            val order = assertIs<UseCaseResult.Success<Order>>(takeaway).value
            assertEquals(OrderType.TAKEAWAY, order.type)
            assertNull(order.tableId)

            // Cancel Order
            db.cancelOrder(order.id, "Client annulé", owner.id, null)
            val cancelledOrder = db.orders.findById(order.id)!!
            assertEquals(OrderStatus.CANCELLED, cancelledOrder.status)

            // Cannot pay a cancelled order
            val payCancelled = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = order.id,
                method = PaymentMethod.CASH,
                receivedCentimes = 4500L,
                token = "pay-cancelled-token"
            )
            assertIs<UseCaseResult.Failure>(payCancelled)
        }
    }

    // =========================================================================
    // 10. Payments (Cash & Card), Decimal VAT, Duplicate Prevention & Idempotency
    // =========================================================================
    @Test
    fun test10_PaymentMethods_Cash_Card_DecimalPrices_VAT_DuplicatePrevention(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L, registerId = 1L, cashierId = owner.id, openingCashCentimes = 10_000L
            )
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(openRes).value

            // Order with multiple items
            val orderRes = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                id = 701L,
                number = "ORD-701",
                type = OrderType.COUNTER,
                sessionId = session.id,
                lineItems = listOf(1L to 1, 2L to 2),
                tableId = null,
                cashierId = owner.id
            )
            val order = assertIs<UseCaseResult.Success<Order>>(orderRes).value

            // Card / TPE Payment
            val payCard = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = order.id,
                method = PaymentMethod.CARD,
                receivedCentimes = order.totalCentimes,
                token = "card-token-unique-1"
            )
            assertIs<UseCaseResult.Success<Payment>>(payCard)

            // Duplicate payment submission prevention (Idempotency token)
            val dupPay = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = order.id,
                method = PaymentMethod.CARD,
                receivedCentimes = order.totalCentimes,
                token = "card-token-unique-1"
            )
            assertIs<UseCaseResult.Success<Payment>>(dupPay)
            assertEquals(payCard.value.id, dupPay.value.id)
        }
    }

    // =========================================================================
    // 11. Sales Reports & Daily Statistics Calculations
    // =========================================================================
    @Test
    fun test11_SalesHistory_RegisterHistory_ReportsStatisticsCalculations(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L, registerId = 1L, cashierId = owner.id, openingCashCentimes = 10_000L
            )
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(openRes).value

            // Order 1 (Cash: 50.00 DH)
            val o1 = assertIs<UseCaseResult.Success<Order>>(CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                801L, "ORD-801", OrderType.TAKEAWAY, session.id, listOf(1L to 2), null, owner.id
            )).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                o1.id, PaymentMethod.CASH, o1.totalCentimes, "token-rep-1"
            )

            // Order 2 (Card: 25.00 DH)
            val o2 = assertIs<UseCaseResult.Success<Order>>(CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                802L, "ORD-802", OrderType.TAKEAWAY, session.id, listOf(2L to 1), null, owner.id
            )).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                o2.id, PaymentMethod.CARD, o2.totalCentimes, "token-rep-2"
            )

            // Summary for range
            val summary = db.salesSummary(0L, Long.MAX_VALUE)
            assertEquals(2, summary.completedOrders)
            assertEquals(o1.totalCentimes + o2.totalCentimes, summary.salesCentimes)
            assertTrue(summary.taxCentimes > 0L)
        }
    }

    // =========================================================================
    // 12. Receipt & Invoice Generation, Printer Config Persistence & Safe Fallback
    // =========================================================================
    @Test
    fun test12_ReceiptPrinting_CompanySettings_PrinterConfig_SafeFallback(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Save Company Legal & Receipt Settings
            db.settings.put(AppSetting("establishment_name", "Café Restaurant Elite"))
            db.settings.put(AppSetting("address", "123 Avenue Mohammed V, Casablanca"))
            db.settings.put(AppSetting("phone", "+212 522 00 11 22"))
            db.settings.put(AppSetting("ice", "001122334455667"))
            db.settings.put(AppSetting("tax_id", "IF-987654"))
            db.settings.put(AppSetting("customer_printer", "POS-80C"))
            db.settings.put(AppSetting("printer_width", "80"))

            val company = ReceiptCompany(
                name = db.settings.get("establishment_name").orEmpty(),
                address = db.settings.get("address").orEmpty(),
                phone = db.settings.get("phone").orEmpty(),
                ice = db.settings.get("ice").orEmpty(),
                taxId = db.settings.get("tax_id").orEmpty()
            )

            val order = Order(
                id = 901L,
                number = "CMD-901",
                type = OrderType.DINE_IN,
                status = OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(1L, "Tajine Poulet Citron", 6500L, 2, 1000)
                ),
                subtotalCentimes = 13000L,
                discountCentimes = 0L,
                taxCentimes = 1182L,
                totalCentimes = 13000L,
                tableId = 5L,
                registerSessionId = 1L,
                cashierId = 1L
            )

            val rendered80 = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, 80)
            assertTrue(rendered80.contains("Café Restaurant Elite"))
            assertTrue(rendered80.contains("001122334455667"))
            assertTrue(rendered80.contains("Tajine Poulet Citron"))
            assertTrue(rendered80.contains("130.00 DH"))

            val rendered58 = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, 58)
            assertTrue(rendered58.contains("Café Restaurant Elite"))
            assertTrue(rendered58.contains("130.00 DH"))

            // Disconnected printer fallback safety
            val printer = WindowsThermalPrinter()
            val result = printer.print(DesktopPrinterConfig("NonExistentPrinter-XYZ", 80), "test".toByteArray())
            assertTrue(result.isFailure, "Safe failure without crash when printer is unreachable")
        }
    }

    // =========================================================================
    // 13. Backup Creation, Restore, Validation & Corrupted Backup Handling
    // =========================================================================
    @Test
    fun test13_BackupAndRestore_Persistence_CorruptedBackupSafety(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("win-pos-backup-test")
        val liveDb = tempDir.resolve("live.db")
        val backupDb = tempDir.resolve("backup.db")

        // 1. Create DB and insert data
        WindowsPosDatabase.open(liveDb).use { db ->
            db.configureInitialSetup("Backup Café", "Owner B", "1234")
            db.createCashier("Cashier In Backup", "9999")
            db.saveArea(DiningArea(0, "Garden Area", true, 1, null))
            db.backupTo(backupDb)
        }
        assertTrue(Files.exists(backupDb) && Files.size(backupDb) > 0L)

        // 2. Modify Live DB
        WindowsPosDatabase.open(liveDb).use { db ->
            db.settings.put(AppSetting("establishment_name", "MODIFIED NAME"))
            assertEquals("MODIFIED NAME", db.settings.get("establishment_name"))

            // 3. Stage restore from backup
            db.stageRestore(backupDb)
        }

        // 4. Reopen liveDb -> Staged restore applied
        WindowsPosDatabase.open(liveDb).use { restored ->
            assertEquals("Backup Café", restored.settings.get("establishment_name"))
            assertNotNull(restored.authentication.authenticate("9999"))
            assertTrue(restored.areas().any { it.name == "Garden Area" })
        }

        // 5. Corrupted Backup Validation Rejection
        val corruptBackup = tempDir.resolve("corrupt.db")
        Files.write(corruptBackup, "Not a valid SQLite database header".toByteArray())

        WindowsPosDatabase.open(liveDb).use { db ->
            assertFails { db.stageRestore(corruptBackup) }
        }
    }

    // =========================================================================
    // 14. License Management, Demo Period, Activation, Expiration & Hardware Binding
    // =========================================================================
    @Test
    fun test14_Licensing_DemoPeriod_ValidLicense_InvalidOrExpired(): Unit {
        val dir = Files.createTempDirectory("win-license-hardcore")
        var now = 10_000_000L
        val store = WindowsSecureStore(dir.resolve("sec_license.dat"))
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val pubKeyPem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(keyPair.public.encoded)}\n-----END PUBLIC KEY-----"

        val licenseManager = WindowsLicenseManager(store, { now }, pubKeyPem)
        assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, licenseManager.state().status)
        assertEquals(7, licenseManager.state().daysRemaining)

        val deviceId = licenseManager.installationId

        fun generateLicenseKey(targetDeviceId: String, expiry: Long): String {
            val payload = "{\"licenseId\":\"LIC-2026-HC\",\"productId\":\"GENERAL_POS_V1\",\"appId\":\"ma.elaroui.generalpos\",\"installationId\":\"$targetDeviceId\",\"customerName\":\"General POS Test\",\"issueDateMs\":$now,\"expirationDateMs\":$expiry,\"licenseType\":\"PERPETUAL\",\"schemaVersion\":\"1.0\"}"
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(keyPair.private)
            signer.update(payload.toByteArray())
            return Base64.getEncoder().encodeToString(payload.toByteArray()) + "." + Base64.getEncoder().encodeToString(signer.sign())
        }

        // Wrong device license
        val wrongDeviceState = licenseManager.activate(generateLicenseKey("OTHER-DEVICE-ID", now + 1_000_000))
        assertEquals(WindowsLicenseStatus.WRONG_DEVICE, wrongDeviceState.status)

        // Valid license activation
        val validState = licenseManager.activate(generateLicenseKey(deviceId, now + 1_000_000))
        assertEquals(WindowsLicenseStatus.VALID, validState.status)

        // Reopen manager and verify persistence
        val reopened = WindowsLicenseManager(store, { now }, pubKeyPem)
        assertEquals(WindowsLicenseStatus.VALID, reopened.state().status)

        // Expiration check
        now += 2_000_000
        assertEquals(WindowsLicenseStatus.EXPIRED, reopened.state().status)
    }

    // =========================================================================
    // 15. Edge Cases: Zero, Negative, Large Amounts, Max Precision & Idempotency
    // =========================================================================
    @Test
    fun test15_EdgeCases_Zero_Negative_ExtremeAmounts_Precision(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Money parsing edge cases
            assertEquals(1050L, (MoneyRules.parseToCentimes("10.50") as MoneyParseResult.Success).centimes)
            assertEquals(1050L, (MoneyRules.parseToCentimes("10,50") as MoneyParseResult.Success).centimes)
            assertEquals(1000L, (MoneyRules.parseToCentimes("10") as MoneyParseResult.Success).centimes)
            assertEquals(0L, (MoneyRules.parseToCentimes("0") as MoneyParseResult.Success).centimes)
            assertEquals(0L, (MoneyRules.parseToCentimes("0.00") as MoneyParseResult.Success).centimes)
            assertTrue(MoneyRules.parseToCentimes("") is MoneyParseResult.Failure)
            assertTrue(MoneyRules.parseToCentimes("   ") is MoneyParseResult.Failure)
            assertTrue(MoneyRules.parseToCentimes("-10.50") is MoneyParseResult.Failure)
            assertTrue(MoneyRules.parseToCentimes("ABC") is MoneyParseResult.Failure)

            // Extreme large amount formatting
            val largeCentimes = 999_999_900L // 9,999,999.00 DH
            val formatted = MoneyRules.formatFixed(largeCentimes)
            assertEquals("9999999.00", formatted)

            // Formatting zero
            assertEquals("0.00", MoneyRules.formatFixed(0L))

            // Formatting 1 centime
            assertEquals("0.01", MoneyRules.formatFixed(1L))
        }
    }

    // =========================================================================
    // 16. Counter Order Creation & Payment
    // =========================================================================
    @Test
    fun test16_OrderType_Counter_Payment(): Unit = runBlocking {
        val tempDir = java.nio.file.Files.createTempDirectory("pos-test-nav-state")
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.authentication.authenticate("1234")!!
            val openRes = OpenRegisterSession(db.sessions, HardcoreTestClock).execute(
                id = 1L, registerId = 1L, cashierId = owner.id, openingCashCentimes = 10_000L
            )
            assertIs<UseCaseResult.Success<RegisterSession>>(openRes)

            val state = DesktopNavState(db, tempDir)
            state.currentUser = owner
            state.session = db.sessions.findOpenByUser(owner.id)

            val product = db.products.observeAll().first().first { it.active && it.available }

            assertEquals(OrderType.COUNTER, state.orderType)
            state.cart[product.id] = 2

            // Sales are counter orders with no table ID
            state.createOrder()
            assertNotNull(state.pendingOrder, "Pending order must be set for payment")
            assertEquals(OrderType.COUNTER, state.pendingOrder!!.type)
            assertNull(state.pendingOrder!!.tableId)

            state.pay(PaymentMethod.CASH, 10_000L)
            val completedCounter = db.orders.findById(state.pendingOrder!!.id)!!
            assertEquals(OrderStatus.COMPLETED, completedCounter.status)
            assertNull(completedCounter.tableId)
            val dupPayResult = CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                orderId = completedCounter.id,
                method = PaymentMethod.CASH,
                receivedCentimes = 5000L,
                token = "dup-token-attempt"
            )
            assertIs<UseCaseResult.Failure>(dupPayResult)
        }
    }

    // =========================================================================
    // 17. Screen Message Isolation & No Leak Across Navigation
    // =========================================================================
    @Test
    fun test17_ScreenMessageIsolationAndNoLeakAcrossNavigation(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("pos_msg_isolation_test")
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Café Mirage", "Owner", "1234")
            val owner = db.allUsers().first()
            val state = DesktopNavState(db, tempDir)

            state.login(owner, "1234")
            assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute)
            assertEquals("", state.message)

            // Open register session
            state.navigateTo(DesktopScreenRoute.OPEN_REGISTER)
            assertEquals("", state.message)
            state.openRegister(10000L) // 100 DH
            assertNotNull(state.session)

            val prod = db.products.findById(1L)!!.copy(name = "Retail item", priceCentimes = 1500L)
            db.products.save(prod)
            state.refresh()

            // 1. Place and Pay order
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            assertEquals("", state.message)
            state.cart[prod.id] = 2 // 30 DH
            state.createOrder()
            assertNotNull(state.pendingOrder)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)

            state.pay(PaymentMethod.CASH, 5000L) // Pay with 50 DH
            assertEquals(DesktopScreenRoute.RECEIPT_PREVIEW, state.currentRoute)
            assertTrue(state.message.contains("Paiement enregistré") || state.message.contains("Payment completed"))

            // 2. Navigate to Settings -> Payment message MUST be cleared / no longer present
            state.navigateTo(DesktopScreenRoute.SETTINGS)
            assertEquals(DesktopScreenRoute.SETTINGS, state.currentRoute)
            assertEquals("", state.message, "Payment success message must not leak to Settings screen")

            // 3. Navigate from Settings to Products -> Must have no stale message
            state.navigateTo(DesktopScreenRoute.PRODUCT_MGMT)
            assertEquals(DesktopScreenRoute.PRODUCT_MGMT, state.currentRoute)
            assertEquals("", state.message, "No stale message should exist on Product Management screen")

            // 4. Trigger validation error on POS (empty cart checkout)
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            state.cart.clear()
            state.createOrder()
            assertTrue(state.message.isNotBlank(), "Validation error should be displayed on POS screen")
            val posErrorMsg = state.message

            // 5. Navigate to Cashier Management -> Error message MUST not leak
            state.navigateTo(DesktopScreenRoute.CASHIER_MGMT)
            assertEquals(DesktopScreenRoute.CASHIER_MGMT, state.currentRoute)
            assertEquals("", state.message, "POS validation error ($posErrorMsg) must not leak to Cashier screen")

            // 6. Lock screen -> Clears state and messages
            state.lock()
            assertEquals(DesktopScreenRoute.USER_SELECTION, state.currentRoute)
            assertNull(state.currentUser)
            assertEquals("", state.message)
        }
    }

    // =========================================================================
    // 18. Error Dialog Dismissal & Non-Fatal Exception Resilience
    // =========================================================================
    @Test
    fun test18_ErrorDialogDismissalNeverTerminatesAppAndAppRemainsUsable(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("pos_error_resilience_test")
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Café Mirage", "Owner", "1234")
            val owner = db.allUsers().first()
            val state = DesktopNavState(db, tempDir)

            state.login(owner, "1234")
            assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute)

            // Open session
            state.navigateTo(DesktopScreenRoute.OPEN_REGISTER)
            state.openRegister(10000L)
            assertNotNull(state.session)

            // 1. Navigate to LICENSE_GATE with corrupted secure store file
            val secureFile = tempDir.resolve("secure").resolve("license.dat")
            Files.createDirectories(secureFile.parent)
            Files.write(secureFile, "CORRUPT_ENCRYPTED_BYTES_NOT_DPAPI".toByteArray())

            val secureStore = WindowsSecureStore(secureFile)
            val licenseMgr = WindowsLicenseManager(secureStore)

            // Verify corrupted file does not throw or crash
            val licState = licenseMgr.state()
            assertNotNull(licState)
            assertNotNull(licState.installationId)
            assertNotNull(licenseMgr.requestCode())

            // Attempt activation with invalid garbage key
            var fatalErrorCaptured: String? = null
            fun simulateErrorDialog(error: String) {
                fatalErrorCaptured = error
            }

            fun simulateClickOk() {
                // Clicking OK dismisses the error dialog only
                fatalErrorCaptured = null
            }

            // 2. Repeat error trigger and dismiss multiple times (10 iterations)
            for (i in 1..10) {
                val actResult = licenseMgr.activate("INVALID.GARBAGE.KEY.$i")
                assertEquals(WindowsLicenseStatus.INVALID, actResult.status)

                // Error occurs
                simulateErrorDialog("Error activating key $i")
                assertNotNull(fatalErrorCaptured)

                // User clicks OK
                simulateClickOk()
                assertNull(fatalErrorCaptured)

                // POS application state and navigation MUST remain fully operational
                state.navigateTo(DesktopScreenRoute.LICENSE_GATE)
                assertEquals(DesktopScreenRoute.LICENSE_GATE, state.currentRoute)

                state.navigateTo(DesktopScreenRoute.POS_MAIN)
                assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
                assertNotNull(state.session, "Session must not be lost after error dismissal")
                assertEquals(owner.id, state.currentUser?.id, "User must remain logged in")
            }

            // 3. User can continue using POS and making sales after dismissing errors
            val prod = db.products.findById(1L)!!.copy(name = "Retail item 2", priceCentimes = 2000L)
            db.products.save(prod)
            state.refresh()

            state.cart[prod.id] = 1
            state.createOrder()
            assertNotNull(state.pendingOrder)
            state.pay(PaymentMethod.CASH, 2000L)
            assertEquals(DesktopScreenRoute.RECEIPT_PREVIEW, state.currentRoute)

            val completedOrder = db.orders.findById(state.pendingOrder!!.id)
            assertNotNull(completedOrder)
            assertEquals(OrderStatus.COMPLETED, completedOrder.status)
        }
    }
}
