package ma.elaroui.pos.desktop.print

import java.awt.Color as AwtColor
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.ReceiptCompany
import org.jetbrains.skia.Image as SkiaImage

enum class TicketKind { CUSTOMER, KITCHEN, PREPARATION }
data class DesktopPrinterConfig(val printerName: String, val paperWidth: Int = 80, val enabled: Boolean = true)
data class DetectedPrinter(val name: String, val isVirtual: Boolean)

object ThermalTicketRenderer {
    const val PRODUCT_ITEM_VERTICAL_SPACING_LINES = 1

    fun loadLogoBufferedImage(path: String?): BufferedImage? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() == 0L) return null
        return runCatching {
            ImageIO.read(file) ?: run {
                val bytes = Files.readAllBytes(file.toPath())
                val skiaImage = SkiaImage.makeFromEncoded(bytes)
                val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(skiaImage)
                val bmp = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        bmp.setRGB(x, y, bitmap.getColor(x, y))
                    }
                }
                bmp
            }
        }.getOrNull()
    }

    fun renderLogoEscPos(logoPath: String?, paperWidth: Int): ByteArray {
        if (logoPath.isNullOrBlank()) return ByteArray(0)
        val image = loadLogoBufferedImage(logoPath) ?: return ByteArray(0)
        val origW = image.width
        val origH = image.height
        if (origW <= 0 || origH <= 0) return ByteArray(0)

        // Keep comfortable printer margins while making the store identity more visible.
        // Scaling always uses the same factor on both axes, so the logo is never distorted.
        val maxW = if (paperWidth == 58) 280 else 420
        val maxH = if (paperWidth == 58) 140 else 185

        val scale = minOf(maxW.toDouble() / origW, maxH.toDouble() / origH, 1.0)
        val targetW = (origW * scale).toInt().coerceAtLeast(8)
        val targetH = (origH * scale).toInt().coerceAtLeast(8)

        val widthInBytes = (targetW + 7) / 8
        val finalW = widthInBytes * 8
        val finalH = targetH

        val scaled = BufferedImage(finalW, finalH, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.color = AwtColor.WHITE
        g.fillRect(0, 0, finalW, finalH)
        g.drawImage(image, (finalW - targetW) / 2, 0, targetW, targetH, null)
        g.dispose()

        val numBytes = widthInBytes * finalH
        val bitData = ByteArray(numBytes)
        var byteIndex = 0

        for (y in 0 until finalH) {
            for (byteX in 0 until widthInBytes) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val px = byteX * 8 + bit
                    if (px < finalW) {
                        val rgb = scaled.getRGB(px, y)
                        val alpha = (rgb shr 24) and 0xFF
                        val r = (rgb shr 16) and 0xFF
                        val gVal = (rgb shr 8) and 0xFF
                        val b = rgb and 0xFF
                        val luminance = if (alpha < 128) 255 else (0.299 * r + 0.587 * gVal + 0.114 * b).toInt()
                        if (luminance < 160) {
                            currentByte = currentByte or (1 shl (7 - bit))
                        }
                    }
                }
                bitData[byteIndex++] = currentByte.toByte()
            }
        }

        val xL = (widthInBytes % 256).toByte()
        val xH = (widthInBytes / 256).toByte()
        val yL = (finalH % 256).toByte()
        val yH = (finalH / 256).toByte()

        // Center alignment + GS v 0 0 xL xH yL yH + bitmap data + LF + reset alignment
        val escCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
        val escReset = byteArrayOf(0x0A, 0x1B, 0x61, 0x00)

        return escCenter + header + bitData + escReset
    }

    fun render(
        order: Order,
        establishment: String,
        kind: TicketKind,
        paperWidth: Int,
        isReprint: Boolean = false
    ) = render(order, ReceiptCompany(name = establishment, address = "", phone = ""), kind, paperWidth, isReprint)

    fun render(
        order: Order,
        company: ReceiptCompany,
        kind: TicketKind,
        paperWidth: Int,
        isReprint: Boolean = false,
        paymentMethod: PaymentMethod? = null,
        receivedCentimes: Long? = null,
        changeCentimes: Long? = null,
        cashierName: String? = null,
        timestampMs: Long? = null,
        tableLabel: String? = null,
        areaLabel: String? = null,
        specialInstructions: String? = null
    ): ByteArray {
        require(paperWidth == 58 || paperWidth == 80) { "Unsupported paper width: $paperWidth" }
        val escInit = EscPosCommands.INIT_CP858
        val escCut = EscPosCommands.FEED_AND_CUT

        val logoBytes = if (kind == TicketKind.CUSTOMER && company.logoPath.isNotBlank()) {
            renderLogoEscPos(company.logoPath, paperWidth)
        } else ByteArray(0)

        val text = previewText(order, company, kind, paperWidth, isReprint, paymentMethod, receivedCentimes, changeCentimes, cashierName, timestampMs, tableLabel, areaLabel, specialInstructions)
        val textBytes = renderStyledText(text, kind, FrenchEscPosEncoder.CHARSET, company.printEstablishmentName)

        return escInit + logoBytes + textBytes + escCut
    }

    /**
     * Adds restrained ESC/POS emphasis with real hardware justification commands:
     * - establishment name: real ALIGN_CENTER, bold, double height;
     * - DUPLICATA, FACTURE / RECU, Merci de votre visite !: real ALIGN_CENTER, bold;
     * - store metadata (address, phone, ICE): real ALIGN_CENTER;
     * - product lines (qty x name + price): ALIGN_LEFT, bold, with price right-aligned on the same line;
     * - sales details, financial totals, and payments: ALIGN_LEFT fixed columnar layout.
     */
    internal fun renderStyledText(
        text: String,
        kind: TicketKind,
        charset: java.nio.charset.Charset,
        hasEstablishmentNameHeader: Boolean = true
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val lines = text.lines()
        var inHeaderBlock = (kind == TicketKind.CUSTOMER)
        var waitingForProductDivider = false
        var inProductSection = false
        var currentAlign = 0 // 0 = LEFT, 1 = CENTER, 2 = RIGHT

        fun setAlign(align: Int) {
            if (currentAlign != align) {
                when (align) {
                    1 -> output.write(EscPosCommands.ALIGN_CENTER)
                    2 -> output.write(EscPosCommands.ALIGN_RIGHT)
                    else -> output.write(EscPosCommands.ALIGN_LEFT)
                }
                currentAlign = align
            }
        }

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            val isDivider = line.isNotEmpty() && line.all { it == '-' || it == '=' }

            if (isDivider && inHeaderBlock) {
                inHeaderBlock = false
            }

            if (kind == TicketKind.CUSTOMER) {
                if (trimmedLine.startsWith("Type:")) {
                    waitingForProductDivider = true
                } else if (waitingForProductDivider && isDivider) {
                    waitingForProductDivider = false
                    inProductSection = true
                } else if (inProductSection && isDivider) {
                    inProductSection = false
                }
            }

            val isEstablishmentName = (kind == TicketKind.CUSTOMER && index == 0 && hasEstablishmentNameHeader)
            val isCentered = inHeaderBlock ||
                    trimmedLine == "*** DUPLICATA ***" ||
                    trimmedLine == "FACTURE / RECU" ||
                    trimmedLine == "FACTURE / REÇU" ||
                    trimmedLine == "Merci de votre visite !" ||
                    trimmedLine == "Merci pour votre visite !" ||
                    trimmedLine == "Merci pour votre visite" ||
                    trimmedLine == "À bientôt" ||
                    trimmedLine == "A bientot" ||
                    trimmedLine.startsWith("Wi-Fi:") ||
                    trimmedLine.startsWith("Code:") ||
                    trimmedLine.startsWith("***") ||
                    trimmedLine == "INSTRUCTIONS"

            val isProductLine = inProductSection && !isDivider && line.isNotBlank()

            if (isCentered && trimmedLine.isNotBlank()) {
                setAlign(1) // ALIGN_CENTER
                if (isEstablishmentName) {
                    output.write(EscPosCommands.BOLD_ON)
                    output.write(EscPosCommands.DOUBLE_HEIGHT)
                    output.write(trimmedLine.toByteArray(charset))
                    output.write(EscPosCommands.NORMAL_SIZE)
                    output.write(EscPosCommands.BOLD_OFF)
                } else if (trimmedLine == "*** DUPLICATA ***" || trimmedLine == "FACTURE / RECU" || trimmedLine == "Merci de votre visite !" || trimmedLine.startsWith("***")) {
                    output.write(EscPosCommands.BOLD_ON)
                    output.write(trimmedLine.toByteArray(charset))
                    output.write(EscPosCommands.BOLD_OFF)
                } else {
                    output.write(trimmedLine.toByteArray(charset))
                }
            } else {
                setAlign(0) // ALIGN_LEFT
                if (isProductLine) {
                    output.write(EscPosCommands.BOLD_ON)
                    output.write(line.toByteArray(charset))
                    output.write(EscPosCommands.BOLD_OFF)
                } else {
                    output.write(line.toByteArray(charset))
                }
            }

            if (index < lines.lastIndex) {
                output.write(0x0A)
            }
        }

        setAlign(0)
        return output.toByteArray()
    }

    fun previewText(
        order: Order,
        company: ReceiptCompany,
        kind: TicketKind,
        paperWidth: Int,
        isReprint: Boolean = false,
        paymentMethod: PaymentMethod? = null,
        receivedCentimes: Long? = null,
        changeCentimes: Long? = null,
        cashierName: String? = null,
        timestampMs: Long? = null,
        tableLabel: String? = null,
        areaLabel: String? = null,
        specialInstructions: String? = null
    ): String {
        require(paperWidth == 58 || paperWidth == 80) { "Unsupported paper width: $paperWidth" }
        val width = if (paperWidth == 58) 32 else 48
        val divider = "-".repeat(width)
        val doubleDivider = "=".repeat(width)
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
        val formattedDate = dateFormat.format(java.util.Date(timestampMs ?: System.currentTimeMillis()))

        return buildString {
            when (kind) {
                TicketKind.CUSTOMER -> {
                    // 1. Full Fiscal / Client Receipt
                    if (company.printEstablishmentName) {
                        appendLine(center(company.name.ifBlank { "PATISSERIE_POS" }, width))
                    }
                    if (company.specialty.isNotBlank()) {
                        wrap(company.specialty, width).forEach { line -> appendLine(center(line, width)) }
                    }
                    if (company.phone.isNotBlank()) {
                        appendLine(center("Tél: ${company.phone}", width))
                    }
                    listOf(
                        "ICE" to company.ice,
                        "IF" to company.taxId,
                        "RC" to company.commercialRegister,
                        "Patente" to company.patente
                    ).filter { it.second.isNotBlank() }.forEach {
                        appendLine(center("${it.first}: ${it.second}", width))
                    }
                    appendLine(divider)

                    if (isReprint) {
                        appendLine(center("*** DUPLICATA ***", width))
                        appendLine(divider)
                    }

                    appendLine(center("FACTURE / RECU", width))
                    appendLine(columns("N° Vente:", order.number, width))
                    appendLine(columns("Date:", formattedDate, width))
                    if (!cashierName.isNullOrBlank()) {
                        appendLine(columns("Caissier:", cashierName, width))
                    }

                    val typeDesc = when (order.type) {
                        OrderType.DINE_IN -> dineInLabel(order, tableLabel, areaLabel, uppercase = false)
                        OrderType.TAKEAWAY -> "A emporter"
                        OrderType.COUNTER -> "Au comptoir"
                        OrderType.PREORDER -> "Précommande"
                    }
                    appendLine(columns("Type:", typeDesc, width))
                    appendLine(divider)

                    // Line items (Fixed column layout: quantity x name on left, price on right)
                    order.lines.forEachIndexed { idx, line ->
                        if (line.quantity > 1) {
                            val qtyName = "${line.quantity} x ${line.name}"
                            if (qtyName.length > width) {
                                wrap(qtyName, width).forEach { appendLine(it) }
                            } else {
                                appendLine(qtyName)
                            }
                            val unitCalc = "   ${money(line.unitPriceCentimes)} x ${line.quantity}"
                            val lineTotal = money(line.unitPriceCentimes * line.quantity)
                            appendLine(columns(unitCalc, lineTotal, width))
                        } else {
                            val qtyName = "${line.quantity} x ${line.name}"
                            val lineTotal = money(line.unitPriceCentimes * line.quantity)
                            appendLine(columns(qtyName, lineTotal, width))
                        }
                        if (idx < order.lines.lastIndex) {
                            repeat(PRODUCT_ITEM_VERTICAL_SPACING_LINES) {
                                appendLine()
                            }
                        }
                    }

                    appendLine(divider)

                    // Financial breakdown
                    if (order.discountCentimes > 0) {
                        appendLine(columns("SOUS-TOTAL", money(order.subtotalCentimes), width))
                        appendLine(columns("REMISE", "-${money(order.discountCentimes)}", width))
                    }

                    if (order.taxCentimes > 0) {
                        val htCentimes = order.totalCentimes - order.taxCentimes
                        appendLine(columns("TOTAL HT", money(htCentimes), width))
                        appendLine(columns("TVA", money(order.taxCentimes), width))
                    }

                    appendLine(doubleDivider)
                    appendLine(columns("TOTAL", money(order.totalCentimes), width))
                    appendLine(doubleDivider)

                    // Payment details
                    if (paymentMethod != null) {
                        val methodLabel = when (paymentMethod) {
                            PaymentMethod.CASH -> "ESPECES"
                            PaymentMethod.CARD -> "CARTE / TPE"
                            PaymentMethod.CARNET_CLIENT -> "CARNET CLIENT"
                            PaymentMethod.MOBILE_QR -> "MOBILE / QR"
                        }
                        appendLine(columns("Mode paiement:", methodLabel, width))
                        if (paymentMethod == PaymentMethod.CASH && receivedCentimes != null && receivedCentimes > 0) {
                            appendLine(columns("Espèces reçues:", money(receivedCentimes), width))
                            if (changeCentimes != null && changeCentimes > 0) {
                                appendLine(columns("Monnaie rendue:", money(changeCentimes), width))
                            }
                        }
                        appendLine(divider)
                    }

                    appendLine(center("Merci de votre visite !", width))
                    if (company.address.isNotBlank()) {
                        appendLine(divider)
                        wrap(company.address, width).forEach { line -> appendLine(center(line, width)) }
                    }
                    appendLine()
                    appendLine()
                }

                TicketKind.KITCHEN -> {
                    // 2. Kitchen / Bar operational ticket: NO fiscal info, NO store address/logo/tax, NO prices
                    appendLine(center("*** TICKET CUISINE / BAR ***", width))
                    appendLine(divider)

                    if (isReprint) {
                        appendLine(center("*** REIMPRESSION ***", width))
                        appendLine(divider)
                    }

                    appendLine(columns("N° Commande:", order.number, width))
                    appendLine(columns("Date:", formattedDate, width))
                    if (!cashierName.isNullOrBlank()) {
                        appendLine(columns("Serveur/Caisse:", cashierName, width))
                    }

                    val typeDesc = when (order.type) {
                        OrderType.DINE_IN -> dineInLabel(order, tableLabel, areaLabel, uppercase = true)
                        OrderType.TAKEAWAY -> "A EMPORTER"
                        OrderType.COUNTER -> "AU COMPTOIR"
                        OrderType.PREORDER -> "PRECOMMANDE"
                    }
                    appendLine(columns("Type:", typeDesc, width))
                    appendLine(doubleDivider)

                    // Emphasized items with quantities (NO prices)
                    order.lines.forEach { line ->
                        val lineStr = ">> ${line.quantity} x ${line.name}"
                        wrap(lineStr, width).forEach(::appendLine)
                    }

                    if (!specialInstructions.isNullOrBlank()) {
                        appendLine(divider)
                        appendLine(center("INSTRUCTIONS", width))
                        wrap(specialInstructions.trim(), width).forEach(::appendLine)
                    }

                    appendLine(doubleDivider)
                    appendLine(center("*** A PREPARER ***", width))
                    appendLine()
                    appendLine()
                }

                TicketKind.PREPARATION -> {
                    // 3. Cashier Preparation ticket: NO fiscal info, NO store address/logo/tax, but includes items + total for order tracking
                    appendLine(center("*** TICKET DE PREPARATION ***", width))
                    appendLine(divider)

                    if (isReprint) {
                        appendLine(center("*** REIMPRESSION ***", width))
                        appendLine(divider)
                    }

                    appendLine(columns("N° Commande:", order.number, width))
                    appendLine(columns("Date:", formattedDate, width))
                    if (!cashierName.isNullOrBlank()) {
                        appendLine(columns("Caissier:", cashierName, width))
                    }

                    val typeDesc = when (order.type) {
                        OrderType.DINE_IN -> dineInLabel(order, tableLabel, areaLabel, uppercase = false)
                        OrderType.TAKEAWAY -> "A emporter"
                        OrderType.COUNTER -> "Au comptoir"
                        OrderType.PREORDER -> "Précommande"
                    }
                    appendLine(columns("Type:", typeDesc, width))
                    appendLine(divider)

                    // Line items with prices for operational cashier reference
                    order.lines.forEach { line ->
                        val qtyName = "${line.quantity} x ${line.name}"
                        val lineTotal = money(line.unitPriceCentimes * line.quantity)
                        appendLine(columns(qtyName, lineTotal, width))
                    }

                    appendLine(doubleDivider)
                    appendLine(columns("TOTAL A REGLER", money(order.totalCentimes), width))
                    appendLine(doubleDivider)
                    appendLine(center("*** COMMANDE EN COURS ***", width))
                    appendLine()
                    appendLine()
                }
            }
        }
    }

    private fun money(value: Long) = "${value / 100}.${(value % 100).toString().padStart(2, '0')} DH"

    private fun dineInLabel(order: Order, tableLabel: String?, areaLabel: String?, uppercase: Boolean): String {
        val table = tableLabel?.takeIf(String::isNotBlank) ?: order.tableId?.let { "Table $it" }
        val location = listOfNotNull(table, areaLabel?.takeIf(String::isNotBlank)).joinToString(" - ")
        val label = if (location.isBlank()) "Sur place" else "Sur place ($location)"
        return if (uppercase) label.uppercase() else label
    }

    private fun center(value: String, width: Int): String {
        val trimmed = value.trim()
        if (trimmed.length >= width) return trimmed.take(width)
        val pad = (width - trimmed.length) / 2
        return " ".repeat(pad) + trimmed
    }

    private fun columns(left: String, right: String, width: Int): String {
        val r = right.trim()
        val l = left.trimEnd()
        if (r.length >= width) return r.take(width)
        if (l.length + r.length + 1 > width) {
            val maxLeft = (width - r.length - 1).coerceAtLeast(0)
            return l.take(maxLeft).padEnd(width - r.length) + r
        }
        val spaces = width - l.length - r.length
        return l + " ".repeat(spaces) + r
    }

    private fun wrap(value: String, width: Int): List<String> {
        if (value.length <= width) return listOf(value)
        val words = value.split(" ")
        val result = mutableListOf<String>()
        var current = ""
        for (word in words) {
            if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= width) {
                current += " $word"
            } else {
                result += current
                current = word
            }
        }
        if (current.isNotBlank()) result += current
        return result
    }
}

class WindowsThermalPrinter {
    companion object {
        private val IGNORED_PRINTER_PATTERNS = listOf(
            "onenote",
            "fax",
            "xps",
            "root print queue",
            "document writer",
            "pdf",
            "print to pdf",
            "microsoft print to pdf",
            "adobe pdf"
        )

        fun isIgnored(name: String): Boolean {
            val n = name.trim().lowercase()
            if (n.isBlank()) return true
            return isVirtual(n) || IGNORED_PRINTER_PATTERNS.any { n.contains(it) }
        }

        private val VIRTUAL_PRINTER_PATTERNS = listOf(
            "print to pdf",
            "onenote",
            "xps",
            "fax",
            "pdf converter",
            "pdf writer",
            "adobe pdf",
            "sage pdf",
            "foxit",
            "cutepdf",
            "bullzip",
            "pdfcreator",
            "dopdf",
            "primopdf",
            "nitro pdf",
            "pdf architect",
            "microsoft print to pdf",
            "document writer",
            "root print queue"
        )

        fun isVirtual(name: String): Boolean {
            val n = name.trim().lowercase()
            if (n.isBlank()) return false
            if (n == "pdf" || n.endsWith(" pdf") || n.startsWith("pdf ") || n.contains(".pdf")) return true
            return VIRTUAL_PRINTER_PATTERNS.any { n.contains(it) }
        }
    }

    fun availablePrinters(): List<String> = try {
        PrintServiceLookup.lookupPrintServices(null, null)
            ?.mapNotNull { it.name }
            ?.filterNot { isIgnored(it) }
            ?.sorted()
            .orEmpty()
    } catch (_: Throwable) {
        emptyList()
    }

    fun physicalPrinters(): List<String> = availablePrinters().filterNot { isVirtual(it) }

    fun virtualPrinters(): List<String> = availablePrinters().filter { isVirtual(it) }

    fun detectedPrinters(): List<DetectedPrinter> = availablePrinters().map {
        DetectedPrinter(it, isVirtual(it))
    }

    fun isPrinterAvailable(name: String): Boolean = runCatching {
        val target = name.trim()
        if (target.isBlank()) return false
        val allServices = PrintServiceLookup.lookupPrintServices(null, null) ?: return false
        allServices.any { it.name.equals(target, ignoreCase = true) }
    }.getOrDefault(false)

    /** Re-resolve the spooler for each attempt so reconnecting a printer can recover. */
    fun print(config: DesktopPrinterConfig, bytes: ByteArray, attempts: Int = 2): Result<Unit> {
        val safeAttempts = attempts.coerceIn(1, 3)
        var failure: Throwable? = null

        repeat(safeAttempts) {
            val result = runCatching {
                require(config.enabled) { "Printer is disabled in settings" }
                val targetName = config.printerName.trim()
                require(targetName.isNotBlank()) { "No printer name specified" }
                require(!isIgnored(targetName)) { "L'imprimante '$targetName' est virtuelle et ne peut pas imprimer de tickets de caisse" }

                val allServices = PrintServiceLookup.lookupPrintServices(null, null)
                    ?: error("Print services lookup unavailable")
                val service = allServices.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                    ?: error("Printer '$targetName' is unavailable, disconnected, or powered off")

                val flavor = rawFlavor(service)
                val doc = SimpleDoc(bytes, flavor, null)
                val job = service.createPrintJob()
                job.print(doc, HashPrintRequestAttributeSet())
            }
            if (result.isSuccess) return result
            failure = result.exceptionOrNull()
        }
        return Result.failure(failure ?: IllegalStateException("Print job execution failed"))
    }

    private fun rawFlavor(service: PrintService): DocFlavor = runCatching {
        listOf(
            DocFlavor.BYTE_ARRAY.AUTOSENSE,
            DocFlavor.BYTE_ARRAY.TEXT_PLAIN_US_ASCII,
            DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8
        ).firstOrNull { runCatching { service.isDocFlavorSupported(it) }.getOrDefault(false) }
    }.getOrNull() ?: DocFlavor.BYTE_ARRAY.AUTOSENSE
}
