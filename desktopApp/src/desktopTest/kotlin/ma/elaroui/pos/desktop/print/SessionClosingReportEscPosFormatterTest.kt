package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.SessionClosingReport
import ma.elaroui.pos.shared.rules.SessionClosingSale
import ma.elaroui.pos.shared.rules.SessionClosingSaleItem
import ma.elaroui.pos.shared.rules.SessionReportType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionClosingReportEscPosFormatterTest {
    @Test
    fun `formatter supports 58 and 80 mm and cuts only at document end`() {
        listOf(58, 80).forEach { width ->
            val formatted = assertIs<EscPosFormatResult.Success>(
                SessionClosingReportEscPosFormatter.format(report(), "PATISSERIE_POS", width)
            )
            assertContentEquals(EscPosCommands.FEED_AND_CUT, formatted.bytes.takeLast(EscPosCommands.FEED_AND_CUT.size).toByteArray())
            assertTrue(formatted.bytes.size > EscPosCommands.FEED_AND_CUT.size)
        }
    }

    @Test
    fun `formatter renders exact detailed product lines with quantities and order totals`() {
        val sampleReport = SessionClosingReport(
            session = RegisterSession(
                id = 42L,
                status = RegisterSessionStatus.CLOSED,
                openingCashCentimes = 50_000L,
                registerId = 1L,
                cashierId = 2L,
                openedAtEpochMilliseconds = 1725816000000L, // 08/09/2026
                closedAtEpochMilliseconds = 1725825000000L,
                expectedCashCentimes = 71_500L,
                countedCashCentimes = 71_500L,
                differenceCentimes = 0L
            ),
            cashierName = "Amina",
            closingUserName = "Owner",
            sales = listOf(
                SessionClosingSale(
                    orderId = 1001L,
                    orderNumber = "1234",
                    paidAtEpochMilliseconds = 1725823800000L,
                    paymentMethods = listOf(PaymentMethod.CASH),
                    totalCentimes = 9_500L,
                    items = listOf(
                        SessionClosingSaleItem("Coca-Cola", 2),
                        SessionClosingSaleItem("Sandwich Poulet", 1),
                        SessionClosingSaleItem("Eau", 3)
                    )
                ),
                SessionClosingSale(
                    orderId = 1002L,
                    orderNumber = "1235",
                    paidAtEpochMilliseconds = 1725824100000L,
                    paymentMethods = listOf(PaymentMethod.CARD),
                    totalCentimes = 12_000L,
                    items = listOf(
                        SessionClosingSaleItem("Petit déjeuner", 1),
                        SessionClosingSaleItem("Jus d'orange", 2)
                    )
                )
            ),
            paymentTotals = mapOf(
                PaymentMethod.CASH to 9_500L,
                PaymentMethod.CARD to 12_000L
            ),
            cashInCentimes = 0L,
            cashOutCentimes = 0L,
            cancelledSalesCount = 0,
            cancelledSalesCentimes = 0L
        )

        listOf(58, 80).forEach { width ->
            val formatted = assertIs<EscPosFormatResult.Success>(
                SessionClosingReportEscPosFormatter.format(sampleReport, "Restaurant Atlas", width, type = SessionReportType.DETAILED)
            )
            val text = String(formatted.bytes, FrenchEscPosEncoder.CHARSET)

            assertTrue(text.contains("RAPPORT DE CLOTURE - DETAIL"), "Must contain DETAILED title for width $width")
            assertTrue(text.contains("Session :"), "Must contain Session : for width $width")
            assertFalse(text.contains("Fermée par"), "Must NOT contain Fermée par")
            assertTrue(text.contains("Commande #1234"), "Must contain Commande #1234 for width $width")
            assertTrue(text.contains("Paiement : Espèces"), "Must contain Paiement : Espèces for width $width")
            assertTrue(text.contains("2 x Coca-Cola"), "Must contain 2 x Coca-Cola for width $width")
            assertTrue(text.contains("1 x Sandwich Poulet"), "Must contain 1 x Sandwich Poulet for width $width")
            assertTrue(text.contains("3 x Eau"), "Must contain 3 x Eau for width $width")
            assertTrue(text.contains("Total commande : 95,00 DH"), "Must contain Total commande : 95,00 DH for width $width")

            assertTrue(text.contains("Commande #1235"), "Must contain Commande #1235 for width $width")
            assertTrue(text.contains("Paiement : Carte / TPE"), "Must contain Paiement : Carte / TPE for width $width")
            assertTrue(text.contains("1 x Petit déjeuner"), "Must contain 1 x Petit déjeuner for width $width")
            assertTrue(text.contains("2 x Jus d'orange"), "Must contain 2 x Jus d'orange for width $width")
            assertTrue(text.contains("Total commande : 120,00 DH"), "Must contain Total commande : 120,00 DH for width $width")

            assertTrue(text.contains("RÉCAPITULATIF"), "Must contain RÉCAPITULATIF for width $width")
            assertTrue(text.contains("TOTAL VENTES"), "Must contain TOTAL VENTES for width $width")
            assertTrue(text.contains("215,00 DH"), "Must contain total 215,00 DH for width $width")

            // Obsolete fields removed:
            assertFalse(text.contains("Fond initial"), "Must NOT contain Fond initial")
            assertFalse(text.contains("Espèces comptées"), "Must NOT contain Espèces comptées")
            assertFalse(text.contains("Écart"), "Must NOT contain Écart")
            assertFalse(text.contains("Laissé en caisse"), "Must NOT contain Laissé en caisse")
            assertFalse(text.contains("Montant retiré"), "Must NOT contain Montant retiré")
        }
    }

    @Test
    fun `summary report does not contain individual orders and handles conditional caisse section`() {
        // Case 1: No cash movements -> CAISSE section must NOT appear
        val reportNoMovements = SessionClosingReport(
            session = RegisterSession(
                id = 50L,
                status = RegisterSessionStatus.CLOSED,
                openingCashCentimes = 20_000L,
                registerId = 1L,
                cashierId = 1L,
                openedAtEpochMilliseconds = 1725816000000L,
                closedAtEpochMilliseconds = 1725825000000L,
                expectedCashCentimes = 0L,
                countedCashCentimes = 0L,
                differenceCentimes = 0L,
                closingNote = "Bonne journée"
            ),
            cashierName = "Mohamed",
            closingUserName = null,
            sales = listOf(
                SessionClosingSale(
                    orderId = 201L,
                    orderNumber = "501",
                    paidAtEpochMilliseconds = 1725820000000L,
                    paymentMethods = listOf(PaymentMethod.CASH),
                    totalCentimes = 15_000L,
                    items = listOf(SessionClosingSaleItem("Tarte", 3))
                )
            ),
            paymentTotals = mapOf(PaymentMethod.CASH to 15_000L),
            cashInCentimes = 0L,
            cashOutCentimes = 0L,
            cancelledSalesCount = 0,
            cancelledSalesCentimes = 0L
        )

        val formatSummary = assertIs<EscPosFormatResult.Success>(
            SessionClosingReportEscPosFormatter.format(reportNoMovements, "Pâtisserie Royale", 80, type = SessionReportType.SUMMARY, isReprint = true)
        )
        val textSummary = String(formatSummary.bytes, FrenchEscPosEncoder.CHARSET)

        assertTrue(textSummary.contains("RAPPORT DE CLOTURE - RESUME"))
        assertFalse(textSummary.contains("DUPLICATA"), "Report must not contain DUPLICATA")
        assertFalse(textSummary.contains("REIMPRESSION"), "Report must not contain REIMPRESSION")
        assertTrue(textSummary.contains("Session N° :"))
        assertTrue(textSummary.contains("Caissier :"))
        assertTrue(textSummary.contains("Nombre de ventes :"))
        assertTrue(textSummary.contains("Articles vendus :"))
        assertTrue(textSummary.contains("Ventes espèces :"))
        assertTrue(textSummary.contains("TOTAL VENTES :"))
        assertTrue(textSummary.contains("ESPECES ATTENDUES :"))
        assertTrue(textSummary.contains("150,00 DH"))
        assertTrue(textSummary.contains("NOTE DE CLOTURE"))
        assertTrue(textSummary.contains("Bonne journée"))

        // Individual order lines must NOT be present
        assertFalse(textSummary.contains("VENTES PAYÉES"))
        assertFalse(textSummary.contains("Commande #501"))
        assertFalse(textSummary.contains("Tarte"))

        // CAISSE section must NOT be present when cashIn == 0 and cashOut == 0
        assertFalse(textSummary.contains("CAISSE"))

        // Case 2: Cash in = 50 DH, Cash out = 0 DH -> CAISSE section with only Entrées espèces
        val reportWithCashIn = reportNoMovements.copy(
            cashInCentimes = 5_000L,
            cashOutCentimes = 0L
        )
        val formatWithCashIn = assertIs<EscPosFormatResult.Success>(
            SessionClosingReportEscPosFormatter.format(reportWithCashIn, "Pâtisserie Royale", 80, type = SessionReportType.SUMMARY)
        )
        val textWithCashIn = String(formatWithCashIn.bytes, FrenchEscPosEncoder.CHARSET)
        assertTrue(textWithCashIn.contains("CAISSE"))
        assertTrue(textWithCashIn.contains("Entrées espèces :"))
        assertTrue(textWithCashIn.contains("+50,00 DH"))
        assertFalse(textWithCashIn.contains("Sorties espèces :"))
        // Expected cash = 150 + 50 = 200 DH
        assertTrue(textWithCashIn.contains("200,00 DH"))
    }

    @Test
    fun `expected cash is styled in bold and double height in binary output`() {
        val sample = report()
        val formatResult = assertIs<EscPosFormatResult.Success>(
            SessionClosingReportEscPosFormatter.format(sample, "Pâtisserie Royale", 80)
        )
        val bytes = formatResult.bytes
        val expectedCashAscii = "ESPECES ATTENDUES :".toByteArray(FrenchEscPosEncoder.CHARSET)

        fun indexOf(pattern: ByteArray): Int {
            for (i in 0..bytes.size - pattern.size) {
                if (pattern.indices.all { bytes[i + it] == pattern[it] }) return i
            }
            return -1
        }

        val textIdx = indexOf(expectedCashAscii)
        assertTrue(textIdx > 0, "ESPECES ATTENDUES must be present in output")

        // Right before expectedCashAscii, BOLD_ON and DOUBLE_HEIGHT must be set
        val boldOn = EscPosCommands.BOLD_ON
        val doubleHeight = EscPosCommands.DOUBLE_HEIGHT
        val normalSize = EscPosCommands.NORMAL_SIZE
        val boldOff = EscPosCommands.BOLD_OFF

        // Check boldOn and doubleHeight exist immediately prior to text
        val prefixSlice = bytes.sliceArray((textIdx - boldOn.size - doubleHeight.size) until textIdx)
        assertContentEquals(boldOn + doubleHeight, prefixSlice, "BOLD_ON and DOUBLE_HEIGHT must precede ESPECES ATTENDUES")

        // Right after expected cash line and newline, NORMAL_SIZE and BOLD_OFF must be restored
        var lineEndIdx = textIdx
        while (lineEndIdx < bytes.size && bytes[lineEndIdx] != 0x0A.toByte()) {
            lineEndIdx++
        }
        assertTrue(lineEndIdx > textIdx, "Newline must follow expected cash line")
        val suffixSlice = bytes.sliceArray((lineEndIdx + 1) until (lineEndIdx + 1 + normalSize.size + boldOff.size))
        assertContentEquals(normalSize + boldOff, suffixSlice, "NORMAL_SIZE and BOLD_OFF must follow ESPECES ATTENDUES")
    }

    @Test
    fun `detailed report ventes payees is compact and has no duplicata on reprint`() {
        val sample = report()
        val formatResult = assertIs<EscPosFormatResult.Success>(
            SessionClosingReportEscPosFormatter.format(sample, "Pâtisserie Royale", 80, type = SessionReportType.DETAILED, isReprint = true)
        )
        val text = String(formatResult.bytes, FrenchEscPosEncoder.CHARSET)

        assertFalse(text.contains("DUPLICATA"), "Must not contain DUPLICATA")
        assertFalse(text.contains("REIMPRESSION"), "Must not contain REIMPRESSION")
        assertTrue(text.contains("VENTES PAYÉES"))
        assertTrue(text.contains("Commande #SALE-1"))
        assertTrue(text.contains("Total commande : 10,00 DH"))
    }

    private fun report() = SessionClosingReport(
        session = RegisterSession(
            id = 1L,
            status = RegisterSessionStatus.CLOSED,
            openingCashCentimes = 1_000L,
            registerId = 1L,
            cashierId = 2L,
            openedAtEpochMilliseconds = 1_000L,
            closedAtEpochMilliseconds = 2_000L,
            expectedCashCentimes = 2_000L,
            countedCashCentimes = 2_000L,
            differenceCentimes = 0L
        ),
        cashierName = "Amina",
        closingUserName = "Owner",
        sales = listOf(SessionClosingSale(1L, "SALE-1", 1_500L, listOf(PaymentMethod.CASH), 1_000L)),
        paymentTotals = mapOf(PaymentMethod.CASH to 1_000L),
        cashInCentimes = 0L,
        cashOutCentimes = 0L,
        cancelledSalesCount = 0,
        cancelledSalesCentimes = 0L
    )
}
