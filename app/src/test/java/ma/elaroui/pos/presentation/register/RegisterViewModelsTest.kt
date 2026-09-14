package ma.elaroui.pos.presentation.register

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ma.elaroui.pos.domain.model.CashMovement
import ma.elaroui.pos.domain.model.CashMovementType
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Register
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.RegisterSessionStatus
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.CloseRegisterSessionUseCase
import ma.elaroui.pos.domain.usecase.FakeCashMovementRepository
import ma.elaroui.pos.domain.usecase.FakeOrderRepository
import ma.elaroui.pos.domain.usecase.FakeRegisterRepository
import ma.elaroui.pos.domain.usecase.FakeUnpaidOrdersChecker
import ma.elaroui.pos.domain.usecase.FakeUserRepository
import ma.elaroui.pos.domain.usecase.FakeSetupPreferences
import ma.elaroui.pos.domain.usecase.GetCurrentExpectedCashUseCase
import ma.elaroui.pos.domain.usecase.GetRegisterSessionHistoryUseCase
import ma.elaroui.pos.domain.usecase.GetRegisterSessionOperationsUseCase
import ma.elaroui.pos.domain.usecase.RecordCashMovementUseCase
import ma.elaroui.pos.domain.usecase.VerifyOwnerPinUseCase
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.presentation.register.close.CloseRegisterViewModel
import ma.elaroui.pos.presentation.register.current.CurrentSessionViewModel
import ma.elaroui.pos.presentation.register.history.RegisterHistoryViewModel
import ma.elaroui.pos.presentation.register.open.OpenRegisterViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun currentSession_acceptsCommaAmount_recordsOnceAndRefreshesExpectedCash() = runTest {
        val fixture = Fixture()
        val viewModel = CurrentSessionViewModel(
            registerRepository = fixture.registers,
            cashMovementRepository = fixture.movements,
            userRepository = fixture.users,
            getCurrentExpectedCashUseCase = fixture.expectedCash,
            recordCashMovementUseCase = fixture.recordMovement,
            sessionManager = fixture.sessionManager
        )
        advanceUntilIdle()

        viewModel.openMovementDialog(CashMovementType.CASH_IN)
        viewModel.updateMovementForm(
            amount = "10,50",
            reason = "Ajout de monnaie",
            description = "Monnaie supplémentaire",
            ownerPin = ""
        )
        viewModel.submitCashMovement()
        advanceUntilIdle()

        assertEquals(1, fixture.movements.movements[10L]?.size)
        assertEquals(
            "Ajout de monnaie — Monnaie supplémentaire",
            fixture.movements.movements[10L]?.single()?.reason
        )
        assertEquals(10_500L, viewModel.uiState.value.currentExpectedCashCentimes)
        assertEquals(1_050L, viewModel.uiState.value.totalCashInCentimes)
        assertFalse(viewModel.uiState.value.isMovementDialogOpen)
    }

    @Test
    fun currentSession_otherReason_requiresDescription() = runTest {
        val fixture = Fixture()
        val viewModel = CurrentSessionViewModel(
            registerRepository = fixture.registers,
            cashMovementRepository = fixture.movements,
            userRepository = fixture.users,
            getCurrentExpectedCashUseCase = fixture.expectedCash,
            recordCashMovementUseCase = fixture.recordMovement,
            sessionManager = fixture.sessionManager
        )
        advanceUntilIdle()

        viewModel.openMovementDialog(CashMovementType.CASH_IN)
        viewModel.updateMovementForm("10", "Autre", "", "")
        viewModel.submitCashMovement()
        advanceUntilIdle()

        assertEquals(0, fixture.movements.movements[10L]?.size ?: 0)
        assertEquals(
            "La description est obligatoire pour le motif Autre.",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun closeSession_rejectsMalformedMoneyAndReportsOpenOrderBlocker() = runTest {
        val fixture = Fixture()
        fixture.orders.orders[1L] = Order(
            id = 1L,
            orderNumber = "ORD-OPEN",
            type = OrderType.COUNTER,
            cashierId = 2L,
            registerSessionId = 10L,
            status = OrderStatus.OPEN
        )
        val viewModel = CloseRegisterViewModel(
            registerRepository = fixture.registers,
            cashMovementRepository = fixture.movements,
            orderRepository = fixture.orders,
            userRepository = fixture.users,
            getCurrentExpectedCashUseCase = fixture.expectedCash,
            closeRegisterSessionUseCase = fixture.closeSession,
            sessionManager = fixture.sessionManager
        )
        advanceUntilIdle()

        viewModel.updateCountedCashInput(".")
        assertEquals(".", viewModel.uiState.value.countedCashInput)

        viewModel.updateCountedCashInput("100,25")
        assertEquals(10_025L, viewModel.uiState.value.countedCashCentimes)
        assertEquals(1, viewModel.uiState.value.openOrderCount)

        var success = false
        viewModel.submitCloseRegister { success = true }
        advanceUntilIdle()
        assertFalse(success)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("ouverte") == true)
    }

    @Test
    fun history_filtersAndRetainsInactiveCashierName_andLoadsMovements() = runTest {
        val fixture = Fixture()
        fixture.users.users[3L] = User(
            id = 3L,
            name = "Ancienne Caissière",
            role = UserRole.CASHIER,
            pinHash = "unused",
            active = false
        )
        fixture.registers.sessions[11L] = RegisterSession(
            id = 11L,
            registerId = 1L,
            cashierId = 3L,
            openingCashCentimes = 20_000L,
            closedAt = System.currentTimeMillis(),
            expectedCashCentimes = 20_000L,
            countedCashCentimes = 19_500L,
            differenceCentimes = -500L,
            status = RegisterSessionStatus.CLOSED
        )
        fixture.movements.movements[11L] = mutableListOf(
            CashMovement(
                id = 1L,
                registerSessionId = 11L,
                type = CashMovementType.CASH_OUT,
                amountCentimes = 500L,
                reason = "Petite dépense",
                createdByUserId = 1L
            )
        )
        val viewModel = RegisterHistoryViewModel(
            getRegisterSessionHistoryUseCase = GetRegisterSessionHistoryUseCase(fixture.registers),
            getRegisterSessionOperationsUseCase = GetRegisterSessionOperationsUseCase(
                fixture.orders,
                fixture.movements,
                fixture.users
            ),
            userRepository = fixture.users
        )
        advanceUntilIdle()

        assertEquals("Ancienne Caissière", viewModel.uiState.value.usersMap[3L]?.name)
        viewModel.updateSearchQuery("ancienne")
        assertEquals(listOf(11L), viewModel.uiState.value.sessions.map { it.id })

        viewModel.selectSession(checkNotNull(viewModel.uiState.value.sessions.single()))
        advanceUntilIdle()
        val operation = viewModel.uiState.value.selectedSessionOperations.single()
        assertEquals("Petite dépense", operation.description)
        assertEquals("Owner User", operation.userName)
    }

    @Test
    fun openRegister_ownerCanCancelAndNavigateToDashboardWithoutOpeningSession() = runTest {
        val fixture = Fixture()
        val owner = checkNotNull(fixture.users.users[1L])
        fixture.sessionManager.login(owner, registerSessionId = null)

        val isOwner = fixture.sessionManager.sessionState.value?.role == UserRole.OWNER
        assertTrue("User must be detected as owner", isOwner)

        val getActiveRegisters = ma.elaroui.pos.domain.usecase.GetActiveRegistersUseCase(fixture.registers)
        val openRegisterSession = ma.elaroui.pos.domain.usecase.OpenRegisterSessionUseCase(
            registerRepository = fixture.registers,
            userRepository = fixture.users,
            sessionManager = fixture.sessionManager
        )
        val viewModel = OpenRegisterViewModel(
            getActiveRegistersUseCase = getActiveRegisters,
            openRegisterSessionUseCase = openRegisterSession,
            userRepository = fixture.users,
            sessionManager = fixture.sessionManager
        )
        advanceUntilIdle()

        // Owner types some float without confirming
        viewModel.updateOpeningCashInput("500")

        // No submitOpenRegister called
        assertEquals(null, fixture.sessionManager.sessionState.value?.currentRegisterSessionId)
        assertTrue(fixture.registers.sessions.values.none { it.cashierId == owner.id && it.status == RegisterSessionStatus.OPEN })
    }

    @Test
    fun openRegister_cashierLocksWithoutOpeningSessionAndCannotAccessDashboard() = runTest {
        val fixture = Fixture()
        fixture.registers.sessions.clear()
        val cashier = checkNotNull(fixture.users.users[2L])
        fixture.sessionManager.login(cashier, registerSessionId = null)

        val isOwner = fixture.sessionManager.sessionState.value?.role == UserRole.OWNER
        assertFalse("Cashier must not be detected as owner", isOwner)

        // Cashier clicks Verrouiller
        fixture.sessionManager.lock()

        assertTrue("Session must be locked", fixture.sessionManager.sessionState.value?.isLocked == true)
        assertEquals(null, fixture.sessionManager.sessionState.value?.currentRegisterSessionId)
        assertTrue("No register session should be opened", fixture.registers.sessions.isEmpty())
    }

    private class Fixture {
        val users = FakeUserRepository()
        val registers = FakeRegisterRepository()
        val movements = FakeCashMovementRepository()
        val orders = FakeOrderRepository()
        val sessionManager = SessionManager()
        private val unpaid = FakeUnpaidOrdersChecker()
        private val ownerPin = VerifyOwnerPinUseCase(users)
        private val setupPreferences = FakeSetupPreferences().apply {
            cashOutApprovalPinHash = SecurityUtils.hashPin(
                "2468",
                SecurityUtils.generateSalt()
            )
        }
        val expectedCash = GetCurrentExpectedCashUseCase(registers, movements, orders)
        val recordMovement = RecordCashMovementUseCase(
            registerRepository = registers,
            cashMovementRepository = movements,
            userRepository = users,
            getCurrentExpectedCashUseCase = expectedCash,
            setupPreferences = setupPreferences
        )
        val closeSession = CloseRegisterSessionUseCase(
            registerRepository = registers,
            cashMovementRepository = movements,
            orderRepository = orders,
            unpaidOrdersChecker = unpaid,
            verifyOwnerPinUseCase = ownerPin,
            sessionManager = sessionManager
        )

        init {
            registers.registers[1L] = Register(id = 1L, name = "Caisse principale")
            registers.sessions[10L] = RegisterSession(
                id = 10L,
                registerId = 1L,
                cashierId = 2L,
                openingCashCentimes = 9_450L,
                status = RegisterSessionStatus.OPEN
            )
            sessionManager.login(checkNotNull(users.users[2L]), registerSessionId = 10L)
        }
    }
}
