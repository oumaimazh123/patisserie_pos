package ma.elaroui.pos.domain.usecase

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ma.elaroui.pos.core.exception.*
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.shared.toShared
import ma.elaroui.pos.shared.rules.PaymentRules
import ma.elaroui.pos.shared.rules.PaymentValidationError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompleteOrderWithPaymentUseCase @Inject constructor(
    private val database: POSDatabase,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val tableRepository: TableRepository,
    private val registerRepository: RegisterRepository,
    private val auditRepository: AuditRepository,
    private val userRepository: UserRepository,
    private val setupPreferences: SetupPreferences,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(
        orderId: Long,
        paymentMethod: PaymentMethod,
        amountCentimes: Long,
        receivedAmountCentimes: Long,
        submissionToken: String,
        externalReference: String? = null,
        note: String? = null,
        buyerCompanyName: String? = null,
        buyerAddress: String? = null,
        buyerIce: String? = null
    ): PaymentResult {
        // 1. Authorization check
        val currentUser = sessionManager.currentUser
            ?: throw DomainException("Aucun utilisateur connecté.")
        val sessionId = currentUser.currentRegisterSessionId
            ?: throw DomainException("Aucune session de caisse ouverte pour enregistrer le paiement.")
        val sharedSession = registerRepository.getSessionById(sessionId)
            ?: throw DomainException("Session de caisse introuvable.")
        if (sharedSession.status != RegisterSessionStatus.OPEN) {
            throw DomainException("La session de caisse partagée est fermée.")
        }
        val actingUser = userRepository.getUserById(currentUser.userId)
            ?: throw DomainException("Utilisateur introuvable.")
        if (!actingUser.active) {
            throw DomainException("Cet utilisateur est désactivé.")
        }

        // 2. Check idempotency token
        val existingPayment = paymentRepository.getPaymentByOrderAndToken(orderId, submissionToken)
        if (existingPayment != null) {
            val completedOrder = orderRepository.getOrderById(orderId)
                ?: throw DomainException("Commande introuvable.")
            val receiptData = buildReceiptData(completedOrder, existingPayment, currentUser.userName, sessionId)
            return PaymentResult(
                payment = existingPayment,
                completedOrder = completedOrder,
                changeAmountCentimes = existingPayment.changeAmountCentimes,
                receiptData = receiptData
            )
        }

        // Execute atomically in a Room Transaction
        return database.withTransaction {
            // 3. Load order & verify status
            val order = orderRepository.getOrderById(orderId)
                ?: throw DomainException("Commande #$orderId introuvable.")

            if (order.status == OrderStatus.COMPLETED) {
                throw OrderAlreadyCompletedException(orderId)
            }
            if (order.status == OrderStatus.CANCELLED) {
                throw OrderCancelledException(orderId)
            }
            if (order.registerSessionId != sessionId) {
                throw DomainException("Cette commande appartient à une autre session de caisse.")
            }
            if (order.items.isEmpty()) {
                throw EmptyOrderException()
            }

            // 4. Validate payment inputs
            val orderTotal = order.totalCentimes
            val paymentCalculation = PaymentRules.calculate(
                method = paymentMethod.toShared(),
                orderTotalCentimes = orderTotal,
                receivedCentimes = receivedAmountCentimes,
                submittedAmountCentimes = amountCentimes,
                submissionToken = submissionToken
            )
            when (paymentCalculation.error) {
                PaymentValidationError.INSUFFICIENT_CASH -> throw CashReceivedTooLowException(
                    total = "${orderTotal / 100.0} DH",
                    received = "${receivedAmountCentimes / 100.0} DH"
                )
                null -> Unit
                else -> throw InvalidPaymentAmountException()
            }
            val changeCentimes = paymentCalculation.changeCentimes

            // 5. Create Payment record
            val payment = Payment(
                orderId = orderId,
                registerSessionId = sessionId,
                method = paymentMethod,
                amountCentimes = orderTotal,
                receivedAmountCentimes = receivedAmountCentimes,
                changeAmountCentimes = changeCentimes,
                createdByUserId = currentUser.userId,
                status = PaymentStatus.COMPLETED,
                externalReference = externalReference,
                submissionToken = submissionToken,
                note = note,
                createdAt = System.currentTimeMillis()
            )

            val paymentId = paymentRepository.insertPayment(payment)
            val savedPayment = payment.copy(id = paymentId)

            // 6. Mark order COMPLETED
            val completedOrder = order.copy(
                status = OrderStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                completedByUserId = currentUser.userId,
                buyerCompanyName = buyerCompanyName?.trim()?.takeIf { it.isNotBlank() },
                buyerAddress = buyerAddress?.trim()?.takeIf { it.isNotBlank() },
                buyerIce = buyerIce?.trim()?.takeIf { it.isNotBlank() },
                payments = listOf(savedPayment)
            )
            orderRepository.updateOrder(completedOrder)

            auditRepository.recordLog(
                AuditLog(
                    actionType = AuditAction.PAYMENT_COMPLETED,
                    actingUserId = currentUser.userId,
                    entityType = "ORDER",
                    entityId = orderId,
                    details = "Paiement ${paymentMethod.name}: ${orderTotal} centimes; reçu: " +
                        "$receivedAmountCentimes; rendu: $changeCentimes; session: $sessionId"
                )
            )

            // 7. Release table if DINE_IN
            if (completedOrder.type == OrderType.DINE_IN && completedOrder.tableId != null) {
                tableRepository.updateTableStatus(completedOrder.tableId, TableStatus.AVAILABLE)
            }

            // 8. Generate Receipt Data
            val receiptData = buildReceiptData(completedOrder, savedPayment, currentUser.userName, sessionId)

            PaymentResult(
                payment = savedPayment,
                completedOrder = completedOrder,
                changeAmountCentimes = changeCentimes,
                receiptData = receiptData
            )
        }
    }

    private suspend fun buildReceiptData(order: Order, payment: Payment, cashierName: String, sessionId: Long): ReceiptData {
        val registerSession = registerRepository.getSessionById(sessionId)
        val register = registerSession?.let { registerRepository.getRegisterById(it.registerId) }
        val table = order.tableId?.let { tableRepository.getTableById(it) }

        return ReceiptData(
            restaurantName = setupPreferences.restaurantName,
            restaurantPhone = setupPreferences.restaurantPhone,
            restaurantAddress = setupPreferences.restaurantAddress,
            restaurantLogoUri = setupPreferences.restaurantLogoUri.takeIf { it.isNotBlank() },
            sellerIce = setupPreferences.sellerIce.takeIf { it.isNotBlank() },
            sellerTaxId = setupPreferences.sellerTaxId.takeIf { it.isNotBlank() },
            sellerCommercialRegister = setupPreferences.sellerCommercialRegister.takeIf { it.isNotBlank() },
            sellerPatente = setupPreferences.sellerPatente.takeIf { it.isNotBlank() },
            buyerCompanyName = order.buyerCompanyName,
            buyerAddress = order.buyerAddress,
            buyerIce = order.buyerIce,
            wifiName = setupPreferences.wifiName.takeIf { it.isNotBlank() },
            wifiCode = setupPreferences.wifiCode.takeIf { it.isNotBlank() },
            orderNumber = order.orderNumber,
            orderType = order.type,
            tableName = table?.name,
            registerName = register?.name ?: "Caisse Principale",
            cashierName = cashierName,
            orderCreatedAt = order.createdAt,
            paymentAt = payment.createdAt,
            items = order.items.map {
                ReceiptItem(
                    name = it.productNameSnapshot,
                    quantity = it.quantity,
                    unitPriceCentimes = it.unitPriceSnapshotCentimes,
                    lineTotalCentimes = it.lineTotalCentimes
                )
            },
            subtotalCentimes = order.subtotalCentimes,
            totalCentimes = order.totalCentimes,
            paymentMethod = payment.method,
            receivedAmountCentimes = if (payment.method == PaymentMethod.CASH) payment.receivedAmountCentimes else null,
            changeAmountCentimes = if (payment.method == PaymentMethod.CASH) payment.changeAmountCentimes else null,
            externalReference = payment.externalReference,
            currency = setupPreferences.currency
        )
    }
}

@Singleton
class ObserveCompletedSalesUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    operator fun invoke(sessionId: Long? = null): Flow<List<Order>> {
        return if (sessionId != null) {
            orderRepository.getCompletedOrdersForSession(sessionId)
        } else {
            orderRepository.getAllCompletedOrders()
        }
    }
}

@Singleton
class GetCompletedSaleDetailsUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val tableRepository: TableRepository,
    private val registerRepository: RegisterRepository,
    private val userRepository: UserRepository,
    private val setupPreferences: SetupPreferences
) {
    suspend operator fun invoke(orderId: Long): SaleDetails? {
        val order = orderRepository.getOrderById(orderId) ?: return null
        val cashierName = userRepository.getUserById(order.cashierId)?.name ?: "Utilisateur inconnu"
        val registerSession = registerRepository.getSessionById(order.registerSessionId)
        val registerName = registerSession
            ?.let { registerRepository.getRegisterById(it.registerId) }
            ?.name
            ?: "Caisse inconnue"
        val tableName = order.tableId
            ?.let { tableRepository.getTableById(it) }
            ?.name

        if (order.status == OrderStatus.CANCELLED) {
            return SaleDetails(
                order = order,
                receiptData = null,
                cashierName = cashierName,
                registerName = registerName,
                tableName = tableName
            )
        }

        val payments = paymentRepository.getPaymentsForOrder(orderId)
        val payment = payments.firstOrNull() ?: Payment(
            orderId = orderId,
            registerSessionId = order.registerSessionId,
            method = PaymentMethod.CASH,
            amountCentimes = order.totalCentimes,
            receivedAmountCentimes = order.totalCentimes,
            changeAmountCentimes = 0L
        )

        val receiptData = ReceiptData(
            restaurantName = setupPreferences.restaurantName,
            restaurantPhone = setupPreferences.restaurantPhone,
            restaurantAddress = setupPreferences.restaurantAddress,
            restaurantLogoUri = setupPreferences.restaurantLogoUri.takeIf { it.isNotBlank() },
            sellerIce = setupPreferences.sellerIce.takeIf { it.isNotBlank() },
            sellerTaxId = setupPreferences.sellerTaxId.takeIf { it.isNotBlank() },
            sellerCommercialRegister = setupPreferences.sellerCommercialRegister.takeIf { it.isNotBlank() },
            sellerPatente = setupPreferences.sellerPatente.takeIf { it.isNotBlank() },
            buyerCompanyName = order.buyerCompanyName,
            buyerAddress = order.buyerAddress,
            buyerIce = order.buyerIce,
            wifiName = setupPreferences.wifiName.takeIf { it.isNotBlank() },
            wifiCode = setupPreferences.wifiCode.takeIf { it.isNotBlank() },
            orderNumber = order.orderNumber,
            orderType = order.type,
            tableName = tableName,
            registerName = registerName,
            cashierName = cashierName,
            orderCreatedAt = order.createdAt,
            paymentAt = payment.createdAt,
            items = order.items.map {
                ReceiptItem(
                    name = it.productNameSnapshot,
                    quantity = it.quantity,
                    unitPriceCentimes = it.unitPriceSnapshotCentimes,
                    lineTotalCentimes = it.lineTotalCentimes
                )
            },
            subtotalCentimes = order.subtotalCentimes,
            totalCentimes = order.totalCentimes,
            paymentMethod = payment.method,
            receivedAmountCentimes = if (payment.method == PaymentMethod.CASH) payment.receivedAmountCentimes else null,
            changeAmountCentimes = if (payment.method == PaymentMethod.CASH) payment.changeAmountCentimes else null,
            externalReference = payment.externalReference,
            currency = setupPreferences.currency
        )

        return SaleDetails(
            order = order,
            receiptData = receiptData,
            cashierName = cashierName,
            registerName = registerName,
            tableName = tableName
        )
    }
}
