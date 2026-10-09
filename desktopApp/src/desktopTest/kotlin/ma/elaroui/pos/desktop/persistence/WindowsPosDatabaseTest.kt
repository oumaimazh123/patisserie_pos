package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import kotlin.test.*

class WindowsPosDatabaseTest {
    @Test fun untouchedLegacyBootstrapIsRemovedButFreshSchemaIsPreserved() = runBlocking {
        val path = Files.createTempDirectory("pos-legacy-bootstrap").resolve("pos.db")
        WindowsPosDatabase.open(path).close()
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.prepareStatement("INSERT INTO users(id,name,role,pin_hash,active,created_at) VALUES(1,'Owner','OWNER',?,1,1)").use {
                it.setString(1, WindowsPosDatabase.hashCredential("1234"))
                it.executeUpdate()
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO registers(id,name,active) VALUES(1,'Main Register',1)")
                listOf("General", "Food & Grocery", "Household", "Personal Care").forEachIndexed { index, name ->
                    connection.prepareStatement("INSERT INTO categories(id,name,display_order,active) VALUES(?,?,?,1)").use {
                        it.setLong(1, index + 1L)
                        it.setString(2, name)
                        it.setInt(3, index)
                        it.executeUpdate()
                    }
                }
                statement.executeUpdate("INSERT INTO settings(key,value) VALUES('establishment_name','General POS')")
            }
        }

        WindowsPosDatabase.open(path).use { db ->
            assertTrue(db.allUsers().isEmpty())
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertNull(db.settings.get("establishment_name"))
            assertEquals(11, db.schemaVersion())
        }
    }

    @Test fun loginAcceptsFourFiveAndSixDigitPinsAndRejectsInvalidPins() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val five = db.createCashier("Five digits", "54321")
            val six = db.createCashier("Six digits", "654321")
            assertEquals(1L, db.authentication.authenticate("1234")?.id)
            assertEquals(five, db.authentication.authenticate("54321")?.id)
            assertEquals(six, db.authentication.authenticate("654321")?.id)
            assertNull(db.authentication.authenticate("123"))
            assertNull(db.authentication.authenticate("1234567"))
            assertNull(db.authentication.authenticate("12a4"))
            assertFails { db.createCashier("Too short", "123") }
            assertFails { db.createCashier("Too long", "1234567") }
            assertFails { db.createCashier("Letters", "12a4") }
        }
        Unit
    }

    @Test fun duplicatePinCreationAndResetReturnFriendlyValidationForActiveAndInactiveUsers() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val inactiveId = db.createCashier("Inactive", "4567")
            db.updateCashier(inactiveId, active = false)

            val duplicateInactive = assertFailsWith<DesktopValidationException> {
                db.createCashier("Duplicate inactive", "4567")
            }
            assertEquals("Ce code PIN est déjà utilisé par un autre utilisateur.", duplicateInactive.message)
            assertFalse(duplicateInactive.message.orEmpty().contains("SQLITE", ignoreCase = true))

            val otherId = db.createCashier("Other", "56789")
            val duplicateOwner = assertFailsWith<DesktopValidationException> {
                db.updateCashier(otherId, pin = "1234")
            }
            assertEquals("Ce code PIN est déjà utilisé par un autre utilisateur.", duplicateOwner.message)
            assertNotNull(db.authentication.authenticate("56789"), "Failed reset must leave the existing PIN unchanged")
        }
        Unit
    }

    @Test fun newDatabaseMigratesWithoutMockDataAndPreservesSettings() = runBlocking {
        val path = Files.createTempDirectory("pos-windows-db").resolve("pos.db")
        WindowsPosDatabase.open(path).use { db ->
            assertNull(db.authentication.authenticate("1234"))
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.tables.observeActive().first().isEmpty())
            db.settings.put(AppSetting("test", "preserved"))
        }
        WindowsPosDatabase.open(path).use { db ->
            assertEquals("preserved", db.settings.get("test"))
            assertTrue(db.categories.observeAll().first().isEmpty())
        }
    }

    @Test fun inactiveManagementRecordsRemainVisibleAndCannotAuthenticate() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val cashierId = db.createCashier("Test Cashier", "4567")
            db.updateCashier(cashierId, active = false)
            assertTrue(db.allUsers().any { it.id == cashierId && !it.active })
            assertNull(db.authentication.authenticate("4567"))
            val table = db.allTables().first()
            db.tables.save(table.copy(active = false))
            assertTrue(db.allTables().any { it.id == table.id && !it.active })
            assertFalse(db.tables.observeActive().first().any { it.id == table.id })
        }
    }

    @Test fun tableOccupancyTracksOrderMoveCancelAndPayment() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 2_000)
            val created = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "ORD-TABLE", OrderType.DINE_IN, 1, listOf(1L to 1), tableId = 1, cashierId = 1)
            assertIs<UseCaseResult.Success<Order>>(created)
            assertEquals(TableStatus.OCCUPIED, db.allTables().first { it.id == 1L }.status)
            db.moveOrder(1, 2)
            assertEquals(TableStatus.AVAILABLE, db.allTables().first { it.id == 1L }.status)
            assertEquals(TableStatus.OCCUPIED, db.allTables().first { it.id == 2L }.status)
            db.cancelOrder(1, "Erreur de saisie", 1, null)
            assertEquals(TableStatus.AVAILABLE, db.allTables().first { it.id == 2L }.status)
            assertEquals(OrderStatus.CANCELLED, db.orders.findById(1)?.status)
        }
    }

    @Test fun dineInMayBeHeldWithoutTableAndRestoredFromSqlite() = runBlocking {
        val path = Files.createTempDirectory("pos-held-order").resolve("pos.db")
        WindowsPosDatabase.open(path).use { db ->
            db.configureInitialSetup("PATISSERIE_POS test", "Owner", "1234")
            db.categories.save(Category(id = 0L, name = "General"))
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 0)
            db.products.save(Product(id = 0L, categoryId = 1L, name = "Held legacy item", priceCentimes = 1_000L, taxRateBasisPoints = 0))
            val result = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "HELD-1", OrderType.DINE_IN, 1, listOf(1L to 3), tableId = null, cashierId = 1)
            val order = assertIs<UseCaseResult.Success<Order>>(result).value
            assertNull(order.tableId)
            assertEquals(3, order.lines.single().quantity)
        }
        WindowsPosDatabase.open(path).use { db ->
            val restored = db.orders.findById(1)
            assertNotNull(restored)
            assertEquals(OrderStatus.OPEN, restored.status)
            assertNull(restored.tableId)
            assertEquals(3, restored.lines.single().quantity)
        }
    }

    @Test fun createOrderRejectsOccupiedTableAndInactiveOrUnavailableCatalogueRecords() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 0)
            db.tables.save(db.allTables().first { it.id == 1L }.copy(status = TableStatus.OCCUPIED))
            assertIs<UseCaseResult.Failure>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                    .execute(1, "BAD-TABLE", OrderType.DINE_IN, 1, listOf(1L to 1), 1, 1)
            )

            val product = db.products.findById(1)!!
            db.products.save(product.copy(available = false))
            assertIs<UseCaseResult.Failure>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                    .execute(2, "BAD-PRODUCT", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            )

            db.products.save(product.copy(available = true))
            val category = db.categories.findById(product.categoryId!!)!!
            db.categories.save(category.copy(active = false))
            assertIs<UseCaseResult.Failure>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                    .execute(3, "BAD-CATEGORY", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            )
        }
        Unit
    }

    @Test fun foreignKeysAndUniqueConstraintsAreEnforced() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            assertFails { db.products.save(Product(99, 999, "Invalid", 1000, 0)) }
            assertFails { db.categories.save(Category(99, "Café")) }
        }
        Unit
    }

    @Test fun transactionRollsBackAllWritesOnFailure() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            assertFails {
                db.transactions.inTransaction {
                    db.settings.put(AppSetting("rollback_probe", "written"))
                    error("force rollback")
                }
            }
            assertNull(db.settings.get("rollback_probe"))
        }
    }

    @Test fun sharedRegisterOrderAndPaymentWorkflowRunsAgainstSqlite() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val session = OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 2_000)
            assertIs<UseCaseResult.Success<RegisterSession>>(session)
            val order = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "ORD-TEST-1", OrderType.COUNTER, 1, listOf(1L to 2), tableId = null, cashierId = 1)
            assertIs<UseCaseResult.Success<Order>>(order)
            val paid = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(1, PaymentMethod.CASH, 3_000, "test-token")
            assertIs<UseCaseResult.Success<Payment>>(paid)
            assertEquals(OrderStatus.COMPLETED, db.orders.findById(1)?.status)
            val categorySales = db.recentCategorySaleSamples(0L)
            assertEquals(order.value.lines.single().categoryIdSnapshot, categorySales.single().categoryId)
            assertEquals(2, categorySales.single().quantity)
            assertEquals(paid.value, (CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(1, PaymentMethod.CASH, 3_000, "test-token") as UseCaseResult.Success).value)
        }
    }

    @Test fun setupUsersBackupAndRestoreSurviveRestart() = runBlocking {
        val directory = Files.createTempDirectory("pos-backup-restore")
        val live = directory.resolve("live.db")
        val backup = directory.resolve("backup.db")
        val areaImage = directory.resolve("images/areas/area_test.png")
        Files.createDirectories(areaImage.parent)
        Files.write(areaImage, byteArrayOf(1, 2, 3, 4))
        WindowsPosDatabase.open(live).use { db ->
            db.configureInitialSetup("Atlas Café", "Owner Atlas", "9876")
            db.createCashier("Cashier One", "4567")
            db.saveArea(DiningArea(0, "Legacy compatibility area"))
            db.saveArea(db.areas().first().copy(imagePath = areaImage.toString()))
            assertEquals("Owner Atlas", db.authentication.authenticate("9876")?.name)
            assertEquals(UserRole.CASHIER, db.authentication.authenticate("4567")?.role)
            db.backupTo(backup)
            Files.delete(areaImage)
            db.settings.put(AppSetting("establishment_name", "Changed after backup"))
            db.stageRestore(backup)
        }
        WindowsPosDatabase.open(live).use { restored ->
            assertEquals("Atlas Café", restored.settings.get("establishment_name"))
            assertEquals("Cashier One", restored.authentication.authenticate("4567")?.name)
            assertEquals(areaImage.toString(), restored.areas().first().imagePath)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(areaImage))
        }
    }

    @Test fun diningAreaImagePathPersistsReplacesRemovesAndSurvivesRestart() {
        val path = Files.createTempDirectory("pos-area-image").resolve("pos.db")
        WindowsPosDatabase.open(path).use { db ->
            db.saveArea(DiningArea(0, "Legacy compatibility area"))
            val area = db.areas().first()
            db.saveArea(area.copy(imagePath = "managed/first.webp"))
            assertEquals("managed/first.webp", db.areas().first().imagePath)
            db.saveArea(db.areas().first().copy(imagePath = "managed/replacement.png"))
            assertEquals("managed/replacement.png", db.areas().first().imagePath)
            db.saveArea(db.areas().first().copy(imagePath = null))
            assertNull(db.areas().first().imagePath)
            db.saveArea(db.areas().first().copy(imagePath = "managed/final.jpg"))
        }
        WindowsPosDatabase.open(path).use { db ->
            assertEquals("managed/final.jpg", db.areas().first().imagePath)
        }
    }

    @Test fun companySettingsAndDataSurviveApplicationUpgradeReopen() = runBlocking {
        val path=Files.createTempDirectory("pos-upgrade").resolve("pos.db")
        WindowsPosDatabase.open(path).use{db->
            mapOf("establishment_name" to "Café Atlas","restaurant_address" to "Rabat","seller_ice" to "123456789012345","wifi_code" to "guest-code").forEach{(key,value)->db.settings.put(AppSetting(key,value))}
            db.createCashier("Upgrade Cashier","4567")
        }
        WindowsPosDatabase.open(path).use{db->
            assertEquals("Café Atlas",db.settings.get("establishment_name"));assertEquals("123456789012345",db.settings.get("seller_ice"));assertEquals("guest-code",db.settings.get("wifi_code"));assertNotNull(db.authentication.authenticate("4567"))
        }
        Unit
    }

    @Test fun salesHistoryFiltersByStatusPaymentAndSearch() = runBlocking {
        WindowsPosDatabase.openInMemory().use{db->
            OpenRegisterSession(db.sessions,TestClock).execute(1,1,1,0)
            CreateOrder(db.sessions,db.categories,db.products,db.tables,db.orders).execute(1,"FILTER-ORDER",OrderType.COUNTER,1,listOf(1L to 1),tableId=null,cashierId=1)
            CompletePayment(db.sessions,db.orders,db.payments,db.transactions).execute(1,PaymentMethod.CARD,null,"filter-token")
            assertEquals(1,db.salesHistory(query="FILTER",status=OrderStatus.COMPLETED,method=PaymentMethod.CARD).size)
            assertTrue(db.salesHistory(method=PaymentMethod.CASH).isEmpty())
            assertTrue(db.salesHistory(query="missing").isEmpty())
        }
    }

    @Test fun salesSummaryUsesDateRangeAndStoredTax() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val rangeStart = System.currentTimeMillis() - 1_000
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 0)
            val product = db.products.findById(1)!!.copy(priceCentimes = 1_000, taxRateBasisPoints = 2_000)
            db.products.save(product)
            CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "TAXED", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(1, PaymentMethod.CASH, 1_200, "taxed-payment")

            val rangeEnd = System.currentTimeMillis() + 1_000
            val included = db.salesSummary(rangeStart, rangeEnd)
            assertEquals(166, included.taxCentimes)
            assertEquals(1_000, included.salesCentimes)
            assertEquals(1, included.completedOrders)
            assertEquals(0, db.salesSummary(0, rangeStart - 1).completedOrders)
        }
    }

    @Test fun categoryWithSingleSubcategoryLifecycle() {
        runBlocking {
            WindowsPosDatabase.openInMemory().use { db ->
                val root = Category(id = 0L, name = "Boissons Chaudes", active = true, imagePath = "images/categories/boissons.png")
                val sub = Category(id = 0L, name = "Cafés Expresso", active = true, imagePath = "images/categories/cafes.png")

                val (rootId, subId) = db.saveCategoryWithSubcategory(root, sub)
                assertTrue(rootId > 0)
                assertTrue(subId > 0)

                val loadedRoot = db.categories.findById(rootId)
                val loadedSub = db.categories.findById(subId)
                assertNotNull(loadedRoot)
                assertNotNull(loadedSub)

                assertEquals("Boissons Chaudes", loadedRoot.name)
                assertNull(loadedRoot.parentId)
                assertEquals("images/categories/boissons.png", loadedRoot.imagePath)
                assertTrue(loadedRoot.isRoot)
                assertFalse(loadedRoot.isSubcategory)

                assertEquals("Cafés Expresso", loadedSub.name)
                assertEquals(rootId, loadedSub.parentId)
                assertEquals("images/categories/cafes.png", loadedSub.imagePath)
                assertFalse(loadedSub.isRoot)
                assertTrue(loadedSub.isSubcategory)

                // Edit both simultaneously
                val updatedRoot = loadedRoot.copy(name = "Boissons Chaudes Bio")
                val updatedSub = loadedSub.copy(name = "Cafés Spécialité")
                val (editedRootId, editedSubId) = db.saveCategoryWithSubcategory(updatedRoot, updatedSub)
                assertEquals(rootId, editedRootId)
                assertEquals(subId, editedSubId)

                assertEquals("Boissons Chaudes Bio", db.categories.findById(rootId)!!.name)
                assertEquals("Cafés Spécialité", db.categories.findById(subId)!!.name)

                // Soft-deleting category also soft-deletes its subcategory
                db.softDeleteCategory(rootId)
                assertNull(db.categories.findByName("Boissons Chaudes Bio"))
                assertNull(db.categories.findByName("Cafés Spécialité"))
                assertTrue(db.deletedCategories().any { it.id == rootId })
                assertTrue(db.deletedCategories().any { it.id == subId })

                // Restoring restores both
                db.restoreCategory(rootId)
                assertNotNull(db.categories.findByName("Boissons Chaudes Bio"))
                assertNotNull(db.categories.findByName("Cafés Spécialité"))
                assertFalse(db.deletedCategories().any { it.id == rootId })
                assertFalse(db.deletedCategories().any { it.id == subId })
            }
        }
    }

    @Test fun threeLevelHierarchyLifecycleAndValidation() {
        runBlocking {
            WindowsPosDatabase.openInMemory().use { db ->
                // 1. Create Level 1
                val root = Category(id = 0L, name = "Boulangerie Artisanale", active = true, imagePath = "images/boulangerie.png")
                val rootId = db.categories.save(root)
                assertTrue(rootId > 0)

                // 2. Create Level 2
                val sub1 = Category(id = 0L, name = "Pains Spéciaux", parentId = rootId, active = true, imagePath = "images/pains.png")
                val sub1Id = db.categories.save(sub1)
                assertTrue(sub1Id > 0)

                // 3. Create another Level 2 (One-to-Many verified!)
                val sub2 = Category(id = 0L, name = "Baguettes", parentId = rootId, active = true, imagePath = "images/baguettes.png")
                val sub2Id = db.categories.save(sub2)
                assertTrue(sub2Id > 0)

                // 4. Create Level 3
                val subSub = Category(id = 0L, name = "Pains de Seigle", parentId = sub1Id, active = true, imagePath = "images/seigle.png")
                val subSubId = db.categories.save(subSub)
                assertTrue(subSubId > 0)

                // 5. Level 4 is allowed and saved
                val level4 = Category(id = 0L, name = "Petits Seigles", parentId = subSubId, active = true, imagePath = "images/petits.png")
                val level4Id = db.categories.save(level4)
                assertTrue(level4Id > 0)

                // 6. Attempt Level 5 (must be strictly blocked!)
                val level5 = Category(id = 0L, name = "Micro Seigles", parentId = level4Id, active = true, imagePath = "images/micro.png")
                val ex = kotlin.test.assertFailsWith<DesktopValidationException> {
                    db.categories.save(level5)
                }
                assertEquals("Cette catégorie est déjà au niveau 4. Une catégorie de niveau 4 ne peut pas avoir de sous-catégorie.", ex.message)

                // 7. Category without image validation and persistence
                val noImage = Category(id = 0L, name = "Sans Image", active = true, imagePath = null)
                val imgValidation = ma.elaroui.pos.shared.rules.CategoryHierarchyRules.validateCategoryHierarchy(noImage, emptyList())
                assertTrue(imgValidation.isSuccess)
                val noImageId = db.categories.save(noImage)
                assertTrue(noImageId > 0)
                val savedNoImage = db.categories.findById(noImageId)
                assertNotNull(savedNoImage)
                assertNull(savedNoImage.imagePath)

                // 8. Cycle prevention
                val cycleCat = Category(id = rootId, name = "Boulangerie Artisanale", parentId = rootId, active = true, imagePath = "images/boulangerie.png")
                val cycleEx = kotlin.test.assertFailsWith<DesktopValidationException> {
                    db.categories.save(cycleCat)
                }
                assertEquals(CategoryHierarchyRules.ERROR_CYCLE_DETECTED, cycleEx.message)

                // 9. Cascade soft delete across 4 levels
                db.softDeleteCategory(rootId)
                assertNull(db.categories.findByName("Boulangerie Artisanale"))
                assertNull(db.categories.findByName("Pains Spéciaux"))
                assertNull(db.categories.findByName("Baguettes"))
                assertNull(db.categories.findByName("Pains de Seigle"))
                assertNull(db.categories.findByName("Petits Seigles"))
                val deletedIds = db.deletedCategories().map { it.id }.toSet()
                assertTrue(rootId in deletedIds)
                assertTrue(sub1Id in deletedIds)
                assertTrue(sub2Id in deletedIds)
                assertTrue(subSubId in deletedIds)
                assertTrue(level4Id in deletedIds)

                // 10. Cascade restore across 4 levels
                db.restoreCategory(rootId)
                assertNotNull(db.categories.findByName("Boulangerie Artisanale"))
                assertNotNull(db.categories.findByName("Pains Spéciaux"))
                assertNotNull(db.categories.findByName("Baguettes"))
                assertNotNull(db.categories.findByName("Pains de Seigle"))
                assertNotNull(db.categories.findByName("Petits Seigles"))
            }
        }
    }

    @Test fun uncategorizedProductPersistenceAndOrderValidation() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val p = Product(id = 0L, categoryId = null, name = "Sac Kraft Emballage", priceCentimes = 50L, taxRateBasisPoints = 0)
            val id = db.products.save(p)
            val loaded = db.products.findById(id)
            assertNotNull(loaded)
            assertEquals("Sac Kraft Emballage", loaded.name)
            assertNull(loaded.categoryId)

            // Duplicate name without category should be rejected
            val dup = Product(id = 0L, categoryId = null, name = "Sac Kraft Emballage", priceCentimes = 100L, taxRateBasisPoints = 0)
            assertFailsWith<DesktopValidationException> {
                db.products.save(dup)
            }

            // Ordering uncategorized product succeeds
            OpenRegisterSession(db.sessions, TestClock).execute(1, 1, 1, 100_00L)
            val orderResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "ORD-UNCAT-1", OrderType.COUNTER, 1, listOf(id to 2), null, 1)
            assertTrue(orderResult is UseCaseResult.Success)
            val order = (orderResult as UseCaseResult.Success).value
            assertEquals(1, order.lines.size)
            assertNull(order.lines.first().categoryIdSnapshot)
        }
    }

    @Test fun categoryDeletionNeverDeletesProductsAndMakesThemUncategorized() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // 1. Create category and products
            val catId = db.categories.save(Category(0L, "Pâtisseries Orientales", true, 1))
            val p1Id = db.products.save(Product(0L, catId, "Cornes de Gazelle", 120_00L, 2000))
            val p2Id = db.products.save(Product(0L, catId, "Briouates aux Amandes", 110_00L, 2000))

            // Verify initial state
            assertEquals(catId, db.products.findById(p1Id)?.categoryId)
            assertEquals(catId, db.products.findById(p2Id)?.categoryId)

            // 2. Modify product to be uncategorized directly
            val p1Modified = db.products.findById(p1Id)!!.copy(categoryId = null, priceCentimes = 125_00L)
            db.products.save(p1Modified)
            val p1Loaded = db.products.findById(p1Id)
            assertNotNull(p1Loaded)
            assertNull(p1Loaded.categoryId, "Product 1 must now be uncategorized")
            assertEquals(125_00L, p1Loaded.priceCentimes)

            // 3. Delete category: verify product 2 is NOT deleted and becomes uncategorized
            db.softDeleteCategory(catId)

            // Category is soft-deleted
            assertNull(db.categories.findByName("Pâtisseries Orientales"))
            assertTrue(db.deletedCategories().any { it.id == catId })

            // Products are STILL present and have categoryId = null
            val p2Loaded = db.products.findById(p2Id)
            assertNotNull(p2Loaded, "Product 2 must never be deleted when its category is deleted")
            assertNull(p2Loaded.categoryId, "Product 2 must become uncategorized when its category is deleted")
            assertTrue(p2Loaded.active, "Product 2 remains active")
            assertTrue(p2Loaded.available, "Product 2 remains available")

            // Both products are listed in observeSellable() and observeAll()
            val sellable = db.products.observeSellable().first()
            assertTrue(sellable.any { it.id == p1Id }, "Product 1 must be sellable")
            assertTrue(sellable.any { it.id == p2Id }, "Product 2 must be sellable")

            // 4. Products can still be modified while uncategorized
            db.products.save(p2Loaded.copy(name = "Briouates Miel & Amandes", priceCentimes = 115_00L))
            val p2Updated = db.products.findById(p2Id)
            assertNotNull(p2Updated)
            assertEquals("Briouates Miel & Amandes", p2Updated.name)
            assertNull(p2Updated.categoryId)
        }
    }

    @Test fun existingProductsArePreservedAcrossMigration11() = runBlocking {
        val path = Files.createTempDirectory("pos-migration11-test").resolve("pos.db")
        var savedCatId: Long = 0L
        var savedProdId: Long = 0L
        WindowsPosDatabase.open(path).use { db ->
            savedCatId = db.categories.save(Category(0L, "Viennoiserie", true, 1))
            savedProdId = db.products.save(Product(0L, savedCatId, "Croissant Beurre", 6_00L, 1000, sku = "CRO-01", barcode = "111222"))
            assertEquals(savedCatId, db.products.findById(savedProdId)?.categoryId)
        }
        // Reconstruct the actual v10 products shape: category_id was mandatory.
        // Merely reopening the current database never executes migration 11.
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { c ->
            c.createStatement().use { s ->
                s.execute("""
                    CREATE TABLE products_v10(
                        id INTEGER PRIMARY KEY, category_id INTEGER NOT NULL REFERENCES categories(id),
                        name TEXT NOT NULL, price_centimes INTEGER NOT NULL CHECK(price_centimes>0),
                        tax_basis_points INTEGER NOT NULL CHECK(tax_basis_points BETWEEN 0 AND 10000),
                        image_path TEXT, available INTEGER NOT NULL DEFAULT 1, active INTEGER NOT NULL DEFAULT 1,
                        display_order INTEGER NOT NULL DEFAULT 0, sku TEXT, barcode TEXT, name_arabic TEXT,
                        unit TEXT, description TEXT, deleted_at INTEGER DEFAULT NULL
                    )
                """.trimIndent())
                s.execute("INSERT INTO products_v10 SELECT id,category_id,name,price_centimes,tax_basis_points,image_path,available,active,display_order,sku,barcode,name_arabic,unit,description,deleted_at FROM products")
                s.execute("DROP TABLE products")
                s.execute("ALTER TABLE products_v10 RENAME TO products")
                s.execute("DELETE FROM schema_migrations WHERE version=11")
            }
        }
        WindowsPosDatabase.open(path).use { db ->
            val prod = db.products.findById(savedProdId)
            assertNotNull(prod)
            assertEquals("Croissant Beurre", prod.name)
            assertEquals(savedCatId, prod.categoryId)
            assertEquals(6_00L, prod.priceCentimes)
            assertEquals("CRO-01", prod.sku)
            assertEquals("111222", prod.barcode)
            assertEquals(11, db.schemaVersion())
            db.products.save(prod.copy(categoryId = null))
            assertNull(db.products.findById(savedProdId)?.categoryId)
            assertFails { db.products.save(prod.copy(id = 0, name = "Duplicate barcode")) }
            db.read { c ->
                c.createStatement().use { s ->
                    s.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
                    s.executeQuery("PRAGMA integrity_check").use { assertTrue(it.next()); assertEquals("ok", it.getString(1)) }
                }
            }
        }
    }

    private object TestClock : ma.elaroui.pos.shared.Clock {
        override fun now() = ma.elaroui.pos.shared.EpochMilliseconds(1_000)
    }
}
