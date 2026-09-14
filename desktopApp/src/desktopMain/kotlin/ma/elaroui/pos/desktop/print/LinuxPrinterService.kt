package ma.elaroui.pos.desktop.print

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import ma.elaroui.pos.shared.PlatformLogger

data class CommandResult(
    val exitCode: Int? = null,
    val standardOutput: String = "",
    val standardError: String = "",
    val timedOut: Boolean = false,
    val commandUnavailable: Boolean = false
) {
    val successful: Boolean get() = exitCode == 0 && !timedOut && !commandUnavailable
}

fun interface CommandExecutor {
    fun execute(arguments: List<String>, standardInput: ByteArray?, timeoutMillis: Long): CommandResult
}

class ProcessBuilderCommandExecutor(
    private val executor: java.util.concurrent.ExecutorService = defaultExecutor
) : CommandExecutor {
    companion object {
        private val defaultExecutor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable).apply {
                name = "pos-command-io"
                isDaemon = true
            }
        }
    }

    override fun execute(arguments: List<String>, standardInput: ByteArray?, timeoutMillis: Long): CommandResult {
        require(arguments.isNotEmpty()) { "Command arguments cannot be empty" }
        return try {
            val process = ProcessBuilder(arguments).start()
            val standardOutput = ByteArrayOutputStream()
            val standardError = ByteArrayOutputStream()
            val outputFuture = executor.submit {
                process.inputStream.use { it.copyTo(standardOutput) }
            }
            val errorFuture = executor.submit {
                process.errorStream.use { it.copyTo(standardError) }
            }
            if (standardInput != null) {
                process.outputStream.use { it.write(standardInput) }
            } else {
                process.outputStream.close()
            }
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                outputFuture.cancel(true)
                errorFuture.cancel(true)
                return CommandResult(timedOut = true, standardError = "Command timed out")
            }
            runCatching { outputFuture.get(1_000L, TimeUnit.MILLISECONDS) }
            runCatching { errorFuture.get(1_000L, TimeUnit.MILLISECONDS) }
            CommandResult(
                exitCode = process.exitValue(),
                standardOutput = standardOutput.toString(StandardCharsets.UTF_8),
                standardError = standardError.toString(StandardCharsets.UTF_8)
            )
        } catch (error: java.io.IOException) {
            CommandResult(commandUnavailable = true, standardError = error.message.orEmpty())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult(timedOut = true, standardError = "Command interrupted")
        } catch (error: Throwable) {
            CommandResult(standardError = error.message.orEmpty())
        }
    }
}

fun interface SocketProber {
    fun isReachable(host: String, port: Int, timeoutMillis: Int): Boolean
}

class DefaultSocketProber : SocketProber {
    override fun isReachable(host: String, port: Int, timeoutMillis: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }
}

fun interface UsbDeviceProber {
    fun isUsbDevicePresent(deviceUri: String, commandExecutor: CommandExecutor): Boolean
}

class DefaultUsbDeviceProber : UsbDeviceProber {
    override fun isUsbDevicePresent(deviceUri: String, commandExecutor: CommandExecutor): Boolean {
        val cleanUri = deviceUri.trim()
        if (cleanUri.isBlank()) return false

        // 1. Direct character device path check
        if (cleanUri.startsWith("/") || cleanUri.startsWith("file:///dev/")) {
            val path = cleanUri.removePrefix("file://")
            if (File(path).exists()) return true
        }

        // 2. Query lpinfo -v (CUPS hardware live device probe)
        val lpinfoResult = commandExecutor.execute(listOf("lpinfo", "-v"), null, 2_000L)
        if (lpinfoResult.successful) {
            val usbLines = lpinfoResult.standardOutput.lineSequence()
                .filter { it.contains("direct usb://", ignoreCase = true) || it.contains("direct /dev/usb/lp", ignoreCase = true) }
                .map { it.trim().lowercase() }
                .toList()

            if (usbLines.isEmpty()) {
                // No physical USB printer devices detected by CUPS at all
                return false
            }

            // If configured URI is usb://...
            if (cleanUri.startsWith("usb://", ignoreCase = true)) {
                val configuredCore = cleanUri.substringBefore("?").removePrefix("usb://").trim().lowercase()
                val configuredSerial = if (cleanUri.contains("serial=", ignoreCase = true)) {
                    cleanUri.substringAfter("serial=", "").substringBefore("&").trim().lowercase()
                } else null

                val matches = usbLines.any { line ->
                    val lineUriWithQuotes = line.substringAfter("direct ", "").trim().lowercase()
                    val lineUri = lineUriWithQuotes.substringBefore(" ").trim()
                    if (lineUri == cleanUri.lowercase()) {
                        return@any true
                    }
                    val lineCore = lineUri.substringBefore("?").removePrefix("usb://").trim()
                    val coreMatch = configuredCore.isNotBlank() && (lineCore.contains(configuredCore) || configuredCore.contains(lineCore))
                    if (coreMatch) {
                        if (!configuredSerial.isNullOrBlank() && lineUri.contains("serial=")) {
                            val lineSerial = lineUri.substringAfter("serial=", "").substringBefore("&").trim()
                            lineSerial == configuredSerial
                        } else {
                            true
                        }
                    } else {
                        false
                    }
                }
                return matches
            }

            // If configured URI is file or direct dev
            val matchingDirect = usbLines.any { it.contains(cleanUri.lowercase()) }
            if (matchingDirect) return true

            return false
        }

        // 3. Fallback when lpinfo is not available: check /dev/usb/lp* files
        val devUsbDir = File("/dev/usb")
        if (devUsbDir.exists() && devUsbDir.isDirectory) {
            val lpFiles = devUsbDir.listFiles { _, name -> name.startsWith("lp") }
            if (!lpFiles.isNullOrEmpty()) return true
        }
        for (i in 0..15) {
            if (File("/dev/usb/lp$i").exists()) return true
        }

        return false
    }
}

data class NetworkEndpoint(val host: String, val port: Int)

object CupsOutputParser {
    fun parseDefaultPrinter(output: String): String? = output.lineSequence()
        .map(String::trim)
        .firstNotNullOfOrNull { line ->
            val prefix = "system default destination:"
            if (line.startsWith(prefix, ignoreCase = true)) {
                line.substring(prefix.length).trim().takeIf(String::isNotBlank)
            } else null
        }

    fun parseDeviceUri(output: String, printerName: String): String? {
        val cleanName = printerName.trim()
        val targetPrefix = "device for $cleanName:"
        for (line in output.lineSequence().map(String::trim)) {
            if (line.startsWith(targetPrefix, ignoreCase = true)) {
                val uri = line.substring(targetPrefix.length).trim()
                if (uri.isNotBlank()) return uri
            }
        }
        val regex = Regex("""device\s+for\s+${Regex.escape(cleanName)}\s*:\s*(.+)""", RegexOption.IGNORE_CASE)
        for (line in output.lineSequence().map(String::trim)) {
            val match = regex.find(line)
            if (match != null) {
                val uri = match.groupValues[1].trim()
                if (uri.isNotBlank()) return uri
            }
        }
        return null
    }

    fun parseNetworkEndpoint(uri: String): NetworkEndpoint? {
        val clean = uri.trim()
        val scheme = when {
            clean.startsWith("socket://", ignoreCase = true) -> "socket"
            clean.startsWith("ipp://", ignoreCase = true) -> "ipp"
            clean.startsWith("ipps://", ignoreCase = true) -> "ipps"
            clean.startsWith("lpd://", ignoreCase = true) -> "lpd"
            clean.startsWith("http://", ignoreCase = true) -> "http"
            clean.startsWith("https://", ignoreCase = true) -> "https"
            else -> return null
        }
        val defaultPort = when (scheme) {
            "socket" -> 9100
            "ipp", "ipps" -> 631
            "lpd" -> 515
            "http" -> 80
            "https" -> 443
            else -> 9100
        }
        val withoutScheme = clean.substringAfter("://").substringBefore("/").substringBefore("?")
        val host = withoutScheme.substringBefore(":")
        val port = withoutScheme.substringAfter(":", "").toIntOrNull() ?: defaultPort
        return if (host.isNotBlank()) NetworkEndpoint(host, port) else null
    }

    fun parsePrinters(printerOutput: String, acceptingOutput: String, defaultName: String?): List<PrinterInfo> {
        val accepting = acceptingOutput.lineSequence().mapNotNull(::parseAcceptingLine).toMap()
        return printerOutput.lineSequence().mapNotNull(::parsePrinterLine)
            .distinctBy { it.first.lowercase() }
            .map { (name, status) ->
                val acceptsJobs = accepting[name.lowercase()] ?: !status.contains("disabled", ignoreCase = true)
                PrinterInfo(
                    name = name,
                    isDefault = name.equals(defaultName, ignoreCase = true),
                    isAvailable = acceptsJobs && !status.contains("disabled", ignoreCase = true),
                    status = status.ifBlank { if (acceptsJobs) "accepting requests" else "not accepting requests" }
                )
            }.toList()
    }

    internal fun parsePrinterLine(raw: String): Pair<String, String>? {
        val line = raw.trim()
        if (!line.startsWith("printer ", ignoreCase = true)) return null
        val body = line.substringAfter(' ').trim()
        val markers = listOf(" is ", " disabled since ", " now printing ", " - ")
        val marker = markers.map { body.indexOf(it, ignoreCase = true) to it }
            .filter { it.first > 0 }
            .minByOrNull { it.first }
        val name = if (marker == null) body else body.substring(0, marker.first).trim()
        val status = if (marker == null) "" else body.substring(marker.first + 1).trim()
        return name.takeIf(String::isNotBlank)?.let { it to status }
    }

    internal fun parseAcceptingLine(raw: String): Pair<String, Boolean>? {
        val line = raw.trim()
        if (line.isBlank()) return null
        val acceptingMarker = " accepting requests"
        val rejectingMarker = " not accepting requests"
        return when {
            line.contains(rejectingMarker, ignoreCase = true) ->
                line.substringBefore(rejectingMarker, missingDelimiterValue = "").trim().takeIf(String::isNotBlank)?.lowercase()?.let { it to false }
            line.contains(acceptingMarker, ignoreCase = true) ->
                line.substringBefore(acceptingMarker, missingDelimiterValue = "").trim().takeIf(String::isNotBlank)?.lowercase()?.let { it to true }
            else -> null
        }
    }
}

class LinuxPrinterService(
    private val commandExecutor: CommandExecutor = ProcessBuilderCommandExecutor(),
    private val logger: PlatformLogger,
    private val timeoutMillis: Long = 5_000L,
    private val socketProber: SocketProber = DefaultSocketProber(),
    private val usbDeviceProber: UsbDeviceProber = DefaultUsbDeviceProber()
) : PrinterService {

    override fun checkPrinterHealth(printerName: String): PrinterHealthStatus {
        val safeName = printerName.trim().ifBlank { DEFAULT_LINUX_POS_QUEUE }

        // 1. Query CUPS status for this queue
        val statusResult = commandExecutor.execute(listOf("lpstat", "-p", safeName), null, timeoutMillis)
        if (statusResult.commandUnavailable) {
            logger.error("[PRINT] queue=$safeName [ERROR] Commande CUPS 'lpstat' non disponible", category = "Printer")
            return PrinterHealthStatus.error("Commande CUPS 'lpstat' non disponible", "CUPS non installé")
        }
        if (statusResult.timedOut) {
            logger.error("[PRINT] queue=$safeName [ERROR] Timeout lpstat", category = "Printer")
            return PrinterHealthStatus.error("Délai d'attente dépassé lors de la vérification CUPS", "Timeout lpstat")
        }

        var queueExists = false
        var queueEnabled = false
        var statusDetail = ""

        if (statusResult.successful) {
            val statusOut = statusResult.standardOutput
            if (statusOut.contains("printer $safeName", ignoreCase = true) || statusOut.contains(safeName, ignoreCase = true)) {
                queueExists = true
                queueEnabled = !statusOut.contains("disabled since", ignoreCase = true) && !statusOut.contains("disabled", ignoreCase = true)
                statusDetail = statusOut.trim()
            }
        }

        // Fallback: If not confirmed yet, check global "lpstat -p"
        if (!queueExists) {
            val allPrintersResult = commandExecutor.execute(listOf("lpstat", "-p"), null, timeoutMillis)
            if (allPrintersResult.successful) {
                val allPrinters = CupsOutputParser.parsePrinters(allPrintersResult.standardOutput, "", null)
                val matched = allPrinters.firstOrNull { it.name.equals(safeName, ignoreCase = true) }
                if (matched != null) {
                    queueExists = true
                    queueEnabled = matched.isAvailable
                    statusDetail = matched.status.orEmpty()
                }
            }
        }

        // 2. Query CUPS device URI via "lpstat -v $safeName" and fallback to "lpstat -v"
        val deviceResult = commandExecutor.execute(listOf("lpstat", "-v", safeName), null, timeoutMillis)
        var deviceUri = if (deviceResult.successful) CupsOutputParser.parseDeviceUri(deviceResult.standardOutput, safeName) else null
        if (deviceUri.isNullOrBlank()) {
            val allDevicesResult = commandExecutor.execute(listOf("lpstat", "-v"), null, timeoutMillis)
            if (allDevicesResult.successful) {
                deviceUri = CupsOutputParser.parseDeviceUri(allDevicesResult.standardOutput, safeName)
            }
        }
        if (!queueExists && !deviceUri.isNullOrBlank()) {
            queueExists = true
            queueEnabled = true
        }

        // 3. Query accepting status via "lpstat -a $safeName" (and fallback to "lpstat -a")
        val acceptResult = commandExecutor.execute(listOf("lpstat", "-a", safeName), null, timeoutMillis)
        var acceptsRequests = true
        if (acceptResult.successful) {
            val acceptOut = acceptResult.standardOutput
            if (acceptOut.contains("not accepting requests", ignoreCase = true)) {
                acceptsRequests = false
                queueEnabled = false
            }
        } else {
            val allAcceptResult = commandExecutor.execute(listOf("lpstat", "-a"), null, timeoutMillis)
            if (allAcceptResult.successful) {
                val acceptingMap = allAcceptResult.standardOutput.lineSequence().mapNotNull(CupsOutputParser::parseAcceptingLine).toMap()
                if (acceptingMap.containsKey(safeName.lowercase())) {
                    queueExists = true
                    acceptsRequests = acceptingMap[safeName.lowercase()] == true
                    if (!acceptsRequests) queueEnabled = false
                }
            }
        }

        // Log diagnostic presence and state
        logger.info("[PRINT] queue=$safeName", "Printer")
        logger.info("[PRINT] queueExists=$queueExists", "Printer")
        logger.info("[PRINT] queueEnabled=$queueEnabled", "Printer")

        if (!queueExists) {
            return PrinterHealthStatus.notConfigured("File d'attente '$safeName' non configurée dans CUPS")
        }

        if (!queueEnabled || !acceptsRequests) {
            val reason = if (!acceptsRequests) "File d'attente '$safeName' refuse les requêtes" else "File d'attente '$safeName' désactivée dans CUPS"
            return PrinterHealthStatus.disabled(reason, deviceUri, statusDetail.ifBlank { null })
        }

        // 4. Hardware reachability / presence check
        if (deviceUri != null) {
            val networkEndpoint = CupsOutputParser.parseNetworkEndpoint(deviceUri)
            if (networkEndpoint != null) {
                val reachable = socketProber.isReachable(networkEndpoint.host, networkEndpoint.port, 1_000)
                return if (reachable) {
                    PrinterHealthStatus.connected(
                        "Imprimante réseau connectée et prête (${networkEndpoint.host}:${networkEndpoint.port})",
                        deviceUri
                    )
                } else {
                    PrinterHealthStatus.disconnected(
                        "Imprimante réseau injoignable (${networkEndpoint.host}:${networkEndpoint.port})",
                        deviceUri,
                        "Échec de connexion socket TCP"
                    )
                }
            }

            if (deviceUri.startsWith("usb://", ignoreCase = true) || deviceUri.contains("/dev/usb/lp", ignoreCase = true)) {
                val present = usbDeviceProber.isUsbDevicePresent(deviceUri, commandExecutor)
                return if (present) {
                    PrinterHealthStatus.connected("Imprimante configurée et disponible", deviceUri)
                } else {
                    PrinterHealthStatus.disconnected(
                        "Câble USB débranché ou imprimante éteinte",
                        deviceUri,
                        "Périphérique USB physique non détecté"
                    )
                }
            }

            if (deviceUri.startsWith("/") || deviceUri.startsWith("file://")) {
                val path = deviceUri.removePrefix("file://")
                val exists = File(path).exists()
                return if (exists) {
                    PrinterHealthStatus.connected("Imprimante configurée et disponible", deviceUri)
                } else {
                    PrinterHealthStatus.disconnected(
                        "Fichier de périphérique local introuvable ($path)",
                        deviceUri,
                        "Fichier inexistant"
                    )
                }
            }
        }

        return PrinterHealthStatus.connected("Imprimante configurée et disponible", deviceUri)
    }

    override fun discoverPrinters(): PrinterDiscoveryResult {
        val printersResult = commandExecutor.execute(listOf("lpstat", "-p"), null, timeoutMillis)
        if (printersResult.commandUnavailable) return discoveryFailure("CUPS command 'lpstat' is not installed", printersResult)
        if (printersResult.timedOut) return discoveryFailure("CUPS printer discovery timed out", printersResult)
        if (!printersResult.successful) return discoveryFailure(cupsFailure("Unable to query CUPS printers", printersResult), printersResult)

        val defaultResult = commandExecutor.execute(listOf("lpstat", "-d"), null, timeoutMillis)
        val acceptingResult = commandExecutor.execute(listOf("lpstat", "-a"), null, timeoutMillis)
        val defaultName = if (defaultResult.successful) CupsOutputParser.parseDefaultPrinter(defaultResult.standardOutput) else null
        val acceptingOutput = if (acceptingResult.successful) acceptingResult.standardOutput else ""
        val printers = CupsOutputParser.parsePrinters(printersResult.standardOutput, acceptingOutput, defaultName)

        logger.info("Discovered ${printers.size} CUPS printer(s); default=${defaultName ?: "none"}", "Printer")
        if (!defaultResult.successful) logger.warning("CUPS default printer query failed: ${defaultResult.standardError.trim()}", "Printer")
        if (!acceptingResult.successful) logger.warning("CUPS availability query failed: ${acceptingResult.standardError.trim()}", "Printer")
        return PrinterDiscoveryResult(printers, printers.firstOrNull { it.isDefault })
    }

    override fun printTestPage(printerName: String, platformTestBytes: ByteArray?): PrintResult {
        val safeName = printerName.trim().ifBlank { DEFAULT_LINUX_POS_QUEUE }
        val bytes = platformTestBytes ?: run {
            val content = buildString {
                appendLine("================================")
                appendLine("         TEST FRANCAIS          ")
                appendLine("================================")
                appendLine()
                appendLine("Café crème")
                appendLine("Thé à la menthe")
                appendLine("Pâtisserie")
                appendLine("Déjeuner")
                appendLine("À emporter")
                appendLine("Espèces")
                appendLine("Réduction")
                appendLine("Quantité")
                appendLine("Numéro")
                appendLine("Merci pour votre visite")
                appendLine("À bientôt")
                appendLine("10,00 €")
                appendLine()
                appendLine("--------------------------------")
                appendLine("Imprimante : $safeName")
                appendLine("Statut     : OK / CONNECTE")
                appendLine("Code page  : CP858 (ESC t 19)")
                appendLine("================================")
            }
            FrenchEscPosEncoder.buildDocument(
                contentBytes = FrenchEscPosEncoder.encodeText(content),
                feedAndCut = true
            )
        }
        return submit(safeName, bytes, raw = true, role = PrinterRole.CASHIER_RECEIPT)
    }

    override fun recordSelection(printerName: String) {
        logger.info("Selected CUPS printer '${printerName.trim()}'", "Printer")
    }

    override fun validateConfiguration(printerName: String): PrintResult {
        val name = printerName.trim().ifBlank { DEFAULT_LINUX_POS_QUEUE }
        val health = checkPrinterHealth(name)
        return when (health.state) {
            PrinterConnectionState.CONNECTED -> PrintResult(true, health.message)
            PrinterConnectionState.DISCONNECTED,
            PrinterConnectionState.DISABLED -> PrintResult(false, health.message, health.detailedReason ?: health.message, PrintErrorCategory.OFFLINE)
            PrinterConnectionState.NOT_CONFIGURED -> PrintResult(false, health.message, health.detailedReason ?: health.message, PrintErrorCategory.NOT_FOUND)
            PrinterConnectionState.ERROR -> PrintResult(false, health.message, health.detailedReason ?: health.message, PrintErrorCategory.CUPS_UNAVAILABLE)
        }
    }

    override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult =
        submit(printerName.trim(), bytes, raw = true, role = role)

    private fun submit(printerName: String, bytes: ByteArray, raw: Boolean, role: PrinterRole = PrinterRole.CASHIER_RECEIPT): PrintResult {
        val targetQueue = printerName.trim()
        if (targetQueue.isBlank()) {
            logger.error("[PRINT] queue=BLANK [ERROR] No printer selected", category = "Printer")
            return PrintResult(false, "No printer selected", "No printer selected", PrintErrorCategory.NOT_CONFIGURED)
        }
        val arguments = buildList {
            addAll(listOf("lp", "-d", targetQueue))
            if (raw) addAll(listOf("-o", "raw"))
        }
        val cmdString = arguments.joinToString(" ")
        logger.info("[PRINT] queue=$targetQueue", "Printer")
        logger.info("[PRINT] command=$cmdString", "Printer")
        logger.info("[PRINT] payloadBytes=${bytes.size}", "Printer")

        val result = commandExecutor.execute(arguments, bytes, timeoutMillis)
        logger.info("[PRINT] exitCode=${result.exitCode ?: "null"}", "Printer")
        logger.info("[PRINT] stdout=${result.standardOutput.trim()}", "Printer")
        if (result.standardError.isNotBlank()) {
            logger.error("[PRINT] stderr=${result.standardError.trim()}", category = "Printer")
        }

        return if (result.successful) {
            val message = result.standardOutput.trim().ifBlank { "Print job accepted by CUPS ($targetQueue)" }
            PrintResult(true, message)
        } else {
            val category = when {
                result.commandUnavailable -> PrintErrorCategory.CUPS_UNAVAILABLE
                result.timedOut -> PrintErrorCategory.TIMEOUT
                result.standardError.contains("permission", ignoreCase = true) -> PrintErrorCategory.PERMISSION_DENIED
                result.standardError.contains("not found", ignoreCase = true) ||
                result.standardError.contains("unknown destination", ignoreCase = true) ||
                result.standardError.contains("does not exist", ignoreCase = true) ||
                result.standardError.contains("Invalid destination", ignoreCase = true) -> PrintErrorCategory.NOT_FOUND
                result.standardError.contains("offline", ignoreCase = true) || result.standardError.contains("disabled", ignoreCase = true) -> PrintErrorCategory.OFFLINE
                else -> PrintErrorCategory.TRANSPORT_FAILURE
            }
            val reason = when {
                result.commandUnavailable -> "CUPS command 'lp' is not installed"
                result.timedOut -> "CUPS print command timed out after ${timeoutMillis}ms"
                else -> cupsFailure("CUPS rejected the print job", result)
            }
            logger.error("[PRINT] CUPS $role print failed for '$targetQueue' [$category]: $reason", category = "Printer")
            PrintResult(false, reason, reason, category)
        }
    }

    private fun discoveryFailure(message: String, result: CommandResult): PrinterDiscoveryResult {
        logger.error("$message: ${result.standardError.trim()}", category = "Printer")
        return PrinterDiscoveryResult(emptyList(), errorMessage = message)
    }

    private fun cupsFailure(prefix: String, result: CommandResult): String {
        val detail = result.standardError.trim().ifBlank { result.standardOutput.trim() }
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }
}

