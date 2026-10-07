package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.RegisterSessionStatus

data class RegisterCashSummary(
    val openingCashCentimes: Long,
    val cashSalesCentimes: Long,
    val cashInCentimes: Long,
    val cashOutCentimes: Long,
    val expectedCashCentimes: Long,
    val countedCashCentimes: Long? = null,
    val differenceCentimes: Long? = null
)

enum class CashMovementValidationError {
    SESSION_CLOSED,
    AMOUNT_NOT_POSITIVE,
    REASON_REQUIRED,
    DESCRIPTION_REQUIRED_FOR_OTHER,
    DESCRIPTION_TOO_LONG,
    WITHDRAWAL_EXCEEDS_EXPECTED_CASH
}

object RegisterCashRules {
    fun calculateExpected(
        openingCashCentimes: Long,
        cashSalesCentimes: Long,
        cashInCentimes: Long,
        cashOutCentimes: Long,
        countedCashCentimes: Long? = null
    ): RegisterCashSummary {
        require(openingCashCentimes >= 0)
        require(cashSalesCentimes >= 0)
        require(cashInCentimes >= 0)
        require(cashOutCentimes >= 0)
        val expected = MathRules.subtractExact(
            MathRules.addExact(
                MathRules.addExact(openingCashCentimes, cashSalesCentimes),
                cashInCentimes
            ),
            cashOutCentimes
        )
        val difference = countedCashCentimes?.let { MathRules.subtractExact(it, expected) }
        return RegisterCashSummary(
            openingCashCentimes,
            cashSalesCentimes,
            cashInCentimes,
            cashOutCentimes,
            expected,
            countedCashCentimes,
            difference
        )
    }

    fun validateMovement(
        sessionStatus: RegisterSessionStatus,
        type: CashMovementType,
        amountCentimes: Long,
        reason: String,
        description: String?,
        currentExpectedCashCentimes: Long
    ): CashMovementValidationError? = when {
        sessionStatus != RegisterSessionStatus.OPEN -> CashMovementValidationError.SESSION_CLOSED
        amountCentimes <= 0 -> CashMovementValidationError.AMOUNT_NOT_POSITIVE
        reason.isBlank() -> CashMovementValidationError.REASON_REQUIRED
        description.orEmpty().length > 200 -> CashMovementValidationError.DESCRIPTION_TOO_LONG
        reason.trim().equals("Autre", ignoreCase = true) && description.isNullOrBlank() ->
            CashMovementValidationError.DESCRIPTION_REQUIRED_FOR_OTHER
        type == CashMovementType.CASH_OUT && amountCentimes > currentExpectedCashCentimes ->
            CashMovementValidationError.WITHDRAWAL_EXCEEDS_EXPECTED_CASH
        else -> null
    }
}

data class RegisterClosingInput(
    val countedCashCentimes: Long? = null,
    val leftInDrawerCentimes: Long? = null,
    val removedAmountCentimes: Long? = null,
    val remittanceReference: String? = null,
    val remittanceDestination: String? = null,
    val closingNote: String? = null,
    val closingUserId: Long? = null,
    val printClosingReport: Boolean? = null
)

enum class RegisterClosingValidationError {
    SESSION_ALREADY_CLOSED,
    ACTIVE_ORDERS_EXIST,
    COUNTED_CASH_NEGATIVE,
    LEFT_IN_DRAWER_NEGATIVE,
    REMOVED_AMOUNT_NEGATIVE,
    BREAKDOWN_SUM_MISMATCH,
    CLOSING_NOTE_TOO_LONG
}

enum class DiscrepancyKind {
    EXACT,
    SURPLUS,
    SHORTAGE
}

object RegisterClosingRules {
    fun classifyDifference(differenceCentimes: Long): DiscrepancyKind = when {
        differenceCentimes == 0L -> DiscrepancyKind.EXACT
        differenceCentimes > 0L -> DiscrepancyKind.SURPLUS
        else -> DiscrepancyKind.SHORTAGE
    }

    fun validateClosing(
        sessionStatus: RegisterSessionStatus,
        activeOrdersCount: Int,
        countedCashCentimes: Long? = null,
        leftInDrawerCentimes: Long? = null,
        removedAmountCentimes: Long? = null,
        closingNote: String? = null
    ): RegisterClosingValidationError? = when {
        sessionStatus != RegisterSessionStatus.OPEN -> RegisterClosingValidationError.SESSION_ALREADY_CLOSED
        activeOrdersCount > 0 -> RegisterClosingValidationError.ACTIVE_ORDERS_EXIST
        countedCashCentimes != null && countedCashCentimes < 0 -> RegisterClosingValidationError.COUNTED_CASH_NEGATIVE
        leftInDrawerCentimes != null && leftInDrawerCentimes < 0 -> RegisterClosingValidationError.LEFT_IN_DRAWER_NEGATIVE
        removedAmountCentimes != null && removedAmountCentimes < 0 -> RegisterClosingValidationError.REMOVED_AMOUNT_NEGATIVE
        leftInDrawerCentimes != null && removedAmountCentimes != null && countedCashCentimes != null &&
            leftInDrawerCentimes + removedAmountCentimes != countedCashCentimes -> RegisterClosingValidationError.BREAKDOWN_SUM_MISMATCH
        closingNote.orEmpty().length > 500 -> RegisterClosingValidationError.CLOSING_NOTE_TOO_LONG
        else -> null
    }
}
