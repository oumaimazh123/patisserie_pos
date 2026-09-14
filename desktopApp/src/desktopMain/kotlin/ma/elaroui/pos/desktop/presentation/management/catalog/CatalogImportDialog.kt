package ma.elaroui.pos.desktop.presentation.management.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.nio.file.Files
import java.nio.file.Path
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.importing.CatalogImportAnalysis
import ma.elaroui.pos.desktop.importing.CatalogPreviewCategoryNode
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.desktop.presentation.components.PosColors

@Composable
fun CatalogImportDialog(
    strings: DesktopStrings,
    initialAnalysis: CatalogImportAnalysis?,
    sourceFile: Path?,
    onPickNewFile: () -> Unit,
    onConfirmImport: (CatalogImportAnalysis, Path) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Tree, 1: Issues

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 720.dp, max = 920.dp)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = PosColors.Canvas,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, PosColors.Border)
        ) {
            if (initialAnalysis == null || sourceFile == null) {
                // Initial state: prompt to select CSV
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "📁",
                        fontSize = 48.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        strings.text("Importer le catalogue CSV", "Import Catalog CSV", "استيراد كتالوج CSV"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.text(
                            "Sélectionnez un fichier CSV au format officiel (16 colonnes, jusqu'à 4 niveaux de catégories).",
                            "Select a CSV file in the official format (16 columns, up to 4 category levels).",
                            "حدد ملف CSV بالتنسيق الرسمي (16 عمودًا، حتى 4 مستويات من الفئات)."
                        ),
                        fontSize = 14.sp,
                        color = PosColors.TextMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                val saveTarget = NativeFileDialogs.saveCsv(
                                    title = strings.text("Enregistrer le modèle CSV", "Save CSV Template", "حفظ نموذج CSV"),
                                    defaultName = "modele_catalogue_patisserie.csv"
                                )
                                if (saveTarget != null) {
                                    Files.writeString(saveTarget, CsvImports.generateOfficialTemplate(), Charsets.UTF_8)
                                }
                            },
                            modifier = Modifier.height(48.dp).pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.text("📥 Télécharger le modèle CSV", "📥 Download CSV Template", "📥 تنزيل نموذج CSV"))
                        }

                        Button(
                            onClick = onPickNewFile,
                            modifier = Modifier.height(48.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.text("Sélectionner un fichier CSV", "Select CSV File", "اختيار ملف CSV"))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text(strings.text("Fermer", "Close", "إغلاق"), color = PosColors.TextMedium)
                    }
                }
            } else {
                // Analysis preview state
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                strings.text("Aperçu de l'import Catalogue", "Catalog Import Preview", "معاينة استيراد الكتالوج"),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                            Text(
                                "${sourceFile.fileName} · ${initialAnalysis.totalRows} " +
                                    strings.text("ligne(s) détectée(s)", "row(s) detected", "سطر مكتشف"),
                                fontSize = 13.sp,
                                color = PosColors.TextMedium
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val saveTarget = NativeFileDialogs.saveCsv(
                                    title = strings.text("Enregistrer le modèle CSV", "Save CSV Template", "حفظ نموذج CSV"),
                                    defaultName = "modele_catalogue_patisserie.csv"
                                )
                                if (saveTarget != null) {
                                    Files.writeString(saveTarget, CsvImports.generateOfficialTemplate(), Charsets.UTF_8)
                                }
                            },
                            modifier = Modifier.height(40.dp).pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                strings.text("Modèle CSV", "CSV Template", "نموذج CSV"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // KPI Summary Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryKpiCard(
                            modifier = Modifier.weight(1f),
                            icon = "🥐",
                            title = strings.text("Produits valides", "Valid Products", "منتجات صالحة"),
                            value = "${initialAnalysis.productsToImportCount}",
                            subtitle = if (initialAnalysis.duplicateProductIdCount > 0)
                                "${initialAnalysis.duplicateProductIdCount} " + strings.text("existant(s)/doublon(s)", "existing/duplicate", "موجود/مكرر")
                            else strings.text("Prêts à importer", "Ready to import", "جاهز للاستيراد"),
                            highlight = initialAnalysis.productsToImportCount > 0
                        )

                        SummaryKpiCard(
                            modifier = Modifier.weight(1.2f),
                            icon = "🏷️",
                            title = strings.text("Catégories", "Categories", "فئات"),
                            value = "${initialAnalysis.newCategoriesCount} " + strings.text("nouvelles", "new", "جديدة"),
                            subtitle = "N1: ${initialAnalysis.categoriesCountByLevel[1] ?: 0} · N2: ${initialAnalysis.categoriesCountByLevel[2] ?: 0} · N3: ${initialAnalysis.categoriesCountByLevel[3] ?: 0} · N4: ${initialAnalysis.categoriesCountByLevel[4] ?: 0}",
                            highlight = true
                        )

                        SummaryKpiCard(
                            modifier = Modifier.weight(1.2f),
                            icon = "📊",
                            title = strings.text("Taux TVA", "VAT Rates", "نسب الضريبة"),
                            value = "0%: ${initialAnalysis.taxBreakdown[0] ?: 0} · 10%: ${initialAnalysis.taxBreakdown[1000] ?: 0} · 20%: ${initialAnalysis.taxBreakdown[2000] ?: 0}",
                            subtitle = strings.text("Répartition stricte", "Strict breakdown", "توزيع دقيق"),
                            highlight = false
                        )

                        SummaryKpiCard(
                            modifier = Modifier.weight(1f),
                            icon = "🖼️",
                            title = strings.text("Images", "Images", "صور"),
                            value = "${initialAnalysis.imagesFoundCount} " + strings.text("trouvées", "found", "موجودة"),
                            subtitle = if (initialAnalysis.imagesMissingCount > 0)
                                "${initialAnalysis.imagesMissingCount} " + strings.text("absente(s)", "missing", "مفقودة")
                            else strings.text("Toutes présentes", "All present", "الكل موجود"),
                            highlight = initialAnalysis.imagesMissingCount == 0
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Validation Status Alert Banner
                    if (initialAnalysis.errors.isNotEmpty()) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⛔", fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${initialAnalysis.errors.size} " + strings.text(
                                        "erreur(s) bloquante(s) trouvée(s). L'importation ne peut pas continuer tant que ces erreurs ne sont pas corrigées.",
                                        "blocking error(s) found. Import cannot proceed until fixed.",
                                        "خطأ يمنع الاستيراد. يرجى تصحيح الأخطاء أولاً."
                                    ),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    } else if (initialAnalysis.warnings.isNotEmpty()) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${initialAnalysis.warnings.size} " + strings.text(
                                        "avertissement(s) non bloquant(s) (ex: images introuvables). Les produits seront importés sans ces images.",
                                        "non-blocking warning(s) (e.g. missing images). Products will import without them.",
                                        "تنبيهات غير معطلة (مثل صور غير موجودة)."
                                    ),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF22C55E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅", fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    strings.text(
                                        "Fichier 100% conforme et prêt pour l'intégration.",
                                        "File 100% valid and ready for import.",
                                        "الملف صالح تمامًا وجاهز للاستيراد."
                                    ),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF166534)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Tab bar: Arborescence vs Erreurs / Avertissements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            label = {
                                Text(
                                    strings.text("Arborescence du catalogue", "Category Hierarchy", "شجرة الفئات") +
                                        " (${initialAnalysis.categoryTree.size})"
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PosColors.Primary,
                                selectedLabelColor = Color.White
                            )
                        )

                        val totalIssues = initialAnalysis.errors.size + initialAnalysis.warnings.size
                        FilterChip(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            label = {
                                Text(
                                    strings.text("Erreurs & Avertissements", "Errors & Warnings", "الأخطاء والتنبيهات") +
                                        if (totalIssues > 0) " ($totalIssues)" else ""
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (initialAnalysis.errors.isNotEmpty()) Color(0xFFDC2626) else PosColors.Secondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tab Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(PosColors.Workspace, RoundedCornerShape(12.dp))
                            .border(1.dp, PosColors.Border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (activeTab == 0) {
                            // Tree View
                            if (initialAnalysis.categoryTree.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        strings.text("Aucune catégorie valide trouvée.", "No valid categories found.", "لم يتم العثور على فئات صالحة."),
                                        color = PosColors.TextMedium
                                    )
                                }
                            } else {
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    initialAnalysis.categoryTree.forEach { rootNode ->
                                        CategoryTreeNodeView(rootNode, depth = 0, strings = strings)
                                    }
                                }
                            }
                        } else {
                            // Errors & Warnings List
                            val totalIssues = initialAnalysis.errors.size + initialAnalysis.warnings.size
                            if (totalIssues == 0) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "✨ " + strings.text("Aucune erreur ni avertissement !", "No errors or warnings!", "لا توجد أخطاء أو تنبيهات!"),
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(initialAnalysis.errors) { err ->
                                        IssueRow(
                                            isError = true,
                                            line = err.line,
                                            column = err.column,
                                            message = err.message
                                        )
                                    }
                                    items(initialAnalysis.warnings) { warn ->
                                        IssueRow(
                                            isError = false,
                                            line = warn.line,
                                            column = warn.column,
                                            message = warn.message
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onPickNewFile,
                            modifier = Modifier.height(48.dp).pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.text("Choisir un autre fichier", "Choose Another File", "اختيار ملف آخر"))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(48.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.text("Annuler", "Cancel", "إلغاء"), color = PosColors.TextMedium)
                            }

                            Button(
                                onClick = { onConfirmImport(initialAnalysis, sourceFile) },
                                enabled = initialAnalysis.canImport,
                                modifier = Modifier.height(48.dp).pointerHoverIcon(PointerIcon.Hand),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PosColors.Primary,
                                    disabledContainerColor = PosColors.Border
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    strings.text(
                                        "Confirmer l'import (${initialAnalysis.productsToImportCount} produits)",
                                        "Confirm Import (${initialAnalysis.productsToImportCount} products)",
                                        "تأكيد الاستيراد (${initialAnalysis.productsToImportCount} منتجات)"
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryKpiCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    subtitle: String,
    highlight: Boolean
) {
    Surface(
        modifier = modifier,
        color = PosColors.Workspace,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (highlight) PosColors.Primary.copy(alpha = 0.4f) else PosColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(icon, fontSize = 16.sp)
                Text(
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosColors.TextMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 10.sp,
                color = PosColors.TextMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryTreeNodeView(
    node: CatalogPreviewCategoryNode,
    depth: Int,
    strings: DesktopStrings
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 24).dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level Badge
            val (levelColor, levelBg) = when (node.level) {
                1 -> PosColors.Primary to PosColors.Primary.copy(alpha = 0.15f)
                2 -> PosColors.Secondary to PosColors.Secondary.copy(alpha = 0.15f)
                3 -> Color(0xFF6B7280) to Color(0xFFF3F4F6)
                else -> Color(0xFF8B5CF6) to Color(0xFFEDE9FE)
            }
            Surface(
                color = levelBg,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "N${node.level}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                node.name,
                fontWeight = if (node.level == 1) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = PosColors.TextHigh
            )

            Spacer(Modifier.width(8.dp))

            if (node.isNew) {
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        strings.text("+ Nouveau", "+ New", "+ جديد"),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            if (node.productCount > 0) {
                Surface(
                    color = PosColors.Canvas,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, PosColors.Border)
                ) {
                    Text(
                        "${node.productCount} " + strings.text("produit(s)", "product(s)", "منتج"),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        color = PosColors.TextMedium
                    )
                }
            }

            if (node.imagePath != null) {
                Spacer(Modifier.width(6.dp))
                Text("📷", fontSize = 11.sp)
            }
        }

        // Render children
        node.children.forEach { child ->
            CategoryTreeNodeView(child, depth = depth + 1, strings = strings)
        }
    }
}

@Composable
private fun IssueRow(
    isError: Boolean,
    line: Int,
    column: String,
    message: String
) {
    val bg = if (isError) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
    val border = if (isError) Color(0xFFFCA5A5) else Color(0xFFFDE68A)
    val tagColor = if (isError) Color(0xFFDC2626) else Color(0xFFD97706)

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isError) "❌" else "⚠️", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Surface(
                color = tagColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "Ligne $line · $column",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                fontSize = 12.sp,
                color = PosColors.TextHigh
            )
        }
    }
}
