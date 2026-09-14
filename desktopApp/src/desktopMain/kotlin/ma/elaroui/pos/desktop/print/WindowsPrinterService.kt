package ma.elaroui.pos.desktop.print

import java.nio.charset.StandardCharsets
import ma.elaroui.pos.shared.PlatformLogger

class WindowsPrinterService(
    private val delegate: WindowsThermalPrinter = WindowsThermalPrinter(),
    private val logger: PlatformLogger
) : PrinterService {
    override fun discoverPrinters(): PrinterDiscoveryResult = runCatching {
        val rawDefault = javax.print.PrintServiceLookup.lookupDefaultPrintService()?.name
        val defaultName = rawDefault?.takeUnless { WindowsThermalPrinter.isIgnored(it) }
        val allPrinters = delegate.availablePrinters()
        val printers = allPrinters.map { name ->
            val isDef = name.equals(defaultName, ignoreCase = true)
            val isVirt = WindowsThermalPrinter.isVirtual(name)
            PrinterInfo(
                name = name,
                isDefault = isDef,
                isAvailable = delegate.isPrinterAvailable(name),
                status = if (delegate.isPrinterAvailable(name)) "Available" else "Unavailable",
                isVirtual = isVirt
            )
        }.sortedWith(
            compareBy(
                { it.isVirtual },     // Physical/thermal printers first
                { !it.isDefault },    // Default printer first
                { it.name.lowercase() }
            )
        )
        logger.info("Discovered ${printers.size} Windows printer(s); default=${defaultName ?: "none"}", "Printer")
        PrinterDiscoveryResult(printers)
    }.getOrElse { error ->
        logger.error("Windows printer discovery failed", error, "Printer")
        PrinterDiscoveryResult(emptyList(), errorMessage = error.message ?: "Printer discovery failed")
    }

    override fun checkPrinterHealth(printerName: String): PrinterHealthStatus {
        val safeName = printerName.trim()
        if (safeName.isBlank()) return PrinterHealthStatus.notConfigured("Aucune imprimante connectée sous Windows")
        if (WindowsThermalPrinter.isIgnored(safeName)) {
            return PrinterHealthStatus.notConfigured("Imprimante non prise en charge pour la caisse ($safeName)")
        }
        val exists = delegate.availablePrinters().any { it.equals(safeName, ignoreCase = true) }
        val available = delegate.isPrinterAvailable(safeName)
        return when {
            !exists -> PrinterHealthStatus.notConfigured("Imprimante '$safeName' non trouvée sous Windows")
            available -> PrinterHealthStatus.ready("Imprimante connectée et prête", "winprint://$safeName")
            else -> PrinterHealthStatus.configuredUnavailable("Imprimante '$safeName' hors ligne ou indisponible sous Windows", "winprint://$safeName")
        }
    }

    override fun printTestPage(printerName: String, platformTestBytes: ByteArray?): PrintResult {
        val content = platformTestBytes ?: run {
            val text = "TEST FRANCAIS\nCafé crème\nThé à la menthe\nPâtisserie\nDéjeuner\nÀ emporter\nEspèces\nRéduction\nQuantité\nNuméro\nMerci pour votre visite\nÀ bientôt\n10,00 €\n\nImprimante: $printerName\nStatut: OK\n\n"
            FrenchEscPosEncoder.buildDocument(FrenchEscPosEncoder.encodeText(text))
        }
        return printRaw(printerName, content)
    }

    override fun recordSelection(printerName: String) {
        logger.info("Selected Windows printer '${printerName.trim()}'", "Printer")
    }

    override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult {
        logger.info("Sending $role print job to Windows printer '$printerName'", "Printer")
        val result = delegate.print(DesktopPrinterConfig(printerName = printerName), bytes)
        return if (result.isSuccess) {
            PrintResult(true, "Print job submitted")
        } else {
            val reason = result.exceptionOrNull()?.message ?: "Print job failed"
            logger.error("Windows print job failed for '$printerName'", result.exceptionOrNull(), "Printer")
            PrintResult(false, reason, reason, PrintErrorCategory.TRANSPORT_FAILURE)
        }
    }
}
