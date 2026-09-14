package ma.elaroui.pos.desktop.print

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.rules.SessionClosingReport

object SessionClosingReportEscPosFormatter {
    fun format(
        report: SessionClosingReport,
        establishmentName: String,
        paperWidth: Int
    ): EscPosFormatResult = runCatching {
        require(paperWidth == 58 || paperWidth == 80) { "Unsupported paper width: $paperWidth" }
        val width = if (paperWidth == 58) 32 else 48
        val divider = "-".repeat(width)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val rawBody = buildString {
            establishmentName.trim().takeIf(String::isNotBlank)?.let {
                appendLine(center(it, width))
            }
            appendLine(center("RAPPORT DE CLÔTURE", width))
            appendLine(divider)
            appendLine(columns("Session", "#${report.session.id}", width))
            appendLine(columns("Caissier", report.cashierName, width))
            appendLine(columns("Ouverture", dateFormat.format(Date(report.session.openedAtEpochMilliseconds)), width))
            appendLine(columns("Clôture", dateFormat.format(Date(requireNotNull(report.session.closedAtEpochMilliseconds))), width))
            report.closingUserName?.let { appendLine(columns("Fermée par", it, width)) }
            appendLine(columns("Fond initial", money(report.session.openingCashCentimes), width))
            appendLine(divider)
            appendLine(center("VENTES PAYÉES", width))
            if (report.sales.isEmpty()) {
                appendLine("Aucune vente payée")
            } else {
                report.sales.forEachIndexed { index, sale ->
                    if (index > 0) {
                        appendLine(divider)
                    }
                    val orderHeader = when {
                        sale.orderNumber.startsWith("Commande", ignoreCase = true) -> sale.orderNumber
                        sale.orderNumber.startsWith("#") -> "Commande ${sale.orderNumber}"
                        else -> "Commande #${sale.orderNumber}"
                    }
                    appendLine(orderHeader)
                    val dateStr = dateFormat.format(Date(sale.paidAtEpochMilliseconds))
                    val paymentMethodsStr = sale.paymentMethods.joinToString("+", transform = ::paymentLabel)
                    if (paymentMethodsStr.isNotBlank()) {
                        appendLine(columns(dateStr, paymentMethodsStr, width))
                    } else {
                        appendLine(dateStr)
                    }
                    appendLine()
                    if (sale.items.isNotEmpty()) {
                        sale.items.forEach { item ->
                            appendLine("${item.quantity} x ${item.productName}")
                        }
                        appendLine()
                    }
                    appendLine("Total commande : ${money(sale.totalCentimes)}")
                }
            }
            appendLine(divider)
            appendLine(center("RÉCAPITULATIF", width))
            appendLine(columns("Nombre de ventes", report.completedSalesCount.toString(), width))
            PaymentMethod.entries.forEach { method ->
                report.paymentTotals[method]?.takeIf { it != 0L }?.let { total ->
                    appendLine(columns(paymentLabel(method), money(total), width))
                }
            }
            appendLine(columns("TOTAL VENTES", money(report.grandTotalSalesCentimes), width))
            appendLine(columns("Entrées espèces", money(report.cashInCentimes), width))
            appendLine(columns("Retraits espèces", money(report.cashOutCentimes), width))
            appendLine(columns("Espèces attendues", money(report.session.expectedCashCentimes ?: 0L), width))
            appendLine(columns("Espèces comptées", money(report.session.countedCashCentimes ?: 0L), width))
            appendLine(columns("Écart", money(report.session.differenceCentimes ?: 0L), width))
            report.session.leftInDrawerCentimes?.let {
                appendLine(columns("Laissé en caisse", money(it), width))
            }
            if ((report.session.removedAmountCentimes ?: 0L) > 0L) {
                appendLine(columns("Montant retiré", money(report.session.removedAmountCentimes ?: 0L), width))
                report.session.remittanceDestination?.takeIf(String::isNotBlank)?.let {
                    appendLine(columns("Destination", it, width))
                }
                report.session.remittanceReference?.takeIf(String::isNotBlank)?.let {
                    appendLine(columns("Réf. Enveloppe", it, width))
                }
            }
            appendLine(divider)
            appendLine(columns("Ventes annulées", report.cancelledSalesCount.toString(), width))
            if (report.cancelledSalesCount > 0) {
                appendLine(columns("Montant annulé", money(report.cancelledSalesCentimes), width))
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
        output.write("RAPPORT DE CLÔTURE\n".toByteArray(FrenchEscPosEncoder.CHARSET))
        output.write(EscPosCommands.BOLD_OFF)
        output.write(EscPosCommands.ALIGN_LEFT)
        output.write(body.substringAfter("RAPPORT DE CLÔTURE\n", body).toByteArray(FrenchEscPosEncoder.CHARSET))
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
