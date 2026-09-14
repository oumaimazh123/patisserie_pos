package ma.elaroui.pos.desktop.presentation.pos.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.domain.isRoot
import ma.elaroui.pos.shared.domain.isSubcategory

/**
 * Unit tests for POS category selector logic and large category counts (10–15 categories).
 */
class PosCategorySelectorTest {

    private fun generateCategories(count: Int): List<Category> {
        return (1..count).map { i ->
            Category(
                id = i.toLong(),
                name = "Catégorie $i",
                active = true,
                displayOrder = i
            )
        }
    }

    private fun generateProducts(categories: List<Category>): List<Product> {
        return categories.map { cat ->
            Product(
                id = cat.id * 100,
                categoryId = cat.id,
                name = "Produit ${cat.name}",
                priceCentimes = 1500L,
                taxRateBasisPoints = 2000,
                active = true,
                available = true
            )
        }
    }

    @Test
    fun `all active categories are retained when 15 categories are present`() {
        val categories = generateCategories(15)
        val activeCategories = categories.filter { it.active }

        assertEquals(15, activeCategories.size)
        assertEquals(1L, activeCategories.first().id)
        assertEquals(15L, activeCategories.last().id)
    }

    @Test
    fun `inactive categories are excluded while active 10 categories remain`() {
        val categories = generateCategories(12).mapIndexed { idx, cat ->
            if (idx == 2 || idx == 5) cat.copy(active = false) else cat
        }
        val activeCategories = categories.filter { it.active }

        assertEquals(10, activeCategories.size)
        assertFalse(activeCategories.any { it.id == 3L || it.id == 6L })
    }

    @Test
    fun `non-empty product search is global even when another category is selected`() {
        val categories = generateCategories(3)
        val products = generateProducts(categories) + Product(
            id = 999,
            categoryId = 1,
            name = "Article spécial",
            priceCentimes = 500,
            taxRateBasisPoints = 0,
            sku = "GLOBAL-SKU",
            barcode = "611999"
        )
        val activeIds = categories.mapTo(mutableSetOf()) { it.id }

        assertEquals(
            listOf(999L),
            filterDesktopPosProducts(products, activeIds, selectedCategoryId = 3L, searchQuery = "global-sku")
                .map { it.id }
        )
    }

    @Test
    fun `product filtering correctly filters products for selected category out of 15 categories`() {
        val categories = generateCategories(15)
        val products = generateProducts(categories)
        val activeCategoryIds = categories.filter { it.active }.mapTo(mutableSetOf()) { it.id }

        // When "Tous" is selected (selectedCategoryId == null)
        val allFiltered = filterDesktopPosProducts(products, activeCategoryIds, null, "")
        assertEquals(15, allFiltered.size)

        // When Category 7 is selected
        val selectedCatId: Long? = 7L
        val cat7Filtered = filterDesktopPosProducts(products, activeCategoryIds, selectedCatId, "")
        assertEquals(1, cat7Filtered.size)
        assertEquals(700L, cat7Filtered.first().id)
        assertEquals("Produit Catégorie 7", cat7Filtered.first().name)
    }

    @Test
    fun `categories without products are still displayed so cashier can navigate all categories`() {
        val categories = generateCategories(10)
        // Products only exist for categories 1 to 5
        val products = generateProducts(categories.take(5))
        val activeCategories = categories.filter { it.active }

        assertEquals(10, activeCategories.size, "All 10 categories must be visible in the selector")
    }

    @Test
    fun `categories sorted by recent sales weight 7d over 30d over 90d and unsold after sold`() {
        val now = 100_000_000_000L
        val day = 24L * 60L * 60L * 1000L

        val cat1 = Category(id = 1L, name = "Boissons Chaudes", displayOrder = 1, active = true)
        val cat2 = Category(id = 2L, name = "Pâtisserie", displayOrder = 2, active = true)
        val cat3 = Category(id = 3L, name = "Jus Frais", displayOrder = 3, active = true)
        val cat4 = Category(id = 4L, name = "Sandwichs", displayOrder = 4, active = true)
        val cat5 = Category(id = 5L, name = "Ancienne Catégorie", displayOrder = 5, active = true)

        // cat1: 2 sales 2 days ago (<= 7 days, weight 4) -> score = 8
        // cat2: 3 sales 20 days ago (<= 30 days, weight 2) -> score = 6
        // cat3: 4 sales 60 days ago (<= 90 days, weight 1) -> score = 4
        // cat5: 50 sales 100 days ago (> 90 days, weight 0) -> score = 0
        // cat4: 0 sales -> score = 0
        val samples = listOf(
            ma.elaroui.pos.shared.rules.CategorySaleSample(categoryId = 1L, quantity = 2, soldAtEpochMilliseconds = now - 2L * day),
            ma.elaroui.pos.shared.rules.CategorySaleSample(categoryId = 2L, quantity = 3, soldAtEpochMilliseconds = now - 20L * day),
            ma.elaroui.pos.shared.rules.CategorySaleSample(categoryId = 3L, quantity = 4, soldAtEpochMilliseconds = now - 60L * day),
            ma.elaroui.pos.shared.rules.CategorySaleSample(categoryId = 5L, quantity = 50, soldAtEpochMilliseconds = now - 100L * day),
        )

        val activeIds = setOf(1L, 2L, 3L, 4L, 5L)
        val scores = ma.elaroui.pos.shared.rules.CategoryPopularityRules.scores(samples, now, activeIds)
        val sorted = ma.elaroui.pos.shared.rules.CategoryPopularityRules.sortCategories(
            listOf(cat5, cat4, cat3, cat2, cat1),
            scores
        )

        // Expected: cat1 (score 8) > cat2 (score 6) > cat3 (score 4) > cat4 (displayOrder 4, score 0) > cat5 (displayOrder 5, score 0)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sorted.map { it.id })
    }

    @Test
    fun `picker options start with Tous les produits followed by sorted categories`() {
        val cat1 = Category(id = 1L, name = "Café", displayOrder = 1, active = true)
        val cat2 = Category(id = 2L, name = "Thé", displayOrder = 2, active = true)
        val scores = mapOf(2L to 20L, 1L to 5L)
        val sorted = ma.elaroui.pos.shared.rules.CategoryPopularityRules.sortCategories(listOf(cat1, cat2), scores)

        // Simulated dialog items: "Tous les produits" (id = null) + sorted categories
        val pickerOptions = listOf<Long?>(null) + sorted.map { it.id }
        assertEquals(listOf(null, 2L, 1L), pickerOptions)
    }

    @Test
    fun `category possesses exactly one subcategory and filtering includes products of both`() {
        val rootCat = Category(id = 10L, name = "Boulangerie", parentId = null, imagePath = "images/boulangerie.png", active = true)
        val subCat = Category(id = 11L, name = "Pains Spéciaux", parentId = 10L, imagePath = "images/pains.png", active = true)
        val allCategories = listOf(rootCat, subCat)

        // 1. Extensions verify 1-to-1 structural properties
        assertTrue(rootCat.isRoot)
        assertFalse(rootCat.isSubcategory)
        assertFalse(subCat.isRoot)
        assertTrue(subCat.isSubcategory)

        // 2. Root categories filter retains only root categories for horizontal bar
        val rootCategories = allCategories.filter { it.isRoot }
        assertEquals(1, rootCategories.size)
        assertEquals(10L, rootCategories.first().id)

        // 3. Root category resolves its single unique subcategory
        val uniqueSub = allCategories.firstOrNull { it.parentId == rootCat.id }
        assertEquals(subCat, uniqueSub)

        // 4. Products under root OR subcategory are both matched in POS filter
        val prodRoot = Product(id = 101L, categoryId = 10L, name = "Baguette Tradition", priceCentimes = 120L, taxRateBasisPoints = 2000, active = true, available = true)
        val prodSub = Product(id = 102L, categoryId = 11L, name = "Pain de Seigle", priceCentimes = 250L, taxRateBasisPoints = 2000, active = true, available = true)
        val prodOther = Product(id = 103L, categoryId = 99L, name = "Autre", priceCentimes = 300L, taxRateBasisPoints = 2000, active = true, available = true)
        val products = listOf(prodRoot, prodSub, prodOther)

        val activeCategoryIds = setOf(10L, 11L)
        val allowedCategoryIds = setOf(rootCat.id, uniqueSub!!.id)

        val filtered = filterDesktopPosProducts(
            products = products,
            activeCategoryIds = activeCategoryIds,
            selectedCategoryId = rootCat.id,
            searchQuery = "",
            allowedCategoryIds = allowedCategoryIds
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 101L })
        assertTrue(filtered.any { it.id == 102L })
        assertFalse(filtered.any { it.id == 103L })
    }

    @Test
    fun `three level progressive category filtering and breadcrumb path resolution`() {
        val lvl1 = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "img/patisserie.png", active = true)
        val lvl2A = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "img/gateaux.png", active = true)
        val lvl2B = Category(id = 3L, name = "Tartes", parentId = 1L, imagePath = "img/tartes.png", active = true)
        val lvl3 = Category(id = 4L, name = "Gâteaux Individuels", parentId = 2L, imagePath = "img/indiv.png", active = true)
        val otherRoot = Category(id = 5L, name = "Boissons", parentId = null, imagePath = "img/boissons.png", active = true)

        val allCategories = listOf(lvl1, lvl2A, lvl2B, lvl3, otherRoot)
        val activeCategoryIds = allCategories.mapTo(mutableSetOf()) { it.id }

        // Products at each level
        val pLvl1 = Product(id = 100L, categoryId = 1L, name = "Plateau Pâtisserie", priceCentimes = 5000L, taxRateBasisPoints = 2000, active = true, available = true)
        val pLvl2A = Product(id = 200L, categoryId = 2L, name = "Forêt Noire", priceCentimes = 3000L, taxRateBasisPoints = 2000, active = true, available = true)
        val pLvl2B = Product(id = 300L, categoryId = 3L, name = "Tarte Citron", priceCentimes = 2500L, taxRateBasisPoints = 2000, active = true, available = true)
        val pLvl3 = Product(id = 400L, categoryId = 4L, name = "Éclair Chocolat", priceCentimes = 350L, taxRateBasisPoints = 2000, active = true, available = true)
        val pOther = Product(id = 500L, categoryId = 5L, name = "Café Expresso", priceCentimes = 200L, taxRateBasisPoints = 2000, active = true, available = true)
        val products = listOf(pLvl1, pLvl2A, pLvl2B, pLvl3, pOther)

        // 1. Breadcrumb verification
        val pathLvl3 = ma.elaroui.pos.shared.rules.CategoryHierarchyRules.getBreadcrumbPath(4L, allCategories)
        assertEquals(listOf(1L, 2L, 4L), pathLvl3.map { it.id })
        assertEquals("Pâtisserie › Gâteaux › Gâteaux Individuels", pathLvl3.joinToString(" › ") { it.name })

        // 2. Progressive selection at Level 1 (Pâtisserie):
        // Descendants are [2, 3, 4]. Allowed = [1, 2, 3, 4]
        val allowedLvl1 = setOf(1L) + ma.elaroui.pos.shared.rules.CategoryHierarchyRules.getAllDescendantIds(1L, allCategories)
        assertEquals(setOf(1L, 2L, 3L, 4L), allowedLvl1)
        val filteredLvl1 = filterDesktopPosProducts(
            products = products,
            activeCategoryIds = activeCategoryIds,
            selectedCategoryId = 1L,
            searchQuery = "",
            allowedCategoryIds = allowedLvl1
        )
        // Must include all products of Lvl1, Lvl2A, Lvl2B, and Lvl3, but NOT Boissons (5)
        assertEquals(4, filteredLvl1.size)
        assertTrue(filteredLvl1.any { it.id == 100L })
        assertTrue(filteredLvl1.any { it.id == 200L })
        assertTrue(filteredLvl1.any { it.id == 300L })
        assertTrue(filteredLvl1.any { it.id == 400L })
        assertFalse(filteredLvl1.any { it.id == 500L })

        // 3. Progressive selection at Level 2 (Gâteaux):
        // Descendants are [4]. Allowed = [2, 4]
        val allowedLvl2 = setOf(2L) + ma.elaroui.pos.shared.rules.CategoryHierarchyRules.getAllDescendantIds(2L, allCategories)
        assertEquals(setOf(2L, 4L), allowedLvl2)
        val filteredLvl2 = filterDesktopPosProducts(
            products = products,
            activeCategoryIds = activeCategoryIds,
            selectedCategoryId = 2L,
            searchQuery = "",
            allowedCategoryIds = allowedLvl2
        )
        // Must include Forêt Noire (200) and Éclair Chocolat (400), but NOT Lvl1 (100) or Tartes (300)
        assertEquals(2, filteredLvl2.size)
        assertTrue(filteredLvl2.any { it.id == 200L })
        assertTrue(filteredLvl2.any { it.id == 400L })

        // 4. Progressive selection at Level 3 (Gâteaux Individuels):
        // Descendants are empty. Allowed = [4]
        val allowedLvl3 = setOf(4L) + ma.elaroui.pos.shared.rules.CategoryHierarchyRules.getAllDescendantIds(4L, allCategories)
        assertEquals(setOf(4L), allowedLvl3)
        val filteredLvl3 = filterDesktopPosProducts(
            products = products,
            activeCategoryIds = activeCategoryIds,
            selectedCategoryId = 4L,
            searchQuery = "",
            allowedCategoryIds = allowedLvl3
        )
        assertEquals(1, filteredLvl3.size)
        assertEquals(400L, filteredLvl3.first().id)
    }
}
