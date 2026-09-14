package ma.elaroui.pos.presentation.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentInputTest {

    @Test
    fun acceptsTypingStatesAndBothDecimalSeparators() {
        listOf("", "50", "50.", "50,", "50.5", "50.50", "50,50").forEach {
            assertTrue("$it should be accepted", isValidMoneyInput(it))
        }
        listOf("-1", "abc", "5..0", "5,0.0", "1.234").forEach {
            assertFalse("$it should be rejected", isValidMoneyInput(it))
        }
    }

    @Test
    fun parsesCashUsingExactCentimes() {
        assertEquals(5_000L, parseMoneyToCentimes("50"))
        assertEquals(5_050L, parseMoneyToCentimes("50.50"))
        assertEquals(5_050L, parseMoneyToCentimes("50,50"))
        assertNull(parseMoneyToCentimes(""))
        assertNull(parseMoneyToCentimes("50,"))
        assertNull(parseMoneyToCentimes("invalid"))
    }

    @Test
    fun changeAndRemainingUseIntegerCentimes() {
        val total = 3_750L
        assertEquals(1_250L, parseMoneyToCentimes("50,00")!! - total)
        assertEquals(0L, parseMoneyToCentimes("37.50")!! - total)
        assertEquals(750L, total - parseMoneyToCentimes("30")!!)
    }
}
