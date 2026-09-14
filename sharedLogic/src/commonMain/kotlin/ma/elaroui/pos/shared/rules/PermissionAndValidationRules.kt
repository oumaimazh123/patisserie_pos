package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Permission
import ma.elaroui.pos.shared.domain.UserRole

object PermissionRules {
    private val ownerPermissions = Permission.entries.toSet()
    private val cashierPermissions = setOf(
        Permission.USE_POS,
        Permission.RECORD_CASH_ENTRY,
        Permission.RECORD_CASH_WITHDRAWAL
    )

    fun permissionsFor(role: UserRole): Set<Permission> = when (role) {
        UserRole.OWNER -> ownerPermissions
        UserRole.CASHIER -> cashierPermissions
    }

    fun isAllowed(role: UserRole, permission: Permission): Boolean =
        permission in permissionsFor(role)
}

enum class ProductValidationError {
    NAME_REQUIRED,
    PRICE_NOT_POSITIVE,
    INVALID_TAX_RATE,
    CATEGORY_INACTIVE
}

object ProductValidationRules {
    fun validate(
        name: String,
        priceCentimes: Long,
        taxRateBasisPoints: Int,
        categoryActive: Boolean
    ): ProductValidationError? = when {
        name.isBlank() -> ProductValidationError.NAME_REQUIRED
        priceCentimes <= 0 -> ProductValidationError.PRICE_NOT_POSITIVE
        taxRateBasisPoints !in 0..10_000 -> ProductValidationError.INVALID_TAX_RATE
        !categoryActive -> ProductValidationError.CATEGORY_INACTIVE
        else -> null
    }
}

enum class PinValidationError {
    BLANK,
    INVALID_LENGTH,
    NOT_DIGITS,
    MISMATCH
}

object PinValidationRules {
    /** PIN must be 4 to 6 digits only. */
    fun validatePin(pin: String): PinValidationError? = when {
        pin.isBlank() -> PinValidationError.BLANK
        pin.length !in 4..6 -> PinValidationError.INVALID_LENGTH
        !pin.all { it.isDigit() } -> PinValidationError.NOT_DIGITS
        else -> null
    }

    /** Validates PIN and confirms that pinConfirm matches pin. */
    fun validatePinConfirmation(pin: String, pinConfirm: String): PinValidationError? {
        val pinError = validatePin(pin)
        if (pinError != null) return pinError
        if (pin != pinConfirm) return PinValidationError.MISMATCH
        return null
    }

    fun isValid(pin: String): Boolean = validatePin(pin) == null
    fun isValidMatch(pin: String, pinConfirm: String): Boolean = validatePinConfirmation(pin, pinConfirm) == null
}
