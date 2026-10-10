package ma.elaroui.pos.desktop.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopBuildInfo
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.SafeProductImage
import ma.elaroui.pos.desktop.presentation.components.TouchPinField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.shared.domain.ReceiptCompany
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage

@Composable
fun SettingsScreen(
    initialCompany: ReceiptCompany,
    initialLogoPath: String,
    currentLanguage: DesktopLanguage,
    strings: DesktopStrings,
    onSaveSettings: suspend (company: ReceiptCompany, language: DesktopLanguage) -> Unit,
    onImportLogo: () -> String?,
    onRemoveLogo: () -> Unit,
    onNavigateToPrinterSettings: () -> Unit = {},
    onNavigateToCustomerDisplay: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToLicenseManagement: () -> Unit = {},
    isCancellationPinConfigured: Boolean = false,
    onSetCancellationPin: suspend (String) -> String? = { null },
    onRemoveCancellationPin: suspend () -> Unit = {},
    automaticSessionClosingReport: Boolean = true,
    onAutomaticSessionClosingReportChanged: suspend (Boolean) -> Unit = {},
    onBack: () -> Unit,
    message: String = ""
) {
    val operationScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialCompany.name) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var specialty by remember { mutableStateOf(initialCompany.specialty) }
    var address by remember { mutableStateOf(initialCompany.address) }
    var phone by remember { mutableStateOf(initialCompany.phone) }
    var ice by remember { mutableStateOf(initialCompany.ice) }
    var taxId by remember { mutableStateOf(initialCompany.taxId) }
    var rc by remember { mutableStateOf(initialCompany.commercialRegister) }
    var patente by remember { mutableStateOf(initialCompany.patente) }
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    var printEstablishmentName by remember { mutableStateOf(initialCompany.printEstablishmentName) }
    var uiMessage by remember { mutableStateOf<UiMessage?>(message.takeIf { it.isNotBlank() }?.let { UiMessage.info(it) }) }
    var logoPath by remember { mutableStateOf(initialLogoPath) }

    var hasCancellationPin by remember(isCancellationPinConfigured) { mutableStateOf(isCancellationPinConfigured) }
    var showCancellationPinDialog by remember { mutableStateOf(false) }
    var cancellationPinInput by remember { mutableStateOf("") }
    var cancellationPinConfirmInput by remember { mutableStateOf("") }
    var cancellationPinInputError by remember { mutableStateOf<String?>(null) }
    var cancellationPinConfirmError by remember { mutableStateOf<String?>(null) }
    var autoPrintClosingReport by remember(automaticSessionClosingReport) {
        mutableStateOf(automaticSessionClosingReport)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(strings.settings, strings, onBack)

        // Settings Navigation Tabs
        SettingsTabBar(
            selectedTab = SettingsTab.ESTABLISHMENT,
            strings = strings,
            onNavigateToEstablishment = {},
            onNavigateToPrinters = onNavigateToPrinterSettings,
            onNavigateToCustomerDisplay = onNavigateToCustomerDisplay,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToDataManagement = onNavigateToDataManagement,
            onNavigateToLicense = onNavigateToLicenseManagement
        )

        // Main Scrollable Settings Form Container
        val settingsScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(settingsScrollState)
                .verticalScroll(settingsScrollState),
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
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header Section Title & Helper
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "🏪 " + strings.text("Établissement & Facture", "Establishment & Invoice", "المؤسسة والفاتورة"),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )
                        Text(
                            strings.text(
                                "Configurez l'identité commerciale, vos coordonnées et informations légales imprimées sur vos tickets et factures.",
                                "Configure your business identity, contact details and legal tax IDs printed on customer tickets and invoices.",
                                "قم بإعداد الهوية التجارية ومعلومات الاتصال والبيانات الضريبية المطبوعة على إيصالات وفواتير الزبائن."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )
                    }

                    // Semantic Status / Feedback Banner (Typed Alert)
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = { uiMessage = null }
                    )

                    // Logo & Branding Card Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PosColors.Surface,
                                border = BorderStroke(1.dp, PosColors.Border),
                                modifier = Modifier.size(96.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    SafeProductImage(
                                        imagePath = logoPath.takeIf { it.isNotBlank() },
                                        contentDescription = strings.logoLabel,
                                        placeholderText = strings.text("Aucun logo", "No logo", "لا يوجد شعار"),
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    strings.logoLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PosColors.TextHigh
                                )
                                Text(
                                    strings.text(
                                        "Ce logo apparaîtra en haut des tickets de caisse et factures imprimés.",
                                        "This logo will appear at the top of printed receipts and invoices.",
                                        "سيظهر هذا الشعار أعلى إيصالات وفواتير الصندوق المطبوعة."
                                    ),
                                    fontSize = 12.sp,
                                    color = PosColors.TextMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onImportLogo()?.let {
                                                logoPath = it
                                                uiMessage = UiMessage.success(strings.text("Logo mis à jour", "Logo updated", "تم تحديث الشعار"))
                                            }
                                        },
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PosColors.Border),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary)
                                    ) {
                                        Text(
                                            if (logoPath.isNotBlank()) strings.text("Changer le logo", "Change logo", "تغيير الشعار") else strings.text("Ajouter un logo", "Add logo", "إضافة شعار"),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (logoPath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                onRemoveLogo()
                                                logoPath = ""
                                                uiMessage = UiMessage.success(strings.text("Logo supprimé", "Logo removed", "تم حذف الشعار"))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Text(strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 1: Informations Générales
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            strings.companyInfo,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )

                        TouchTextField(
                            value = name,
                            onValueChange = { name = it; nameError = null; uiMessage = null },
                            label = strings.companyNameLabel + " *",
                            placeholder = strings.text("Nom commercial de l'établissement", "Business name", "الاسم التجاري للمؤسسة"),
                            singleLine = true,
                            isError = !nameError.isNullOrBlank(),
                            errorMessage = nameError,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        TouchTextField(
                            value = specialty,
                            onValueChange = { specialty = it },
                            label = strings.text("Spécialité / Sous-titre de l'établissement", "Specialty / Subtitle", "تخصص المؤسسة / العنوان الفرعي"),
                            placeholder = strings.text("Ex: Café Restaurant, Boulangerie Pâtisserie, Épicerie, Snack...", "Ex: Coffee Shop, Bakery, Grocery, Snack...", "مثال: مقهى ومطعم، مخبزة وحلويات، بقالة، وجبات سريعة..."),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        TouchTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = strings.addressLabel,
                            placeholder = strings.text("Adresse complète de l'établissement", "Full address", "العنوان الكامل للمؤسسة"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        TouchTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = strings.phoneLabel,
                            placeholder = "05XX XX XX XX / 06XX XX XX XX",
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Toggle: Afficher le nom de l'établissement sur le ticket
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        strings.text("Afficher le nom de l’établissement sur le ticket", "Print establishment name on receipt", "عرض اسم المؤسسة على الإيصال"),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        strings.text(
                                            "Si désactivé, le nom ne sera pas imprimé sur le reçu client (le logo reste imprimé si configuré).",
                                            "If disabled, the name will not be printed on the customer receipt (the logo remains printed if configured).",
                                            "إذا تم تعطيله، لن يُطبع الاسم على إيصال الزبون (يظل الشعار مطبوعًا إذا تم تكوينه)."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextMedium
                                    )
                                }
                                Switch(
                                    checked = printEstablishmentName,
                                    onCheckedChange = { printEstablishmentName = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PosColors.Success,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = PosColors.BorderVariant
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Section 2: Langue de l'application
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            strings.languageLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                Pair(DesktopLanguage.FR, "Français"),
                                Pair(DesktopLanguage.EN, "English"),
                                Pair(DesktopLanguage.AR, "العربية")
                            ).forEach { (lang, label) ->
                                val isSelected = selectedLanguage == lang
                                Surface(
                                    onClick = { selectedLanguage = lang },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                    border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (isSelected) Color.White else PosColors.TextHigh,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "🧾 " + strings.text("Rapport de clôture", "Closing Report", "تقرير الإغلاق"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        strings.text(
                                            "Imprimer automatiquement le rapport à la clôture de session",
                                            "Automatically print the report when closing a session",
                                            "طباعة التقرير تلقائياً عند إغلاق الجلسة"
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        strings.text(
                                            "Utilise l’imprimante client configurée. Une panne d’impression ne bloque jamais la clôture.",
                                            "Uses the configured receipt printer. A print failure never blocks closing.",
                                            "يستخدم طابعة الإيصالات المحددة. فشل الطباعة لا يمنع إغلاق الجلسة."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextMedium
                                    )
                                }
                                Switch(
                                    checked = autoPrintClosingReport,
                                    onCheckedChange = { enabled ->
                                        if (!isSaving) {
                                            isSaving = true
                                            autoPrintClosingReport = enabled
                                            operationScope.launch {
                                                try { onAutomaticSessionClosingReportChanged(enabled) }
                                                finally { isSaving = false }
                                            }
                                        }
                                    },
                                    enabled = !isSaving,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PosColors.Success,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = PosColors.BorderVariant
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Section 3: Mentions Fiscales & Légales (Optionnel)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            strings.text("Informations Fiscales & Légales (Optionnel)", "Tax & Legal IDs (Optional)", "المعلومات الضريبية والقانونية (اختياري)"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )

                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 300.dp
                        ) {
                            TouchTextField(
                                value = ice,
                                onValueChange = { ice = it },
                                label = strings.text("Identifiant Commun de l’Entreprise (ICE)", "ICE (Tax ID)", "التعريف الموحد للمقاولة ICE"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            TouchTextField(
                                value = taxId,
                                onValueChange = { taxId = it },
                                label = strings.text("Identifiant Fiscal (IF)", "Tax ID (IF)", "الرقم الضريبي IF"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 300.dp
                        ) {
                            TouchTextField(
                                value = rc,
                                onValueChange = { rc = it },
                                label = strings.text("Registre de Commerce (RC)", "Commercial Register (RC)", "السجل التجاري RC"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            TouchTextField(
                                value = patente,
                                onValueChange = { patente = it },
                                label = strings.text("Taxe Professionnelle (Patente)", "Business License (Patente)", "رقم الباتينتا Patente"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Section 4: Code PIN d'annulation des commandes (Admin)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "🛡️ " + strings.text("Code PIN d’annulation des commandes", "Order Cancellation PIN", "رمز PIN لإلغاء الطلبات"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )
                        Text(
                            strings.text(
                                "Sécurise l'annulation de commandes pour les caissiers. Si configuré, un PIN à 4-6 chiffres sera exigé lors de toute tentative d'annulation par un caissier.",
                                "Secures order cancellation for cashiers. If configured, a 4-6 digit PIN will be required when a cashier attempts to cancel an order.",
                                "حماية إلغاء الطلبات للكاشير. إذا تم تكوينه، سيُطلب رمز PIN مكون من 4 إلى 6 أرقام عند أي محاولة إلغاء من قِبل الكاشير."
                            ),
                            fontSize = 12.sp,
                            color = PosColors.TextMedium
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (hasCancellationPin) PosColors.SuccessLight else PosColors.Surface,
                                        border = BorderStroke(1.dp, if (hasCancellationPin) PosColors.Success.copy(alpha = 0.3f) else PosColors.Border)
                                    ) {
                                        Text(
                                            text = if (hasCancellationPin)
                                                strings.text("Actif (PIN configuré)", "Active (PIN set)", "مفعل (تم تعيين PIN)")
                                            else
                                                strings.text("Inactif (Annulation libre)", "Inactive (Open cancellation)", "غير مفعل (إلغاء حر)"),
                                            color = if (hasCancellationPin) PosColors.Success else PosColors.TextMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            cancellationPinInput = ""
                                            cancellationPinConfirmInput = ""
                                            cancellationPinInputError = null
                                            cancellationPinConfirmError = null
                                            showCancellationPinDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(38.dp).pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text(
                                            if (hasCancellationPin)
                                                strings.text("Modifier le PIN", "Change PIN", "تغيير PIN")
                                            else
                                                strings.text("Définir un PIN", "Set PIN", "تعيين PIN"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (hasCancellationPin) {
                                        OutlinedButton(
                                            onClick = {
                                                if (!isSaving) {
                                                    isSaving = true
                                                    operationScope.launch {
                                                        try {
                                                            onRemoveCancellationPin()
                                                            hasCancellationPin = false
                                                            uiMessage = UiMessage.success(strings.text("Code PIN d'annulation désactivé", "Cancellation PIN disabled", "تم تعطيل رمز PIN للإلغاء"))
                                                        } finally { isSaving = false }
                                                    }
                                                }
                                            },
                                            enabled = !isSaving,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                            modifier = Modifier.height(38.dp).pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Text(
                                                 strings.text("Supprimer", "Remove", "حذف"),
                                                color = PosColors.Danger,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Primary Save Button
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = strings.required
                            } else {
                                val updated = initialCompany.copy(
                                    name = name.trim(),
                                    specialty = specialty.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    ice = ice.trim(),
                                    taxId = taxId.trim(),
                                    commercialRegister = rc.trim(),
                                    patente = patente.trim(),
                                    wifiName = initialCompany.wifiName,
                                    wifiCode = initialCompany.wifiCode,
                                    printEstablishmentName = printEstablishmentName
                                )
                                if (!isSaving) {
                                    isSaving = true
                                    operationScope.launch {
                                        try {
                                            onAutomaticSessionClosingReportChanged(autoPrintClosingReport)
                                            onSaveSettings(updated, selectedLanguage)
                                            uiMessage = UiMessage.success(strings.text("Paramètres enregistrés avec succès", "Settings saved successfully", "تم حفظ الإعدادات بنجاح"))
                                        } finally { isSaving = false }
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(
                            "💾 " + strings.save,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = "${DesktopBuildInfo.APPLICATION_NAME} ${DesktopBuildInfo.version} · ${DesktopBuildInfo.operatingSystem} · ${DesktopBuildInfo.architecture}",
                        color = PosColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Dialog: Définir / Modifier le PIN d'annulation des commandes
        if (showCancellationPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCancellationPinDialog = false
                    cancellationPinInputError = null
                    cancellationPinConfirmError = null
                },
                title = {
                    Text(
                        strings.text("Code PIN d’annulation des commandes", "Order Cancellation PIN", "رمز PIN لإلغاء الطلبات"),
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            strings.text(
                                "Saisissez un code PIN numérique de 4 à 6 chiffres. Ce code sera exigé lors de l'annulation de commandes par les caissiers.",
                                "Enter a 4 to 6 digit numeric PIN. This code will be required when cashiers cancel orders.",
                                "أدخل رمز PIN رقمي مكون من 4 إلى 6 أرقام. سيُطلب هذا الرمز عند إلغاء الطلبات من قِبل الكاشير."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )

                        TouchPinField(
                            pin = cancellationPinInput,
                            onPinChange = { cancellationPinInput = it; cancellationPinInputError = null },
                            label = strings.text("Nouveau PIN (4 à 6 chiffres)", "New PIN (4 to 6 digits)", "PIN جديد (4 إلى 6 أرقام)"),
                            isError = !cancellationPinInputError.isNullOrBlank(),
                            errorMessage = cancellationPinInputError,
                            maxDigits = 6,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        TouchPinField(
                            pin = cancellationPinConfirmInput,
                            onPinChange = { cancellationPinConfirmInput = it; cancellationPinConfirmError = null },
                            label = strings.text("Confirmer le PIN", "Confirm PIN", "تأكيد PIN"),
                            isError = !cancellationPinConfirmError.isNullOrBlank(),
                            errorMessage = cancellationPinConfirmError,
                            maxDigits = 6,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val validationErr = ma.elaroui.pos.shared.rules.PinValidationRules.validatePinConfirmation(
                                cancellationPinInput,
                                cancellationPinConfirmInput
                            )
                            if (validationErr != null) {
                                when (validationErr) {
                                    ma.elaroui.pos.shared.rules.PinValidationError.BLANK -> {
                                        if (cancellationPinInput.isBlank()) cancellationPinInputError = strings.required
                                        else cancellationPinConfirmError = strings.required
                                    }
                                    ma.elaroui.pos.shared.rules.PinValidationError.INVALID_LENGTH -> cancellationPinInputError = "Le PIN doit comporter 4 à 6 chiffres."
                                    ma.elaroui.pos.shared.rules.PinValidationError.NOT_DIGITS -> cancellationPinInputError = "Le PIN ne doit contenir que des chiffres."
                                    ma.elaroui.pos.shared.rules.PinValidationError.MISMATCH -> cancellationPinConfirmError = strings.pinMismatch
                                }
                                return@Button
                            }
                            if (!isSaving) {
                                isSaving = true
                                operationScope.launch {
                                    try {
                                        val error = onSetCancellationPin(cancellationPinInput)
                                        if (error == null) {
                                            hasCancellationPin = true
                                            showCancellationPinDialog = false
                                            uiMessage = UiMessage.success(strings.text("Code PIN d'annulation enregistré avec succès", "Cancellation PIN saved successfully", "تم حفظ رمز PIN للإلغاء بنجاح"))
                                        } else cancellationPinInputError = error
                                    } finally { isSaving = false }
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(strings.confirm, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showCancellationPinDialog = false
                            cancellationPinInputError = null
                            cancellationPinConfirmError = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(strings.cancel, color = PosColors.TextMedium)
                    }
                }
            )
        }
    }
}
