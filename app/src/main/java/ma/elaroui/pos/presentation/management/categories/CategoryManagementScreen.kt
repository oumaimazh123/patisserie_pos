package ma.elaroui.pos.presentation.management.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage, uiState.successMessage, uiState.isAddEditDialogOpen) {
        val message = uiState.errorMessage.takeIf { !uiState.isAddEditDialogOpen }
            ?: uiState.successMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val filteredCategories = uiState.categories.filter {
        it.name.contains(uiState.searchQuery, ignoreCase = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gestion des Catégories", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = Color(0xFFE63946)
            ) {
                Text("+", fontSize = 28.sp, color = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .padding(24.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Rechercher une catégorie...") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (filteredCategories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune catégorie trouvée.", fontSize = 18.sp, color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredCategories) { category ->
                        CategoryRow(
                            category = category,
                            onEdit = { viewModel.openEditDialog(category) },
                            onToggleActive = { viewModel.toggleCategoryActiveState(category) }
                        )
                    }
                }
            }

            if (uiState.isAddEditDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text(if (uiState.editingCategory == null) "Ajouter une Catégorie" else "Modifier la Catégorie") },
                    text = {
                        Column {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            OutlinedTextField(
                                value = uiState.categoryNameInput,
                                onValueChange = { viewModel.updateCategoryNameInput(it) },
                                label = { Text("Nom de la catégorie *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitSaveCategory() },
                            enabled = uiState.categoryNameInput.isNotBlank() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Enregistrer", color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.closeDialog() }) { Text("Annuler") }
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(category.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Text(
                    text = if (category.active) "Statut: Actif" else "Statut: Inactif",
                    fontSize = 14.sp,
                    color = if (category.active) Color(0xFF1E8E3E) else Color(0xFF64748B)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Éditer") }
                Switch(
                    checked = category.active,
                    onCheckedChange = { onToggleActive() }
                )
            }
        }
    }
}
