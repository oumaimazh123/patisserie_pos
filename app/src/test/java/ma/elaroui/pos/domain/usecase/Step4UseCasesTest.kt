package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.exception.InvalidOwnerPinException
import ma.elaroui.pos.core.exception.SessionAlreadyOpenException
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.CashMovementRepository
import ma.elaroui.pos.domain.session.SessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeCashMovementRepository : CashMovementRepository {
    val movements = mutableMapOf<Long, MutableList<CashMovement>>()
    var nextId = 1L

    override suspend fun insertCashMovement(movement: CashMovement): Long {
        val id = nextId++
        val list = movements.getOrPut(movement.registerSessionId) { mutableListOf() }
        list.add(movement.copy(id = id))
        return id
    }

    override fun getCashMovementsForSession(sessionId: Long): Flow<List<CashMovement>> =
        flowOf(movements[sessionId] ?: emptyList())

    override suspend fun getCashMovementsListForSession(sessionId: Long): List<CashMovement> =
        movements[sessionId] ?: emptyList()

    override suspend fun getTotalCashInForSession(sessionId: Long): Long =
        movements[sessionId]?.filter { it.type == CashMovementType.CASH_IN }?.sumOf { it.amountCentimes } ?: 0L

    override suspend fun getTotalCashOutForSession(sessionId: Long): Long =
        movements[sessionId]?.filter { it.type == CashMovementType.CASH_OUT }?.sumOf { it.amountCentimes } ?: 0L
}

class FakeUnpaidOrdersChecker : UnpaidOrdersChecker {
    var unpaidCount = 0
    override suspend fun getUnpaidOrderCount(sessionId: Long): Int = unpaidCount
}

class Step4UseCasesTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var registerRepository: FakeRegisterRepository
    private lateinit var cashMovementRepository: FakeCashMovementRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var setupPreferences: FakeSetupPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var unpaidOrdersChecker: FakeUnpaidOrdersChecker

    private lateinit var getActiveRegistersUseCase: GetActiveRegistersUseCase
    private lateinit var createRegisterUseCase: CreateRegisterUseCase
    private lateinit var deactivateRegisterUseCase: DeactivateRegisterUseCase
    private lateinit var openRegisterSessionUseCase: OpenRegisterSessionUseCase
    private lateinit var getCurrentExpectedCashUseCase: GetCurrentExpectedCashUseCase
    private lateinit var recordCashMovementUseCase: RecordCashMovementUseCase
    private lateinit var closeRegisterSessionUseCase: CloseRegisterSessionUseCase
    private lateinit var verifyOwnerPinUseCase: VerifyOwnerPinUseCase

    private lateinit var ownerUser: User
    private lateinit var cashierUser: User

    @Before
    fun setUp() {
        userRepository = FakeUserRepository()
        registerRepository = FakeRegisterRepository()
        cashMovementRepository = FakeCashMovementRepository()
        orderRepository = FakeOrderRepository()
        setupPreferences = FakeSetupPreferences()
        setupPreferences.cashOutApprovalPinHash = SecurityUtils.hashPin(
            "2468",
            SecurityUtils.generateSalt()
        )
        sessionManager = SessionManager()
        unpaidOrdersChecker = FakeUnpaidOrdersChecker()

        val salt = SecurityUtils.generateSalt()
        ownerUser = User(id = 1L, name = "Owner Zakaria", role = UserRole.OWNER, pinHash = SecurityUtils.hashPin("1234", salt))
        cashierUser = User(id = 2L, name = "Cashier Youssef", role = UserRole.CASHIER, pinHash = SecurityUtils.hashPin("0000", salt))

        runTest {
            userRepository.insertUser(ownerUser)
            userRepository.insertUser(cashierUser)
            registerRepository.insertRegister(Register(id = 1L, name = "Main Register", active = true))
        }

        sessionManager.login(cashierUser, null)

        getActiveRegistersUseCase = GetActiveRegistersUseCase(registerRepository)
        createRegisterUseCase = CreateRegisterUseCase(registerRepository)
        deactivateRegisterUseCase = DeactivateRegisterUseCase(registerRepository)
        openRegisterSessionUseCase = OpenRegisterSessionUseCase(registerRepository, userRepository, sessionManager)
        getCurrentExpectedCashUseCase = GetCurrentExpectedCashUseCase(registerRepository, cashMovementRepository, orderRepository)
        verifyOwnerPinUseCase = VerifyOwnerPinUseCase(userRepository)
        recordCashMovementUseCase = RecordCashMovementUseCase(
            registerRepository = registerRepository,
            cashMovementRepository = cashMovementRepository,
            userRepository = userRepository,
            getCurrentExpectedCashUseCase = getCurrentExpectedCashUseCase,
            setupPreferences = setupPreferences
        )
        closeRegisterSessionUseCase = CloseRegisterSessionUseCase(
            registerRepository = registerRepository,
            cashMovementRepository = cashMovementRepository,
            orderRepository = orderRepository,
            unpaidOrdersChecker = unpaidOrdersChecker,
            verifyOwnerPinUseCase = verifyOwnerPinUseCase,
            sessionManager = sessionManager
        )
    }

    @Test
    fun openRegister_zeroCash_success() = runTest {
        val session = openRegisterSessionUseCase(
            registerId = 1L,
            openingCashCentimes = 0L,
            authenticatedUser = cashierUser
        )
        assertNotNull(session)
        assertEquals(0L, session.openingCashCentimes)
        assertEquals(RegisterSessionStatus.OPEN, session.status)
        assertEquals(session.id, sessionManager.currentUser?.currentRegisterSessionId)
    }

    @Test(expected = DomainException::class)
    fun openRegister_negativeCash_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = -500L, authenticatedUser = cashierUser)
    }

    @Test(expected = DomainException::class)
    fun openRegister_deactivatedRegister_throwsException() = runTest {
        registerRepository.insertRegister(Register(id = 2L, name = "Closed Reg", active = false))
        openRegisterSessionUseCase(registerId = 2L, openingCashCentimes = 1000L, authenticatedUser = cashierUser)
    }

    @Test(expected = SessionAlreadyOpenException::class)
    fun openRegister_registerAlreadyHasSession_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 1000L, authenticatedUser = cashierUser)
        sessionManager.login(ownerUser, null)
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 1000L, authenticatedUser = ownerUser)
    }

    @Test(expected = DomainException::class)
    fun openRegister_userDifferentFromAuthenticatedSession_isRejected() = runTest {
        openRegisterSessionUseCase(
            registerId = 1L,
            openingCashCentimes = 1_000L,
            authenticatedUser = ownerUser
        )
    }

    @Test
    fun recordCashMovement_cashIn_increasesExpectedCash() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)

        val movement = recordCashMovementUseCase(
            sessionId = session.id,
            type = CashMovementType.CASH_IN,
            amountCentimes = 5000L,
            reason = "Adding change float",
            currentUser = cashierUser
        )

        assertEquals(5000L, movement.amountCentimes)
        val expected = getCurrentExpectedCashUseCase(session.id)
        assertEquals(15000L, expected)
    }

    @Test
    fun recordCashMovement_differentCashier_usesSharedSessionAndTracksUser() = runTest {
        val session = openRegisterSessionUseCase(1L, 10_000L, cashierUser)
        val secondCashier = User(
            id = 3L,
            name = "Cashier Salma",
            role = UserRole.CASHIER,
            pinHash = cashierUser.pinHash
        )
        userRepository.insertUser(secondCashier)

        val movement = recordCashMovementUseCase(
            session.id,
            CashMovementType.CASH_IN,
            2_000L,
            "Ajout monnaie",
            secondCashier
        )

        assertEquals(secondCashier.id, movement.createdByUserId)
        assertEquals(session.id, movement.registerSessionId)
    }

    @Test
    fun recordCashMovement_cashOutWithOwnerApproval_decreasesExpectedCash() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)

        recordCashMovementUseCase(
            sessionId = session.id,
            type = CashMovementType.CASH_OUT,
            amountCentimes = 3000L,
            reason = "Paid small expense",
            currentUser = cashierUser,
            approvalPinForCashOut = "2468"
        )

        val expected = getCurrentExpectedCashUseCase(session.id)
        assertEquals(7000L, expected)
    }

    @Test(expected = DomainException::class)
    fun recordCashMovement_cashOutInvalidSharedPin_throwsException() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)

        recordCashMovementUseCase(
            sessionId = session.id,
            type = CashMovementType.CASH_OUT,
            amountCentimes = 3000L,
            reason = "Paid small expense",
            currentUser = cashierUser,
            approvalPinForCashOut = "9999"
        )
    }

    @Test(expected = DomainException::class)
    fun recordCashMovement_cashOutExceedsExpected_throwsException() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 5000L, authenticatedUser = cashierUser)

        recordCashMovementUseCase(
            sessionId = session.id,
            type = CashMovementType.CASH_OUT,
            amountCentimes = 10000L, // Exceeds 5000 float
            reason = "Overdraw",
            currentUser = cashierUser,
            approvalPinForCashOut = "2468"
        )
    }

    @Test
    fun closeRegister_balancedSession_closesSuccessfully() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)
        recordCashMovementUseCase(session.id, CashMovementType.CASH_IN, 2000L, "Added Float", cashierUser)
        sessionManager.login(ownerUser, session.id)

        val closed = closeRegisterSessionUseCase(
            sessionId = session.id,
            countedCashCentimes = 12000L,
            currentUser = ownerUser
        )

        assertEquals(RegisterSessionStatus.CLOSED, closed.status)
        assertEquals(12000L, closed.expectedCashCentimes)
        assertEquals(12000L, closed.countedCashCentimes)
        assertEquals(0L, closed.differenceCentimes)
        assertNull(sessionManager.currentUser?.currentRegisterSessionId)
    }

    @Test(expected = DomainException::class)
    fun closeRegister_largeDifferenceWithoutNote_throwsException() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)
        sessionManager.login(ownerUser, session.id)

        // Counted 2000L vs Expected 10000L -> Difference -8000L (> 5000L threshold)
        closeRegisterSessionUseCase(
            sessionId = session.id,
            countedCashCentimes = 2000L,
            closingNote = null,
            currentUser = ownerUser
        )
    }

    @Test(expected = DomainException::class)
    fun closeRegister_cashierCannotCloseSharedSession() = runTest {
        val session = openRegisterSessionUseCase(1L, 10_000L, cashierUser)
        closeRegisterSessionUseCase(session.id, 10_000L, currentUser = cashierUser)
    }

    @Test
    fun closeRegister_largeDifferenceWithOwnerApproval_succeeds() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 10000L, authenticatedUser = cashierUser)
        sessionManager.login(ownerUser, session.id)

        val closed = closeRegisterSessionUseCase(
            sessionId = session.id,
            countedCashCentimes = 2000L, // -8000L diff
            closingNote = "Shortage due to spilled coffee reimbursement",
            currentUser = ownerUser,
            ownerPinForLargeDifference = "1234"
        )

        assertEquals(RegisterSessionStatus.CLOSED, closed.status)
        assertEquals(-8000L, closed.differenceCentimes)
        assertTrue(closed.ownerApprovedDifference)
        assertEquals(ownerUser.id, closed.approvedByOwnerId)
    }

    @Test
    fun closeRegister_differentSession_doesNotClearOperatorsOwnSessionPointer() = runTest {
        sessionManager.login(ownerUser, registerSessionId = 99L)
        registerRepository.sessions[10L] = RegisterSession(
            id = 10L,
            registerId = 1L,
            cashierId = cashierUser.id,
            openingCashCentimes = 0L,
            status = RegisterSessionStatus.OPEN
        )

        closeRegisterSessionUseCase(
            sessionId = 10L,
            countedCashCentimes = 0L,
            currentUser = ownerUser
        )

        assertEquals(99L, sessionManager.currentUser?.currentRegisterSessionId)
    }
}
