package ma.elaroui.pos.domain.shared

import kotlinx.coroutines.flow.map
import ma.elaroui.pos.domain.model.CartItem
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.RegisterSessionStatus
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.repository.ProductRepository as AndroidProductRepository
import ma.elaroui.pos.domain.repository.UserRepository as AndroidUserRepository
import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.rules.OrderCalculationRules
import ma.elaroui.pos.shared.rules.OrderTotals
import kotlin.math.roundToInt
import ma.elaroui.pos.shared.domain.OrderStatus as SharedOrderStatus
import ma.elaroui.pos.shared.domain.PaymentMethod as SharedPaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSessionStatus as SharedRegisterSessionStatus
import ma.elaroui.pos.shared.domain.UserRole as SharedUserRole
import ma.elaroui.pos.shared.domain.Product as SharedProduct
import ma.elaroui.pos.shared.domain.User as SharedUser
import ma.elaroui.pos.shared.domain.ProductRepository as SharedProductRepository
import ma.elaroui.pos.shared.domain.UserRepository as SharedUserRepository

/**
 * Compatibility boundary between the unchanged Android domain model and
 * platform-neutral rules. Room entities and Android framework types never
 * cross this boundary.
 */
object SharedBusinessAdapter {
    fun calculateCart(items: List<CartItem>, discountBasisPoints: Int = 0): OrderTotals =
        calculateLines(items.map { item ->
            OrderLine(
                productId = item.product.id,
                name = item.product.name,
                unitPriceCentimes = item.unitPriceCentimes,
                quantity = item.quantity,
                taxRateBasisPoints = item.product.tvaRate.toBasisPoints()
            )
        }, discountBasisPoints)

    fun calculateLines(lines: List<OrderLine>, discountBasisPoints: Int = 0): OrderTotals =
        OrderCalculationRules.calculate(lines, discountBasisPoints)
}

fun Double.toBasisPoints(): Int = (this * 10_000.0).roundToInt()

fun UserRole.toShared(): SharedUserRole = SharedUserRole.valueOf(name)
fun OrderStatus.toShared(): SharedOrderStatus = SharedOrderStatus.valueOf(name)
fun PaymentMethod.toShared(): SharedPaymentMethod = SharedPaymentMethod.valueOf(name)
fun RegisterSessionStatus.toShared(): SharedRegisterSessionStatus =
    SharedRegisterSessionStatus.valueOf(name)

fun ma.elaroui.pos.domain.model.User.toShared() = SharedUser(
    id = id,
    name = name,
    role = role.toShared(),
    active = active
)

fun ma.elaroui.pos.domain.model.Product.toShared() = SharedProduct(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCentimes = priceCentimes,
    taxRateBasisPoints = tvaRate.toBasisPoints(),
    available = available,
    active = active,
    imagePath = imagePath,
    sku = sku,
    barcode = barcode
)

private fun SharedProduct.toAndroid() = ma.elaroui.pos.domain.model.Product(
    id=id,categoryId=categoryId,name=name,priceCentimes=priceCentimes,
    tvaRate=taxRateBasisPoints/10_000.0,imagePath=imagePath,available=available,active=active,
    sku=sku,barcode=barcode
)

/**
 * Read adapters are deliberately not injected yet. They demonstrate the
 * incremental boundary without replacing Room repositories or Hilt bindings.
 */
class SharedUserRepositoryAdapter(
    private val delegate: AndroidUserRepository
) : SharedUserRepository {
    override suspend fun findById(id: Long): SharedUser? = delegate.getUserById(id)?.toShared()
    override fun observeActive() = delegate.getAllActiveUsers().map { users -> users.map { it.toShared() } }
}

class SharedProductRepositoryAdapter(
    private val delegate: AndroidProductRepository
) : SharedProductRepository {
    override suspend fun findById(id: Long): SharedProduct? = delegate.getProductById(id)?.toShared()
    override fun observeSellable() =
        delegate.getAllAvailableProducts().map { products -> products.map { it.toShared() } }
    override fun observeAll() = delegate.getAllProductsForOwner().map { products -> products.map { it.toShared() } }
    override suspend fun save(product: SharedProduct): Long {
        val android=product.toAndroid()
        return if(product.id==0L) delegate.insertProduct(android) else { delegate.updateProduct(android);product.id }
    }
}
