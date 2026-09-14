package ma.elaroui.pos.presentation.sales

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.PaymentStatus
import ma.elaroui.pos.domain.model.SalesHistoryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CompletedSalesScreen(
    viewModel: CompletedSalesViewModel,
    onSelectSale: (Long) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri) }
                .onSuccess { stream ->
                    if (stream == null) {
                        viewModel.reportExportError("Impossible d'ouvrir le fichier choisi.")
                    } else {
                        viewModel.exportCsv(stream)
                    }
                }
                .onFailure {
                    viewModel.reportExportError(
                        "Impossible de créer le fichier CSV : ${
                            it.localizedMessage ?: "erreur inconnue"
                        }"
                    )
                }
        }
    }

    CompletedSalesContent(
        uiState = uiState,
        onSelectSale = onSelectSale,
        onBack = onBack,
        onRefresh = viewModel::loadHistory,
        onExport = {
            exportLauncher.launch(
                "historique_ventes_${fileDate(uiState.startDateMs)}_${
                    fileDate(uiState.endDateMs)
                }.csv"
            )
        },
        onSelectStatus = viewModel::selectStatus,
        onChooseStartDate = {
            showDatePicker(context, uiState.startDateMs, viewModel::selectStartDate)
        },
        onChooseEndDate = {
            showDatePicker(context, uiState.endDateMs, viewModel::selectEndDate)
        },
        onCurrentMonth = viewModel::selectCurrentMonth,
        onSearch = viewModel::updateSearchQuery,
        onSelectCashier = viewModel::selectCashier,
        onSelectRegister = viewModel::selectRegister,
        onSelectPayment = viewModel::selectPaymentMethod,
        onSelectOrderType = viewModel::selectOrderType,
        onClearFilters = viewModel::clearFilters,
        onLoadMore = viewModel::loadMore,
        onClearError = viewModel::clearErrorMessage,
        onClearExportMessage = viewModel::clearExportMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedSalesContent(
    uiState: CompletedSalesUiState,
    onSelectSale: (Long) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onSelectStatus: (OrderStatus) -> Unit,
    onChooseStartDate: () -> Unit,
    onChooseEndDate: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectCashier: (Long?) -> Unit,
    onSelectRegister: (Long?) -> Unit,
    onSelectPayment: (PaymentMethod?) -> Unit,
    onSelectOrderType: (OrderType?) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onClearError: () -> Unit,
    onClearExportMessage: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("sales_history_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isOwner) {
                            "Historique global des ventes"
                        } else {
                            "Ventes de la session"
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Navy, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLoading && !uiState.isExporting
                    ) {
                        Text("Actualiser")
                    }
                    Button(
                        onClick = onExport,
                        enabled = uiState.filteredCount > 0 &&
                            !uiState.isLoading &&
                            !uiState.isExporting,
                        modifier = Modifier.testTag("sales_history_export"),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("CSV", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("sales_history_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusSelector(
                    selected = uiState.status,
                    onSelect = onSelectStatus
                )
            }

            item {
                DateRangeCard(
                    startDateMs = uiState.startDateMs,
                    endDateMs = uiState.endDateMs,
                    onChooseStartDate = onChooseStartDate,
                    onChooseEndDate = onChooseEndDate,
                    onCurrentMonth = onCurrentMonth
                )
            }

            item {
                SummaryGrid(uiState)
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearch,
                    label = { Text("Commande, caissier ou caisse") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_history_search"),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                FiltersCard(
                    uiState = uiState,
                    onSelectCashier = onSelectCashier,
                    onSelectRegister = onSelectRegister,
                    onSelectPayment = onSelectPayment,
                    onSelectOrderType = onSelectOrderType,
                    onClearFilters = onClearFilters
                )
            }

            uiState.errorMessage?.let { error ->
                item {
                    MessageCard(error, onClearError, onRefresh)
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(10.dp))
                                Text("Chargement de l'historique…", color = Slate)
                            }
                        }
                    }
                }

                uiState.entries.isEmpty() -> {
                    item {
                        EmptyHistory(status = uiState.status)
                    }
                }

                else -> {
                    item {
                        Text(
                            "${uiState.filteredCount} résultat(s)",
                            color = Slate,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("sales_history_result_count")
                        )
                    }
                    items(uiState.entries, key = { it.order.id }) { entry ->
                        SalesHistoryCard(
                            entry = entry,
                            onClick = { onSelectSale(entry.order.id) }
                        )
                    }
                    if (uiState.hasMore) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sales_history_load_more")
                            ) {
                                Text(
                                    "Charger plus (${
                                        uiState.filteredCount - uiState.entries.size
                                    } restant(s))"
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    uiState.exportSuccessMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onClearExportMessage,
            title = { Text("Exportation réussie ✓") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onClearExportMessage) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun StatusSelector(
    selected: OrderStatus,
    onSelect: (OrderStatus) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusButton(
            label = "Ventes clôturées",
            selected = selected == OrderStatus.COMPLETED,
            modifier = Modifier
                .weight(1f)
                .testTag("sales_history_completed_tab"),
            onClick = { onSelect(OrderStatus.COMPLETED) }
        )
        StatusButton(
            label = "Annulations",
            selected = selected == OrderStatus.CANCELLED,
            modifier = Modifier
                .weight(1f)
                .testTag("sales_history_cancelled_tab"),
            onClick = { onSelect(OrderStatus.CANCELLED) }
        )
    }
}

@Composable
private fun StatusButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Navy)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DateRangeCard(
    startDateMs: Long,
    endDateMs: Long,
    onChooseStartDate: () -> Unit,
    onChooseEndDate: () -> Unit,
    onCurrentMonth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (maxWidth >= 640.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateButton("Du", startDateMs, onChooseStartDate, Modifier.weight(1f))
                    DateButton("Au", endDateMs, onChooseEndDate, Modifier.weight(1f))
                    TextButton(onClick = onCurrentMonth) {
                        Text("Mois en cours", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton("Du", startDateMs, onChooseStartDate, Modifier.fillMaxWidth())
                    DateButton("Au", endDateMs, onChooseEndDate, Modifier.fillMaxWidth())
                    TextButton(onClick = onCurrentMonth, modifier = Modifier.align(Alignment.End)) {
                        Text("Mois en cours", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateButton(
    prefix: String,
    dateMs: Long,
    onClick: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(
            "$prefix ${displayDate(dateMs)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class SummaryCardValue(
    val title: String,
    val value: String,
    val subtitle: String,
    val color: Color
)

@Composable
private fun SummaryGrid(uiState: CompletedSalesUiState) {
    val summary = uiState.summary
    val values = if (uiState.status == OrderStatus.CANCELLED) {
        listOf(
            SummaryCardValue(
                "Commandes annulées",
                summary.orderCount.toString(),
                "Hors chiffre d'affaires",
                Red
            ),
            SummaryCardValue(
                "Valeur annulée",
                MonetaryUtils.formatDh(summary.totalCentimes),
                "Montant non encaissé",
                Color(0xFFE76F51)
            )
        )
    } else {
        listOf(
            SummaryCardValue(
                "Total ventes",
                MonetaryUtils.formatDh(summary.totalCentimes),
                "${summary.orderCount} commande(s)",
                Navy
            ),
            SummaryCardValue(
                "Espèces",
                MonetaryUtils.formatDh(summary.cashCentimes),
                "Paiements cash",
                Teal
            ),
            SummaryCardValue(
                "Carte / TPE",
                MonetaryUtils.formatDh(summary.cardCentimes),
                "Paiements carte",
                Color(0xFF457B9D)
            ),
            SummaryCardValue(
                "Panier moyen",
                MonetaryUtils.formatDh(summary.averageCentimes),
                "Par commande",
                Color(0xFFE76F51)
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 900.dp -> values.size
            maxWidth >= 600.dp -> 2
            else -> 2
        }.coerceAtLeast(1)
        val rows = (values.size + columns - 1) / columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height((rows * 104 + (rows - 1) * 10).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(values, key = { it.title }) { value ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = value.color),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(104.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(value.title, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp)
                        Text(
                            value.value,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            value.subtitle,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersCard(
    uiState: CompletedSalesUiState,
    onSelectCashier: (Long?) -> Unit,
    onSelectRegister: (Long?) -> Unit,
    onSelectPayment: (PaymentMethod?) -> Unit,
    onSelectOrderType: (OrderType?) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Filtres${if (uiState.activeFilterCount > 0) " (${uiState.activeFilterCount})" else ""}",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                TextButton(
                    onClick = onClearFilters,
                    enabled = uiState.activeFilterCount > 0 || uiState.searchQuery.isNotBlank()
                ) {
                    Text("Effacer")
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 700.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.isOwner) {
                            OptionDropdown(
                                label = "Caissier",
                                selected = uiState.cashierOptions
                                    .firstOrNull { it.id == uiState.selectedCashierId }?.label,
                                options = uiState.cashierOptions.map { it.id to it.label },
                                onSelect = onSelectCashier,
                                modifier = Modifier.weight(1f)
                            )
                            OptionDropdown(
                                label = "Caisse",
                                selected = uiState.registerOptions
                                    .firstOrNull { it.id == uiState.selectedRegisterId }?.label,
                                options = uiState.registerOptions.map { it.id to it.label },
                                onSelect = onSelectRegister,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PaymentDropdown(
                            selected = uiState.selectedPaymentMethod,
                            onSelect = onSelectPayment,
                            modifier = Modifier.weight(1f)
                        )
                        OrderTypeDropdown(
                            selected = uiState.selectedOrderType,
                            onSelect = onSelectOrderType,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.isOwner) {
                            OptionDropdown(
                                label = "Caissier",
                                selected = uiState.cashierOptions
                                    .firstOrNull { it.id == uiState.selectedCashierId }?.label,
                                options = uiState.cashierOptions.map { it.id to it.label },
                                onSelect = onSelectCashier,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OptionDropdown(
                                label = "Caisse",
                                selected = uiState.registerOptions
                                    .firstOrNull { it.id == uiState.selectedRegisterId }?.label,
                                options = uiState.registerOptions.map { it.id to it.label },
                                onSelect = onSelectRegister,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        PaymentDropdown(
                            selected = uiState.selectedPaymentMethod,
                            onSelect = onSelectPayment,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OrderTypeDropdown(
                            selected = uiState.selectedOrderType,
                            onSelect = onSelectOrderType,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionDropdown(
    label: String,
    selected: String?,
    options: List<Pair<Long, String>>,
    onSelect: (Long?) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected ?: "$label : Tous",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Tous") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { (id, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PaymentDropdown(
    selected: PaymentMethod?,
    onSelect: (PaymentMethod?) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let(::paymentMethodLabel) ?: "Paiement : Tous",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Tous") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            PaymentMethod.values().forEach { method ->
                DropdownMenuItem(
                    text = { Text(paymentMethodLabel(method)) },
                    onClick = {
                        onSelect(method)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OrderTypeDropdown(
    selected: OrderType?,
    onSelect: (OrderType?) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let(::orderTypeLabel) ?: "Type : Tous",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Tous") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            OrderType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(orderTypeLabel(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SalesHistoryCard(
    entry: SalesHistoryEntry,
    onClick: () -> Unit
) {
    val order = entry.order
    val cancelled = order.status == OrderStatus.CANCELLED
    val eventAt = if (cancelled) {
        order.cancelledAt ?: order.updatedAt
    } else {
        order.completedAt ?: order.updatedAt
    }
    val methods = order.payments
        .filter { it.status == PaymentStatus.COMPLETED }
        .map { paymentMethodLabel(it.method) }
        .distinct()
        .joinToString(" + ")
        .ifBlank { "—" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sales_history_order_${order.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (maxWidth >= 650.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrderIdentity(entry, eventAt, Modifier.weight(1.4f))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.cashierName, fontWeight = FontWeight.SemiBold, color = Navy)
                        Text(entry.registerName, color = Slate, fontSize = 12.sp)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            MonetaryUtils.formatDh(order.totalCentimes),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cancelled) Red else Teal
                        )
                        Text(
                            if (cancelled) "Annulée" else methods,
                            color = if (cancelled) Red else Slate,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderIdentity(entry, eventAt, Modifier.fillMaxWidth())
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.cashierName, fontWeight = FontWeight.SemiBold, color = Navy)
                            Text(entry.registerName, color = Slate, fontSize = 12.sp)
                            Text(
                                if (cancelled) "Annulée" else methods,
                                color = if (cancelled) Red else Slate,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            MonetaryUtils.formatDh(order.totalCentimes),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cancelled) Red else Teal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderIdentity(
    entry: SalesHistoryEntry,
    eventAt: Long,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Text(
            entry.order.orderNumber,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Navy
        )
        Text(
            "${orderTypeLabel(entry.order.type)} • ${displayDateTime(eventAt)}",
            fontSize = 12.sp,
            color = Slate
        )
        if (entry.order.status == OrderStatus.CANCELLED) {
            Text(
                entry.order.cancellationReason?.takeIf { it.isNotBlank() }
                    ?: "Motif non renseigné",
                fontSize = 12.sp,
                color = Red,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFFC62828),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Fermer") }
            TextButton(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

@Composable
private fun EmptyHistory(status: OrderStatus) {
    Surface(
        color = LightBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_history_empty")
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (status == OrderStatus.CANCELLED) {
                    "Aucune commande annulée"
                } else {
                    "Aucune vente clôturée"
                },
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Modifiez les dates ou effacez les filtres.",
                color = Slate
            )
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    selectedDateMs: Long,
    onSelected: (Long) -> Unit
) {
    val selected = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onSelected(
                Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 12, 0, 0)
                }.timeInMillis
            )
        },
        selected.get(Calendar.YEAR),
        selected.get(Calendar.MONTH),
        selected.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

private fun orderTypeLabel(type: OrderType): String = when (type) {
    OrderType.DINE_IN -> "Sur place"
    OrderType.TAKEAWAY -> "À emporter"
    OrderType.COUNTER -> "Comptoir"
}

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Espèces"
    PaymentMethod.CARD -> "Carte / TPE"
    PaymentMethod.CARNET_CLIENT -> "Carnet client"
    PaymentMethod.MOBILE_QR -> "Paiement mobile"
}

private fun displayDate(dateMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(Date(dateMs))

private fun displayDateTime(dateMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(Date(dateMs))

private fun fileDate(dateMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMs))

private val Navy = Color(0xFF1D3557)
private val Teal = Color(0xFF2A9D8F)
private val Red = Color(0xFFE63946)
private val Slate = Color(0xFF64748B)
private val LightBackground = Color(0xFFF8F9FA)
