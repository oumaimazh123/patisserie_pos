package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.print.PrintResult
import ma.elaroui.pos.core.print.PrinterStatus
import ma.elaroui.pos.core.print.ReceiptPrinter
import ma.elaroui.pos.domain.model.PrinterSettings
import ma.elaroui.pos.domain.model.PrinterType
import ma.elaroui.pos.domain.model.ReceiptData
import ma.elaroui.pos.domain.repository.PrinterSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterUseCasesTest {
    @Test
    fun customerAndKitchenJobs_useIndependentDestinations() = runTest {
        val repository = FakePrinterSettingsRepository(
            PrinterSettings(
                enabled = true,
                printerType = PrinterType.ETHERNET_ESCPOS,
                printerName = "Client",
                printerAddress = "192.168.1.20:9100",
                kitchenEnabled = true,
                kitchenPrinterType = PrinterType.BLUETOOTH_ESCPOS,
                kitchenPrinterName = "Cuisine",
                kitchenPrinterAddress = "AA:BB:CC:DD:EE:FF"
            )
        )
        val printer = CapturingPrinter()
        val useCase = PrintReceiptUseCase(repository, printer)

        useCase.printTest("Café")
        assertEquals(PrinterType.ETHERNET_ESCPOS, printer.lastSettings?.printerType)
        assertEquals("192.168.1.20:9100", printer.lastSettings?.printerAddress)

        useCase.printKitchenTest("Café")
        assertEquals(PrinterType.BLUETOOTH_ESCPOS, printer.lastSettings?.printerType)
        assertEquals("AA:BB:CC:DD:EE:FF", printer.lastSettings?.printerAddress)
    }

    @Test
    fun savePrinterSettings_rejectsMissingOrMalformedDestinations() = runTest {
        val repository = FakePrinterSettingsRepository(PrinterSettings())
        val useCase = SavePrinterSettingsUseCase(repository)
        val invalid = listOf(
            PrinterSettings(
                enabled = true,
                printerType = PrinterType.ETHERNET_ESCPOS,
                printerAddress = ""
            ),
            PrinterSettings(
                kitchenEnabled = true,
                kitchenPrinterType = PrinterType.BLUETOOTH_ESCPOS,
                kitchenPrinterAddress = "not-a-mac"
            )
        )
        invalid.forEach { settings ->
            val result = runCatching { useCase(settings) }
            assertTrue(result.isFailure)
        }
    }
}

private class FakePrinterSettingsRepository(
    var value: PrinterSettings
) : PrinterSettingsRepository {
    override suspend fun getSettings(): PrinterSettings = value
    override fun observeSettings(): Flow<PrinterSettings> = flowOf(value)
    override suspend fun saveSettings(settings: PrinterSettings) {
        value = settings
    }
}

private class CapturingPrinter : ReceiptPrinter {
    var lastSettings: PrinterSettings? = null
    override fun getStatus(): PrinterStatus = PrinterStatus.AVAILABLE
    override suspend fun print(
        data: ReceiptData,
        settings: PrinterSettings
    ): PrintResult {
        lastSettings = settings
        return PrintResult.Success
    }

    override suspend fun printTest(
        restaurantName: String,
        settings: PrinterSettings
    ): PrintResult {
        lastSettings = settings
        return PrintResult.Success
    }
}
