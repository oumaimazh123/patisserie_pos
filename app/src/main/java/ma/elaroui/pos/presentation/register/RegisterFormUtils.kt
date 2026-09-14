package ma.elaroui.pos.presentation.register

import ma.elaroui.pos.core.util.MonetaryUtils
import java.math.BigDecimal

private val MONEY_INPUT_PATTERN = Regex("""^\d{0,7}([.,]\d{0,2})?$""")

internal fun sanitizeMoneyInput(input: String): String? {
    val compact = input.trim().replace(" ", "")
    return compact.takeIf {
        it.length <= 10 &&
            MONEY_INPUT_PATTERN.matches(it) &&
            it.count { character -> character == '.' || character == ',' } <= 1
    }
}

internal fun moneyInputToCentimesOrNull(input: String): Long? {
    val sanitized = sanitizeMoneyInput(input)?.replace(',', '.') ?: return null
    if (sanitized.isBlank() || sanitized == ".") return null
    return runCatching {
        MonetaryUtils.toCentimes(BigDecimal(sanitized))
    }.getOrNull()
}
