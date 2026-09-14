package ma.elaroui.pos.presentation.management.tables

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import ma.elaroui.pos.domain.model.DiningArea
import ma.elaroui.pos.domain.model.RestaurantTable
import ma.elaroui.pos.domain.model.TableStatus
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions
import ma.elaroui.pos.presentation.components.ManagedLocalImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManagementScreen(
    viewModel: TableManagementViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Tables, 1 = Zones
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val areaImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importAreaImage(context, it) }
    }
    LaunchedEffect(
        uiState.errorMessage,
        uiState.successMessage,
        uiState.isAreaDialogOpen,
        uiState.isTableDialogOpen,
        uiState.isBulkDialogOpen
    ) {
        val anyDialogOpen = uiState.isAreaDialogOpen || uiState.isTableDialogOpen || uiState.isBulkDialogOpen
        val message = uiState.errorMessage.takeIf { !anyDialogOpen } ?: uiState.successMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val filteredTables = uiState.tables.filter { table ->
        val matchesSearch = table.name.contains(uiState.searchQuery, ignoreCase = true)
        val matchesArea = (uiState.selectedAreaId == null || table.areaId == uiState.selectedAreaId)
        matchesSearch && matchesArea
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Zones & Tables de Restauration", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openBulkDialog() },
                        containerColor = Color(0xFF1D3557),
                        contentColor = Color.White
                    ) {
                        Text("⚡ Création en Lot")
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) viewModel.openTableDialog() else viewModel.openAreaDialog()
                    },
                    containerColor = Color(0xFFE63946)
                ) {
                    Text("+", fontSize = 28.sp, color = Color.White)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .padding(adaptive.screenContentPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Gestion des Tables", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Zones de Restauration", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // TABLES TAB
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    label = { Text("Rechercher une table...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Area Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = (uiState.selectedAreaId == null),
                            onClick = { viewModel.selectAreaFilter(null) },
                            label = { Text("Toutes les zones") }
                        )
                    }
                    items(uiState.areas) { area ->
                        FilterChip(
                            selected = (uiState.selectedAreaId == area.id),
                            onClick = { viewModel.selectAreaFilter(area.id) },
                            label = { Text(area.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aucune table trouvée.", fontSize = 18.sp, color = Color(0xFF64748B))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptive.generalCardMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTables) { table ->
                            val areaName = uiState.areas.find { it.id == table.areaId }?.name ?: "Zone #${table.areaId}"
                            TableCard(
                                table = table,
                                areaName = areaName,
                                onEdit = { viewModel.openTableDialog(table) },
                                onToggleActive = { viewModel.toggleTableActiveState(table) }
                            )
                        }
                    }
                }
            } else {
                // DINING AREAS TAB
                if (uiState.areas.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aucune zone de restauration enregistrée.", fontSize = 18.sp, color = Color(0xFF64748B))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptive.productCardMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.areas) { area ->
                            val tableCount = uiState.tables.count { it.areaId == area.id && it.active }
                            AreaCard(
                                area = area,
                                tableCount = tableCount,
                                onEdit = { viewModel.openAreaDialog(area) },
                                onToggleActive = { viewModel.toggleAreaActiveState(area) }
                            )
                        }
                    }
                }
            }

            // SINGLE TABLE DIALOG
            if (uiState.isTableDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeTableDialog() },
                    title = { Text(if (uiState.editingTable == null) "Ajouter une Table" else "Modifier la Table") },
                    text = {
                        Column {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            OutlinedTextField(
                                value = uiState.tableNameInput,
                                onValueChange = { viewModel.updateTableForm(it, uiState.tableAreaIdInput) },
                                label = { Text("Nom ou numéro de table *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Zone de Restauration *", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.areas.filter { it.active }) { area ->
                                    FilterChip(
                                        selected = (uiState.tableAreaIdInput == area.id),
                                        onClick = { viewModel.updateTableForm(uiState.tableNameInput, area.id) },
                                        label = { Text(area.name) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitSaveTable() },
                            enabled = uiState.tableNameInput.isNotBlank() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Enregistrer", color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.closeTableDialog() }) { Text("Annuler") }
                    }
                )
            }

            // AREA DIALOG
            if (uiState.isAreaDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeAreaDialog() },
                    title = { Text(if (uiState.editingArea == null) "Ajouter une Zone" else "Modifier la Zone") },
                    text = {
                        Column {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            ManagedLocalImage(
                                imagePath = uiState.areaImagePathInput,
                                contentDescription = uiState.areaNameInput,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { areaImagePicker.launch("image/*") }) {
                                    Text(if (uiState.areaImagePathInput == null) "Choisir une image" else "Changer l'image")
                                }
                                if (uiState.areaImagePathInput != null) {
                                    TextButton(onClick = viewModel::removeAreaImage) { Text("Supprimer") }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.areaNameInput,
                                onValueChange = { viewModel.updateAreaNameInput(it) },
                                label = { Text("Nom de la zone *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitSaveArea() },
                            enabled = uiState.areaNameInput.isNotBlank() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Enregistrer", color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.closeAreaDialog() }) { Text("Annuler") }
                    }
                )
            }

            // BULK TABLE CREATION DIALOG
            if (uiState.isBulkDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeBulkDialog() },
                    title = { Text("Création de Tables en Lot") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text("Zone de Restauration *", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.areas.filter { it.active }) { area ->
                                    FilterChip(
                                        selected = (uiState.bulkAreaIdInput == area.id),
                                        onClick = {
                                            viewModel.updateBulkForm(
                                                area.id,
                                                uiState.bulkPrefixInput,
                                                uiState.bulkStartNumberInput,
                                                uiState.bulkCountInput,
                                                uiState.bulkPaddingInput
                                            )
                                        },
                                        label = { Text(area.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = uiState.bulkPrefixInput,
                                onValueChange = {
                                    viewModel.updateBulkForm(
                                        uiState.bulkAreaIdInput,
                                        it,
                                        uiState.bulkStartNumberInput,
                                        uiState.bulkCountInput,
                                        uiState.bulkPaddingInput
                                    )
                                },
                                label = { Text("Préfixe (ex: Table, T)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.bulkStartNumberInput,
                                    onValueChange = {
                                        viewModel.updateBulkForm(
                                            uiState.bulkAreaIdInput,
                                            uiState.bulkPrefixInput,
                                            it,
                                            uiState.bulkCountInput,
                                            uiState.bulkPaddingInput
                                        )
                                    },
                                    label = { Text("Numéro départ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = uiState.bulkCountInput,
                                    onValueChange = {
                                        viewModel.updateBulkForm(
                                            uiState.bulkAreaIdInput,
                                            uiState.bulkPrefixInput,
                                            uiState.bulkStartNumberInput,
                                            it,
                                            uiState.bulkPaddingInput
                                        )
                                    },
                                    label = { Text("Nombre (1-100)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = uiState.bulkPaddingInput,
                                onValueChange = {
                                    viewModel.updateBulkForm(
                                        uiState.bulkAreaIdInput,
                                        uiState.bulkPrefixInput,
                                        uiState.bulkStartNumberInput,
                                        uiState.bulkCountInput,
                                        it
                                    )
                                },
                                label = { Text("Remplissage zéro (ex: 2 pour T01)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Aperçu des noms générés:", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp).padding(top = 4.dp)
                            ) {
                                Text(
                                    text = uiState.bulkGeneratedNamesPreview.joinToString(", "),
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitBulkCreateTables() },
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Créer le Lot de Tables", color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.closeBulkDialog() }) { Text("Annuler") }
                    }
                )
            }
        }
    }
}

@Composable
private fun TableCard(
    table: RestaurantTable,
    areaName: String,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(table.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
            Text(areaName, fontSize = 14.sp, color = Color(0xFF64748B))
            Text(
                when (table.status) {
                    TableStatus.AVAILABLE -> "Disponible"
                    TableStatus.OCCUPIED -> "Occupée"
                    TableStatus.RESERVED -> "Réservée"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (table.status) {
                    TableStatus.AVAILABLE -> Color(0xFF1E8E3E)
                    TableStatus.OCCUPIED -> Color(0xFFE63946)
                    TableStatus.RESERVED -> Color(0xFFE09F3E)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Éditer", fontSize = 12.sp) }
                Switch(
                    checked = table.active,
                    onCheckedChange = { onToggleActive() },
                    enabled = table.status == TableStatus.AVAILABLE || !table.active
                )
            }
        }
    }
}

@Composable
private fun AreaCard(
    area: DiningArea,
    tableCount: Int,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            ManagedLocalImage(
                imagePath = area.imagePath,
                contentDescription = area.name,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            Column(modifier = Modifier.padding(16.dp)) {
            Text(area.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
            Text("$tableCount tables actives", fontSize = 14.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Éditer") }
                Switch(
                    checked = area.active,
                    onCheckedChange = { onToggleActive() }
                )
            }
            }
        }
    }
}
