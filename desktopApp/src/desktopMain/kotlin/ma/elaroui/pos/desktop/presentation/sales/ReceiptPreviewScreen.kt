package ma.elaroui.pos.desktop.presentation.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.SafeProductImage
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.ReceiptCompany
import ma.elaroui.pos.shared.rules.MoneyRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptPreviewScreen(
    order: Order,
    company: ReceiptCompany,
    kind: TicketKind = TicketKind.CUSTOMER,
    customerPrinterConfig: DesktopPrinterConfig,
    kitchenPrinterConfig: DesktopPrinterConfig? = null,
    strings: DesktopStrings,
    onNavigateToPrinterSettings: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var selectedKind by remember { mutableStateOf(kind) }
    var printStatusMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var selectedWidth by remember { mutableStateOf(customerPrinterConfig.paperWidth) }
    var showRawText by remember { mutableStateOf(false) }
    val printerService = remember { DesktopPrinterServiceFactory.create() }
    val formatter = remember { EscPosFormatterFactory.create() }
    val manualPrintGuard = remember { PrintJobDeduplicator(retentionMillis = 1_500L) }

    val rawPreviewText = remember(order, company, selectedKind, selectedWidth) {
        ThermalTicketRenderer.previewText(order, company, selectedKind, selectedWidth)
    }

    val orderDateFormatted = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
    }

    val activePrinterConfig = remember(selectedKind, customerPrinterConfig, kitchenPrinterConfig, selectedWidth) {
        val baseConfig = if (selectedKind == TicketKind.KITCHEN && !kitchenPrinterConfig?.printerName.isNullOrBlank()) {
            kitchenPrinterConfig!!
        } else {
            customerPrinterConfig
        }
        baseConfig.copy(paperWidth = selectedWidth)
    }

    LaunchedEffect(activePrinterConfig.printerName) {
        val isWindows = DesktopPlatform.detect() == DesktopPlatform.WINDOWS
        val target = activePrinterConfig.printerName.trim()
        val defaultTarget = if (isWindows) {
            printerService.getDefaultPrinter()?.name.orEmpty()
        } else {
            DEFAULT_LINUX_POS_QUEUE
        }
        val effective = target.ifBlank { defaultTarget }
        if (effective.isBlank() || (isWindows && WindowsThermalPrinter.isIgnored(effective))) {
            printStatusMessage = strings.text(
                "Aucune imprimante connectée détectée. Veuillez brancher une imprimante de caisse.",
                "No connected printer detected. Please connect a POS printer.",
                "لم يتم العثور على طابعة متصلة. يرجى توصيل طابعة."
            )
            isError = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = when (selectedKind) {
                TicketKind.CUSTOMER -> strings.customerReceipt
                TicketKind.KITCHEN -> strings.kitchenTicket
                TicketKind.PREPARATION -> strings.preparationTicket
            },
            strings = strings,
            onBackToDashboard = onBack
        ) {
            // Paper Width Switcher Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(80, 58).forEach { w ->
                    val isSelected = selectedWidth == w
                    Surface(
                        onClick = { selectedWidth = w },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PosColors.Primary else PosColors.Surface,
                        border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                        modifier = Modifier
                            .height(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                "$w mm",
                                color = if (isSelected) Color.White else PosColors.TextHigh,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Primary Print Action
            Button(
                onClick = {
                    val isWindows = DesktopPlatform.detect() == DesktopPlatform.WINDOWS
                    val defaultTarget = if (isWindows) {
                        printerService.getDefaultPrinter()?.name.orEmpty()
                    } else {
                        DEFAULT_LINUX_POS_QUEUE
                    }
                    val targetPrinter = activePrinterConfig.printerName.trim().ifBlank { defaultTarget }

                    if (targetPrinter.isBlank() || (isWindows && WindowsThermalPrinter.isIgnored(targetPrinter))) {
                        printStatusMessage = strings.text(
                            "Aucune imprimante connectée trouvée. Veuillez vérifier le branchement de votre imprimante.",
                            "No connected printer found. Please verify your printer connection.",
                            "لم يتم العثور على طابعة متصلة. يرجى التحقق من توصيل الطابعة."
                        )
                        isError = true
                        return@Button
                    }

                    val health = printerService.checkPrinterHealth(targetPrinter)
                    if (!health.isConnected) {
                        printStatusMessage = strings.text(
                            "Impossible d'imprimer sur « $targetPrinter » : ${health.message}",
                            "Unable to print to \"$targetPrinter\": ${health.message}",
                            "تعذر الطباعة على الطابعة : ${health.message}"
                        ) + (health.detailedReason?.let { " ($it)" } ?: "")
                        isError = true
                        return@Button
                    }

                    val printKey = "manual:${order.id}:${selectedKind.name}:$targetPrinter"
                    if (!manualPrintGuard.acquire(printKey)) {
                        printStatusMessage = strings.text(
                            "Impression déjà envoyée. Patientez avant de réimprimer.",
                            "Print already submitted. Wait before reprinting.",
                            "تم إرسال الطباعة بالفعل. انتظر قبل إعادة الطباعة."
                        )
                        isError = true
                        return@Button
                    }
                    val formatted = formatter.format(
                        EscPosPrintRequest(order, company, selectedKind, selectedWidth, isReprint = true)
                    )
                    val result = when (formatted) {
                        is EscPosFormatResult.Success -> printerService.printRaw(
                            targetPrinter,
                            formatted.bytes,
                            PrinterRole.CASHIER_RECEIPT
                        )
                        is EscPosFormatResult.Failure -> PrintResult(false, formatted.message, formatted.message, formatted.category)
                    }
                    if (result.success) {
                        printStatusMessage = strings.text(
                            "Ticket envoyé à l'imprimante « $targetPrinter » avec succès.",
                            "Receipt sent to printer \"$targetPrinter\" successfully.",
                            "تم إرسال التذكرة إلى الطابعة بنجاح."
                        )
                        isError = false
                    } else {
                        manualPrintGuard.releaseAfterFailure(printKey)
                        printStatusMessage = strings.text(
                            "Impossible d'imprimer sur « $targetPrinter ». Vérifiez que l'imprimante est allumée et connectée.",
                            "Unable to print to \"$targetPrinter\". Please check if the printer is powered on and connected.",
                            "تعذر الطباعة على الطابعة. يرجى التحقق من تشغيلها وتوصيلها."
                        ) + result.errorMessage?.let { " ($it)" }.orEmpty()
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    "🖨️ " + when (selectedKind) {
                        TicketKind.CUSTOMER -> strings.printReceipt
                        TicketKind.KITCHEN -> strings.printKitchen
                        TicketKind.PREPARATION -> strings.printPreparation
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Ticket Kind Selector Bar
        Surface(
            color = PosColors.Surface,
            border = BorderStroke(1.dp, PosColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.text("Type de ticket :", "Ticket Type:", "نوع التذكرة:"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosColors.TextMedium
                )

                val kinds = listOf(TicketKind.CUSTOMER to ("📄 " + strings.customerReceipt))

                kinds.forEach { (k, label) ->
                    val isSelected = selectedKind == k
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedKind = k },
                        label = {
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PosColors.Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { showRawText = !showRawText },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PosColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        if (showRawText) "Visualiser Ticket" else "Texte ESC/POS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Notification Banner (Status / Missing Printer Alert)
        if (printStatusMessage.isNotBlank()) {
            Surface(
                color = if (isError) PosColors.DangerLight else PosColors.SuccessLight,
                border = BorderStroke(1.dp, if (isError) PosColors.Danger.copy(alpha = 0.4f) else PosColors.Success.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    ) {
                        Text(if (isError) "⚠️ " else "✅ ", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            printStatusMessage,
                            color = if (isError) PosColors.Danger else PosColors.Success,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isError && onNavigateToPrinterSettings != null && activePrinterConfig.printerName.isBlank()) {
                        Button(
                            onClick = onNavigateToPrinterSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                "⚙️ " + strings.text("Configurer l'imprimante", "Configure Printer", "إعداد الطابعة"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Main Scrollable Area with Centered Realistic Receipt
        val receiptScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 16.dp)
                .touchDragScroll(receiptScrollState)
                .verticalScroll(receiptScrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            val ticketCardWidth = if (selectedWidth == 58) 330.dp else 400.dp

            if (showRawText) {
                // Monospace Thermal Printer Text Preview
                Card(
                    modifier = Modifier.width(ticketCardWidth).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = rawPreviewText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (selectedWidth == 58) 11.sp else 12.sp,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .width(ticketCardWidth)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedKind) {
                            TicketKind.CUSTOMER -> {
                                // Customer Receipt Layout (Full Fiscal / Store Details)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (company.logoPath.isNotBlank()) {
                                        SafeProductImage(
                                            imagePath = company.logoPath,
                                            contentDescription = company.name,
                                            placeholderText = "",
                                            modifier = Modifier
                                                .size(if (selectedWidth == 58) 60.dp else 76.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }

                                    if (company.printEstablishmentName) {
                                        Text(
                                            company.name.ifBlank { "PATISSERIE_POS" },
                                            fontSize = if (selectedWidth == 58) 16.sp else 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PosColors.TextHigh,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    if (company.specialty.isNotBlank()) {
                                        Text(
                                            company.specialty,
                                            fontSize = if (selectedWidth == 58) 12.sp else 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PosColors.TextMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    if (company.phone.isNotBlank()) {
                                        Text(
                                            "Tél : ${company.phone}",
                                            fontSize = 12.sp,
                                            color = PosColors.TextMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    val legalList = listOfNotNull(
                                        company.ice.takeIf { it.isNotBlank() }?.let { "ICE: $it" },
                                        company.taxId.takeIf { it.isNotBlank() }?.let { "IF: $it" },
                                        company.commercialRegister.takeIf { it.isNotBlank() }?.let { "RC: $it" },
                                        company.patente.takeIf { it.isNotBlank() }?.let { "Patente: $it" }
                                    )
                                    if (legalList.isNotEmpty()) {
                                        Text(
                                            legalList.joinToString(" • "),
                                            fontSize = 11.sp,
                                            color = PosColors.TextMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                ReceiptDashedLine()

                                // Order Metadata
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "FACTURE / REÇU DE CAISSE",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.TextHigh,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    ReceiptMetaRow(label = strings.text("N° Commande :", "Order #:", "رقم الطلب:"), value = order.number)
                                    val typeDesc = when (order.type) {
                                        OrderType.DINE_IN -> if (order.tableId != null) "Sur place (Table ${order.tableId})" else "Sur place"
                                        OrderType.TAKEAWAY -> "À emporter"
                                        OrderType.COUNTER -> "Au comptoir"
                                        OrderType.PREORDER -> "Précommande"
                                    }
                                    ReceiptMetaRow(label = strings.text("Type :", "Type:", "النوع:"), value = typeDesc)
                                    ReceiptMetaRow(label = strings.text("Date & Heure :", "Date & Time:", "التاريخ والوقت:"), value = orderDateFormatted)
                                }

                                ReceiptDashedLine()

                                // Line items with prices
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    order.lines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    line.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PosColors.TextHigh
                                                )
                                                Text(
                                                    "${line.quantity} × ${MoneyRules.formatFixed(line.unitPriceCentimes)} DH" +
                                                            if (line.taxRateBasisPoints > 0) " (TVA ${line.taxRateBasisPoints / 100}%)" else "",
                                                    fontSize = 11.sp,
                                                    color = PosColors.TextMedium
                                                )
                                            }
                                            Text(
                                                "${MoneyRules.formatFixed(line.unitPriceCentimes * line.quantity)} DH",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.TextHigh
                                            )
                                        }
                                    }
                                }

                                ReceiptDashedLine()

                                // Financial Summary
                                val htCentimes = order.totalCentimes - order.taxCentimes
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (order.discountCentimes > 0) {
                                        ReceiptSummaryRow("Sous-total :", "${MoneyRules.formatFixed(order.subtotalCentimes)} DH")
                                        ReceiptSummaryRow("Remise :", "-${MoneyRules.formatFixed(order.discountCentimes)} DH", PosColors.Danger)
                                    }
                                    if (order.taxCentimes > 0) {
                                        ReceiptSummaryRow("Total HT :", "${MoneyRules.formatFixed(htCentimes)} DH")
                                        ReceiptSummaryRow("TVA :", "${MoneyRules.formatFixed(order.taxCentimes)} DH")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PosColors.Workspace,
                                        border = BorderStroke(1.dp, PosColors.Border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("TOTAL TTC", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                                            Text("${MoneyRules.formatFixed(order.totalCentimes)} DH", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PosColors.PrimaryDark)
                                        }
                                    }
                                }

                                ReceiptDashedLine()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("✨ Merci de votre visite et à bientôt ! ✨", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PosColors.TextMedium, textAlign = TextAlign.Center)
                                    if (company.address.isNotBlank()) {
                                        Text(company.address, fontSize = 12.sp, color = PosColors.TextMedium, textAlign = TextAlign.Center)
                                    }
                                    Text("Système de Caisse Certifié POS", fontSize = 10.sp, color = PosColors.TextMuted, textAlign = TextAlign.Center)
                                }
                            }

                            TicketKind.KITCHEN -> {
                                // Kitchen / Bar Ticket Layout (NO logo, NO fiscal/store info, NO prices)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PosColors.DangerLight,
                                        border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "🍳 TICKET CUISINE / BAR",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PosColors.Danger,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }

                                ReceiptDashedLine()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ReceiptMetaRow(label = "N° Commande :", value = order.number)
                                    val typeDesc = when (order.type) {
                                        OrderType.DINE_IN -> if (order.tableId != null) "Sur place (TABLE ${order.tableId})" else "Sur place"
                                        OrderType.TAKEAWAY -> "À EMPORTER"
                                        OrderType.COUNTER -> "AU COMPTOIR"
                                        OrderType.PREORDER -> "PRÉCOMMANDE"
                                    }
                                    ReceiptMetaRow(label = "Type de service :", value = typeDesc)
                                    ReceiptMetaRow(label = "Heure de commande :", value = orderDateFormatted)
                                }

                                ReceiptDashedLine()

                                // Emphasized items and quantities (NO prices)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    order.lines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PosColors.BakeryBrown,
                                                modifier = Modifier.padding(end = 10.dp)
                                            ) {
                                                Text(
                                                    "${line.quantity} ×",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Text(
                                                line.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.TextHigh
                                            )
                                        }
                                    }
                                }

                                ReceiptDashedLine()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "*** À PRÉPARER EN CUISINE ***",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.Danger,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            TicketKind.PREPARATION -> {
                                // Cashier Preparation Ticket Layout (NO fiscal info, includes items + total)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PosColors.AlertLight,
                                        border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "📋 TICKET DE PRÉPARATION",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PosColors.Alert,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }

                                ReceiptDashedLine()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ReceiptMetaRow(label = "N° Commande :", value = order.number)
                                    val typeDesc = when (order.type) {
                                        OrderType.DINE_IN -> if (order.tableId != null) "Sur place (Table ${order.tableId})" else "Sur place"
                                        OrderType.TAKEAWAY -> "À emporter"
                                        OrderType.COUNTER -> "Au comptoir"
                                        OrderType.PREORDER -> "Précommande"
                                    }
                                    ReceiptMetaRow(label = "Type :", value = typeDesc)
                                    ReceiptMetaRow(label = "Date & Heure :", value = orderDateFormatted)
                                }

                                ReceiptDashedLine()

                                // Line items with prices
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    order.lines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                "${line.quantity} × ${line.name}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PosColors.TextHigh
                                            )
                                            Text(
                                                "${MoneyRules.formatFixed(line.unitPriceCentimes * line.quantity)} DH",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.TextHigh
                                            )
                                        }
                                    }
                                }

                                ReceiptDashedLine()

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("TOTAL COMMANDE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                                        Text("${MoneyRules.formatFixed(order.totalCentimes)} DH", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PosColors.TextHigh)
                                    }
                                }

                                ReceiptDashedLine()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "*** COMMANDE EN COURS ***",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.Alert,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = PosColors.TextMedium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh)
    }
}

@Composable
private fun ReceiptSummaryRow(label: String, value: String, valueColor: Color = PosColors.TextMedium) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = PosColors.TextMedium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun ReceiptDashedLine() {
    Text(
        "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
        color = PosColors.Border,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}
