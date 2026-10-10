package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderStatus

data class OrderTotals(
    val itemCount: Int,
    val subtotalCentimes: Long,
    val discountCentimes: Long,
    val taxCentimes: Long,
    val totalCentimes: Long,
    val lineAmounts: List<RecognizedLineAmounts> = emptyList()
)

data class RecognizedLineAmounts(val amountCentimes: Long, val taxCentimes: Long)

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
        val afterItemDiscount = mutableListOf<Pair<Long, Long>>()
        val taxRates = mutableListOf<Int>()
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
            afterItemDiscount += line.productId to (lineTotal - lineDisc)
            taxRates += line.taxRateBasisPoints
        }

        val subtotalAfterItemDiscounts = MathRules.subtractExact(subtotal, totalItemDiscounts)
        val globalDiscount = MathRules.divideHalfUp(
            MathRules.multiplyExact(subtotalAfterItemDiscounts, discountBasisPoints.toLong()),
            10_000L
        )
        val totalDiscount = MathRules.addExact(totalItemDiscounts, globalDiscount)
        val total = MathRules.subtractExact(subtotal, totalDiscount)
        val globalShares = allocate(globalDiscount, afterItemDiscount.map { it.second })
        val lineAmounts = afterItemDiscount.mapIndexed { index, (_, amount) ->
            val net = amount - globalShares[index]
            val rate = taxRates[index]
            val tax = MathRules.multiplyExact(net, rate.toLong()) / (10_000L + rate)
            RecognizedLineAmounts(net, tax)
        }
        val tax = lineAmounts.fold(0L) { sum, line -> MathRules.addExact(sum, line.taxCentimes) }
        return OrderTotals(itemCount, subtotal, totalDiscount, tax, total, lineAmounts)
    }

    /** Largest remainders keep the allocated centimes equal to the order discount. */
    private fun allocate(amount: Long, weights: List<Long>): List<Long> {
        val sum = weights.fold(0L, MathRules::addExact)
        if (sum == 0L) return weights.map { 0L }
        val base = weights.map { MathRules.multiplyExact(amount, it) / sum }.toMutableList()
        var remainder = amount - base.sum()
        val byRemainder = weights.indices.sortedWith(
            compareByDescending<Int> { MathRules.multiplyExact(amount, weights[it]) % sum }.thenBy { it }
        )
        for (index in byRemainder) {
            if (remainder == 0L) break
            base[index]++
            remainder--
        }
        return base
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
