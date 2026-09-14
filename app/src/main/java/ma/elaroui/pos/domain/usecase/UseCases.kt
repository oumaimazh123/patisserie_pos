package ma.elaroui.pos.domain.usecase

import ma.elaroui.pos.core.exception.*
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AddOrderItemUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository
) {
    suspend operator fun invoke(
        orderId: Long,
        productId: Long,
        quantity: Int = 1,
        note: String? = null
    ): Order {
        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Order #$orderId not found.")

        if (order.status != OrderStatus.OPEN) {
            throw OrderAlreadyClosedException(orderId, order.status.name)
        }

        val product = productRepository.getProductById(productId)
            ?: throw DomainException("Product #$productId not found.")

        if (!product.available) {
            throw ProductNotAvailableException(product.name)
        }

        val lineTotal = product.priceCentimes * quantity
        val newItem = OrderItem(
            orderId = orderId,
            productId = product.id,
            productNameSnapshot = product.name,
            unitPriceSnapshotCentimes = product.priceCentimes,
            tvaRateSnapshot = product.tvaRate,
            quantity = quantity,
            note = note,
            lineTotalCentimes = lineTotal
        )

        orderRepository.insertOrderItem(newItem)

        // Reload order and recalculate subtotal & totals
        val updatedItems = orderRepository.getOrderById(orderId)?.items ?: emptyList()
        val subtotal = updatedItems.sumOf { it.lineTotalCentimes }
        val tvaTotal = updatedItems.sumOf { (it.lineTotalCentimes * it.tvaRateSnapshot).toLong() }
        val total = subtotal

        val updatedOrder = order.copy(
            subtotalCentimes = subtotal,
            tvaTotalCentimes = tvaTotal,
            totalCentimes = total,
            items = updatedItems
        )

        orderRepository.updateOrder(updatedOrder)
        return updatedOrder
    }
}

@Singleton
class RecordPaymentUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(
        orderId: Long,
        method: PaymentMethod,
        amountCentimes: Long,
        receivedAmountCentimes: Long
    ): Payment {
        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Order #$orderId not found.")

        if (order.status != OrderStatus.OPEN) {
            throw OrderAlreadyClosedException(orderId, order.status.name)
        }

        val changeCentimes = if (receivedAmountCentimes > amountCentimes) {
            receivedAmountCentimes - amountCentimes
        } else 0L

        val payment = Payment(
            orderId = orderId,
            registerSessionId = order.registerSessionId,
            method = method,
            amountCentimes = amountCentimes,
            receivedAmountCentimes = receivedAmountCentimes,
            changeAmountCentimes = changeCentimes
        )

        val paymentId = orderRepository.insertPayment(payment)
        return payment.copy(id = paymentId)
    }
}

@Singleton
class CompleteOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(orderId: Long): Order {
        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Order #$orderId not found.")

        if (order.status != OrderStatus.OPEN) {
            throw OrderAlreadyClosedException(orderId, order.status.name)
        }

        val payments = orderRepository.getPaymentsForOrder(orderId)
        val totalPaid = payments.fold(0L) { acc, p -> acc + p.amountCentimes }

        if (totalPaid < order.totalCentimes) {
            throw InsufficientPaymentException(
                total = "${order.totalCentimes / 100.0} DH",
                paid = "${totalPaid / 100.0} DH"
            )
        }

        val completedOrder = order.copy(
            status = OrderStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            payments = payments
        )

        orderRepository.updateOrder(completedOrder)

        // Free table if Dine-In
        if (completedOrder.tableId != null) {
            tableRepository.updateTableStatus(completedOrder.tableId, TableStatus.AVAILABLE)
        }

        return completedOrder
    }
}

@Singleton
class VerifyOwnerPinUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Verifies the given PIN against the stored owner account.
     * Returns the owner [User] if the PIN is correct, or null if invalid.
     */
    suspend operator fun invoke(ownerPin: String): User? {
        val owner = userRepository.getOwnerUser() ?: return null
        return if (SecurityUtils.verifyPin(ownerPin, owner.pinHash)) owner else null
    }
}

@Singleton
class CancelOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository,
    private val verifyOwnerPinUseCase: VerifyOwnerPinUseCase
) {
    suspend operator fun invoke(orderId: Long, ownerPin: String, reason: String): Order {
        if (reason.isBlank()) {
            throw CancellationReasonRequiredException()
        }

        val ownerUser = verifyOwnerPinUseCase(ownerPin)
        if (ownerUser == null) {
            throw InvalidOwnerPinException()
        }

        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Order #$orderId not found.")

        if (order.status != OrderStatus.OPEN) {
            throw OrderAlreadyClosedException(orderId, order.status.name)
        }

        val cancelledOrder = order.copy(
            status = OrderStatus.CANCELLED,
            cancelledAt = System.currentTimeMillis(),
            cancellationReason = reason
        )

        orderRepository.updateOrder(cancelledOrder)

        // Free table if Dine-In
        if (cancelledOrder.tableId != null) {
            tableRepository.updateTableStatus(cancelledOrder.tableId, TableStatus.AVAILABLE)
        }

        return cancelledOrder
    }
}
