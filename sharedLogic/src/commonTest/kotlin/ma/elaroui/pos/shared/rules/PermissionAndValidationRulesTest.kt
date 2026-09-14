package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Permission
import ma.elaroui.pos.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermissionAndValidationRulesTest {
    @Test
    fun ownerHasEveryPermission() {
        Permission.entries.forEach { assertTrue(PermissionRules.isAllowed(UserRole.OWNER, it)) }
    }

    @Test
    fun cashierHasOperationalButNotAdministrativePermissions() {
        assertTrue(PermissionRules.isAllowed(UserRole.CASHIER, Permission.USE_POS))
        assertTrue(PermissionRules.isAllowed(UserRole.CASHIER, Permission.RECORD_CASH_ENTRY))
        assertTrue(PermissionRules.isAllowed(UserRole.CASHIER, Permission.RECORD_CASH_WITHDRAWAL))
        assertFalse(PermissionRules.isAllowed(UserRole.CASHIER, Permission.MANAGE_USERS))
        assertFalse(PermissionRules.isAllowed(UserRole.CASHIER, Permission.CLOSE_REGISTER))
    }

    @Test
    fun productValidationCoversRequiredBusinessFields() {
        assertEquals(
            ProductValidationError.NAME_REQUIRED,
            ProductValidationRules.validate("", 100, 2_000, true)
        )
        assertEquals(
            ProductValidationError.PRICE_NOT_POSITIVE,
            ProductValidationRules.validate("Café", 0, 2_000, true)
        )
        assertEquals(
            ProductValidationError.INVALID_TAX_RATE,
            ProductValidationRules.validate("Café", 100, 10_001, true)
        )
        assertEquals(
            ProductValidationError.CATEGORY_INACTIVE,
            ProductValidationRules.validate("Café", 100, 2_000, false)
        )
        assertNull(ProductValidationRules.validate("Café", 1_200, 2_000, true))
    }

    @Test
    fun pinValidationStrictlyRequires4To6DigitsOnly() {
        assertEquals(PinValidationError.BLANK, PinValidationRules.validatePin(""))
        assertEquals(PinValidationError.INVALID_LENGTH, PinValidationRules.validatePin("123"))
        assertEquals(PinValidationError.INVALID_LENGTH, PinValidationRules.validatePin("1234567"))
        assertEquals(PinValidationError.NOT_DIGITS, PinValidationRules.validatePin("12a4"))
        assertNull(PinValidationRules.validatePin("1234"))
        assertNull(PinValidationRules.validatePin("123456"))

        assertEquals(PinValidationError.MISMATCH, PinValidationRules.validatePinConfirmation("1234", "1235"))
        assertNull(PinValidationRules.validatePinConfirmation("1234", "1234"))
        assertTrue(PinValidationRules.isValidMatch("1234", "1234"))
        assertFalse(PinValidationRules.isValidMatch("1234", "abcd"))
    }
}
