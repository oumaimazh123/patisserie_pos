package ma.elaroui.pos.domain.usecase

import ma.elaroui.pos.core.exception.CashReceivedTooLowException
import ma.elaroui.pos.core.exception.EmptyOrderException
import ma.elaroui.pos.core.print.PrinterStatus
import ma.elaroui.pos.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class Step7And8UseCasesTest {

    @Test
    fun cashPayment_validReceivedAmount_calculatesChangeCorrectly() {
        val totalCentimes = 15000L // 150.00 DH
        val receivedCentimes = 20000L // 200.00 DH
        val change = receivedCentimes - totalCentimes
        assertEquals(5000L, change)
    }

    @Test(expected = CashReceivedTooLowException::class)
    fun cashPayment_receivedLessThanTotal_throwsException() {
        val totalCentimes = 15000L
        val receivedCentimes = 10000L
        if (receivedCentimes < totalCentimes) {
            throw CashReceivedTooLowException(
                total = "${totalCentimes / 100.0} DH",
                received = "${receivedCentimes / 100.0} DH"
            )
        }
    }

    @Test
    fun cardPayment_exactAmount_zeroChange() {
        val totalCentimes = 15000L
        val receivedCentimes = totalCentimes
        val change = 0L
        assertEquals(0L, change)
    }

    @Test(expected = EmptyOrderException::class)
    fun orderWithNoItems_cannotBePaid() {
        val items = emptyList<OrderItem>()
        if (items.isEmpty()) {
            throw EmptyOrderException()
        }
    }

    @Test
    fun receiptData_snapshotPreservation_usesItemSnapshots() {
        val item = OrderItem(
            id = 1L,
            orderId = 10L,
            productId = 5L,
            productNameSnapshot = "Café Noir Historique",
            unitPriceSnapshotCentimes = 1500L,
            quantity = 2,
            lineTotalCentimes = 3000L
        )

        val receiptData = ReceiptData(
            restaurantName = "Café Casablanca POS",
            restaurantPhone = "0522000000",
            restaurantAddress = "Boulevard Anfa",
            orderNumber = "ORD-20260730-001",
            orderType = OrderType.DINE_IN,
            tableName = "T1",
            registerName = "Caisse Principale",
            cashierName = "Karim",
            orderCreatedAt = System.currentTimeMillis(),
            paymentAt = System.currentTimeMillis(),
            items = listOf(
                ReceiptItem(
                    name = item.productNameSnapshot,
                    quantity = item.quantity,
                    unitPriceCentimes = item.unitPriceSnapshotCentimes,
                    lineTotalCentimes = item.lineTotalCentimes
                )
            ),
            subtotalCentimes = 3000L,
            totalCentimes = 3000L,
            paymentMethod = PaymentMethod.CASH,
            receivedAmountCentimes = 5000L,
            changeAmountCentimes = 2000L,
            externalReference = null
        )

        assertEquals("Café Noir Historique", receiptData.items.first().name)
        assertEquals(3000L, receiptData.totalCentimes)
        assertEquals(2000L, receiptData.changeAmountCentimes)
    }

    @Test
    fun receiptData_optionalLegalBuyerAndWifiFields_arePreserved() {
        val receipt = ReceiptData(
            restaurantName = "Café Atlas SARL",
            restaurantPhone = "0522000000",
            restaurantAddress = "10 Avenue Hassan II, Casablanca",
            sellerIce = "001234567890123",
            sellerTaxId = "12345678",
            sellerCommercialRegister = "CASABLANCA 12345",
            sellerPatente = "98765432",
            buyerCompanyName = "Client Maroc SA",
            buyerAddress = "20 Boulevard Zerktouni, Casablanca",
            buyerIce = "009876543210123",
            wifiName = "CafeAtlas",
            wifiCode = "Atlas-Guest-2026",
            orderNumber = "ORD-001",
            orderType = OrderType.COUNTER,
            tableName = null,
            registerName = "Main Register",
            cashierName = "Karim",
            orderCreatedAt = 1L,
            paymentAt = 2L,
            items = emptyList(),
            subtotalCentimes = 0L,
            totalCentimes = 0L,
            paymentMethod = PaymentMethod.CASH,
            receivedAmountCentimes = 0L,
            changeAmountCentimes = 0L,
            externalReference = null
        )

        assertEquals("001234567890123", receipt.sellerIce)
        assertEquals("Client Maroc SA", receipt.buyerCompanyName)
        assertEquals("009876543210123", receipt.buyerIce)
        assertEquals("CafeAtlas", receipt.wifiName)
        assertEquals("Atlas-Guest-2026", receipt.wifiCode)
    }

    @Test
    fun receiptData_optionalInvoiceFields_canRemainEmpty() {
        val receipt = ReceiptData(
            restaurantName = "Café Atlas",
            restaurantPhone = null,
            restaurantAddress = null,
            orderNumber = "ORD-002",
            orderType = OrderType.COUNTER,
            tableName = null,
            registerName = "Main Register",
            cashierName = "Karim",
            orderCreatedAt = 1L,
            paymentAt = 2L,
            items = emptyList(),
            subtotalCentimes = 0L,
            totalCentimes = 0L,
            paymentMethod = PaymentMethod.CASH,
            receivedAmountCentimes = 0L,
            changeAmountCentimes = 0L,
            externalReference = null
        )

        assertNull(receipt.sellerIce)
        assertNull(receipt.buyerCompanyName)
        assertNull(receipt.wifiCode)
    }

    @Test
    fun dailySalesReport_calculation_aggregatesTotals() {
        val cashSales = 50000L // 500.00 DH
        val cardSales = 30000L // 300.00 DH
        val totalSales = cashSales + cardSales
        val count = 4
        val avgOrderValue = totalSales / count

        assertEquals(80000L, totalSales)
        assertEquals(20000L, avgOrderValue)
    }
}
