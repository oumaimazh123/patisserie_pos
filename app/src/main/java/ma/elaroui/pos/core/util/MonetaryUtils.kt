package ma.elaroui.pos.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules

object MonetaryUtils {
    private val ONE_HUNDRED = BigDecimal("100")

    fun toCentimes(amount: BigDecimal): Long {
        return when (val parsed = MoneyRules.parseToCentimes(amount.toPlainString(), allowNegative = true)) {
            is MoneyParseResult.Success -> parsed.centimes
            is MoneyParseResult.Failure -> throw ArithmeticException("Invalid monetary amount: ${parsed.error}")
        }
    }

    fun toBigDecimal(centimes: Long): BigDecimal {
        return BigDecimal(centimes).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
    }

    fun formatDh(centimes: Long): String {
        val amount = toBigDecimal(centimes)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00 DH", symbols)
        return formatter.format(amount)
    }
}
