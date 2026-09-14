package ma.elaroui.pos.shared.rules

enum class MoneyParseError {
    EMPTY,
    INVALID_FORMAT,
    NEGATIVE_NOT_ALLOWED,
    OVERFLOW
}

sealed interface MoneyParseResult {
    data class Success(val centimes: Long) : MoneyParseResult
    data class Failure(val error: MoneyParseError) : MoneyParseResult
}

object MoneyRules {
    /**
     * Parses a decimal currency value using comma or dot. More than two decimal
     * places are rounded HALF_UP, without Float/Double.
     */
    fun parseToCentimes(
        input: String,
        allowNegative: Boolean = false
    ): MoneyParseResult {
        val value = input.trim().replace(',', '.')
        if (value.isEmpty()) return MoneyParseResult.Failure(MoneyParseError.EMPTY)
        if (!Regex("^-?\\d+(\\.\\d+)?$").matches(value)) {
            return MoneyParseResult.Failure(MoneyParseError.INVALID_FORMAT)
        }

        val negative = value.startsWith('-')
        if (negative && !allowNegative) {
            return MoneyParseResult.Failure(MoneyParseError.NEGATIVE_NOT_ALLOWED)
        }

        val unsigned = value.removePrefix("-")
        val wholeText = unsigned.substringBefore('.')
        val fraction = unsigned.substringAfter('.', "")
        return try {
            val whole = wholeText.toLong()
            var centimes = MathRules.addExact(
                MathRules.multiplyExact(whole, 100L),
                fraction.padEnd(2, '0').take(2).toLongOrNull() ?: 0L
            )
            if (fraction.length > 2 && fraction[2] >= '5') {
                centimes = MathRules.addExact(centimes, 1L)
            }
            MoneyParseResult.Success(if (negative) -centimes else centimes)
        } catch (_: ArithmeticException) {
            MoneyParseResult.Failure(MoneyParseError.OVERFLOW)
        }
    }

    fun formatFixed(centimes: Long): String {
        val negative = centimes < 0
        val absolute = if (centimes == Long.MIN_VALUE) {
            return "-92233720368547758.08"
        } else if (negative) -centimes else centimes
        val whole = absolute / 100
        val fraction = (absolute % 100).toString().padStart(2, '0')
        return "${if (negative) "-" else ""}$whole.$fraction"
    }
}

internal object MathRules {
    fun addExact(left: Int, right: Int): Int {
        val result = left.toLong() + right.toLong()
        if (result !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            throw ArithmeticException("Int overflow")
        }
        return result.toInt()
    }

    fun addExact(left: Long, right: Long): Long {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0) throw ArithmeticException("Long overflow")
        return result
    }

    fun subtractExact(left: Long, right: Long): Long =
        if (right == Long.MIN_VALUE) {
            if (left >= 0) throw ArithmeticException("Long overflow")
            left - right
        } else {
            addExact(left, -right)
        }

    fun multiplyExact(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        val result = left * right
        if (result / right != left || (left == Long.MIN_VALUE && right == -1L)) {
            throw ArithmeticException("Long overflow")
        }
        return result
    }

    fun divideHalfUp(numerator: Long, denominator: Long): Long {
        require(denominator > 0)
        require(numerator >= 0)
        val quotient = numerator / denominator
        val remainder = numerator % denominator
        return if (remainder >= (denominator + 1) / 2) addExact(quotient, 1) else quotient
    }
}
