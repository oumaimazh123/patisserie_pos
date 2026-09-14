package ma.elaroui.pos.desktop.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.print.DEFAULT_LINUX_POS_QUEUE
import ma.elaroui.pos.desktop.print.DesktopPrinterServiceFactory
import ma.elaroui.pos.desktop.print.PrinterConnectionState
import ma.elaroui.pos.desktop.print.PrinterHealthStatus
import ma.elaroui.pos.desktop.print.PrinterInfo
import ma.elaroui.pos.desktop.print.PrinterService
import ma.elaroui.pos.desktop.print.ThermalTicketRenderer
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.desktop.print.WindowsThermalPrinter
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.ReceiptCompany
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage

@Composable
fun PrinterSettingsScreen(
    customerPrinterName: String,
    kitchenPrinterName: String = "",
    paperWidth: Int,
    cashDrawerEnabled: Boolean,
    company: ReceiptCompany,
    strings: DesktopStrings,
    automaticSessionClosingReport: Boolean = true,
    onAutomaticSessionClosingReportChanged: (Boolean) -> Unit = {},
    onSavePrinterSettings: (customer: String, kitchen: String, width: Int, cashDrawerEnabled: Boolean) -> Unit,
    printerService: PrinterService = remember { DesktopPrinterServiceFactory.create() },
    onNavigateToEstablishment: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val isWindows = remember { DesktopPlatform.detect() == DesktopPlatform.WINDOWS }
    var customerPrinter by remember {
        mutableStateOf(
            if (isWindows && (customerPrinterName == DEFAULT_LINUX_POS_QUEUE || WindowsThermalPrinter.isIgnored(customerPrinterName))) ""
            else customerPrinterName.ifBlank { if (isWindows) "" else DEFAULT_LINUX_POS_QUEUE }
        )
    }
    var width by remember { mutableStateOf(paperWidth.toString()) }
    var openDrawerAfterCashPayment by remember { mutableStateOf(cashDrawerEnabled) }
    var autoPrintClosingReport by remember(automaticSessionClosingReport) { mutableStateOf(automaticSessionClosingReport) }
    var physicalPrinters by remember { mutableStateOf<List<PrinterInfo>>(emptyList()) }
    var customerPrinterHealth by remember {
        mutableStateOf(
            PrinterHealthStatus(
                state = PrinterConnectionState.NOT_CONFIGURED,
                message = "Vérification..."
            )
        )
    }
    var customerPrinterError by remember { mutableStateOf<String?>(null) }
    var uiMessage by remember { mutableStateOf<UiMessage?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isTestingRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var isTesting by remember { mutableStateOf(false) }

    suspend fun refreshPrinterStatus() {
        val discovery = withContext(Dispatchers.IO) { printerService.discoverPrinters() }
        val nonIgnored = discovery.printers.filterNot { isWindows && WindowsThermalPrinter.isIgnored(it.name) }
        val defaultName = discovery.defaultPrinter?.name.orEmpty()
        val defaultPrinter = if (isWindows && WindowsThermalPrinter.isIgnored(defaultName)) "" else defaultName
        if (customerPrinter.isBlank() || (isWindows && (customerPrinter == DEFAULT_LINUX_POS_QUEUE || WindowsThermalPrinter.isIgnored(customerPrinter)))) {
            if (defaultPrinter.isNotBlank()) {
                customerPrinter = defaultPrinter
            } else if (nonIgnored.isNotEmpty()) {
                customerPrinter = nonIgnored.first().name
            } else {
                customerPrinter = ""
            }
        }
        val health = withContext(Dispatchers.IO) { printerService.checkPrinterHealth(customerPrinter) }
        physicalPrinters = nonIgnored
        customerPrinterHealth = health
        discovery.errorMessage?.let { uiMessage = UiMessage.error(it) }
    }

    LaunchedEffect(customerPrinter) {
        customerPrinterHealth = withContext(Dispatchers.IO) { printerService.checkPrinterHealth(customerPrinter) }
    }

    LaunchedEffect(Unit) {
        refreshPrinterStatus()
        while (isActive) {
            delay(5_000L)
            customerPrinterHealth = withContext(Dispatchers.IO) { printerService.checkPrinterHealth(customerPrinter) }
        }
    }

    val sampleOrder = Order(
        id = 0L,
        number = "APERÇU-001",
        type = OrderType.COUNTER,
        status = OrderStatus.COMPLETED,
        lines = listOf(
            OrderLine(1L, "Café crème", 1500L, 2, 1000),
            OrderLine(2L, "Croissant Pur Beurre", 800L, 1, 1000)
        ),
        subtotalCentimes = 3800L,
        discountCentimes = 0L,
        taxCentimes = 345L,
        totalCentimes = 3800L,
        tableId = null,
        registerSessionId = 1L,
        cashierId = 1L
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Workspace)
    ) {
        ManagementPageHeader(strings.settings, strings, onBack)

        // Settings Navigation Tabs
        SettingsTabBar(
            selectedTab = SettingsTab.PRINTERS,
            strings = strings,
            onNavigateToEstablishment = onNavigateToEstablishment,
            onNavigateToPrinters = {},
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToDataManagement = onNavigateToDataManagement,
            onNavigateToLicense = onNavigateToLicense
        )

        // Main Scrollable Container
        val printerScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(printerScrollState)
                .verticalScroll(printerScrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                border = BorderStroke(1.dp, PosColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Header Section Title & Helper
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "🖨️ " + strings.printersTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Text(
                            if (isWindows) {
                                strings.text(
                                    "Configuration de l'imprimante thermique de caisse sous Windows (Spooler d'impression).",
                                    "Receipt thermal printer setup on Windows (Print Spooler).",
                                    "إعداد طابعة الإيصالات الحرارية في Windows (Spooler)."
                                )
                            } else {
                                strings.text(
                                    "Configuration de l'imprimante thermique de caisse (CUPS).",
                                    "Receipt thermal printer setup (CUPS).",
                                    "إعداد طابعة الإيصالات الحرارية (CUPS)."
                                )
                            },
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )
                    }

                    // Status Notification Banner (Typed Alert)
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = { uiMessage = null }
                    )

                    // Live Connection Health Status Banner
                    val statusBg = when (customerPrinterHealth.state) {
                        PrinterConnectionState.CONNECTED -> PosColors.SuccessLight
                        PrinterConnectionState.DISCONNECTED -> PosColors.AlertLight
                        PrinterConnectionState.DISABLED -> PosColors.AlertLight
                        PrinterConnectionState.NOT_CONFIGURED,
                        PrinterConnectionState.ERROR -> PosColors.DangerLight
                    }
                    val statusBorder = when (customerPrinterHealth.state) {
                        PrinterConnectionState.CONNECTED -> PosColors.Success.copy(alpha = 0.3f)
                        PrinterConnectionState.DISCONNECTED -> PosColors.Alert.copy(alpha = 0.3f)
                        PrinterConnectionState.DISABLED -> PosColors.Alert.copy(alpha = 0.3f)
                        PrinterConnectionState.NOT_CONFIGURED,
                        PrinterConnectionState.ERROR -> PosColors.Danger.copy(alpha = 0.3f)
                    }
                    val statusText = when (customerPrinterHealth.state) {
                        PrinterConnectionState.CONNECTED -> PosColors.Success
                        PrinterConnectionState.DISCONNECTED -> PosColors.Alert
                        PrinterConnectionState.DISABLED -> PosColors.Alert
                        PrinterConnectionState.NOT_CONFIGURED,
                        PrinterConnectionState.ERROR -> PosColors.Danger
                    }
                    val statusIcon = when (customerPrinterHealth.state) {
                        PrinterConnectionState.CONNECTED -> "🟢"
                        PrinterConnectionState.DISCONNECTED -> "🟠"
                        PrinterConnectionState.DISABLED -> "🟡"
                        PrinterConnectionState.NOT_CONFIGURED,
                        PrinterConnectionState.ERROR -> "🔴"
                    }
                    val statusLabel = when (customerPrinterHealth.state) {
                        PrinterConnectionState.CONNECTED -> strings.text("Connectée / Prête", "Connected / Ready", "متصلة / جاهزة")
                        PrinterConnectionState.DISCONNECTED -> if (isWindows) {
                            strings.text("Déconnectée / Hors ligne", "Disconnected / Offline", "غير متصلة / غير متاحة")
                        } else {
                            strings.text("Déconnectée / Câble débranché", "Disconnected / Cable unplugged", "غير متصلة / الكابل مفصول")
                        }
                        PrinterConnectionState.DISABLED -> if (isWindows) {
                            strings.text("Désactivée sous Windows", "Disabled in Windows", "معطلة في Windows")
                        } else {
                            strings.text("Désactivée dans CUPS", "Disabled in CUPS", "معطلة في CUPS")
                        }
                        PrinterConnectionState.NOT_CONFIGURED -> if (isWindows) {
                            strings.text("Non configurée sous Windows", "Not configured in Windows", "غير مضبوطة في Windows")
                        } else {
                            strings.text("Non configurée dans CUPS", "Not configured in CUPS", "غير مضبوطة في CUPS")
                        }
                        PrinterConnectionState.ERROR -> if (isWindows) {
                            strings.text("Erreur Spooler Windows", "Windows Spooler Error", "خطأ في Spooler Windows")
                        } else {
                            strings.text("Erreur CUPS", "CUPS Error", "خطأ في CUPS")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(statusIcon, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    statusLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = statusText
                                )
                                Text(
                                    customerPrinterHealth.message + (customerPrinterHealth.detailedReason?.let { " — $it" } ?: ""),
                                    fontSize = 12.sp,
                                    color = statusText.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // Section 1: Detected Physical / System Printers
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🖨️", fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (physicalPrinters.isNotEmpty()) {
                                            "${physicalPrinters.size} " + strings.text(
                                                "imprimante(s) détectée(s)",
                                                "printer(s) detected",
                                                "طابعة مكتشفة"
                                            )
                                        } else {
                                            "0 " + strings.text(
                                                "imprimante détectée",
                                                "printer detected",
                                                "طابعة مكتشفة"
                                            )
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PosColors.TextHigh
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            refreshPrinterStatus()
                                            val statusText = if (physicalPrinters.isEmpty()) {
                                                 if (isWindows) {
                                                     strings.text(
                                                         "Aucune imprimante détectée sous Windows.",
                                                         "No printer detected in Windows.",
                                                         "لم يتم اكتشاف أي طابعة في Windows."
                                                     )
                                                 } else {
                                                     strings.text(
                                                         "Aucune imprimante physique détectée.",
                                                         "No physical printer detected.",
                                                         "لم يتم اكتشاف أي طابعة فعلية."
                                                     )
                                                 }
                                            } else {
                                                strings.text(
                                                    "Détection actualisée (${physicalPrinters.size} imprimante(s))",
                                                    "Detection refreshed (${physicalPrinters.size} printer(s))",
                                                    "تم تحديث الاكتشاف"
                                                )
                                            }
                                            uiMessage = UiMessage.info(statusText)
                                        }
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🔄 " + strings.refresh, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Printers Quick Selection
                            if (physicalPrinters.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        strings.text("Sélection rapide imprimante de caisse :", "Quick select receipt printer:", "اختيار سريع لطابعة الصندوق:"),
                                        fontSize = 12.sp,
                                        color = PosColors.TextMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(physicalPrinters, key = PrinterInfo::name) { printer ->
                                            val isSelected = customerPrinter == printer.name
                                            Surface(
                                                onClick = {
                                                    customerPrinter = printer.name
                                                    customerPrinterError = null
                                                    printerService.recordSelection(printer.name)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) PosColors.PrimaryLight else PosColors.Surface,
                                                border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Text(
                                                    buildString {
                                                        append("🖨️ ${printer.name}")
                                                        if (printer.isDefault) append(strings.text(" — Par défaut", " — Default", " — الافتراضية"))
                                                        if (!printer.isAvailable) append(strings.text(" — Indisponible", " — Unavailable", " — غير متاحة"))
                                                        if (printer.isVirtual) append(strings.text(" (Virtuelle/PDF)", " (Virtual/PDF)", " (افتراضية/PDF)"))
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) PosColors.Primary else PosColors.TextHigh,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PosColors.AlertLight,
                                    border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (isWindows) {
                                            strings.text(
                                                "Aucune imprimante détectée sous Windows. Vérifiez que votre imprimante est branchée (USB/Réseau) et installée dans Windows.",
                                                "No printer detected in Windows. Ensure your printer is connected (USB/Network) and installed in Windows.",
                                                "لم يتم اكتشاف أي طابعة في Windows. تأكد من توصيل الطابعة وتثبيتها في Windows."
                                            )
                                        } else {
                                            strings.text(
                                                "Aucune imprimante physique détectée. Connectez/configurez votre imprimante puis cliquez sur Actualiser.",
                                                "No physical printer detected. Connect/configure your printer then click Refresh.",
                                                "لم يتم اكتشاف أي طابعة فعلية. يرجى توصيل الطابعة ثم النقر على تحديث."
                                            )
                                        },
                                        fontSize = 12.sp,
                                        color = PosColors.Alert,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Printer Configuration Fields
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            strings.text("Configuration de l’imprimante", "Printer Configuration", "إعداد الطابعة"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )

                        TouchTextField(
                            value = customerPrinter,
                            onValueChange = { customerPrinter = it; customerPrinterError = null; uiMessage = null },
                            label = if (isWindows) {
                                strings.text("Nom de l'imprimante Windows", "Windows Printer Name", "اسم طابعة Windows")
                            } else {
                                strings.text("Nom de la file CUPS / Imprimante", "CUPS Queue / Printer Name", "اسم طابور CUPS / الطابعة")
                            },
                            placeholder = if (isWindows) "POS-80 / Rongta / Microsoft Print to PDF" else "POS_CLIENT",
                            singleLine = true,
                            isError = !customerPrinterError.isNullOrBlank(),
                            errorMessage = customerPrinterError,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Paper Width Selection Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                strings.text("Largeur du papier thermique", "Thermal paper width", "عرض الورق الحراري"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PosColors.TextMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("80", "58").forEach { w ->
                                    val isSelected = width == w
                                    Surface(
                                        onClick = { width = w },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) PosColors.Primary else PosColors.Surface,
                                        border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text(
                                            "$w mm (${if (w == "80") "Standard 48 col" else "Compact 32 col"})",
                                            color = if (isSelected) Color.White else PosColors.TextHigh,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = openDrawerAfterCashPayment,
                                onCheckedChange = { openDrawerAfterCashPayment = it }
                            )
                            Column {
                                Text(
                                    strings.text(
                                        "Ouvrir le tiroir après un paiement en espèces",
                                        "Open cash drawer after a cash payment",
                                        "فتح درج النقود بعد الدفع نقداً"
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    strings.text(
                                        "Le tiroir doit être connecté à l’imprimante de caisse.",
                                        "The drawer must be connected to the receipt printer.",
                                        "يجب توصيل الدرج بطابعة الإيصالات."
                                    ),
                                    color = PosColors.TextMedium,
                                    fontSize = 11.sp
                                 )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Switch(
                                checked = autoPrintClosingReport,
                                onCheckedChange = {
                                    autoPrintClosingReport = it
                                    onAutomaticSessionClosingReportChanged(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PosColors.Success,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = PosColors.Border
                                )
                            )
                            Column {
                                Text(
                                    strings.text(
                                        "Imprimer automatiquement le rapport à la clôture de session",
                                        "Automatically print the report when closing a session",
                                        "طباعة التقرير تلقائياً عند إغلاق الجلسة"
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    strings.text(
                                        "Utilise l’imprimante client configurée. Une panne d’impression ne bloque jamais la clôture.",
                                        "Uses the configured receipt printer. A print failure never blocks closing.",
                                        "يستخدم طابعة الإيصالات المحددة. فشل الطباعة لا يمنع إغلاق الجلسة."
                                    ),
                                    color = PosColors.TextMedium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Section 3: Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val selectedWidth = if (width.trim() == "58") 58 else 80
                                val customerValidation = printerService.validateConfiguration(customerPrinter.trim())
                                val validationFailure = customerValidation.takeIf { !it.success }
                                if (validationFailure != null) {
                                    customerPrinterError = validationFailure.errorMessage ?: validationFailure.message
                                    return@Button
                                }
                                printerService.recordSelection(customerPrinter.trim())
                                onAutomaticSessionClosingReportChanged(autoPrintClosingReport)
                                onSavePrinterSettings(customerPrinter.trim(), "", selectedWidth, openDrawerAfterCashPayment)
                                uiMessage = UiMessage.success(
                                    strings.text(
                                        "Configuration de l'imprimante enregistrée avec succès.",
                                        "Printer settings saved successfully.",
                                        "تم حفظ إعدادات الطابعة بنجاح."
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(strings.save, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            enabled = !isTesting,
                            onClick = {
                                if (!isTestingRef.compareAndSet(false, true)) return@OutlinedButton
                                isTesting = true
                                val trimmedPrinter = customerPrinter.trim()
                                if (trimmedPrinter.isBlank()) {
                                    isTestingRef.set(false)
                                    isTesting = false
                                    customerPrinterError = strings.text(
                                        "Aucune imprimante valide configurée. Sélectionnez une imprimante avant de lancer le test.",
                                        "No valid printer configured. Please select a printer before running the test.",
                                        "لم يتم تكوين طابعة صالحة. يرجى تحديد طابعة قبل بدء الاختبار."
                                    )
                                    return@OutlinedButton
                                }

                                val selectedWidth = if (width.trim() == "58") 58 else 80
                                val testTicketBytes = ThermalTicketRenderer.render(
                                    order = sampleOrder,
                                    company = company,
                                    kind = TicketKind.CUSTOMER,
                                    paperWidth = selectedWidth
                                )

                                coroutineScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            printerService.printTestPage(trimmedPrinter, testTicketBytes)
                                        }
                                        if (result.success) {
                                            uiMessage = UiMessage.success(
                                                strings.text(
                                                    "Test d'impression envoyé avec succès à « $trimmedPrinter ».",
                                                    "Test print job sent successfully to \"$trimmedPrinter\".",
                                                    "تم إرسال اختبار الطباعة بنجاح."
                                                )
                                            )
                                        } else {
                                            uiMessage = UiMessage.error(
                                                result.errorMessage ?: strings.text(
                                                    "L’imprimante sélectionnée est indisponible. Vérifiez la connexion puis réessayez.",
                                                    "The selected printer is unavailable. Check the connection and try again.",
                                                    "الطابعة المحددة غير متوفرة. يرجى التحقق من الاتصال والمحاولة مرة أخرى."
                                                )
                                            )
                                        }
                                    } catch (_: Throwable) {
                                        uiMessage = UiMessage.error(
                                            strings.text(
                                                "L’imprimante sélectionnée est indisponible. Vérifiez la connexion puis réessayez.",
                                                "The selected printer is unavailable. Check the connection and try again.",
                                                "الطابعة المحددة غير متوفرة. يرجى التحقق من الاتصال والمحاولة مرة أخرى."
                                            )
                                        )
                                    } finally {
                                        isTestingRef.set(false)
                                        isTesting = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .pointerHoverIcon(if (isTesting) PointerIcon.Default else PointerIcon.Hand)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PosColors.Primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    strings.text("Impression en cours...", "Printing...", "جاري الطباعة..."),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text("🖨️ " + strings.text("Test d'impression", "Test Print", "اختبار الطباعة"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
