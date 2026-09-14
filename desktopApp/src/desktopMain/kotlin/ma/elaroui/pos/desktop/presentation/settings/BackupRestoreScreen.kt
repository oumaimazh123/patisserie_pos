package ma.elaroui.pos.desktop.presentation.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosConfirmDialog
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.model.UiMessage

@Composable
fun BackupRestoreScreen(
    defaultBackupPath: Path,
    strings: DesktopStrings,
    onBackupRequested: (targetPath: Path) -> Result<Unit>,
    onRestoreRequested: (sourcePath: Path) -> Result<Unit>,
    onNavigateToEstablishment: () -> Unit = {},
    onNavigateToPrinters: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    var exportPathInput by remember { mutableStateOf(defaultBackupPath.toAbsolutePath().toString()) }
    var exportPathError by remember { mutableStateOf<String?>(null) }
    var restorePathInput by remember { mutableStateOf("") }
    var restorePathError by remember { mutableStateOf<String?>(null) }
    var uiMessage by remember { mutableStateOf<UiMessage?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Workspace)
    ) {
        ManagementPageHeader(strings.settings, strings, onBack)

        // Settings Navigation Tabs
        SettingsTabBar(
            selectedTab = SettingsTab.BACKUP_RESTORE,
            strings = strings,
            onNavigateToEstablishment = onNavigateToEstablishment,
            onNavigateToPrinters = onNavigateToPrinters,
            onNavigateToBackupRestore = {},
            onNavigateToDataManagement = onNavigateToDataManagement,
            onNavigateToLicense = onNavigateToLicense
        )

        // Main Scrollable Container
        val backupScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(backupScrollState)
                .verticalScroll(backupScrollState),
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
                            "💾 " + strings.backupRestoreTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Text(
                            strings.text(
                                "Protégez votre activité en créant des copies de secours régulières ou restaurez vos données depuis un fichier sécurisé.",
                                "Protect your business by creating regular backups or restore your database from a secure file.",
                                "احمِ بيانات نشاطك بإنشاء نسخ احتياطية بانتظام أو استرجاع بياناتك من ملف محفوظ."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )
                    }

                    // Status Notification Banner (Typed Alert)
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = { uiMessage = null }
                    )

                    // Section 1: Exporter une Sauvegarde
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "📥 " + strings.exportBackup,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.TextHigh
                                )
                                Text(
                                    strings.text(
                                        "Créez une copie complète contenant le catalogue, les ventes, les utilisateurs et les paramètres actuels (.db).",
                                        "Export a full database archive including catalog, sales, users and configuration records (.db).",
                                        "إنشاء نسخة احتياطية كاملة تحتوي على الكتالوج والمبيعات والمستخدمين والإعدادات."
                                    ),
                                    fontSize = 13.sp,
                                    color = PosColors.TextMedium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TouchTextField(
                                    value = exportPathInput,
                                    onValueChange = { exportPathInput = it; exportPathError = null; uiMessage = null },
                                    label = strings.text("Emplacement de sauvegarde", "Backup path", "مسار الحفظ"),
                                    singleLine = true,
                                    readOnly = true,
                                    isError = !exportPathError.isNullOrBlank(),
                                    errorMessage = exportPathError,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = {
                                        val defaultName = runCatching { Path.of(exportPathInput).fileName?.toString() }.getOrNull() ?: "PATISSERIE_POS_Backup.zip"
                                        val selected = NativeFileDialogs.selectBackup(
                                            title = strings.text("Emplacement de la sauvegarde", "Choose backup destination", "اختر مكان النسخة الاحتياطية"),
                                            save = true,
                                            defaultName = defaultName
                                        )
                                        if (selected != null) {
                                            exportPathInput = selected.toAbsolutePath().toString()
                                            exportPathError = null
                                            uiMessage = null
                                        }
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📂 " + strings.text("Parcourir", "Browse", "تصفح"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val path = runCatching { Path.of(exportPathInput) }.getOrNull()
                                    if (path == null) {
                                        exportPathError = strings.text("Chemin de destination invalide", "Invalid target path", "مسار غير صالح")
                                    } else {
                                        val result = onBackupRequested(path)
                                        if (result.isSuccess) {
                                            exportPathError = null
                                            uiMessage = UiMessage.success(
                                                strings.text("Sauvegarde exportée avec succès : ", "Backup exported successfully to: ", "تم تصدير النسخة الاحتياطية بنجاح: ") + path.fileName
                                            )
                                        } else {
                                            exportPathError = strings.text("Échec de la sauvegarde", "Backup failed", "فشل التصدير") + ": ${result.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("💾 " + strings.exportBackup, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Section 2: Restaurer depuis un Fichier
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.AlertLight,
                        border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "📤 " + strings.restoreBackup,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.Alert
                                )
                                Text(
                                    strings.text(
                                        "⚠️ La restauration remplacera l'ensemble de vos données actuelles par celles contenues dans le fichier sélectionné.",
                                        "⚠️ Restoring will replace all current data with the contents of the chosen backup file.",
                                        "⚠️ تنبيه: ستؤدي الاستعادة إلى استبدال جميع البيانات الحالية بالبيانات الموجودة في الملف المحدد."
                                    ),
                                    fontSize = 13.sp,
                                    color = PosColors.Alert
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TouchTextField(
                                    value = restorePathInput,
                                    onValueChange = { restorePathInput = it; restorePathError = null; uiMessage = null },
                                    placeholder = strings.text("Sélectionnez un fichier .db à restaurer...", "Select a .db backup file...", "اختر ملف قاعدة بيانات..."),
                                    singleLine = true,
                                    readOnly = true,
                                    isError = !restorePathError.isNullOrBlank(),
                                    errorMessage = restorePathError,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = {
                                        val selected = NativeFileDialogs.selectBackup(
                                            title = strings.text("Choisir le fichier de restauration", "Select backup file to restore", "اختر ملف النسخة الاحتياطية"),
                                            save = false,
                                            defaultName = "PATISSERIE_POS_Backup.zip"
                                        )
                                        if (selected != null) {
                                            restorePathInput = selected.toAbsolutePath().toString()
                                            restorePathError = null
                                            uiMessage = null
                                        }
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📂 " + strings.text("Parcourir", "Browse", "تصفح"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    if (restorePathInput.isBlank()) {
                                        restorePathError = strings.text("Veuillez d'abord sélectionner un fichier de sauvegarde", "Please select a backup file first", "يرجى اختيار ملف أولاً")
                                    } else {
                                        restorePathError = null
                                        showRestoreConfirmation = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("🔄 " + strings.restoreBackup, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Confirmation Dialog before Staging Restore
    if (showRestoreConfirmation) {
        PosConfirmDialog(
            title = strings.text("Confirmer la restauration", "Confirm Database Restore", "تأكيد استرجاع قاعدة البيانات"),
            message = strings.text(
                "Attention : la restauration remplacera toutes les données actuelles de la caisse par les données du fichier sélectionné au prochain redémarrage.",
                "Warning: Restoring will overwrite all current database records with the backup file upon next app restart.",
                "تحذير: ستؤدي الاستعادة إلى استبدال كافة بيانات الصندوق الحالية ببيانات الملف المحدد عند إعادة تشغيل التطبيق."
            ),
            warningNote = strings.text(
                "Assurez-vous d'avoir une copie de secours récente de vos ventes courantes avant de poursuivre.",
                "Ensure you have a recent backup of your current sales before proceeding.",
                "تأكد من وجود نسخة احتياطية حديثة من مبيعاتك الحالية قبل المتابعة."
            ),
            confirmLabel = strings.text("Confirmer et Programmer", "Confirm & Schedule", "تأكيد وجدولة"),
            cancelLabel = strings.cancel,
            isDestructive = true,
            onConfirm = {
                showRestoreConfirmation = false
                val sourcePath = Path.of(restorePathInput)
                val result = onRestoreRequested(sourcePath)
                if (result.isSuccess) {
                    uiMessage = UiMessage.success(
                        strings.text(
                            "Restauration programmée avec succès. Elle sera appliquée au prochain redémarrage de l'application.",
                            "Restore staged successfully. It will be applied when the app restarts.",
                            "تمت جدولة الاستعادة بنجاح وسيتم تطبيقها عند إعادة تشغيل التطبيق."
                        )
                    )
                } else {
                    uiMessage = UiMessage.error(
                        strings.text("Échec de la restauration", "Restore failed", "فشل الاسترجاع") + ": ${result.exceptionOrNull()?.message}"
                    )
                }
            },
            onDismiss = { showRestoreConfirmation = false }
        )
    }
}
