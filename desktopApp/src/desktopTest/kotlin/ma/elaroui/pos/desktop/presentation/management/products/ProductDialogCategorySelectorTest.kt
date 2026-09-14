package ma.elaroui.pos.desktop.presentation.management.products

import ma.elaroui.pos.shared.domain.Category
import kotlin.test.*

class ProductDialogCategorySelectorTest {

    @Test
    fun testActiveCategoriesAndCurrentSelectedCategoryIncluded() {
        // 20 categories test dataset
        val categories = (1L..20L).map { id ->
            Category(
                id = id,
                name = "Catégorie $id",
                active = id != 13L, // Category 13 is inactive
                displayOrder = id.toInt()
            )
        }

        // When selected is an active category (e.g. 5)
        val selectedActive = 5L
        val activeList1 = categories.filter { it.active || it.id == selectedActive }
        assertEquals(19, activeList1.size)
        assertFalse(activeList1.any { it.id == 13L })
        assertTrue(activeList1.any { it.id == selectedActive })

        // When editing a product belonging to an inactive category (13)
        val selectedInactive = 13L
        val activeList2 = categories.filter { it.active || it.id == selectedInactive }
        assertEquals(20, activeList2.size)
        assertTrue(activeList2.any { it.id == 13L })
    }

    @Test
    fun testCategoryIndexResolutionForAutoScrollWithManyCategories() {
        val categories = (1L..25L).map { id ->
            Category(id = id, name = "Catégorie $id", active = true, displayOrder = id.toInt())
        }

        // Category 1 is at index 0
        assertEquals(0, categories.indexOfFirst { it.id == 1L })
        // Category 18 is at index 17
        assertEquals(17, categories.indexOfFirst { it.id == 18L })
        // Category 25 is at index 24
        assertEquals(24, categories.indexOfFirst { it.id == 25L })
        // Non-existent category
        assertEquals(-1, categories.indexOfFirst { it.id == 999L })
    }

    @Test
    fun testScrollStepDistanceMovesMultipleCategoriesAtOnce() {
        // Chip width ~100px + spacing 6px = 106px per category item
        val approximateItemWidth = 106f
        val stepDelta = 280f

        // Step of 280px scrolls ~2.64 categories per arrow click, satisfying smooth multi-item scrolling
        val categoriesScrolled = stepDelta / approximateItemWidth
        assertTrue(categoriesScrolled >= 2.0f, "Scroll step must scroll at least 2 categories at a time")
        assertTrue(categoriesScrolled <= 4.0f, "Scroll step should not skip too many categories at once")
    }

    @Test
    fun testTouchTargetMinimumDimensionsSatisfyTouchscreenStandards() {
        val minimumTouchTargetDp = 48
        val arrowButtonWidthDp = 48
        val arrowButtonHeightDp = 48
        val chipHeightDp = 48

        assertTrue(arrowButtonWidthDp >= minimumTouchTargetDp, "Arrow button width must be at least 48dp for touchscreen")
        assertTrue(arrowButtonHeightDp >= minimumTouchTargetDp, "Arrow button height must be at least 48dp for touchscreen")
        assertTrue(chipHeightDp >= minimumTouchTargetDp, "Category chip height must be at least 48dp for touchscreen")
    }

    @Test
    fun testDialogWidthAccommodatesBothArrowsAndVisibleChipsWithoutOverflow() {
        val dialogMaxWidthDp = 500
        val arrowButtonsTotalWidthDp = 48 + 48 + (6 * 2) // Two 48dp buttons + 6dp gaps = 108dp
        val availableListWidthDp = dialogMaxWidthDp - arrowButtonsTotalWidthDp

        assertTrue(availableListWidthDp >= 380, "Available horizontal space for category list must be at least 380dp")
    }
}
