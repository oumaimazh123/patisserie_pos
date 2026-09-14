package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

/** A completed-sale line used to rank POS categories. */
data class CategorySaleSample(
    val categoryId: Long,
    val quantity: Int,
    val soldAtEpochMilliseconds: Long
)

/** A completed-sale line used to rank POS products. */
data class ProductSaleSample(
    val productId: Long,
    val quantity: Int,
    val soldAtEpochMilliseconds: Long
)

/**
 * Ranks categories from real completed sales while deliberately favouring recent activity.
 * Sales older than 90 days no longer influence the selector, preventing historic best sellers
 * from staying pinned forever.
 */
object CategoryPopularityRules {
    const val LOOKBACK_DAYS = 90L
    private const val DAY_MS = 24L * 60L * 60L * 1_000L

    fun scores(
        samples: List<CategorySaleSample>,
        nowEpochMilliseconds: Long,
        activeCategoryIds: Set<Long> = emptySet()
    ): Map<Long, Long> {
        if (samples.isEmpty()) return emptyMap()

        return samples.asSequence()
            .filter { (activeCategoryIds.isEmpty() || it.categoryId in activeCategoryIds) && it.quantity > 0 }
            .mapNotNull { sample ->
                val age = (nowEpochMilliseconds - sample.soldAtEpochMilliseconds).coerceAtLeast(0L)
                val weight = when {
                    age <= 7L * DAY_MS -> 4L
                    age <= 30L * DAY_MS -> 2L
                    age <= LOOKBACK_DAYS * DAY_MS -> 1L
                    else -> 0L
                }
                weight.takeIf { it > 0L }?.let { sample.categoryId to sample.quantity.toLong() * it }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, weightedScores) -> weightedScores.sum() }
    }

    fun sortCategories(
        categories: List<Category>,
        scores: Map<Long, Long>
    ): List<Category> {
        return categories.sortedWith(
            compareByDescending<Category> { scores[it.id] ?: 0L }
                .thenBy { it.displayOrder }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
    }

    fun rank(
        samples: List<CategorySaleSample>,
        nowEpochMilliseconds: Long,
        activeCategoryIds: Set<Long>,
        limit: Int = 4
    ): List<Long> {
        if (limit <= 0 || activeCategoryIds.isEmpty()) return emptyList()

        val calculatedScores = scores(samples, nowEpochMilliseconds, activeCategoryIds)
        return calculatedScores.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    fun lookbackStart(nowEpochMilliseconds: Long): Long =
        (nowEpochMilliseconds - LOOKBACK_DAYS * DAY_MS).coerceAtLeast(0L)
}

/**
 * Ranks products from real completed sales while giving more weight to recent sales.
 * Products with sales history appear before products without history.
 */
object ProductPopularityRules {
    const val LOOKBACK_DAYS = 90L
    private const val DAY_MS = 24L * 60L * 60L * 1_000L

    fun scores(
        samples: List<ProductSaleSample>,
        nowEpochMilliseconds: Long,
        activeProductIds: Set<Long> = emptySet()
    ): Map<Long, Long> {
        if (samples.isEmpty()) return emptyMap()

        return samples.asSequence()
            .filter { (activeProductIds.isEmpty() || it.productId in activeProductIds) && it.quantity > 0 }
            .mapNotNull { sample ->
                val age = (nowEpochMilliseconds - sample.soldAtEpochMilliseconds).coerceAtLeast(0L)
                val weight = when {
                    age <= 7L * DAY_MS -> 4L
                    age <= 30L * DAY_MS -> 2L
                    age <= LOOKBACK_DAYS * DAY_MS -> 1L
                    else -> 0L
                }
                weight.takeIf { it > 0L }?.let { sample.productId to sample.quantity.toLong() * it }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, weightedScores) -> weightedScores.sum() }
    }

    fun sortProducts(
        products: List<Product>,
        scores: Map<Long, Long>
    ): List<Product> {
        return products.sortedWith(
            compareByDescending<Product> { scores[it.id] ?: 0L }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
    }

    fun lookbackStart(nowEpochMilliseconds: Long): Long =
        (nowEpochMilliseconds - LOOKBACK_DAYS * DAY_MS).coerceAtLeast(0L)
}

