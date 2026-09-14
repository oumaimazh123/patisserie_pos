package ma.elaroui.pos.desktop.print

import kotlinx.coroutines.*
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PrinterTestGuardAuditTest {

    private class TestAuditLogger : PlatformLogger {
        val messages = mutableListOf<String>()
        override fun log(entry: LogEntry) {
            messages += "${entry.level}: ${entry.message}"
        }
    }

    @Test
    fun testSingleClickTriggersExactlyOnePrintJob() {
        val executionCount = AtomicInteger(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ ->
                executionCount.incrementAndGet()
                CommandResult(0, "request id is POS-101")
            },
            logger = TestAuditLogger()
        )

        val result = service.printTestPage("POS-80-USB")
        assertTrue(result.success)
        assertEquals(1, executionCount.get(), "Single click must produce exactly one print job")
    }

    @Test
    fun testRapidConsecutiveClicksAllowMaximumOneActiveJob() = runBlocking {
        val executionCount = AtomicInteger(0)
        val inFlightCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val isTestingGuard = AtomicBoolean(false)

        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ ->
                val current = inFlightCount.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                Thread.sleep(50)
                inFlightCount.decrementAndGet()
                executionCount.incrementAndGet()
                CommandResult(0, "request id is POS-102")
            },
            logger = TestAuditLogger()
        )

        // Simulate 5 rapid clicks with the AtomicBoolean UI guard
        val jobs = (1..5).map {
            async(Dispatchers.Default) {
                if (isTestingGuard.compareAndSet(false, true)) {
                    try {
                        service.printTestPage("POS-80-USB")
                    } finally {
                        isTestingGuard.set(false)
                    }
                }
            }
        }
        jobs.awaitAll()

        assertEquals(1, maxConcurrent.get(), "Maximum concurrent jobs must be 1")
        assertTrue(executionCount.get() in 1..2, "Rapid burst clicks must be guarded, execution count=${executionCount.get()}")
    }

    @Test
    fun testOfflinePrinterReturnsSingleFailureWithNoRetryLoop() {
        val executionCount = AtomicInteger(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ ->
                executionCount.incrementAndGet()
                CommandResult(1, standardError = "lp: The printer or class does not exist.")
            },
            logger = TestAuditLogger()
        )

        val result = service.printTestPage("OFFLINE_PRINTER")
        assertFalse(result.success)
        assertEquals(1, executionCount.get(), "Offline printer failure must NOT trigger retry loops")
        assertEquals(PrintErrorCategory.NOT_FOUND, result.errorCategory)
    }

    @Test
    fun testPrinterDiscoveryAndReconnectDoesNotTriggerPrint() {
        val executionCount = AtomicInteger(0)
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { args, _, _ ->
                if (args.firstOrNull() == "lp") {
                    executionCount.incrementAndGet()
                    CommandResult(0, "printed")
                } else {
                    CommandResult(0, "printer POS-80 is idle. enabled since now")
                }
            },
            logger = TestAuditLogger()
        )

        // 1. Discovery when offline
        service.discoverPrinters()
        // 2. Discovery when reconnected
        service.discoverPrinters()
        // 3. Selection change
        service.recordSelection("POS-80")
        // 4. Configuration validation
        service.validateConfiguration("POS-80")

        assertEquals(0, executionCount.get(), "Printer status discovery and reconnect must NEVER trigger printing")
    }

    @Test
    fun testNavigationLifecycleDoesNotResubmitTestJob() {
        var submittedJobs = 0
        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { args, _, _ ->
                if (args.firstOrNull() == "lp") submittedJobs++
                CommandResult(0, "printer POS-80 is idle.")
            },
            logger = TestAuditLogger()
        )

        // Simulating navigating away and back to settings
        service.discoverPrinters()
        service.validateConfiguration("POS-80")
        service.recordSelection("POS-80")

        assertEquals(0, submittedJobs, "Navigation and screen lifecycle must NOT trigger any print job")
    }
}
