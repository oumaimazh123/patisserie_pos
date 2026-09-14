package ma.elaroui.pos.domain.model

import ma.elaroui.pos.domain.shared.SharedBusinessAdapter

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val note: String? = null,
    val unitPriceCentimes: Long = product.priceCentimes
) {
    val lineTotalCentimes: Long get() =
        SharedBusinessAdapter.calculateCart(listOf(this)).subtotalCentimes
    val tvaRate: Double get() = product.tvaRate
    val tvaAmountCentimes: Long get() =
        SharedBusinessAdapter.calculateCart(listOf(this)).taxCentimes
}

data class CartState(
    val items: List<CartItem> = emptyList(),
    val discountBasisPoints: Int = 0,
    /** New sales are always generic counter sales. Legacy order types remain readable. */
    val orderType: OrderType = OrderType.COUNTER,
    val selectedTable: RestaurantTable? = null
) {
    private val totals get() = SharedBusinessAdapter.calculateCart(items, discountBasisPoints)
    val totalItemsCount: Int get() = totals.itemCount
    val subtotalCentimes: Long get() = totals.subtotalCentimes
    val discountCentimes: Long get() = totals.discountCentimes
    val tvaTotalCentimes: Long get() = totals.taxCentimes
    val totalCentimes: Long get() = totals.totalCentimes
}
