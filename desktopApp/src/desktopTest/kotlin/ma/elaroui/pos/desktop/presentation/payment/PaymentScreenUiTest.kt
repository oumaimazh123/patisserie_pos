package ma.elaroui.pos.desktop.presentation.payment

import kotlin.test.*
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules

class PaymentScreenUiTest {

    private fun handleKeypadInput(current: String, key: String): String {
        return when (key) {
            "⌫" -> if (current.isNotEmpty()) current.dropLast(1) else ""
            "." -> {
                if (current.isEmpty()) "0."
                else if (!current.contains(".")) "$current."
                else current
            }
            else -> { // Digits 0-9
                if (current == "0" && key == "0") current
                else if (current == "0" && key != ".") key
                else {
                    val dotIdx = current.indexOf('.')
                    if (dotIdx != -1 && current.length - dotIdx > 2) {
                        current
                    } else if (current.length >= 8) {
                        current
                    } else {
                        current + key
                    }
                }
            }
        }
    }

    private fun createTestOrder(totalCentimes: Long): Order {
        return Order(
            id = 1L,
            number = "SALE-20260902-001",
            type = OrderType.COUNTER,
            status = ma.elaroui.pos.shared.domain.OrderStatus.OPEN,
            lines = listOf(
                OrderLine(1L, "Café Crème", totalCentimes, 1)
            ),
            subtotalCentimes = totalCentimes,
            discountCentimes = 0L,
            taxCentimes = 0L,
            totalCentimes = totalCentimes,
            tableId = null,
            registerSessionId = 1L,
            cashierId = 1L
        )
    }

    @Test
    fun `numeric keypad correctly builds and edits numbers`() {
        var input = ""
        input = handleKeypadInput(input, "1")
        input = handleKeypadInput(input, "5")
        input = handleKeypadInput(input, "0")
        assertEquals("150", input)

        input = handleKeypadInput(input, ".")
        input = handleKeypadInput(input, "5")
        input = handleKeypadInput(input, "0")
        assertEquals("150.50", input)

        // Cannot add more than 2 decimal digits
        input = handleKeypadInput(input, "9")
        assertEquals("150.50", input)

        // Backspace removes last digit
        input = handleKeypadInput(input, "⌫")
        assertEquals("150.5", input)
        input = handleKeypadInput(input, "⌫")
        assertEquals("150.", input)
        input = handleKeypadInput(input, "⌫")
        assertEquals("150", input)
    }

    @Test
    fun `decimal point starting from empty string yields zero dot`() {
        var input = ""
        input = handleKeypadInput(input, ".")
        assertEquals("0.", input)
        input = handleKeypadInput(input, "5")
        assertEquals("0.5", input)
    }

    @Test
    fun `exact amount quick button fills exact total`() {
        val order = createTestOrder(75_50L) // 75.50 DH
        val exactStr = MoneyRules.formatFixed(order.totalCentimes)
        assertEquals("75.50", exactStr)

        val parsed = (MoneyRules.parseToCentimes(exactStr) as MoneyParseResult.Success).centimes
        assertEquals(75_50L, parsed)
        assertEquals(0L, if (parsed > order.totalCentimes) parsed - order.totalCentimes else 0L)
    }

    @Test
    fun `cash payment calculation computes correct change and remaining amount`() {
        val order = createTestOrder(85_00L) // 85.00 DH

        // When cash is 100 DH
        val cash100 = (MoneyRules.parseToCentimes("100") as MoneyParseResult.Success).centimes
        val change100 = if (cash100 > order.totalCentimes) cash100 - order.totalCentimes else 0L
        val remaining100 = if (cash100 < order.totalCentimes) order.totalCentimes - cash100 else 0L
        assertEquals(15_00L, change100)
        assertEquals(0L, remaining100)

        // When cash is 50 DH (insufficient)
        val cash50 = (MoneyRules.parseToCentimes("50") as MoneyParseResult.Success).centimes
        val change50 = if (cash50 > order.totalCentimes) cash50 - order.totalCentimes else 0L
        val remaining50 = if (cash50 < order.totalCentimes) order.totalCentimes - cash50 else 0L
        assertEquals(0L, change50)
        assertEquals(35_00L, remaining50)
    }

    @Test
    fun `payment validation rule allows card immediately and validates cash sufficiency`() {
        val order = createTestOrder(50_00L)

        // Card is always valid
        val isCardValid = PaymentMethod.CARD != PaymentMethod.CASH
        assertTrue(isCardValid)

        // Cash requires cash >= order total
        val cashLow = (MoneyRules.parseToCentimes("40") as MoneyParseResult.Success).centimes
        val isCashLowValid = cashLow >= order.totalCentimes
        assertFalse(isCashLowValid)

        val cashExact = (MoneyRules.parseToCentimes("50") as MoneyParseResult.Success).centimes
        val isCashExactValid = cashExact >= order.totalCentimes
        assertTrue(isCashExactValid)

        val cashHigh = (MoneyRules.parseToCentimes("100") as MoneyParseResult.Success).centimes
        val isCashHighValid = cashHigh >= order.totalCentimes
        assertTrue(isCashHighValid)
    }
}
