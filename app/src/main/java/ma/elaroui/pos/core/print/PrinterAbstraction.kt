package ma.elaroui.pos.core.print

import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.PrinterSettings
import ma.elaroui.pos.domain.model.PrinterType
import ma.elaroui.pos.domain.model.ReceiptData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class PrinterStatus {
    AVAILABLE,
    UNAVAILABLE,
    BUSY,
    ERROR,
    PERMISSION_REQUIRED
}

sealed class PrintResult {
    object Success : PrintResult()
    data class Failure(val reason: String) : PrintResult()
}

interface ReceiptPrinter {
    fun getStatus(): PrinterStatus
    suspend fun print(data: ReceiptData, settings: PrinterSettings): PrintResult
    suspend fun printTest(restaurantName: String, settings: PrinterSettings): PrintResult
}

class AndroidSystemPrinter(private val context: Context) : ReceiptPrinter {
    override fun getStatus(): PrinterStatus = PrinterStatus.AVAILABLE

    override suspend fun print(data: ReceiptData, settings: PrinterSettings): PrintResult {
        return try {
            val htmlContent = buildReceiptHtml(data, settings.paperWidth)
            printHtml(htmlContent, "Receipt_${data.orderNumber}")
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Failure(e.localizedMessage ?: "Print error")
        }
    }

    override suspend fun printTest(restaurantName: String, settings: PrinterSettings): PrintResult {
        return try {
            val htmlContent = buildTestHtml(restaurantName, settings.paperWidth)
            printHtml(htmlContent, "Test_Receipt")
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Failure(e.localizedMessage ?: "Print test error")
        }
    }

    private fun printHtml(htmlContent: String, jobName: String) {
        val intent = Intent(context, AndroidPrintActivity::class.java).apply {
            putExtra(AndroidPrintActivity.EXTRA_HTML, htmlContent)
            putExtra(AndroidPrintActivity.EXTRA_JOB_NAME, jobName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun buildReceiptHtml(data: ReceiptData, paperWidth: Int): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val widthCss = if (paperWidth == 58) "220px" else "300px"
        val fontSizeCss = if (paperWidth == 58) "11px" else "13px"

        val itemsHtml = data.items.joinToString("") { item ->
            """
            <tr>
                <td style="text-align: left;">${escapeHtml(item.name)} x${item.quantity}</td>
                <td style="text-align: right;">${MonetaryUtils.formatDh(item.lineTotalCentimes)}</td>
            </tr>
            """.trimIndent()
        }

        val reprintHeader = if (data.isReprint) "<div style='text-align:center; font-weight:bold;'>*** DUPLICATA - REIMPRESSION ***</div><hr/>" else ""
        val sellerLegalHtml = listOfNotNull(
            data.sellerIce?.takeIf { it.isNotBlank() }?.let { "ICE: ${escapeHtml(it)}" },
            data.sellerTaxId?.takeIf { it.isNotBlank() }?.let { "IF: ${escapeHtml(it)}" },
            data.sellerCommercialRegister?.takeIf { it.isNotBlank() }?.let { "RC: ${escapeHtml(it)}" },
            data.sellerPatente?.takeIf { it.isNotBlank() }?.let { "Patente: ${escapeHtml(it)}" }
        ).joinToString(" | ")
        val buyerHtml = listOfNotNull(
            data.buyerCompanyName?.takeIf { it.isNotBlank() }?.let { "<div><strong>Client:</strong> ${escapeHtml(it)}</div>" },
            data.buyerAddress?.takeIf { it.isNotBlank() }?.let { "<div><strong>Adresse client:</strong> ${escapeHtml(it)}</div>" },
            data.buyerIce?.takeIf { it.isNotBlank() }?.let { "<div><strong>ICE client:</strong> ${escapeHtml(it)}</div>" }
        ).joinToString("")
        val wifiHtml = listOfNotNull(
            data.wifiName?.takeIf { it.isNotBlank() }?.let { "<div>Wi-Fi: ${escapeHtml(it)}</div>" },
            data.wifiCode?.takeIf { it.isNotBlank() }?.let { "<div>Code Wi-Fi: ${escapeHtml(it)}</div>" }
        ).joinToString("")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <style>
                body { font-family: monospace; width: $widthCss; font-size: $fontSizeCss; margin: 0; padding: 5px; }
                .center { text-align: center; }
                .right { text-align: right; }
                .bold { font-weight: bold; }
                hr { border-top: 1px dashed #000; }
                table { width: 100%; border-collapse: collapse; }
            </style>
        </head>
        <body>
            $reprintHeader
            ${data.restaurantLogoUri?.let { "<div class='center'><img src='$it' style='max-width:120px;max-height:80px'/></div>" } ?: ""}
            <div class="center bold" style="font-size: 16px;">${escapeHtml(data.restaurantName)}</div>
            ${data.restaurantAddress?.takeIf { it.isNotBlank() }?.let { "<div class='center'>${escapeHtml(it)}</div>" } ?: ""}
            ${data.restaurantPhone?.takeIf { it.isNotBlank() }?.let { "<div class='center'>Tél: ${escapeHtml(it)}</div>" } ?: ""}
            ${sellerLegalHtml.takeIf { it.isNotBlank() }?.let { "<div class='center'>$it</div>" } ?: ""}
            <hr/>
            <div><strong>N° Commande:</strong> ${data.orderNumber}</div>
            <div><strong>Type:</strong> ${data.orderType.name}${data.tableName?.let { " - Table $it" } ?: ""}</div>
            <div><strong>Caisse:</strong> ${data.registerName} | <strong>Serveur:</strong> ${data.cashierName}</div>
            <div><strong>Date:</strong> ${dateFormat.format(Date(data.paymentAt))}</div>
            $buyerHtml
            <hr/>
            <table>
                <thead>
                    <tr>
                        <th style="text-align: left;">Article</th>
                        <th style="text-align: right;">Total</th>
                    </tr>
                </thead>
                <tbody>
                    $itemsHtml
                </tbody>
            </table>
            <hr/>
            <table class="bold">
                <tr>
                    <td>TOTAL TTC:</td>
                    <td class="right">${MonetaryUtils.formatDh(data.totalCentimes)}</td>
                </tr>
                <tr>
                    <td>Mode de paiement:</td>
                    <td class="right">${data.paymentMethod.name}</td>
                </tr>
                ${data.receivedAmountCentimes?.let { "<tr><td>Reçu:</td><td class='right'>${MonetaryUtils.formatDh(it)}</td></tr>" } ?: ""}
                ${data.changeAmountCentimes?.let { "<tr><td>Rendu:</td><td class='right'>${MonetaryUtils.formatDh(it)}</td></tr>" } ?: ""}
            </table>
            <hr/>
            ${wifiHtml.takeIf { it.isNotBlank() }?.let { "<div class='center'>$it</div><hr/>" } ?: ""}
            <div class="center">${escapeHtml(data.thankYouMessage)}</div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun buildTestHtml(restaurantName: String, paperWidth: Int): String {
        val widthCss = if (paperWidth == 58) "220px" else "300px"
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <style>
                body { font-family: monospace; width: $widthCss; font-size: 12px; margin: 0; padding: 5px; text-align: center; }
                hr { border-top: 1px dashed #000; }
            </style>
        </head>
        <body>
            <div style="font-weight: bold; font-size: 16px;">$restaurantName</div>
            <div>*** IMPRESSION DE TEST ***</div>
            <hr/>
            <div>Imprimante: Système Android</div>
            <div>Largeur papier: ${paperWidth}mm</div>
            <div>Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}</div>
            <hr/>
            <div>1234567890 - Alignement Test</div>
            <div>Café / Restaurant POS V1</div>
            <hr/>
            <div>TEST REUSSI ✓</div>
        </body>
        </html>
        """.trimIndent()
    }
}

class ConfiguredReceiptPrinter(private val context: Context) : ReceiptPrinter {
    override fun getStatus(): PrinterStatus = PrinterStatus.AVAILABLE

    override suspend fun print(data: ReceiptData, settings: PrinterSettings): PrintResult =
        delegate(settings).print(data, settings)

    override suspend fun printTest(
        restaurantName: String,
        settings: PrinterSettings
    ): PrintResult = delegate(settings).printTest(restaurantName, settings)

    private fun delegate(settings: PrinterSettings): ReceiptPrinter = when (settings.printerType) {
        PrinterType.ANDROID_SYSTEM -> AndroidSystemPrinter(context)
        PrinterType.ETHERNET_ESCPOS -> EthernetEscPosPrinter()
        PrinterType.BLUETOOTH_ESCPOS -> BluetoothEscPosPrinter(context)
    }
}

class EthernetEscPosPrinter : ReceiptPrinter {
    override fun getStatus(): PrinterStatus = PrinterStatus.AVAILABLE

    override suspend fun print(data: ReceiptData, settings: PrinterSettings): PrintResult =
        send(settings.printerAddress, EscPosFormatter.receipt(data, settings.paperWidth))

    override suspend fun printTest(
        restaurantName: String,
        settings: PrinterSettings
    ): PrintResult = send(
        settings.printerAddress,
        EscPosFormatter.test(restaurantName, settings.paperWidth, "Ethernet")
    )

    private fun send(address: String?, bytes: ByteArray): PrintResult {
        val value = address?.trim().orEmpty()
        if (value.isBlank()) return PrintResult.Failure("Adresse IP de l'imprimante manquante.")
        val host = value.substringBefore(':').trim()
        val port = value.substringAfter(':', "9100").toIntOrNull()
            ?: return PrintResult.Failure("Port d'imprimante invalide.")
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4_000)
                socket.soTimeout = 4_000
                socket.getOutputStream().use {
                    it.write(bytes)
                    it.flush()
                }
            }
            PrintResult.Success
        } catch (error: Exception) {
            PrintResult.Failure(error.localizedMessage ?: "Connexion Ethernet impossible.")
        }
    }
}

class BluetoothEscPosPrinter(private val context: Context) : ReceiptPrinter {
    override fun getStatus(): PrinterStatus = PrinterStatus.AVAILABLE

    override suspend fun print(data: ReceiptData, settings: PrinterSettings): PrintResult =
        send(settings.printerAddress, EscPosFormatter.receipt(data, settings.paperWidth))

    override suspend fun printTest(
        restaurantName: String,
        settings: PrinterSettings
    ): PrintResult = send(
        settings.printerAddress,
        EscPosFormatter.test(restaurantName, settings.paperWidth, "Bluetooth")
    )

    @Suppress("MissingPermission")
    private fun send(address: String?, bytes: ByteArray): PrintResult {
        val macAddress = address?.trim().orEmpty()
        if (!BluetoothAdapter.checkBluetoothAddress(macAddress)) {
            return PrintResult.Failure("Adresse Bluetooth MAC invalide.")
        }
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
                ?: return PrintResult.Failure("Bluetooth indisponible sur cet appareil.")
            if (!adapter.isEnabled) return PrintResult.Failure("Bluetooth désactivé.")
            val device = adapter.getRemoteDevice(macAddress)
            val socket = device.createRfcommSocketToServiceRecord(
                UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            socket.use {
                it.connect()
                it.outputStream.use { output ->
                    output.write(bytes)
                    output.flush()
                }
            }
            PrintResult.Success
        } catch (error: SecurityException) {
            PrintResult.Failure("Autorisation Bluetooth requise.")
        } catch (error: Exception) {
            PrintResult.Failure(error.localizedMessage ?: "Connexion Bluetooth impossible.")
        }
    }
}

private object EscPosFormatter {
    private val charset = runCatching { charset("CP858") }.getOrDefault(Charsets.UTF_8)

    fun receipt(data: ReceiptData, paperWidth: Int): ByteArray {
        val columns = if (paperWidth == 58) 32 else 48
        val body = buildString {
            appendLine(center(data.restaurantName, columns))
            data.restaurantAddress?.takeIf { it.isNotBlank() }?.let {
                appendLine(center(it, columns))
            }
            data.restaurantPhone?.takeIf { it.isNotBlank() }?.let {
                appendLine(center("Tél: $it", columns))
            }
            listOfNotNull(
                data.sellerIce?.takeIf { it.isNotBlank() }?.let { "ICE: $it" },
                data.sellerTaxId?.takeIf { it.isNotBlank() }?.let { "IF: $it" },
                data.sellerCommercialRegister?.takeIf { it.isNotBlank() }?.let { "RC: $it" },
                data.sellerPatente?.takeIf { it.isNotBlank() }?.let { "Patente: $it" }
            ).forEach { appendLine(center(it, columns)) }
            appendLine("-".repeat(columns))
            appendLine("Commande: ${data.orderNumber}")
            appendLine("Serveur: ${data.cashierName}")
            data.buyerCompanyName?.takeIf { it.isNotBlank() }?.let { appendLine("Client: $it") }
            data.buyerAddress?.takeIf { it.isNotBlank() }?.let { appendLine("Adresse client: $it") }
            data.buyerIce?.takeIf { it.isNotBlank() }?.let { appendLine("ICE client: $it") }
            appendLine("-".repeat(columns))
            data.items.forEach { appendLine("${it.quantity} x ${it.name}  ${MonetaryUtils.formatDh(it.lineTotalCentimes)}") }
            appendLine("-".repeat(columns))
            appendLine("TOTAL: ${MonetaryUtils.formatDh(data.totalCentimes)}")
            appendLine()
            data.wifiName?.takeIf { it.isNotBlank() }?.let { appendLine(center("Wi-Fi: $it", columns)) }
            data.wifiCode?.takeIf { it.isNotBlank() }?.let { appendLine(center("Code Wi-Fi: $it", columns)) }
            if (!data.wifiName.isNullOrBlank() || !data.wifiCode.isNullOrBlank()) appendLine()
            appendLine(center(data.thankYouMessage, columns))
            appendLine()
            appendLine()
        }
        return byteArrayOf(0x1B, 0x40) + body.toByteArray(charset) +
            byteArrayOf(0x1D, 0x56, 0x00)
    }

    fun test(name: String, width: Int, transport: String): ByteArray {
        val columns = if (width == 58) 32 else 48
        val body = "${center(name, columns)}\n*** TEST $transport ***\nPapier: ${width}mm\n\n\n"
        return byteArrayOf(0x1B, 0x40) + body.toByteArray(charset) +
            byteArrayOf(0x1D, 0x56, 0x00)
    }

    private fun center(text: String, width: Int): String {
        val clean = text.take(width)
        return " ".repeat(((width - clean.length) / 2).coerceAtLeast(0)) + clean
    }
}
