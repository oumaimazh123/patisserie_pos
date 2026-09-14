package ma.elaroui.pos.domain.model

enum class CashMovementType {
    CASH_IN,
    CASH_OUT
}

data class CashMovement(
    val id: Long = 0,
    val registerSessionId: Long,
    val type: CashMovementType,
    val amountCentimes: Long,
    val reason: String,
    val createdByUserId: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class RegisterSession(
    val id: Long = 0,
    val registerId: Long,
    val cashierId: Long,
    val openedByUserId: Long = cashierId,
    val openedAt: Long = System.currentTimeMillis(),
    val openingCashCentimes: Long,
    val closedByUserId: Long? = null,
    val closedAt: Long? = null,
    val expectedCashCentimes: Long? = null,
    val countedCashCentimes: Long? = null,
    val differenceCentimes: Long? = null,
    val closingNote: String? = null,
    val ownerApprovedDifference: Boolean = false,
    val approvedByOwnerId: Long? = null,
    val status: RegisterSessionStatus = RegisterSessionStatus.OPEN
)

data class OrderItem(
    val id: Long = 0,
    val orderId: Long = 0,
    val productId: Long,
    val productNameSnapshot: String,
    val categoryIdSnapshot: Long? = null,
    val categoryNameSnapshot: String? = null,
    val unitPriceSnapshotCentimes: Long,
    val tvaRateSnapshot: Double = 0.10,
    val quantity: Int = 1,
    val note: String? = null,
    val lineTotalCentimes: Long = unitPriceSnapshotCentimes * quantity,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Payment(
    val id: Long = 0,
    val orderId: Long,
    val registerSessionId: Long,
    val method: PaymentMethod,
    val amountCentimes: Long,
    val receivedAmountCentimes: Long,
    val changeAmountCentimes: Long,
    val createdByUserId: Long = 0L,
    val status: PaymentStatus = PaymentStatus.COMPLETED,
    val externalReference: String? = null,
    val submissionToken: String = "",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Order(
    val id: Long = 0,
    val orderNumber: String,
    val type: OrderType,
    val tableId: Long? = null,
    val cashierId: Long,
    val registerSessionId: Long,
    val status: OrderStatus = OrderStatus.OPEN,
    val subtotalCentimes: Long = 0L,
    val discountCentimes: Long = 0L,
    val tvaTotalCentimes: Long = 0L,
    val totalCentimes: Long = 0L,
    val buyerCompanyName: String? = null,
    val buyerAddress: String? = null,
    val buyerIce: String? = null,
    val items: List<OrderItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val completedByUserId: Long? = null,
    val cancelledAt: Long? = null,
    val cancelledByUserId: Long? = null,
    val cancellationReason: String? = null,
    val approvedByOwnerId: Long? = null,
    val version: Int = 1
)

/**
 * Read-only projection used by the global sales history.
 *
 * A register session ID is not a register ID, so the resolved register is kept
 * alongside the order to prevent presentation code from making that mistake.
 */
data class SalesHistoryEntry(
    val order: Order,
    val cashierName: String,
    val registerId: Long?,
    val registerName: String
)

data class SaleDetails(
    val order: Order,
    val receiptData: ReceiptData?,
    val cashierName: String,
    val registerName: String,
    val tableName: String?
)

enum class RegisterOperationType {
    ORDER_CREATED,
    SALE_COMPLETED,
    ORDER_CANCELLED,
    CASH_IN,
    CASH_OUT
}

data class RegisterSessionOperation(
    val stableId: String,
    val sessionId: Long,
    val type: RegisterOperationType,
    val occurredAt: Long,
    val userId: Long,
    val userName: String,
    val description: String,
    val reference: String? = null,
    val amountCentimes: Long? = null
)
