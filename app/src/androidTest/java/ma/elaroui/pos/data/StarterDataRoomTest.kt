package ma.elaroui.pos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.data.repository.CategoryRepositoryImpl
import ma.elaroui.pos.data.repository.ProductRepositoryImpl
import ma.elaroui.pos.data.repository.TableRepositoryImpl
import ma.elaroui.pos.domain.usecase.EnsureStarterDataUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarterDataRoomTest {
    private lateinit var database: POSDatabase
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var products: ProductRepositoryImpl
    private lateinit var tables: TableRepositoryImpl
    private lateinit var ensureStarterData: EnsureStarterDataUseCase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            POSDatabase::class.java
        ).allowMainThreadQueries().build()
        categories = CategoryRepositoryImpl(database.categoryDao())
        products = ProductRepositoryImpl(database.productDao())
        tables = TableRepositoryImpl(database.tableDao())
        ensureStarterData = EnsureStarterDataUseCase(categories, products, tables)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun seedAndRepair_isIdempotentAndPreservesExistingRoomRows() = runBlocking {
        ensureStarterData()

        assertEquals(7, categories.getAllCategoriesForOwner().first().size)
        assertEquals(34, products.getAllProductsForOwner().first().size)
        assertEquals(1, tables.getAllAreasForOwner().first().size)
        assertEquals(10, tables.getAllTablesForOwner().first().size)

        val coffee = categories.getCategoryByName("Coffee")!!
        val espresso = products.getProductByNameInCategory(coffee.id, "Espresso")!!
        products.updateProduct(espresso.copy(priceCentimes = 9_999L, available = false))
        val tableOne = tables.getTableByNameInArea(
            tables.getAreaByName("Salle")!!.id,
            "Table 1"
        )!!
        tables.deactivateTable(tableOne.id)

        ensureStarterData()

        assertEquals(7, categories.getAllCategoriesForOwner().first().size)
        assertEquals(34, products.getAllProductsForOwner().first().size)
        assertEquals(10, tables.getAllTablesForOwner().first().size)
        assertEquals(
            9_999L,
            products.getProductByNameInCategory(coffee.id, "Espresso")!!.priceCentimes
        )
        assertFalse(
            tables.getTableByNameInArea(tableOne.areaId, "Table 1")!!.active
        )
    }
}
