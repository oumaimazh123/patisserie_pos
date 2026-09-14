package ma.elaroui.pos.desktop.persistence

import java.sql.DriverManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.security.AdminDeletionSecurity
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class DataManagementDeepAuditTest {

    @Test
    fun testAdminSecurityEdgeCasesAndBruteForceResistance() {
        // Minimum 8 characters requirement
        listOf("", "a", "1234567", "pass!7 ", "       ").forEach { invalid ->
            assertFails {
                AdminDeletionSecurity.hashPassword(invalid, AdminDeletionSecurity.generateSalt())
            }
        }

        // Exactly 8 characters and longer
        val valid8 = "12345678"
        val salt1 = AdminDeletionSecurity.generateSalt()
        val hash8 = AdminDeletionSecurity.hashPassword(valid8, salt1)
        assertTrue(AdminDeletionSecurity.verifyPassword(valid8, hash8, salt1))
        assertFalse(AdminDeletionSecurity.verifyPassword("12345679", hash8, salt1))

        // Unicode, accent, symbol password support
        val complexPass = "C@fé_Rèstaurànt-POS#2026! 🚀"
        val saltComplex = AdminDeletionSecurity.generateSalt()
        val hashComplex = AdminDeletionSecurity.hashPassword(complexPass, saltComplex)
        assertTrue(AdminDeletionSecurity.verifyPassword(complexPass, hashComplex, saltComplex))
        assertFalse(AdminDeletionSecurity.verifyPassword("C@fe_Restaurant-POS#2026!", hashComplex, saltComplex))

        // Salt uniqueness test across 200 generations
        val salts = (1..200).map { AdminDeletionSecurity.generateSalt() }.toSet()
        assertEquals(200, salts.size, "All generated salts must be cryptographically unique")

        // Same password with different salts must yield distinct hashes
        val saltA = AdminDeletionSecurity.generateSalt()
        val saltB = AdminDeletionSecurity.generateSalt()
        val hashA = AdminDeletionSecurity.hashPassword(valid8, saltA)
        val hashB = AdminDeletionSecurity.hashPassword(valid8, saltB)
        assertNotEquals(hashA, hashB, "Hashes of same password with different salts must not match")

        // Cross-verification with wrong salt or tampered hash
        assertFalse(AdminDeletionSecurity.verifyPassword(valid8, hashA, saltB))
        assertFalse(AdminDeletionSecurity.verifyPassword(valid8, hashA.reversed(), saltA))
    }

    @Test
    fun testAuditLoggingIntegrityAndNoPasswordLeakage() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val initialLogs = db.auditLogs().size

            val catId = db.categories.save(Category(0L, "Deep Test Cat", true, 0))
            db.products.save(Product(0L, catId, "Deep Item", 1000L, 1000, true, true))

            // 1. Delete products
            db.deleteProducts(actingUserId = 1L)

            // 2. Delete categories
            db.deleteCategories(actingUserId = 1L)

            // 3. Delete cashiers
            db.createCashier("Cashier Audit", "9999")
            db.deleteCashiers(actingUserId = 1L)

            val logs = db.auditLogs()
            assertTrue(logs.size >= initialLogs + 3, "Audit logs should record all deletion operations")

            val productLog = logs.firstOrNull { it.entityType == "PRODUCTS" }
            assertNotNull(productLog)
            assertEquals("DATA_DELETION", productLog.actionType)
            assertEquals(1L, productLog.actingUserId)
            assertTrue(productLog.details?.contains("Suppression") == true)

            val cashierLog = logs.firstOrNull { it.entityType == "CASHIERS" }
            assertNotNull(cashierLog)
            assertEquals(1L, cashierLog.actingUserId)

            // Verify no passwords or salts leak in any log details
            logs.forEach { log ->
                val details = log.details.orEmpty().lowercase()
                assertFalse(details.contains("password"), "Password keyword should never be recorded in audit log")
                assertFalse(details.contains("pbkdf2"), "Hash details should never be leaked in audit log")
                assertFalse(details.contains("pin_hash"), "Pin hash should never be leaked in audit log")
            }
        }
    }

    @Test
    fun testAllPermutationsOfSelectiveDeletion() = runBlocking {
        // Permutation 1: Empty Selection
        WindowsPosDatabase.openInMemory().use { db ->
            val summary = db.deleteSelective(DataGroupSelection())
            assertEquals(0, summary.totalRecordsDeleted)
            assertEquals(0, summary.productsDeleted)
            assertEquals(0, summary.categoriesDeleted)
            assertEquals(0, summary.salesHistoryDeleted)
            assertEquals(0, summary.suspendedSalesDeleted)
            assertEquals(0, summary.tablesDeleted)
            assertEquals(0, summary.cashiersDeleted)
        }

        // Permutation 2: Products Only (Categories Intact)
        WindowsPosDatabase.openInMemory().use { db ->
            val initialProds = db.products.observeAll().first().size
            val initialCats = db.categories.observeAll().first().size
            val summary = db.deleteSelective(DataGroupSelection(products = true, categories = false))
            assertEquals(initialProds, summary.productsDeleted)
            assertEquals(0, summary.categoriesDeleted)
            assertTrue(db.products.observeAll().first().isEmpty())
            assertEquals(initialCats, db.categories.observeAll().first().size)
        }

        // Permutation 3: Categories Selected (Implicitly purges Products & Categories)
        WindowsPosDatabase.openInMemory().use { db ->
            val initialProds = db.products.observeAll().first().size
            val initialCats = db.categories.observeAll().first().size
            val summary = db.deleteSelective(DataGroupSelection(products = false, categories = true))
            assertEquals(initialProds, summary.productsDeleted)
            assertEquals(initialCats, summary.categoriesDeleted)
            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.categories.observeAll().first().isEmpty())
        }

        // Permutation 4: Suspended Sales Only (Frees tables, preserves sales history)
        WindowsPosDatabase.openInMemory().use { db ->
            val initialSales = db.salesHistory().size
            val areaId = db.saveArea(DiningArea(0L, "Main Room", true, 0))
            val tableId = db.tables.save(RestaurantTable(0L, areaId, "Table 10", TableStatus.AVAILABLE, true, 0))
            val catId = db.categories.save(Category(0L, "Beverages", true, 0))
            val pId = db.products.save(Product(0L, catId, "Water", 500L, 1000, true, true))

            val session = db.sessions.save(RegisterSession(
                id = 100L, status = RegisterSessionStatus.OPEN, openingCashCentimes = 5000L,
                registerId = 1L, cashierId = 1L, openedAtEpochMilliseconds = System.currentTimeMillis(),
                closedAtEpochMilliseconds = null, expectedCashCentimes = null, countedCashCentimes = null, differenceCentimes = null
            ))

            // Create open order on table
            db.orders.save(Order(
                id = 0L, number = "SUSP-01", type = OrderType.DINE_IN, status = OrderStatus.OPEN,
                lines = listOf(OrderLine(pId, "Water", 500L, 2, 1000)), subtotalCentimes = 1000L,
                discountCentimes = 0L, taxCentimes = 100L, totalCentimes = 1000L, tableId = tableId,
                registerSessionId = session, cashierId = 1L
            ))
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(tableId)?.status)

            // Create completed sale
            val compOrderId = db.orders.save(Order(
                id = 0L, number = "COMP-01", type = OrderType.COUNTER, status = OrderStatus.COMPLETED,
                lines = listOf(OrderLine(pId, "Water", 500L, 1, 1000)), subtotalCentimes = 500L,
                discountCentimes = 0L, taxCentimes = 50L, totalCentimes = 500L, tableId = null,
                registerSessionId = session, cashierId = 1L
            ))
            db.payments.save(Payment(0L, compOrderId, session, PaymentMethod.CASH, 500L, 500L, 0L, PaymentStatus.COMPLETED, "tok-c1", System.currentTimeMillis()))

            assertEquals(1, db.orderHistory(OrderStatus.OPEN).size)
            assertEquals(1, db.salesHistory(status = OrderStatus.COMPLETED).size)

            val summary = db.deleteSelective(DataGroupSelection(suspendedSales = true))
            assertEquals(1, summary.suspendedSalesDeleted)
            assertEquals(0, summary.salesHistoryDeleted)

            // Table should be released back to AVAILABLE
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(tableId)?.status)
            assertTrue(db.orderHistory(OrderStatus.OPEN).isEmpty())
            assertEquals(1, db.salesHistory(status = OrderStatus.COMPLETED).size, "Completed sales history must remain intact")
        }

        // Permutation 5: Tables and Dining Areas Only
        WindowsPosDatabase.openInMemory().use { db ->
            val initialTables = db.allTables().size
            val initialAreas = db.areas().size
            val areaId = db.saveArea(DiningArea(0L, "Garden", true, 0))
            val tableId = db.tables.save(RestaurantTable(0L, areaId, "G1", TableStatus.AVAILABLE, true, 0))
            assertEquals(initialTables + 1, db.allTables().size)

            val summary = db.deleteSelective(DataGroupSelection(tablesAndAreas = true))
            assertEquals(initialTables + 1, summary.tablesDeleted)
            assertEquals(initialAreas + 1, summary.areasDeleted)
            assertTrue(db.allTables().isEmpty())
            assertTrue(db.areas().isEmpty())
        }
    }

    @Test
    fun testStateFlowReactivityAcrossAllRepositories() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val catId = db.categories.save(Category(0L, "Flow Test Cat", true, 0))
            db.products.save(Product(0L, catId, "Flow Item", 2000L, 1000, true, true))
            val areaId = db.saveArea(DiningArea(0L, "Flow Area", true, 0))
            db.tables.save(RestaurantTable(0L, areaId, "F1", TableStatus.AVAILABLE, true, 0))
            db.createCashier("Flow Cashier", "8888")

            // Verify initial emission
            assertTrue(db.products.observeAll().first().any { it.name == "Flow Item" })
            assertTrue(db.categories.observeAll().first().any { it.name == "Flow Test Cat" })
            assertTrue(db.tables.observeAll().first().any { it.name == "F1" })
            assertTrue(db.users.observeActive().first().any { it.name == "Flow Cashier" })

            // Perform deletions and verify flows react immediately
            db.deleteProducts()
            assertTrue(db.products.observeAll().first().isEmpty(), "Product flow should emit empty list immediately")

            db.deleteCategories()
            assertTrue(db.categories.observeAll().first().isEmpty(), "Category flow should emit empty list immediately")

            db.deleteTablesAndAreas()
            assertTrue(db.tables.observeAll().first().isEmpty(), "Table flow should emit empty list immediately")

            db.deleteCashiers()
            assertEquals(1, db.users.observeActive().first().size, "User flow should emit only owner immediately")
            assertEquals(UserRole.OWNER, db.users.observeActive().first().first().role)
        }
    }

    @Test
    fun testFactoryResetDeepIntegrityAndLicensePreservation() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Seed custom data across all domains
            db.settings.put(AppSetting("establishment_name", "Grand Café Central"))
            db.settings.put(AppSetting("establishment_specialty", "Pâtisserie & Salon de Thé"))
            db.settings.put(AppSetting("establishment_address", "123 Avenue Mohammed V"))
            db.settings.put(AppSetting("establishment_phone", "0522001122"))
            db.settings.put(AppSetting("license_key", "LIC-PREMIUM-2026-XYZ"))
            db.settings.put(AppSetting("license_signature", "SIGNATURE-PROD-998877"))
            db.settings.put(AppSetting("license_status", "ACTIVE"))

            val catId = db.categories.save(Category(0L, "Pastry", true, 0))
            val pId = db.products.save(Product(0L, catId, "Eclair", 1500L, 1000, true, true))
            val areaId = db.saveArea(DiningArea(0L, "VIP Lounge", true, 0))
            db.tables.save(RestaurantTable(0L, areaId, "VIP-1", TableStatus.AVAILABLE, true, 0))
            val cashierId = db.createCashier("Amina", "4321")

            val session = db.sessions.save(RegisterSession(
                id = 50L, status = RegisterSessionStatus.OPEN, openingCashCentimes = 20000L,
                registerId = 1L, cashierId = cashierId, openedAtEpochMilliseconds = System.currentTimeMillis(),
                closedAtEpochMilliseconds = null, expectedCashCentimes = null, countedCashCentimes = null, differenceCentimes = null
            ))

            db.cashMovements.save(CashMovement(0L, session, CashMovementType.CASH_IN, 5000L, "Fonds initial", null, cashierId, System.currentTimeMillis()))

            val orderId = db.orders.save(Order(
                id = 0L, number = "VIP-001", type = OrderType.DINE_IN, status = OrderStatus.COMPLETED,
                lines = listOf(OrderLine(pId, "Eclair", 1500L, 2, 1000)), subtotalCentimes = 3000L,
                discountCentimes = 0L, taxCentimes = 300L, totalCentimes = 3000L, tableId = null,
                registerSessionId = session, cashierId = cashierId
            ))
            db.payments.save(Payment(0L, orderId, session, PaymentMethod.CASH, 3000L, 3000L, 0L, PaymentStatus.COMPLETED, "tok-vip", System.currentTimeMillis()))

            // Verify populated state before reset
            assertTrue(db.products.observeAll().first().isNotEmpty())
            assertTrue(db.categories.observeAll().first().isNotEmpty())
            assertTrue(db.allTables().isNotEmpty())
            assertTrue(db.allUsers().size >= 2)
            assertEquals("Grand Café Central", db.settings.get("establishment_name"))

            // Perform complete factory reset
            db.factoryResetData(actingUserId = 1L)

            // Verify ALL domain tables are wiped clean
            assertTrue(db.products.observeAll().first().isEmpty(), "Products must be completely empty after factory reset")
            assertTrue(db.categories.observeAll().first().isEmpty(), "Categories must be completely empty after factory reset")
            assertTrue(db.allTables().isEmpty(), "Tables must be completely empty after factory reset")
            assertTrue(db.areas().isEmpty(), "Dining areas must be completely empty after factory reset")
            assertTrue(db.allUsers().isEmpty(), "Users table must be reset after factory reset")
            assertTrue(db.salesHistory().isEmpty(), "Sales history must be completely empty after factory reset")
            assertTrue(db.orderHistory(OrderStatus.OPEN).isEmpty(), "Open orders must be completely empty after factory reset")
            assertNull(db.lastClosedSession(), "Sessions must be completely reset")

            // Verify business settings are wiped
            assertNull(db.settings.get("establishment_name"), "Establishment name must be wiped")
            assertNull(db.settings.get("establishment_specialty"), "Establishment specialty must be wiped")
            assertNull(db.settings.get("establishment_address"), "Establishment address must be wiped")

            // Verify LICENSE IS PRESERVED INTACT
            assertEquals("LIC-PREMIUM-2026-XYZ", db.settings.get("license_key"), "License key must be preserved")
            assertEquals("SIGNATURE-PROD-998877", db.settings.get("license_signature"), "License signature must be preserved")
            assertEquals("ACTIVE", db.settings.get("license_status"), "License status must be preserved")
        }
    }

    @Test
    fun testConcurrentReadsAndDeletions() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val catId = db.categories.save(Category(0L, "Stress Category", true, 0))
            (1..20).forEach { i ->
                db.products.save(Product(0L, catId, "Stress Product $i", (100 * i).toLong(), 1000, true, true))
            }

            // Launch concurrent reading coroutines
            val readJobs = (1..10).map {
                async {
                    var readCount = 0
                    repeat(15) {
                        readCount += db.products.observeAll().first().size
                        readCount += db.categories.observeAll().first().size
                        readCount += db.allUsers().size
                    }
                    readCount
                }
            }

            // Perform deletion in parallel
            val deletionJob = async {
                db.deleteProducts()
            }

            val totalReads = readJobs.awaitAll().sum()
            val deletedCount = deletionJob.await()

            assertTrue(totalReads >= 0)
            assertTrue(deletedCount >= 20)
            assertTrue(db.products.observeAll().first().isEmpty())
        }
    }
}
