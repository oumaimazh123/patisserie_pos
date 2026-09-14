package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegisterRulesTest {
    @Test
    fun calculatesExpectedCashAndCountDifference() {
        val result = RegisterCashRules.calculateExpected(
            openingCashCentimes = 20_000,
            cashSalesCentimes = 45_000,
            cashInCentimes = 5_000,
            cashOutCentimes = 12_000,
            countedCashCentimes = 57_500
        )
        assertEquals(58_000, result.expectedCashCentimes)
        assertEquals(-500, result.differenceCentimes)
    }

    @Test
    fun validatesCashEntry() {
        assertNull(valid(CashMovementType.CASH_IN))
        assertEquals(
            CashMovementValidationError.SESSION_CLOSED,
            valid(CashMovementType.CASH_IN, status = RegisterSessionStatus.CLOSED)
        )
        assertEquals(
            CashMovementValidationError.AMOUNT_NOT_POSITIVE,
            valid(CashMovementType.CASH_IN, amount = 0)
        )
        assertEquals(
            CashMovementValidationError.REASON_REQUIRED,
            valid(CashMovementType.CASH_IN, reason = " ")
        )
    }

    @Test
    fun validatesWithdrawalWithoutPinAndDrawerLimit() {
        assertEquals(null, valid(CashMovementType.CASH_OUT))
        assertEquals(
            CashMovementValidationError.WITHDRAWAL_EXCEEDS_EXPECTED_CASH,
            valid(CashMovementType.CASH_OUT, amount = 10_001)
        )
        assertNull(valid(CashMovementType.CASH_OUT))
    }

    @Test
    fun otherReasonRequiresDescriptionAndDescriptionIsBounded() {
        assertEquals(
            CashMovementValidationError.DESCRIPTION_REQUIRED_FOR_OTHER,
            valid(CashMovementType.CASH_IN, reason = "Autre", description = "")
        )
        assertEquals(
            CashMovementValidationError.DESCRIPTION_TOO_LONG,
            valid(CashMovementType.CASH_IN, description = "x".repeat(201))
        )
    }

    @Test
    fun registersClosingRulesValidatesBreakdownAndDiscrepancies() {
        // Exact match
        assertEquals(DiscrepancyKind.EXACT, RegisterClosingRules.classifyDifference(0))
        // Surplus
        assertEquals(DiscrepancyKind.SURPLUS, RegisterClosingRules.classifyDifference(1500))
        // Shortage
        assertEquals(DiscrepancyKind.SHORTAGE, RegisterClosingRules.classifyDifference(-800))

        // Valid closing
        assertNull(
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.OPEN,
                activeOrdersCount = 0,
                countedCashCentimes = 50_000,
                leftInDrawerCentimes = 20_000,
                removedAmountCentimes = 30_000,
                closingNote = "Clôture régulière"
            )
        )

        // Session already closed
        assertEquals(
            RegisterClosingValidationError.SESSION_ALREADY_CLOSED,
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.CLOSED,
                activeOrdersCount = 0,
                countedCashCentimes = 50_000,
                leftInDrawerCentimes = 20_000,
                removedAmountCentimes = 30_000
            )
        )

        // Active orders exist
        assertEquals(
            RegisterClosingValidationError.ACTIVE_ORDERS_EXIST,
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.OPEN,
                activeOrdersCount = 2,
                countedCashCentimes = 50_000,
                leftInDrawerCentimes = 20_000,
                removedAmountCentimes = 30_000
            )
        )

        // Breakdown mismatch (left + removed != counted)
        assertEquals(
            RegisterClosingValidationError.BREAKDOWN_SUM_MISMATCH,
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.OPEN,
                activeOrdersCount = 0,
                countedCashCentimes = 50_000,
                leftInDrawerCentimes = 20_000,
                removedAmountCentimes = 25_000 // sum is 45000 != 50000
            )
        )

        // Negative values rejected
        assertEquals(
            RegisterClosingValidationError.COUNTED_CASH_NEGATIVE,
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.OPEN,
                activeOrdersCount = 0,
                countedCashCentimes = -100,
                leftInDrawerCentimes = 0,
                removedAmountCentimes = 0
            )
        )
        assertEquals(
            RegisterClosingValidationError.LEFT_IN_DRAWER_NEGATIVE,
            RegisterClosingRules.validateClosing(
                sessionStatus = RegisterSessionStatus.OPEN,
                activeOrdersCount = 0,
                countedCashCentimes = 5000,
                leftInDrawerCentimes = -100,
                removedAmountCentimes = 5100
            )
        )
    }

    private fun valid(
        type: CashMovementType,
        status: RegisterSessionStatus = RegisterSessionStatus.OPEN,
        amount: Long = 1_000,
        reason: String = "Correction de caisse",
        description: String? = null
    ) = RegisterCashRules.validateMovement(
        sessionStatus = status,
        type = type,
        amountCentimes = amount,
        reason = reason,
        description = description,
        currentExpectedCashCentimes = 10_000
    )
}
