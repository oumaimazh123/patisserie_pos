package ma.elaroui.pos.presentation.pos.main

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.CartItem
import ma.elaroui.pos.domain.model.Category
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Product
import ma.elaroui.pos.domain.model.RestaurantTable
import ma.elaroui.pos.domain.model.TableStatus
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions
import ma.elaroui.pos.presentation.management.products.SafeProductImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun POSMainScreen(
    viewModel: POSMainViewModel,
    onNavigateToActiveOrders: () -> Unit,
    onNavigateToSessionDetails: () -> Unit,
    onNavigateToPayment: (Long) -> Unit,
    onLockPos: () -> Unit,
    onSwitchUser: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current
    var compactSection by rememberSaveable { mutableIntStateOf(0) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPopularCategories()
    }

    val filteredProducts = filterPosProducts(
        products = uiState.products,
        searchQuery = uiState.searchQuery,
        selectedCategoryId = uiState.selectedCategoryId,
        productScores = uiState.productScores
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Caisse POS", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Caissier: ${viewModel.sessionManager.currentUser?.userName ?: ""}",
                            fontSize = 14.sp,
                            color = Color(0xFFA8DADC)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToActiveOrders) {
                        Text("📋 Ventes en attente", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onNavigateToSessionDetails) {
                        Text("Session", color = Color.White)
                    }
                    TextButton(onClick = onLockPos) {
                        Text("🔒 Verrouiller", color = Color.White)
                    }
                    TextButton(onClick = onSwitchUser) {
                        Text("Changer User", color = Color.White)
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
        ) {
            if (adaptive.isCompact) {
                PrimaryTabRow(selectedTabIndex = compactSection) {
                    Tab(
                        selected = compactSection == 0,
                        onClick = { compactSection = 0 },
                        text = { Text("Produits") }
                    )
                    Tab(
                        selected = compactSection == 1,
                        onClick = { compactSection = 1 },
                        text = { Text("Panier (${uiState.cartState.items.size})") }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxSize()) {
            // LEFT COLUMN: Catalogue & Categories (Weight 0.65)
            if (!adaptive.isCompact || compactSection == 0) {
            Column(
                modifier = Modifier
                    .then(if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier.weight(0.65f))
                    .fillMaxHeight()
                    .padding(adaptive.screenPadding)
            ) {
                val selectedCategoryName = uiState.categories
                    .firstOrNull { it.id == uiState.selectedCategoryId }
                    ?.name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = { Text("Rechercher un produit...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    )
                    Button(
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 132.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            text = selectedCategoryName?.let { "Catégories · $it" } ?: "Catégories",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uiState.selectedCategoryId != null) {
                        Surface(
                            onClick = { viewModel.selectCategory(null) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFE4E6),
                            border = BorderStroke(1.dp, Color(0xFFE63946)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Box(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "✕ Tous",
                                    color = Color(0xFFE63946),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = uiState.categories,
                        categoryScores = uiState.categoryScores,
                        popularCategoryIds = uiState.popularCategoryIds,
                        selectedCategoryId = uiState.selectedCategoryId,
                        onSelect = { categoryId ->
                            viewModel.selectCategory(categoryId)
                            showCategoryPicker = false
                        },
                        onDismiss = { showCategoryPicker = false }
                    )
                }

                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aucun produit disponible.", fontSize = 18.sp, color = Color(0xFF64748B))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptive.generalCardMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts) { product ->
                            POSProductCard(product = product, onClick = { viewModel.addProductToCart(product) })
                        }
                    }
                }
            }
            }

            // RIGHT COLUMN: Live Ticket Cart (Weight 0.35)
            if (!adaptive.isCompact || compactSection == 1) {
            Surface(
                modifier = Modifier
                    .then(
                        if (adaptive.isCompact) Modifier.fillMaxWidth()
                        else Modifier.weight(0.35f).widthIn(min = 320.dp, max = 480.dp)
                    )
                    .fillMaxHeight(),
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (uiState.editingOrderNumber != null) "Modifier la vente ${uiState.editingOrderNumber}" else "Nouvelle vente",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D3557)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Vente comptoir · aucun client ni table requis",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cart Items List
                    if (uiState.cartState.items.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Panier vide. Cliquez sur un produit pour l'ajouter.", color = Color(0xFF64748B))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiState.cartState.items) { index, item ->
                                POSCartLineItem(
                                    item = item,
                                    onQtyDelta = { delta -> viewModel.updateCartItemQuantity(index, delta) },
                                    onRemove = { viewModel.removeCartItem(index) },
                                    onAddNote = { viewModel.openNoteDialog(item) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Remise", fontSize = 12.sp, color = Color(0xFF64748B))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(0, 500, 1000)) { basisPoints ->
                            FilterChip(
                                selected = uiState.cartState.discountBasisPoints == basisPoints,
                                onClick = { viewModel.setDiscountBasisPoints(basisPoints) },
                                label = { Text("${basisPoints / 100}%") }
                            )
                        }
                    }
                    if (uiState.cartState.discountCentimes > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sous-total", color = Color(0xFF64748B))
                            Text(MonetaryUtils.formatDh(uiState.cartState.subtotalCentimes))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Remise", color = Color(0xFF64748B))
                            Text("-${MonetaryUtils.formatDh(uiState.cartState.discountCentimes)}", color = Color(0xFFE63946))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total TTC:", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                        Text(MonetaryUtils.formatDh(uiState.cartState.totalCentimes), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                    }

                    uiState.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, color = Color(0xFFE63946), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.clearCart() },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.cartState.items.isNotEmpty()
                        ) {
                            Text("Vider")
                        }
                        Button(
                            onClick = { viewModel.submitSaveOrder() },
                            modifier = Modifier.weight(2f),
                            enabled = uiState.cartState.items.isNotEmpty() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text(if (uiState.editingOrderId == null) "Passer au paiement" else "Mettre à jour", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            }
            }
        }

        // Table Picker Modal
        if (uiState.isTablePickerOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeTablePicker() },
                title = { Text("Sélectionner une Table Disponible") },
                text = {
                    Column {
                        OutlinedButton(
                            onClick = { viewModel.clearDineInTable() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continuer sans table")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val availableTables = uiState.tables.filter { it.active && it.status == TableStatus.AVAILABLE }
                        if (availableTables.isEmpty()) {
                            Text("Aucune table disponible actuellement.", color = Color(0xFFE63946))
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(250.dp)
                            ) {
                                items(availableTables) { table ->
                                    Button(
                                        onClick = { viewModel.selectDineInTable(table) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3557))
                                    ) {
                                        Text(table.name, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { OutlinedButton(onClick = { viewModel.closeTablePicker() }) { Text("Fermer") } }
            )
        }

        // Item Note Modal
        if (uiState.isNoteDialogOpen) {
            var noteText by remember { mutableStateOf(uiState.itemNoteInput) }
            AlertDialog(
                onDismissRequest = { viewModel.closeNoteDialog() },
                title = { Text("Ajouter une Remarque à l'Article") },
                text = {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Remarque article (optionnelle)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.saveCartItemNote(noteText) }) { Text("Valider") }
                },
                dismissButton = { OutlinedButton(onClick = { viewModel.closeNoteDialog() }) { Text("Annuler") } }
            )
        }

        // Success Event Dialog
        uiState.orderCreatedSuccess?.let { order ->
            AlertDialog(
                onDismissRequest = { viewModel.clearOrderSuccessEvent() },
                title = { Text("Vente enregistrée ✓") },
                text = { Text("Vente ${order.orderNumber} prête à être encaissée.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetId = order.id
                            viewModel.clearOrderSuccessEvent()
                            onNavigateToPayment(targetId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F))
                    ) {
                        Text("Passer au paiement")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearOrderSuccessEvent() }) {
                        Text("Suspendre la vente")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    categoryScores: Map<Long, Long> = emptyMap(),
    popularCategoryIds: List<Long> = emptyList(),
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedCategories = remember(categories, categoryScores, popularCategoryIds) {
        val effectiveScores = if (categoryScores.isNotEmpty()) {
            categoryScores
        } else {
            popularCategoryIds.mapIndexed { index, id -> id to (popularCategoryIds.size - index).toLong() }.toMap()
        }
        categories.filter { it.active }.sortedWith(
            compareByDescending<Category> { effectiveScores[it.id] ?: 0L }
                .thenBy { it.displayOrder }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choisir une catégorie", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Text("✕", fontSize = 18.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryPickerButton("Tous les produits", selectedCategoryId == null) { onSelect(null) }
                    sortedCategories.forEach { category ->
                        CategoryPickerButton(category.name, selectedCategoryId == category.id) {
                            onSelect(category.id)
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Fermer")
            }
        }
    )
}

@Composable
private fun CategoryPickerButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF1D3557) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (selected) Color(0xFF1D3557) else Color(0xFFCBD5E1)),
        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 132.dp)
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else Color(0xFF1D3557),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrderTypeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFE63946) else Color(0xFFF1F5F9),
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else Color(0xFF1D3557), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun POSProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("pos_product_${product.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SafeProductImage(
                imagePath = product.imagePath,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    product.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    MonetaryUtils.formatDh(product.priceCentimes).replace('.', ','),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE63946),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun POSCartLineItem(
    item: CartItem,
    onQtyDelta: (Int) -> Unit,
    onRemove: () -> Unit,
    onAddNote: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1D3557), modifier = Modifier.weight(1f))
                Text(MonetaryUtils.formatDh(item.lineTotalCentimes), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFE63946))
            }
            item.note?.let { note ->
                Text("Note: $note", fontSize = 12.sp, color = Color(0xFF64748B))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddNote) { Text(if (item.note.isNull_or_blank()) "+ Note" else "Edit Note", fontSize = 11.sp) }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onQtyDelta(-1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) { Text("-") }
                Text("${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onQtyDelta(1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
