package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryPopularityRulesTest {
    private val day = 24L * 60L * 60L * 1_000L
    private val now = 200L * day

    @Test
    fun `recent sales receive more weight than older sales`() {
        val ranked = CategoryPopularityRules.rank(
            samples = listOf(
                CategorySaleSample(categoryId = 1, quantity = 3, soldAtEpochMilliseconds = now - day),
                CategorySaleSample(categoryId = 2, quantity = 8, soldAtEpochMilliseconds = now - 45L * day)
            ),
            nowEpochMilliseconds = now,
            activeCategoryIds = setOf(1, 2)
        )

        assertEquals(listOf(1L, 2L), ranked)
    }

    @Test
    fun `inactive and sales older than lookback are excluded`() {
        val ranked = CategoryPopularityRules.rank(
            samples = listOf(
                CategorySaleSample(categoryId = 1, quantity = 100, soldAtEpochMilliseconds = now - 100L * day),
                CategorySaleSample(categoryId = 2, quantity = 5, soldAtEpochMilliseconds = now - day),
                CategorySaleSample(categoryId = 3, quantity = 20, soldAtEpochMilliseconds = now - day)
            ),
            nowEpochMilliseconds = now,
            activeCategoryIds = setOf(1, 2)
        )

        assertEquals(listOf(2L), ranked)
    }

    @Test
    fun `category sorting places categories with sales first and categories without sales after`() {
        val cat1 = Category(id = 1L, name = "Boissons", displayOrder = 1)
        val cat2 = Category(id = 2L, name = "Pâtisserie", displayOrder = 2)
        val cat3 = Category(id = 3L, name = "Snacks", displayOrder = 3)
        val cat4 = Category(id = 4L, name = "Autres", displayOrder = 4)

        // Cat 2 has 2 sales 3 days ago (score = 2 * 4 = 8)
        // Cat 1 has 5 sales 40 days ago (score = 5 * 1 = 5)
        // Cat 3 & 4 have 0 sales
        val samples = listOf(
            CategorySaleSample(categoryId = 2L, quantity = 2, soldAtEpochMilliseconds = now - 3L * day),
            CategorySaleSample(categoryId = 1L, quantity = 5, soldAtEpochMilliseconds = now - 40L * day)
        )

        val scores = CategoryPopularityRules.scores(samples, now, setOf(1L, 2L, 3L, 4L))
        val sorted = CategoryPopularityRules.sortCategories(listOf(cat4, cat3, cat1, cat2), scores)

        // Expected order: cat2 (score 8) > cat1 (score 5) > cat3 (displayOrder 3) > cat4 (displayOrder 4)
        assertEquals(listOf(2L, 1L, 3L, 4L), sorted.map { it.id })
    }

    @Test
    fun `product popularity rules favor recent sales and place products without sales after`() {
        val prodA = Product(id = 10L, categoryId = 1L, name = "Café Expresso", priceCentimes = 1500, taxRateBasisPoints = 0)
        val prodB = Product(id = 20L, categoryId = 1L, name = "Cappuccino", priceCentimes = 2000, taxRateBasisPoints = 0)
        val prodC = Product(id = 30L, categoryId = 1L, name = "Thé à la menthe", priceCentimes = 1200, taxRateBasisPoints = 0)
        val prodD = Product(id = 40L, categoryId = 1L, name = "Jus d'Orange", priceCentimes = 2500, taxRateBasisPoints = 0)

        // prodB: 4 sold 2 days ago (weight 4) -> score = 16
        // prodA: 10 sold 50 days ago (weight 1) -> score = 10
        // prodC & prodD: 0 sold (score 0)
        val samples = listOf(
            ProductSaleSample(productId = 20L, quantity = 4, soldAtEpochMilliseconds = now - 2L * day),
            ProductSaleSample(productId = 10L, quantity = 10, soldAtEpochMilliseconds = now - 50L * day)
        )

        val scores = ProductPopularityRules.scores(samples, now, setOf(10L, 20L, 30L, 40L))
        val sorted = ProductPopularityRules.sortProducts(listOf(prodD, prodC, prodA, prodB), scores)

        // prodB (16) > prodA (10) > prodD (Jus d'Orange) > prodC (Thé à la menthe, alphabetical)
        assertEquals(listOf(20L, 10L, 40L, 30L), sorted.map { it.id })
    }
}
