package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class SalesDeletionComprehensiveTest {

    private fun WindowsPosDatabase.queryInt(sql: String): Int = read { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private suspend fun setupTestEnvironment(db: WindowsPosDatabase): Triple<Long, Long, Long> {
        val catId = db.categories.save(Category(0L, "Beverages", true, 0))
        val p1 = db.products.save(Product(0L, catId, "Espresso", 1500L, 1000, true, true))
        val p2 = db.products.save(Product(0L, catId, "Croissant", 2000L, 1000, true, true))
        return Triple(catId, p1, p2)
    }

    private suspend fun createSession(db: WindowsPosDatabase, cashierId: Long): Long {
        return db.sessions.save(RegisterSession(
            id = 0L,
            status = RegisterSessionStatus.OPEN,
            openingCashCentimes = 50000L,
            registerId = 1L,
            cashierId = cashierId,
            openedAtEpochMilliseconds = System.currentTimeMillis(),
            closedAtEpochMilliseconds = null,
            expectedCashCentimes = null,
            countedCashCentimes = null,
            differenceCentimes = null
        ))
    }

    // 1. Create multiple completed sales -> verify deleteSalesHistory deletes them all
    @Test
    fun testDeleteCompletedSalesDeletesAllCompletedSales(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val (_, p1, p2) = setupTestEnvironment(db)
            val sessionId = createSession(db, 1L)

            // Create 3 completed orders with multiple items and payments
            for (i in 1..3) {
                val orderId = db.orders.save(Order(
                    id = 0L,
                    number = "SALE-00$i",
                    type = OrderType.COUNTER,
                    status = OrderStatus.COMPLETED,
                    lines = listOf(
                        OrderLine(p1, "Espresso", 1500L, 2, 1000),
                        OrderLine(p2, "Croissant", 2000L, 1, 1000)
                    ),
                    subtotalCentimes = 5000L,
                    discountCentimes = 0L,
                    taxCentimes = 500L,
                    totalCentimes = 5000L,
                    tableId = null,
                    registerSessionId = sessionId,
                    cashierId = 1L
                ))
                db.payments.save(Payment(
                    id = 0L,
                    orderId = orderId,
                    registerSessionId = sessionId,
                    method = if (i % 2 == 0) PaymentMethod.CARD else PaymentMethod.CASH,
                    amountCentimes = 5000L,
                    receivedCentimes = 5000L,
                    changeCentimes = 0L,
                    status = PaymentStatus.COMPLETED,
                    submissionToken = "tok-$i",
                    createdAtEpochMilliseconds = System.currentTimeMillis()
                ))
            }

            assertEquals(3, db.salesHistory().size)

            val deletedCount = db.deleteSalesHistory()
            assertEquals(3, deletedCount)
            assertTrue(db.salesHistory().isEmpty())
            assertEquals(0, db.orderHistory(OrderStatus.COMPLETED).size)

            // Verify order items and payments for completed sales are deleted
            val orphanItems = db.queryInt("SELECT count(*) FROM order_items")
            val orphanPayments = db.queryInt("SELECT count(*) FROM payments")
            assertEquals(0, orphanItems)
            assertEquals(0, orphanPayments)
        }
    }

    // 2. Create multiple pending sales -> verify deleteSuspendedSales deletes them all and releases tables
    @Test
    fun testDeleteSuspendedSalesDeletesAllPendingSalesAndReleasesTables(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val (_, p1, _) = setupTestEnvironment(db)
            val areaId = db.saveArea(DiningArea(0L, "Main Hall", true, 0))
            val table1 = db.tables.save(RestaurantTable(0L, areaId, "Table 1", TableStatus.AVAILABLE, true, 0))
            val table2 = db.tables.save(RestaurantTable(0L, areaId, "Table 2", TableStatus.AVAILABLE, true, 0))
            val sessionId = createSession(db, 1L)

            // Create 2 open orders assigned to tables
            db.orders.save(Order(
                id = 0L,
                number = "OPEN-001",
                type = OrderType.DINE_IN,
                status = OrderStatus.OPEN,
                lines = listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)),
                subtotalCentimes = 1500L,
                discountCentimes = 0L,
                taxCentimes = 150L,
                totalCentimes = 1500L,
                tableId = table1,
                registerSessionId = sessionId,
                cashierId = 1L
            ))
            db.orders.save(Order(
                id = 0L,
                number = "OPEN-002",
                type = OrderType.DINE_IN,
                status = OrderStatus.OPEN,
                lines = listOf(OrderLine(p1, "Espresso", 1500L, 2, 1000)),
                subtotalCentimes = 3000L,
                discountCentimes = 0L,
                taxCentimes = 300L,
                totalCentimes = 3000L,
                tableId = table2,
                registerSessionId = sessionId,
                cashierId = 1L
            ))

            // Verify tables are OCCUPIED
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(table1)?.status)
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(table2)?.status)
            assertEquals(2, db.orderHistory(OrderStatus.OPEN).size)

            val deletedCount = db.deleteSuspendedSales()
            assertEquals(2, deletedCount)

            // Verify all open orders are gone
            assertTrue(db.orderHistory(OrderStatus.OPEN).isEmpty())

            // Verify tables are released back to AVAILABLE
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(table1)?.status)
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(table2)?.status)

            // Verify child records
            assertEquals(0, db.queryInt("SELECT count(*) FROM order_items"))
        }
    }

    // 3. Isolation: completed sales deletion does NOT delete pending sales, and vice versa
    @Test
    fun testDeletionIsolationBetweenCompletedAndPendingSales(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val (_, p1, p2) = setupTestEnvironment(db)
            val sessionId = createSession(db, 1L)

            // Create 1 completed sale
            val comp1 = db.orders.save(Order(
                id = 0L,
                number = "COMP-01",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)),
                subtotalCentimes = 1500L,
                discountCentimes = 0L,
                taxCentimes = 150L,
                totalCentimes = 1500L,
                tableId = null,
                registerSessionId = sessionId,
                cashierId = 1L
            ))
            db.payments.save(Payment(0L, comp1, sessionId, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok-c1", System.currentTimeMillis()))

            // Create 1 open order
            val open1 = db.orders.save(Order(
                id = 0L,
                number = "OPEN-01",
                type = OrderType.COUNTER,
                status = OrderStatus.OPEN,
                lines = listOf(OrderLine(p2, "Croissant", 2000L, 2, 1000)),
                subtotalCentimes = 4000L,
                discountCentimes = 0L,
                taxCentimes = 400L,
                totalCentimes = 4000L,
                tableId = null,
                registerSessionId = sessionId,
                cashierId = 1L
            ))

            assertEquals(1, db.orderHistory(OrderStatus.COMPLETED).size)
            assertEquals(1, db.orderHistory(OrderStatus.OPEN).size)
            assertEquals(2, db.salesHistory().size)

            // Action: Delete completed sales only
            val deletedSales = db.deleteSalesHistory()
            assertEquals(1, deletedSales)

            // Verify: Completed sale is gone, but open order is intact!
            assertEquals(0, db.orderHistory(OrderStatus.COMPLETED).size)
            assertTrue(db.salesHistory(status = OrderStatus.COMPLETED).isEmpty())
            val remainingOpen = db.orderHistory(OrderStatus.OPEN)
            assertEquals(1, remainingOpen.size)
            assertEquals(open1, remainingOpen.first().id)
            assertEquals("OPEN-01", remainingOpen.first().number)

            // Child items of open order are still intact
            assertEquals(1, db.queryInt("SELECT count(*) FROM order_items WHERE order_id = $open1"))

            // Action: Now delete suspended sales
            val deletedSuspended = db.deleteSuspendedSales()
            assertEquals(1, deletedSuspended)

            // Both are now empty
            assertTrue(db.orderHistory(OrderStatus.OPEN).isEmpty())
            assertEquals(0, db.queryInt("SELECT count(*) FROM order_items"))
        }
    }

    // 4. Test with empty database (must not fail or crash)
    @Test
    fun testDeletionOnEmptyDatabaseDoesNotCrash(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Empty database
            val deletedSales = db.deleteSalesHistory()
            assertEquals(0, deletedSales)

            val deletedSuspended = db.deleteSuspendedSales()
            assertEquals(0, deletedSuspended)

            val selective = db.deleteSelective(DataGroupSelection(salesHistory = true, suspendedSales = true))
            assertEquals(0, selective.salesHistoryDeleted)
            assertEquals(0, selective.suspendedSalesDeleted)
        }
    }

    // 5. Test with multiple cash sessions (orders across sessions deleted, sessions and cash movements preserved)
    @Test
    fun testDeletionWithMultipleRegisterSessions(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val (_, p1, _) = setupTestEnvironment(db)
            val cashier2 = db.createCashier("Second Cashier", "4321")

            val session1 = createSession(db, 1L)
            val session2 = createSession(db, cashier2)

            // Add cash movements to both sessions
            db.cashMovements.save(CashMovement(0L, session1, CashMovementType.CASH_IN, 10000L, "Opening extra", null, 1L, System.currentTimeMillis()))
            db.cashMovements.save(CashMovement(0L, session2, CashMovementType.CASH_IN, 20000L, "Opening extra 2", null, cashier2, System.currentTimeMillis()))

            // Orders in session 1
            val o1 = db.orders.save(Order(0L, "S1-O1", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session1, 1L))
            db.payments.save(Payment(0L, o1, session1, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok-s1", System.currentTimeMillis()))

            // Orders in session 2
            val o2 = db.orders.save(Order(0L, "S2-O1", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session2, cashier2))
            db.payments.save(Payment(0L, o2, session2, PaymentMethod.CARD, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok-s2", System.currentTimeMillis()))

            assertEquals(2, db.salesHistory().size)

            val deleted = db.deleteSalesHistory()
            assertEquals(2, deleted)
            assertTrue(db.salesHistory().isEmpty())

            // Crucial: Register sessions are NOT deleted!
            val s1 = db.sessions.findById(session1)
            val s2 = db.sessions.findById(session2)
            assertNotNull(s1)
            assertNotNull(s2)

            // Crucial: Cash movements are NOT deleted!
            val movements1 = db.cashMovements.observeForSession(session1).first()
            val movements2 = db.cashMovements.observeForSession(session2).first()
            assertEquals(1, movements1.size)
            assertEquals(1, movements2.size)
        }
    }

    // 6. Verify relationships: orders, articles (order_items), payments, and sessions - zero orphan records
    @Test
    fun testRelationshipIntegrityNoOrphanRecords(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val (_, p1, p2) = setupTestEnvironment(db)
            val session = createSession(db, 1L)

            val o1 = db.orders.save(Order(0L, "O1", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session, 1L))
            db.payments.save(Payment(0L, o1, session, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok-1", System.currentTimeMillis()))

            val o2 = db.orders.save(Order(0L, "O2", OrderType.COUNTER, OrderStatus.OPEN, listOf(OrderLine(p2, "Croissant", 2000L, 1, 1000)), 2000L, 0L, 200L, 2000L, null, session, 1L))

            // Delete completed sales
            db.deleteSalesHistory()

            // Verify no orphaned order_items or payments pointing to non-existent orders
            val orphanItems1 = db.queryInt("SELECT count(*) FROM order_items WHERE order_id NOT IN (SELECT id FROM orders)")
            val orphanPayments1 = db.queryInt("SELECT count(*) FROM payments WHERE order_id NOT IN (SELECT id FROM orders)")
            assertEquals(0, orphanItems1)
            assertEquals(0, orphanPayments1)

            // Delete suspended sales
            db.deleteSuspendedSales()

            val orphanItems2 = db.queryInt("SELECT count(*) FROM order_items WHERE order_id NOT IN (SELECT id FROM orders)")
            val orphanPayments2 = db.queryInt("SELECT count(*) FROM payments WHERE order_id NOT IN (SELECT id FROM orders)")
            assertEquals(0, orphanItems2)
            assertEquals(0, orphanPayments2)
        }
    }

    // 7. Verify after restart that deleted data does NOT reappear
    @Test
    fun testPersistenceAcrossDatabaseReopen(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("pos_general_test_persistence")
        val dbFile = tempDir.resolve("pos_restart.db")
        try {
            // First run: insert sales and delete them
            WindowsPosDatabase.open(dbFile).use { db ->
                db.configureInitialSetup("Restart Test Store", "Owner Zakaria", "1234")
                val (_, p1, _) = setupTestEnvironment(db)
                val session = createSession(db, 1L)

                val o1 = db.orders.save(Order(0L, "PERST-01", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session, 1L))
                db.payments.save(Payment(0L, o1, session, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok-p", System.currentTimeMillis()))

                assertEquals(1, db.salesHistory().size)

                val deleted = db.deleteSalesHistory()
                assertEquals(1, deleted)
                assertTrue(db.salesHistory().isEmpty())
            }

            // Second run: reopen DB from disk and verify data is still gone
            WindowsPosDatabase.open(dbFile).use { db ->
                assertTrue(db.salesHistory().isEmpty())
                assertEquals(0, db.orderHistory(OrderStatus.COMPLETED).size)
                assertEquals(0, db.queryInt("SELECT count(*) FROM order_items"))
                assertEquals(0, db.queryInt("SELECT count(*) FROM payments"))
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // 8. Verify NO other business data is deleted (products, categories, users/cashiers, settings, etc.)
    @Test
    fun testPreservationOfNonTargetedBusinessData(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Store Preservation Test", "Owner Zakaria", "1234")
            val (catId, p1, p2) = setupTestEnvironment(db)
            val cashierId = db.createCashier("Preserved Cashier", "9999")
            val areaId = db.saveArea(DiningArea(0L, "Patio", true, 0))
            val tableId = db.tables.save(RestaurantTable(0L, areaId, "P-01", TableStatus.AVAILABLE, true, 0))
            db.settings.put(AppSetting("print_auto_closure_report", "true"))
            val session = createSession(db, 1L)

            // Create sales and delete both completed and suspended
            val o1 = db.orders.save(Order(0L, "O-1", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session, 1L))
            db.payments.save(Payment(0L, o1, session, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok", System.currentTimeMillis()))
            db.orders.save(Order(0L, "O-2", OrderType.DINE_IN, OrderStatus.OPEN, listOf(OrderLine(p2, "Croissant", 2000L, 1, 1000)), 2000L, 0L, 200L, 2000L, tableId, session, 1L))

            db.deleteSalesHistory()
            db.deleteSuspendedSales()

            // Verify Products are 100% intact
            val products = db.products.observeAll().first()
            assertTrue(products.size >= 2)
            assertTrue(products.any { it.id == p1 && it.name == "Espresso" })
            assertTrue(products.any { it.id == p2 && it.name == "Croissant" })

            // Verify Categories are 100% intact
            val categories = db.categories.observeAll().first()
            assertTrue(categories.isNotEmpty())
            assertTrue(categories.any { it.name == "Beverages" })

            // Verify Cashiers and Users are 100% intact
            val users = db.allUsers()
            assertEquals(2, users.size) // Owner + Preserved Cashier
            assertTrue(users.any { it.id == cashierId && it.name == "Preserved Cashier" })

            // Verify Dining Area & Tables are intact (table is AVAILABLE)
            assertTrue(db.areas().any { it.id == areaId && it.name == "Patio" })
            val table = db.tables.findById(tableId)
            assertNotNull(table)
            assertEquals(TableStatus.AVAILABLE, table.status)

            // Verify Settings are 100% intact
            assertEquals("true", db.settings.get("print_auto_closure_report"))
            assertEquals("Store Preservation Test", db.settings.get("establishment_name"))
        }
    }

    // 9. Verify DesktopNavState refreshes screens and state immediately upon deletion
    @Test
    fun testNavStateImmediateRefreshOnDeletion(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("pos_navstate_refresh_test")
        try {
            WindowsPosDatabase.openInMemory().use { db ->
                db.configureInitialSetup("PATISSERIE_POS", "Zakaria Owner", "1234")
                val navState = DesktopNavState(db, tempDir)
                val owner = db.allUsers().first { it.role == UserRole.OWNER }
                navState.login(owner, "1234")

                val (_, p1, _) = setupTestEnvironment(db)
                val session = createSession(db, owner.id)

                // Populate sales history
                val o1 = db.orders.save(Order(0L, "NAV-01", OrderType.COUNTER, OrderStatus.COMPLETED, listOf(OrderLine(p1, "Espresso", 1500L, 1, 1000)), 1500L, 0L, 150L, 1500L, null, session, owner.id))
                db.payments.save(Payment(0L, o1, session, PaymentMethod.CASH, 1500L, 1500L, 0L, PaymentStatus.COMPLETED, "tok", System.currentTimeMillis()))

                // Simulate sales history being displayed
                navState.loadSalesHistory()
                assertEquals(1, navState.salesHistory.size)

                // Delete sales
                db.deleteSalesHistory()

                // Trigger UI refresh notification
                navState.onDataResetOrDeleted()

                // Verify navState salesHistory is immediately cleared!
                assertTrue(navState.salesHistory.isEmpty())
                assertTrue(navState.cart.isEmpty())
                assertNull(navState.pendingOrder)
                assertNull(navState.editingOrderId)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
