package ma.elaroui.pos.desktop.presentation.pos.main

import ma.elaroui.pos.shared.domain.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [filterDesktopPosProducts]:
 * - Search must be global (ignores active category filter)
 * - Category filter applies only when search is empty
 * - Active/available product visibility
 */
class POSProductFilterTest {

    private fun product(
        id: Long,
        categoryId: Long,
        name: String,
        sku: String? = null,
        barcode: String? = null,
        active: Boolean = true,
        available: Boolean = true
    ) = Product(
        id = id,
        categoryId = categoryId,
        name = name,
        priceCentimes = 1000L,
        taxRateBasisPoints = 0,
        active = active,
        available = available,
        sku = sku,
        barcode = barcode
    )

    private val catA = 1L
    private val catB = 2L
    private val catC = 3L  // inactive category

    private val products = listOf(
        product(1L, catA, "Café Latte"),
        product(2L, catA, "Café Noisette"),
        product(3L, catB, "Croissant Beurre", sku = "CRO-001"),
        product(4L, catB, "Pain au Chocolat", barcode = "1234567890"),
        product(5L, catC, "Produit inactif", active = false),
        product(6L, catA, "Produit indisponible", available = false),
        product(7L, catB, "Chebakia", sku = "CHB-100"),
    )

    private val activeCategoryIds = setOf(catA, catB)

    // --- No filter, no search ---

    @Test
    fun noFilterNoSearch_showsAllActiveAvailableProducts() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "")
        assertEquals(5, result.size)
        assertTrue(result.none { it.id == 5L })
        assertTrue(result.none { it.id == 6L })
    }

    // --- Category filter, no search ---

    @Test
    fun categoryFilterSelected_noSearch_showsOnlySelectedCategory() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, catA, "")
        val ids = result.map { it.id }
        assertTrue(ids.contains(1L))
        assertTrue(ids.contains(2L))
        assertFalse(ids.contains(3L))
        assertFalse(ids.contains(4L))
        assertFalse(ids.contains(7L))
    }

    // --- KEY: Search is GLOBAL — ignores the active category filter ---

    @Test
    fun searchWithCategoryFilterActive_searchIsGlobal_ignoresCategoryFilter() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, catA, "Croissant")
        assertEquals(1, result.size)
        assertEquals(3L, result.first().id)
    }

    @Test
    fun searchAcrossAllCategories_returnsMatchesFromAllActiveCategories() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, catB, "café")
        val ids = result.map { it.id }
        assertTrue(ids.contains(1L))
        assertTrue(ids.contains(2L))
        assertFalse(ids.contains(3L))
    }

    // --- Search by SKU and barcode ---

    @Test
    fun searchBySku_findsProduct() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "CRO-001")
        assertEquals(1, result.size)
        assertEquals(3L, result.first().id)
    }

    @Test
    fun searchByBarcode_findsProduct() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "1234567890")
        assertEquals(1, result.size)
        assertEquals(4L, result.first().id)
    }

    @Test
    fun searchBySkuWithCategoryFilterActive_stillFindsProduct() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, catA, "CHB-100")
        assertEquals(1, result.size)
        assertEquals(7L, result.first().id)
    }

    // --- Inactive / unavailable products never appear ---

    @Test
    fun search_neverReturnsInactiveOrUnavailableProducts() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "inactif")
        assertTrue(result.isEmpty())
    }

    @Test
    fun search_neverReturnsUnavailableProducts() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "indisponible")
        assertTrue(result.isEmpty())
    }

    // --- Case-insensitive ---

    @Test
    fun search_isCaseInsensitive() {
        val lower = filterDesktopPosProducts(products, activeCategoryIds, null, "café")
        val upper = filterDesktopPosProducts(products, activeCategoryIds, null, "CAFÉ")
        assertEquals(lower.map { it.id }, upper.map { it.id })
    }

    // --- Whitespace treated as empty ---

    @Test
    fun whitespaceOnlySearch_treatedAsEmptySearch_respectsCategoryFilter() {
        val result = filterDesktopPosProducts(products, activeCategoryIds, catA, "   ")
        val ids = result.map { it.id }
        assertTrue(ids.contains(1L))
        assertTrue(ids.contains(2L))
        assertFalse(ids.contains(3L))
    }

    // --- Dynamic product ordering: All products ---

    @Test
    fun allProducts_sortedByPopularityScoreDescending() {
        // In our active list: prod 1 (catA), 2 (catA), 3 (catB), 4 (catB), 7 (catB)
        // Let's assign scores: prod 7 has 50, prod 2 has 20, prod 3 has 10, prod 1 and 4 have 0
        val scores = mapOf(7L to 50L, 2L to 20L, 3L to 10L)
        val result = filterDesktopPosProducts(products, activeCategoryIds, null, "", scores)

        // Best sellers must appear first
        assertEquals(7L, result[0].id, "Highest score (prod 7, score 50) must appear first")
        assertEquals(2L, result[1].id, "Second highest score (prod 2, score 20) must appear second")
        assertEquals(3L, result[2].id, "Third highest score (prod 3, score 10) must appear third")

        // Products without sales score (0) must appear after products with sales history
        val remainingIds = result.drop(3).map { it.id }.toSet()
        assertEquals(setOf(1L, 4L), remainingIds, "Products without sales history appear after scored products")
    }

    // --- Dynamic product ordering: Selected Category ---

    @Test
    fun selectedCategory_sortedByPopularityScoreDescending() {
        // catB products: prod 3 (Croissant), prod 4 (Pain au Chocolat), prod 7 (Chebakia)
        // Let's assign score: prod 4 has 100, prod 3 has 50, prod 7 has 0
        val scores = mapOf(4L to 100L, 3L to 50L)
        val result = filterDesktopPosProducts(products, activeCategoryIds, catB, "", scores)

        assertEquals(3, result.size)
        assertEquals(4L, result[0].id, "Best seller in catB (prod 4, score 100) must appear first")
        assertEquals(3L, result[1].id, "Second best seller in catB (prod 3, score 50) must appear second")
        assertEquals(7L, result[2].id, "Product without sales in catB (prod 7, score 0) must appear last")
    }

    // --- Recency weighting priority ---

    @Test
    fun recentSalesGivenPriorityOverOlderSales() {
        val now = 100_000_000_000L
        val day = 24L * 60L * 60L * 1000L

        // prod 1: 5 units sold yesterday (age 1 day, weight 4 -> score = 20)
        // prod 2: 12 units sold 40 days ago (age 40 days, weight 1 -> score = 12)
        val samples = listOf(
            ma.elaroui.pos.shared.rules.ProductSaleSample(productId = 1L, quantity = 5, soldAtEpochMilliseconds = now - day),
            ma.elaroui.pos.shared.rules.ProductSaleSample(productId = 2L, quantity = 12, soldAtEpochMilliseconds = now - 40L * day)
        )

        val scores = ma.elaroui.pos.shared.rules.ProductPopularityRules.scores(samples, now)
        val result = filterDesktopPosProducts(products, activeCategoryIds, catA, "", scores)

        // prod 1 should rank higher than prod 2 because of the recency multiplier
        assertEquals(1L, result[0].id, "Recent sales give higher score (prod 1: 5*4=20 vs prod 2: 12*1=12)")
        assertEquals(2L, result[1].id)
    }
}