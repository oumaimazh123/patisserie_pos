package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class SoftDeleteAndEntityActionsTest {

    @Test
    fun testProductsSoftDeleteAndRestore() {
        runBlocking {
            val path = Files.createTempDirectory("pos-soft-del-prod").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")
                val catId = db.categories.save(Category(0L, "Boissons Chaudes", true, 1))

                val prod = Product(
                    id = 0L,
                    categoryId = catId,
                    name = "Café Crème",
                    priceCentimes = 2500,
                    taxRateBasisPoints = 1000,
                    available = true,
                    active = true,
                    sku = "CC-01",
                    barcode = "1234567890123"
                )
                val prodId = db.products.save(prod)

                assertTrue(db.products.observeAll().first().any { it.id == prodId })
                assertEquals(0, db.deletedProducts().size)

                // Soft delete product
                db.softDeleteProduct(prodId)

                // Verify active list does not contain it
                assertFalse(db.products.observeAll().first().any { it.id == prodId })
                assertEquals(1, db.deletedProducts().size)
                assertEquals("Café Crème", db.deletedProducts().first().name)

                // Verify raw SQL has the row with deleted_at set (NEVER physically deleted)
                DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                    conn.prepareStatement("SELECT deleted_at, active, available FROM products WHERE id=?").use { stmt ->
                        stmt.setLong(1, prodId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertNotNull(rs.getLong("deleted_at"))
                            assertTrue(rs.getLong("deleted_at") > 0L)
                            assertEquals(0, rs.getInt("active"))
                            assertEquals(0, rs.getInt("available"))
                        }
                    }
                }

                // Restore product
                db.restoreProduct(prodId)
                assertTrue(db.products.observeAll().first().any { it.id == prodId })
                assertEquals(0, db.deletedProducts().size)

                DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                    conn.prepareStatement("SELECT deleted_at, active, available FROM products WHERE id=?").use { stmt ->
                        stmt.setLong(1, prodId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertEquals(0L, rs.getLong("deleted_at"))
                            assertTrue(rs.wasNull())
                            assertEquals(1, rs.getInt("active"))
                            assertEquals(1, rs.getInt("available"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testCategoriesSoftDeleteAndRestore() {
        runBlocking {
            val path = Files.createTempDirectory("pos-soft-del-cat").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")
                val catId = db.categories.save(Category(0L, "Desserts", true, 1))

                assertTrue(db.categories.observeAll().first().any { it.id == catId })
                assertEquals(0, db.deletedCategories().size)

                // Soft delete category
                db.softDeleteCategory(catId)

                assertFalse(db.categories.observeAll().first().any { it.id == catId })
                assertEquals(1, db.deletedCategories().size)
                assertEquals("Desserts", db.deletedCategories().first().name)

                // Verify raw SQL persistence
                DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                    conn.prepareStatement("SELECT deleted_at, active FROM categories WHERE id=?").use { stmt ->
                        stmt.setLong(1, catId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertTrue(rs.getLong("deleted_at") > 0L)
                            assertEquals(0, rs.getInt("active"))
                        }
                    }
                }

                // Restore
                db.restoreCategory(catId)
                assertTrue(db.categories.observeAll().first().any { it.id == catId })
                assertEquals(0, db.deletedCategories().size)
            }
        }
    }

    @Test
    fun testDiningAreasAndTablesSoftDeleteAndRestoreAndEdit() {
        runBlocking {
            val path = Files.createTempDirectory("pos-soft-del-tables").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")
                val areaId = db.saveArea(DiningArea(0L, "Terrasse", true, 1))
                val tableId = db.createTable(areaId, "Table 1")

                assertTrue(db.areas().any { it.id == areaId })
                assertTrue(db.allTables().any { it.id == tableId })

                // Edit table name and area
                val area2Id = db.saveArea(DiningArea(0L, "Mezzanine", true, 2))
                db.updateTable(tableId, "Table 101", area2Id)
                val updatedTable = db.allTables().first { it.id == tableId }
                assertEquals("Table 101", updatedTable.name)
                assertEquals(area2Id, updatedTable.areaId)

                // Soft delete table
                db.softDeleteTable(tableId)
                assertFalse(db.allTables().any { it.id == tableId })
                assertEquals(1, db.deletedTables().size)

                // Soft delete area
                db.softDeleteArea(areaId)
                assertFalse(db.areas().any { it.id == areaId })
                assertEquals(1, db.deletedAreas().size)

                // Verify raw SQL persistence
                DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                    conn.prepareStatement("SELECT deleted_at FROM restaurant_tables WHERE id=?").use { stmt ->
                        stmt.setLong(1, tableId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertTrue(rs.getLong("deleted_at") > 0L)
                        }
                    }
                    conn.prepareStatement("SELECT deleted_at FROM dining_areas WHERE id=?").use { stmt ->
                        stmt.setLong(1, areaId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertTrue(rs.getLong("deleted_at") > 0L)
                        }
                    }
                }

                // Restore table and area
                db.restoreTable(tableId)
                db.restoreArea(areaId)
                assertTrue(db.allTables().any { it.id == tableId })
                assertTrue(db.areas().any { it.id == areaId })
            }
        }
    }

    @Test
    fun testCashierSoftDeleteRestoreAndOwnerProtection() {
        runBlocking {
            val path = Files.createTempDirectory("pos-soft-del-cashier").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")

                // Owner ID 1 cannot be soft deleted
                assertFails { db.softDeleteCashier(1L) }

                val cashierId = db.createCashier("Karim", "5555")
                assertNotNull(db.authentication.authenticate("5555"))
                assertTrue(db.allUsers().any { it.id == cashierId })

                // Soft delete cashier
                db.softDeleteCashier(cashierId)
                assertFalse(db.allUsers().any { it.id == cashierId })
                assertEquals(1, db.deletedUsers().size)

                // Authentication with deleted cashier PIN is rejected
                assertNull(db.authentication.authenticate("5555"))

                // Verify raw SQL persistence
                DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                    conn.prepareStatement("SELECT deleted_at, active FROM users WHERE id=?").use { stmt ->
                        stmt.setLong(1, cashierId)
                        stmt.executeQuery().use { rs ->
                            assertTrue(rs.next())
                            assertTrue(rs.getLong("deleted_at") > 0L)
                            assertEquals(0, rs.getInt("active"))
                        }
                    }
                }

                // Restore cashier
                db.restoreCashier(cashierId)
                assertTrue(db.allUsers().any { it.id == cashierId })
                assertNotNull(db.authentication.authenticate("5555"))
            }
        }
    }

    @Test
    fun testHistoricalDataIntegrityAfterSoftDeleteOfAllEntities() {
        runBlocking {
            val path = Files.createTempDirectory("pos-history-integrity").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")

                val areaId = db.saveArea(DiningArea(0L, "Salle", true, 1))
                val tableId = db.createTable(areaId, "Table 5")
                val catId = db.categories.save(Category(0L, "Boissons", true, 1))
                val prodId = db.products.save(
                    Product(0L, catId, "Thé à la menthe", 1500, 1000, true, true)
                )
                val cashierId = db.createCashier("Samir", "9999")

                // Open Session
                val session = RegisterSession(
                    id = 1L,
                    status = RegisterSessionStatus.OPEN,
                    openingCashCentimes = 50000,
                    registerId = 1L,
                    cashierId = cashierId,
                    openedAtEpochMilliseconds = System.currentTimeMillis(),
                    closedAtEpochMilliseconds = null,
                    expectedCashCentimes = null,
                    countedCashCentimes = null,
                    differenceCentimes = null
                )
                db.sessions.save(session)

                // Create and pay order
                val order = Order(
                    id = 0L,
                    number = "ORD-1001",
                    type = OrderType.DINE_IN,
                    tableId = tableId,
                    cashierId = cashierId,
                    registerSessionId = 1L,
                    status = OrderStatus.OPEN,
                    subtotalCentimes = 1500,
                    discountCentimes = 0,
                    taxCentimes = 150,
                    totalCentimes = 1500,
                    lines = listOf(
                        OrderLine(
                            productId = prodId,
                            name = "Thé à la menthe",
                            unitPriceCentimes = 1500,
                            taxRateBasisPoints = 1000,
                            quantity = 1,
                            categoryIdSnapshot = catId,
                            categoryNameSnapshot = "Boissons"
                        )
                    )
                )
                val orderId = db.orders.save(order)

                // Complete payment
                db.payments.save(
                    Payment(
                        id = 0L,
                        orderId = orderId,
                        registerSessionId = 1L,
                        method = PaymentMethod.CASH,
                        amountCentimes = 1500,
                        receivedCentimes = 2000,
                        changeCentimes = 500,
                        status = PaymentStatus.COMPLETED,
                        submissionToken = "tok-1",
                        createdAtEpochMilliseconds = System.currentTimeMillis()
                    )
                )
                db.orders.save(order.copy(id = orderId, status = OrderStatus.COMPLETED))

                // Now SOFT DELETE EVERYTHING: product, category, table, area, cashier
                db.softDeleteProduct(prodId)
                db.softDeleteCategory(catId)
                db.softDeleteTable(tableId)
                db.softDeleteArea(areaId)
                db.softDeleteCashier(cashierId)

                // Verify historical orders load without errors
                val loadedOrder = db.orders.findById(orderId)
                assertNotNull(loadedOrder)
                assertEquals("ORD-1001", loadedOrder.number)
                assertEquals(tableId, loadedOrder.tableId)
                assertEquals(cashierId, loadedOrder.cashierId)
                assertEquals(1, loadedOrder.lines.size)
                assertEquals("Thé à la menthe", loadedOrder.lines[0].name)
                assertEquals("Boissons", loadedOrder.lines[0].categoryNameSnapshot)

                // Verify sales summary and history rows
                val salesRows = db.salesHistory()
                assertEquals(1, salesRows.size)
                assertEquals("ORD-1001", salesRows[0].order.number)

                val summary = db.salesSummary()
                assertEquals(1500L, summary.salesCentimes)
                assertEquals(1, summary.completedOrders)
            }
        }
    }

    @Test
    fun testUniqueConstraintsAllowReuseOfSoftDeletedNamesAndPins() {
        runBlocking {
            val path = Files.createTempDirectory("pos-uniqueness-softdel").resolve("pos.db")
            WindowsPosDatabase.open(path).use { db ->
                db.configureInitialSetup("Café Test", "Admin", "1234")
                val catId = db.categories.save(Category(0L, "Sandwiches", true, 1))

                // 1. Products
                val prod1Id = db.products.save(
                    Product(0L, catId, "Panini Poulet", 3000, 1000, true, true, sku = "PAN-P", barcode = "111222333")
                )
                db.softDeleteProduct(prod1Id)

                // Creating a new product with same name/sku/barcode succeeds because old one is deleted
                val prod2Id = db.products.save(
                    Product(0L, catId, "Panini Poulet", 3500, 1000, true, true, sku = "PAN-P", barcode = "111222333")
                )
                assertTrue(prod2Id > 0L)

                // 2. Categories
                val cat2Id = db.categories.save(Category(0L, "Jus Frais", true, 2))
                db.softDeleteCategory(cat2Id)
                val cat3Id = db.categories.save(Category(0L, "Jus Frais", true, 3))
                assertTrue(cat3Id > 0L)

                // 3. Cashiers
                val cashier1 = db.createCashier("Yassine", "7777")
                db.softDeleteCashier(cashier1)
                val cashier2 = db.createCashier("Yassine Nouveau", "7777")
                assertTrue(cashier2 > 0L)
            }
        }
    }
}
