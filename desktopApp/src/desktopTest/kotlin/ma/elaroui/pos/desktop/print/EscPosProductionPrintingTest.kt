package ma.elaroui.pos.desktop.print

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformLogger
import ma.elaroui.pos.shared.domain.*

class EscPosProductionPrintingTest {
    private val company = ReceiptCompany(
        name = "Café Atlas",
        address = "Rabat",
        phone = "0537000000",
        ice = "001122334455667"
    )
    private val order = Order(
        id = 41,
        number = "ORD-0041",
        type = OrderType.DINE_IN,
        status = OrderStatus.COMPLETED,
        lines = listOf(OrderLine(1, "Café crème", 1_500, 2, 1_000)),
        subtotalCentimes = 3_000,
        discountCentimes = 0,
        taxCentimes = 273,
        totalCentimes = 3_000,
        tableId = 5,
        registerSessionId = 2,
        cashierId = 3
    )

    @Test
    fun centralizedEscPosCommandsHaveExpectedBytes() {
        assertContentEquals(byteArrayOf(0x1B, 0x40), EscPosCommands.INITIALIZE)
        assertContentEquals(byteArrayOf(0x1B, 0x61, 0x01), EscPosCommands.ALIGN_CENTER)
        assertContentEquals(byteArrayOf(0x1B, 0x45, 0x01), EscPosCommands.BOLD_ON)
        assertContentEquals(byteArrayOf(0x1D, 0x21, 0x10), EscPosCommands.DOUBLE_HEIGHT)
        assertContentEquals(byteArrayOf(0x1D, 0x21, 0x11), EscPosCommands.DOUBLE_SIZE)
        assertContentEquals(byteArrayOf(0x1D, 0x56, 0x00), EscPosCommands.CUT)
        assertContentEquals(
            EscPosCommands.lineFeeds(EscPosCommands.RECEIPT_END_FEED_LINES) + EscPosCommands.CUT,
            EscPosCommands.FEED_AND_CUT
        )
        assertContentEquals(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0x7D), EscPosCommands.cashDrawerPulse())
    }

    @Test
    fun linuxFormatterProducesCp858ReceiptForFrenchTextAtBothWidths() {
        listOf(58 to 32, 80 to 48).forEach { (paperWidth, columns) ->
            val request = EscPosPrintRequest(
                order, company, TicketKind.CUSTOMER, paperWidth,
                paymentMethod = PaymentMethod.CASH,
                receivedCentimes = 5_000,
                changeCentimes = 2_000,
                cashierName = "Khalid",
                tableLabel = "Table Terrasse 5",
                areaLabel = "Terrasse"
            )
            val formatted = LinuxEscPosFormatter.format(request)
            assertTrue(formatted is EscPosFormatResult.Success, formatted.toString())
            val result = formatted
            assertTrue(result.bytes.startsWith(EscPosCommands.INIT_CP858))
            assertTrue(result.bytes.endsWith(EscPosCommands.FEED_AND_CUT))

            val preview = ThermalTicketRenderer.previewText(
                order, company, TicketKind.CUSTOMER, paperWidth,
                paymentMethod = PaymentMethod.CASH, receivedCentimes = 5_000, changeCentimes = 2_000,
                cashierName = "Khalid", tableLabel = "Table Terrasse 5", areaLabel = "Terrasse"
            )
            assertTrue(preview.lines().all { it.length <= columns })
            val encodedAccent = "Café crème".toByteArray(Charset.forName("IBM00858"))
            assertTrue(result.bytes.indexOfSubArray(encodedAccent) >= 0)
        }
    }

    @Test
    fun customerReceiptAndDuplicateEndWithWifiThenFooterFeedsAndOneCut() {
        val companyWithWifi = company.copy(wifiName = "PATISSERIE_POS", wifiCode = "client-2026")
        val footer = "Merci de votre visite !".toByteArray(Charset.forName("IBM00858"))
        val wifi = "Wi-Fi: PATISSERIE_POS".toByteArray(Charset.forName("IBM00858"))

        listOf(false, true).forEach { isReprint ->
            val bytes = assertIs<EscPosFormatResult.Success>(
                LinuxEscPosFormatter.format(
                    EscPosPrintRequest(order, companyWithWifi, TicketKind.CUSTOMER, 80, isReprint = isReprint)
                )
            ).bytes

            val wifiIndex = bytes.indexOfSubArray(wifi)
            val footerIndex = bytes.indexOfSubArray(footer)
            val cutIndexes = bytes.indexesOfSubArray(EscPosCommands.CUT)

            assertTrue(wifiIndex >= 0)
            assertTrue(footerIndex > wifiIndex)
            assertEquals(listOf(bytes.size - EscPosCommands.CUT.size), cutIndexes)
            val trailingBytes = bytes.copyOfRange(footerIndex + footer.size, cutIndexes.single())
            assertTrue(trailingBytes.size >= EscPosCommands.RECEIPT_END_FEED_LINES)
        }
    }

    @Test
    fun linuxCustomerReceiptEmphasizesStoreAndProductsWithRightAlignedPriceOnSameLine() {
        val bytes = assertIs<EscPosFormatResult.Success>(
            LinuxEscPosFormatter.format(EscPosPrintRequest(order, company, TicketKind.CUSTOMER, 80))
        ).bytes
        val charset = Charset.forName("IBM00858")
        val store = company.name.toByteArray(charset)
        val product = "2 x Café crème".toByteArray(charset)
        val price = "30.00 DH".toByteArray(charset)
        val storeIndex = bytes.indexOfSubArray(store)
        val productIndex = bytes.indexOfSubArray(product)
        val priceIndex = bytes.indexOfSubArray(price)
        val boldBeforeStore = bytes.lastIndexOfSubArray(EscPosCommands.BOLD_ON, storeIndex)
        val heightBeforeStore = bytes.lastIndexOfSubArray(EscPosCommands.DOUBLE_HEIGHT, storeIndex)

        assertTrue(storeIndex > bytes.indexOfSubArray(EscPosCommands.ALIGN_CENTER))
        assertTrue(boldBeforeStore in 0 until storeIndex)
        assertTrue(heightBeforeStore in 0 until storeIndex)
        assertTrue(productIndex > 0)
        assertTrue(priceIndex > productIndex)

        val previewLines = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, 80).lines()
        val nameLine = previewLines.first { it.contains("2 x Café crème") }
        val calcLine = previewLines.first { it.contains("15.00 DH x 2") }
        assertEquals("2 x Café crème", nameLine)
        assertTrue(calcLine.startsWith("   15.00 DH x 2"))
        assertTrue(calcLine.endsWith("30.00 DH"))
        assertEquals(48, calcLine.length)
    }

    @Test
    fun kitchenTicketContainsOperationalLocationAndInstructionsButNoFiscalDataOrPrices() {
        val request = EscPosPrintRequest(
            order, company, TicketKind.KITCHEN, 80,
            cashierName = "Khalid",
            tableLabel = "T5",
            areaLabel = "Terrasse",
            specialInstructions = "Sans sucre, servir très chaud"
        )
        val preview = ThermalTicketRenderer.previewText(
            order, company, TicketKind.KITCHEN, 80,
            cashierName = "Khalid", tableLabel = "T5", areaLabel = "Terrasse",
            specialInstructions = "Sans sucre, servir très chaud"
        )

        assertTrue(preview.contains("T5"))
        assertTrue(preview.contains("TERRASSE", ignoreCase = true))
        assertTrue(preview.contains("Sans sucre"))
        assertFalse(preview.contains(company.ice))
        assertFalse(preview.contains("30.00 DH"))
        assertIs<EscPosFormatResult.Success>(LinuxEscPosFormatter.format(request))
    }

    @Test
    fun arabicIsRejectedExplicitlyInsteadOfBeingCorrupted() {
        val arabicOrder = order.copy(lines = listOf(OrderLine(2, "شاي بالنعناع", 1_200, 1)))
        val result = assertIs<EscPosFormatResult.Failure>(
            LinuxEscPosFormatter.format(EscPosPrintRequest(arabicOrder, company, TicketKind.KITCHEN, 80))
        )

        assertEquals(PrintErrorCategory.UNSUPPORTED_CHARACTERS, result.category)
        assertTrue(result.message.contains("Arabic"))
    }

    @Test
    fun linuxRawTransportUsesCupsRawAndPreservesBinaryBytes() {
        var arguments = emptyList<String>()
        var submitted = ByteArray(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { args, input, _ ->
                arguments = args
                submitted = input ?: ByteArray(0)
                CommandResult(0, "request id is Receipt-1")
            },
            logger = RecordingLogger()
        )
        val binary = EscPosCommands.INITIALIZE + byteArrayOf(0x00, 0x7F, 0xFF.toByte()) + EscPosCommands.FEED_AND_CUT

        val result = service.printRaw("Receipt Printer", binary, PrinterRole.CASHIER_RECEIPT)

        assertTrue(result.success)
        assertEquals(listOf("lp", "-d", "Receipt Printer", "-o", "raw"), arguments)
        assertContentEquals(binary, submitted)
    }

    @Test
    fun drawerPulseUsesReceiptPrinterAndRawTransport() {
        var submitted = ByteArray(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, input, _ ->
                submitted = input ?: ByteArray(0)
                CommandResult(0, "request accepted")
            },
            logger = RecordingLogger()
        )

        assertTrue(service.openCashDrawer("Receipt Printer").success)
        assertContentEquals(EscPosCommands.cashDrawerPulse(), submitted)
    }

    @Test
    fun transportMapsPermissionAndOfflineFailures() {
        val permissionService = LinuxPrinterService(
            CommandExecutor { _, _, _ -> CommandResult(1, standardError = "Permission denied") },
            RecordingLogger()
        )
        val offlineService = LinuxPrinterService(
            CommandExecutor { _, _, _ -> CommandResult(1, standardError = "printer is offline") },
            RecordingLogger()
        )

        assertEquals(PrintErrorCategory.PERMISSION_DENIED, permissionService.printRaw("Receipt", byteArrayOf(1)).errorCategory)
        assertEquals(PrintErrorCategory.OFFLINE, offlineService.printRaw("Receipt", byteArrayOf(1)).errorCategory)
    }

    @Test
    fun duplicateGuardSuppressesImmediateDuplicateAndAllowsRetryAfterFailure() {
        var now = 1_000L
        val guard = PrintJobDeduplicator(retentionMillis = 1_500L) { now }

        assertTrue(guard.acquire("receipt:41"))
        assertFalse(guard.acquire("receipt:41"))
        guard.releaseAfterFailure("receipt:41")
        assertTrue(guard.acquire("receipt:41"))
        now += 1_501L
        assertTrue(guard.acquire("receipt:41"))
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)
    private fun ByteArray.endsWith(suffix: ByteArray): Boolean = size >= suffix.size && copyOfRange(size - suffix.size, size).contentEquals(suffix)
    private fun ByteArray.indexOfSubArray(value: ByteArray): Int = indices.firstOrNull { index ->
        index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
    } ?: -1

    private fun ByteArray.indexesOfSubArray(value: ByteArray): List<Int> = indices.filter { index ->
        index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
    }

    private fun ByteArray.lastIndexOfSubArray(value: ByteArray, before: Int): Int =
        (0 until before).lastOrNull { index ->
            index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
        } ?: -1

    private class RecordingLogger : PlatformLogger {
        override fun log(entry: LogEntry) = Unit
    }
}
