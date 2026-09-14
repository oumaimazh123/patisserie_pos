package ma.elaroui.pos.presentation.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Payment
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ExportSalesHistoryCsvUseCase
import ma.elaroui.pos.domain.usecase.FakeOrderRepository
import ma.elaroui.pos.domain.usecase.FakeRegisterRepository
import ma.elaroui.pos.domain.usecase.FakeUserRepository
import ma.elaroui.pos.domain.usecase.GetSalesHistoryUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CompletedSalesViewModelTest {

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
    fun owner_filtersPayments_updatesSummary_andExportsFullFilteredResult() = runTest {
        val fixture = Fixture(owner = true)
        val viewModel = fixture.createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOwner)
        assertEquals(2, viewModel.uiState.value.filteredCount)
        assertEquals(4_500L, viewModel.uiState.value.summary.totalCentimes)

        viewModel.selectPaymentMethod(PaymentMethod.CARD)
        assertEquals(1, viewModel.uiState.value.filteredCount)
        assertEquals(2_000L, viewModel.uiState.value.summary.totalCentimes)
        assertEquals(2_000L, viewModel.uiState.value.summary.cardCentimes)

        val output = TrackingOutputStream()
        viewModel.exportCsv(output)
        advanceUntilIdle()

        val csv = output.toString(Charsets.UTF_8.name())
        assertTrue(output.closed)
        assertTrue(csv.contains("ORD-CARD"))
        assertFalse(csv.contains("ORD-CASH"))
        assertFalse(viewModel.uiState.value.isExporting)
    }

    @Test
    fun cashier_seesOnlyCurrentSession_andCancelledTabHasNoSalesRevenue() = runTest {
        val fixture = Fixture(owner = false)
        val viewModel = fixture.createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOwner)
        assertEquals(listOf("ORD-CASH"), viewModel.uiState.value.entries.map { it.order.orderNumber })

        viewModel.selectStatus(OrderStatus.CANCELLED)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.filteredCount)
        assertEquals("ORD-CANCEL", viewModel.uiState.value.entries.single().order.orderNumber)
        assertEquals(1_000L, viewModel.uiState.value.summary.totalCentimes)
        assertEquals(0L, viewModel.uiState.value.summary.cashCentimes)
    }

    private class Fixture(owner: Boolean) {
        private val orders = FakeOrderRepository()
        private val users = FakeUserRepository()
        private val registers = FakeRegisterRepository()
        private val session = SessionManager()
        private val now = System.currentTimeMillis()

        init {
            registers.sessions[10L] = RegisterSession(
                id = 10L,
                registerId = 1L,
                cashierId = 2L,
                openingCashCentimes = 0L
            )
            registers.sessions[11L] = RegisterSession(
                id = 11L,
                registerId = 1L,
                cashierId = 2L,
                openingCashCentimes = 0L
            )
            orders.orders[1L] = paidOrder(
                id = 1L,
                number = "ORD-CASH",
                registerSessionId = 10L,
                method = PaymentMethod.CASH,
                total = 2_500L
            )
            orders.orders[2L] = paidOrder(
                id = 2L,
                number = "ORD-CARD",
                registerSessionId = 11L,
                method = PaymentMethod.CARD,
                total = 2_000L
            )
            orders.orders[3L] = Order(
                id = 3L,
                orderNumber = "ORD-CANCEL",
                type = OrderType.COUNTER,
                cashierId = 2L,
                registerSessionId = 10L,
                status = OrderStatus.CANCELLED,
                totalCentimes = 1_000L,
                cancelledAt = now,
                cancellationReason = "Test"
            )

            if (owner) {
                session.login(checkNotNull(users.users[1L]))
            } else {
                session.login(checkNotNull(users.users[2L]), registerSessionId = 10L)
            }
        }

        fun createViewModel() = CompletedSalesViewModel(
            getSalesHistoryUseCase = GetSalesHistoryUseCase(orders, users, registers),
            exportSalesHistoryCsvUseCase = ExportSalesHistoryCsvUseCase(),
            sessionManager = session
        )

        private fun paidOrder(
            id: Long,
            number: String,
            registerSessionId: Long,
            method: PaymentMethod,
            total: Long
        ): Order {
            val payment = Payment(
                orderId = id,
                registerSessionId = registerSessionId,
                method = method,
                amountCentimes = total,
                receivedAmountCentimes = total,
                changeAmountCentimes = 0L
            )
            orders.payments[id] = mutableListOf(payment)
            return Order(
                id = id,
                orderNumber = number,
                type = OrderType.DINE_IN,
                cashierId = 2L,
                registerSessionId = registerSessionId,
                status = OrderStatus.COMPLETED,
                totalCentimes = total,
                completedAt = now,
                payments = listOf(payment)
            )
        }
    }
}

private class TrackingOutputStream : ByteArrayOutputStream() {
    var closed = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}
