package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.desktop.JvmPlatformLogger
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.PlatformLogger

data class PrinterInfo(
    val name: String,
    val isDefault: Boolean = false,
    val isAvailable: Boolean = true,
    val status: String? = null,
    val isVirtual: Boolean = false
)

data class PrinterDiscoveryResult(
    val printers: List<PrinterInfo>,
    val defaultPrinter: PrinterInfo? = printers.firstOrNull { it.isDefault },
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = errorMessage == null
}

data class PrintResult(
    val success: Boolean,
    val message: String,
    val errorMessage: String? = null,
    val errorCategory: PrintErrorCategory? = null
)

const val DEFAULT_LINUX_POS_QUEUE = "POS_CLIENT"

enum class PrinterRole { CASHIER_RECEIPT, KITCHEN, SESSION_CLOSING_REPORT }
enum class PrintErrorCategory { NOT_CONFIGURED, NOT_FOUND, OFFLINE, CUPS_UNAVAILABLE, TIMEOUT, PERMISSION_DENIED, UNSUPPORTED_CHARACTERS, TRANSPORT_FAILURE }

enum class PrinterConnectionState {
    NOT_CONFIGURED,
    CONNECTED,
    DISCONNECTED,
    DISABLED,
    ERROR
}

data class PrinterHealthStatus(
    val state: PrinterConnectionState,
    val isConfigured: Boolean = state in setOf(PrinterConnectionState.CONNECTED, PrinterConnectionState.DISCONNECTED, PrinterConnectionState.DISABLED),
    val isConnected: Boolean = state == PrinterConnectionState.CONNECTED,
    val isReady: Boolean = state == PrinterConnectionState.CONNECTED,
    val deviceUri: String? = null,
    val message: String = "",
    val detailedReason: String? = null
) {
    companion object {
        fun notConfigured(
            message: String = if (DesktopPlatform.detect() == DesktopPlatform.WINDOWS) "Aucune imprimante Windows configurée" else "File d'attente non configurée dans CUPS"
        ) = PrinterHealthStatus(
            state = PrinterConnectionState.NOT_CONFIGURED,
            isConfigured = false,
            isConnected = false,
            isReady = false,
            message = message
        )

        fun connected(message: String = "Imprimante configurée et disponible", deviceUri: String? = null) = PrinterHealthStatus(
            state = PrinterConnectionState.CONNECTED,
            isConfigured = true,
            isConnected = true,
            isReady = true,
            deviceUri = deviceUri,
            message = message
        )

        fun disconnected(message: String = "Câble USB débranché ou imprimante éteinte", deviceUri: String? = null, detail: String? = null) = PrinterHealthStatus(
            state = PrinterConnectionState.DISCONNECTED,
            isConfigured = true,
            isConnected = false,
            isReady = false,
            deviceUri = deviceUri,
            message = message,
            detailedReason = detail
        )

        fun disabled(
            message: String = if (DesktopPlatform.detect() == DesktopPlatform.WINDOWS) "Imprimante désactivée sous Windows" else "File d'attente désactivée dans CUPS",
            deviceUri: String? = null,
            detail: String? = null
        ) = PrinterHealthStatus(
            state = PrinterConnectionState.DISABLED,
            isConfigured = true,
            isConnected = false,
            isReady = false,
            deviceUri = deviceUri,
            message = message,
            detailedReason = detail
        )

        fun error(
            message: String = if (DesktopPlatform.detect() == DesktopPlatform.WINDOWS) "Erreur de communication Spooler Windows" else "Erreur de communication CUPS",
            detail: String? = null
        ) = PrinterHealthStatus(
            state = PrinterConnectionState.ERROR,
            isConfigured = false,
            isConnected = false,
            isReady = false,
            message = message,
            detailedReason = detail
        )

        // Backward compatibility
        fun ready(message: String = "Imprimante prête", deviceUri: String? = null) = connected(message, deviceUri)
        fun configuredUnavailable(message: String, deviceUri: String? = null, detail: String? = null) = disconnected(message, deviceUri, detail)
    }
}

interface PrinterService {
    companion object {
        const val DEFAULT_LINUX_POS_QUEUE = "POS_CLIENT"
    }
    fun discoverPrinters(): PrinterDiscoveryResult
    fun getDefaultPrinter(): PrinterInfo? = discoverPrinters().defaultPrinter
    fun recordSelection(printerName: String) = Unit
    fun validateConfiguration(printerName: String): PrintResult = PrintResult(true, "Printer configuration accepted")
    fun checkPrinterHealth(printerName: String): PrinterHealthStatus = if (printerName.trim().isBlank()) {
        PrinterHealthStatus.notConfigured()
    } else {
        PrinterHealthStatus.ready("Imprimante configurée", null)
    }
    fun printTestPage(printerName: String, platformTestBytes: ByteArray? = null): PrintResult
    fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole = PrinterRole.CASHIER_RECEIPT): PrintResult
    fun openCashDrawer(printerName: String): PrintResult = printRaw(
        printerName,
        EscPosCommands.cashDrawerPulse(),
        PrinterRole.CASHIER_RECEIPT
    )
}

object DesktopPrinterServiceFactory {
    fun create(
        platform: DesktopPlatform = DesktopPlatform.detect(),
        logger: PlatformLogger = JvmPlatformLogger()
    ): PrinterService = when (platform) {
        DesktopPlatform.WINDOWS -> WindowsPrinterService(logger = logger)
        DesktopPlatform.LINUX -> LinuxPrinterService(logger = logger)
        DesktopPlatform.UNSUPPORTED -> UnsupportedPrinterService(platform, logger)
    }
}

private class UnsupportedPrinterService(
    private val platform: DesktopPlatform,
    private val logger: PlatformLogger
) : PrinterService {
    private val reason = "Printer integration is unavailable on desktop platform $platform"

    override fun discoverPrinters(): PrinterDiscoveryResult {
        logger.warning(reason, "Printer")
        return PrinterDiscoveryResult(emptyList(), errorMessage = reason)
    }

    override fun printTestPage(printerName: String, platformTestBytes: ByteArray?) = PrintResult(false, reason, reason, PrintErrorCategory.TRANSPORT_FAILURE)
    override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole) = PrintResult(false, reason, reason, PrintErrorCategory.TRANSPORT_FAILURE)
}
