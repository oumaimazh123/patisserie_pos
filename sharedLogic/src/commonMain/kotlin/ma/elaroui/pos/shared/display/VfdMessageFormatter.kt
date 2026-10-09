package ma.elaroui.pos.shared.display

import ma.elaroui.pos.shared.rules.MoneyRules

object VfdMessageFormatter {

    /**
     * Replaces French accented characters with their plain ASCII equivalents
     * to prevent corrupted glyphs on standard VFD displays.
     */
    fun transliterateAccents(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val replacement = when (c) {
                'é', 'è', 'ê', 'ë' -> 'e'
                'É', 'È', 'Ê', 'Ë' -> 'E'
                'à', 'â', 'ä' -> 'a'
                'À', 'Â', 'Ä' -> 'A'
                'î', 'ï' -> 'i'
                'Î', 'Ï' -> 'I'
                'ô', 'ö' -> 'o'
                'Ô', 'Ö' -> 'O'
                'ù', 'û', 'ü' -> 'u'
                'Ù', 'Û', 'Ü' -> 'U'
                'ç' -> 'c'
                'Ç' -> 'C'
                'œ' -> 'o'
                'Œ' -> 'O'
                'æ' -> 'a'
                'Æ' -> 'A'
                '’', '`' -> '\''
                '€' -> 'E'
                else -> if (c.code in 32..126) c else ' '
            }
            sb.append(replacement)
        }
        return sb.toString()
    }

    /**
     * Sanitizes and pads/truncates a line to the exact specified width.
     */
    fun fitLine(text: String, width: Int): String {
        val safe = transliterateAccents(text).trimEnd()
        return if (safe.length >= width) {
            safe.take(width)
        } else {
            safe.padEnd(width, ' ')
        }
    }

    /**
     * Formats a line with a left-aligned label and right-aligned value,
     * ensuring the total line length is exactly [width].
     */
    fun formatKeyValue(label: String, value: String, width: Int): String {
        val safeLabel = transliterateAccents(label).trim()
        val safeValue = transliterateAccents(value).trim()

        if (safeValue.length >= width) {
            return safeValue.take(width)
        }

        val availableForLabel = width - safeValue.length - 1
        val truncatedLabel = if (safeLabel.length > availableForLabel) {
            safeLabel.take(availableForLabel.coerceAtLeast(0))
        } else {
            safeLabel
        }

        val spacesNeeded = (width - truncatedLabel.length - safeValue.length).coerceAtLeast(1)
        val line = truncatedLabel + " ".repeat(spacesNeeded) + safeValue
        return line.take(width).padEnd(width, ' ')
    }

    /**
     * Formats the customer display state into two display lines conforming to the configuration.
     */
    fun format(
        state: CustomerDisplayState,
        config: CustomerDisplayConfig,
        currencySuffix: String = ""
    ): FormattedDisplayLines {
        val cols = config.columns.coerceAtLeast(10)
        val curr = if (currencySuffix.isNotBlank()) " $currencySuffix" else ""

        return when (state) {
            is CustomerDisplayState.Idle -> {
                val l1 = fitLine(config.welcomeLine1.ifBlank { "BIENVENUE" }, cols)
                val l2 = fitLine(config.welcomeLine2.ifBlank { "HYPER CAISSE" }, cols)
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.DuringSale -> {
                val formattedTotal = MoneyRules.formatFixed(state.totalCentimes) + curr
                val formattedDue = MoneyRules.formatFixed(state.dueCentimes) + curr
                val l1 = formatKeyValue("TOTAL", formattedTotal, cols)
                val l2 = formatKeyValue("A PAYER", formattedDue, cols)
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.PaymentStarted -> {
                val formattedTotal = MoneyRules.formatFixed(state.totalCentimes) + curr
                val formattedDue = MoneyRules.formatFixed(state.dueCentimes) + curr
                val l1 = formatKeyValue("TOTAL", formattedTotal, cols)
                val l2 = formatKeyValue("A PAYER", formattedDue, cols)
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.CashPayment -> {
                val formattedReceived = MoneyRules.formatFixed(state.receivedCentimes) + curr
                val l1 = formatKeyValue("RECU", formattedReceived, cols)
                val l2 = if (state.changeCentimes > 0L) {
                    val formattedChange = MoneyRules.formatFixed(state.changeCentimes) + curr
                    formatKeyValue("MONNAIE", formattedChange, cols)
                } else if (state.remainingCentimes > 0L) {
                    val formattedRemaining = MoneyRules.formatFixed(state.remainingCentimes) + curr
                    formatKeyValue("A PAYER", formattedRemaining, cols)
                } else {
                    val zeroChange = MoneyRules.formatFixed(0L) + curr
                    formatKeyValue("MONNAIE", zeroChange, cols)
                }
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.NonCashPayment -> {
                val formattedTotal = MoneyRules.formatFixed(state.amountCentimes) + curr
                val l1 = formatKeyValue("TOTAL", formattedTotal, cols)
                val l2 = formatKeyValue(state.methodLabel.ifBlank { "PAIEMENT" }, formattedTotal, cols)
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.PaymentCompleted -> {
                val l1 = fitLine(config.thankYouLine1.ifBlank { "MERCI POUR VOTRE" }, cols)
                val l2 = fitLine(config.thankYouLine2.ifBlank { "VISITE" }, cols)
                FormattedDisplayLines(l1, l2)
            }

            is CustomerDisplayState.CustomMessage -> {
                val l1 = fitLine(state.line1, cols)
                val l2 = fitLine(state.line2, cols)
                FormattedDisplayLines(l1, l2)
            }
        }
    }
}
