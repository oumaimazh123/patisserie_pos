package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Step5UseCasesTest {

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var tableRepository: FakeTableRepository
    private lateinit var orderRepository: FakeOrderRepository

    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var deactivateCategoryUseCase: DeactivateCategoryUseCase

    private lateinit var getProductsUseCase: GetProductsUseCase
    private lateinit var createProductUseCase: CreateProductUseCase
    private lateinit var updateProductUseCase: UpdateProductUseCase
    private lateinit var setProductAvailabilityUseCase: SetProductAvailabilityUseCase
    private lateinit var observeSellableCatalogueUseCase: ObserveSellableCatalogueUseCase

    private lateinit var createDiningAreaUseCase: CreateDiningAreaUseCase
    private lateinit var createTableUseCase: CreateTableUseCase
    private lateinit var updateTableUseCase: UpdateTableUseCase
    private lateinit var deactivateTableUseCase: DeactivateTableUseCase
    private lateinit var bulkCreateTablesUseCase: BulkCreateTablesUseCase
    private lateinit var observeSelectableTablesUseCase: ObserveSelectableTablesUseCase

    private lateinit var ownerUser: User
    private lateinit var cashierUser: User

    @Before
    fun setUp() {
        categoryRepository = FakeCategoryRepository()
        productRepository = FakeProductRepository()
        tableRepository = FakeTableRepository()
        orderRepository = FakeOrderRepository()

        val salt = SecurityUtils.generateSalt()
        ownerUser = User(id = 1L, name = "Owner User", role = UserRole.OWNER, pinHash = SecurityUtils.hashPin("1234", salt))
        cashierUser = User(id = 2L, name = "Cashier User", role = UserRole.CASHIER, pinHash = SecurityUtils.hashPin("0000", salt))

        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        createCategoryUseCase = CreateCategoryUseCase(categoryRepository)
        updateCategoryUseCase = UpdateCategoryUseCase(categoryRepository)
        deactivateCategoryUseCase = DeactivateCategoryUseCase(categoryRepository)

        getProductsUseCase = GetProductsUseCase(productRepository)
        createProductUseCase = CreateProductUseCase(productRepository, categoryRepository)
        updateProductUseCase = UpdateProductUseCase(productRepository, categoryRepository)
        setProductAvailabilityUseCase = SetProductAvailabilityUseCase(productRepository)
        observeSellableCatalogueUseCase = ObserveSellableCatalogueUseCase(categoryRepository, productRepository)

        createDiningAreaUseCase = CreateDiningAreaUseCase(tableRepository)
        createTableUseCase = CreateTableUseCase(tableRepository)
        updateTableUseCase = UpdateTableUseCase(tableRepository)
        deactivateTableUseCase = DeactivateTableUseCase(tableRepository, orderRepository)
        bulkCreateTablesUseCase = BulkCreateTablesUseCase(tableRepository)
        observeSelectableTablesUseCase = ObserveSelectableTablesUseCase(tableRepository)
    }

    @Test
    fun createCategory_success() = runTest {
        val category = createCategoryUseCase(name = "Boissons Chaudes", currentUser = ownerUser)
        assertNotNull(category)
        assertEquals("Boissons Chaudes", category.name)
        assertTrue(category.active)
    }

    @Test(expected = DomainException::class)
    fun createCategory_duplicateName_throwsException() = runTest {
        createCategoryUseCase(name = "Desserts", currentUser = ownerUser)
        createCategoryUseCase(name = "Desserts", currentUser = ownerUser)
    }

    @Test(expected = DomainException::class)
    fun createCategory_duplicateInactiveName_throwsException() = runTest {
        val category = createCategoryUseCase(name = "Boissons", currentUser = ownerUser)
        deactivateCategoryUseCase(category.id, ownerUser)
        createCategoryUseCase(name = " boissons ", currentUser = ownerUser)
    }

    @Test(expected = DomainException::class)
    fun createCategory_cashierRole_throwsException() = runTest {
        createCategoryUseCase(name = "Snacks", currentUser = cashierUser)
    }

    @Test(expected = DomainException::class)
    fun createProduct_zeroPrice_throwsException() = runTest {
        val category = createCategoryUseCase(name = "Offres", currentUser = ownerUser)
        createProductUseCase(
            categoryId = category.id,
            name = "Verre d'eau gratuit",
            priceCentimes = 0L,
            currentUser = ownerUser
        )
    }

    @Test(expected = DomainException::class)
    fun createProduct_negativePrice_throwsException() = runTest {
        val category = createCategoryUseCase(name = "Café", currentUser = ownerUser)
        createProductUseCase(
            categoryId = category.id,
            name = "Espresso",
            priceCentimes = -1500L,
            currentUser = ownerUser
        )
    }

    @Test(expected = DomainException::class)
    fun createProduct_duplicateInSameCategory_throwsException() = runTest {
        val category = createCategoryUseCase(name = "Jus", currentUser = ownerUser)
        createProductUseCase(categoryId = category.id, name = "Jus d'Orange", priceCentimes = 1500L, currentUser = ownerUser)
        createProductUseCase(categoryId = category.id, name = "Jus d'Orange", priceCentimes = 1500L, currentUser = ownerUser)
    }

    @Test(expected = DomainException::class)
    fun createProduct_inInactiveCategory_throwsException() = runTest {
        val category = createCategoryUseCase(name = "Saisonnier", currentUser = ownerUser)
        deactivateCategoryUseCase(category.id, ownerUser)
        createProductUseCase(
            categoryId = category.id,
            name = "Jus saisonnier",
            priceCentimes = 2_000L,
            currentUser = ownerUser
        )
    }

    @Test
    fun unavailableActiveProduct_remainsVisibleButIsNotSellable() = runTest {
        val category = createCategoryUseCase(name = "Sandwiches", currentUser = ownerUser)
        val product = createProductUseCase(categoryId = category.id, name = "Panini Poulet", priceCentimes = 2500L, available = true, currentUser = ownerUser)

        val catalogueBefore = observeSellableCatalogueUseCase().first()
        assertEquals(1, catalogueBefore.find { it.category.id == category.id }?.products?.size)

        setProductAvailabilityUseCase(productId = product.id, available = false, currentUser = ownerUser)

        val catalogueAfter = observeSellableCatalogueUseCase().first()
        val visibleProduct = catalogueAfter.find { it.category.id == category.id }?.products?.single()
        assertNotNull(visibleProduct)
        assertFalse(visibleProduct!!.available)
    }

    @Test
    fun catalogue_excludesInactiveProductsAndCategoriesWithoutActiveProducts() = runTest {
        val drinks = createCategoryUseCase("Boissons catalogue", ownerUser)
        val empty = createCategoryUseCase("Catégorie vide", ownerUser)
        val active = createProductUseCase(drinks.id, "Actif", 1_000L, currentUser = ownerUser)
        val inactive = createProductUseCase(drinks.id, "Inactif", 1_100L, currentUser = ownerUser)
        DeactivateProductUseCase(productRepository)(inactive.id, ownerUser)

        val catalogue = observeSellableCatalogueUseCase().first()

        assertTrue(catalogue.none { it.category.id == empty.id })
        assertEquals(listOf(active.id), catalogue.single { it.category.id == drinks.id }.products.map { it.id })
    }

    @Test
    fun bulkCreateTables_withPadding_createsCorrectBatch() = runTest {
        val area = createDiningAreaUseCase(name = "Jardin", currentUser = ownerUser)

        val generatedTables = bulkCreateTablesUseCase(
            areaId = area.id,
            prefix = "T",
            startNumber = 1,
            count = 5,
            paddingLength = 2,
            currentUser = ownerUser
        )

        assertEquals(5, generatedTables.size)
        assertEquals("T 01", generatedTables[0].name)
        assertEquals("T 02", generatedTables[1].name)
        assertEquals("T 05", generatedTables[4].name)
    }

    @Test(expected = DomainException::class)
    fun bulkCreateTables_nameConflict_rejectsTransaction() = runTest {
        val area = createDiningAreaUseCase(name = "Mezzanine", currentUser = ownerUser)
        createTableUseCase(areaId = area.id, name = "Table 1", currentUser = ownerUser)

        // Attempting bulk creation containing 'Table 1'
        bulkCreateTablesUseCase(
            areaId = area.id,
            prefix = "Table",
            startNumber = 1,
            count = 5,
            currentUser = ownerUser
        )
    }

    @Test(expected = DomainException::class)
    fun deactivateTable_reservedTable_isRejected() = runTest {
        val area = createDiningAreaUseCase("Réservations", ownerUser)
        val table = createTableUseCase(area.id, "R-1", ownerUser)
        tableRepository.updateTableStatus(table.id, TableStatus.RESERVED)

        deactivateTableUseCase(table.id, ownerUser)
    }

    @Test(expected = DomainException::class)
    fun updateTable_occupiedTableCannotMoveArea() = runTest {
        val source = createDiningAreaUseCase("Salle A", ownerUser)
        val destination = createDiningAreaUseCase("Salle B", ownerUser)
        val table = createTableUseCase(source.id, "A-1", ownerUser)
        tableRepository.updateTableStatus(table.id, TableStatus.OCCUPIED)

        updateTableUseCase(table.id, destination.id, "A-1", ownerUser)
    }

    @Test
    fun category_updateDeactivateAndReactivate_preservesRecord() = runTest {
        val category = createCategoryUseCase("Boissons Chaudes", ownerUser)
        val updated = updateCategoryUseCase(category.id, "Boissons", ownerUser)
        assertEquals("Boissons", updated.name)

        DeactivateCategoryUseCase(categoryRepository)(category.id, ownerUser)
        assertFalse(categoryRepository.getCategoryById(category.id)!!.active)
        ActivateCategoryUseCase(categoryRepository)(category.id, ownerUser)
        assertTrue(categoryRepository.getCategoryById(category.id)!!.active)
    }

    @Test
    fun product_updateDeactivateReactivateAndAvailability_completeLifecycle() = runTest {
        val category = createCategoryUseCase("Jus", ownerUser)
        val product = createProductUseCase(
            category.id,
            "Orange",
            2_000L,
            available = true,
            currentUser = ownerUser
        )
        val updated = updateProductUseCase(
            product.id,
            category.id,
            "Orange Pressée",
            2_500L,
            available = true,
            currentUser = ownerUser
        )
        assertEquals(2_500L, updated.priceCentimes)

        DeactivateProductUseCase(productRepository)(product.id, ownerUser)
        assertFalse(productRepository.getProductById(product.id)!!.active)
        assertFalse(productRepository.getProductById(product.id)!!.available)
        try {
            setProductAvailabilityUseCase(product.id, true, ownerUser)
            fail("Expected DomainException")
        } catch (_: DomainException) {
            // Expected.
        }
        ActivateProductUseCase(productRepository, categoryRepository)(product.id, ownerUser)
        setProductAvailabilityUseCase(product.id, true, ownerUser)
        assertTrue(productRepository.getProductById(product.id)!!.available)
    }

    @Test
    fun productImage_updateCanPreserveReplaceAndRemoveImage() = runTest {
        val category = createCategoryUseCase("Images", ownerUser)
        val product = createProductUseCase(
            category.id,
            "Produit illustré",
            2_500L,
            imagePath = "/images/original.jpg",
            currentUser = ownerUser
        )

        val preserved = updateProductUseCase(
            product.id,
            category.id,
            product.name,
            product.priceCentimes,
            imagePath = null,
            currentUser = ownerUser
        )
        assertEquals("/images/original.jpg", preserved.imagePath)

        val replaced = updateProductUseCase(
            product.id,
            category.id,
            product.name,
            product.priceCentimes,
            imagePath = "/images/replacement.jpg",
            replaceImage = true,
            currentUser = ownerUser
        )
        assertEquals("/images/replacement.jpg", replaced.imagePath)

        val removed = updateProductUseCase(
            product.id,
            category.id,
            product.name,
            product.priceCentimes,
            imagePath = null,
            replaceImage = true,
            currentUser = ownerUser
        )
        assertNull(removed.imagePath)
    }

    @Test
    fun product_rejectsInvalidTva_andInactiveDestinationCategory() = runTest {
        val category = createCategoryUseCase("TVA", ownerUser)
        try {
            createProductUseCase(
                category.id,
                "Invalide",
                1_000L,
                tvaRate = 1.01,
                currentUser = ownerUser
            )
            fail("Expected DomainException")
        } catch (_: DomainException) {
            // Expected.
        }
        DeactivateCategoryUseCase(categoryRepository)(category.id, ownerUser)
        try {
            createProductUseCase(category.id, "Bloqué", 1_000L, currentUser = ownerUser)
            fail("Expected DomainException")
        } catch (_: DomainException) {
            // Expected.
        }
    }

    @Test
    fun areaAndTable_crudLifecycle_andInactiveDuplicateProtection() = runTest {
        val area = createDiningAreaUseCase("Salle CRUD", ownerUser, "managed/area-one.jpg")
        assertEquals("managed/area-one.jpg", area.imagePath)
        val updatedArea = UpdateDiningAreaUseCase(tableRepository)(
            area.id,
            "Salle Principale",
            ownerUser,
            imagePath = "managed/area-two.webp",
            replaceImage = true
        )
        assertEquals("Salle Principale", updatedArea.name)
        assertEquals("managed/area-two.webp", updatedArea.imagePath)
        val removedImage = UpdateDiningAreaUseCase(tableRepository)(
            area.id, "Salle Principale", ownerUser, imagePath = null, replaceImage = true
        )
        assertNull(removedImage.imagePath)
        val table = createTableUseCase(area.id, "Table CRUD", ownerUser)
        val updatedTable = updateTableUseCase(table.id, area.id, "Table VIP", ownerUser)
        assertEquals("Table VIP", updatedTable.name)

        deactivateTableUseCase(table.id, ownerUser)
        assertFalse(tableRepository.getTableById(table.id)!!.active)
        assertThrows(DomainException::class.java) {
            kotlinx.coroutines.test.runTest {
                createTableUseCase(area.id, "Table VIP", ownerUser)
            }
        }
        ActivateTableUseCase(tableRepository)(table.id, ownerUser)
        assertTrue(tableRepository.getTableById(table.id)!!.active)
    }

    @Test
    fun bulkCreateTables_rejectsInvalidCountStartAndPadding() = runTest {
        val area = createDiningAreaUseCase("Lot Validation", ownerUser)
        suspend fun expectFailure(block: suspend () -> Unit) {
            try {
                block()
                fail("Expected DomainException")
            } catch (_: DomainException) {
                // Expected.
            }
        }
        expectFailure {
            bulkCreateTablesUseCase(area.id, count = 0, currentUser = ownerUser)
        }
        expectFailure {
            bulkCreateTablesUseCase(
                area.id,
                startNumber = -1,
                count = 1,
                currentUser = ownerUser
            )
        }
        expectFailure {
            bulkCreateTablesUseCase(
                area.id,
                count = 1,
                paddingLength = 7,
                currentUser = ownerUser
            )
        }
    }
}
