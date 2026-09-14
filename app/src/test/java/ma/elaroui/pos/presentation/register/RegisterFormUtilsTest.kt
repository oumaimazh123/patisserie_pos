package ma.elaroui.pos.presentation.register

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterFormUtilsTest {

    @Test
    fun moneyInput_acceptsFrenchCommaAndTwoDecimals() {
        assertEquals("125,50", sanitizeMoneyInput("125,50"))
        assertEquals(12_550L, moneyInputToCentimesOrNull("125,50"))
    }

    @Test
    fun moneyInput_rejectsMalformedOrOverPreciseValues() {
        assertNull(moneyInputToCentimesOrNull("."))
        assertNull(sanitizeMoneyInput("1.2.3"))
        assertNull(sanitizeMoneyInput("10.999"))
        assertNull(sanitizeMoneyInput("-5"))
    }
}
