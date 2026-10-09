package ma.elaroui.pos.shared.display

interface VfdTransport {
    val isConnected: Boolean
    fun connect(config: CustomerDisplayConfig): Result<Unit>
    fun disconnect()
    fun send(bytes: ByteArray): Result<Unit>
    fun isDeviceAvailable(portName: String): Boolean
}

class MockVfdTransport(
    initialConnected: Boolean = true
) : VfdTransport {

    var simulateFailureOnSend: Boolean = false
    var simulateUnavailable: Boolean = false
    var simulateConnectionFailure: Boolean = false

    private var _isConnected: Boolean = initialConnected
    override val isConnected: Boolean get() = _isConnected

    val sentByteHistory = mutableListOf<ByteArray>()
    val sentTextHistory = mutableListOf<String>()

    override fun connect(config: CustomerDisplayConfig): Result<Unit> {
        if (simulateConnectionFailure) {
            _isConnected = false
            return Result.failure(IllegalStateException("Simulated connection failure for port ${config.portName}"))
        }
        _isConnected = true
        return Result.success(Unit)
    }

    override fun disconnect() {
        _isConnected = false
    }

    override fun send(bytes: ByteArray): Result<Unit> {
        if (!_isConnected) {
            return Result.failure(IllegalStateException("VFD transport is not connected"))
        }
        if (simulateFailureOnSend) {
            return Result.failure(IllegalStateException("Simulated hardware write failure"))
        }
        sentByteHistory.add(bytes.copyOf())
        val text = bytes.map { b ->
            val c = b.toInt().toChar()
            if (c.code in 32..126) c else ' '
        }.joinToString("").trim()
        sentTextHistory.add(text)
        return Result.success(Unit)
    }

    override fun isDeviceAvailable(portName: String): Boolean {
        return !simulateUnavailable && portName.isNotBlank()
    }

    fun clearHistory() {
        sentByteHistory.clear()
        sentTextHistory.clear()
    }
}
