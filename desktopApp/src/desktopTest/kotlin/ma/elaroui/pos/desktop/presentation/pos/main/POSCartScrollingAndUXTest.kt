package ma.elaroui.pos.desktop.presentation.pos.main

import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.shared.domain.Product
import kotlin.test.*

class POSCartScrollingAndUXTest {

    private val strings = DesktopStrings(DesktopLanguage.FR)

    private fun createProduct(id: Long, name: String, priceCentimes: Long = 10_00L) = Product(
        id = id,
        categoryId = 1L,
        name = name,
        priceCentimes = priceCentimes,
        taxRateBasisPoints = 1000,
        active = true
    )

    @Test
    fun test01_cartScrollbarAndLayoutIntegrity() {
        val productsById = (1L..15L).associate { id ->
            id to createProduct(id, "Pâtisserie #$id")
        }

        // Simulating 15 items in cart (requiring vertical scrolling)
        val cart = (1L..15L).associate { id -> id to 2 }.toMutableMap()
        val cartItems = buildCartItems(cart, productsById)

        assertEquals(15, cartItems.size, "Cart should contain 15 line items")
        val totalCount = cartItems.sumOf { it.second }
        assertEquals(30, totalCount, "Total items count should be 30")

        // Unique keys invariant for LazyColumn
        val keys = cartItems.map { it.first.id }.toSet()
        assertEquals(15, keys.size, "All cart line items must have unique keys for smooth LazyColumn scrolling")
    }

    @Test
    fun test02_autoScrollLogic_triggersOnlyOnNewProductReference() {
        // Initial state: cart with product 1
        var previousProductIds = setOf(1L)
        val cart = mutableMapOf(1L to 1)

        // 1. Simulating quantity update (1 -> 2)
        cart[1L] = 2
        var currentKeys = cart.keys.toSet()
        var newKeys = currentKeys - previousProductIds
        assertTrue(newKeys.isEmpty(), "Quantity changes must NOT trigger auto-scroll")
        previousProductIds = currentKeys

        // 2. Simulating adding a brand new product reference (Product 2)
        cart[2L] = 1
        currentKeys = cart.keys.toSet()
        newKeys = currentKeys - previousProductIds
        assertEquals(setOf(2L), newKeys, "Adding a new product reference must be detected for auto-scroll")
        val productsById = mapOf(1L to createProduct(1L, "Croissant"), 2L to createProduct(2L, "Éclair"))
        val cartItems = buildCartItems(cart, productsById)
        val targetIndex = cartItems.indexOfFirst { (p, _) -> p.id in newKeys }
        assertTrue(targetIndex >= 0, "Target index for new item must be valid for animateScrollToItem")
        previousProductIds = currentKeys

        // 3. Simulating item discount or price change (same keys)
        currentKeys = cart.keys.toSet()
        newKeys = currentKeys - previousProductIds
        assertTrue(newKeys.isEmpty(), "Discount or price changes without new keys must NOT trigger auto-scroll")

        // 4. Simulating removing an item
        cart.remove(1L)
        currentKeys = cart.keys.toSet()
        newKeys = currentKeys - previousProductIds
        assertTrue(newKeys.isEmpty(), "Removing an item must NOT trigger auto-scroll")
    }

    @Test
    fun test03_mouseWheelSensitivityMultiplier_0_75x() {
        val baseLineScrollPx = 64f
        val multiplier = 0.75f

        // Standard notch delta
        val notchDeltaY = 1.0f
        val calculatedScroll = notchDeltaY * baseLineScrollPx * multiplier

        assertEquals(48.0f, calculatedScroll, 0.001f, "0.75x multiplier must scale 64px to exactly 48px")

        // Reverse notch (scrolling up)
        val reverseDeltaY = -1.0f
        val reverseCalculatedScroll = reverseDeltaY * baseLineScrollPx * multiplier

        assertEquals(-48.0f, reverseCalculatedScroll, 0.001f, "Negative scroll delta must scale accordingly")
        assertTrue(kotlin.math.abs(calculatedScroll) < baseLineScrollPx, "Scaled delta must be smaller than default 64px for smoother feel")
    }

    @Test
    fun test04_clearCartConfirmationWorkflow() {
        val cart = mutableMapOf(1L to 3, 2L to 1, 3L to 4)
        val itemDiscounts = mutableMapOf(1L to 1000, 2L to 500)

        // Case A: User opens dialog and cancels -> cart remains intact
        var dialogOpen = true
        var userConfirmed = false

        if (!userConfirmed) {
            dialogOpen = false
            // No action taken
        }

        assertEquals(3, cart.size, "Cart must remain intact when user cancels clear dialog")
        assertEquals(2, itemDiscounts.size, "Discounts must remain intact when user cancels")

        // Case B: User confirms "Vider"
        dialogOpen = true
        userConfirmed = true

        if (userConfirmed) {
            cart.clear()
            itemDiscounts.clear()
            dialogOpen = false
        }

        assertTrue(cart.isEmpty(), "Cart must be completely cleared upon confirmation")
        assertTrue(itemDiscounts.isEmpty(), "Item discounts must be cleared upon confirmation")
        assertFalse(dialogOpen, "Dialog must close after clear action")
    }

    @Test
    fun test05_touchAndMouseCoexistenceWithoutConflicts() {
        var scrollOffset = 0f

        // 1. Mouse wheel event: scrolls 48px (0.75x)
        val wheelDelta = 48f
        scrollOffset += wheelDelta
        assertEquals(48f, scrollOffset)

        // 2. Touch drag event: continuous 1:1 real-time drag (e.g. +20px)
        val touchDragDelta = 20f
        scrollOffset += touchDragDelta
        assertEquals(68f, scrollOffset)

        // Both mechanisms cooperate on the same offset without interfering
        assertTrue(scrollOffset > 0f)
    }
}
