package ma.elaroui.pos.presentation.pos.active

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.TableStatus
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveOrdersScreen(
    viewModel: ActiveOrdersViewModel,
    onResumeOrder: (Long) -> Unit,
    onNavigateToPayment: (Long) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current

    val filteredOrders = uiState.activeOrders.filter { order ->
        val tableName = uiState.tables.find { it.id == order.tableId }?.name ?: ""
        val matchesSearch = order.orderNumber.contains(uiState.searchQuery, ignoreCase = true) ||
                tableName.contains(uiState.searchQuery, ignoreCase = true)
        val matchesType = (uiState.selectedTypeFilter == null || order.type == uiState.selectedTypeFilter)
        matchesSearch && matchesType
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commandes En Cours (Active Orders)", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Caisse", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .padding(adaptive.screenContentPadding)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Rechercher par N° commande ou Table...") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Order Type Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = (uiState.selectedTypeFilter == null),
                        onClick = { viewModel.selectTypeFilter(null) },
                        label = { Text("Toutes (${uiState.activeOrders.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = (uiState.selectedTypeFilter == OrderType.DINE_IN),
                        onClick = { viewModel.selectTypeFilter(OrderType.DINE_IN) },
                        label = { Text("Sur Place (${uiState.activeOrders.count { it.type == OrderType.DINE_IN }})") }
                    )
                }
                item {
                    FilterChip(
                        selected = (uiState.selectedTypeFilter == OrderType.TAKEAWAY),
                        onClick = { viewModel.selectTypeFilter(OrderType.TAKEAWAY) },
                        label = { Text("A Emporter (${uiState.activeOrders.count { it.type == OrderType.TAKEAWAY }})") }
                    )
                }
                item {
                    FilterChip(
                        selected = (uiState.selectedTypeFilter == OrderType.COUNTER),
                        onClick = { viewModel.selectTypeFilter(OrderType.COUNTER) },
                        label = { Text("Comptoir (${uiState.activeOrders.count { it.type == OrderType.COUNTER }})") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune commande active en cours.", fontSize = 18.sp, color = Color(0xFF64748B))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredOrders) { order ->
                        val tableName = uiState.tables.find { it.id == order.tableId }?.name
                        ActiveOrderCard(
                            order = order,
                            tableName = tableName,
                            onResume = { onResumeOrder(order.id) },
                            onPay = { onNavigateToPayment(order.id) },
                            onMoveTable = { viewModel.openMoveTableDialog(order) },
                            onCancel = { viewModel.openCancelDialog(order) }
                        )
                    }
                }
            }

            // MOVE TABLE DIALOG
            if (uiState.isMoveTableDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeMoveTableDialog() },
                    title = { Text("Déplacer la Commande vers une Autre Table") },
                    text = {
                        Column {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            val availableTables = uiState.tables.filter { it.active && it.status == TableStatus.AVAILABLE }
                            Text("Choisir la table de destination *", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (availableTables.isEmpty()) {
                                Text("Aucune table disponible.", color = Color(0xFFE63946))
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableTables) { table ->
                                        FilterChip(
                                            selected = (uiState.destinationTableIdInput == table.id),
                                            onClick = { viewModel.selectDestinationTable(table.id) },
                                            label = { Text(table.name) }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitMoveTable() },
                            enabled = uiState.destinationTableIdInput != null && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Confirmer le Déplacement", color = Color.White)
                        }
                    },
                    dismissButton = { OutlinedButton(onClick = { viewModel.closeMoveTableDialog() }) { Text("Annuler") } }
                )
            }

            // CANCEL ORDER CONFIRMATION
            if (uiState.isCancelDialogOpen) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeCancelDialog() },
                    title = { Text("Annuler la vente") },
                    text = {
                        Column {
                            uiState.errorMessage?.let { error ->
                                Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text("Cette vente en attente sera annulée. Une autorisation propriétaire est requise.")
                            Spacer(modifier = Modifier.height(12.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = uiState.cancellationReasonInput,
                                onValueChange = { viewModel.updateCancellationReason(it) },
                                label = { Text("Motif obligatoire") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.ownerPinInput,
                                onValueChange = viewModel::updateOwnerPin,
                                label = { Text("PIN propriétaire") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitCancelOrder() },
                            enabled = !uiState.isLoading && uiState.cancellationReasonInput.isNotBlank() && uiState.ownerPinInput.length in 4..6,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Annuler la vente", color = Color.White)
                        }
                    },
                    dismissButton = { OutlinedButton(onClick = { viewModel.closeCancelDialog() }) { Text("Annuler") } }
                )
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(
    order: Order,
    tableName: String?,
    onResume: () -> Unit,
    onPay: () -> Unit,
    onMoveTable: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 286.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.orderNumber,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = MonetaryUtils.formatDh(order.totalCentimes),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE63946),
                    maxLines = 1,
                    softWrap = false
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFE2E8F0)
            )

            OrderInfoRow(
                label = "Type",
                value = when (order.type) {
                    OrderType.DINE_IN -> "Sur place"
                    OrderType.TAKEAWAY -> "À emporter"
                    OrderType.COUNTER -> "Comptoir"
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderInfoRow(
                label = "Table",
                value = if (order.type == OrderType.DINE_IN) {
                    tableName?.takeIf { it.isNotBlank() } ?: "Sans table"
                } else {
                    "—"
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderInfoRow(
                label = "Créée le",
                value = formatOrderCreationDateTime(order.createdAt)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OrderInfoRow(
                label = "Articles",
                value = order.items.sumOf { it.quantity }.toString()
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPay,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F))
                ) {
                    Text(
                        text = "Régler",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Modifier", maxLines = 1, softWrap = false)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (order.type == OrderType.DINE_IN) {
                    OutlinedButton(
                        onClick = onMoveTable,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Déplacer", maxLines = 1, softWrap = false)
                    }
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Annuler",
                        color = Color(0xFFE63946),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B),
            maxLines = 1
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1D3557),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatOrderCreationDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
}

