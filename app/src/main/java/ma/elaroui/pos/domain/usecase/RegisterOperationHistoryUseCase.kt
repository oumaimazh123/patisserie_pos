package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.first
import ma.elaroui.pos.domain.model.RegisterOperationType
import ma.elaroui.pos.domain.model.RegisterSessionOperation
import ma.elaroui.pos.domain.repository.CashMovementRepository
import ma.elaroui.pos.domain.repository.OrderRepository
import ma.elaroui.pos.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetRegisterSessionOperationsUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(sessionId: Long): List<RegisterSessionOperation> {
        val orders = orderRepository.getOrdersBySessionId(sessionId).first()
        val movements = cashMovementRepository.getCashMovementsListForSession(sessionId)
        val userNames = mutableMapOf<Long, String>()

        suspend fun userName(userId: Long): String {
            userNames[userId]?.let { return it }
            return (userRepository.getUserById(userId)?.name ?: "Utilisateur #$userId")
                .also { userNames[userId] = it }
        }

        val operations = buildList {
            orders.forEach { order ->
                add(
                    RegisterSessionOperation(
                        stableId = "order-created-${order.id}",
                        sessionId = sessionId,
                        type = RegisterOperationType.ORDER_CREATED,
                        occurredAt = order.createdAt,
                        userId = order.cashierId,
                        userName = userName(order.cashierId),
                        description = "Commande créée",
                        reference = order.orderNumber,
                        amountCentimes = order.totalCentimes
                    )
                )
                order.completedAt?.let { completedAt ->
                    val userId = order.completedByUserId
                        ?: order.payments.firstOrNull()?.createdByUserId
                        ?: order.cashierId
                    add(
                        RegisterSessionOperation(
                            stableId = "sale-completed-${order.id}",
                            sessionId = sessionId,
                            type = RegisterOperationType.SALE_COMPLETED,
                            occurredAt = completedAt,
                            userId = userId,
                            userName = userName(userId),
                            description = "Vente encaissée",
                            reference = order.orderNumber,
                            amountCentimes = order.totalCentimes
                        )
                    )
                }
                order.cancelledAt?.let { cancelledAt ->
                    val userId = order.cancelledByUserId ?: order.cashierId
                    add(
                        RegisterSessionOperation(
                            stableId = "order-cancelled-${order.id}",
                            sessionId = sessionId,
                            type = RegisterOperationType.ORDER_CANCELLED,
                            occurredAt = cancelledAt,
                            userId = userId,
                            userName = userName(userId),
                            description = order.cancellationReason
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "Commande annulée : $it" }
                                ?: "Commande annulée",
                            reference = order.orderNumber,
                            amountCentimes = order.totalCentimes
                        )
                    )
                }
            }
            movements.forEach { movement ->
                val type = when (movement.type) {
                    ma.elaroui.pos.domain.model.CashMovementType.CASH_IN ->
                        RegisterOperationType.CASH_IN
                    ma.elaroui.pos.domain.model.CashMovementType.CASH_OUT ->
                        RegisterOperationType.CASH_OUT
                }
                add(
                    RegisterSessionOperation(
                        stableId = "cash-movement-${movement.id}",
                        sessionId = sessionId,
                        type = type,
                        occurredAt = movement.createdAt,
                        userId = movement.createdByUserId,
                        userName = userName(movement.createdByUserId),
                        description = movement.reason,
                        amountCentimes = movement.amountCentimes
                    )
                )
            }
        }
        return operations.sortedByDescending { it.occurredAt }
    }
}
