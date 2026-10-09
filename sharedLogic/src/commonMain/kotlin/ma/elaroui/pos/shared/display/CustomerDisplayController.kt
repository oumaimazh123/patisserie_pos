package ma.elaroui.pos.shared.display

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CustomerDisplayController(
    initialConfig: CustomerDisplayConfig = CustomerDisplayConfig(),
    private val transport: VfdTransport = MockVfdTransport(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val currencySuffix: String = ""
) {

    private val mutex = Mutex()
    private var thankYouJob: Job? = null
    private var testJob: Job? = null
    private var stateBeforeTest: CustomerDisplayState? = null
    private var lastSentLines: FormattedDisplayLines? = null

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<CustomerDisplayConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow<CustomerDisplayState>(
        CustomerDisplayState.Idle(
            line1 = initialConfig.welcomeLine1,
            line2 = initialConfig.welcomeLine2
        )
    )
    val state: StateFlow<CustomerDisplayState> = _state.asStateFlow()

    private val _connectionStatus = MutableStateFlow(
        if (initialConfig.enabled && transport.isConnected)
            CustomerDisplayConnectionStatus.CONNECTED
        else
            CustomerDisplayConnectionStatus.DISCONNECTED
    )
    val connectionStatus: StateFlow<CustomerDisplayConnectionStatus> = _connectionStatus.asStateFlow()

    init {
        if (initialConfig.enabled) {
            scope.launch {
                connectInternal(initialConfig)
                dispatchStateToHardware(_state.value)
            }
        }
    }

    private fun cancelPendingJobs() {
        thankYouJob?.cancel()
        testJob?.cancel()
        stateBeforeTest = null
    }

    suspend fun updateConfig(newConfig: CustomerDisplayConfig) {
        cancelPendingJobs()
        val oldConfig = _config.value
        _config.value = newConfig

        if (!newConfig.enabled) {
            transport.disconnect()
            _connectionStatus.value = CustomerDisplayConnectionStatus.DISCONNECTED
            lastSentLines = null
            return
        }

        if (!oldConfig.enabled || oldConfig.portName != newConfig.portName || oldConfig.protocol != newConfig.protocol) {
            connectInternal(newConfig)
        }
        val currentState = _state.value
        if (currentState is CustomerDisplayState.Idle) {
            _state.value = CustomerDisplayState.Idle(
                line1 = newConfig.welcomeLine1,
                line2 = newConfig.welcomeLine2
            )
        }
        dispatchStateToHardware(_state.value)
    }

    private suspend fun connectInternal(cfg: CustomerDisplayConfig) {
        if (!cfg.enabled) return
        _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTING
        val result = transport.connect(cfg)
        if (result.isSuccess) {
            _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTED
            // Initialize display
            val driver = VfdDriverRegistry.getDriver(cfg.protocol)
            transport.send(driver.initDisplay())
        } else {
            _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
        }
    }

    fun showIdle() {
        cancelPendingJobs()
        val next = CustomerDisplayState.Idle(
            line1 = _config.value.welcomeLine1,
            line2 = _config.value.welcomeLine2
        )
        transitionTo(next)
    }

    fun updateCart(totalCentimes: Long) {
        cancelPendingJobs()
        if (totalCentimes <= 0L) {
            val next = CustomerDisplayState.Idle(
                line1 = _config.value.welcomeLine1,
                line2 = _config.value.welcomeLine2
            )
            transitionTo(next)
            return
        }
        val next = CustomerDisplayState.DuringSale(
            totalCentimes = totalCentimes,
            dueCentimes = totalCentimes
        )
        transitionTo(next)
    }

    fun paymentStarted(totalCentimes: Long, dueCentimes: Long = totalCentimes) {
        cancelPendingJobs()
        val next = CustomerDisplayState.PaymentStarted(
            totalCentimes = totalCentimes,
            dueCentimes = dueCentimes
        )
        transitionTo(next)
    }

    fun cashReceived(receivedCentimes: Long, totalCentimes: Long) {
        cancelPendingJobs()
        if (receivedCentimes <= 0L) {
            paymentStarted(totalCentimes)
            return
        }

        val change = if (receivedCentimes >= totalCentimes) receivedCentimes - totalCentimes else 0L
        val remaining = if (receivedCentimes < totalCentimes) totalCentimes - receivedCentimes else 0L

        val next = CustomerDisplayState.CashPayment(
            receivedCentimes = receivedCentimes,
            changeCentimes = change,
            remainingCentimes = remaining
        )
        transitionTo(next)
    }

    fun nonCashPaymentSelected(methodLabel: String, amountCentimes: Long) {
        cancelPendingJobs()
        val next = CustomerDisplayState.NonCashPayment(
            methodLabel = methodLabel,
            amountCentimes = amountCentimes
        )
        transitionTo(next)
    }

    fun paymentCompleted() {
        cancelPendingJobs()
        val next = CustomerDisplayState.PaymentCompleted(
            line1 = _config.value.thankYouLine1,
            line2 = _config.value.thankYouLine2
        )
        transitionTo(next)

        val duration = _config.value.thankYouDurationSeconds.coerceIn(1, 60)
        thankYouJob = scope.launch {
            delay(duration * 1000L)
            if (_state.value is CustomerDisplayState.PaymentCompleted) {
                showIdle()
            }
        }
    }

    fun showCustomMessage(line1: String, line2: String) {
        cancelPendingJobs()
        val next = CustomerDisplayState.CustomMessage(line1, line2)
        transitionTo(next)
    }

    suspend fun testConnection(): Boolean {
        val cfg = _config.value
        _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTING
        val res = transport.connect(cfg)
        return if (res.isSuccess) {
            _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTED
            val driver = VfdDriverRegistry.getDriver(cfg.protocol)
            transport.send(driver.initDisplay()).isSuccess
        } else {
            _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
            false
        }
    }

    suspend fun testDisplay(durationSeconds: Int = CustomerDisplayDefaults.TEST_DURATION_SECONDS): Boolean {
        thankYouJob?.cancel()
        testJob?.cancel()

        val cfg = _config.value
        if (!cfg.enabled) {
            _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
            stateBeforeTest = null
            return false
        }

        // Preserve current underlying state before testing
        val previousState = stateBeforeTest ?: _state.value
        stateBeforeTest = previousState

        val driver = VfdDriverRegistry.getDriver(cfg.protocol)
        val testLines = FormattedDisplayLines(
            line1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, cfg.columns),
            line2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, cfg.columns)
        )

        val success = mutex.withLock {
            if (!transport.isConnected) {
                _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTING
                val connectRes = transport.connect(cfg)
                if (connectRes.isFailure) {
                    _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
                    stateBeforeTest = null
                    return@withLock false
                }
            }

            val bytes = driver.writeLines(testLines, cfg.columns)
            val sendResult = transport.send(bytes)

            if (sendResult.isSuccess) {
                _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTED
                lastSentLines = testLines
                _state.value = CustomerDisplayState.CustomMessage(
                    CustomerDisplayDefaults.TEST_LINE_1,
                    CustomerDisplayDefaults.TEST_LINE_2
                )
                true
            } else {
                _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
                stateBeforeTest = null
                false
            }
        }

        if (!success) {
            return false
        }

        testJob = scope.launch {
            delay(durationSeconds * 1000L)
            val toRestore = stateBeforeTest ?: CustomerDisplayState.Idle(
                line1 = _config.value.welcomeLine1,
                line2 = _config.value.welcomeLine2
            )
            stateBeforeTest = null
            lastSentLines = null
            _state.value = toRestore
            dispatchStateToHardware(toRestore)
        }

        return true
    }

    suspend fun sendTestMessage(): Boolean = testDisplay()

    private fun transitionTo(newState: CustomerDisplayState) {
        _state.value = newState
        scope.launch {
            dispatchStateToHardware(newState)
        }
    }

    private suspend fun dispatchStateToHardware(targetState: CustomerDisplayState) {
        val cfg = _config.value
        if (!cfg.enabled) return

        mutex.withLock {
            val formatted = VfdMessageFormatter.format(targetState, cfg, currencySuffix)
            if (formatted == lastSentLines) {
                // Suppress duplicate command if screen content is identical
                return
            }

            if (!transport.isConnected) {
                val connectRes = transport.connect(cfg)
                if (connectRes.isFailure) {
                    _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
                    return
                }
                _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTED
            }

            val driver = VfdDriverRegistry.getDriver(cfg.protocol)
            val bytes = driver.writeLines(formatted, cfg.columns)
            val sendResult = transport.send(bytes)

            if (sendResult.isSuccess) {
                lastSentLines = formatted
                _connectionStatus.value = CustomerDisplayConnectionStatus.CONNECTED
            } else {
                _connectionStatus.value = CustomerDisplayConnectionStatus.ERROR
            }
        }
    }

    fun shutdown() {
        cancelPendingJobs()
        transport.disconnect()
        _connectionStatus.value = CustomerDisplayConnectionStatus.DISCONNECTED
    }
}
