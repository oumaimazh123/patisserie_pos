package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.Payment
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.PaymentStatus
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionClosingReportRulesTest {
    private val closedSession = RegisterSession(
        id = 10L,
        status = RegisterSessionStatus.CLOSED,
        openingCashCentimes = 10_000L,
        registerId = 1L,
        cashierId = 2L,
        openedAtEpochMilliseconds = 1_000L,
        closedAtEpochMilliseconds = 9_000L,
        expectedCashCentimes = 14_000L,
        countedCashCentimes = 13_500L,
        differenceCentimes = -500L
    )

    @Test
    fun `report includes only paid completed sales from requested session and deduplicates payments`() {
        val paid = order(1L, 10L, OrderStatus.COMPLETED, 4_000L)
        val unpaidCompleted = order(2L, 10L, OrderStatus.COMPLETED, 3_000L)
        val cancelled = order(3L, 10L, OrderStatus.CANCELLED, 2_000L)
        val otherSession = order(4L, 11L, OrderStatus.COMPLETED, 8_000L)
        val payment = payment(5L, 1L, 10L, PaymentMethod.CASH, 4_000L)

        val report = SessionClosingReportRules.build(
            session = closedSession,
            cashierName = "Amina",
            closingUserName = "Owner",
            orders = listOf(paid, unpaidCompleted, cancelled, otherSession),
            payments = listOf(payment, payment, payment(6L, 4L, 11L, PaymentMethod.CARD, 8_000L)),
            cashInCentimes = 500L,
            cashOutCentimes = 500L
        )

        assertEquals(listOf(1L), report.sales.map { it.orderId })
        assertEquals(1, report.completedSalesCount)
        assertEquals(4_000L, report.paymentTotals[PaymentMethod.CASH])
        assertEquals(4_000L, report.grandTotalSalesCentimes)
        assertEquals(1, report.cancelledSalesCount)
        assertEquals(2_000L, report.cancelledSalesCentimes)
    }

    @Test
    fun `report preserves cash card and other payment totals`() {
        val orders = listOf(
            order(1L, 10L, OrderStatus.COMPLETED, 1_000L),
            order(2L, 10L, OrderStatus.COMPLETED, 2_000L),
            order(3L, 10L, OrderStatus.COMPLETED, 3_000L)
        )
        val report = SessionClosingReportRules.build(
            closedSession,
            "Amina",
            "Owner",
            orders,
            listOf(
                payment(1L, 1L, 10L, PaymentMethod.CASH, 1_000L),
                payment(2L, 2L, 10L, PaymentMethod.CARD, 2_000L),
                payment(3L, 3L, 10L, PaymentMethod.MOBILE_QR, 3_000L)
            ),
            0L,
            0L
        )

        assertEquals(1_000L, report.paymentTotals[PaymentMethod.CASH])
        assertEquals(2_000L, report.paymentTotals[PaymentMethod.CARD])
        assertEquals(3_000L, report.paymentTotals[PaymentMethod.MOBILE_QR])
        assertEquals(6_000L, report.grandTotalSalesCentimes)
    }

    private fun order(id: Long, sessionId: Long, status: OrderStatus, total: Long) = Order(
        id = id,
        number = "SALE-$id",
        type = OrderType.COUNTER,
        status = status,
        lines = emptyList(),
        subtotalCentimes = total,
        discountCentimes = 0L,
        taxCentimes = 0L,
        totalCentimes = total,
        registerSessionId = sessionId,
        cashierId = 2L,
        createdAtEpochMilliseconds = id * 1_000L
    )

    private fun payment(
        id: Long,
        orderId: Long,
        sessionId: Long,
        method: PaymentMethod,
        amount: Long
    ) = Payment(
        id = id,
        orderId = orderId,
        registerSessionId = sessionId,
        method = method,
        amountCentimes = amount,
        receivedCentimes = amount,
        changeCentimes = 0L,
        status = PaymentStatus.COMPLETED,
        submissionToken = "token-$id",
        createdAtEpochMilliseconds = id * 1_000L
    )
}
