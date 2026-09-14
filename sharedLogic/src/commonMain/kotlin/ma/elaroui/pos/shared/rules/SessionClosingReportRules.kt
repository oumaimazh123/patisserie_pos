package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.Payment
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.PaymentStatus
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.domain.RegisterSessionStatus

const val SESSION_CLOSING_REPORT_SETTING = "auto_print_session_closing_report"

data class SessionClosingSaleItem(
    val productName: String,
    val quantity: Int
)

data class SessionClosingSale(
    val orderId: Long,
    val orderNumber: String,
    val paidAtEpochMilliseconds: Long,
    val paymentMethods: List<PaymentMethod>,
    val totalCentimes: Long,
    val items: List<SessionClosingSaleItem> = emptyList()
)

data class SessionClosingReport(
    val session: RegisterSession,
    val cashierName: String,
    val closingUserName: String?,
    val sales: List<SessionClosingSale>,
    val paymentTotals: Map<PaymentMethod, Long>,
    val cashInCentimes: Long,
    val cashOutCentimes: Long,
    val cancelledSalesCount: Int,
    val cancelledSalesCentimes: Long
) {
    val completedSalesCount: Int get() = sales.size
    val grandTotalSalesCentimes: Long get() = paymentTotals.values.sum()
}

/** Builds a report only from persisted records belonging to the closed session. */
object SessionClosingReportRules {
    /**
     * Determines whether the session closing report should be automatically printed.
     * Rule: ON by default (true if setting is null/unset or "true", false only if explicitly "false").
     */
    fun isAutoPrintEnabled(settingValue: String?): Boolean =
        settingValue == null || settingValue.equals("true", ignoreCase = true)

    fun build(
        session: RegisterSession,
        cashierName: String,
        closingUserName: String?,
        orders: List<Order>,
        payments: List<Payment>,
        cashInCentimes: Long,
        cashOutCentimes: Long
    ): SessionClosingReport {
        require(session.status == RegisterSessionStatus.CLOSED) {
            "Session must be closed before its report can be printed"
        }

        val sessionOrders = orders.filter { it.registerSessionId == session.id }
        val sessionOrderIds = sessionOrders.mapTo(mutableSetOf()) { it.id }
        val completedPayments = payments.asSequence()
            .filter {
                it.registerSessionId == session.id &&
                    it.orderId in sessionOrderIds &&
                    it.status == PaymentStatus.COMPLETED
            }
            .distinctBy(::paymentIdentity)
            .groupBy { it.orderId }

        val sales = sessionOrders.asSequence()
            .filter { it.status == OrderStatus.COMPLETED }
            .distinctBy { it.id }
            .mapNotNull { order ->
                val paid = completedPayments[order.id].orEmpty()
                if (paid.isEmpty()) return@mapNotNull null
                val consolidatedItems = LinkedHashMap<String, Int>()
                order.lines.forEach { line ->
                    val name = line.name.trim()
                    if (name.isNotBlank() && line.quantity > 0) {
                        consolidatedItems[name] = (consolidatedItems[name] ?: 0) + line.quantity
                    }
                }
                val items = consolidatedItems.map { (name, qty) ->
                    SessionClosingSaleItem(productName = name, quantity = qty)
                }
                SessionClosingSale(
                    orderId = order.id,
                    orderNumber = order.number,
                    paidAtEpochMilliseconds = paid.maxOf { it.createdAtEpochMilliseconds },
                    paymentMethods = paid.map { it.method }.distinct(),
                    totalCentimes = paid.sumOf { it.amountCentimes },
                    items = items
                )
            }
            .sortedBy { it.paidAtEpochMilliseconds }
            .toList()

        val recognizedOrderIds = sales.mapTo(mutableSetOf()) { it.orderId }
        val paymentTotals = completedPayments.asSequence()
            .filter { it.key in recognizedOrderIds }
            .flatMap { it.value.asSequence() }
            .groupBy { it.method }
            .mapValues { (_, methodPayments) -> methodPayments.sumOf { it.amountCentimes } }

        val cancelled = sessionOrders.filter { it.status == OrderStatus.CANCELLED }
        return SessionClosingReport(
            session = session,
            cashierName = cashierName,
            closingUserName = closingUserName,
            sales = sales,
            paymentTotals = paymentTotals,
            cashInCentimes = cashInCentimes,
            cashOutCentimes = cashOutCentimes,
            cancelledSalesCount = cancelled.size,
            cancelledSalesCentimes = cancelled.sumOf { it.totalCentimes }
        )
    }

    private fun paymentIdentity(payment: Payment): String = when {
        payment.id > 0L -> "id:${payment.id}"
        payment.submissionToken.isNotBlank() -> "token:${payment.orderId}:${payment.submissionToken}"
        else -> "legacy:${payment.orderId}:${payment.method}:${payment.amountCentimes}:${payment.createdAtEpochMilliseconds}"
    }
}
