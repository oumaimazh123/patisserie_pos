package ma.elaroui.pos.domain.shared

import ma.elaroui.pos.domain.model.CartItem
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.Product
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.shared.domain.OrderStatus as SharedOrderStatus
import ma.elaroui.pos.shared.domain.UserRole as SharedUserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedBusinessAdapterTest {
    @Test
    fun androidCartUsesSharedTaxInclusiveCalculation() {
        val product = Product(
            id = 1,
            categoryId = 2,
            name = "Café crème",
            priceCentimes = 2_500,
            tvaRate = 0.10
        )

        val totals = SharedBusinessAdapter.calculateCart(
            listOf(CartItem(product = product, quantity = 2))
        )

        assertEquals(2, totals.itemCount)
        assertEquals(5_000, totals.subtotalCentimes)
        assertEquals(454, totals.taxCentimes)
        assertEquals(5_000, totals.totalCentimes)
    }

    @Test
    fun enumAdaptersKeepExistingAndroidSemantics() {
        assertEquals(SharedUserRole.OWNER, UserRole.OWNER.toShared())
        assertEquals(SharedOrderStatus.CANCELLED, OrderStatus.CANCELLED.toShared())
        assertEquals(1_000, 0.10.toBasisPoints())
    }
}
