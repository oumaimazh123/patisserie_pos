package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ma.elaroui.pos.core.exception.*
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.shared.rules.RegisterCashRules
import ma.elaroui.pos.domain.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

interface UnpaidOrdersChecker {
    suspend fun getUnpaidOrderCount(sessionId: Long): Int
}

@Singleton
class DefaultUnpaidOrdersChecker @Inject constructor(
    private val orderRepository: OrderRepository
) : UnpaidOrdersChecker {
    override suspend fun getUnpaidOrderCount(sessionId: Long): Int {
        return orderRepository.getOpenOrderCountForSession(sessionId)
    }
}

@Singleton
class GetActiveRegistersUseCase @Inject constructor(
    private val registerRepository: RegisterRepository
) {
    operator fun invoke(): Flow<List<Register>> = registerRepository.getAllActiveRegisters()
}

@Singleton
class CreateRegisterUseCase @Inject constructor(
    private val registerRepository: RegisterRepository
) {
    suspend operator fun invoke(name: String): Register {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Register name cannot be empty.")
        }
        val existing = registerRepository.getRegisterByName(trimmed)
        if (existing != null && existing.active) {
            throw DomainException("A register named '$trimmed' already exists.")
        }
        val register = Register(name = trimmed, active = true)
        val newId = registerRepository.insertRegister(register)
        return register.copy(id = newId)
    }
}

@Singleton
class DeactivateRegisterUseCase @Inject constructor(
    private val registerRepository: RegisterRepository
) {
    suspend operator fun invoke(registerId: Long) {
        val activeSession = registerRepository.getActiveSessionForRegister(registerId)
        if (activeSession != null) {
            throw DomainException("Cannot deactivate a register that has an active open session.")
        }
        val register = registerRepository.getRegisterById(registerId)
            ?: throw DomainException("Register not found.")

        registerRepository.updateRegister(register.copy(active = false, updatedAt = System.currentTimeMillis()))
    }
}

@Singleton
class OpenRegisterSessionUseCase @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(
        registerId: Long,
        openingCashCentimes: Long,
        authenticatedUser: User
    ): RegisterSession {
        if (openingCashCentimes < 0) {
            throw DomainException("Opening cash amount cannot be negative.")
        }
        if (!authenticatedUser.active) {
            throw DomainException("Deactivated users cannot open a cash register session.")
        }
        val authenticatedSession = sessionManager.currentUser
            ?: throw DomainException("No authenticated user session.")
        if (authenticatedSession.userId != authenticatedUser.id) {
            throw DomainException("The authenticated user does not match the requested cashier.")
        }

        val register = registerRepository.getRegisterById(registerId)
            ?: throw DomainException("Register not found.")
        if (!register.active) {
            throw DomainException("Cannot open a session for a deactivated register.")
        }

        // Offline shared-drawer model: only one register shift may be open at a time.
        val existingOpenSession = registerRepository.getAnyActiveSession()
        if (existingOpenSession != null) {
            throw SessionAlreadyOpenException(existingOpenSession.registerId)
        }

        val session = RegisterSession(
            registerId = registerId,
            cashierId = authenticatedUser.id,
            openedByUserId = authenticatedUser.id,
            openingCashCentimes = openingCashCentimes,
            status = RegisterSessionStatus.OPEN
        )

        val newSessionId = registerRepository.openSession(session)
        val openedSession = session.copy(id = newSessionId)

        // Update SessionManager
        sessionManager.updateRegisterSessionId(newSessionId)

        return openedSession
    }
}

@Singleton
class GetCurrentExpectedCashUseCase @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(sessionId: Long): Long {
        val session = registerRepository.getSessionById(sessionId)
            ?: throw DomainException("Session not found.")

        val cashIn = cashMovementRepository.getTotalCashInForSession(sessionId)
        val cashOut = cashMovementRepository.getTotalCashOutForSession(sessionId)
        val cashSales = orderRepository.getTotalCashPaymentsForSession(sessionId)

        return RegisterCashRules.calculateExpected(
            openingCashCentimes = session.openingCashCentimes,
            cashSalesCentimes = cashSales,
            cashInCentimes = cashIn,
            cashOutCentimes = cashOut
        ).expectedCashCentimes
    }
}

@Singleton
class RecordCashMovementUseCase @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val userRepository: UserRepository,
    private val getCurrentExpectedCashUseCase: GetCurrentExpectedCashUseCase,
    private val setupPreferences: SetupPreferences
) {
    suspend operator fun invoke(
        sessionId: Long,
        type: CashMovementType,
        amountCentimes: Long,
        reason: String,
        currentUser: User,
        approvalPinForCashOut: String? = null
    ): CashMovement {
        if (amountCentimes <= 0) {
            throw DomainException("Cash movement amount must be greater than zero.")
        }
        if (reason.isBlank()) {
            throw DomainException("Reason is required for cash movements.")
        }
        if (reason.trim().length > 240) {
            throw DomainException("Le motif et la description du mouvement sont trop longs.")
        }

        val storedUser = userRepository.getUserById(currentUser.id)
            ?: throw DomainException("User not found.")
        if (!storedUser.active) {
            throw DomainException("Deactivated users cannot record cash movements.")
        }

        val session = registerRepository.getSessionById(sessionId)
            ?: throw DomainException("Register session not found.")
        if (session.status != RegisterSessionStatus.OPEN) {
            throw DomainException("Cash movements can only be recorded for an OPEN session.")
        }

        if (type == CashMovementType.CASH_OUT) {
            val storedApprovalPinHash = setupPreferences.cashOutApprovalPinHash
            if (storedApprovalPinHash.isBlank()) {
                throw DomainException("Le PIN partagé des sorties doit être configuré par le propriétaire.")
            }
            if (
                approvalPinForCashOut.isNullOrBlank() ||
                !SecurityUtils.verifyPin(approvalPinForCashOut, storedApprovalPinHash)
            ) {
                throw DomainException("PIN partagé des sorties invalide.")
            }

            // Verify Cash-out does not make expected cash negative
            val currentExpected = getCurrentExpectedCashUseCase(sessionId)
            if (currentExpected - amountCentimes < 0) {
                throw DomainException("Cash out of ${amountCentimes / 100.0} DH exceeds expected cash in drawer.")
            }
        }

        val movement = CashMovement(
            registerSessionId = sessionId,
            type = type,
            amountCentimes = amountCentimes,
            reason = reason.trim(),
            createdByUserId = storedUser.id
        )

        val newId = cashMovementRepository.insertCashMovement(movement)
        return movement.copy(id = newId)
    }
}

@Singleton
class CloseRegisterSessionUseCase @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val orderRepository: OrderRepository,
    private val unpaidOrdersChecker: UnpaidOrdersChecker,
    private val verifyOwnerPinUseCase: VerifyOwnerPinUseCase,
    private val sessionManager: SessionManager
) {
    // Default threshold: 50 MAD = 5000 centimes
    val varianceThresholdCentimes: Long = 5000L

    suspend operator fun invoke(
        sessionId: Long,
        countedCashCentimes: Long,
        closingNote: String? = null,
        currentUser: User,
        ownerPinForLargeDifference: String? = null
    ): RegisterSession {
        if (countedCashCentimes < 0) {
            throw DomainException("Counted cash amount cannot be negative.")
        }
        val authenticatedSession = sessionManager.currentUser
            ?: throw DomainException("No authenticated user session.")
        if (authenticatedSession.userId != currentUser.id) {
            throw DomainException("The authenticated user does not match the closing operator.")
        }
        if (!currentUser.active) {
            throw DomainException("Deactivated users cannot close a register session.")
        }

        val session = registerRepository.getSessionById(sessionId)
            ?: throw DomainException("Register session not found.")

        if (session.status == RegisterSessionStatus.CLOSED) {
            throw DomainException("Session #$sessionId is already closed.")
        }

        // The shared register is closed for everyone, so closure is owner-only.
        if (currentUser.role != UserRole.OWNER) {
            throw DomainException("Only the owner can close the shared register.")
        }

        // Unpaid orders check integration
        val unpaidCount = unpaidOrdersChecker.getUnpaidOrderCount(sessionId)
        if (unpaidCount > 0) {
            throw UnpaidOrdersExistException(sessionId, unpaidCount)
        }

        // Recalculate totals from Room inside transaction
        val cashIn = cashMovementRepository.getTotalCashInForSession(sessionId)
        val cashOut = cashMovementRepository.getTotalCashOutForSession(sessionId)
        val cashSales = orderRepository.getTotalCashPaymentsForSession(sessionId)
        val cashSummary = RegisterCashRules.calculateExpected(
            openingCashCentimes = session.openingCashCentimes,
            cashSalesCentimes = cashSales,
            cashInCentimes = cashIn,
            cashOutCentimes = cashOut,
            countedCashCentimes = countedCashCentimes
        )
        val expectedCash = cashSummary.expectedCashCentimes
        val difference = requireNotNull(cashSummary.differenceCentimes)

        var ownerApproved = false
        var approvedByOwnerId: Long? = null

        val absDifference = if (difference < 0) -difference else difference
        if (absDifference > varianceThresholdCentimes) {
            if (closingNote.isNullOrBlank()) {
                throw DomainException("A closing note is required when difference exceeds 50.00 DH.")
            }

            if (ownerPinForLargeDifference.isNullOrBlank()) {
                throw DomainException("Owner PIN approval is required when difference exceeds 50.00 DH.")
            }

            val ownerUser = verifyOwnerPinUseCase(ownerPinForLargeDifference)
            if (ownerUser == null) {
                throw InvalidOwnerPinException()
            }

            ownerApproved = true
            approvedByOwnerId = ownerUser.id
        }

        val closedSession = session.copy(
            closedByUserId = currentUser.id,
            closedAt = System.currentTimeMillis(),
            expectedCashCentimes = expectedCash,
            countedCashCentimes = countedCashCentimes,
            differenceCentimes = difference,
            closingNote = closingNote?.trim()?.takeIf { it.isNotBlank() },
            ownerApprovedDifference = ownerApproved,
            approvedByOwnerId = approvedByOwnerId,
            status = RegisterSessionStatus.CLOSED
        )

        registerRepository.updateSession(closedSession)

        // Clear active session ID from SessionManager
        if (sessionManager.currentUser?.currentRegisterSessionId == sessionId) {
            sessionManager.updateRegisterSessionId(null)
        }

        return closedSession
    }
}

@Singleton
class GetRegisterSessionHistoryUseCase @Inject constructor(
    private val registerRepository: RegisterRepository
) {
    operator fun invoke(): Flow<List<RegisterSession>> = registerRepository.getAllSessionsHistory()
}
