package ma.elaroui.pos.desktop.presentation.management.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import kotlinx.coroutines.launch
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.SafeProductImage
import ma.elaroui.pos.desktop.presentation.components.FieldErrorMessage
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import java.nio.file.Files
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategoryTreeNode

@Composable
fun CategoryManagementScreen(
    categories: List<Category>,
    products: List<Product> = emptyList(),
    strings: DesktopStrings,
    onSaveCategoryWithSubcategory: (suspend (category: Category, subcategory: Category) -> String?)? = null,
    onSaveCategory: (suspend (category: Category) -> String?)? = null,
    onToggleCategoryActive: (Category) -> Unit,
    onSoftDeleteCategory: (Category) -> Unit = {},
    onImportImage: (() -> Result<String?>)? = null,
    onImportCsv: () -> String?,
    onBack: (() -> Unit)? = null,
    message: String = ""
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    var isSavingCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var preselectedParentId by remember { mutableStateOf<Long?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    // Dialog form inputs
    var categoryNameInput by remember { mutableStateOf("") }
    var categoryImageInput by remember { mutableStateOf<String?>(null) }
    var categoryParentIdInput by remember { mutableStateOf<Long?>(null) }
    var categoryDisplayOrderInput by remember { mutableStateOf("0") }
    var categoryActiveInput by remember { mutableStateOf(true) }

    var categoryNameError by remember { mutableStateOf<String?>(null) }
    var categoryImageError by remember { mutableStateOf<String?>(null) }
    var categoryHierarchyError by remember { mutableStateOf<String?>(null) }

    var importSummary by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Expanded category IDs set (starts empty: only Level 1 categories visible at page load)
    var expandedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

    fun toggleCategory(categoryId: Long) {
        expandedCategoryIds = if (categoryId in expandedCategoryIds) {
            // When closing a category, close it and all its descendants
            val descendants = CategoryHierarchyRules.getAllDescendantIds(categoryId, categories)
            expandedCategoryIds - categoryId - descendants
        } else {
            expandedCategoryIds + categoryId
        }
    }

    // When searching, auto-expand parent/ancestor nodes so matching items are visible in hierarchy
    val searchMatchingAncestorIds = remember(categories, searchQuery) {
        val q = searchQuery.trim()
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

    val effectiveExpandedIds = remember(expandedCategoryIds, searchMatchingAncestorIds, searchQuery) {
        if (searchQuery.isNotBlank()) {
            expandedCategoryIds + searchMatchingAncestorIds
        } else {
            expandedCategoryIds
        }
    }

    // Build the 3-level tree hierarchy
    val tree = remember(categories) {
        CategoryHierarchyRules.buildCategoryTree(categories)
    }

    // Flatten tree respecting expansion & search query
    fun flattenTree(nodes: List<CategoryTreeNode>, query: String, expandedIds: Set<Long>): List<CategoryTreeNode> {
        val result = mutableListOf<CategoryTreeNode>()
        for (node in nodes) {
            val matchesQuery = query.isBlank() || node.category.name.contains(query, ignoreCase = true)
            val hasMatchingDescendant = query.isNotBlank() && run {
                val descendants = CategoryHierarchyRules.getAllDescendantIds(node.category.id, categories)
                categories.any { it.id in descendants && it.name.contains(query, ignoreCase = true) }
            }
            if (matchesQuery || hasMatchingDescendant) {
                result.add(node)
                val isExpanded = node.category.id in expandedIds
                if (isExpanded && node.children.isNotEmpty()) {
                    result.addAll(flattenTree(node.children, query, expandedIds))
                }
            }
        }
        return result
    }

    val visibleNodes = remember(tree, searchQuery, effectiveExpandedIds) {
        flattenTree(tree, searchQuery.trim(), effectiveExpandedIds)
    }

    fun openAddCategory(parentId: Long? = null) {
        editingCategory = null
        preselectedParentId = parentId
        categoryParentIdInput = parentId
        categoryNameInput = ""
        categoryImageInput = null
        val sameParentCats = categories.filter { it.parentId == parentId }
        val nextOrder = (sameParentCats.maxOfOrNull { it.displayOrder } ?: -1) + 1
        categoryDisplayOrderInput = nextOrder.toString()
        categoryActiveInput = true
        categoryNameError = null
        categoryImageError = null
        categoryHierarchyError = null
        showCategoryDialog = true
    }

    fun openEditCategory(cat: Category) {
        editingCategory = cat
        preselectedParentId = null
        categoryParentIdInput = cat.parentId
        categoryNameInput = cat.name
        categoryImageInput = cat.imagePath
        categoryDisplayOrderInput = cat.displayOrder.toString()
        categoryActiveInput = cat.active
        categoryNameError = null
        categoryImageError = null
        categoryHierarchyError = null
        showCategoryDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.categoryMgmt,
            strings = strings,
            onBackToDashboard = onBack
        ) {
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
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "📥 " + strings.text("Modèle CSV", "CSV Template", "نموذج CSV"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

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

            Button(
                onClick = { openAddCategory(null) },
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "+ " + strings.text("Ajouter une catégorie", "Add Category", "إضافة فئة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TouchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = strings.search,
                    placeholder = strings.text("Rechercher une catégorie (Niveau 1, 2 ou 3)...", "Search category (Level 1, 2 or 3)...", "بحث عن فئة..."),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

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

                if (visibleNodes.isEmpty()) {
                    EmptyStateCard(
                        title = if (searchQuery.isNotBlank()) strings.noResults else strings.text("Aucune catégorie configurée", "No category configured", "لا توجد فئات مضافة"),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .touchDragScroll(listState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleNodes, key = { it.category.id }) { node ->
                            val descendants = remember(node.category.id, categories) {
                                CategoryHierarchyRules.getAllDescendantIds(node.category.id, categories)
                            }
                            val productCount = remember(node.category.id, descendants, products) {
                                products.count { it.categoryId == node.category.id || it.categoryId in descendants }
                            }
                            val isExpanded = node.category.id in effectiveExpandedIds

                            CategoryTreeNodeRowCard(
                                node = node,
                                productCount = productCount,
                                isExpanded = isExpanded,
                                onToggleExpand = { toggleCategory(node.category.id) },
                                strings = strings,
                                onAddChild = if (node.level < 3) {
                                    { openAddCategory(parentId = node.category.id) }
                                } else null,
                                onEdit = { openEditCategory(node.category) },
                                onToggleActive = { onToggleCategoryActive(node.category) },
                                onDelete = { categoryToDelete = node.category }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Add / Edit Category Dialog
    if (showCategoryDialog) {
        val isEditing = editingCategory != null

        // Calculate dynamic level based on currently selected parent
        val calculatedLevel = remember(categoryParentIdInput, categories) {
            val pid = categoryParentIdInput
            if (pid == null) 1 else CategoryHierarchyRules.calculateLevel(pid, categories) + 1
        }

        // Available parent candidates (cannot be self, cannot be descendant of self, cannot be level 3)
        val validParentOptions = remember(categories, editingCategory) {
            val disallowedIds = mutableSetOf<Long>()
            editingCategory?.let { editCat ->
                disallowedIds.add(editCat.id)
                disallowedIds.addAll(CategoryHierarchyRules.getAllDescendantIds(editCat.id, categories))
            }
            categories.filter { cat ->
                cat.id !in disallowedIds && CategoryHierarchyRules.canCategoryHaveChildren(cat.id, categories)
            }.sortedWith(compareBy({ CategoryHierarchyRules.calculateLevel(it.id, categories) }, { it.displayOrder }, { it.name }))
        }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = {
                Text(
                    if (!isEditing) strings.text("Ajouter une catégorie", "Add Category", "إضافة فئة")
                    else strings.text("Modifier la catégorie", "Edit Category", "تعديل الفئة"),
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
                        .widthIn(min = 460.dp, max = 560.dp)
                        .touchDragScroll(dialogScrollState)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dynamic Level Preview Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (calculatedLevel) {
                            1 -> PosColors.PrimaryLight
                            2 -> PosColors.SecondaryLight
                            else -> PosColors.Workspace
                        },
                        border = BorderStroke(
                            1.dp,
                            when (calculatedLevel) {
                                1 -> PosColors.Primary.copy(alpha = 0.5f)
                                2 -> PosColors.Secondary.copy(alpha = 0.5f)
                                else -> PosColors.Border
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when (calculatedLevel) {
                                    1 -> "🏷️ " + strings.text("Niveau 1 — Catégorie principale", "Level 1 — Main Category", "المستوى 1 — فئة رئيسية")
                                    2 -> "📂 " + strings.text("Niveau 2 — Sous-catégorie", "Level 2 — Subcategory", "المستوى 2 — فئة فرعية")
                                    3 -> "📄 " + strings.text("Niveau 3 — Sous-sous-catégorie (Niveau Max)", "Level 3 — Sub-subcategory (Max Level)", "المستوى 3 — فئة فرعية ثانية (أقصى مستوى)")
                                    else -> "⚠️ " + strings.text("Niveau non autorisé (> 3)", "Disallowed level (> 3)", "مستوى غير مسموح به")
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = when (calculatedLevel) {
                                    1 -> PosColors.Primary
                                    2 -> PosColors.SecondaryDark
                                    else -> PosColors.TextHigh
                                }
                            )
                        }
                    }

                    // Category Image Picker (Optional)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            strings.text("Image de la catégorie", "Category Image", "صورة الفئة"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PosColors.TextHigh
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SafeProductImage(
                                imagePath = categoryImageInput,
                                contentDescription = categoryNameInput,
                                placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        BorderStroke(
                                            1.5.dp,
                                            PosColors.Border
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        onImportImage?.invoke()
                                            ?.onSuccess { path -> if (path != null) { categoryImageInput = path; categoryImageError = null } }
                                            ?.onFailure { categoryImageError = it.message }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(40.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        if (!categoryImageInput.isNullOrBlank()) strings.text("Changer l'image", "Change image", "تغيير الصورة")
                                        else strings.text("Ajouter une image", "Add image", "إضافة صورة"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (!categoryImageInput.isNullOrBlank()) {
                                    TextButton(
                                        onClick = { categoryImageInput = null },
                                        colors = ButtonDefaults.textButtonColors(contentColor = PosColors.Danger),
                                        modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text(strings.delete, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                FieldErrorMessage(categoryImageError)
                            }
                        }
                    }

                    // Category Name Field
                    TouchTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it; categoryNameError = null },
                        label = strings.categoryName + " *",
                        placeholder = strings.text("Ex: Pâtisserie, Gâteaux, Viennoiserie...", "E.g. Pastry, Cakes, Viennoiserie...", "مثال: حلويات، كعك..."),
                        errorMessage = categoryNameError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Parent Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            strings.text("Catégorie parente", "Parent Category", "الفئة الأب"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PosColors.TextHigh
                        )

                        var parentDropdownExpanded by remember { mutableStateOf(false) }
                        val selectedParentName = remember(categoryParentIdInput, categories) {
                            if (categoryParentIdInput == null) {
                                "(Aucun — Catégorie racine / Niveau 1)"
                            } else {
                                val parent = categories.firstOrNull { it.id == categoryParentIdInput }
                                if (parent != null) {
                                    val parentLevel = CategoryHierarchyRules.calculateLevel(parent.id, categories)
                                    "Niveau $parentLevel : ${parent.name}"
                                } else "(Aucun — Catégorie racine / Niveau 1)"
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { parentDropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = PosColors.Surface
                                ),
                                border = BorderStroke(1.dp, PosColors.Border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedParentName,
                                        fontSize = 13.sp,
                                        color = PosColors.TextHigh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("▼", fontSize = 11.sp, color = PosColors.TextMedium)
                                }
                            }

                            DropdownMenu(
                                expanded = parentDropdownExpanded,
                                onDismissRequest = { parentDropdownExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 340.dp, max = 480.dp)
                                    .background(PosColors.Surface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "🏠 " + strings.text("(Aucun — Catégorie racine / Niveau 1)", "(None — Root Category / Level 1)", "(بدون — فئة رئيسية)"),
                                            fontWeight = if (categoryParentIdInput == null) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            color = if (categoryParentIdInput == null) PosColors.Primary else PosColors.TextHigh
                                        )
                                    },
                                    onClick = {
                                        categoryParentIdInput = null
                                        parentDropdownExpanded = false
                                    }
                                )

                                Divider(color = PosColors.Border, thickness = 0.5.dp)

                                validParentOptions.forEach { parentCandidate ->
                                    val candidateLevel = CategoryHierarchyRules.calculateLevel(parentCandidate.id, categories)
                                    val prefix = if (candidateLevel == 1) "🏷️ " else "  ↳ 📂 "
                                    val levelBadgeText = if (candidateLevel == 1) "Niv. 1" else "Niv. 2"

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "$prefix${parentCandidate.name}",
                                                    fontWeight = if (categoryParentIdInput == parentCandidate.id) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    color = if (categoryParentIdInput == parentCandidate.id) PosColors.Primary else PosColors.TextHigh
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (candidateLevel == 1) PosColors.PrimaryLight else PosColors.SecondaryLight
                                                ) {
                                                    Text(
                                                        levelBadgeText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (candidateLevel == 1) PosColors.Primary else PosColors.SecondaryDark,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            categoryParentIdInput = parentCandidate.id
                                            categoryHierarchyError = null
                                            parentDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        FieldErrorMessage(categoryHierarchyError)
                    }

                    // Display order numeric field
                    TouchNumericField(
                        value = categoryDisplayOrderInput,
                        onValueChange = { categoryDisplayOrderInput = it },
                        label = strings.text("Ordre d’affichage", "Display order", "ترتيب العرض"),
                        placeholder = "0, 1, 2…",
                        isDecimalAllowed = false,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Active Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryActiveInput = !categoryActiveInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Switch(
                            checked = categoryActiveInput,
                            onCheckedChange = { categoryActiveInput = it }
                        )
                        Column {
                            Text(
                                if (categoryActiveInput) strings.text("Catégorie active", "Active category", "فئة نشطة")
                                else strings.text("Catégorie inactive", "Inactive category", "فئة غير نشطة"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = PosColors.TextHigh
                            )
                            Text(
                                if (categoryActiveInput) strings.text("Visible dans la caisse", "Visible on POS", "مرئية في الكاشير")
                                else strings.text("Masquée de la caisse", "Hidden from POS", "مخفية من الكاشير"),
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
                        categoryNameError = null
                        categoryImageError = null
                        categoryHierarchyError = null

                        if (categoryNameInput.trim().isBlank()) {
                            categoryNameError = strings.required
                            return@Button
                        }

                        val targetCat = Category(
                            id = editingCategory?.id ?: 0L,
                            name = categoryNameInput.trim(),
                            active = categoryActiveInput,
                            displayOrder = categoryDisplayOrderInput.toIntOrNull() ?: 0,
                            parentId = categoryParentIdInput,
                            imagePath = categoryImageInput?.trim()?.ifBlank { null }
                        )

                        // Hierarchy validation
                        val validation = CategoryHierarchyRules.validateCategoryHierarchy(targetCat, categories)
                        if (validation.isFailure) {
                            categoryHierarchyError = validation.exceptionOrNull()?.message ?: "Erreur de validation."
                            return@Button
                        }

                        if (isSavingCategory) return@Button
                        isSavingCategory = true
                        scope.launch {
                            try {
                                val saveError = if (onSaveCategory != null) onSaveCategory(targetCat)
                                    else onSaveCategoryWithSubcategory?.invoke(targetCat, targetCat)
                                if (saveError == null) {
                                    showCategoryDialog = false
                                    targetCat.parentId?.let { pid -> expandedCategoryIds = expandedCategoryIds + pid }
                                } else {
                                    categoryNameError = saveError
                                }
                            } finally { isSavingCategory = false }
                        }
                    },
                    enabled = !isSavingCategory,
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCategoryDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Modal: Delete Confirmation Dialog
    categoryToDelete?.let { cat ->
        val descendants = remember(cat, categories) {
            CategoryHierarchyRules.getAllDescendantIds(cat.id, categories)
        }
        val affectedProductsCount = remember(cat, descendants, products) {
            products.count { it.categoryId == cat.id || it.categoryId in descendants }
        }

        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = {
                Text(
                    strings.text("Supprimer la catégorie", "Delete Category", "حذف الفئة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.TextHigh
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        strings.text(
                            "Êtes-vous sûr de vouloir supprimer la catégorie « ${cat.name} » ?",
                            "Are you sure you want to delete category '${cat.name}'?",
                            "هل أنت متأكد من حذف الفئة '${cat.name}'؟"
                        ),
                        fontSize = 14.sp,
                        color = PosColors.TextMedium
                    )

                    if (descendants.isNotEmpty() || affectedProductsCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PosColors.Danger.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "⚠️ " + strings.text("Conséquences de la suppression :", "Deletion consequences:", "عواقب الحذف:"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PosColors.Danger
                                )
                                if (descendants.isNotEmpty()) {
                                    Text(
                                        "• " + strings.text(
                                            "${descendants.size} sous-catégorie(s) enfant(s) seront également archivée(s) en cascade.",
                                            "${descendants.size} child subcategory/ies will also be archived in cascade.",
                                            "سيتم أرشفة ${descendants.size} فئة فرعية تابعة."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextHigh
                                    )
                                }
                                if (affectedProductsCount > 0) {
                                    Text(
                                        "• " + strings.text(
                                            "$affectedProductsCount produit(s) associés resteront enregistrés mais n'apparaîtront plus sous cette catégorie.",
                                            "$affectedProductsCount product(s) will remain in DB but won't be listed under this category.",
                                            "ستبقى $affectedProductsCount منتجات مسجلة ولكن لن تظهر تحت هذه الفئة."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextHigh
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSoftDeleteCategory(cat)
                        categoryToDelete = null
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
                    onClick = { categoryToDelete = null },
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
private fun CategoryTreeNodeRowCard(
    node: CategoryTreeNode,
    productCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    strings: DesktopStrings,
    onAddChild: (() -> Unit)?,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val levelIndent = when (node.level) {
        1 -> 0.dp
        2 -> 40.dp
        else -> 80.dp
    }

    val imageSize = when (node.level) {
        1 -> 60.dp
        2 -> 48.dp
        else -> 40.dp
    }

    val levelBadgeColor = when (node.level) {
        1 -> PosColors.Primary
        2 -> PosColors.SecondaryDark
        else -> Color(0xFF64748B) // Neutral Slate
    }

    val levelBadgeBg = when (node.level) {
        1 -> PosColors.PrimaryLight
        2 -> PosColors.SecondaryLight
        else -> Color(0xFFF1F5F9)
    }

    val levelText = when (node.level) {
        1 -> "NIVEAU 1"
        2 -> "NIVEAU 2"
        else -> "NIVEAU 3 (MAX)"
    }

    val hasChildren = node.children.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = levelIndent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand / Collapse arrow button (only if has children)
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .size(28.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) PosColors.Primary else PosColors.TextMedium
                )
            }
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (hasChildren) {
                            onToggleExpand()
                        }
                    }
                )
                .pointerHoverIcon(if (hasChildren) PointerIcon.Hand else PointerIcon.Default),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!node.category.active) PosColors.Workspace.copy(alpha = 0.5f)
                else if (isHovered && hasChildren) PosColors.Workspace else PosColors.Surface
            ),
            border = BorderStroke(
                1.dp,
                if (isHovered && hasChildren) PosColors.Primary.copy(alpha = 0.6f) else PosColors.Border
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (node.level == 1) 2.dp else 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Image
                SafeProductImage(
                    imagePath = node.category.imagePath,
                    contentDescription = node.category.name,
                    placeholderText = node.category.name.take(2).uppercase(),
                    modifier = Modifier
                        .size(imageSize)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, PosColors.Border, RoundedCornerShape(8.dp))
                )

                // Info & Badges
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = node.category.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (node.level == 1) 15.sp else 14.sp,
                            color = if (node.category.active) PosColors.TextHigh else PosColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = levelBadgeBg,
                            border = BorderStroke(1.dp, levelBadgeColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = levelText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelBadgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!node.category.active) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PosColors.Workspace,
                                border = BorderStroke(1.dp, PosColors.Border)
                            ) {
                                Text(
                                    strings.text("Inactive", "Inactive", "غير نشطة"),
                                    fontSize = 10.sp,
                                    color = PosColors.TextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (node.level < 3) {
                            Text(
                                text = "📂 " + strings.text(
                                    "${node.children.size} sous-catégorie(s)",
                                    "${node.children.size} subcategory/ies",
                                    "${node.children.size} فئة فرعية"
                                ),
                                fontSize = 12.sp,
                                color = PosColors.TextMedium
                            )
                        }

                        Text(
                            text = "🏷️ " + strings.text(
                                "$productCount produit(s)",
                                "$productCount product(s)",
                                "$productCount منتج"
                            ),
                            fontSize = 12.sp,
                            color = PosColors.TextMedium
                        )

                        Text(
                            text = "#${node.category.displayOrder}",
                            fontSize = 11.sp,
                            color = PosColors.TextMuted
                        )
                    }
                }

                // Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Switch
                    Switch(
                        checked = node.category.active,
                        onCheckedChange = { onToggleActive() },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )

                    // Quick "+ Sous-catégorie" action (Allowed ONLY on Level 1 and Level 2, strictly blocked/hidden on Level 3)
                    if (onAddChild != null) {
                        OutlinedButton(
                            onClick = onAddChild,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PosColors.Primary
                            ),
                            border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(36.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                "+ " + strings.text("Sous-catégorie", "Subcategory", "فئة فرعية"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Edit Button
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(strings.edit, fontSize = 12.sp)
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("🗑️", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
