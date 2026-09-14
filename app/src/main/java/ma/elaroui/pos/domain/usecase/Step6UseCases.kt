package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import ma.elaroui.pos.core.exception.*
import ma.elaroui.pos.core.util.OrderNumberGenerator
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.shared.SharedBusinessAdapter
import ma.elaroui.pos.domain.shared.toBasisPoints
import ma.elaroui.pos.domain.shared.toShared
import ma.elaroui.pos.shared.domain.OrderLine as SharedOrderLine
import ma.elaroui.pos.shared.rules.OrderTransitionRules
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateOrderNumberUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(): String {
        val datePrefix = OrderNumberGenerator.generateDatePrefix()
        val currentCount = orderRepository.getDailyOrderCount(datePrefix)
        return OrderNumberGenerator.formatOrderNumber(datePrefix, currentCount + 1)
    }
}

@Singleton
class CreateOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val registerRepository: RegisterRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val tableRepository: TableRepository,
    private val generateOrderNumberUseCase: GenerateOrderNumberUseCase
) {
    suspend operator fun invoke(
        type: OrderType,
        tableId: Long?,
        cartItems: List<CartItem>,
        discountBasisPoints: Int = 0,
        currentUser: User
    ): Order {
        if (cartItems.isEmpty()) {
            throw DomainException("Impossible de créer une commande vide.")
        }
        if (!currentUser.active) {
            throw DomainException("Cet utilisateur est désactivé.")
        }

        // Shared offline drawer: every authenticated user works in the same open shift.
        val activeSession = registerRepository.getAnyActiveSession()
            ?: throw DomainException("Aucune session de caisse partagée n'est ouverte.")

        if (activeSession.status != RegisterSessionStatus.OPEN) {
            throw DomainException("La session de caisse est fermée.")
        }

        var table: RestaurantTable? = null
        if (type == OrderType.DINE_IN) {
            if (tableId != null) {
                table = tableRepository.getTableById(tableId)
                    ?: throw DomainException("Table introuvable.")

                if (!table.active) {
                    throw DomainException("La table '${table.name}' est désactivée.")
                }
                if (table.status != TableStatus.AVAILABLE) {
                    throw TableOccupiedException(table.name)
                }
                val openOrdersOnTable = orderRepository.getOpenOrderCountForTable(tableId)
                if (openOrdersOnTable > 0) {
                    throw DomainException("La table '${table.name}' possède déjà une commande ouverte.")
                }
            }
        } else {
            if (tableId != null) {
                throw DomainException("Une commande à emporter ou au comptoir ne doit pas avoir de table attribuée.")
            }
        }

        // Validate products and compute line snapshots
        val orderItemsToCreate = mutableListOf<OrderItem>()
        val calculationLines = mutableListOf<SharedOrderLine>()

        for (item in cartItems) {
            val freshProduct = productRepository.getProductById(item.product.id)
                ?: throw DomainException("Produit '${item.product.name}' introuvable.")
            val categorySnapshot = categoryRepository.getCategoryById(freshProduct.categoryId)

            if (!freshProduct.active || !freshProduct.available) {
                throw DomainException("Le produit '${freshProduct.name}' n'est plus disponible à la vente.")
            }

            if (item.quantity <= 0) {
                throw DomainException("La quantité du produit '${freshProduct.name}' doit être supérieure à zéro.")
            }

            val price = freshProduct.priceCentimes
            val calculationLine = SharedOrderLine(
                productId = freshProduct.id,
                name = freshProduct.name,
                unitPriceCentimes = price,
                quantity = item.quantity,
                taxRateBasisPoints = freshProduct.tvaRate.toBasisPoints()
            )
            calculationLines += calculationLine
            val lineTotal = SharedBusinessAdapter.calculateLines(listOf(calculationLine)).totalCentimes

            orderItemsToCreate.add(
                OrderItem(
                    productId = freshProduct.id,
                    productNameSnapshot = freshProduct.name,
                    categoryIdSnapshot = freshProduct.categoryId,
                    categoryNameSnapshot = categorySnapshot?.name,
                    unitPriceSnapshotCentimes = price,
                    tvaRateSnapshot = freshProduct.tvaRate,
                    quantity = item.quantity,
                    note = item.note?.trim()?.takeIf { it.isNotBlank() },
                    lineTotalCentimes = lineTotal
                )
            )
        }

        val totals = SharedBusinessAdapter.calculateLines(calculationLines, discountBasisPoints)
        val orderNumber = generateOrderNumberUseCase()

        val newOrder = Order(
            orderNumber = orderNumber,
            type = type,
            tableId = table?.id,
            cashierId = currentUser.id,
            registerSessionId = activeSession.id,
            status = OrderStatus.OPEN,
            subtotalCentimes = totals.subtotalCentimes,
            discountCentimes = totals.discountCentimes,
            tvaTotalCentimes = totals.taxCentimes,
            totalCentimes = totals.totalCentimes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Insert Order
        val createdOrderId = orderRepository.insertOrder(newOrder)

        // Insert Order Items
        for (item in orderItemsToCreate) {
            orderRepository.insertOrderItem(item.copy(orderId = createdOrderId))
        }

        // Update Table Status if DINE_IN
        table?.let {
            tableRepository.updateTableStatus(it.id, TableStatus.OCCUPIED)
        }

        return orderRepository.getOrderById(createdOrderId)
            ?: throw DomainException("Erreur de création de la commande.")
    }
}

@Singleton
class ObserveActiveOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    operator fun invoke(): Flow<List<Order>> {
        return orderRepository.getActiveOrdersForList()
    }
}

@Singleton
class GetOrderDetailsUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(orderId: Long): Order? {
        return orderRepository.getOrderById(orderId)
    }
}

@Singleton
class UpdateOpenOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val registerRepository: RegisterRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        orderId: Long,
        updatedCartItems: List<CartItem>,
        discountBasisPoints: Int = 0,
        currentUser: User
    ): Order {
        val existingOrder = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Commande #$orderId introuvable.")

        if (!OrderTransitionRules.canEdit(existingOrder.status.toShared())) {
            throw DomainException("La commande ${existingOrder.orderNumber} est ${existingOrder.status.name} et ne peut plus être modifiée.")
        }

        if (!currentUser.active) {
            throw DomainException("Cet utilisateur est désactivé.")
        }
        val activeSession = registerRepository.getAnyActiveSession()
            ?: throw DomainException("Aucune session de caisse partagée n'est ouverte.")

        if (activeSession.status != RegisterSessionStatus.OPEN) {
            throw DomainException("La session de caisse est fermée.")
        }
        if (existingOrder.registerSessionId != activeSession.id) {
            throw DomainException("Cette commande appartient à une autre session de caisse.")
        }

        if (updatedCartItems.isEmpty()) {
            throw DomainException("Une commande ne peut pas être vidée complètement sans être annulée.")
        }

        val calculationLines = mutableListOf<SharedOrderLine>()
        val newItems = mutableListOf<OrderItem>()

        for (item in updatedCartItems) {
            val freshProduct = productRepository.getProductById(item.product.id)
                ?: throw DomainException("Produit '${item.product.name}' introuvable.")
            val categorySnapshot = categoryRepository.getCategoryById(freshProduct.categoryId)

            if (!freshProduct.active || !freshProduct.available) {
                throw DomainException("Le produit '${freshProduct.name}' n'est plus disponible.")
            }

            // Use existing snapshot price if item existed, else use fresh product price
            val existingItem = existingOrder.items.find { it.productId == freshProduct.id }
            val unitPrice = existingItem?.unitPriceSnapshotCentimes ?: freshProduct.priceCentimes
            val calculationLine = SharedOrderLine(
                productId = freshProduct.id,
                name = freshProduct.name,
                unitPriceCentimes = unitPrice,
                quantity = item.quantity,
                taxRateBasisPoints = freshProduct.tvaRate.toBasisPoints()
            )
            calculationLines += calculationLine
            val lineTotal = SharedBusinessAdapter.calculateLines(listOf(calculationLine)).totalCentimes

            newItems.add(
                OrderItem(
                    id = existingItem?.id ?: 0L,
                    orderId = orderId,
                    productId = freshProduct.id,
                    productNameSnapshot = existingItem?.productNameSnapshot ?: freshProduct.name,
                    categoryIdSnapshot = existingItem?.categoryIdSnapshot ?: freshProduct.categoryId,
                    categoryNameSnapshot = existingItem?.categoryNameSnapshot ?: categorySnapshot?.name,
                    unitPriceSnapshotCentimes = unitPrice,
                    tvaRateSnapshot = freshProduct.tvaRate,
                    quantity = item.quantity,
                    note = item.note?.trim()?.takeIf { it.isNotBlank() },
                    lineTotalCentimes = lineTotal
                )
            )
        }

        val totals = SharedBusinessAdapter.calculateLines(calculationLines, discountBasisPoints)
        val updatedOrder = existingOrder.copy(
            subtotalCentimes = totals.subtotalCentimes,
            discountCentimes = totals.discountCentimes,
            tvaTotalCentimes = totals.taxCentimes,
            totalCentimes = totals.totalCentimes,
            updatedAt = System.currentTimeMillis(),
            version = existingOrder.version + 1
        )

        // Save Order & Items
        orderRepository.updateOrder(updatedOrder)

        // Replace Items
        val currentItems = orderRepository.getOrderItems(orderId)
        for (oldItem in currentItems) {
            if (newItems.none { it.id == oldItem.id }) {
                orderRepository.deleteOrderItem(oldItem.id)
            }
        }
        for (newItem in newItems) {
            if (newItem.id == 0L) {
                orderRepository.insertOrderItem(newItem)
            } else {
                orderRepository.updateOrderItem(newItem)
            }
        }

        return orderRepository.getOrderById(orderId)!!
    }
}

@Singleton
class MoveDineInOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(
        orderId: Long,
        destinationTableId: Long,
        currentUser: User
    ) {
        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Commande introuvable.")

        if (order.status != OrderStatus.OPEN) {
            throw DomainException("Seules les commandes ouvertes peuvent être déplacées.")
        }
        if (order.type != OrderType.DINE_IN) {
            throw DomainException("Seules les commandes sur place (Dine-In) peuvent être associées à une table.")
        }
        if (order.tableId == destinationTableId) {
            throw DomainException("La commande est déjà attribuée à cette table.")
        }

        val destTable = tableRepository.getTableById(destinationTableId)
            ?: throw DomainException("Table de destination introuvable.")

        if (!destTable.active) {
            throw DomainException("La table '${destTable.name}' est désactivée.")
        }
        if (destTable.status != TableStatus.AVAILABLE) {
            throw DomainException("La table '${destTable.name}' est déjà occupée par une autre commande.")
        }

        val oldTableId = order.tableId

        // Transactional Table Move
        orderRepository.updateOrder(
            order.copy(
                tableId = destTable.id,
                updatedAt = System.currentTimeMillis(),
                version = order.version + 1
            )
        )

        oldTableId?.let { tableRepository.updateTableStatus(it, TableStatus.AVAILABLE) }
        tableRepository.updateTableStatus(destTable.id, TableStatus.OCCUPIED)
    }
}

@Singleton
class CancelOpenOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(
        orderId: Long,
        cancellationReason: String?,
        currentUser: User
    ) {
        val trimmedReason = cancellationReason?.trim()?.takeIf { it.isNotBlank() }

        val order = orderRepository.getOrderById(orderId)
            ?: throw DomainException("Commande introuvable.")

        if (order.status != OrderStatus.OPEN) {
            throw DomainException("Cette commande est ${order.status.name} et ne peut pas être annulée.")
        }

        // Cancel order
        val cancelledOrder = order.copy(
            status = OrderStatus.CANCELLED,
            cancelledAt = System.currentTimeMillis(),
            cancelledByUserId = currentUser.id,
            cancellationReason = trimmedReason,
            approvedByOwnerId = null,
            updatedAt = System.currentTimeMillis(),
            version = order.version + 1
        )
        orderRepository.updateOrder(cancelledOrder)

        // Release Table if DINE_IN
        order.tableId?.let { tableId ->
            tableRepository.updateTableStatus(tableId, TableStatus.AVAILABLE)
        }
    }
}

@Singleton
class GetOpenOrdersForRegisterSessionUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(sessionId: Long): Int {
        return orderRepository.getOpenOrderCountForSession(sessionId)
    }
}
