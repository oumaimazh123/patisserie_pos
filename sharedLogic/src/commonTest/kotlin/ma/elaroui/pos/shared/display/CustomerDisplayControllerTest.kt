package ma.elaroui.pos.shared.display

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDisplayControllerTest {

    private val testConfig = CustomerDisplayConfig(
        enabled = true,
        protocol = VfdProtocol.ESC_POS,
        welcomeLine1 = "BIENVENUE",
        welcomeLine2 = "HYPER CAISSE",
        thankYouLine1 = "MERCI POUR VOTRE",
        thankYouLine2 = "VISITE",
        thankYouDurationSeconds = 2
    )

    @Test
    fun testInitialStateIsIdle() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.showIdle()
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.Idle)
        assertEquals(2, transport.sentTextHistory.size) // 1 from init + 1 from showIdle
        assertTrue(transport.sentTextHistory.last().contains("BIENVENUE"))
        assertTrue(transport.sentTextHistory.last().contains("HYPER CAISSE"))
    }

    @Test
    fun testFirstProductAddedImmediatelyShowsDuringSale() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.showIdle()
        testScheduler.runCurrent()
        val historyBefore = transport.sentTextHistory.size

        // First product added: total = 25.00
        controller.updateCart(totalCentimes = 2500L)
        testScheduler.runCurrent()

        val state = controller.state.value
        assertTrue(state is CustomerDisplayState.DuringSale)
        assertEquals(2500L, state.totalCentimes)
        assertEquals(historyBefore + 1, transport.sentTextHistory.size)
        assertTrue(transport.sentTextHistory.last().contains("TOTAL"))
        assertTrue(transport.sentTextHistory.last().contains("25.00"))
        assertTrue(transport.sentTextHistory.last().contains("A PAYER"))
    }

    @Test
    fun testAdditionalProductAddedUpdatesTotal() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 2500L)
        testScheduler.runCurrent()

        // Second product added: total = 75.00
        controller.updateCart(totalCentimes = 7500L)
        testScheduler.runCurrent()

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("75.00"))
    }

    @Test
    fun testProductRemovedUpdatesTotal() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 15000L)
        testScheduler.runCurrent()

        // 1 item removed -> new total 100.00
        controller.updateCart(totalCentimes = 10000L)
        testScheduler.runCurrent()

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("100.00"))
    }

    @Test
    fun testQuantityChangedUpdatesTotal() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 1000L)
        testScheduler.runCurrent()

        // Quantity increased -> total = 50.00
        controller.updateCart(totalCentimes = 5000L)
        testScheduler.runCurrent()

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("50.00"))
    }

    @Test
    fun testDiscountAppliedUpdatesTotal() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 10000L)
        testScheduler.runCurrent()

        // Apply discount -> total = 80.00
        controller.updateCart(totalCentimes = 8000L)
        testScheduler.runCurrent()

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("80.00"))
    }

    @Test
    fun testCartEmptiedReturnsToIdle() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 5000L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)

        // Cart emptied
        controller.updateCart(totalCentimes = 0L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.Idle)
        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("BIENVENUE"))
        assertTrue(lastSent.contains("HYPER CAISSE"))
    }

    @Test
    fun testCartSuspendedReturnsToIdle() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 5000L)
        testScheduler.runCurrent()

        controller.showIdle()
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.Idle)
    }

    @Test
    fun testCartResumedRestoresDuringSale() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.showIdle()
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.Idle)

        // Resume draft cart
        controller.updateCart(totalCentimes = 12000L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("120.00"))
    }

    @Test
    fun testPaymentStartedDoesNotDuplicateCommandsWhenAmountSame() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 10000L)
        testScheduler.runCurrent()
        val countBefore = transport.sentTextHistory.size

        // Payment started with same total/due amount
        controller.paymentStarted(totalCentimes = 10000L, dueCentimes = 10000L)
        testScheduler.runCurrent()

        // Same formatted lines suppress duplicate hardware write
        assertEquals(countBefore, transport.sentTextHistory.size)
    }

    @Test
    fun testCashPaymentReceivedAndChange() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        // Total: 125.00, Paid: 200.00 -> Change: 75.00
        controller.cashReceived(receivedCentimes = 20000L, totalCentimes = 12500L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.CashPayment)
        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("RECU"))
        assertTrue(lastSent.contains("200.00"))
        assertTrue(lastSent.contains("MONNAIE"))
        assertTrue(lastSent.contains("75.00"))
    }

    @Test
    fun testInsufficientCashNeverShowsPositiveChange() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        // Received: 50.00, Total: 125.00 -> Remaining: 75.00
        controller.cashReceived(receivedCentimes = 5000L, totalCentimes = 12500L)
        testScheduler.runCurrent()

        val lines = VfdMessageFormatter.format(controller.state.value, testConfig)
        assertTrue(lines.line2.contains("A PAYER"))
        assertTrue(lines.line2.contains("75.00"))
    }

    @Test
    fun testPaymentCompletedShowsThankYouThenReturnsToIdle() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.paymentCompleted()
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.PaymentCompleted)
        val thankYouSent = transport.sentTextHistory.last()
        assertTrue(thankYouSent.contains("MERCI POUR VOTRE"))
        assertTrue(thankYouSent.contains("VISITE"))

        // Advance past thank-you duration (2 seconds)
        advanceTimeBy(2500L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.Idle)
        val finalSent = transport.sentTextHistory.last()
        assertTrue(finalSent.contains("BIENVENUE"))
    }

    @Test
    fun testNewCartInterruptsThankYouImmediately() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.paymentCompleted()
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.PaymentCompleted)

        // Immediately start new sale before thank you timer ends
        controller.updateCart(totalCentimes = 3000L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("30.00"))

        // Even after time passes, it should remain DuringSale, not revert to Idle
        advanceTimeBy(3000L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
    }

    @Test
    fun testHardwareFailureDoesNotThrowOrCrash() = runTest {
        val brokenTransport = MockVfdTransport()
        brokenTransport.simulateFailureOnSend = true
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = brokenTransport,
            scope = this
        )

        // Should not throw exception
        controller.updateCart(totalCentimes = 5000L)
        testScheduler.runCurrent()

        // Status is ERROR, but controller state transitioned safely
        assertEquals(CustomerDisplayConnectionStatus.ERROR, controller.connectionStatus.value)
        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
    }

    @Test
    fun testDisabledDisplaySuppressesHardwareWrites() = runTest {
        val transport = MockVfdTransport()
        val disabledConfig = testConfig.copy(enabled = false)
        val controller = CustomerDisplayController(
            initialConfig = disabledConfig,
            transport = transport,
            scope = this
        )

        controller.updateCart(totalCentimes = 5000L)
        testScheduler.runCurrent()

        assertEquals(0, transport.sentTextHistory.size)
        assertEquals(CustomerDisplayConnectionStatus.DISCONNECTED, controller.connectionStatus.value)
    }

    @Test
    fun testSendCustomMessage() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )

        controller.showCustomMessage("PROMO DU JOUR", "-50% SUR TARTES")
        testScheduler.runCurrent()

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("PROMO DU JOUR"))
        assertTrue(lastSent.contains("-50% SUR TARTES"))
    }

    @Test
    fun testDisplaySuccessfulTransmission() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )
        testScheduler.runCurrent() // Allow init to complete

        val success = controller.testDisplay(durationSeconds = 5)
        testScheduler.runCurrent()

        assertTrue(success)
        assertEquals(CustomerDisplayConnectionStatus.CONNECTED, controller.connectionStatus.value)
        val state = controller.state.value
        assertTrue(state is CustomerDisplayState.CustomMessage)
        assertEquals("AURA CAISSE", state.line1)
        assertEquals("BY ZAKARIA EL EAROUI", state.line2)

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("AURA CAISSE"))
        assertTrue(lastSent.contains("BY ZAKARIA EL EAROUI"))

        controller.shutdown()
    }

    @Test
    fun testDisplayRestorationAfterFiveSeconds() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )
        testScheduler.runCurrent() // Allow init to complete

        // Initial state is a sale in progress
        controller.updateCart(totalCentimes = 7500L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)

        // Launch test display for 5 seconds
        val success = controller.testDisplay(durationSeconds = 5)
        testScheduler.runCurrent()
        assertTrue(success)
        assertTrue(controller.state.value is CustomerDisplayState.CustomMessage)

        // Fast-forward past 5 seconds
        advanceTimeBy(5500L)
        testScheduler.runCurrent()

        // Restored state should be DuringSale with 7500L
        val restoredState = controller.state.value
        assertTrue(restoredState is CustomerDisplayState.DuringSale)
        assertEquals(7500L, restoredState.totalCentimes)

        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("75.00"))
    }

    @Test
    fun testDisplayConnectionFailureReturnsFalseAndSetsErrorStatus() = runTest {
        val transport = MockVfdTransport(initialConnected = false)
        transport.simulateConnectionFailure = true
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )
        testScheduler.runCurrent() // Allow init to run

        val success = controller.testDisplay(durationSeconds = 5)
        testScheduler.runCurrent()

        assertFalse(success)
        assertEquals(CustomerDisplayConnectionStatus.ERROR, controller.connectionStatus.value)
    }

    @Test
    fun testDisplaySendFailureReturnsFalseAndSetsErrorStatus() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )
        testScheduler.runCurrent()

        transport.simulateFailureOnSend = true
        val success = controller.testDisplay(durationSeconds = 5)
        testScheduler.runCurrent()

        assertFalse(success)
        assertEquals(CustomerDisplayConnectionStatus.ERROR, controller.connectionStatus.value)
    }

    @Test
    fun testDisplayInterruptedByCartUpdateBeforeFiveSeconds() = runTest {
        val transport = MockVfdTransport()
        val controller = CustomerDisplayController(
            initialConfig = testConfig,
            transport = transport,
            scope = this
        )
        testScheduler.runCurrent() // Allow init to complete

        controller.showIdle()
        testScheduler.runCurrent()

        // Launch test display
        val success = controller.testDisplay(durationSeconds = 5)
        testScheduler.runCurrent()
        assertTrue(success)
        assertTrue(controller.state.value is CustomerDisplayState.CustomMessage)

        // 2 seconds later, cashier scans a product
        advanceTimeBy(2000L)
        controller.updateCart(totalCentimes = 4500L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
        val lastSent = transport.sentTextHistory.last()
        assertTrue(lastSent.contains("45.00"))

        // After remaining 4 seconds, verify it does NOT revert to Idle or test message
        advanceTimeBy(4000L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is CustomerDisplayState.DuringSale)
    }
}
