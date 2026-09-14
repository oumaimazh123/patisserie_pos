package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class ActiveInactiveEntityAuditTest {

    @Test
    fun testProductActivationDeactivationCycleAndPersistence() = runBlocking {
        val tempDir = Files.createTempDirectory("pos-product-active-test")
        val dbPath = tempDir.resolve("pos.db")

        // 1. Initial creation
        var db = WindowsPosDatabase.open(dbPath)
        val catId = db.categories.save(Category(id = 0L, name = "Boissons", displayOrder = 1, active = true))
        val prodId = db.products.save(
            Product(
                id = 0L,
                categoryId = catId,
                name = "Espresso",
                priceCentimes = 1500L,
                taxRateBasisPoints = 1000,
                available = true,
                active = true
            )
        )

        val initialProduct = db.products.findById(prodId)
        assertNotNull(initialProduct)
        assertTrue(initialProduct.active)
        assertTrue(initialProduct.available)

        var sellable = db.products.observeSellable().first()
        assertEquals(1, sellable.size)
        assertEquals("Espresso", sellable.first().name)

        // 2. Toggle to Deactivated (active = false, available = false)
        val deactivated = initialProduct.copy(active = false, available = false)
        db.products.save(deactivated)

        val fetchedDeactivated = db.products.findById(prodId)
        assertNotNull(fetchedDeactivated)
        assertFalse(fetchedDeactivated.active)
        assertFalse(fetchedDeactivated.available)

        sellable = db.products.observeSellable().first()
        assertEquals(0, sellable.size)

        val allProducts = db.products.observeAll().first()
        assertEquals(1, allProducts.size)
        assertFalse(allProducts.first().active)

        // 3. Toggle back to Reactivated (active = true, available = true)
        val reactivated = fetchedDeactivated.copy(active = true, available = true)
        db.products.save(reactivated)

        val fetchedReactivated = db.products.findById(prodId)
        assertNotNull(fetchedReactivated)
        assertTrue(fetchedReactivated.active)
        assertTrue(fetchedReactivated.available)

        sellable = db.products.observeSellable().first()
        assertEquals(1, sellable.size)
        assertEquals("Espresso", sellable.first().name)

        // 4. Verify persistence across database close and reopen
        db.close()
        db = WindowsPosDatabase.open(dbPath)

        val reloadedProduct = db.products.findById(prodId)
        assertNotNull(reloadedProduct)
        assertTrue(reloadedProduct.active)
        assertTrue(reloadedProduct.available)

        sellable = db.products.observeSellable().first()
        assertEquals(1, sellable.size)

        db.close()
    }

    @Test
    fun testCategoryActivationDeactivationCycle() = runBlocking {
        val tempDir = Files.createTempDirectory("pos-cat-active-test")
        val dbPath = tempDir.resolve("pos.db")

        var db = WindowsPosDatabase.open(dbPath)
        val catId = db.categories.save(Category(id = 0L, name = "Snacks", displayOrder = 1, active = true))

        var cat = db.categories.findById(catId)
        assertNotNull(cat)
        assertTrue(cat.active)

        // Deactivate category
        db.categories.save(cat.copy(active = false))
        cat = db.categories.findById(catId)
        assertNotNull(cat)
        assertFalse(cat.active)

        // Reactivate category
        db.categories.save(cat.copy(active = true))
        cat = db.categories.findById(catId)
        assertNotNull(cat)
        assertTrue(cat.active)

        // Verify across reopen
        db.close()
        db = WindowsPosDatabase.open(dbPath)
        cat = db.categories.findById(catId)
        assertNotNull(cat)
        assertTrue(cat.active)

        db.close()
    }

    @Test
    fun testCashierActivationDeactivationAndAuthentication() = runBlocking {
        val tempDir = Files.createTempDirectory("pos-cashier-active-test")
        val dbPath = tempDir.resolve("pos.db")

        var db = WindowsPosDatabase.open(dbPath)
        db.configureInitialSetup("POS Retail", "Manager", "9999")
        val cashierId = db.createCashier("Ahmed", "1234")

        // Active cashier can authenticate
        val authUser = db.authentication.authenticate("1234")
        assertNotNull(authUser)
        assertEquals("Ahmed", authUser.name)
        assertTrue(authUser.active)

        // Deactivate cashier
        db.updateCashier(cashierId, active = false)
        val allUsers = db.allUsers()
        val deactivatedUser = allUsers.first { it.id == cashierId }
        assertFalse(deactivatedUser.active)

        // Inactive cashier CANNOT authenticate
        val authDeactivated = db.authentication.authenticate("1234")
        assertNull(authDeactivated)

        // Reactivate cashier
        db.updateCashier(cashierId, active = true)
        val reactivatedUser = db.allUsers().first { it.id == cashierId }
        assertTrue(reactivatedUser.active)

        // Reactivated cashier can authenticate again
        val authReactivated = db.authentication.authenticate("1234")
        assertNotNull(authReactivated)
        assertEquals("Ahmed", authReactivated.name)

        // Verify across reopen
        db.close()
        db = WindowsPosDatabase.open(dbPath)
        val reloadedAuth = db.authentication.authenticate("1234")
        assertNotNull(reloadedAuth)

        db.close()
    }

    @Test
    fun testTableAndAreaActivationDeactivation() = runBlocking {
        val tempDir = Files.createTempDirectory("pos-table-active-test")
        val dbPath = tempDir.resolve("pos.db")

        val db = WindowsPosDatabase.open(dbPath)
        val areaId = db.saveArea(DiningArea(id = 0L, name = "Terrasse", active = true, displayOrder = 1))
        val tableId = db.createTable(areaId, "T1")

        var activeTables = db.tables.observeActive().first()
        assertEquals(1, activeTables.size)
        assertEquals("T1", activeTables.first().name)

        // Deactivate table
        val table = db.tables.findById(tableId)
        assertNotNull(table)
        db.tables.save(table.copy(active = false))

        activeTables = db.tables.observeActive().first()
        assertEquals(0, activeTables.size)

        val allTables = db.tables.observeAll().first()
        assertEquals(1, allTables.size)
        assertFalse(allTables.first().active)

        // Reactivate table
        db.tables.save(table.copy(active = true))
        activeTables = db.tables.observeActive().first()
        assertEquals(1, activeTables.size)
        assertTrue(activeTables.first().active)

        db.close()
    }
}
