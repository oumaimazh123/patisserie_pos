package ma.elaroui.pos.shared.application

import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.*

object SystemClock : Clock {
    override fun now(): EpochMilliseconds = EpochMilliseconds(System.currentTimeMillis())
}

object TestClock : Clock {
    override fun now(): EpochMilliseconds = EpochMilliseconds(1_700_000_000_000L)
}

sealed class UseCaseResult<out T> {
    data class Success<out T>(val value: T) : UseCaseResult<T>()
    data class Failure(val reason: String) : UseCaseResult<Nothing>()
}

class AuthenticateUser(private val authentication: AuthenticationRepository) {
    suspend fun execute(pin: String): UseCaseResult<User> {
        if (!PinValidationRules.isValid(pin)) {
            return UseCaseResult.Failure("PIN must contain 4 to 6 digits")
        }
        val user = authentication.authenticate(pin) ?: return UseCaseResult.Failure("PIN incorrect")
        return UseCaseResult.Success(user)
    }
}

class CreateOrder(
    private val sessions: RegisterSessionRepository,
    private val categories: CategoryRepository,
    private val products: ProductRepository,
    private val tables: TableRepository,
    private val orders: OrderRepository
) {
    suspend fun execute(
        id: Long,
        number: String,
        type: OrderType,
        sessionId: Long,
        lineItems: List<Pair<Long, Int>>,
        tableId: Long?,
        cashierId: Long,
        discountBasisPoints: Int = 0,
        customerName: String? = null,
        customerPhone: String? = null,
        pickupDateEpochMs: Long? = null,
        preparationStatus: PreparationStatus = PreparationStatus.PENDING,
        customNote: String? = null,
        depositCentimes: Long = 0L,
        itemDiscountsBasisPoints: Map<Long, Int> = emptyMap()
    ): UseCaseResult<Order> {
        val session = sessions.findById(sessionId) ?: return UseCaseResult.Failure("Session not found")
        if (session.status != RegisterSessionStatus.OPEN) return UseCaseResult.Failure("Register session is not open")
        if (session.cashierId != cashierId) return UseCaseResult.Failure("Register session belongs to another user")
        val existingOrder = orders.findById(id)
        if (type != OrderType.DINE_IN && tableId != null) return UseCaseResult.Failure("Only dine-in orders can use a table")
        if (tableId != null) {
            val table=tables.findById(tableId) ?: return UseCaseResult.Failure("Table not found")
            if (!table.active || (table.status != TableStatus.AVAILABLE && existingOrder?.tableId != tableId)) return UseCaseResult.Failure("Table is not available")
        }

        val lines = lineItems.map { (productId, qty) ->
            val product = products.findById(productId) ?: return UseCaseResult.Failure("Product $productId not found")
            if (qty <= 0) return UseCaseResult.Failure("Product quantity must be positive")
            if (!product.active || !product.available) return UseCaseResult.Failure("Product ${product.name} is not available")
            val category = categories.findById(product.categoryId) ?: return UseCaseResult.Failure("Category not found")
            val validation = ProductValidationRules.validate(product.name, product.priceCentimes, product.taxRateBasisPoints, category.active)
            if (validation != null) return UseCaseResult.Failure("Invalid product: $validation")
            OrderLine(
                product.id, product.name, product.priceCentimes, qty, product.taxRateBasisPoints,
                categoryIdSnapshot = category.id,
                categoryNameSnapshot = category.name
            )
        }

        val calculation = OrderCalculationRules.calculate(lines, discountBasisPoints, itemDiscountsBasisPoints)
        val createdAt = existingOrder?.createdAtEpochMilliseconds?.takeIf { it > 0L } ?: System.currentTimeMillis()

        if (type == OrderType.PREORDER) {
            val preorderError = PreorderRules.validatePreorder(
                customerName = customerName,
                pickupDateEpochMs = pickupDateEpochMs,
                orderCreatedAtMs = createdAt,
                itemCount = calculation.itemCount,
                totalCentimes = calculation.totalCentimes,
                depositCentimes = depositCentimes
            )
            if (preorderError != null) return UseCaseResult.Failure("Erreur précommande: $preorderError")
        }

        val order = Order(
            id = id,
            number = number,
            type = type,
            status = OrderStatus.OPEN,
            lines = lines,
            subtotalCentimes = calculation.subtotalCentimes,
            discountCentimes = calculation.discountCentimes,
            taxCentimes = calculation.taxCentimes,
            totalCentimes = calculation.totalCentimes,
            tableId = tableId,
            registerSessionId = sessionId,
            cashierId = cashierId,
            createdAtEpochMilliseconds = createdAt,
            customerName = customerName?.trim()?.takeIf { it.isNotBlank() },
            customerPhone = customerPhone?.trim()?.takeIf { it.isNotBlank() },
            pickupDateEpochMs = pickupDateEpochMs,
            preparationStatus = preparationStatus,
            customNote = customNote?.trim()?.takeIf { it.isNotBlank() },
            depositCentimes = depositCentimes
        )

        val saveResult = orders.save(order)
        val finalOrder = if (order.id == 0L && saveResult > 0L) order.copy(id = saveResult) else order
        return if (saveResult > 0) UseCaseResult.Success(finalOrder) else UseCaseResult.Failure("Failed to save order")
    }
}

class CompletePayment(
    private val sessions: RegisterSessionRepository,
    private val orders: OrderRepository,
    private val payments: PaymentRepository,
    private val transactions: TransactionRunner
) {
    suspend fun execute(
        orderId: Long,
        method: PaymentMethod,
        receivedCentimes: Long?,
        token: String,
        actingCashierId: Long? = null
    ): UseCaseResult<Payment> =
        transactions.inTransaction {
            val order = orders.findById(orderId) ?: return@inTransaction UseCaseResult.Failure("Order not found")
            val session = sessions.findById(order.registerSessionId)
            if (session?.status != RegisterSessionStatus.OPEN)
                return@inTransaction UseCaseResult.Failure("Register session is not open")
            if (actingCashierId != null && (session.cashierId != actingCashierId || order.cashierId != actingCashierId))
                return@inTransaction UseCaseResult.Failure("Order belongs to another user session")
            payments.findBySubmissionToken(orderId, token)?.let { return@inTransaction UseCaseResult.Success(it) }
            if (!OrderTransitionRules.canPay(order.status)) return@inTransaction UseCaseResult.Failure("Order cannot be paid")
            val calculation = PaymentRules.calculate(method, order.totalCentimes, receivedCentimes, submissionToken = token)
            if (!calculation.canConfirm) return@inTransaction UseCaseResult.Failure(calculation.error.toString())
            val payment = Payment(
                id = 0L,
                orderId = orderId,
                registerSessionId = order.registerSessionId,
                method = method,
                amountCentimes = calculation.amountCentimes,
                receivedCentimes = calculation.receivedCentimes,
                changeCentimes = calculation.changeCentimes,
                status = PaymentStatus.COMPLETED,
                submissionToken = token,
                createdAtEpochMilliseconds = System.currentTimeMillis()
            )
            val savedId = payments.save(payment)
            val savedPayment = payments.findBySubmissionToken(orderId, token) ?: payment.copy(id = savedId)
            orders.save(order.copy(status = OrderStatus.COMPLETED))
            UseCaseResult.Success(savedPayment)
        }
}

class OpenRegisterSession(private val sessions: RegisterSessionRepository, private val clock: Clock) {
    suspend fun execute(id: Long, registerId: Long, cashierId: Long, openingCashCentimes: Long): UseCaseResult<RegisterSession> {
        if (openingCashCentimes < 0) return UseCaseResult.Failure("Opening cash cannot be negative")
        if (sessions.findOpenByUser(cashierId) != null) return UseCaseResult.Failure("This user already has an open register session")
        val session = RegisterSession(
            id = id,
            status = RegisterSessionStatus.OPEN,
            openingCashCentimes = openingCashCentimes,
            registerId = registerId,
            cashierId = cashierId,
            openedAtEpochMilliseconds = clock.now().value,
            closedAtEpochMilliseconds = null,
            expectedCashCentimes = null,
            countedCashCentimes = null,
            differenceCentimes = null
        )
        runCatching { sessions.save(session) }.getOrElse {
            return UseCaseResult.Failure("This user already has an open register session")
        }
        return UseCaseResult.Success(session)
    }
}

class CloseRegisterSession(
    private val sessions: RegisterSessionRepository,
    private val payments: PaymentRepository,
    private val movements: CashMovementRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val orders: OrderRepository? = null
) {
    suspend fun execute(sessionId: Long, countedCashCentimes: Long): UseCaseResult<RegisterSession> =
        execute(
            sessionId,
            RegisterClosingInput(
                countedCashCentimes = countedCashCentimes,
                leftInDrawerCentimes = countedCashCentimes,
                removedAmountCentimes = 0L
            )
        )

    suspend fun execute(sessionId: Long, input: RegisterClosingInput): UseCaseResult<RegisterSession> = transactions.inTransaction {
        val session = sessions.findById(sessionId) ?: return@inTransaction UseCaseResult.Failure("Session not found")
        if (session.status != RegisterSessionStatus.OPEN) return@inTransaction UseCaseResult.Failure("Session is already closed")

        val activeOrdersCount = orders?.countOpenForSession(sessionId) ?: 0
        val validationError = RegisterClosingRules.validateClosing(
            sessionStatus = session.status,
            activeOrdersCount = activeOrdersCount,
            countedCashCentimes = input.countedCashCentimes,
            leftInDrawerCentimes = input.leftInDrawerCentimes,
            removedAmountCentimes = input.removedAmountCentimes,
            closingNote = input.closingNote
        )
        if (validationError != null) {
            val msg = when (validationError) {
                RegisterClosingValidationError.SESSION_ALREADY_CLOSED -> "Session is already closed"
                RegisterClosingValidationError.ACTIVE_ORDERS_EXIST -> "Active orders must be completed or cancelled before closing the register"
                RegisterClosingValidationError.COUNTED_CASH_NEGATIVE -> "Counted cash cannot be negative"
                RegisterClosingValidationError.LEFT_IN_DRAWER_NEGATIVE -> "Amount left in drawer cannot be negative"
                RegisterClosingValidationError.REMOVED_AMOUNT_NEGATIVE -> "Removed amount cannot be negative"
                RegisterClosingValidationError.BREAKDOWN_SUM_MISMATCH -> "The sum of amount left in drawer and removed amount must equal counted cash"
                RegisterClosingValidationError.CLOSING_NOTE_TOO_LONG -> "Closing note is too long"
            }
            return@inTransaction UseCaseResult.Failure(msg)
        }

        val summary = RegisterCashRules.calculateExpected(
            session.openingCashCentimes,
            payments.totalCashForSession(sessionId),
            movements.totalCashIn(sessionId),
            movements.totalCashOut(sessionId),
            input.countedCashCentimes
        )
        val closed = session.copy(
            status = RegisterSessionStatus.CLOSED,
            closedAtEpochMilliseconds = clock.now().value,
            expectedCashCentimes = summary.expectedCashCentimes,
            countedCashCentimes = input.countedCashCentimes,
            differenceCentimes = summary.differenceCentimes,
            closingUserId = input.closingUserId,
            leftInDrawerCentimes = input.leftInDrawerCentimes,
            removedAmountCentimes = input.removedAmountCentimes,
            remittanceReference = input.remittanceReference?.trim()?.takeIf { it.isNotBlank() },
            remittanceDestination = input.remittanceDestination?.trim()?.takeIf { it.isNotBlank() },
            closingNote = input.closingNote?.trim()?.takeIf { it.isNotBlank() }
        )
        sessions.save(closed)
        UseCaseResult.Success(closed)
    }
}

class RecordCashMovement(
    private val sessions: RegisterSessionRepository,
    private val payments: PaymentRepository,
    private val movements: CashMovementRepository,
    private val clock: Clock
) {
    suspend fun execute(
        sessionId: Long,
        type: CashMovementType,
        amountCentimes: Long,
        reason: String,
        description: String?,
        userId: Long
    ): UseCaseResult<CashMovement> {
        val session = sessions.findById(sessionId) ?: return UseCaseResult.Failure("Session not found")
        if (session.cashierId != userId) return UseCaseResult.Failure("Register session belongs to another user")
        val expected = RegisterCashRules.calculateExpected(
            session.openingCashCentimes,
            payments.totalCashForSession(sessionId),
            movements.totalCashIn(sessionId),
            movements.totalCashOut(sessionId)
        ).expectedCashCentimes
        val error = RegisterCashRules.validateMovement(session.status, type, amountCentimes, reason, description, expected)
        if (error != null) return UseCaseResult.Failure(error.toString())
        val movement = CashMovement(
            id = 0L,
            sessionId = sessionId,
            type = type,
            amountCentimes = amountCentimes,
            reason = reason.trim(),
            description = description?.trim(),
            createdByUserId = userId,
            createdAtEpochMilliseconds = clock.now().value
        )
        movements.save(movement)
        return UseCaseResult.Success(movement)
    }
}

class SaveProduct(private val categories: CategoryRepository, private val products: ProductRepository) {
    suspend fun execute(product: Product): UseCaseResult<Long> {
        val category = categories.findById(product.categoryId) ?: return UseCaseResult.Failure("Category not found")
        ProductValidationRules.validate(product.name, product.priceCentimes, product.taxRateBasisPoints, category.active)?.let {
            return UseCaseResult.Failure(it.toString())
        }
        return try {
            UseCaseResult.Success(products.save(product.copy(name = product.name.trim())))
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Impossible d'enregistrer le produit.")
        }
    }
}

class ChangeTableStatus(private val tables: TableRepository) {
    suspend fun execute(tableId: Long, newStatus: TableStatus): UseCaseResult<Unit> {
        val table = tables.findById(tableId) ?: return UseCaseResult.Failure("Table not found")
        if (!table.active) return UseCaseResult.Failure("Table is inactive")
        tables.updateStatus(tableId, newStatus)
        return UseCaseResult.Success(Unit)
    }
}
