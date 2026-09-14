package ma.elaroui.pos.desktop.presentation.pos.main

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import ma.elaroui.pos.shared.domain.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class POSCartStateTest {
    private val product = Product(
        id = 41L,
        categoryId = 7L,
        name = "Produit test",
        priceCentimes = 1_275L,
        taxRateBasisPoints = 2_000
    )

    @Test
    fun `cart projection refreshes immediately when a product is added`() {
        val cart = mutableStateMapOf<Long, Int>()
        val productsById = mapOf(product.id to product)
        val cartItems by derivedStateOf { buildCartItems(cart, productsById) }

        assertTrue(cartItems.isEmpty())

        cart[product.id] = 1

        assertEquals(1, cartItems.size)
        assertSame(product, cartItems.single().first)
        assertEquals(1, cartItems.single().second)
        assertEquals(1_275L, cartItems.single().first.priceCentimes * cartItems.single().second)
    }

    @Test
    fun `repeated mouse or touch callbacks update quantity and total without duplicate lines`() {
        val cart = mutableStateMapOf<Long, Int>()
        val productsById = mapOf(product.id to product)
        val cartItems by derivedStateOf { buildCartItems(cart, productsById) }
        val addProduct = { cart[product.id] = (cart[product.id] ?: 0) + 1 }

        addProduct() // Mouse click.
        addProduct() // Touch tap uses the same callback.

        assertEquals(1, cartItems.size)
        assertEquals(2, cartItems.single().second)
        assertEquals(2_550L, cartItems.sumOf { (item, quantity) -> item.priceCentimes * quantity })

        cart.remove(product.id)
        assertTrue(cartItems.isEmpty())
    }
}
