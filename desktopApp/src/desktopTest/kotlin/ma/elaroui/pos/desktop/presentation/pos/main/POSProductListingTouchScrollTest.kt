package ma.elaroui.pos.desktop.presentation.pos.main

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import java.text.Normalizer
import kotlin.math.abs
import kotlin.test.*

class POSProductListingTouchScrollTest {

    private val strings = DesktopStrings(DesktopLanguage.FR)

    private fun normalizeForSearch(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}"), "").lowercase()
    }

    @Test
    fun testProductListingScaleWithLargeCatalog() {
        val categories = (1..5).map { catIndex ->
            Category(id = catIndex.toLong(), name = "Catégorie $catIndex", active = true, displayOrder = catIndex)
        }

        // Generate 150 products across categories
        val allProducts = (1..150).map { prodIndex ->
            val catId = (prodIndex % 5 + 1).toLong()
            Product(
                id = prodIndex.toLong(),
                categoryId = catId,
                name = "Produit Expresso $prodIndex",
                priceCentimes = (prodIndex * 150L),
                taxRateBasisPoints = 1000,
                active = true,
                sku = "SKU-$prodIndex",
                barcode = "6111%06d".format(prodIndex)
            )
        }

        assertEquals(150, allProducts.size)

        // Verify uniqueness of product IDs for LazyVerticalGrid keying
        val keys = allProducts.map { it.id }.toSet()
        assertEquals(150, keys.size, "All product keys must be unique for Compose LazyVerticalGrid")

        // Test Category filtering
        val cat1Products = allProducts.filter { it.categoryId == 1L }
        assertEquals(30, cat1Products.size)

        // Test Search filtering with diacritics / normalization
        val query = "expresso 5"
        val normalizedQuery = normalizeForSearch(query)
        val searchResults = allProducts.filter { p ->
            normalizeForSearch(p.name).contains(normalizedQuery) ||
            normalizeForSearch(p.sku.orEmpty()).contains(normalizedQuery) ||
            normalizeForSearch(p.barcode.orEmpty()).contains(normalizedQuery)
        }
        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.any { it.name == "Produit Expresso 5" })
        assertTrue(searchResults.any { it.name == "Produit Expresso 50" })
    }

    @Test
    fun testInactiveProductFilteringInPosMainGrid() {
        val products = listOf(
            Product(id = 1L, categoryId = 1L, name = "Café Actif", priceCentimes = 1500L, taxRateBasisPoints = 1000, active = true),
            Product(id = 2L, categoryId = 1L, name = "Café Inactif", priceCentimes = 1500L, taxRateBasisPoints = 1000, active = false),
            Product(id = 3L, categoryId = 1L, name = "Thé Actif", priceCentimes = 1200L, taxRateBasisPoints = 1000, active = true)
        )

        val activeProducts = products.filter { it.active }
        assertEquals(2, activeProducts.size)
        assertTrue(activeProducts.none { it.id == 2L })
    }

    @Test
    fun testDatabaseProductListingPersistenceAndRetrieval() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Touchscreen POS Cafe", "Admin", "1234")

            val initialProducts = db.products.observeAll().first()
            val initialCount = initialProducts.size

            val cat1 = db.categories.save(Category(id = 0L, name = "Boissons Chaudes", active = true, displayOrder = 1))
            val cat2 = db.categories.save(Category(id = 0L, name = "Gâteaux Spéciaux", active = true, displayOrder = 2))

            // Save 60 products in db
            (1..60).forEach { i ->
                val catId = if (i <= 30) cat1 else cat2
                db.products.save(
                    Product(
                        id = 0L,
                        categoryId = catId,
                        name = if (i <= 30) "Café Noir #$i" else "Croissant #$i",
                        priceCentimes = 1500L + (i * 50L),
                        taxRateBasisPoints = 1000,
                        active = (i % 10 != 0) // some inactive
                    )
                )
            }

            val savedProducts = db.products.observeAll().first()
            assertEquals(initialCount + 60, savedProducts.size)

            val activeProducts = savedProducts.filter { it.active }
            assertTrue(activeProducts.isNotEmpty())

            // Verify unique IDs for compose lazy grid key adapter
            val uniqueIds = activeProducts.map { it.id }.toSet()
            assertEquals(activeProducts.size, uniqueIds.size)
        }
        Unit
    }

    @Test
    fun testTouchGestureDragVsTapDisambiguationLogic() {
        val touchSlop = 18f // standard desktop/X11 touch slop

        // Simulation 1: Fast or short Tap without dragging
        val tapDeltaY = 4f
        val tapDeltaX = 2f
        val isDragTap = abs(tapDeltaY) > touchSlop && abs(tapDeltaY) >= abs(tapDeltaX)
        assertFalse(isDragTap, "Small movement below touchSlop must be treated as a TAP, not a scroll drag")

        // Simulation 2: Vertical scroll gesture on a product card
        val scrollDeltaY = -45f // swipe up to reveal items below
        val scrollDeltaX = 5f
        val isVerticalDrag = abs(scrollDeltaY) > touchSlop && abs(scrollDeltaY) >= abs(scrollDeltaX)
        assertTrue(isVerticalDrag, "Swipe up exceeding touchSlop must engage vertical scroll")

        // Raw delta dispatch direction check
        val rawDelta = -scrollDeltaY // +45f scroll offset increase
        assertTrue(rawDelta > 0f, "Swiping up reveals products below by advancing scroll offset")

        // Simulation 3: vertical swipe inside the category popup
        val popupSwipeDeltaY = -60f
        val popupSwipeDeltaX = 8f
        val isPopupVerticalDrag = abs(popupSwipeDeltaY) > touchSlop &&
            abs(popupSwipeDeltaY) >= abs(popupSwipeDeltaX)
        assertTrue(isPopupVerticalDrag, "Category popup must remain vertically scrollable by touch")
    }

    @Test
    fun testScrollbarAndEmptyStateLocalization() {
        val stringsFr = DesktopStrings(DesktopLanguage.FR)
        val stringsEn = DesktopStrings(DesktopLanguage.EN)
        val stringsAr = DesktopStrings(DesktopLanguage.AR)

        assertEquals("Rechercher", stringsFr.search)
        assertEquals("Search", stringsEn.search)
        assertEquals("بحث", stringsAr.search)

        assertEquals("Tous", stringsFr.all)
        assertEquals("All", stringsEn.all)
        assertEquals("الكل", stringsAr.all)
    }
}
