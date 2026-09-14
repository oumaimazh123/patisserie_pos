package ma.elaroui.pos.presentation.reports

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import ma.elaroui.pos.domain.model.DailySalesReport
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.ProductSalesSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesScreen(
    viewModel: DailySalesViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri) }
                .onSuccess { outputStream ->
                    if (outputStream != null) {
                        // The ViewModel owns and closes this stream after its coroutine writes.
                        viewModel.exportCsv(outputStream)
                    } else {
                        viewModel.reportExportError(
                            "Impossible d'ouvrir le fichier choisi pour l'exportation."
                        )
                    }
                }
                .onFailure { error ->
                    viewModel.reportExportError(
                        "Impossible de créer le fichier CSV : ${error.localizedMessage ?: "erreur inconnue"}"
                    )
                }
        }
    }

    DailySalesReportContent(
        uiState = uiState,
        onBack = onBack,
        onChooseDate = {
            val selected = Calendar.getInstance().apply {
                timeInMillis = uiState.selectedDateMs
            }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val localDate = Calendar.getInstance().apply {
                        clear()
                        set(year, month, day, 12, 0, 0)
                    }
                    viewModel.selectDate(localDate.timeInMillis)
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        },
        onPreviousDay = viewModel::selectPreviousDay,
        onNextDay = viewModel::selectNextDay,
        onToday = viewModel::selectToday,
        onRefresh = viewModel::loadReport,
        onExport = {
            exportLauncher.launch(
                "rapport_ventes_${fileDate(uiState.selectedDateMs)}.csv"
            )
        },
        onClearError = viewModel::clearErrorMessage,
        onClearExportMessage = viewModel::clearExportMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesReportContent(
    uiState: DailySalesUiState,
    onBack: () -> Unit,
    onChooseDate: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onClearError: () -> Unit,
    onClearExportMessage: () -> Unit
) {
    val report = uiState.report
    val canGoNext = startOfDay(uiState.selectedDateMs) < startOfDay(System.currentTimeMillis())

    Scaffold(
        modifier = Modifier.testTag("sales_report_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Rapport des ventes",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLoading && !uiState.isExporting,
                        modifier = Modifier.testTag("sales_report_refresh_button")
                    ) {
                        Text("Actualiser")
                    }
                    Button(
                        onClick = onExport,
                        enabled = report != null && !uiState.isLoading && !uiState.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F)),
                        modifier = Modifier.testTag("sales_report_export_button")
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Exporter CSV", color = Color.White, fontWeight = FontWeight.Bold)
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
                .testTag("sales_report_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateNavigationCard(
                    selectedDateMs = uiState.selectedDateMs,
                    canGoNext = canGoNext,
                    onChooseDate = onChooseDate,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onToday = onToday
                )
            }

            uiState.errorMessage?.let { error ->
                item {
                    MessageCard(
                        message = error,
                        backgroundColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFC62828),
                        actionLabel = if (report == null) "Réessayer" else "Fermer",
                        onAction = if (report == null) onRefresh else onClearError
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Calcul du rapport en cours…", color = Color(0xFF64748B))
                            }
                        }
                    }
                }

                report == null -> {
                    item {
                        EmptyState(
                            title = "Rapport indisponible",
                            description = "Actualisez la page pour réessayer."
                        )
                    }
                }

                else -> {
                    item { SummaryGrid(report) }

                    if (report.completedOrderCount == 0) {
                        item {
                            EmptyState(
                                title = "Aucune vente clôturée",
                                description = "Aucune commande payée n'a été trouvée pour cette date."
                            )
                        }
                    }

                    item { SectionTitle("Ventes par catégorie") }
                    val categoryRows = report.salesByCategory.entries.sortedByDescending { it.value }
                    if (categoryRows.isEmpty()) {
                        item { SectionEmptyText("Aucune vente à répartir.") }
                    } else {
                        items(categoryRows, key = { it.key }) { entry ->
                            AmountRow(
                                title = entry.key,
                                subtitle = "Chiffre d'affaires",
                                valueCentimes = entry.value,
                                valueColor = Color(0xFF457B9D)
                            )
                        }
                    }

                    item { SectionTitle("Ventes par caissier") }
                    val cashierRows = report.salesByCashier.entries
                        .sortedByDescending { it.value }
                    if (cashierRows.isEmpty()) {
                        item { SectionEmptyText("Aucune vente par caissier pour cette date.") }
                    } else {
                        items(cashierRows, key = { it.key }) { entry ->
                            AmountRow(
                                title = entry.key,
                                subtitle = "Total encaissé",
                                valueCentimes = entry.value,
                                valueColor = Color(0xFF2A9D8F)
                            )
                        }
                    }

                    item { SectionTitle("Top produits vendus") }
                    if (report.topProducts.isEmpty()) {
                        item { SectionEmptyText("Aucun produit vendu pour cette date.") }
                    } else {
                        items(report.topProducts, key = { it.productName }) { product ->
                            ProductRow(product)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    uiState.exportSuccessMessage?.let { message ->
        androidx.compose.material3.AlertDialog(
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
private fun DateNavigationCard(
    selectedDateMs: Long,
    canGoNext: Boolean,
    onChooseDate: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPreviousDay,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.testTag("sales_report_previous_day")
            ) {
                Text("‹", fontSize = 24.sp)
            }
            OutlinedButton(
                onClick = onChooseDate,
                modifier = Modifier
                    .weight(1f)
                    .testTag("sales_report_date_button")
            ) {
                Text(
                    text = displayDate(selectedDateMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onToday) {
                Text("Aujourd'hui", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onNextDay,
                enabled = canGoNext,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.testTag("sales_report_next_day")
            ) {
                Text("›", fontSize = 24.sp)
            }
        }
    }
}

private data class SummaryValue(
    val title: String,
    val value: String,
    val subtitle: String,
    val color: Color
)

@Composable
private fun SummaryGrid(report: DailySalesReport) {
    val values = buildList {
        add(
            SummaryValue(
                "Total ventes",
                MonetaryUtils.formatDh(report.totalSalesCentimes),
                "${report.completedOrderCount} commande(s) clôturée(s)",
                Color(0xFF1D3557)
            )
        )
        add(
            SummaryValue(
                "Espèces",
                MonetaryUtils.formatDh(report.cashSalesCentimes),
                "Paiements cash",
                Color(0xFF2A9D8F)
            )
        )
        add(
            SummaryValue(
                "Carte / TPE",
                MonetaryUtils.formatDh(report.cardSalesCentimes),
                "Paiements carte",
                Color(0xFF457B9D)
            )
        )
        if (report.otherSalesCentimes > 0L) {
            add(
                SummaryValue(
                    "Autres paiements",
                    MonetaryUtils.formatDh(report.otherSalesCentimes),
                    "Mobile ou carnet",
                    Color(0xFF6D597A)
                )
            )
        }
        add(
            SummaryValue(
                "Panier moyen",
                MonetaryUtils.formatDh(report.avgOrderValueCentimes),
                "Moyenne par commande",
                Color(0xFFE76F51)
            )
        )
        add(
            SummaryValue(
                "Annulations",
                report.cancelledOrderCount.toString(),
                "Commande(s) annulée(s)",
                Color(0xFFE63946)
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 700.dp) 3 else 2
        val rows = (values.size + columns - 1) / columns
        val gridHeight = (rows * 112 + (rows - 1) * 12).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(values, key = { it.title }) { summary ->
                ReportCard(summary)
            }
        }
    }
}

@Composable
private fun ReportCard(summary: SummaryValue) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = summary.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(summary.title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            Text(
                summary.value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                summary.subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D3557)
        )
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0))
    }
}

@Composable
private fun AmountRow(
    title: String,
    subtitle: String,
    valueCentimes: Long,
    valueColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Text(
                MonetaryUtils.formatDh(valueCentimes),
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun ProductRow(product: ProductSalesSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.productName,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557)
                )
                Text(
                    "Quantité vendue : ${product.quantitySold}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            Text(
                MonetaryUtils.formatDh(product.totalSalesCentimes),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE63946)
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    backgroundColor: Color,
    contentColor: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            TextButton(onClick = onAction) {
                Text(actionLabel, color = contentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_report_empty_state")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun SectionEmptyText(message: String) {
    Text(
        message,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun orderTypeLabel(type: OrderType): String = when (type) {
    OrderType.DINE_IN -> "Sur place"
    OrderType.TAKEAWAY -> "À emporter"
    OrderType.COUNTER -> "Comptoir"
}

private fun displayDate(dateMs: Long): String =
    SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH).format(Date(dateMs))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }

private fun fileDate(dateMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMs))

private fun startOfDay(dateMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dateMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
