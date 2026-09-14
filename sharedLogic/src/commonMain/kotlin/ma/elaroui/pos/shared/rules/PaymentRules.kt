package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.PaymentMethod

enum class PaymentValidationError {
    NEGATIVE_TOTAL,
    NEGATIVE_RECEIVED,
    INSUFFICIENT_CASH,
    AMOUNT_MISMATCH,
    CARD_NOT_APPROVED,
    EMPTY_SUBMISSION_TOKEN
}

data class PaymentCalculation(
    val amountCentimes: Long,
    val receivedCentimes: Long,
    val changeCentimes: Long,
    val remainingCentimes: Long,
    val canConfirm: Boolean,
    val error: PaymentValidationError? = null
)

object PaymentRules {
    fun calculate(
        method: PaymentMethod,
        orderTotalCentimes: Long,
        receivedCentimes: Long?,
        submittedAmountCentimes: Long = orderTotalCentimes,
        cardApproved: Boolean = true,
        submissionToken: String = "validated-by-caller"
    ): PaymentCalculation {
        if (orderTotalCentimes < 0) return failure(PaymentValidationError.NEGATIVE_TOTAL)
        if (submissionToken.isBlank()) return failure(PaymentValidationError.EMPTY_SUBMISSION_TOKEN)

        return when (method) {
            PaymentMethod.CASH -> {
                val received = receivedCentimes ?: 0L
                if (received < 0) return failure(PaymentValidationError.NEGATIVE_RECEIVED)
                if (received < orderTotalCentimes) {
                    PaymentCalculation(
                        amountCentimes = orderTotalCentimes,
                        receivedCentimes = received,
                        changeCentimes = 0L,
                        remainingCentimes = orderTotalCentimes - received,
                        canConfirm = false,
                        error = PaymentValidationError.INSUFFICIENT_CASH
                    )
                } else {
                    PaymentCalculation(
                        amountCentimes = orderTotalCentimes,
                        receivedCentimes = received,
                        changeCentimes = received - orderTotalCentimes,
                        remainingCentimes = 0L,
                        canConfirm = true
                    )
                }
            }
            PaymentMethod.CARD -> when {
                !cardApproved -> failure(PaymentValidationError.CARD_NOT_APPROVED)
                submittedAmountCentimes != orderTotalCentimes ->
                    failure(PaymentValidationError.AMOUNT_MISMATCH)
                else -> PaymentCalculation(
                    amountCentimes = orderTotalCentimes,
                    receivedCentimes = orderTotalCentimes,
                    changeCentimes = 0L,
                    remainingCentimes = 0L,
                    canConfirm = true
                )
            }
            PaymentMethod.CARNET_CLIENT, PaymentMethod.MOBILE_QR -> PaymentCalculation(
                amountCentimes = orderTotalCentimes,
                receivedCentimes = orderTotalCentimes,
                changeCentimes = 0L,
                remainingCentimes = 0L,
                canConfirm = true
            )
        }
    }

    private fun failure(error: PaymentValidationError) = PaymentCalculation(
        amountCentimes = 0L,
        receivedCentimes = 0L,
        changeCentimes = 0L,
        remainingCentimes = 0L,
        canConfirm = false,
        error = error
    )
}

enum class PaymentSubmissionDecision {
    PROCEED,
    RETURN_EXISTING_PAYMENT,
    REJECT_ALREADY_PAID,
    REJECT_CANCELLED_ORDER
}

object PaymentSubmissionRules {
    fun decide(
        orderStatus: OrderStatus,
        existingPaymentForToken: Boolean
    ): PaymentSubmissionDecision = when {
        existingPaymentForToken -> PaymentSubmissionDecision.RETURN_EXISTING_PAYMENT
        orderStatus == OrderStatus.COMPLETED -> PaymentSubmissionDecision.REJECT_ALREADY_PAID
        orderStatus == OrderStatus.CANCELLED -> PaymentSubmissionDecision.REJECT_CANCELLED_ORDER
        else -> PaymentSubmissionDecision.PROCEED
    }
}
