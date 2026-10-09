package ma.elaroui.pos.desktop.print

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.rules.SessionClosingReport
import ma.elaroui.pos.shared.rules.SessionReportType

object SessionClosingReportEscPosFormatter {
    fun format(
        report: SessionClosingReport,
        establishmentName: String,
        paperWidth: Int,
        type: SessionReportType = SessionReportType.SUMMARY,
        isReprint: Boolean = false
    ): EscPosFormatResult = runCatching {
        require(paperWidth == 58 || paperWidth == 80) { "Unsupported paper width: $paperWidth" }
        val width = if (paperWidth == 58) 32 else 48
        val singleDivider = "-".repeat(width)
        val doubleDivider = "=".repeat(width)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

        val title = when (type) {
            SessionReportType.SUMMARY -> "RAPPORT DE CLOTURE - RESUME"
            SessionReportType.DETAILED -> "RAPPORT DE CLOTURE - DETAIL"
        }

        val rawBody = buildString {
            establishmentName.trim().takeIf(String::isNotBlank)?.let {
                appendLine(center(it, width))
            }
            appendLine(center(title, width))
            appendLine(if (type == SessionReportType.DETAILED) singleDivider else doubleDivider)
            appendLine()
            val sessionLabel = if (type == SessionReportType.DETAILED) "Session :" else "Session N° :"
            appendLine(columns(sessionLabel, "#${report.session.id}", width))
            appendLine(columns("Caissier :", report.cashierName, width))
            appendLine(columns("Ouverture :", dateFormat.format(Date(report.session.openedAtEpochMilliseconds)), width))
            appendLine(columns("Clôture :", dateFormat.format(Date(requireNotNull(report.session.closedAtEpochMilliseconds))), width))
            appendLine()

            if (type == SessionReportType.DETAILED) {
                appendLine(singleDivider)
                appendLine(center("VENTES PAYÉES", width))
                appendLine(singleDivider)
                if (report.sales.isEmpty()) {
                    appendLine("Aucune vente payée")
                } else {
                    report.sales.forEachIndexed { index, sale ->
                        if (index > 0) {
                            appendLine(singleDivider)
                        }
                        val orderHeader = when {
                            sale.orderNumber.startsWith("Commande", ignoreCase = true) -> sale.orderNumber
                            sale.orderNumber.startsWith("#") -> "Commande ${sale.orderNumber}"
                            else -> "Commande #${sale.orderNumber}"
                        }
                        val dateStr = dateFormat.format(Date(sale.paidAtEpochMilliseconds))
                        val headerLine = if (orderHeader.length + dateStr.length + 1 <= width) {
                            columns(orderHeader, dateStr, width)
                        } else {
                            "$orderHeader\n$dateStr"
                        }
                        appendLine(headerLine)
                        val paymentMethodsStr = sale.paymentMethods.joinToString(" + ", transform = ::paymentLabel)
                        if (paymentMethodsStr.isNotBlank()) {
                            appendLine("Paiement : $paymentMethodsStr")
                        }
                        if (sale.items.isNotEmpty()) {
                            sale.items.forEach { item ->
                                appendLine("${item.quantity} x ${item.productName}")
                            }
                        }
                        appendLine("Total commande : ${money(sale.totalCentimes)}")
                    }
                }
                appendLine()
            }

            val summaryTitle = if (type == SessionReportType.DETAILED) "RÉCAPITULATIF" else "VENTES"
            appendLine(singleDivider)
            appendLine(center(summaryTitle, width))
            appendLine(singleDivider)
            appendLine(columns("Nombre de ventes :", report.completedSalesCount.toString(), width))
            appendLine(columns("Articles vendus :", report.totalItemsSold.toString(), width))
            appendLine()

            appendLine(columns("Ventes espèces :", money(report.cashSalesCentimes), width))
            appendLine(columns("Ventes carte :", money(report.cardSalesCentimes), width))

            // Other payment methods if any
            PaymentMethod.entries.forEach { method ->
                if (method != PaymentMethod.CASH && method != PaymentMethod.CARD) {
                    report.paymentTotals[method]?.takeIf { it != 0L }?.let { total ->
                        appendLine(columns("Ventes ${paymentLabel(method).lowercase()} :", money(total), width))
                    }
                }
            }

            appendLine()
            appendLine(doubleDivider)
            appendLine(columns("TOTAL VENTES :", money(report.grandTotalSalesCentimes), width))
            appendLine(doubleDivider)

            // Cash movements section (CAISSE)
            // Rule: Only display if cashIn != 0 or cashOut != 0.
            // Only show each line when its value is non-zero.
            if (report.cashInCentimes > 0L || report.cashOutCentimes > 0L) {
                appendLine()
                appendLine(singleDivider)
                appendLine(center("CAISSE", width))
                appendLine(singleDivider)
                if (report.cashInCentimes > 0L) {
                    appendLine(columns("Entrées espèces :", "+${money(report.cashInCentimes)}", width))
                }
                if (report.cashOutCentimes > 0L) {
                    appendLine(columns("Sorties espèces :", "-${money(report.cashOutCentimes)}", width))
                }
            }

            // Expected cash (always printed)
            // Formula: Cash sales + Cash inflows - Cash outflows
            val expectedCash = report.expectedCashCentimes
            appendLine()
            appendLine(doubleDivider)
            appendLine(columns("ESPECES ATTENDUES :", money(expectedCash), width))
            appendLine(doubleDivider)

            // Cancelled sales if any
            if (report.cancelledSalesCount > 0) {
                appendLine()
                appendLine(singleDivider)
                appendLine(center("ANNULATIONS", width))
                appendLine(singleDivider)
                appendLine(columns("Ventes annulées :", report.cancelledSalesCount.toString(), width))
                appendLine(columns("Montant annulé :", money(report.cancelledSalesCentimes), width))
            }

            // Closing note if present and not blank
            val note = report.session.closingNote?.trim()
            if (!note.isNullOrBlank()) {
                appendLine()
                appendLine(singleDivider)
                appendLine(center("NOTE DE CLOTURE", width))
                appendLine(singleDivider)
                appendLine(note)
            }
        }.trimEnd()

        val body = ensureCp858Encodable(rawBody)

        if (!FrenchEscPosEncoder.canEncode(body)) {
            return EscPosFormatResult.Failure(
                "Le rapport contient des caractères non pris en charge par ESC/POS CP858",
                PrintErrorCategory.UNSUPPORTED_CHARACTERS
            )
        }

        val output = ByteArrayOutputStream()
        output.write(EscPosCommands.INIT_CP858)
        output.write(EscPosCommands.ALIGN_CENTER)
        establishmentName.trim().takeIf(String::isNotBlank)?.let {
            output.write(it.take(width).toByteArray(FrenchEscPosEncoder.CHARSET))
            output.write('\n'.code)
        }
        output.write(EscPosCommands.BOLD_ON)
        output.write("$title\n".toByteArray(FrenchEscPosEncoder.CHARSET))
        output.write(EscPosCommands.BOLD_OFF)
        output.write(EscPosCommands.ALIGN_LEFT)
        val bodyAfterTitle = body.substringAfter("$title\n", body)
        val expectedCashLine = columns("ESPECES ATTENDUES :", money(report.expectedCashCentimes), width)

        if (bodyAfterTitle.contains(expectedCashLine)) {
            val beforeExpected = bodyAfterTitle.substringBefore(expectedCashLine)
            val afterExpected = bodyAfterTitle.substringAfter(expectedCashLine).removePrefix("\n")
            output.write(beforeExpected.toByteArray(FrenchEscPosEncoder.CHARSET))
            output.write(EscPosCommands.BOLD_ON)
            output.write(EscPosCommands.DOUBLE_HEIGHT)
            output.write(expectedCashLine.toByteArray(FrenchEscPosEncoder.CHARSET))
            output.write('\n'.code)
            output.write(EscPosCommands.NORMAL_SIZE)
            output.write(EscPosCommands.BOLD_OFF)
            output.write(afterExpected.toByteArray(FrenchEscPosEncoder.CHARSET))
        } else {
            output.write(bodyAfterTitle.toByteArray(FrenchEscPosEncoder.CHARSET))
        }

        output.write(EscPosCommands.FEED_AND_CUT)
        EscPosFormatResult.Success(output.toByteArray())
    }.getOrElse {
        EscPosFormatResult.Failure(
            it.message ?: "Impossible de formater le rapport de clôture",
            PrintErrorCategory.TRANSPORT_FAILURE
        )
    }

    private fun paymentLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Espèces"
        PaymentMethod.CARD -> "Carte / TPE"
        PaymentMethod.CARNET_CLIENT -> "Carnet client"
        PaymentMethod.MOBILE_QR -> "Mobile / QR"
    }

    private fun money(value: Long): String {
        val absolute = kotlin.math.abs(value)
        val sign = if (value < 0L) "-" else ""
        return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')} DH"
    }

    private fun columns(left: String, right: String, width: Int): String {
        if (right.length >= width) return right.take(width)
        val availableLeft = (width - right.length - 1).coerceAtLeast(0)
        val safeLeft = left.take(availableLeft)
        return safeLeft + " ".repeat((width - safeLeft.length - right.length).coerceAtLeast(1)) + right
    }

    private fun center(value: String, width: Int): String {
        val text = value.take(width)
        return " ".repeat(((width - text.length) / 2).coerceAtLeast(0)) + text
    }

    private fun sanitizeForCp858(text: String): String = text
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace('—', '-')
        .replace('–', '-')
        .replace("…", "...")

    private fun ensureCp858Encodable(text: String): String {
        val sanitized = sanitizeForCp858(text)
        if (FrenchEscPosEncoder.canEncode(sanitized)) return sanitized
        return sanitized.map { ch ->
            if (FrenchEscPosEncoder.canEncode(ch.toString())) ch else '?'
        }.joinToString("")
    }
}
