package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.SessionClosingReport
import ma.elaroui.pos.shared.rules.SessionClosingSale
import ma.elaroui.pos.shared.rules.SessionClosingSaleItem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
                SessionClosingReportEscPosFormatter.format(sampleReport, "Restaurant Atlas", width)
            )
            val text = String(formatted.bytes, FrenchEscPosEncoder.CHARSET)

            assertTrue(text.contains("Commande #1234"), "Must contain Commande #1234 for width $width")
            assertTrue(text.contains("2 x Coca-Cola"), "Must contain 2 x Coca-Cola for width $width")
            assertTrue(text.contains("1 x Sandwich Poulet"), "Must contain 1 x Sandwich Poulet for width $width")
            assertTrue(text.contains("3 x Eau"), "Must contain 3 x Eau for width $width")
            assertTrue(text.contains("Total commande : 95,00 DH"), "Must contain Total commande : 95,00 DH for width $width")

            assertTrue(text.contains("Commande #1235"), "Must contain Commande #1235 for width $width")
            assertTrue(text.contains("1 x Petit déjeuner"), "Must contain 1 x Petit déjeuner for width $width")
            assertTrue(text.contains("2 x Jus d'orange"), "Must contain 2 x Jus d'orange for width $width")
            assertTrue(text.contains("Total commande : 120,00 DH"), "Must contain Total commande : 120,00 DH for width $width")

            assertTrue(text.contains("RÉCAPITULATIF"), "Must contain RÉCAPITULATIF for width $width")
            assertTrue(text.contains("TOTAL VENTES"), "Must contain TOTAL VENTES for width $width")
            assertTrue(text.contains("215,00 DH"), "Must contain total 215,00 DH for width $width")
        }
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
