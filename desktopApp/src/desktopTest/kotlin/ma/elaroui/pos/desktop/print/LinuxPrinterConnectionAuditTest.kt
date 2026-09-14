package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformLogger
import kotlin.test.*

class LinuxPrinterConnectionAuditTest {

    private class MockPlatformLogger : PlatformLogger {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry) {
            entries += entry
        }
    }

    private class TestCommandExecutor : CommandExecutor {
        val executedCommands = mutableListOf<List<String>>()
        var lpstatVHandler: (printerName: String) -> CommandResult = { name ->
            CommandResult(exitCode = 0, standardOutput = "device for $name: usb://Xprinter/XP-80C?serial=12345")
        }
        var lpstatPHandler: (printerName: String) -> CommandResult = { name ->
            CommandResult(exitCode = 0, standardOutput = "printer $name is idle. enabled since Thu Jan 1 00:00:00 2026")
        }
        var lpstatAHandler: (printerName: String) -> CommandResult = { name ->
            CommandResult(exitCode = 0, standardOutput = "$name accepting requests since Thu Jan 1 00:00:00 2026")
        }
        var lpinfoHandler: () -> CommandResult = {
            CommandResult(exitCode = 0, standardOutput = "direct usb://Xprinter/XP-80C?serial=12345 \"Xprinter XP-80C\" \"USB Printer\"")
        }
        var lpHandler: (args: List<String>, input: ByteArray?) -> CommandResult = { _, _ ->
            CommandResult(exitCode = 0, standardOutput = "request id is POS_CLIENT-1 (1 file(s))")
        }

        override fun execute(arguments: List<String>, standardInput: ByteArray?, timeoutMillis: Long): CommandResult {
            executedCommands.add(arguments)
            return when {
                arguments.size >= 3 && arguments[0] == "lpstat" && arguments[1] == "-v" -> lpstatVHandler(arguments[2])
                arguments.size >= 3 && arguments[0] == "lpstat" && arguments[1] == "-p" -> lpstatPHandler(arguments[2])
                arguments.size >= 3 && arguments[0] == "lpstat" && arguments[1] == "-a" -> lpstatAHandler(arguments[2])
                arguments.size >= 2 && arguments[0] == "lpinfo" && arguments[1] == "-v" -> lpinfoHandler()
                arguments.isNotEmpty() && arguments[0] == "lp" -> lpHandler(arguments, standardInput)
                arguments.size >= 2 && arguments[0] == "lpstat" && arguments[1] == "-d" -> CommandResult(exitCode = 0, standardOutput = "system default destination: POS_CLIENT")
                else -> CommandResult(exitCode = 0, standardOutput = "")
            }
        }
    }

    @Test
    fun testPosClientQueueWithUsbConnectedReturnsConnectedAndReady() {
        val executor = TestCommandExecutor()
        val logger = MockPlatformLogger()
        var usbConnected = true
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> usbConnected }
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.CONNECTED, health.state)
        assertTrue(health.isConfigured)
        assertTrue(health.isConnected)
        assertTrue(health.isReady)
        assertEquals("usb://Xprinter/XP-80C?serial=12345", health.deviceUri)
        assertTrue(health.message.contains("disponible", ignoreCase = true) || health.message.contains("connectée", ignoreCase = true))
    }

    @Test
    fun testPosClientQueueWithUsbUnpluggedReturnsDisconnected() {
        val executor = TestCommandExecutor()
        val logger = MockPlatformLogger()
        // Simulate physical USB printer cable disconnected
        val usbConnected = false
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> usbConnected }
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.DISCONNECTED, health.state)
        assertTrue(health.isConfigured)
        assertFalse(health.isConnected, "Disconnected USB printer must NOT report as connected")
        assertFalse(health.isReady, "Disconnected USB printer must NOT report as ready")
        assertTrue(health.message.contains("débranchée", ignoreCase = true) || health.message.contains("débranché", ignoreCase = true))
    }

    @Test
    fun testPosClientQueueDoesNotExistReturnsNotConfigured() {
        val executor = TestCommandExecutor().apply {
            lpstatPHandler = { _ -> CommandResult(exitCode = 1, standardError = "lpstat: unknown destination") }
            lpstatVHandler = { _ -> CommandResult(exitCode = 1, standardError = "lpstat: unknown destination") }
            lpstatAHandler = { _ -> CommandResult(exitCode = 1, standardError = "lpstat: unknown destination") }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.NOT_CONFIGURED, health.state)
        assertFalse(health.isConfigured)
        assertFalse(health.isConnected)
        assertFalse(health.isReady)
    }

    @Test
    fun testPosClientQueueDisabledReturnsDisabled() {
        val executor = TestCommandExecutor().apply {
            lpstatPHandler = { name -> CommandResult(exitCode = 0, standardOutput = "printer $name disabled since Thu Jan 1 00:00:00 2026 - Paused") }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.DISABLED, health.state)
        assertFalse(health.isReady)
        assertTrue(health.message.contains("désactivée", ignoreCase = true))
    }

    @Test
    fun testPosClientQueueRejectingJobsReturnsDisabled() {
        val executor = TestCommandExecutor().apply {
            lpstatAHandler = { name -> CommandResult(exitCode = 0, standardOutput = "$name not accepting requests since Thu Jan 1 00:00:00 2026") }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.DISABLED, health.state)
        assertFalse(health.isReady)
    }

    @Test
    fun testPosClientPrinterBecomesDisconnectedAfterPreviouslyBeingConnected() {
        val executor = TestCommandExecutor()
        val logger = MockPlatformLogger()
        var usbPluggedIn = true
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> usbPluggedIn }
        )

        // Step 1: Initial state = Connected
        val health1 = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.CONNECTED, health1.state)

        // Step 2: Unplug printer
        usbPluggedIn = false

        // Step 3: Next poll = Disconnected
        val health2 = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.DISCONNECTED, health2.state)
        assertFalse(health2.isConnected)
    }

    @Test
    fun testValidateConfigurationAndHealthCheckRejectsWhenDisconnected() {
        val executor = TestCommandExecutor()
        val logger = MockPlatformLogger()
        val usbConnected = false
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> usbConnected }
        )

        val health = service.checkPrinterHealth(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertEquals(PrinterConnectionState.DISCONNECTED, health.state)
        assertFalse(health.isReady)

        val validationResult = service.validateConfiguration(PrinterService.DEFAULT_LINUX_POS_QUEUE)
        assertFalse(validationResult.success, "Validation must fail when printer is physically disconnected")
        assertEquals(PrintErrorCategory.OFFLINE, validationResult.errorCategory)
        val combinedError = "${validationResult.message} ${validationResult.errorMessage.orEmpty()}"
        assertTrue(combinedError.contains("débranchée") || combinedError.contains("débranché") || combinedError.contains("non détecté"))
    }

    @Test
    fun testDefaultUsbDeviceProberAccuratelyDetectsConnectedAndDisconnectedUsb() {
        val executor = TestCommandExecutor().apply {
            lpinfoHandler = {
                CommandResult(
                    exitCode = 0,
                    standardOutput = """
                        direct usb://Xprinter/XP-80C?serial=XP12345 "Xprinter XP-80C" "USB Printer"
                        direct usb://EPSON/TM-T20III?serial=EP98765 "EPSON TM-T20III" "USB Printer"
                    """.trimIndent()
                )
            }
        }
        val prober = DefaultUsbDeviceProber()

        // Same URI and matching serial
        assertTrue(prober.isUsbDevicePresent("usb://Xprinter/XP-80C?serial=XP12345", executor))
        // Matching base URI
        assertTrue(prober.isUsbDevicePresent("usb://EPSON/TM-T20III", executor))
        // Unplugged device (different URI not in lpinfo)
        assertFalse(prober.isUsbDevicePresent("usb://Zebra/ZD420?serial=ZB111", executor))
    }

    @Test
    fun testRawEscPosPrintJobSubmittedToPosClient() {
        val executor = TestCommandExecutor()
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val dummyBytes = byteArrayOf(0x1B, 0x40, 0x0A) // ESC @ \n
        val result = service.printRaw(PrinterService.DEFAULT_LINUX_POS_QUEUE, dummyBytes, PrinterRole.CASHIER_RECEIPT)

        assertTrue(result.success)
        val lpCmd = executor.executedCommands.lastOrNull { it.firstOrNull() == "lp" }
        assertNotNull(lpCmd)
        assertEquals(listOf("lp", "-d", "POS_CLIENT", "-o", "raw"), lpCmd)
    }

    @Test
    fun testPosRongtaQueueWithUnderscoreDetectedAndAvailable() {
        val executor = TestCommandExecutor().apply {
            lpstatPHandler = { name ->
                if (name == "POS_RONGTA") CommandResult(0, "printer POS_RONGTA is idle. enabled since Sat Sep 5 18:00:00 2026")
                else CommandResult(1, standardError = "lpstat: unknown destination")
            }
            lpstatVHandler = { name ->
                if (name == "POS_RONGTA") CommandResult(0, "device for POS_RONGTA: usb://Rongta/RP80%20Printer?serial=RT999")
                else CommandResult(1, standardError = "lpstat: unknown destination")
            }
            lpstatAHandler = { name ->
                if (name == "POS_RONGTA") CommandResult(0, "POS_RONGTA accepting requests since Sat Sep 5 18:00:00 2026")
                else CommandResult(1, standardError = "lpstat: unknown destination")
            }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val health = service.checkPrinterHealth("POS_RONGTA")
        assertEquals(PrinterConnectionState.CONNECTED, health.state)
        assertTrue(health.isConfigured)
        assertTrue(health.isConnected)
        assertTrue(health.isReady)
        assertEquals("Imprimante configurée et disponible", health.message)
        assertEquals("usb://Rongta/RP80%20Printer?serial=RT999", health.deviceUri)
    }

    @Test
    fun testPosRongtaTestPrintExecutesLpWithRawOptionAndBytesOnStdin() {
        var capturedBytes: ByteArray? = null
        val executor = TestCommandExecutor().apply {
            lpHandler = { _, bytes ->
                capturedBytes = bytes
                CommandResult(exitCode = 0, standardOutput = "request id is POS_RONGTA-42 (1 file(s))")
            }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val result = service.printTestPage("POS_RONGTA")
        assertTrue(result.success)

        val lpCmd = executor.executedCommands.lastOrNull { it.firstOrNull() == "lp" }
        assertNotNull(lpCmd)
        assertEquals(listOf("lp", "-d", "POS_RONGTA", "-o", "raw"), lpCmd)
        assertNotNull(capturedBytes)
        assertTrue(capturedBytes!!.isNotEmpty())

        // Verify diagnostic logs were captured
        val logMessages = logger.entries.map { it.message }
        assertTrue(logMessages.any { it.contains("[PRINT] queue=POS_RONGTA") })
        assertTrue(logMessages.any { it.contains("[PRINT] command=lp -d POS_RONGTA -o raw") })
        assertTrue(logMessages.any { it.contains("[PRINT] payloadBytes=") })
        assertTrue(logMessages.any { it.contains("[PRINT] exitCode=0") })
    }

    @Test
    fun testLpCommandFailureLogsTechnicalReasonAndErrorCategory() {
        val executor = TestCommandExecutor().apply {
            lpHandler = { _, _ ->
                CommandResult(exitCode = 1, standardError = "lp: destination 'POS_RONGTA' is offline or disabled")
            }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            usbDeviceProber = { _, _ -> true }
        )

        val result = service.printTestPage("POS_RONGTA")
        assertFalse(result.success)
        assertEquals(PrintErrorCategory.OFFLINE, result.errorCategory)
        assertTrue(result.errorMessage.orEmpty().contains("offline or disabled"))

        val logMessages = logger.entries.map { it.message }
        assertTrue(logMessages.any { it.contains("[PRINT] exitCode=1") })
        assertTrue(logMessages.any { it.contains("[PRINT] stderr=lp: destination 'POS_RONGTA' is offline or disabled") })
    }

    @Test
    fun testEthernetPrinterReachableReturnsConnected() {
        val executor = TestCommandExecutor().apply {
            lpstatVHandler = { name -> CommandResult(exitCode = 0, standardOutput = "device for $name: socket://192.168.1.200:9100") }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            socketProber = { host, port, _ -> host == "192.168.1.200" && port == 9100 }
        )

        val health = service.checkPrinterHealth("LAN-POS")
        assertEquals(PrinterConnectionState.CONNECTED, health.state)
        assertTrue(health.isConfigured)
        assertTrue(health.isConnected)
        assertTrue(health.isReady)
        assertTrue(health.message.contains("réseau connectée", ignoreCase = true))
    }

    @Test
    fun testEthernetPrinterUnreachableReturnsDisconnected() {
        val executor = TestCommandExecutor().apply {
            lpstatVHandler = { name -> CommandResult(exitCode = 0, standardOutput = "device for $name: socket://192.168.1.200:9100") }
        }
        val logger = MockPlatformLogger()
        val service = LinuxPrinterService(
            commandExecutor = executor,
            logger = logger,
            socketProber = { _, _, _ -> false } // simulate host offline / timeout
        )

        val health = service.checkPrinterHealth("LAN-POS")
        assertEquals(PrinterConnectionState.DISCONNECTED, health.state)
        assertTrue(health.isConfigured)
        assertFalse(health.isConnected)
        assertFalse(health.isReady)
        assertTrue(health.message.contains("injoignable", ignoreCase = true))
    }
}

