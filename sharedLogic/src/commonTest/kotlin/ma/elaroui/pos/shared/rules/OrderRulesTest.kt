package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderRulesTest {
    @Test
    fun lineAmountsAndVatReconcileAfterMixedDiscountsAndRounding() {
        val totals = OrderCalculationRules.calculate(
            listOf(
                OrderLine(1, "Taxed", 10_001, 1, 2_000),
                OrderLine(2, "Reduced VAT", 10_003, 1, 1_000),
                OrderLine(3, "Exempt", 10_007, 1, 0)
            ),
            discountBasisPoints = 333,
            itemDiscountsBasisPoints = mapOf(1L to 1_111, 2L to 2_222)
        )
        assertEquals(totals.totalCentimes, totals.lineAmounts.sumOf { it.amountCentimes })
        assertEquals(totals.taxCentimes, totals.lineAmounts.sumOf { it.taxCentimes })
        assertEquals(0L, totals.lineAmounts[2].taxCentimes)
        assertTrue(totals.lineAmounts[0].taxCentimes > totals.lineAmounts[1].taxCentimes)
    }

    @Test
    fun itemDiscountOnlyReducesTaxOfTheDiscountedProduct() {
        val lines = listOf(
            OrderLine(1, "Taxed cake", 1200, 1, 2000),
            OrderLine(2, "Exempt item", 1000, 1, 0)
        )
        val freeCake = OrderCalculationRules.calculate(lines, itemDiscountsBasisPoints = mapOf(1L to 10000))
        assertEquals(1000L, freeCake.totalCentimes)
        assertEquals(0L, freeCake.taxCentimes)
        val freeExemptItem = OrderCalculationRules.calculate(lines, itemDiscountsBasisPoints = mapOf(2L to 10000))
        assertEquals(1200L, freeExemptItem.totalCentimes)
        assertEquals(200L, freeExemptItem.taxCentimes)
    }

    @Test
    fun calculatesTaxInclusiveSubtotalTaxAndTotal() {
        val totals = OrderCalculationRules.calculate(
            listOf(
                line(price = 2_500, quantity = 2, taxBasisPoints = 1_000),
                line(price = 1_200, quantity = 1, taxBasisPoints = 2_000)
            )
        )

        assertEquals(3, totals.itemCount)
        assertEquals(6_200, totals.subtotalCentimes)
        assertEquals(0, totals.discountCentimes)
        assertEquals(654, totals.taxCentimes)
        assertEquals(6_200, totals.totalCentimes)
    }

    @Test
    fun appliesDiscountAndProportionallyReducesTax() {
        val totals = OrderCalculationRules.calculate(
            listOf(line(price = 11_000, taxBasisPoints = 1_000)),
            discountBasisPoints = 1_000
        )

        assertEquals(11_000, totals.subtotalCentimes)
        assertEquals(1_100, totals.discountCentimes)
        assertEquals(900, totals.taxCentimes)
        assertEquals(9_900, totals.totalCentimes)
    }

    @Test
    fun rejectsInvalidLinesAndDiscounts() {
        assertFailsWith<IllegalArgumentException> {
            OrderCalculationRules.calculate(listOf(line(quantity = 0)))
        }
        assertFailsWith<IllegalArgumentException> {
            OrderCalculationRules.calculate(listOf(line(price = -1)))
        }
        assertFailsWith<IllegalArgumentException> {
            OrderCalculationRules.calculate(listOf(line(taxBasisPoints = 10_001)))
        }
        assertFailsWith<IllegalArgumentException> {
            OrderCalculationRules.calculate(emptyList(), discountBasisPoints = -1)
        }
    }

    @Test
    fun appliesItemDiscountsSeparatelyFromGlobalDiscount() {
        val line1 = OrderLine(1L, "Batbout", 200, 2, 0)
        val line2 = OrderLine(2L, "Croissant", 500, 1, 0)
        val totals = OrderCalculationRules.calculate(
            lines = listOf(line1, line2),
            discountBasisPoints = 1_000,
            itemDiscountsBasisPoints = mapOf(1L to 1_000)
        )
        assertEquals(3, totals.itemCount)
        assertEquals(900, totals.subtotalCentimes)
        assertEquals(126, totals.discountCentimes)
        assertEquals(774, totals.totalCentimes)
    }

    @Test
    fun onlyOpenOrdersCanCompleteOrCancel() {
        assertTrue(OrderTransitionRules.canTransition(OrderStatus.OPEN, OrderStatus.COMPLETED))
        assertTrue(OrderTransitionRules.canTransition(OrderStatus.OPEN, OrderStatus.CANCELLED))
        assertFalse(OrderTransitionRules.canTransition(OrderStatus.OPEN, OrderStatus.OPEN))
        assertFalse(OrderTransitionRules.canTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED))
        assertFalse(OrderTransitionRules.canPay(OrderStatus.CANCELLED))
        assertTrue(OrderTransitionRules.canEdit(OrderStatus.OPEN))
    }

    private fun line(
        price: Long = 1_000,
        quantity: Int = 1,
        taxBasisPoints: Int = 0
    ) = OrderLine(1, "Produit", price, quantity, taxBasisPoints)
}
