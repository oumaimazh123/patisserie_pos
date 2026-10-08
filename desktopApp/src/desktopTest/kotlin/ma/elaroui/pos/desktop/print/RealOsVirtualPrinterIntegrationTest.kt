package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformLogger
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class RealOsVirtualPrinterIntegrationTest {

    private class ConsoleLogger : PlatformLogger {
        override fun log(entry: LogEntry) {
            println("[${entry.level}] ${entry.category}: ${entry.message}")
        }
    }

    private val sampleOrder = Order(
        id = 999L,
        number = "CI-TICKET-01",
        type = OrderType.COUNTER,
        status = OrderStatus.COMPLETED,
        lines = listOf(
            OrderLine(1L, "Croissant Amande", 1800L, 2, 1000),
            OrderLine(2L, "Café Crème", 1500L, 1, 1000)
        ),
        subtotalCentimes = 5100L,
        discountCentimes = 0L,
        taxCentimes = 464L,
        totalCentimes = 5100L,
        registerSessionId = 1L,
        cashierId = 1L
    )

    private val company = ReceiptCompany(
        name = "Pâtisserie Royale CI",
        specialty = "Boulangerie & Salon de Thé",
        address = "123 Boulevard Mohammed V",
        phone = "0522001122",
        ice = "001234567890001",
        printEstablishmentName = true
    )

    @Test
    fun testRealOsSpoolerVirtualPrinterIfConfigured() {
        val platform = DesktopPlatform.detect()
        val logger = ConsoleLogger()

        when (platform) {
            DesktopPlatform.LINUX -> {
                val executor = ProcessBuilderCommandExecutor()
                val lpstatCheck = executor.execute(listOf("which", "lp"), null, 2_000L)
                if (!lpstatCheck.successful) {
                    println("CUPS 'lp' utility is not installed on this host. Skipping Linux OS spooler test.")
                    return
                }

                val queuesCheck = executor.execute(listOf("lpstat", "-p"), null, 2_000L)
                val posClientConfigured = queuesCheck.successful && queuesCheck.standardOutput.contains("POS_CLIENT")
                if (!posClientConfigured) {
                    println("Virtual printer queue 'POS_CLIENT' not found in CUPS. Skipping live CUPS print.")
                    return
                }

                val service = LinuxPrinterService(executor, logger)
                val health = service.checkPrinterHealth("POS_CLIENT")
                println("CUPS POS_CLIENT Health: state=${health.state}, ready=${health.isReady}")

                // 1. Test page
                val testResult = service.printTestPage("POS_CLIENT")
                assertTrue(testResult.success, "Test page print to CUPS POS_CLIENT should succeed: ${testResult.errorMessage}")

                // 2. Receipt ESC/POS binary print
                val receiptBytes = ThermalTicketRenderer.render(
                    order = sampleOrder,
                    company = company,
                    kind = TicketKind.CUSTOMER,
                    paperWidth = 80
                )
                assertTrue(receiptBytes.isNotEmpty())
                val printResult = service.printRaw("POS_CLIENT", receiptBytes, PrinterRole.CASHIER_RECEIPT)
                assertTrue(printResult.success, "Live receipt print to CUPS POS_CLIENT should succeed: ${printResult.errorMessage}")
            }

            DesktopPlatform.WINDOWS -> {
                val service = WindowsPrinterService(logger = logger)
                val discovery = service.discoverPrinters()
                val targetPrinterName = "Virtual Thermal POS"
                val found = discovery.printers.any { it.name.equals(targetPrinterName, ignoreCase = true) }

                if (!found) {
                    println("Windows virtual printer '$targetPrinterName' not installed on this host. Skipping live Windows spooler print.")
                    return
                }

                val health = service.checkPrinterHealth(targetPrinterName)
                println("Windows '$targetPrinterName' Health: state=${health.state}, ready=${health.isReady}")

                // 1. Test page
                val testResult = service.printTestPage(targetPrinterName)
                assertTrue(testResult.success, "Test page print to Windows spooler should succeed: ${testResult.errorMessage}")

                // 2. Receipt ESC/POS binary print
                val receiptBytes = ThermalTicketRenderer.render(
                    order = sampleOrder,
                    company = company,
                    kind = TicketKind.CUSTOMER,
                    paperWidth = 80
                )
                assertTrue(receiptBytes.isNotEmpty())
                val printResult = service.printRaw(targetPrinterName, receiptBytes, PrinterRole.CASHIER_RECEIPT)
                assertTrue(printResult.success, "Live receipt print to Windows spooler should succeed: ${printResult.errorMessage}")
            }

            else -> {
                println("Platform $platform does not have a live spooler integration test.")
            }
        }
    }
}
