package ma.elaroui.pos.desktop.presentation.management.tables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.FieldErrorMessage
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.SafeAreaImage
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.touchHorizontalDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.domain.DiningArea
import ma.elaroui.pos.shared.domain.RestaurantTable
import ma.elaroui.pos.shared.domain.TableStatus

@Composable
fun TableManagementScreen(
    areas: List<DiningArea>,
    tables: List<RestaurantTable>,
    strings: DesktopStrings,
    onSaveArea: (DiningArea) -> String?,
    onToggleAreaActive: (DiningArea) -> Unit,
    onSoftDeleteArea: (DiningArea) -> Unit = {},
    onSaveTable: (areaId: Long, name: String) -> String?,
    onUpdateTable: (tableId: Long, name: String, areaId: Long) -> String? = { _, _, _ -> null },
    onTableStatusChanged: (tableId: Long, newStatus: TableStatus) -> Unit,
    onToggleTableActive: (RestaurantTable) -> Unit,
    onSoftDeleteTable: (RestaurantTable) -> Unit = {},
    onImportImage: () -> String?,
    onBack: (() -> Unit)? = null,
    message: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var selectedAreaId by remember { mutableStateOf<Long?>(areas.firstOrNull()?.id) }
    var showAreaDialog by remember { mutableStateOf(false) }
    var editingArea by remember { mutableStateOf<DiningArea?>(null) }
    var areaToDelete by remember { mutableStateOf<DiningArea?>(null) }
    var showTableDialog by remember { mutableStateOf(false) }
    var editingTable by remember { mutableStateOf<RestaurantTable?>(null) }
    var tableToDelete by remember { mutableStateOf<RestaurantTable?>(null) }

    var areaNameInput by remember { mutableStateOf("") }
    var areaImagePathInput by remember { mutableStateOf<String?>(null) }
    var areaActiveInput by remember { mutableStateOf(true) }
    var tableNameInput by remember { mutableStateOf("") }
    var selectedTableAreaId by remember { mutableStateOf<Long>(areas.firstOrNull()?.id ?: 0L) }

    var areaNameError by remember { mutableStateOf<String?>(null) }
    var tableNameError by remember { mutableStateOf<String?>(null) }
    var tableAreaError by remember { mutableStateOf<String?>(null) }

    // If selected area is no longer in active list, update selected area
    LaunchedEffect(areas) {
        if (selectedAreaId == null || areas.none { it.id == selectedAreaId }) {
            selectedAreaId = areas.firstOrNull()?.id
        }
    }

    val selectedArea = areas.firstOrNull { it.id == selectedAreaId }
    val filteredTables = tables.filter { selectedAreaId == null || it.areaId == selectedAreaId }

    fun openAddAreaDialog() {
        editingArea = null
        areaNameInput = ""
        areaImagePathInput = null
        areaActiveInput = true
        areaNameError = null
        showAreaDialog = true
    }

    fun openEditAreaDialog(area: DiningArea) {
        editingArea = area
        areaNameInput = area.name
        areaImagePathInput = area.imagePath
        areaActiveInput = area.active
        areaNameError = null
        showAreaDialog = true
    }

    fun openAddTableDialog() {
        editingTable = null
        tableNameInput = ""
        selectedTableAreaId = selectedAreaId ?: areas.firstOrNull()?.id ?: 0L
        tableNameError = null
        tableAreaError = null
        showTableDialog = true
    }

    fun openEditTableDialog(table: RestaurantTable) {
        editingTable = table
        tableNameInput = table.name
        selectedTableAreaId = table.areaId
        tableNameError = null
        tableAreaError = null
        showTableDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.tableMgmt,
            strings = strings,
            onBackToDashboard = onBack
        ) {
            OutlinedButton(
                    onClick = { openAddAreaDialog() },
                    modifier = Modifier
                        .height(48.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+ " + strings.areaName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                    onClick = { openAddTableDialog() },
                    modifier = Modifier
                        .height(48.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text("+ " + strings.tableLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (message.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PosColors.PrimaryLight,
                    border = BorderStroke(1.dp, PosColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message, color = PosColors.BakeryBrown, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // Active Tables and Areas View
            // Area Filter Chips & Info Banner
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val areaListState = remember { androidx.compose.foundation.lazy.LazyListState() }
                    LazyRow(
                        state = areaListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.touchHorizontalDragScroll(areaListState)
                    ) {
                        items(areas) { area ->
                            val isSelected = selectedAreaId == area.id
                            Surface(
                                onClick = { selectedAreaId = area.id },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) PosColors.Primary else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                shadowElevation = if (isSelected) 2.dp else 1.dp,
                                modifier = Modifier
                                    .height(48.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        area.name + if (!area.active) " (${strings.inactive})" else "",
                                        color = if (isSelected) Color.White else PosColors.TextHigh,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Selected Area Info Card
                    selectedArea?.let { area ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, PosColors.Border),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SafeAreaImage(
                                        imagePath = area.imagePath,
                                        contentDescription = area.name,
                                        placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(area.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosColors.BakeryBrown)
                                        Text(
                                            "${filteredTables.size} ${strings.text("table(s) assignée(s)", "assigned table(s)", "طاولة محددة")}" +
                                                    if (!area.active) " · ${strings.inactive}" else "",
                                            fontSize = 12.sp,
                                            color = if (area.active) PosColors.TextMedium else PosColors.Danger
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onToggleAreaActive(area) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text(if (area.active) strings.deactivate else strings.activate, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { openEditAreaDialog(area) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text("✏️ " + strings.edit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { areaToDelete = area },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Text("🗑️ " + strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Tables Grid
                if (filteredTables.isEmpty()) {
                    EmptyStateCard(
                        title = if (areas.isEmpty()) strings.text("Veuillez d'abord ajouter un espace", "Please add a dining area first", "يرجى إضافة مساحة أولاً") else strings.noResults,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val tablesGridState = remember { androidx.compose.foundation.lazy.grid.LazyGridState() }
                    LazyVerticalGrid(
                        state = tablesGridState,
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .touchDragScroll(tablesGridState)
                    ) {
                        items(filteredTables) { table ->
                            TableCardItem(
                                table = table,
                                strings = strings,
                                onEdit = { openEditTableDialog(table) },
                                onStatusChanged = { newStatus -> onTableStatusChanged(table.id, newStatus) },
                                onToggleActive = { onToggleTableActive(table) },
                                onDelete = { tableToDelete = table }
                            )
                        }
                    }
                }
            }
        }

    // Add / Edit Area Modal Dialog
    if (showAreaDialog) {
        val isEditing = editingArea != null
        AlertDialog(
            onDismissRequest = { showAreaDialog = false },
            title = {
                Text(
                    if (!isEditing) strings.text("Ajouter un espace", "Add Dining Area", "إضافة مساحة") else strings.text("Modifier l'espace", "Edit Dining Area", "تعديل المساحة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val areaDialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .touchDragScroll(areaDialogScrollState)
                        .verticalScroll(areaDialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SafeAreaImage(
                        imagePath = areaImagePathInput,
                        contentDescription = areaNameInput,
                        placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val newPath = onImportImage()
                                if (newPath != null) areaImagePathInput = newPath
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                if (areaImagePathInput != null) strings.text("Changer l'image", "Change image", "تغيير الصورة") else strings.text("Choisir une image", "Choose image", "اختيار صورة"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (areaImagePathInput != null) {
                            TextButton(
                                onClick = { areaImagePathInput = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = PosColors.Danger),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    TouchTextField(
                        value = areaNameInput,
                        onValueChange = { areaNameInput = it; areaNameError = null },
                        label = strings.areaName + " *",
                        placeholder = strings.text("Ex: Terrasse, Salle Principale…", "e.g. Terrace, Main Room…", "مثال: الشرفة، القاعة الرئيسية…"),
                        errorMessage = areaNameError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { areaActiveInput = !areaActiveInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Switch(
                            checked = areaActiveInput,
                            onCheckedChange = { areaActiveInput = it }
                        )
                        Column {
                            Text(
                                if (areaActiveInput) strings.text("Espace actif", "Active area", "مساحة نشطة")
                                else strings.text("Espace inactif", "Inactive area", "مساحة غير نشطة"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = PosColors.TextHigh
                            )
                            Text(
                                if (areaActiveInput) strings.text("Disponible pour le placement de tables", "Available for table assignments", "متاح لتعيين الطاولات")
                                else strings.text("Désactivé pour le service", "Disabled for service", "معطل للخدمة"),
                                fontSize = 11.sp,
                                color = PosColors.TextMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        areaNameError = null
                        if (areaNameInput.isBlank()) {
                            areaNameError = strings.required
                        } else {
                            val areaToSave = editingArea?.copy(
                                name = areaNameInput.trim(),
                                active = areaActiveInput,
                                imagePath = areaImagePathInput
                            ) ?: DiningArea(
                                id = 0L,
                                name = areaNameInput.trim(),
                                active = areaActiveInput,
                                imagePath = areaImagePathInput
                            )
                            val saveError = onSaveArea(areaToSave)
                            if (saveError == null) {
                                showAreaDialog = false
                            } else {
                                areaNameError = saveError
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAreaDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Add / Edit Table Modal Dialog
    if (showTableDialog) {
        val isEditing = editingTable != null

        AlertDialog(
            onDismissRequest = { showTableDialog = false },
            title = {
                Text(
                    if (isEditing) strings.text("Modifier la table", "Edit Table", "تعديل الطاولة") else strings.text("Ajouter une table", "Add Table", "إضافة طاولة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val tableDialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .touchDragScroll(tableDialogScrollState)
                        .verticalScroll(tableDialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        strings.areaName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.TextHigh
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(areas.filter { it.active }) { area ->
                            val isSelected = selectedTableAreaId == area.id
                            Surface(
                                onClick = {
                                    selectedTableAreaId = area.id
                                    tableAreaError = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                modifier = Modifier
                                    .height(40.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(
                                        area.name,
                                        color = if (isSelected) Color.White else PosColors.TextHigh,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    FieldErrorMessage(tableAreaError)

                    TouchTextField(
                        value = tableNameInput,
                        onValueChange = { tableNameInput = it; tableNameError = null },
                        label = strings.tableName + " *",
                        placeholder = strings.text("Ex: Table 1, T12…", "e.g. Table 1, T12…", "مثال: طاولة 1…"),
                        errorMessage = tableNameError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        tableNameError = null
                        tableAreaError = null
                        var hasError = false
                        if (tableNameInput.isBlank()) {
                            tableNameError = strings.required
                            hasError = true
                        }
                        if (selectedTableAreaId <= 0L) {
                            tableAreaError = strings.text("Veuillez sélectionner un espace", "Please select an area", "يرجى اختيار مساحة")
                            hasError = true
                        }
                        if (hasError) return@Button

                        val saveError = if (isEditing) {
                            onUpdateTable(editingTable!!.id, tableNameInput.trim(), selectedTableAreaId)
                        } else {
                            onSaveTable(selectedTableAreaId, tableNameInput.trim())
                        }
                        if (saveError == null) {
                            showTableDialog = false
                        } else {
                            tableNameError = saveError
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showTableDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete Area Confirmation Dialog
    if (areaToDelete != null) {
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            title = {
                Text(
                    strings.text("Supprimer l'espace", "Delete Dining Area", "حذف المساحة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                Text(
                    strings.text(
                        "Voulez-vous vraiment supprimer l'espace « ${areaToDelete?.name} » ? Les tables et l'historique associés resteront intacts.",
                        "Are you sure you want to delete area \"${areaToDelete?.name}\"? Associated tables and history will remain intact.",
                        "هل أنت متأكد من حذف المساحة « ${areaToDelete?.name} »؟ ستبقى الطاولات والسجلات المرتبطة بها سليمة."
                    ),
                    fontSize = 14.sp,
                    color = PosColors.TextMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        areaToDelete?.let { onSoftDeleteArea(it) }
                        areaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { areaToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete Table Confirmation Dialog
    if (tableToDelete != null) {
        AlertDialog(
            onDismissRequest = { tableToDelete = null },
            title = {
                Text(
                    strings.text("Supprimer la table", "Delete Table", "حذف الطاولة"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                Text(
                    strings.text(
                        "Voulez-vous vraiment supprimer la table « ${tableToDelete?.name} » ? Les commandes passées associées resteront intactes.",
                        "Are you sure you want to delete table \"${tableToDelete?.name}\"? Associated past orders will remain intact.",
                        "هل أنت متأكد من حذف الطاولة « ${tableToDelete?.name} »؟ ستبقى الطلبات السابقة المرتبطة بها سليمة."
                    ),
                    fontSize = 14.sp,
                    color = PosColors.TextMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        tableToDelete?.let { onSoftDeleteTable(it) }
                        tableToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { tableToDelete = null },
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
private fun TableCardItem(
    table: RestaurantTable,
    strings: DesktopStrings,
    onEdit: () -> Unit,
    onStatusChanged: (TableStatus) -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg = when (table.status) {
        TableStatus.OCCUPIED -> PosColors.DangerLight.copy(alpha = 0.6f)
        TableStatus.RESERVED -> PosColors.AlertLight.copy(alpha = 0.6f)
        TableStatus.AVAILABLE -> if (isHovered) PosColors.Workspace else PosColors.Surface
    }

    val borderColor = when (table.status) {
        TableStatus.OCCUPIED -> if (isHovered) PosColors.Danger else PosColors.Danger.copy(alpha = 0.4f)
        TableStatus.RESERVED -> if (isHovered) PosColors.Alert else PosColors.Alert.copy(alpha = 0.4f)
        TableStatus.AVAILABLE -> if (isHovered) PosColors.Primary else PosColors.Border
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (table.status == TableStatus.OCCUPIED) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Table Icon & Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                when (table.status) {
                                    TableStatus.OCCUPIED -> PosColors.DangerLight
                                    TableStatus.RESERVED -> PosColors.AlertLight
                                    TableStatus.AVAILABLE -> PosColors.Workspace
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🪑", fontSize = 16.sp)
                    }
                    Column {
                        Text(
                            table.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!table.active) {
                            Text(
                                strings.inactive,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PosColors.Danger
                            )
                        }
                    }
                }

                // Status Indicator Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (table.status) {
                        TableStatus.AVAILABLE -> PosColors.SuccessLight
                        TableStatus.OCCUPIED -> PosColors.DangerLight
                        TableStatus.RESERVED -> PosColors.AlertLight
                    },
                    border = BorderStroke(
                        1.dp,
                        when (table.status) {
                            TableStatus.AVAILABLE -> PosColors.Success.copy(alpha = 0.3f)
                            TableStatus.OCCUPIED -> PosColors.Danger.copy(alpha = 0.3f)
                            TableStatus.RESERVED -> PosColors.Alert.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Text(
                        when (table.status) {
                            TableStatus.AVAILABLE -> "🟢 " + strings.tableAvailable
                            TableStatus.OCCUPIED -> "🔴 " + strings.tableOccupied
                            TableStatus.RESERVED -> "🟡 " + strings.tableReserved
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (table.status) {
                            TableStatus.AVAILABLE -> PosColors.Success
                            TableStatus.OCCUPIED -> PosColors.Danger
                            TableStatus.RESERVED -> PosColors.Alert
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = PosColors.Border)

            // Quick Status Switcher Segmented Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TableStatus.entries.forEach { status ->
                    val isCurrent = table.status == status
                    val label = when (status) {
                        TableStatus.AVAILABLE -> strings.tableAvailable
                        TableStatus.OCCUPIED -> strings.tableOccupied
                        TableStatus.RESERVED -> strings.tableReserved
                    }

                    Surface(
                        onClick = { if (table.active && !isCurrent) onStatusChanged(status) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCurrent) {
                            when (status) {
                                TableStatus.AVAILABLE -> PosColors.Success
                                TableStatus.OCCUPIED -> PosColors.Danger
                                TableStatus.RESERVED -> PosColors.Alert
                            }
                        } else PosColors.Workspace,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .pointerHoverIcon(if (table.active && !isCurrent) PointerIcon.Hand else PointerIcon.Default)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                color = if (isCurrent) Color.White else PosColors.TextMedium,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Actions: Modifier, Activer/Désactiver, Supprimer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, PosColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("✏️ " + strings.edit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onToggleActive,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(
                            if (table.active) strings.deactivate else strings.activate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (table.active) PosColors.TextMuted else PosColors.Primary
                        )
                    }

                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = PosColors.Danger),
                        modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("🗑️ " + strings.delete, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
