package ma.elaroui.pos.presentation.pos.main

import ma.elaroui.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class POSCatalogueFilterTest {
    private val products = listOf(
        Product(1, 10, "Café crème", 1_500),
        Product(2, 10, "Café long", 1_600, active = false),
        Product(3, 20, "Thé menthe", 1_200),
        Product(4, 20, "Café glacé", 2_000)
    )

    @Test
    fun allCategorySearchesOnlyActiveProducts() {
        assertEquals(listOf(1L, 4L), filterPosProducts(products, "café", null).map { it.id })
    }

    @Test
    fun searchIsGlobalEvenWhenCategoryIsSelected() {
        assertEquals(listOf(1L, 4L), filterPosProducts(products, "café", 20).map { it.id })
        assertEquals(listOf(4L, 3L), filterPosProducts(products, "", 20).map { it.id })
    }

    @Test
    fun productsAreSortedByPopularityScores() {
        // Product 4 has score 10, Product 1 has score 50
        val scores = mapOf(4L to 10L, 1L to 50L)
        val sorted = filterPosProducts(products, "café", null, scores)
        assertEquals(listOf(1L, 4L), sorted.map { it.id })

        val reverseScores = mapOf(4L to 100L, 1L to 20L)
        val reverseSorted = filterPosProducts(products, "café", null, reverseScores)
        assertEquals(listOf(4L, 1L), reverseSorted.map { it.id })
    }

    @Test
    fun unscoredProductsAppearAfterScoredProducts() {
        // Only product 4 has a score; product 3 has 0
        val scores = mapOf(4L to 15L)
        val sorted = filterPosProducts(products, "", 20, scores)
        assertEquals(listOf(4L, 3L), sorted.map { it.id })
    }
}
