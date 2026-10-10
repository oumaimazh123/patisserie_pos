@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.management.products

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.nio.file.Files
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.FieldErrorMessage
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosDimens
import ma.elaroui.pos.desktop.presentation.components.SafeProductImage
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.touchHorizontalDragScroll
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategoryTreeNode
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules

private fun normalizeForSearch(text: String): String {
    val temp = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
}

@Composable
fun ProductManagementScreen(
    products: List<Product>,
    categories: List<Category>,
    strings: DesktopStrings,
    onSaveProduct: suspend (product: Product) -> String?,
    onToggleProductActive: (Product) -> Unit,
    onSoftDeleteProduct: (Product) -> Unit = {},
    onImportImage: () -> Result<String?>,
    onImportCsv: () -> String?,
    onBack: (() -> Unit)? = null,
    message: String = ""
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isSavingProduct by remember { mutableStateOf(false) }
    var showImportHelp by remember { mutableStateOf(false) }

    // Dialog state fields
    var nameInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var skuInput by remember { mutableStateOf("") }
    var barcodeInput by remember { mutableStateOf("") }
    var categoryIdInput by remember { mutableStateOf<Long?>(null) }
    var taxRateInput by remember { mutableStateOf(1000) } // Default 10% (1000 basis points)
    var activeInput by remember { mutableStateOf(true) }
    var imagePathInput by remember { mutableStateOf<String?>(null) }

    var productNameError by remember { mutableStateOf<String?>(null) }
    var productPriceError by remember { mutableStateOf<String?>(null) }
    var productCategoryError by remember { mutableStateOf<String?>(null) }
    var productImageError by remember { mutableStateOf<String?>(null) }

    var importSummary by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "ACTIVE", "INACTIVE"

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val normalizedQuery = remember(searchQuery) { normalizeForSearch(searchQuery) }

    val allowedCategoryIds = remember(selectedCategoryId, categories) {
        val catId = selectedCategoryId
        if (catId != null) {
            setOf(catId) + CategoryHierarchyRules.getAllDescendantIds(catId, categories)
        } else null
    }

    val filteredProducts = remember(products, allowedCategoryIds, normalizedQuery, categoryMap, selectedStatusFilter) {
        products.filter { p ->
            val matchesCategory = allowedCategoryIds == null || (p.categoryId != null && p.categoryId in allowedCategoryIds)
            if (!matchesCategory) return@filter false
            val matchesStatus = when (selectedStatusFilter) {
                "ACTIVE" -> p.active
                "INACTIVE" -> !p.active
                else -> true
            }
            if (!matchesStatus) return@filter false
            if (normalizedQuery.isBlank()) return@filter true
            val catName = p.categoryId?.let { categoryMap[it]?.name }.orEmpty()
            normalizeForSearch(p.name).contains(normalizedQuery) ||
                normalizeForSearch(catName).contains(normalizedQuery) ||
                normalizeForSearch(p.sku.orEmpty()).contains(normalizedQuery) ||
                normalizeForSearch(p.barcode.orEmpty()).contains(normalizedQuery)
        }
    }

    fun openAddDialog() {
        editingProduct = null
        nameInput = ""
        priceInput = ""
        skuInput = ""
        barcodeInput = ""
        categoryIdInput = null
        taxRateInput = 1000
        activeInput = true
        imagePathInput = null
        productNameError = null
        productPriceError = null
        productCategoryError = null
        productImageError = null
        showProductDialog = true
    }

    fun openEditDialog(product: Product) {
        editingProduct = product
        nameInput = product.name
        priceInput = MoneyRules.formatFixed(product.priceCentimes)
        skuInput = product.sku.orEmpty()
        barcodeInput = product.barcode.orEmpty()
        categoryIdInput = product.categoryId
        taxRateInput = product.taxRateBasisPoints
        activeInput = product.active
        imagePathInput = product.imagePath
        productNameError = null
        productPriceError = null
        productCategoryError = null
        productImageError = null
        showProductDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.productMgmt,
            strings = strings,
            onBackToDashboard = onBack
        ) {
            OutlinedButton(
                onClick = { importSummary = onImportCsv() },
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "📁 " + strings.text("Importer CSV", "Import CSV", "استيراد CSV"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { showImportHelp = true },
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    strings.text("Format CSV + images", "CSV format + images", "تنسيق CSV والصور"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { openAddDialog() },
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    "+ " + strings.add,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1250.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val hasActiveFilters = searchQuery.isNotBlank() || selectedCategoryId != null || selectedStatusFilter != "ALL"

                // Responsive Filter Row using BoxWithConstraints
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 820.dp

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProductSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                strings = strings,
                                modifier = Modifier.weight(1.3f)
                            )

                            ProductCategoryDropdown(
                                categories = categories,
                                products = products,
                                selectedCategoryId = selectedCategoryId,
                                onCategorySelected = { selectedCategoryId = it },
                                strings = strings,
                                modifier = Modifier.weight(1f)
                            )

                            ProductStatusDropdown(
                                selectedStatus = selectedStatusFilter,
                                onStatusSelected = { selectedStatusFilter = it },
                                strings = strings,
                                modifier = Modifier.width(170.dp)
                            )

                            if (hasActiveFilters) {
                                Surface(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategoryId = null
                                        selectedStatusFilter = "ALL"
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = PosColors.Surface,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 48.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("↺", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProductSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                strings = strings,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProductCategoryDropdown(
                                    categories = categories,
                                    products = products,
                                    selectedCategoryId = selectedCategoryId,
                                    onCategorySelected = { selectedCategoryId = it },
                                    strings = strings,
                                    modifier = Modifier.weight(1f)
                                )

                                ProductStatusDropdown(
                                    selectedStatus = selectedStatusFilter,
                                    onStatusSelected = { selectedStatusFilter = it },
                                    strings = strings,
                                    modifier = Modifier.width(160.dp)
                                )

                                if (hasActiveFilters) {
                                    Surface(
                                        onClick = {
                                            searchQuery = ""
                                            selectedCategoryId = null
                                            selectedStatusFilter = "ALL"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = PosColors.Surface,
                                        border = BorderStroke(1.dp, PosColors.Border),
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 48.dp)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("↺", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Results Count & Active Filter Indicator Row
                val selectedCategoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredProducts.size} ${if (filteredProducts.size > 1) strings.text("produits trouvés", "products found", "منتجات") else strings.text("produit trouvé", "product found", "منتج")}" +
                            if (filteredProducts.size != products.size) " ${strings.text("sur", "of", "من")} ${products.size}" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.TextMedium
                    )

                    if (selectedCategoryName != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PosColors.PrimaryLight,
                            border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📂 $selectedCategoryName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.PrimaryDark
                                )
                                Text(
                                    text = "✕",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.PrimaryDark,
                                    modifier = Modifier
                                        .clickable { selectedCategoryId = null }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                        }
                    }
                }

                if (message.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.PrimaryLight,
                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(message, color = PosColors.TextHigh, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                if (!importSummary.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.SuccessLight,
                        border = BorderStroke(1.dp, PosColors.Success.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(importSummary!!, color = PosColors.Success, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                if (filteredProducts.isEmpty()) {
                        EmptyStateCard(
                            title = if (searchQuery.isNotBlank() || selectedCategoryId != null) {
                                strings.text("Aucun produit trouvé.", "No product found.", "لم يتم العثور على أي منتج.")
                            } else {
                                strings.text("Aucun produit configuré", "No product configured", "لا توجد منتجات مضافة")
                            },
                            modifier = Modifier.weight(1f)
                        )
                } else {
                        val productListState = rememberLazyListState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                state = productListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .touchDragScroll(productListState),
                                contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredProducts, key = { it.id }) { product ->
                                    val catName = categories.firstOrNull { it.id == product.categoryId }?.name ?: "—"
                                    ProductRowCard(
                                        product = product,
                                        categoryName = catName,
                                        strings = strings,
                                        onEdit = { openEditDialog(product) },
                                        onToggleActive = { onToggleProductActive(product) },
                                        onDelete = { productToDelete = product }
                                    )
                                }
                            }

                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(productListState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 4.dp),
                                style = defaultScrollbarStyle()
                            )
                        }
                }
            }
        }
    }

    if (showImportHelp) {
        AlertDialog(
            onDismissRequest = { showImportHelp = false },
            title = { Text(strings.text("Import produits CSV", "CSV product import", "استيراد المنتجات CSV"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(strings.text(
                        "Format officiel unique à 16 colonnes. Permet d'importer simultanément la hiérarchie de catégories (jusqu'à 4 niveaux) et les produits.",
                        "Official single 16-column format. Imports category hierarchy (up to 4 levels) and products simultaneously.",
                        "تنسيق رسمي موحد من 16 عمودًا. يتيح استيراد هيكل الفئات (حتى 4 مستويات) والمنتجات معاً."
                    ))
                    Surface(
                        color = PosColors.Workspace,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PosColors.Border)
                    ) {
                        Text(
                            "Product ID,Main Category,Main Category Image,Sub-Category 1,Sub-Category 1 Image,Sub-Category 2,Sub-Category 2 Image,Sub-Category 3,Sub-Category 3 Image,Product Name (French),Product Name (Arabic),Product Image,Unit / Packaging,Price (MAD),TVA,Description",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            color = PosColors.TextHigh
                        )
                    }
                    Text(strings.text(
                        "• Hiérarchie : Jusqu'à 4 niveaux de catégories. Continuité obligatoire (aucun saut de niveau autorisé).\n• Prix unitaire : Prix fixe en MAD (ex: 18.00 ou 18,50). Les fourchettes de prix sont strictement rejetées.\n• TVA stricte : Seuls 0%, 10% et 20% sont acceptés. Tout autre taux invalide la ligne.\n• Images : Fichiers d'images facultatifs relatifs au CSV (PNG, JPG, WebP). Si absents, avertissement non-bloquant.",
                        "• Hierarchy: Up to 4 category levels. Continuity required (no gaps allowed).\n• Unit Price: Fixed price in MAD (e.g. 18.00 or 18,50). Price ranges are strictly rejected.\n• Strict VAT: Only 0%, 10%, and 20% accepted. Any other rate invalidates the row.\n• Images: Optional image paths relative to CSV (PNG, JPG, WebP).",
                        "• هيكل الفئات: حتى 4 مستويات. الاستمرارية مطلوبة.\n• السعر: سعر ثابت بالدرهم (مثل 18.00 أو 18,50). نطاقات الأسعار مرفوضة.\n• الضريبة: 0% أو 10% أو 20% فقط.\n• الصور: اختيارية."
                    ))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val saveTarget = NativeFileDialogs.saveCsv(
                                title = strings.text("Enregistrer le modèle CSV", "Save CSV Template", "حفظ نموذج CSV"),
                                defaultName = "modele_catalogue_patisserie.csv"
                            )
                            if (saveTarget != null) {
                                Files.writeString(saveTarget, CsvImports.generateOfficialTemplate(), Charsets.UTF_8)
                            }
                        }
                    ) {
                        Text(strings.text("Télécharger le modèle", "Download Template", "تنزيل النموذج"))
                    }
                    Button(onClick = { showImportHelp = false }) { Text(strings.text("Compris", "Got it", "حسناً")) }
                }
            }
        )
    }

    // Add / Edit Product Dialog Modal
    if (showProductDialog) {
        val parsedPrice = (MoneyRules.parseToCentimes(priceInput) as? MoneyParseResult.Success)?.centimes

        AlertDialog(
            onDismissRequest = { showProductDialog = false },
            title = {
                Text(
                    if (editingProduct == null) strings.text("Nouveau produit", "New Product", "منتج جديد") else strings.text("Modifier le produit", "Edit Product", "تعديل المنتج"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.TextHigh
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .touchDragScroll(dialogScrollState)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SafeProductImage(
                        imagePath = imagePathInput,
                        contentDescription = nameInput,
                        placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onImportImage()
                                    .onSuccess { newPath ->
                                        if (newPath != null) {
                                            imagePathInput = newPath
                                            productImageError = null
                                        }
                                    }
                                    .onFailure { error ->
                                        productImageError = error.message ?: strings.text("Image invalide", "Invalid image", "صورة غير صالحة")
                                    }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                if (imagePathInput != null) strings.text("Changer l'image", "Change image", "تغيير الصورة") else strings.text("Ajouter une image", "Add image", "إضافة صورة"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (imagePathInput != null) {
                            TextButton(
                                onClick = { imagePathInput = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = PosColors.Danger),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    FieldErrorMessage(productImageError)

                    Text(
                        strings.text(
                            "PNG, JPG/JPEG ou WebP. L'image est copiée dans PATISSERIE_POS.",
                            "PNG, JPG/JPEG, or WebP. The image is copied into PATISSERIE_POS.",
                            "PNG أو JPG أو WebP. يتم نسخ الصورة داخل PATISSERIE_POS."
                        ),
                        fontSize = 11.sp,
                        color = PosColors.TextMuted
                    )

                    TouchTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; productNameError = null },
                        label = strings.productName + " *",
                        placeholder = strings.text("Nom du produit", "Product name", "اسم المنتج"),
                        errorMessage = productNameError,
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchNumericField(
                        value = priceInput,
                        onValueChange = { candidate ->
                            priceInput = candidate
                            productPriceError = null
                        },
                        label = strings.priceTtc + " *",
                        placeholder = "0.00",
                        errorMessage = productPriceError,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchTextField(
                        value = skuInput,
                        onValueChange = { skuInput = it },
                        label = strings.text("SKU / Réf (facultatif)", "SKU / Ref (optional)", "رمز التخزين (اختياري)"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchTextField(
                        value = barcodeInput,
                        onValueChange = { barcodeInput = it },
                        label = strings.text("Code-barres (facultatif)", "Barcode (optional)", "الباركود (اختياري)"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            strings.category + " *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextHigh
                        )
                        DialogCategoryDropdownSelector(
                            categories = categories,
                            selectedCategoryId = categoryIdInput,
                            onCategorySelected = {
                                categoryIdInput = it
                                productCategoryError = null
                            },
                            strings = strings,
                            errorMessage = productCategoryError
                        )
                        FieldErrorMessage(productCategoryError)
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            strings.taxRate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextHigh
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "0%", 1000 to "10%", 2000 to "20%").forEach { (bps, label) ->
                                val isSelected = taxRateInput == bps
                                Surface(
                                    onClick = { taxRateInput = bps },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                    border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                        Text(
                                            label,
                                            color = if (isSelected) Color.White else PosColors.TextHigh,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeInput = !activeInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Switch(
                            checked = activeInput,
                            onCheckedChange = { activeInput = it }
                        )
                        Column {
                            Text(
                                if (activeInput) strings.text("Produit actif", "Active product", "منتج نشط")
                                else strings.text("Produit inactif", "Inactive product", "منتج غير نشط"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = PosColors.TextHigh
                            )
                            Text(
                                if (activeInput) strings.text("Visible et disponible à la vente", "Visible and available for sale", "مرئي ومتاح للبيع")
                                else strings.text("Masqué de la caisse de vente", "Hidden from POS register", "مخفي من شاشة البيع"),
                                fontSize = 11.sp,
                                color = PosColors.TextMuted
                            )
                        }
                    }

                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        productNameError = null
                        productPriceError = null
                        productCategoryError = null
                        productImageError = null

                        val cid = categoryIdInput
                        var hasError = false
                        if (nameInput.isBlank()) {
                            productNameError = strings.required
                            hasError = true
                        }
                        if (parsedPrice == null || parsedPrice <= 0) {
                            productPriceError = strings.invalidAmount
                            hasError = true
                        }
                        if (hasError) return@Button

                        val p = Product(
                            id = editingProduct?.id ?: 0L,
                            categoryId = cid,
                            name = nameInput.trim(),
                            priceCentimes = parsedPrice!!,
                            taxRateBasisPoints = taxRateInput,
                            available = activeInput,
                            active = activeInput,
                            imagePath = imagePathInput?.trim()?.ifBlank { null },
                            sku = skuInput.trim().ifBlank { null },
                            barcode = barcodeInput.trim().ifBlank { null }
                        )
                        if (isSavingProduct) return@Button
                        isSavingProduct = true
                        scope.launch {
                            try {
                                val saveError = onSaveProduct(p)
                                if (saveError == null) showProductDialog = false
                                else productNameError = saveError
                            } finally { isSavingProduct = false }
                        }
                    },
                    enabled = !isSavingProduct,
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showProductDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete Confirmation Modal Dialog
    productToDelete?.let { prod ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = {
                Text(
                    strings.text("Supprimer le produit", "Delete Product", "حذف المنتج"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.TextHigh
                )
            },
            text = {
                Text(
                    strings.text(
                        "Êtes-vous sûr de vouloir supprimer « ${prod.name} » ?\n\nLe produit ne sera plus disponible pour les nouvelles ventes. L'historique existant sera conservé intact.",
                        "Are you sure you want to delete '${prod.name}'?\n\nThe product will no longer be available for new sales. Existing history will be preserved.",
                        "هل أنت متأكد من حذف '${prod.name}'؟\n\nلن يعود المنتج متاحاً للمبيعات الجديدة، وسيبقى السجل السابق محفوظاً."
                    ),
                    fontSize = 14.sp,
                    color = PosColors.TextMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSoftDeleteProduct(prod)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { productToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun ProductSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    TouchTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = strings.text("Rechercher un produit (nom, réf, code-barres)…", "Search product (name, sku, barcode)…", "البحث عن منتج…"),
        leadingIcon = {
            Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Text("✕", color = PosColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else null,
        singleLine = true,
        maxLines = 1,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = PosColors.Primary,
            unfocusedBorderColor = PosColors.Border,
            cursorColor = PosColors.Primary
        ),
        modifier = modifier.heightIn(min = 48.dp)
    )
}

data class CategoryTreeDisplayItem(
    val id: Long?,
    val name: String,
    val level: Int,
    val productCount: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

@Composable
private fun ProductCategoryDropdown(
    categories: List<Category>,
    products: List<Product>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var menuSearchQuery by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

    var expandedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

    val selectedCategory = remember(selectedCategoryId, categories) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }

    val productCountByCatId = remember(products, categories) {
        val counts = mutableMapOf<Long, Int>()
        categories.forEach { cat ->
            val descendantIds = setOf(cat.id) + CategoryHierarchyRules.getAllDescendantIds(cat.id, categories)
            counts[cat.id] = products.count { it.categoryId in descendantIds }
        }
        counts
    }

    val categoryTree = remember(categories) { CategoryHierarchyRules.buildCategoryTree(categories) }

    // Ancestor IDs to auto-expand matching items or selected item
    val searchMatchingAncestorIds = remember(categories, menuSearchQuery) {
        val q = menuSearchQuery.trim()
        if (q.isBlank()) {
            emptySet<Long>()
        } else {
            val matchingCategories = categories.filter { it.name.contains(q, ignoreCase = true) }
            val categoryMap = categories.associateBy { it.id }
            val ancestors = mutableSetOf<Long>()
            for (matching in matchingCategories) {
                var curr = matching.parentId?.let { categoryMap[it] }
                while (curr != null) {
                    ancestors.add(curr.id)
                    curr = curr.parentId?.let { categoryMap[it] }
                }
            }
            ancestors
        }
    }

    val selectedAncestorIds = remember(selectedCategoryId, categories) {
        val categoryMap = categories.associateBy { it.id }
        val ancestors = mutableSetOf<Long>()
        var curr = selectedCategoryId?.let { categoryMap[it]?.parentId }?.let { categoryMap[it] }
        while (curr != null) {
            ancestors.add(curr.id)
            curr = curr.parentId?.let { categoryMap[it] }
        }
        ancestors
    }

    val effectiveExpandedIds = remember(expandedCategoryIds, searchMatchingAncestorIds, selectedAncestorIds, menuSearchQuery) {
        if (menuSearchQuery.isNotBlank()) {
            expandedCategoryIds + searchMatchingAncestorIds
        } else {
            expandedCategoryIds + selectedAncestorIds
        }
    }

    fun toggleCategoryExpand(catId: Long) {
        expandedCategoryIds = if (catId in expandedCategoryIds) {
            val descendants = CategoryHierarchyRules.getAllDescendantIds(catId, categories)
            expandedCategoryIds - catId - descendants
        } else {
            expandedCategoryIds + catId
        }
    }

    val visibleTreeItems = remember(categoryTree, productCountByCatId, effectiveExpandedIds, menuSearchQuery, searchMatchingAncestorIds) {
        val result = mutableListOf<CategoryTreeDisplayItem>()
        val query = menuSearchQuery.trim()

        fun traverse(nodes: List<CategoryTreeNode>) {
            nodes.forEach { node ->
                val matchesDirectly = query.isBlank() || node.category.name.contains(query, ignoreCase = true)
                val isAncestorOfMatch = searchMatchingAncestorIds.contains(node.category.id)
                val isVisible = matchesDirectly || isAncestorOfMatch

                if (isVisible) {
                    val isExpanded = effectiveExpandedIds.contains(node.category.id)
                    val hasChildren = node.children.isNotEmpty()
                    result.add(
                        CategoryTreeDisplayItem(
                            id = node.category.id,
                            name = node.category.name,
                            level = node.level,
                            productCount = productCountByCatId[node.category.id] ?: 0,
                            hasChildren = hasChildren,
                            isExpanded = isExpanded
                        )
                    )
                    if (hasChildren && (isExpanded || isAncestorOfMatch)) {
                        traverse(node.children)
                    }
                }
            }
        }
        traverse(categoryTree)
        result
    }

    val isFilterActive = selectedCategoryId != null

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            color = if (isFilterActive) PosColors.PrimaryLight.copy(alpha = 0.5f) else Color.White,
            border = BorderStroke(1.dp, if (isFilterActive) PosColors.Primary else PosColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("📂", fontSize = 14.sp)
                    Text(
                        text = selectedCategory?.name ?: strings.text("Toutes les catégories", "All categories", "جميع الفئات"),
                        fontSize = 13.sp,
                        fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFilterActive) PosColors.PrimaryDark else PosColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFilterActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PosColors.Primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${productCountByCatId[selectedCategoryId] ?: 0}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.PrimaryDark,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFilterActive) {
                        IconButton(
                            onClick = { onCategorySelected(null) },
                            modifier = Modifier.size(24.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PosColors.PrimaryDark)
                        }
                    }
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = if (isFilterActive) PosColors.Primary else PosColors.TextMedium
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                menuSearchQuery = ""
            },
            modifier = Modifier
                .background(Color.White)
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 440.dp)
        ) {
            // Search Input with BasicTextField - perfectly centered, never clipped!
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔍", fontSize = 13.sp)
                        BasicTextField(
                            value = menuSearchQuery,
                            onValueChange = { menuSearchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = PosColors.TextHigh,
                                fontWeight = FontWeight.Normal
                            ),
                            decorationBox = { innerTextField ->
                                if (menuSearchQuery.isEmpty()) {
                                    Text(
                                        strings.text("Rechercher catégorie…", "Search category…", "بحث عن فئة…"),
                                        fontSize = 12.sp,
                                        color = PosColors.TextLow
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (menuSearchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { menuSearchQuery = "" },
                                modifier = Modifier.size(22.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("✕", fontSize = 11.sp, color = PosColors.TextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = PosColors.Border.copy(alpha = 0.5f))

            // "Toutes les catégories" Header Item
            if (menuSearchQuery.isBlank()) {
                val isAllSelected = selectedCategoryId == null
                val allInteraction = remember { MutableInteractionSource() }
                val isAllHovered by allInteraction.collectIsHoveredAsState()

                Surface(
                    onClick = {
                        onCategorySelected(null)
                        expanded = false
                        menuSearchQuery = ""
                    },
                    interactionSource = allInteraction,
                    color = when {
                        isAllSelected -> PosColors.PrimaryLight.copy(alpha = 0.5f)
                        isAllHovered -> PosColors.Workspace
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = if (isAllSelected) BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .height(38.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🏷️", fontSize = 13.sp)
                            Text(
                                text = strings.text("Toutes les catégories", "All categories", "جميع الفئات"),
                                fontSize = 13.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isAllSelected) PosColors.PrimaryDark else PosColors.TextHigh
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isAllSelected) {
                                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAllSelected) PosColors.Primary.copy(alpha = 0.2f) else PosColors.Workspace
                            ) {
                                Text(
                                    text = "${products.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllSelected) PosColors.PrimaryDark else PosColors.TextMuted,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = PosColors.Border.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 3.dp))
            }

            // Categories Hierarchy Tree
            if (visibleTreeItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.text("Aucune catégorie trouvée", "No category found", "لم يتم العثور على أي فئة"),
                        fontSize = 12.sp,
                        color = PosColors.TextMuted
                    )
                }
            } else {
                visibleTreeItems.forEach { item ->
                    val isSelected = item.id == selectedCategoryId
                    val itemInteraction = remember { MutableInteractionSource() }
                    val isHovered by itemInteraction.collectIsHoveredAsState()

                    val startPadding = when (item.level) {
                        1 -> 8.dp
                        2 -> 24.dp
                        else -> 42.dp
                    }

                    Surface(
                        onClick = {
                            onCategorySelected(item.id)
                            expanded = false
                            menuSearchQuery = ""
                        },
                        interactionSource = itemInteraction,
                        color = when {
                            isSelected -> PosColors.PrimaryLight.copy(alpha = 0.5f)
                            isHovered -> PosColors.Workspace
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.35f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .height(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startPadding, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                // Expand / Collapse chevron if has children
                                if (item.hasChildren) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                item.id?.let { toggleCategoryExpand(it) }
                                            }
                                            .pointerHoverIcon(PointerIcon.Hand),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (item.isExpanded) "▼" else "▶",
                                            fontSize = 9.sp,
                                            color = if (isSelected) PosColors.Primary else PosColors.TextMedium
                                        )
                                    }
                                } else {
                                    if (item.level > 1) {
                                        Text(
                                            text = "↳",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PosColors.Primary else PosColors.BorderVariant
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }
                                }

                                val icon = when (item.level) {
                                    1 -> "📁"
                                    2 -> "📂"
                                    else -> "🏷️"
                                }
                                Text(icon, fontSize = 12.sp)

                                Text(
                                    text = item.name,
                                    fontSize = if (item.level == 1) 13.sp else 12.sp,
                                    fontWeight = when {
                                        isSelected -> FontWeight.Bold
                                        item.level == 1 -> FontWeight.Bold
                                        item.level == 2 -> FontWeight.SemiBold
                                        else -> FontWeight.Normal
                                    },
                                    color = when {
                                        isSelected -> PosColors.PrimaryDark
                                        item.level == 1 -> PosColors.TextHigh
                                        else -> PosColors.TextMedium
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PosColors.Primary.copy(alpha = 0.2f) else PosColors.Workspace
                                ) {
                                    Text(
                                        text = "${item.productCount}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) PosColors.PrimaryDark else PosColors.TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
private fun ProductStatusDropdown(
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isFilterActive = selectedStatus != "ALL"

    val label = when (selectedStatus) {
        "ACTIVE" -> strings.text("Actifs", "Active", "نشط")
        "INACTIVE" -> strings.text("Inactifs", "Inactive", "غير نشط")
        else -> strings.text("Tous les statuts", "All statuses", "جميع الحالات")
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = if (isFilterActive) PosColors.PrimaryLight.copy(alpha = 0.5f) else Color.White,
            border = BorderStroke(1.dp, if (isFilterActive) PosColors.Primary else PosColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFilterActive) PosColors.PrimaryDark else PosColors.TextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 10.sp,
                    color = if (isFilterActive) PosColors.Primary else PosColors.TextMedium
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White).widthIn(min = 160.dp)
        ) {
            listOf(
                "ALL" to strings.text("Tous les statuts", "All statuses", "جميع الحالات"),
                "ACTIVE" to strings.text("Actifs uniquement", "Active only", "النشطة فقط"),
                "INACTIVE" to strings.text("Inactifs uniquement", "Inactive only", "غير النشطة فقط")
            ).forEach { (code, text) ->
                val isSelected = code == selectedStatus
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PosColors.Primary else PosColors.TextHigh
                        )
                    },
                    onClick = {
                        onStatusSelected(code)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isSelected) PosColors.PrimaryLight.copy(alpha = 0.4f) else Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
private fun DialogCategoryDropdownSelector(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    strings: DesktopStrings,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var menuSearchQuery by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

    val activeCategories = remember(categories, selectedCategoryId) {
        categories.filter { it.active || it.id == selectedCategoryId }
    }

    var expandedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

    // Breadcrumb path for selected category
    val selectedBreadcrumb = remember(selectedCategoryId, categories) {
        if (selectedCategoryId != null) {
            CategoryHierarchyRules.getBreadcrumbPath(selectedCategoryId, categories)
        } else {
            emptyList()
        }
    }

    val selectedDisplayName = remember(selectedBreadcrumb, selectedCategoryId, categories) {
        if (selectedBreadcrumb.isNotEmpty()) {
            selectedBreadcrumb.joinToString(" › ") { it.name }
        } else {
            categories.firstOrNull { it.id == selectedCategoryId }?.name
        }
    }

    val categoryTree = remember(activeCategories) { CategoryHierarchyRules.buildCategoryTree(activeCategories) }

    // Ancestor IDs of search matches or selected category
    val searchMatchingAncestorIds = remember(activeCategories, menuSearchQuery) {
        val q = menuSearchQuery.trim()
        if (q.isBlank()) {
            emptySet<Long>()
        } else {
            val matchingCategories = activeCategories.filter { it.name.contains(q, ignoreCase = true) }
            val categoryMap = activeCategories.associateBy { it.id }
            val ancestors = mutableSetOf<Long>()
            for (matching in matchingCategories) {
                var curr = matching.parentId?.let { categoryMap[it] }
                while (curr != null) {
                    ancestors.add(curr.id)
                    curr = curr.parentId?.let { categoryMap[it] }
                }
            }
            ancestors
        }
    }

    val selectedAncestorIds = remember(selectedCategoryId, activeCategories) {
        val categoryMap = activeCategories.associateBy { it.id }
        val ancestors = mutableSetOf<Long>()
        var curr = selectedCategoryId?.let { categoryMap[it]?.parentId }?.let { categoryMap[it] }
        while (curr != null) {
            ancestors.add(curr.id)
            curr = curr.parentId?.let { categoryMap[it] }
        }
        ancestors
    }

    val effectiveExpandedIds = remember(expandedCategoryIds, searchMatchingAncestorIds, selectedAncestorIds, menuSearchQuery) {
        if (menuSearchQuery.isNotBlank()) {
            expandedCategoryIds + searchMatchingAncestorIds
        } else {
            expandedCategoryIds + selectedAncestorIds
        }
    }

    fun toggleCategoryExpand(catId: Long) {
        expandedCategoryIds = if (catId in expandedCategoryIds) {
            val descendants = CategoryHierarchyRules.getAllDescendantIds(catId, activeCategories)
            expandedCategoryIds - catId - descendants
        } else {
            expandedCategoryIds + catId
        }
    }

    val visibleTreeItems = remember(categoryTree, effectiveExpandedIds, menuSearchQuery, searchMatchingAncestorIds) {
        val result = mutableListOf<CategoryTreeDisplayItem>()
        val query = menuSearchQuery.trim()

        fun traverse(nodes: List<CategoryTreeNode>) {
            nodes.forEach { node ->
                val matchesDirectly = query.isBlank() || node.category.name.contains(query, ignoreCase = true)
                val isAncestorOfMatch = searchMatchingAncestorIds.contains(node.category.id)
                val isVisible = matchesDirectly || isAncestorOfMatch

                if (isVisible) {
                    val isExpanded = effectiveExpandedIds.contains(node.category.id)
                    val hasChildren = node.children.isNotEmpty()
                    result.add(
                        CategoryTreeDisplayItem(
                            id = node.category.id,
                            name = node.category.name,
                            level = node.level,
                            productCount = 0,
                            hasChildren = hasChildren,
                            isExpanded = isExpanded
                        )
                    )
                    if (hasChildren && (isExpanded || isAncestorOfMatch)) {
                        traverse(node.children)
                    }
                }
            }
        }
        traverse(categoryTree)
        result
    }

    val borderColor = when {
        errorMessage != null -> PosColors.Danger
        expanded -> PosColors.Primary
        else -> PosColors.Border
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("📁", fontSize = 15.sp)
                    Text(
                        text = selectedDisplayName ?: strings.text("Sans catégorie", "No category", "بدون فئة"),
                        fontSize = 13.sp,
                        fontWeight = if (selectedDisplayName != null) FontWeight.SemiBold else FontWeight.Medium,
                        color = PosColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 11.sp,
                    color = if (expanded) PosColors.Primary else PosColors.TextMedium
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                menuSearchQuery = ""
            },
            modifier = Modifier
                .background(Color.White)
                .widthIn(min = 360.dp, max = 500.dp)
                .heightIn(max = 420.dp)
        ) {
            // Search Input
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔍", fontSize = 13.sp)
                        BasicTextField(
                            value = menuSearchQuery,
                            onValueChange = { menuSearchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = PosColors.TextHigh,
                                fontWeight = FontWeight.Normal
                            ),
                            decorationBox = { innerTextField ->
                                if (menuSearchQuery.isEmpty()) {
                                    Text(
                                        strings.text("Rechercher catégorie…", "Search category…", "بحث عن فئة…"),
                                        fontSize = 12.sp,
                                        color = PosColors.TextLow
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (menuSearchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { menuSearchQuery = "" },
                                modifier = Modifier.size(22.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("✕", fontSize = 11.sp, color = PosColors.TextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = PosColors.Border.copy(alpha = 0.5f))

            // "Toutes les catégories" Option
            if (menuSearchQuery.isBlank()) {
                val isAllSelected = selectedCategoryId == null
                val allInteraction = remember { MutableInteractionSource() }
                val isAllHovered by allInteraction.collectIsHoveredAsState()

                Surface(
                    onClick = {
                        onCategorySelected(null)
                        expanded = false
                        menuSearchQuery = ""
                    },
                    interactionSource = allInteraction,
                    color = when {
                        isAllSelected -> PosColors.PrimaryLight.copy(alpha = 0.5f)
                        isAllHovered -> PosColors.Workspace
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = if (isAllSelected) BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .height(38.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🏷️", fontSize = 13.sp)
                            Text(
                                text = strings.text("Sans catégorie (Aucune)", "No category (None)", "بدون فئة (لا يوجد)"),
                                fontSize = 13.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isAllSelected) PosColors.PrimaryDark else PosColors.TextHigh
                            )
                        }
                        if (isAllSelected) {
                            Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                        }
                    }
                }

                HorizontalDivider(color = PosColors.Border.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 3.dp))
            }

            if (visibleTreeItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.text("Aucune catégorie trouvée", "No category found", "لم يتم العثور على أي فئة"),
                        fontSize = 12.sp,
                        color = PosColors.TextMuted
                    )
                }
            } else {
                visibleTreeItems.forEach { item ->
                    val isSelected = item.id == selectedCategoryId
                    val itemInteraction = remember { MutableInteractionSource() }
                    val isHovered by itemInteraction.collectIsHoveredAsState()

                    val startPadding = when (item.level) {
                        1 -> 8.dp
                        2 -> 24.dp
                        else -> 42.dp
                    }

                    Surface(
                        onClick = {
                            item.id?.let { onCategorySelected(it) }
                            expanded = false
                            menuSearchQuery = ""
                        },
                        interactionSource = itemInteraction,
                        color = when {
                            isSelected -> PosColors.PrimaryLight.copy(alpha = 0.5f)
                            isHovered -> PosColors.Workspace
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.35f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .height(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startPadding, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (item.hasChildren) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                item.id?.let { toggleCategoryExpand(it) }
                                            }
                                            .pointerHoverIcon(PointerIcon.Hand),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (item.isExpanded) "▼" else "▶",
                                            fontSize = 9.sp,
                                            color = if (isSelected) PosColors.Primary else PosColors.TextMedium
                                        )
                                    }
                                } else {
                                    if (item.level > 1) {
                                        Text(
                                            text = "↳",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PosColors.Primary else PosColors.BorderVariant
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }
                                }

                                val icon = when (item.level) {
                                    1 -> "📁"
                                    2 -> "📂"
                                    else -> "🏷️"
                                }
                                Text(icon, fontSize = 12.sp)

                                Text(
                                    text = item.name,
                                    fontSize = if (item.level == 1) 13.sp else 12.sp,
                                    fontWeight = when {
                                        isSelected -> FontWeight.Bold
                                        item.level == 1 -> FontWeight.Bold
                                        item.level == 2 -> FontWeight.SemiBold
                                        else -> FontWeight.Normal
                                    },
                                    color = when {
                                        isSelected -> PosColors.PrimaryDark
                                        item.level == 1 -> PosColors.TextHigh
                                        else -> PosColors.TextMedium
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSelected) {
                                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProductRowCard(
    product: Product,
    categoryName: String,
    strings: DesktopStrings,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBorder by animateColorAsState(
        if (isHovered) PosColors.Primary else PosColors.Border
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHovered) PosColors.Workspace else PosColors.Surface),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 3.dp else 1.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            val isCompact = maxWidth < 600.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SafeProductImage(
                            imagePath = product.imagePath,
                            contentDescription = product.name,
                            placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                product.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace
                                ) {
                                    Text(
                                        "📁 $categoryName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PosColors.TextMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace
                                ) {
                                    Text(
                                        "TVA ${(product.taxRateBasisPoints / 100)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PosColors.TextMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (!product.sku.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PosColors.PrimaryLight,
                                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            "🏷️ ${product.sku}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PosColors.PrimaryDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (!product.barcode.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PosColors.Workspace,
                                        border = BorderStroke(1.dp, PosColors.Border)
                                    ) {
                                        Text(
                                            "📶 ${product.barcode}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PosColors.TextMedium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (product.active) PosColors.SuccessLight else PosColors.DangerLight,
                                    border = BorderStroke(1.dp, if (product.active) PosColors.Success.copy(alpha = 0.4f) else PosColors.Danger.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        if (product.active) "● " + strings.active else "○ " + strings.inactive,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (product.active) PosColors.Success else PosColors.Danger,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${MoneyRules.formatFixed(product.priceCentimes)} ${strings.currency}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.Primary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onEdit,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.edit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (product.active) {
                                OutlinedButton(
                                    onClick = onToggleActive,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.deactivate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = onToggleActive,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.activate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = onDelete,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.4f)),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("🗑️ " + strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    ) {
                        SafeProductImage(
                            imagePath = product.imagePath,
                            contentDescription = product.name,
                            placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                product.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace
                                ) {
                                    Text(
                                        "📁 $categoryName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PosColors.TextMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace
                                ) {
                                    Text(
                                        "TVA ${(product.taxRateBasisPoints / 100)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PosColors.TextMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (!product.sku.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PosColors.PrimaryLight,
                                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            "🏷️ ${product.sku}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PosColors.PrimaryDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (!product.barcode.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PosColors.Workspace,
                                        border = BorderStroke(1.dp, PosColors.Border)
                                    ) {
                                        Text(
                                            "📶 ${product.barcode}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PosColors.TextMedium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (product.active) PosColors.SuccessLight else PosColors.DangerLight,
                                    border = BorderStroke(1.dp, if (product.active) PosColors.Success.copy(alpha = 0.4f) else PosColors.Danger.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        if (product.active) "● " + strings.active else "○ " + strings.inactive,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (product.active) PosColors.Success else PosColors.Danger,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "${MoneyRules.formatFixed(product.priceCentimes)} ${strings.currency}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.Primary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onEdit,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.edit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (product.active) {
                                OutlinedButton(
                                    onClick = onToggleActive,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.deactivate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = onToggleActive,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.activate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = onDelete,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.4f)),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("🗑️ " + strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
