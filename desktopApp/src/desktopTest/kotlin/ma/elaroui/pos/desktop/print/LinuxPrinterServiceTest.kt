package ma.elaroui.pos.desktop.print

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformLogger

class LinuxPrinterServiceTest {

    private val logger = RecordingLogger()

    @Test
    fun discoveryParsesLpstatOutputWithoutShellOrDirectDeviceAccess() {
        val executor = CommandExecutor { args, _, _ ->
            when (args) {
                listOf("lpstat", "-p") -> CommandResult(
                    0,
                    """
                    printer Cafe_Main is idle. enabled since Thu 28 Aug 2026 10:00:00 AM
                    printer Kitchen_Pass is idle. enabled since Thu 28 Aug 2026 10:00:00 AM
                    """.trimIndent()
                )
                listOf("lpstat", "-d") -> CommandResult(0, "system default destination: Cafe_Main")
                listOf("lpstat", "-a") -> CommandResult(
                    0,
                    """
                    Cafe_Main accepting requests since Thu 28 Aug 2026 10:00:00 AM
                    Kitchen_Pass not accepting requests since Thu 28 Aug 2026 10:00:00 AM
                    """.trimIndent()
                )
                else -> CommandResult(1, standardError = "unexpected command: $args")
            }
        }

        val result = LinuxPrinterService(executor, logger).discoverPrinters()

        assertTrue(result.isSuccess)
        assertEquals(2, result.printers.size)
        val defaultPrinter = requireNotNull(result.defaultPrinter)
        assertEquals("Cafe_Main", defaultPrinter.name)
        assertTrue(defaultPrinter.isDefault)
        assertTrue(defaultPrinter.isAvailable)

        val kitchen = result.printers.first { it.name == "Kitchen_Pass" }
        assertFalse(kitchen.isDefault)
        assertFalse(kitchen.isAvailable)
    }

    @Test
    fun discoverySurvivesMissingDefaultPrinterGracefully() {
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { args, _, _ ->
                when (args) {
                    listOf("lpstat", "-p") -> CommandResult(0, "printer Solo is idle. enabled since now")
                    listOf("lpstat", "-d") -> CommandResult(1, standardError = "no system default destination")
                    listOf("lpstat", "-a") -> CommandResult(0, "Solo accepting requests since now")
                    else -> CommandResult(1, standardError = "unexpected command: $args")
                }
            },
            logger = logger
        )

        val result = service.discoverPrinters()

        assertTrue(result.isSuccess)
        assertEquals(1, result.printers.size)
        assertEquals(null, result.defaultPrinter)
        assertTrue(result.printers.first().isAvailable)
    }

    @Test
    fun discoveryReturnsFallbackWhenLpstatFails() {
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ -> CommandResult(1, standardError = "cupsd not running") },
            logger = logger
        )

        val result = service.discoverPrinters()

        assertFalse(result.isSuccess)
        assertTrue(result.printers.isEmpty())
        assertTrue(result.errorMessage.orEmpty().contains("cupsd not running"))
    }

    @Test
    fun discoveryExecutesExpectedLpstatCommandsInOrder() {
        val calls = mutableListOf<List<String>>()
        val executor = CommandExecutor { args, _, _ ->
            calls += args
            when (args) {
                listOf("lpstat", "-p") -> CommandResult(0, "printer Cafe_Main is idle. enabled since now")
                listOf("lpstat", "-d") -> CommandResult(0, "system default destination: Cafe_Main")
                listOf("lpstat", "-a") -> CommandResult(0, "Cafe_Main accepting requests since now")
                else -> CommandResult(1, standardError = "unexpected command: $args")
            }
        }
        val result = LinuxPrinterService(executor, logger).discoverPrinters()

        assertEquals(listOf(listOf("lpstat", "-p"), listOf("lpstat", "-d"), listOf("lpstat", "-a")), calls)
        assertEquals("Cafe_Main", result.defaultPrinter?.name)
    }

    @Test
    fun testPrintPassesPrinterNameAsSingleArgumentAndContentOnStdinWithRawOption() {
        var arguments = emptyList<String>()
        var input = ByteArray(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { args, bytes, _ ->
                arguments = args
                input = bytes ?: ByteArray(0)
                CommandResult(0, "request id is Cafe-Main-1")
            },
            logger = logger
        )

        val result = service.printTestPage("Cafe Main Printer")

        assertTrue(result.success)
        assertEquals("Cafe Main Printer", arguments[2])
        assertTrue(arguments.contains("-o"))
        assertTrue(arguments.contains("raw"))
        assertTrue(input.isNotEmpty())
        assertTrue(input.decodeToString().contains("Test") || input.decodeToString().contains("TEST"))
    }

    @Test
    fun failedTestPrintDoesNotThrow() {
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ -> CommandResult(1, standardError = "printer not found") },
            logger = logger
        )

        val result = service.printTestPage("Removed_Printer")

        assertFalse(result.success)
        assertTrue(result.errorMessage.orEmpty().contains("printer not found"))
    }

    @Test
    fun testDiscoveryAndParsingWithPosRongtaDefaultQueue() {
        val executor = CommandExecutor { args, _, _ ->
            when (args) {
                listOf("lpstat", "-p") -> CommandResult(
                    0,
                    """
                    printer POS_RONGTA is idle. enabled since Sat 05 Sep 2026 05:00:00 PM
                    printer Kitchen_Pass is idle. enabled since Sat 05 Sep 2026 05:00:00 PM
                    """.trimIndent()
                )
                listOf("lpstat", "-d") -> CommandResult(0, "system default destination: POS_RONGTA")
                listOf("lpstat", "-a") -> CommandResult(
                    0,
                    """
                    POS_RONGTA accepting requests since Sat 05 Sep 2026 05:00:00 PM
                    Kitchen_Pass accepting requests since Sat 05 Sep 2026 05:00:00 PM
                    """.trimIndent()
                )
                else -> CommandResult(1, standardError = "unexpected command: $args")
            }
        }
        val result = LinuxPrinterService(executor, logger).discoverPrinters()

        assertTrue(result.isSuccess)
        assertEquals(2, result.printers.size)
        val defaultPrinter = requireNotNull(result.defaultPrinter)
        assertEquals("POS_RONGTA", defaultPrinter.name)
        assertTrue(defaultPrinter.isDefault)
        assertTrue(defaultPrinter.isAvailable)

        // Device URI parsing with underscore
        val uri = CupsOutputParser.parseDeviceUri("device for POS_RONGTA: usb://Rongta/RP80?serial=RT1", "POS_RONGTA")
        assertEquals("usb://Rongta/RP80?serial=RT1", uri)
    }

    private class RecordingLogger : PlatformLogger {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry) {
            entries += entry
        }
    }
}
