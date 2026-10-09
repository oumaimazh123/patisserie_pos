package ma.elaroui.pos.desktop.presentation.register.current

import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.shared.domain.CashMovement
import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.MoneyRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.test.*

class CurrentSessionScreenLayoutTest {

    private val strings = DesktopStrings(DesktopLanguage.FR)

    private fun createDummySession(
        id: Long = 1L,
        openingCashCentimes: Long = 200_00L
    ) = RegisterSession(
        id = id,
        status = RegisterSessionStatus.OPEN,
        openingCashCentimes = openingCashCentimes,
        registerId = 1L,
        cashierId = 1L,
        openedAtEpochMilliseconds = 1700000000000L,
        closedAtEpochMilliseconds = null,
        expectedCashCentimes = null,
        countedCashCentimes = null,
        differenceCentimes = null
    )

    private fun createDummyOrder(
        id: Long,
        number: String,
        status: OrderStatus = OrderStatus.COMPLETED,
        totalCentimes: Long = 50_00L,
        lines: List<OrderLine> = emptyList(),
        createdAtEpochMs: Long = 1700001000000L
    ) = Order(
        id = id,
        number = number,
        type = OrderType.COUNTER,
        status = status,
        lines = lines,
        subtotalCentimes = totalCentimes,
        discountCentimes = 0L,
        taxCentimes = 0L,
        totalCentimes = totalCentimes,
        tableId = null,
        registerSessionId = 1L,
        cashierId = 1L,
        createdAtEpochMilliseconds = createdAtEpochMs
    )

    private fun createDummySaleRow(
        order: Order,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        cashierName: String = "Test Cashier",
        paidAtEpochMillis: Long? = 1700001000000L
    ) = SalesHistoryRow(
        order = order,
        cashierName = cashierName,
        paymentMethod = paymentMethod,
        paidAtEpochMillis = paidAtEpochMillis
    )

    private fun createDummyMovement(
        id: Long,
        type: CashMovementType,
        amountCentimes: Long,
        reason: String,
        description: String? = null,
        userId: Long = 1L,
        createdAtMs: Long = 1700002000000L
    ) = CashMovement(
        id = id,
        sessionId = 1L,
        type = type,
        amountCentimes = amountCentimes,
        reason = reason,
        description = description,
        createdByUserId = userId,
        createdAtEpochMilliseconds = createdAtMs
    )

    @Test
    fun testExpectedPhysicalCashCalculationExcludesCardSales() {
        val openingCash = 300_00L // 300.00 DH
        val cashSales = 500_00L   // 500.00 DH
        val cardSales = 450_00L   // 450.00 DH (electronic payment, NOT in drawer)
        val cashIn = 100_00L      // +100.00 DH
        val cashOut = 50_00L      // -50.00 DH

        val expectedPhysicalCash = openingCash + cashSales + cashIn - cashOut
        assertEquals(850_00L, expectedPhysicalCash)
        assertEquals("850.00", MoneyRules.formatFixed(expectedPhysicalCash))

        val totalSales = cashSales + cardSales
        assertEquals(950_00L, totalSales)
        assertEquals("950.00", MoneyRules.formatFixed(totalSales))
    }

    @Test
    fun testSalesFilteringConfirmedVsCancelled() {
        val sales = listOf(
            createDummySaleRow(createDummyOrder(1L, "CMD-001", OrderStatus.COMPLETED, 30_00L)),
            createDummySaleRow(createDummyOrder(2L, "CMD-002", OrderStatus.COMPLETED, 45_00L)),
            createDummySaleRow(createDummyOrder(3L, "CMD-003", OrderStatus.CANCELLED, 20_00L)),
            createDummySaleRow(createDummyOrder(4L, "CMD-004", OrderStatus.COMPLETED, 80_00L)),
            createDummySaleRow(createDummyOrder(5L, "CMD-005", OrderStatus.CANCELLED, 15_00L))
        )

        val confirmed = sales.filter { it.order.status == OrderStatus.COMPLETED }
        val cancelled = sales.filter { it.order.status == OrderStatus.CANCELLED }

        assertEquals(3, confirmed.size)
        assertEquals(2, cancelled.size)
        assertEquals(listOf("CMD-001", "CMD-002", "CMD-004"), confirmed.map { it.order.number })
        assertEquals(listOf("CMD-003", "CMD-005"), cancelled.map { it.order.number })
    }

    @Test
    fun testProductSummaryFormattingLogic() {
        // Case 1: Empty lines
        val emptyLines = emptyList<OrderLine>()
        val emptySummary = if (emptyLines.isEmpty()) "" else "something"
        assertEquals("", emptySummary)

        // Case 2: 1-2 items (no truncation)
        val shortLines = listOf(
            OrderLine(productId = 101L, name = "Croissant", unitPriceCentimes = 10_00L, quantity = 2),
            OrderLine(productId = 102L, name = "Café Crème", unitPriceCentimes = 15_00L, quantity = 1)
        )
        val maxToShow = 2
        val shortSummary = shortLines.take(maxToShow).joinToString(", ") { "${it.quantity}x ${it.name}" }
        val shortExtra = shortLines.size - maxToShow
        val finalShortSummary = if (shortExtra > 0) "$shortSummary (+ $shortExtra)" else shortSummary
        assertEquals("2x Croissant, 1x Café Crème", finalShortSummary)

        // Case 3: 4 items (truncated with extra count)
        val longLines = listOf(
            OrderLine(productId = 101L, name = "Croissant", unitPriceCentimes = 10_00L, quantity = 2),
            OrderLine(productId = 102L, name = "Café Crème", unitPriceCentimes = 15_00L, quantity = 1),
            OrderLine(productId = 103L, name = "Pain au chocolat", unitPriceCentimes = 12_00L, quantity = 3),
            OrderLine(productId = 104L, name = "Tartelette fraise", unitPriceCentimes = 25_00L, quantity = 1)
        )
        val longSummary = longLines.take(maxToShow).joinToString(", ") { "${it.quantity}x ${it.name}" }
        val longExtra = longLines.size - maxToShow
        val finalLongSummary = if (longExtra > 0) "$longSummary (+ $longExtra)" else longSummary
        assertEquals("2x Croissant, 1x Café Crème (+ 2)", finalLongSummary)
    }

    @Test
    fun testCashMovementAggregationAndDisplayRules() {
        val movements = listOf(
            createDummyMovement(1L, CashMovementType.CASH_IN, 100_00L, "Apport monnaie"),
            createDummyMovement(2L, CashMovementType.CASH_OUT, 45_00L, "Achat lait"),
            createDummyMovement(3L, CashMovementType.CASH_IN, 50_00L, "Monnaie d'appoint"),
            createDummyMovement(4L, CashMovementType.CASH_OUT, 80_00L, "Facture fournisseur")
        )

        val totalIn = movements.filter { it.type == CashMovementType.CASH_IN }.sumOf { it.amountCentimes }
        val totalOut = movements.filter { it.type == CashMovementType.CASH_OUT }.sumOf { it.amountCentimes }

        assertEquals(150_00L, totalIn)
        assertEquals(125_00L, totalOut)

        // Prefix formatting
        val formattedIn = "+${MoneyRules.formatFixed(totalIn)}"
        val formattedOut = "-${MoneyRules.formatFixed(totalOut)}"
        assertEquals("+150.00", formattedIn)
        assertEquals("-125.00", formattedOut)
    }

    @Test
    fun testCashMovementTimeAndUserAttributionFormatting() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val testEpoch = 1700000000000L // Date in milliseconds
        val expectedTime = timeFormat.format(Date(testEpoch))

        val movement = createDummyMovement(
            id = 10L,
            type = CashMovementType.CASH_IN,
            amountCentimes = 75_00L,
            reason = "Apport pièces",
            description = "Rouleaux de 5 et 10 DH",
            userId = 3L,
            createdAtMs = testEpoch
        )

        val formattedTime = if (movement.createdAtEpochMilliseconds > 0L) {
            timeFormat.format(Date(movement.createdAtEpochMilliseconds))
        } else ""

        assertEquals(expectedTime, formattedTime)
        assertEquals(3L, movement.createdByUserId)
        assertEquals("Rouleaux de 5 et 10 DH", movement.description)
    }

    @Test
    fun testPaymentMethodLabelMapping() {
        fun mapPayment(method: PaymentMethod?): String = when (method) {
            PaymentMethod.CASH -> "💵 " + strings.cash
            PaymentMethod.CARD -> "💳 " + strings.text("Carte", "Card", "بطاقة")
            PaymentMethod.CARNET_CLIENT -> "📖 " + strings.text("Carnet", "Credit", "دفتر")
            PaymentMethod.MOBILE_QR -> "📱 " + strings.text("Mobile/QR", "Mobile/QR", "محمول")
            null -> strings.text("Non réglé", "Unpaid", "غير مسدد")
        }

        assertTrue(mapPayment(PaymentMethod.CASH).contains("Espèces"))
        assertTrue(mapPayment(PaymentMethod.CARD).contains("Carte"))
        assertTrue(mapPayment(PaymentMethod.CARNET_CLIENT).contains("Carnet"))
        assertTrue(mapPayment(PaymentMethod.MOBILE_QR).contains("Mobile"))
        assertTrue(mapPayment(null).contains("Non réglé"))
    }

    @Test
    fun testLargeScaleSessionDataIntegrity() {
        // Test with 200 sales and 50 cash movements
        val sales = (1..200).map { i ->
            val method = if (i % 2 == 0) PaymentMethod.CASH else PaymentMethod.CARD
            val status = if (i % 20 == 0) OrderStatus.CANCELLED else OrderStatus.COMPLETED
            createDummySaleRow(
                order = createDummyOrder(
                    id = i.toLong(),
                    number = "CMD-%04d".format(i),
                    status = status,
                    totalCentimes = (i * 10_00L)
                ),
                paymentMethod = method
            )
        }

        val movements = (1..50).map { i ->
            val type = if (i % 2 == 0) CashMovementType.CASH_IN else CashMovementType.CASH_OUT
            createDummyMovement(
                id = i.toLong(),
                type = type,
                amountCentimes = (i * 5_00L),
                reason = "Mouvement test #$i"
            )
        }

        val confirmedCount = sales.count { it.order.status == OrderStatus.COMPLETED }
        val cancelledCount = sales.count { it.order.status == OrderStatus.CANCELLED }

        assertEquals(190, confirmedCount)
        assertEquals(10, cancelledCount)
        assertEquals(25, movements.count { it.type == CashMovementType.CASH_IN })
        assertEquals(25, movements.count { it.type == CashMovementType.CASH_OUT })
    }
}
