package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Payment
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.Register
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.RegisterSessionStatus
import ma.elaroui.pos.domain.repository.PaymentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SalesHistoryUseCasesTest {

    @Test
    fun getHistory_resolvesRegisterThroughSession_andRestrictsRequestedSession() = runTest {
        val orders = FakeOrderRepository()
        val users = FakeUserRepository()
        val registers = FakeRegisterRepository().apply {
            this.registers[7L] = Register(id = 7L, name = "Caisse Terrasse")
            sessions[21L] = RegisterSession(
                id = 21L,
                registerId = 7L,
                cashierId = 2L,
                openingCashCentimes = 0L,
                status = RegisterSessionStatus.OPEN
            )
            sessions[22L] = RegisterSession(
                id = 22L,
                registerId = 1L,
                cashierId = 2L,
                openingCashCentimes = 0L,
                status = RegisterSessionStatus.CLOSED
            )
        }
        val now = System.currentTimeMillis()
        orders.orders[1L] = completedOrder(1L, "ORD-001", 21L, now)
        orders.orders[2L] = completedOrder(2L, "ORD-002", 22L, now - 1_000L)

        val result = GetSalesHistoryUseCase(orders, users, registers)(
            status = OrderStatus.COMPLETED,
            startMs = now - 10_000L,
            endMs = now + 10_000L,
            sessionId = 21L
        )

        assertEquals(1, result.size)
        assertEquals("ORD-001", result.single().order.orderNumber)
        assertEquals(7L, result.single().registerId)
        assertEquals("Caisse Terrasse", result.single().registerName)
        assertEquals("Cashier User", result.single().cashierName)
    }

    @Test
    fun cancelledHistory_andCsv_keepReasonAndExcludeCompletedRows() = runTest {
        val orders = FakeOrderRepository()
        val users = FakeUserRepository()
        val registers = FakeRegisterRepository().apply {
            sessions[10L] = RegisterSession(
                id = 10L,
                registerId = 1L,
                cashierId = 2L,
                openingCashCentimes = 0L
            )
        }
        val now = System.currentTimeMillis()
        orders.orders[1L] = completedOrder(1L, "ORD-PAID", 10L, now)
        orders.orders[2L] = Order(
            id = 2L,
            orderNumber = "ORD-CANCEL",
            type = OrderType.TAKEAWAY,
            cashierId = 2L,
            registerSessionId = 10L,
            status = OrderStatus.CANCELLED,
            totalCentimes = 3_500L,
            cancelledAt = now,
            cancellationReason = "Erreur de saisie"
        )

        val entries = GetSalesHistoryUseCase(orders, users, registers)(
            status = OrderStatus.CANCELLED,
            startMs = now - 10_000L,
            endMs = now + 10_000L,
            sessionId = null
        )
        val output = ByteArrayOutputStream()
        ExportSalesHistoryCsvUseCase()(output, entries)
        val csv = output.toString(Charsets.UTF_8.name())

        assertEquals(listOf("ORD-CANCEL"), entries.map { it.order.orderNumber })
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Erreur de saisie"))
        assertTrue(csv.contains("Annulée"))
        assertFalse(csv.contains("ORD-PAID"))
    }

    @Test
    fun saleDetails_usesRegisterIdFromSession_notTheSessionId() = runTest {
        val orders = FakeOrderRepository()
        val users = FakeUserRepository()
        val tables = FakeTableRepository()
        val registers = FakeRegisterRepository().apply {
            this.registers[7L] = Register(id = 7L, name = "Caisse Jardin")
            sessions[21L] = RegisterSession(
                id = 21L,
                registerId = 7L,
                cashierId = 2L,
                openingCashCentimes = 0L
            )
        }
        val now = System.currentTimeMillis()
        val order = completedOrder(1L, "ORD-DETAIL", 21L, now)
        orders.orders[1L] = order
        orders.payments[1L] = order.payments.toMutableList()

        val details = GetCompletedSaleDetailsUseCase(
            orderRepository = orders,
            paymentRepository = HistoryPaymentRepository(orders.payments),
            tableRepository = tables,
            registerRepository = registers,
            userRepository = users,
            setupPreferences = FakeSetupPreferences()
        )(1L)

        assertEquals("Caisse Jardin", details?.registerName)
        assertEquals("Caisse Jardin", details?.receiptData?.registerName)
        assertEquals("Cashier User", details?.cashierName)
    }

    private fun completedOrder(
        id: Long,
        number: String,
        sessionId: Long,
        completedAt: Long
    ) = Order(
        id = id,
        orderNumber = number,
        type = OrderType.DINE_IN,
        cashierId = 2L,
        registerSessionId = sessionId,
        status = OrderStatus.COMPLETED,
        totalCentimes = 2_500L,
        completedAt = completedAt,
        payments = listOf(
            Payment(
                orderId = id,
                registerSessionId = sessionId,
                method = PaymentMethod.CASH,
                amountCentimes = 2_500L,
                receivedAmountCentimes = 2_500L,
                changeAmountCentimes = 0L
            )
        )
    )
}

private class HistoryPaymentRepository(
    private val payments: MutableMap<Long, MutableList<Payment>>
) : PaymentRepository {
    override suspend fun insertPayment(payment: Payment): Long {
        payments.getOrPut(payment.orderId) { mutableListOf() }.add(payment)
        return payment.id
    }

    override suspend fun getPaymentByOrderAndToken(orderId: Long, token: String): Payment? =
        payments[orderId]?.firstOrNull { it.submissionToken == token }

    override suspend fun getPaymentsForOrder(orderId: Long): List<Payment> =
        payments[orderId].orEmpty()

    override suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long =
        amountFor(sessionId, PaymentMethod.CASH)

    override suspend fun getTotalCardPaymentsForSession(sessionId: Long): Long =
        amountFor(sessionId, PaymentMethod.CARD)

    override suspend fun getTotalPaymentsForSession(sessionId: Long): Long =
        payments.values.flatten()
            .filter { it.registerSessionId == sessionId }
            .sumOf { it.amountCentimes }

    override suspend fun getCashPaymentsForDateRange(startMs: Long, endMs: Long): Long =
        amountForRange(startMs, endMs, PaymentMethod.CASH)

    override suspend fun getCardPaymentsForDateRange(startMs: Long, endMs: Long): Long =
        amountForRange(startMs, endMs, PaymentMethod.CARD)

    private fun amountFor(sessionId: Long, method: PaymentMethod): Long =
        payments.values.flatten()
            .filter { it.registerSessionId == sessionId && it.method == method }
            .sumOf { it.amountCentimes }

    private fun amountForRange(startMs: Long, endMs: Long, method: PaymentMethod): Long =
        payments.values.flatten()
            .filter { it.createdAt in startMs..endMs && it.method == method }
            .sumOf { it.amountCentimes }
}
