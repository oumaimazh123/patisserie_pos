package ma.elaroui.pos.desktop.presentation.license

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.license.WindowsLicenseManager
import ma.elaroui.pos.desktop.license.WindowsLicenseState
import ma.elaroui.pos.desktop.license.WindowsLicenseStatus
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.getClipboardText
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.settings.SettingsTab
import ma.elaroui.pos.desktop.presentation.settings.SettingsTabBar
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@Composable
fun LicenseManagementScreen(
    licenseManager: WindowsLicenseManager,
    licenseState: WindowsLicenseState,
    strings: DesktopStrings,
    onLicenseUpdated: (WindowsLicenseState) -> Unit,
    canNavigateBack: Boolean = true,
    onNavigateToEstablishment: () -> Unit = {},
    onNavigateToPrinters: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Workspace)
    ) {
        ManagementPageHeader(
            title = strings.settings,
            strings = strings,
            onBackToDashboard = onBack.takeIf { canNavigateBack }
        )

        // Settings Navigation Tabs
        SettingsTabBar(
            selectedTab = SettingsTab.LICENSE,
            strings = strings,
            onNavigateToEstablishment = onNavigateToEstablishment,
            onNavigateToPrinters = onNavigateToPrinters,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToDataManagement = onNavigateToDataManagement,
            onNavigateToLicense = {}
        )

        // Main Scrollable Container
        val licenseScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(licenseScrollState)
                .verticalScroll(licenseScrollState),
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
                            "🔑 " + strings.licenseTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Text(
                            strings.text(
                                "Consultez le statut de votre licence logicielle, identifiez votre machine et activez votre version définitive.",
                                "View software license status, machine hardware installation ID and activate your full commercial version.",
                                "تحقق من حالة ترخيص البرنامج ومعرف الجهاز وقم بتفعيل النسخة الكاملة."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )
                    }

                    // Status Notification Banner (Success / Info only)
                    if (feedbackMessage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.SuccessLight,
                            border = BorderStroke(1.dp, PosColors.Success.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅ ", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = feedbackMessage,
                                    color = PosColors.Success,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Section 1: Current License Status Card
                    val (statusBg, statusBorder, statusText, statusColor) = when (licenseState.status) {
                        WindowsLicenseStatus.VALID -> Quadruple(PosColors.SuccessLight, PosColors.Success.copy(alpha = 0.3f), "✅ Licence Active & Validée (Version Complète)", PosColors.Success)
                        WindowsLicenseStatus.TRIAL_ACTIVE -> Quadruple(PosColors.PrimaryLight, PosColors.Primary.copy(alpha = 0.3f), "⏳ Période d'essai active (${licenseState.daysRemaining} jour(s) restant(s))", PosColors.Primary)
                        WindowsLicenseStatus.TRIAL_EXPIRED -> Quadruple(PosColors.DangerLight, PosColors.Danger.copy(alpha = 0.3f), "⚠️ Période d'essai expirée", PosColors.Danger)
                        WindowsLicenseStatus.EXPIRED -> Quadruple(PosColors.DangerLight, PosColors.Danger.copy(alpha = 0.3f), "⚠️ Licence commerciale expirée", PosColors.Danger)
                        WindowsLicenseStatus.WRONG_DEVICE -> Quadruple(PosColors.AlertLight, PosColors.Alert.copy(alpha = 0.3f), "⚠️ Clé non compatible avec cette machine", PosColors.Alert)
                        WindowsLicenseStatus.INVALID -> Quadruple(PosColors.DangerLight, PosColors.Danger.copy(alpha = 0.3f), "⚠️ Clé de licence invalide ou corrompue", PosColors.Danger)
                        WindowsLicenseStatus.CLOCK_ROLLBACK -> Quadruple(PosColors.DangerLight, PosColors.Danger.copy(alpha = 0.3f), "⚠️ Horloge système modifiée ou invalide", PosColors.Danger)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                statusText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )

                            if (licenseState.customer != null) {
                                Text(
                                    strings.text("Titulaire : ", "Licensed to: ", "مرخص لـ: ") + licenseState.customer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PosColors.TextHigh
                                )
                            }
                        }
                    }

                    // Section 2: Machine Identifiers (Installation ID & Request Code)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        strings.installationId,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        licenseState.installationId,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = PosColors.TextMedium
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            clipboardManager.setText(AnnotatedString(licenseState.installationId))
                                        }
                                        runCatching {
                                            val sel = java.awt.datatransfer.StringSelection(licenseState.installationId)
                                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                        }
                                        feedbackMessage = strings.text("Identifiant machine copié dans le presse-papiers", "Machine ID copied", "تم نسخ المعرف")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("📋 " + strings.text("Copier", "Copy", "نسخ"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            HorizontalDivider(color = PosColors.Border)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        strings.requestCode,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        licenseManager.requestCode(),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = PosColors.TextMedium
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val reqCode = runCatching { licenseManager.requestCode() }.getOrDefault("")
                                        runCatching {
                                            clipboardManager.setText(AnnotatedString(reqCode))
                                        }
                                        runCatching {
                                            val sel = java.awt.datatransfer.StringSelection(reqCode)
                                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                        }
                                        feedbackMessage = strings.text("Code de demande copié dans le presse-papiers", "Request code copied", "تم نسخ رمز الطلب")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("📋 " + strings.text("Copier", "Copy", "نسخ"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Section 3: Activation Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            strings.text("Activer ou Mettre à Jour la Clé de Licence", "Activate or Update License Key", "تفعيل أو تحديث مفتاح الترخيص"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )

                        Text(
                            strings.text(
                                "Collez ici la clé de licence cryptographique fournie par notre équipe après votre achat.",
                                "Paste the cryptographic license key token provided upon purchase.",
                                "الصق هنا مفتاح الترخيص المشفر المقدم من طرف الدعم الفني."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )

                        TouchTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it; keyError = null; feedbackMessage = "" },
                            placeholder = "Ex: eyJsaWNlbnNlSWQiOiJMSUMt...",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 3,
                            isError = !keyError.isNullOrBlank(),
                            errorMessage = keyError,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipText = getClipboardText()
                                    if (!clipText.isNullOrBlank()) {
                                        keyInput = clipText.trim()
                                        keyError = null
                                        feedbackMessage = strings.text("Clé collée depuis le presse-papier.", "Key pasted from clipboard.", "تم لصق المفتاح من الحافظة.")
                                    } else {
                                        feedbackMessage = ""
                                        keyError = strings.text("Le presse-papier est vide.", "Clipboard is empty.", "الحافظة فارغة.")
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("📋 " + strings.text("Coller la clé", "Paste key", "لصق المفتاح"))
                            }

                            OutlinedButton(
                                onClick = {
                                    val selected = NativeFileDialogs.selectLicense(
                                        strings.text("Importer une licence", "Import a licence", "استيراد ترخيص")
                                    ) ?: return@OutlinedButton
                                    runCatching {
                                        require(Files.size(selected) in 1..64_000) { "Invalid licence file size" }
                                        Files.readString(selected, StandardCharsets.UTF_8).trim()
                                    }.onSuccess {
                                        keyInput = it
                                        keyError = null
                                        feedbackMessage = strings.text(
                                            "Fichier de licence chargé. Vérifiez puis activez.",
                                            "Licence file loaded. Review it and activate.",
                                            "تم تحميل ملف الترخيص. تحقق منه ثم قم بالتفعيل."
                                        )
                                    }.onFailure {
                                        feedbackMessage = ""
                                        keyError = strings.text(
                                            "Fichier de licence invalide ou illisible.",
                                            "Invalid or unreadable licence file.",
                                            "ملف الترخيص غير صالح أو غير قابل للقراءة."
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f).height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("📂 " + strings.text("Importer fichier", "Import file", "استيراد ملف"))
                            }
                        }

                        Button(
                            onClick = {
                                val newState = licenseManager.activate(keyInput.trim())
                                onLicenseUpdated(newState)
                                if (newState.allowsUse) {
                                    feedbackMessage = strings.text("Licence activée avec succès ! Merci pour votre confiance.", "License activated successfully!", "تم تفعيل الترخيص بنجاح!")
                                    keyError = null
                                    keyInput = ""
                                } else {
                                    feedbackMessage = ""
                                    keyError = strings.text("Clé de licence invalide ou incompatible avec cet appareil.", "Invalid or incompatible license key.", "مفتاح الترخيص غير صالح أو غير متوافق مع هذا الجهاز.")
                                }
                            },
                            enabled = keyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("🔑 " + strings.activateNow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
