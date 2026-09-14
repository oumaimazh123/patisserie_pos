package ma.elaroui.pos.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MoneyRulesTest {
    @Test
    fun acceptsCommaDotAndWholeValues() {
        assertMoney("50", 5_000)
        assertMoney("50.5", 5_050)
        assertMoney("50.50", 5_050)
        assertMoney("50,50", 5_050)
    }

    @Test
    fun roundsAdditionalDecimalsHalfUp() {
        assertMoney("1.004", 100)
        assertMoney("1.005", 101)
        assertMoney("1.999", 200)
    }

    @Test
    fun rejectsInvalidAndNegativeValuesByDefault() {
        assertEquals(MoneyParseError.EMPTY, failure("").error)
        assertEquals(MoneyParseError.INVALID_FORMAT, failure("12..4").error)
        assertEquals(MoneyParseError.INVALID_FORMAT, failure("DH 12").error)
        assertEquals(MoneyParseError.NEGATIVE_NOT_ALLOWED, failure("-1").error)
    }

    @Test
    fun supportsNegativeValuesWhenExplicitlyAllowed() {
        assertEquals(
            MoneyParseResult.Success(-125),
            MoneyRules.parseToCentimes("-1,25", allowNegative = true)
        )
    }

    @Test
    fun formatsCentimesWithTwoDecimals() {
        assertEquals("0.00", MoneyRules.formatFixed(0))
        assertEquals("25.00", MoneyRules.formatFixed(2_500))
        assertEquals("-12.34", MoneyRules.formatFixed(-1_234))
        assertEquals("-92233720368547758.08", MoneyRules.formatFixed(Long.MIN_VALUE))
    }

    private fun assertMoney(input: String, expected: Long) {
        assertEquals(MoneyParseResult.Success(expected), MoneyRules.parseToCentimes(input))
    }

    private fun failure(input: String) =
        assertIs<MoneyParseResult.Failure>(MoneyRules.parseToCentimes(input))
}
