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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.DatabaseMetrics
import ma.elaroui.pos.desktop.persistence.DataGroupSelection
import ma.elaroui.pos.desktop.persistence.DeletionSummary
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.desktop.presentation.components.PosColors

private enum class DeletionTargetType {
    PRODUCTS,
    CATEGORIES,
    CATALOGUE,
    SALES_HISTORY,
    SUSPENDED_SALES,
    TABLES_AREAS,
    CASHIERS,
    SELECTIVE,
    BUSINESS_DATA,
    FACTORY_RESET
}

@Composable
fun DataManagementScreen(
    db: WindowsPosDatabase,
    strings: DesktopStrings,
    onDataResetOrDeleted: () -> Unit,
    onFactoryResetComplete: () -> Unit,
    onNavigateToEstablishment: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToLicense: () -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var uiMessage by remember { mutableStateOf<UiMessage?>(null) }

    // Real-time database metrics
    var metrics: DatabaseMetrics by remember { mutableStateOf(db.getDatabaseMetrics()) }
    fun refreshMetrics() {
        coroutineScope.launch {
            metrics = withContext(Dispatchers.IO) { db.getDatabaseMetrics() }
        }
    }

    // Selective deletion checkboxes
    var selProducts by remember { mutableStateOf(false) }
    var selCategories by remember { mutableStateOf(false) }
    var selSales by remember { mutableStateOf(false) }
    var selSuspended by remember { mutableStateOf(false) }
    var selCashiers by remember { mutableStateOf(false) }

    // Dialog state
    var activeDeletionTarget by remember { mutableStateOf<DeletionTargetType?>(null) }
    var deletionPasswordInput by remember { mutableStateOf("") }
    var factoryConfirmationInput by remember { mutableStateOf("") }
    var factoryConfirmationError by remember { mutableStateOf<String?>(null) }
    var deletionPasswordError by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun openProtectedTarget(target: DeletionTargetType) {
        factoryConfirmationError = null
        deletionPasswordError = null
        deletionPasswordInput = ""
        if (target == DeletionTargetType.FACTORY_RESET) factoryConfirmationInput = ""
        activeDeletionTarget = target
    }

    LaunchedEffect(Unit) {
        refreshMetrics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Workspace)
    ) {
        ManagementPageHeader(strings.settings, strings, onBack)

        SettingsTabBar(
            selectedTab = SettingsTab.DATA_MANAGEMENT,
            strings = strings,
            onNavigateToEstablishment = onNavigateToEstablishment,
            onNavigateToPrinters = onNavigateToPrinters,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToDataManagement = {},
            onNavigateToLicense = onNavigateToLicense
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(scrollState)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Top inline notification
                PosInlineAlert(
                    message = uiMessage,
                    onDismiss = { uiMessage = null }
                )

                // 1. Live Data Overview Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📊", fontSize = 18.sp)
                                Text(
                                    strings.text("Données actuelles en caisse", "Current Register Data", "البيانات المسجلة حالياً"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.TextHigh
                                )
                            }

                            TextButton(
                                onClick = { refreshMetrics() },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("🔄 " + strings.text("Actualiser les chiffres", "Refresh stats", "تحديث الأرقام"), fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                label = strings.text("Produits", "Products", "المنتجات"),
                                count = metrics.productsCount,
                                icon = "📦",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.text("Catégories", "Categories", "الفئات"),
                                count = metrics.categoriesCount,
                                icon = "📁",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.text("Ventes clôturées", "Completed Sales", "المبيعات المكتملة"),
                                count = metrics.completedSalesCount,
                                icon = "🧾",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.text("En attente", "Pending", "المعلقة"),
                                count = metrics.pendingSalesCount,
                                icon = "⏳",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.text("Caissiers", "Cashiers", "الصرافين"),
                                count = metrics.cashiersCount,
                                icon = "👥",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Custom Selective Deletion Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("🗑️", fontSize = 18.sp)
                                    Text(
                                        strings.text("Suppression sélective personnalisée", "Custom Selective Deletion", "حذف انتقائي مخصص"),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.TextHigh
                                    )
                                }
                                Text(
                                    strings.text(
                                        "Sélectionnez précisément les données à supprimer en une seule opération sécurisée :",
                                        "Select precisely which data to delete in a single secure operation:",
                                        "حدد بدقة البيانات المراد حذفها في عملية واحدة آمنة:"
                                    ),
                                    fontSize = 13.sp,
                                    color = PosColors.TextMedium
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        selProducts = true
                                        selCategories = true
                                        selSales = true
                                        selSuspended = true
                                        selCashiers = true
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.text("Tout cocher", "Select all", "تحديد الكل"), fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = {
                                        selProducts = false
                                        selCategories = false
                                        selSales = false
                                        selSuspended = false
                                        selCashiers = false
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.text("Tout décocher", "Deselect all", "إلغاء تحديد الكل"), fontSize = 12.sp)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModuleSelectionCard(
                                icon = "📦",
                                title = strings.text("Produits du catalogue", "Catalogue Products", "منتجات الكتالوج"),
                                countText = "${metrics.productsCount} " + strings.text("articles", "items", "عنصر"),
                                description = strings.text(
                                    "Supprime l'ensemble des articles du catalogue de vente.",
                                    "Deletes all articles from the sales catalogue.",
                                    "حذف جميع منتجات كتالوج المبيعات."
                                ),
                                checked = selProducts,
                                onCheckedChange = { selProducts = it }
                            )

                            ModuleSelectionCard(
                                icon = "📁",
                                title = strings.text("Catégories & Sous-catégories", "Categories & Sub-categories", "الفئات والفئات الفرعية"),
                                countText = "${metrics.categoriesCount} " + strings.text("catégories", "categories", "فئة"),
                                description = strings.text(
                                    "Supprime l'arborescence des catégories et les produits associés.",
                                    "Deletes categories hierarchy and associated products.",
                                    "حذف هيكل الفئات والمنتجات المرتبطة بها."
                                ),
                                checked = selCategories,
                                onCheckedChange = { selCategories = it }
                            )

                            ModuleSelectionCard(
                                icon = "🧾",
                                title = strings.text("Historique des ventes & paiements", "Sales History & Payments", "سجل المبيعات والمدفوعات"),
                                countText = "${metrics.completedSalesCount} " + strings.text("ventes", "sales", "مبيعة"),
                                description = strings.text(
                                    "Supprime définitivement toutes les ventes clôturées et annulées ainsi que leurs règlements.",
                                    "Permanently deletes completed and cancelled orders and all payments.",
                                    "حذف المبيعات المكتملة والملغاة ومدفوعاتها وسجل الحركات نهائياً."
                                ),
                                checked = selSales,
                                onCheckedChange = { selSales = it }
                            )

                            ModuleSelectionCard(
                                icon = "⏳",
                                title = strings.text("Ventes en attente / Commandes ouvertes", "Pending Sales / Open Orders", "المبيعات المعلقة / الطلبات المفتوحة"),
                                countText = "${metrics.pendingSalesCount} " + strings.text("en cours", "pending", "معلقة"),
                                description = strings.text(
                                    "Supprime toutes les commandes actuellement ouvertes et libère automatiquement les tables associées.",
                                    "Deletes open orders and releases occupied tables.",
                                    "حذف جميع الطلبات المفتوحة حالياً وتحرير الطاولات المرتبطة بها تلقائياً."
                                ),
                                checked = selSuspended,
                                onCheckedChange = { selSuspended = it }
                            )

                            ModuleSelectionCard(
                                icon = "👥",
                                title = strings.text("Comptes Caissiers", "Cashier Accounts", "حسابات الصرافين"),
                                countText = "${metrics.cashiersCount} " + strings.text("comptes", "accounts", "حساب"),
                                description = strings.text(
                                    "Supprime tous les profils de caissiers secondaires (le compte Propriétaire est toujours conservé).",
                                    "Deletes all secondary cashier accounts (Owner account is always preserved).",
                                    "حذف حسابات الصرافين الثانوية (يتم دائماً الاحتفاظ بحساب المالك)."
                                ),
                                checked = selCashiers,
                                onCheckedChange = { selCashiers = it }
                            )
                        }

                        val selectedCount = listOf(selProducts, selCategories, selSales, selSuspended, selCashiers).count { it }
                        val hasAnySelection = selectedCount > 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (hasAnySelection) {
                                        openProtectedTarget(DeletionTargetType.SELECTIVE)
                                    }
                                },
                                enabled = hasAnySelection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PosColors.Danger,
                                    disabledContainerColor = PosColors.Border
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .pointerHoverIcon(if (hasAnySelection) PointerIcon.Hand else PointerIcon.Default)
                            ) {
                                Text(
                                    "🗑️ " + strings.text(
                                        if (selectedCount > 1) "Supprimer les $selectedCount modules sélectionnés"
                                        else if (selectedCount == 1) "Supprimer le module sélectionné"
                                        else "Supprimer les données sélectionnées",
                                        if (selectedCount > 1) "Delete $selectedCount selected modules"
                                        else if (selectedCount == 1) "Delete selected module"
                                        else "Delete selected data",
                                        "حذف البيانات المحددة"
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (hasAnySelection) Color.White else PosColors.TextLow
                                )
                            }
                        }
                    }
                }

                // 3. Quick Targeted Deletion Actions Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚡", fontSize = 18.sp)
                            Text(
                                strings.text("Actions rapides de suppression ciblée", "Quick Targeted Deletion Actions", "إجراءات الحذف السريع الموجه"),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TargetedDeletionRow(
                                icon = "🧾",
                                title = strings.text("Supprimer les ventes", "Delete Sales", "حذف المبيعات"),
                                countBadge = "${metrics.completedSalesCount} " + strings.text("ventes", "sales", "مبيعة"),
                                description = strings.text(
                                    "Supprime définitivement toutes les ventes clôturées et annulées ainsi que leurs règlements.",
                                    "Permanently deletes all completed and cancelled orders and their payment records.",
                                    "حذف جميع المبيعات المكتملة والملغاة ومدفوعاتها نهائياً."
                                ),
                                buttonText = strings.text("Supprimer les ventes", "Delete Sales", "حذف المبيعات"),
                                onAction = { openProtectedTarget(DeletionTargetType.SALES_HISTORY) }
                            )

                            HorizontalDivider(color = PosColors.Border)

                            TargetedDeletionRow(
                                icon = "⏳",
                                title = strings.text("Supprimer les ventes en attente", "Delete Pending Sales", "حذف المبيعات المعلقة"),
                                countBadge = "${metrics.pendingSalesCount} " + strings.text("en attente", "pending", "معلقة"),
                                description = strings.text(
                                    "Supprime toutes les commandes actuellement ouvertes et libère automatiquement les tables associées.",
                                    "Deletes all currently open orders and resets occupied tables to Available status.",
                                    "حذف جميع الطلبات المفتوحة حالياً وتحرير الطاولات المشغولة تلقائياً."
                                ),
                                buttonText = strings.text("Supprimer les ventes en attente", "Delete Pending Sales", "حذف المبيعات المعلقة"),
                                onAction = { openProtectedTarget(DeletionTargetType.SUSPENDED_SALES) }
                            )

                            HorizontalDivider(color = PosColors.Border)

                            TargetedDeletionRow(
                                icon = "📦",
                                title = strings.text("Supprimer tout le catalogue", "Delete Entire Catalogue", "حذف الكتالوج بالكامل"),
                                countBadge = "${metrics.productsCount} " + strings.text("prod.", "prod.", "منتج") + " / ${metrics.categoriesCount} " + strings.text("cat.", "cat.", "فئة"),
                                description = strings.text(
                                    "Supprime l'intégralité des produits et des catégories du magasin.",
                                    "Deletes all products and categories from the store.",
                                    "حذف جميع المنتجات والفئات من المتجر."
                                ),
                                buttonText = strings.text("Supprimer le catalogue", "Delete Catalogue", "حذف الكتالوج"),
                                onAction = { openProtectedTarget(DeletionTargetType.CATALOGUE) }
                            )

                            HorizontalDivider(color = PosColors.Border)

                            TargetedDeletionRow(
                                icon = "👥",
                                title = strings.text("Supprimer les comptes caissiers", "Delete Cashier Accounts", "حذف حسابات الصرافين"),
                                countBadge = "${metrics.cashiersCount} " + strings.text("comptes", "accounts", "حساب"),
                                description = strings.text(
                                    "Supprime tous les profils de caissiers (le compte Propriétaire reste actif et inchangé).",
                                    "Deletes all cashier profiles (Owner account remains active).",
                                    "حذف جميع حسابات الصرافين مع بقاء حساب المالك نشطاً."
                                ),
                                buttonText = strings.text("Supprimer les caissiers", "Delete Cashiers", "حذف الصرافين"),
                                onAction = { openProtectedTarget(DeletionTargetType.CASHIERS) }
                            )
                        }
                    }
                }


                // 5. Danger Zone Card (Full Reset)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PosColors.DangerLight),
                    border = BorderStroke(1.5.dp, PosColors.Danger.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🚨", fontSize = 20.sp)
                            Text(
                                strings.text("Zone critique / Réinitialisation", "Danger Zone / Reset", "منطقة الخطر / إعادة الضبط"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.Danger
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        strings.text("Réinitialisation d'usine complète", "Complete Factory Reset", "إعادة ضبط المصنع بالكامل"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PosColors.Danger
                                    )
                                    Text(
                                        strings.text(
                                            "Remet complètement l'application à zéro (retour à l'assistant de démarrage). Conserve uniquement la clé de licence.",
                                            "Completely resets the application (back to initial setup wizard). Preserves license key only.",
                                            "إعادة ضبط التطبيق بالكامل إلى حالة التثبيت الأولى (العودة إلى معالج الإعداد). مع الحفاظ على مفتاح الترخيص فقط."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextMedium
                                    )
                                }
                                Button(
                                    onClick = { openProtectedTarget(DeletionTargetType.FACTORY_RESET) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.text("Réinitialisation d'usine", "Factory Reset", "إعادة ضبط المصنع"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

    // Confirmation & Password Modal Dialog
    if (activeDeletionTarget != null) {
        val target = activeDeletionTarget!!
        val isFactory = target == DeletionTargetType.FACTORY_RESET

        var passwordVisible by remember { mutableStateOf(false) }

        val title = when (target) {
            DeletionTargetType.PRODUCTS -> strings.text("Supprimer tous les produits ?", "Delete all products?", "حذف جميع المنتجات؟")
            DeletionTargetType.CATEGORIES -> strings.text("Supprimer toutes les catégories ?", "Delete all categories?", "حذف جميع الفئات؟")
            DeletionTargetType.CATALOGUE -> strings.text("Supprimer tout le catalogue ?", "Delete entire catalog?", "حذف كامل الكتالوج؟")
            DeletionTargetType.SALES_HISTORY -> strings.text("Supprimer les ventes ?", "Delete sales?", "حذف المبيعات؟")
            DeletionTargetType.SUSPENDED_SALES -> strings.text("Supprimer les ventes en attente ?", "Delete pending sales?", "حذف المبيعات المعلقة؟")
            DeletionTargetType.TABLES_AREAS -> strings.text("Supprimer les tables et salles ?", "Delete tables and areas?", "حذف الطاولات والقاعات؟")
            DeletionTargetType.CASHIERS -> strings.text("Supprimer les caissiers ?", "Delete cashiers?", "حذف الصرافين؟")
            DeletionTargetType.SELECTIVE -> strings.text("Supprimer les données sélectionnées ?", "Delete selected data?", "حذف البيانات المحددة؟")
            DeletionTargetType.BUSINESS_DATA -> strings.text("Supprimer toutes les données commerciales ?", "Delete all business data?", "حذف جميع البيانات التجارية؟")
            DeletionTargetType.FACTORY_RESET -> strings.text("Réinitialisation d'usine complète ?", "Complete factory reset?", "إعادة ضبط المصنع بالكامل؟")
        }

        val description = when (target) {
            DeletionTargetType.PRODUCTS -> strings.text(
                "Cette action supprimera tous les produits du catalogue. Les anciens tickets et historiques de ventes restent conservés.",
                "This will delete all products from the catalog. Past receipts and sales history are preserved.",
                "سيؤدي هذا الإجراء إلى حذف جميع منتجات الكتالوج مع الاحتفاظ بالإيصالات السابقة وسجل المبيعات."
            )
            DeletionTargetType.CATEGORIES -> strings.text(
                "Cette action supprimera toutes les catégories et leurs produits associés. Les anciens tickets restent intacts.",
                "This will delete all categories and their associated products. Past receipts remain intact.",
                "سيؤدي هذا الإجراء إلى حذف جميع الفئات والمنتجات المرتبطة بها مع الاحتفاظ بالإيصالات السابقة."
            )
            DeletionTargetType.CATALOGUE -> strings.text(
                "Cette action supprimera l'intégralité du catalogue (toutes les catégories et tous les produits). L'historique des ventes reste préservé.",
                "This will delete the entire catalog (all categories and products). Sales history remains intact.",
                "سيؤدي هذا الإجراء إلى حذف الكتالوج بالكامل (جميع الفئات والمنتجات) مع بقاء سجل المبيعات سليماً."
            )
            DeletionTargetType.SALES_HISTORY -> strings.text(
                "Cette action supprimera l'intégralité des tickets et reçus des ventes passées. Cette opération est irréversible.",
                "This will permanently delete all past sales and receipts history. This action is irreversible.",
                "سيؤدي هذا الإجراء إلى حذف سجل المبيعات والإيصالات السابقة بالكامل. هذا الإجراء نهائي."
            )
            DeletionTargetType.SUSPENDED_SALES -> strings.text(
                "Cette action supprimera toutes les commandes ouvertes et libérera les tables occupées.",
                "This will delete all currently open/suspended orders and release occupied tables.",
                "سيؤدي هذا الإجراء إلى حذف جميع الطلبات المعلقة وتحرير الطاولات."
            )
            DeletionTargetType.TABLES_AREAS -> strings.text(
                "Cette action supprimera l'ensemble des tables et des zones de restauration.",
                "This will delete all tables and dining areas.",
                "سيؤدي هذا الإجراء إلى حذف جميع الطاولات وقاعات المطعم."
            )
            DeletionTargetType.CASHIERS -> strings.text(
                "Cette action supprimera et désactivera tous les profils de caissiers. Le compte Propriétaire et l'attribution des anciennes ventes seront conservés.",
                "This deletes and disables all cashier accounts. The Owner account and past sales attribution are preserved.",
                "سيؤدي هذا إلى حذف وتعطيل جميع حسابات الصرافين مع الحفاظ على حساب المالك ونسبة المبيعات القديمة."
            )
            DeletionTargetType.SELECTIVE -> strings.text(
                "Cette action supprimera définitivement les éléments sélectionnés.",
                "This will permanently delete the selected items.",
                "سيؤدي هذا الإجراء إلى حذف العناصر المحددة نهائياً."
            )
            DeletionTargetType.BUSINESS_DATA -> strings.text(
                "ATTENTION : Cette action supprimera tous les produits, catégories, commandes, ventes, tables et caissiers. Les paramètres de l'établissement et la licence seront conservés.",
                "WARNING: This will delete all products, categories, orders, sales, tables and cashiers. Store settings and license are preserved.",
                "تحذير: سيؤدي هذا إلى حذف جميع المنتجات والفئات والطلبات والمبيعات والطاولات والصرافين. مع الاحتفاظ بإعدادات المؤسسة والترخيص."
            )
            DeletionTargetType.FACTORY_RESET -> strings.text(
                "DANGER EXTRÊME : Cette action efface totalement la base de données locale. L'application redémarrera à l'étape de configuration initiale (Setup Wizard). Seule la clé de licence est conservée.",
                "EXTREME DANGER: This wipes the local database completely. The app will return to the initial setup wizard. Only the license key is kept.",
                "خطر كبير: سيؤدي هذا الإجراء إلى مسح قاعدة البيانات المحلية بالكامل والعودة إلى معالج التثبيت الأولي. يتم الاحتفاظ بمفتاح الترخيص فقط."
            )
        }

        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    activeDeletionTarget = null
                    factoryConfirmationError = null
                    deletionPasswordError = null
                    deletionPasswordInput = ""
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isFactory) "🚨" else "⚠️", fontSize = 22.sp)
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PosColors.Danger)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(description, fontSize = 13.sp, color = PosColors.TextHigh, lineHeight = 19.sp)

                    if (isFactory) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PosColors.DangerLight,
                            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    strings.text(
                                        "Pour confirmer la réinitialisation complète, tapez SUPPRIMER ci-dessous :",
                                        "To confirm complete factory reset, type SUPPRIMER below:",
                                        "لتأكيد إعادة ضبط المصنع، اكتب SUPPRIMER أدناه:"
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.Danger
                                )
                                TouchTextField(
                                    value = factoryConfirmationInput,
                                    onValueChange = { factoryConfirmationInput = it; factoryConfirmationError = null },
                                    label = strings.text("Tapez SUPPRIMER", "Type SUPPRIMER", "اكتب SUPPRIMER"),
                                    errorMessage = factoryConfirmationError,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            strings.text(
                                "Entrez le code PIN du propriétaire :",
                                "Enter Owner PIN:",
                                "أدخل رمز PIN للمالك:"
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextHigh
                        )
                        TouchTextField(
                            value = deletionPasswordInput,
                            onValueChange = { deletionPasswordInput = it; deletionPasswordError = null },
                            label = strings.text("Code PIN (4 à 6 chiffres)", "PIN Code (4-6 digits)", "رمز PIN (4 إلى 6 أرقام)"),
                            errorMessage = deletionPasswordError,
                            isPassword = !passwordVisible,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { passwordVisible = !passwordVisible },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (passwordVisible) strings.text("Masquer le code PIN", "Hide PIN", "إخفاء رمز PIN")
                                else strings.text("Afficher le code PIN", "Show PIN", "إظهار رمز PIN"),
                                fontSize = 12.sp,
                                color = PosColors.Primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        factoryConfirmationError = null
                        deletionPasswordError = null

                        if (isFactory && factoryConfirmationInput.trim() != "SUPPRIMER") {
                            factoryConfirmationError = strings.text(
                                "Veuillez saisir exactement 'SUPPRIMER' pour confirmer.",
                                "Please type exactly 'SUPPRIMER' to confirm.",
                                "يرجى كتابة 'SUPPRIMER' تماماً للتأكيد."
                            )
                            return@Button
                        }

                        val cleanInput = deletionPasswordInput.trim()
                        if (cleanInput.isEmpty()) {
                            deletionPasswordError = strings.text(
                                "Veuillez saisir votre code PIN.",
                                "Please enter your PIN code.",
                                "يرجى إدخال رمز PIN."
                            )
                            return@Button
                        }

                        coroutineScope.launch {
                            val verification = runProtectedAction(
                                onProcessingChanged = { isProcessing = it },
                                verifyCredential = {
                                    withContext(Dispatchers.IO) {
                                        db.verifyAdminDeletionPassword(deletionPasswordInput)
                                    }
                                },
                                action = { Unit }
                            )
                            when (verification) {
                                ProtectedActionResult.InvalidCredential -> {
                                    deletionPasswordError = strings.text(
                                        "Code PIN incorrect.",
                                        "Incorrect PIN code.",
                                        "رمز PIN غير صحيح."
                                    )
                                    return@launch
                                }
                                is ProtectedActionResult.Failure -> {
                                    deletionPasswordError = strings.text(
                                        "Impossible de vérifier le code PIN. Veuillez réessayer.",
                                        "Unable to verify PIN. Please try again.",
                                        "تعذر التحقق من رمز PIN. يرجى المحاولة مرة أخرى."
                                    )
                                    return@launch
                                }
                                is ProtectedActionResult.Success -> Unit
                            }

                            isProcessing = true
                            try {
                                when (target) {
                                    DeletionTargetType.PRODUCTS -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteProducts() }
                                        uiMessage = UiMessage.success(strings.text("$count produits supprimés avec succès.", "$count products deleted.", "تم حذف $count منتجات بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.CATEGORIES -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteCategories() }
                                        uiMessage = UiMessage.success(strings.text("$count catégories et produits associés supprimés.", "$count categories deleted.", "تم حذف $count فئات بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.CATALOGUE -> {
                                        val (cats, prods) = withContext(Dispatchers.IO) { db.deleteCatalogue() }
                                        uiMessage = UiMessage.success(strings.text("Catalogue supprimé ($cats catégories, $prods produits).", "Catalog deleted ($cats categories, $prods products).", "تم حذف الكتالوج ($cats فئات، $prods منتجات)."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.SALES_HISTORY -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteSalesHistory() }
                                        uiMessage = UiMessage.success(strings.text("$count ventes supprimées de l'historique.", "$count sales deleted.", "تم حذف $count مبيعات بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.SUSPENDED_SALES -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteSuspendedSales() }
                                        uiMessage = UiMessage.success(strings.text("$count commandes en attente supprimées.", "$count suspended orders deleted.", "تم حذف $count طلبات معلقة بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.TABLES_AREAS -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteTablesAndAreas() }
                                        uiMessage = UiMessage.success(strings.text("$count tables et salles supprimées.", "$count tables and areas deleted.", "تم حذف $count طاولات وقاعات بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.CASHIERS -> {
                                        val count = withContext(Dispatchers.IO) { db.deleteCashiers() }
                                        uiMessage = UiMessage.success(strings.text("$count caissiers supprimés (Propriétaire conservé).", "$count cashiers deleted.", "تم حذف $count صرافين بنجاح."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.SELECTIVE -> {
                                        val selection = DataGroupSelection(
                                            products = selProducts,
                                            categories = selCategories,
                                            salesHistory = selSales,
                                            suspendedSales = selSuspended,
                                            tablesAndAreas = false,
                                            cashiers = selCashiers
                                        )
                                        val summary = withContext(Dispatchers.IO) { db.deleteSelective(selection) }
                                        uiMessage = UiMessage.success(strings.text("Suppression terminée (${summary.totalRecordsDeleted} éléments supprimés).", "Deletion complete (${summary.totalRecordsDeleted} records deleted).", "اكتمل الحذف (${summary.totalRecordsDeleted} عناصر)."))
                                        selProducts = false
                                        selCategories = false
                                        selSales = false
                                        selSuspended = false
                                        selCashiers = false
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.BUSINESS_DATA -> {
                                        val summary = withContext(Dispatchers.IO) { db.deleteBusinessData() }
                                        uiMessage = UiMessage.success(strings.text("Toutes les données commerciales ont été supprimées (${summary.totalRecordsDeleted} éléments).", "All business data deleted (${summary.totalRecordsDeleted} items).", "تم حذف جميع البيانات التجارية."))
                                        refreshMetrics()
                                        onDataResetOrDeleted()
                                    }
                                    DeletionTargetType.FACTORY_RESET -> {
                                        withContext(Dispatchers.IO) { db.factoryResetData() }
                                        onFactoryResetComplete()
                                    }
                                }
                                activeDeletionTarget = null
                            } catch (e: Throwable) {
                                deletionPasswordError = strings.text(
                                    "Une erreur est survenue lors de l'opération.",
                                    "An error occurred during the operation.",
                                    "حدث خطأ أثناء العملية."
                                )
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessing,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(
                        if (isProcessing) {
                            if (isFactory) strings.text("Réinitialisation en cours...", "Resetting...", "جاري إعادة الضبط...")
                            else strings.text("Suppression en cours...", "Deleting...", "جاري الحذف...")
                        } else {
                            if (isFactory) strings.text("Confirmer la réinitialisation", "Confirm Reset", "تأكيد إعادة الضبط")
                            else strings.text("Confirmer la suppression", "Confirm Deletion", "تأكيد الحذف")
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        activeDeletionTarget = null
                        factoryConfirmationError = null
                        deletionPasswordError = null
                        deletionPasswordInput = ""
                        factoryConfirmationInput = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessing,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
}


@Composable
private fun MetricTile(
    label: String,
    count: Int,
    icon: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PosColors.Workspace,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(icon, fontSize = 16.sp)
                Text(
                    text = count.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (count > 0) PosColors.TextHigh else PosColors.TextMedium
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PosColors.TextMedium
            )
        }
    }
}

@Composable
private fun ModuleSelectionCard(
    title: String,
    countText: String = "",
    description: String,
    icon: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (checked) PosColors.DangerLight.copy(alpha = 0.5f) else PosColors.Workspace,
        border = BorderStroke(1.dp, if (checked) PosColors.Danger.copy(alpha = 0.5f) else PosColors.Border),
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = PosColors.Danger,
                        checkmarkColor = Color.White
                    )
                )
                Text(icon, fontSize = 20.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (checked) PosColors.Danger else PosColors.TextHigh
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) PosColors.Danger.copy(alpha = 0.15f) else PosColors.Border.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = countText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (checked) PosColors.Danger else PosColors.TextMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = PosColors.TextMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetedDeletionRow(
    icon: String,
    title: String,
    countBadge: String? = null,
    description: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 20.sp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PosColors.TextHigh)
                    if (countBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Border.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = countBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = PosColors.TextMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(description, fontSize = 12.sp, color = PosColors.TextMedium)
            }
        }

        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

