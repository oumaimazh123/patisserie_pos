package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderStatus

data class OrderTotals(
    val itemCount: Int,
    val subtotalCentimes: Long,
    val discountCentimes: Long,
    val taxCentimes: Long,
    val totalCentimes: Long
)

object OrderCalculationRules {
    /**
     * Prices are tax-inclusive, matching the Android POS. Discount is expressed
     * in basis points (10_000 = 100%) and defaults to zero.
     */
    fun calculate(
        lines: List<OrderLine>,
        discountBasisPoints: Int = 0,
        itemDiscountsBasisPoints: Map<Long, Int> = emptyMap()
    ): OrderTotals {
        require(discountBasisPoints in 0..10_000) { "Discount must be between 0% and 100%." }
        itemDiscountsBasisPoints.values.forEach {
            require(it in 0..10_000) { "Item discount must be between 0% and 100%." }
        }

        var itemCount = 0
        var subtotal = 0L
        var totalItemDiscounts = 0L
        var taxBeforeDiscount = 0L
        lines.forEach { line ->
            require(line.quantity > 0) { "Quantity must be greater than zero." }
            require(line.unitPriceCentimes >= 0) { "Unit price cannot be negative." }
            require(line.taxRateBasisPoints in 0..10_000) { "Tax rate must be between 0% and 100%." }
            val lineTotal = MathRules.multiplyExact(line.unitPriceCentimes, line.quantity.toLong())
            itemCount = MathRules.addExact(itemCount, line.quantity)
            subtotal = MathRules.addExact(subtotal, lineTotal)

            val itemDiscBps = (itemDiscountsBasisPoints[line.productId] ?: 0).coerceIn(0, 10_000)
            val lineDisc = MathRules.divideHalfUp(
                MathRules.multiplyExact(lineTotal, itemDiscBps.toLong()),
                10_000L
            )
            totalItemDiscounts = MathRules.addExact(totalItemDiscounts, lineDisc)

            val tax = MathRules.multiplyExact(lineTotal - lineDisc, line.taxRateBasisPoints.toLong()) /
                (10_000L + line.taxRateBasisPoints)
            taxBeforeDiscount = MathRules.addExact(taxBeforeDiscount, tax)
        }

        val subtotalAfterItemDiscounts = MathRules.subtractExact(subtotal, totalItemDiscounts)
        val globalDiscount = MathRules.divideHalfUp(
            MathRules.multiplyExact(subtotalAfterItemDiscounts, discountBasisPoints.toLong()),
            10_000L
        )
        val totalDiscount = MathRules.addExact(totalItemDiscounts, globalDiscount)
        val total = MathRules.subtractExact(subtotal, totalDiscount)
        val tax = if (subtotalAfterItemDiscounts == 0L) 0L else {
            MathRules.divideHalfUp(MathRules.multiplyExact(taxBeforeDiscount, total), subtotalAfterItemDiscounts)
        }
        return OrderTotals(itemCount, subtotal, totalDiscount, tax, total)
    }
}

object OrderTransitionRules {
    fun canTransition(from: OrderStatus, to: OrderStatus): Boolean = when (from) {
        OrderStatus.OPEN -> to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED
        OrderStatus.COMPLETED, OrderStatus.CANCELLED -> false
    }

    fun requireTransition(from: OrderStatus, to: OrderStatus) {
        require(canTransition(from, to)) { "Order cannot transition from $from to $to." }
    }

    fun canEdit(status: OrderStatus): Boolean = status == OrderStatus.OPEN
    fun canPay(status: OrderStatus): Boolean = status == OrderStatus.OPEN
    fun canCancel(status: OrderStatus): Boolean = status == OrderStatus.OPEN
}
