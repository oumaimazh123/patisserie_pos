package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.PaymentMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentRulesTest {
    @Test
    fun cashReportsRemainingAmountWhenInsufficient() {
        val result = PaymentRules.calculate(PaymentMethod.CASH, 3_750, 2_000)
        assertFalse(result.canConfirm)
        assertEquals(1_750, result.remainingCentimes)
        assertEquals(0, result.changeCentimes)
        assertEquals(PaymentValidationError.INSUFFICIENT_CASH, result.error)
    }

    @Test
    fun cashSupportsExactAmountAndChange() {
        val exact = PaymentRules.calculate(PaymentMethod.CASH, 3_750, 3_750)
        assertTrue(exact.canConfirm)
        assertEquals(0, exact.changeCentimes)

        val change = PaymentRules.calculate(PaymentMethod.CASH, 3_750, 5_000)
        assertTrue(change.canConfirm)
        assertEquals(1_250, change.changeCentimes)
    }

    @Test
    fun validatesCardAndNonCashPayments() {
        assertEquals(
            PaymentValidationError.CARD_NOT_APPROVED,
            PaymentRules.calculate(PaymentMethod.CARD, 1_000, null, cardApproved = false).error
        )
        assertEquals(
            PaymentValidationError.AMOUNT_MISMATCH,
            PaymentRules.calculate(PaymentMethod.CARD, 1_000, null, submittedAmountCentimes = 999).error
        )
        assertTrue(PaymentRules.calculate(PaymentMethod.MOBILE_QR, 1_000, null).canConfirm)
        assertTrue(PaymentRules.calculate(PaymentMethod.CARNET_CLIENT, 1_000, null).canConfirm)
    }

    @Test
    fun rejectsNegativeAndUnidentifiedSubmissions() {
        assertEquals(
            PaymentValidationError.NEGATIVE_TOTAL,
            PaymentRules.calculate(PaymentMethod.CASH, -1, 0).error
        )
        assertEquals(
            PaymentValidationError.NEGATIVE_RECEIVED,
            PaymentRules.calculate(PaymentMethod.CASH, 1, -1).error
        )
        assertEquals(
            PaymentValidationError.EMPTY_SUBMISSION_TOKEN,
            PaymentRules.calculate(PaymentMethod.CASH, 1, 1, submissionToken = "").error
        )
    }

    @Test
    fun duplicateSubmissionReturnsExistingPaymentWithoutProcessingAgain() {
        assertEquals(
            PaymentSubmissionDecision.RETURN_EXISTING_PAYMENT,
            PaymentSubmissionRules.decide(OrderStatus.COMPLETED, existingPaymentForToken = true)
        )
        assertEquals(
            PaymentSubmissionDecision.REJECT_ALREADY_PAID,
            PaymentSubmissionRules.decide(OrderStatus.COMPLETED, existingPaymentForToken = false)
        )
        assertEquals(
            PaymentSubmissionDecision.REJECT_CANCELLED_ORDER,
            PaymentSubmissionRules.decide(OrderStatus.CANCELLED, existingPaymentForToken = false)
        )
        assertEquals(
            PaymentSubmissionDecision.PROCEED,
            PaymentSubmissionRules.decide(OrderStatus.OPEN, existingPaymentForToken = false)
        )
    }
}
