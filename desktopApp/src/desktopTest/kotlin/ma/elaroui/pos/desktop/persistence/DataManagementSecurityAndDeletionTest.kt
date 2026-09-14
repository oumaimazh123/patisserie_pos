package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.security.AdminDeletionSecurity
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class DataManagementSecurityAndDeletionTest {

    @Test
    fun testAdminDeletionSecurityHashingAndValidation() {
        // Enforce minimum 8 characters
        assertFails {
            AdminDeletionSecurity.hashPassword("short7", AdminDeletionSecurity.generateSalt())
        }

        val salt = AdminDeletionSecurity.generateSalt()
        assertEquals(32, salt.length) // 16 bytes = 32 hex chars

        val password = "MySecureAdminPass2026!"
        val hash = AdminDeletionSecurity.hashPassword(password, salt)
        assertFalse(hash.isBlank())

        // Correct password verification
        assertTrue(AdminDeletionSecurity.verifyPassword(password, hash, salt))

        // Incorrect password verification
        assertFalse(AdminDeletionSecurity.verifyPassword("WrongPassword123!", hash, salt))
        assertFalse(AdminDeletionSecurity.verifyPassword("", hash, salt))
        assertFalse(AdminDeletionSecurity.verifyPassword("short", hash, salt))
    }

    @Test
    fun testOwnerPinAuthorizesDeletion() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Default bootstrap owner has PIN "1234", which authorizes deletions
            assertTrue(db.verifyAdminDeletionPassword("1234"), "Owner PIN must authorize deletion")
            assertFalse(db.verifyAdminDeletionPassword("9999"), "Wrong PIN must be rejected")
            assertFalse(db.verifyAdminDeletionPassword(""), "Blank PIN must be rejected")

            // When owner PIN is updated, the new PIN authorizes deletions
            db.updateOwnerPin("5678")
            assertTrue(db.verifyAdminDeletionPassword("5678"), "New Owner PIN must authorize deletion")
            assertFalse(db.verifyAdminDeletionPassword("1234"), "Old Owner PIN must be rejected")
        }
    }

    @Test
    fun testConfigureInitialSetupWithOwnerPin() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup(
                establishmentName = "Initial Setup Patisserie",
                ownerName = "Zakaria Owner",
                ownerPin = "4321"
            )

            // Owner PIN authorizes deletion immediately
            assertTrue(db.verifyAdminDeletionPassword("4321"), "Owner PIN from setup must authorize deletion")
            assertFalse(db.verifyAdminDeletionPassword("1234"), "Old default PIN must be rejected")
            assertFalse(db.verifyAdminDeletionPassword("9999"), "Wrong PIN must fail")
        }
    }

    @Test
    fun testDeleteProducts() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val initialProductCount = db.products.observeAll().first().size
            val initialCategoryCount = db.categories.observeAll().first().size
            val catId = db.categories.save(Category(0L, "Bakery", true, 0))
            db.products.save(Product(0L, catId, "Croissant", 1500L, 1000, true, true))
            db.products.save(Product(0L, catId, "Baguette", 800L, 1000, true, true))

            assertEquals(initialProductCount + 2, db.products.observeAll().first().size)

            val deleted = db.deleteProducts()
            assertEquals(initialProductCount + 2, deleted)
            assertTrue(db.products.observeAll().first().isEmpty())
            // Categories should still be intact (initial + 1 added)
            assertEquals(initialCategoryCount + 1, db.categories.observeAll().first().size)
        }
    }

    @Test
    fun testDeleteCategories() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val initialCategoryCount = db.categories.observeAll().first().size
            val catId = db.categories.save(Category(0L, "Beverages", true, 0))
            db.products.save(Product(0L, catId, "Espresso", 1200L, 1000, true, true))

            val catDeleted = db.deleteCategories()
            assertEquals(initialCategoryCount + 1, catDeleted)
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertTrue(db.products.observeAll().first().isEmpty())
        }
    }

    @Test
    fun testDeleteSalesHistory() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val catId = db.categories.save(Category(0L, "Food", true, 0))
            val pId = db.products.save(Product(0L, catId, "Sandwich", 3000L, 1000, true, true))
            val session = db.sessions.save(RegisterSession(
                id = 10L,
                status = RegisterSessionStatus.OPEN,
                openingCashCentimes = 10000L,
                registerId = 1L,
                cashierId = 1L,
                openedAtEpochMilliseconds = System.currentTimeMillis(),
                closedAtEpochMilliseconds = null,
                expectedCashCentimes = null,
                countedCashCentimes = null,
                differenceCentimes = null
            ))

            val orderId = db.orders.save(Order(
                id = 0L,
                number = "SALE-001",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(OrderLine(pId, "Sandwich", 3000L, 1, 1000)),
                subtotalCentimes = 3000L,
                discountCentimes = 0L,
                taxCentimes = 300L,
                totalCentimes = 3000L,
                tableId = null,
                registerSessionId = session,
                cashierId = 1L
            ))

            db.payments.save(Payment(0L, orderId, session, PaymentMethod.CASH, 3000L, 3000L, 0L, PaymentStatus.COMPLETED, "token-1", System.currentTimeMillis()))

            assertEquals(1, db.salesHistory().size)

            val deleted = db.deleteSalesHistory()
            assertEquals(1, deleted)
            assertTrue(db.salesHistory().isEmpty())
        }
    }

    @Test
    fun testDeleteSuspendedSalesReleasesTables() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val areaId = db.saveArea(DiningArea(0L, "Terrace", true, 0))
            val tableId = db.tables.save(RestaurantTable(0L, areaId, "T1", TableStatus.AVAILABLE, true, 0))

            val catId = db.categories.save(Category(0L, "Drinks", true, 0))
            val pId = db.products.save(Product(0L, catId, "Tea", 1000L, 1000, true, true))

            val session = db.sessions.save(RegisterSession(
                id = 20L,
                status = RegisterSessionStatus.OPEN,
                openingCashCentimes = 10000L,
                registerId = 1L,
                cashierId = 1L,
                openedAtEpochMilliseconds = System.currentTimeMillis(),
                closedAtEpochMilliseconds = null,
                expectedCashCentimes = null,
                countedCashCentimes = null,
                differenceCentimes = null
            ))

            // Create open order on table T1
            db.orders.save(Order(
                id = 0L,
                number = "OPEN-001",
                type = OrderType.DINE_IN,
                status = OrderStatus.OPEN,
                lines = listOf(OrderLine(pId, "Tea", 1000L, 1, 1000)),
                subtotalCentimes = 1000L,
                discountCentimes = 0L,
                taxCentimes = 100L,
                totalCentimes = 1000L,
                tableId = tableId,
                registerSessionId = session,
                cashierId = 1L
            ))

            // Table should now be OCCUPIED
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(tableId)?.status)

            val deletedCount = db.deleteSuspendedSales()
            assertEquals(1, deletedCount)

            // Table should be released back to AVAILABLE
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(tableId)?.status)
            assertTrue(db.orderHistory(OrderStatus.OPEN).isEmpty())
        }
    }

    @Test
    fun testDeleteCashiersPreservesOwner() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val c1 = db.createCashier("Cashier 1", "5555")
            val c2 = db.createCashier("Cashier 2", "6666")

            assertEquals(3, db.allUsers().size) // 1 Owner + 2 Cashiers

            val deleted = db.deleteCashiers()
            assertEquals(2, deleted)

            val remainingUsers = db.allUsers()
            assertEquals(1, remainingUsers.size)
            assertEquals(UserRole.OWNER, remainingUsers.first().role)
        }
    }

    @Test
    fun testDeleteSelective() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val initialProductCount = db.products.observeAll().first().size
            val initialCategoryCount = db.categories.observeAll().first().size
            val catId = db.categories.save(Category(0L, "Snacks", true, 0))
            val pId = db.products.save(Product(0L, catId, "Chips", 500L, 1000, true, true))
            val cashierId = db.createCashier("Selective Cashier", "7777")

            val selection = DataGroupSelection(
                products = true,
                cashiers = true,
                categories = false
            )

            val summary = db.deleteSelective(selection)
            assertEquals(initialProductCount + 1, summary.productsDeleted)
            assertEquals(1, summary.cashiersDeleted)
            assertEquals(0, summary.categoriesDeleted)

            assertTrue(db.products.observeAll().first().isEmpty())
            assertEquals(initialCategoryCount + 1, db.categories.observeAll().first().size)
            assertEquals(1, db.allUsers().size) // Only owner left
        }
    }

    @Test
    fun testBusinessDataAndFactoryResetPreservesLicense() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.settings.put(AppSetting("license_key", "LIC-KEY-2026-TEST"))
            db.settings.put(AppSetting("license_signature", "SIG-ABC-123"))
            db.settings.put(AppSetting("license_status", "ACTIVE"))
            db.settings.put(AppSetting("establishment_name", "My Store"))

            val catId = db.categories.save(Category(0L, "Custom Test Category", true, 0))
            db.products.save(Product(0L, catId, "Item 1", 1000L, 1000, true, true))

            // Perform business data wipe
            val bSummary = db.deleteBusinessData()
            assertTrue(bSummary.totalRecordsDeleted > 0)
            assertTrue(db.products.observeAll().first().isEmpty())
            assertEquals("My Store", db.settings.get("establishment_name"))

            // Perform factory reset
            db.factoryResetData()
            assertNull(db.settings.get("establishment_name"))
            assertEquals("LIC-KEY-2026-TEST", db.settings.get("license_key"))
            assertEquals("SIG-ABC-123", db.settings.get("license_signature"))
            assertEquals("ACTIVE", db.settings.get("license_status"))
        }
    }

    @Test
    fun testFactoryResetToSetupAndLoginLifecycleWithoutRestart() = runBlocking {
        val tempDir = Files.createTempDirectory("pos_general_test_lifecycle")
        try {
            WindowsPosDatabase.openInMemory().use { db ->
                db.configureInitialSetup("General Store", "Owner", "1234")
                val navState = ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState(db, tempDir)

                // App starts configured
                assertTrue(navState.setupComplete)
                val ownerUser = db.allUsers().first { it.role == UserRole.OWNER }

                // Login as owner
                navState.login(ownerUser, "1234")
                assertEquals(ownerUser.id, navState.currentUser?.id)
                assertEquals(ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute.DASHBOARD, navState.currentRoute)

                // Perform Factory Reset
                db.factoryResetData()
                navState.onFactoryResetCompleted()

                // Verify in-memory state after reset
                assertFalse(navState.setupComplete)
                assertNull(navState.currentUser)
                assertEquals(ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute.SETUP, navState.currentRoute)

                // Execute Setup wizard
                val setupResult = navState.setup(
                    business = "General Store 2026",
                    address = "456 Avenue des Nations",
                    phone = "0699887766",
                    ownerName = "Nouveau Gérant",
                    pin = "8888"
                )
                assertTrue(setupResult.isSuccess)

                // Verify state after setup completion (ready for user selection / login)
                assertTrue(navState.setupComplete)
                assertNull(navState.currentUser)
                assertEquals(ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute.USER_SELECTION, navState.currentRoute)

                // Verify new owner can log in with new PIN
                val newOwner = db.allUsers().first { it.role == UserRole.OWNER }
                assertEquals("Nouveau Gérant", newOwner.name)
                navState.login(newOwner, "8888")

                assertNotNull(navState.currentUser)
                assertEquals("Nouveau Gérant", navState.currentUser?.name)
                assertEquals(ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute.DASHBOARD, navState.currentRoute)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
