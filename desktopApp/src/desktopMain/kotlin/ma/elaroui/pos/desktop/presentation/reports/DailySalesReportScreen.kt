package ma.elaroui.pos.desktop.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesSummary
import ma.elaroui.pos.desktop.persistence.RetailSalesAnalytics
import ma.elaroui.pos.desktop.persistence.SalesBreakdown
import ma.elaroui.pos.desktop.persistence.SalesEvolutionPoint
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchDatePickerModal
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.touchHorizontalDragScroll
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.User

import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategoryTreeNode
import ma.elaroui.pos.shared.rules.MoneyRules

private data class LoadedReport(
    val summary: SalesSummary,
    val analytics: RetailSalesAnalytics,
    val evolution: List<SalesEvolutionPoint>
)

private data class FlatCategoryDisplay(val category: Category, val depth: Int)

private fun flattenCategoryTree(nodes: List<CategoryTreeNode>, depth: Int = 0): List<FlatCategoryDisplay> {
    val result = mutableListOf<FlatCategoryDisplay>()
    for (node in nodes) {
        result.add(FlatCategoryDisplay(node.category, depth))
        result.addAll(flattenCategoryTree(node.children, depth + 1))
    }
    return result
}

private fun orderTypeDisplayName(type: OrderType?, strings: DesktopStrings): String = when (type) {
    null -> strings.allOrderTypes
    OrderType.COUNTER -> strings.text("☕ Comptoir", "☕ Counter", "☕ بالصندوق")
    OrderType.TAKEAWAY -> strings.text("🛍️ À emporter", "🛍️ Takeaway", "🛍️ سفري")
    OrderType.DINE_IN -> strings.text("🍽️ Sur place", "🍽️ Dine In", "🍽️ محلي")
    OrderType.PREORDER -> strings.text("🎂 Précommande", "🎂 Pre-order", "🎂 طلب مسبق")
}

@Composable
fun DailySalesReportScreen(
    summaryForRange: (fromEpoch: Long, toEpoch: Long, cashierId: Long?, orderType: OrderType?, matchingCategoryIds: Set<Long>?) -> SalesSummary,
    analyticsForRange: (fromEpoch: Long, toEpoch: Long, cashierId: Long?, orderType: OrderType?, matchingCategoryIds: Set<Long>?) -> RetailSalesAnalytics,
    evolutionForRange: (fromEpoch: Long, toEpoch: Long, isHourly: Boolean, cashierId: Long?, orderType: OrderType?, matchingCategoryIds: Set<Long>?) -> List<SalesEvolutionPoint> = { _, _, _, _, _, _ -> emptyList() },
    earliestSaleEpoch: (() -> Long?)? = null,
    cashiers: List<User> = emptyList(),
    categories: List<Category> = emptyList(),
    strings: DesktopStrings,
    onBack: (() -> Unit)? = null
) {
    val zone = remember { ZoneId.systemDefault() }
    val todayDate = remember { LocalDate.now(zone) }

    val minDate = remember(earliestSaleEpoch) {
        val epoch = earliestSaleEpoch?.invoke()
        if (epoch != null && epoch > 0L) {
            Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().coerceAtMost(todayDate)
        } else {
            todayDate
        }
    }

    var startDate by remember { mutableStateOf(todayDate.coerceAtLeast(minDate)) }
    var endDate by remember { mutableStateOf(todayDate) }
    var selectedPreset by remember { mutableStateOf(0) } // 0: Today, 1: Yesterday, 2: 7 Days, 3: Month, -1: Custom

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Secondary filters
    var selectedCashierId by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedOrderType by remember { mutableStateOf<OrderType?>(null) }

    var cashierDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var orderTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Category hierarchy resolution
    val matchingCategoryIds = remember(selectedCategoryId, categories) {
        if (selectedCategoryId == null) {
            null
        } else {
            val descendants = CategoryHierarchyRules.getAllDescendantIds(selectedCategoryId!!, categories)
            setOf(selectedCategoryId!!) + descendants
        }
    }

    val flatCategories = remember(categories) {
        val tree = CategoryHierarchyRules.buildCategoryTree(categories)
        flattenCategoryTree(tree)
    }

    val selectedCashier = remember(selectedCashierId, cashiers) {
        cashiers.firstOrNull { it.id == selectedCashierId }
    }
    val selectedCategory = remember(selectedCategoryId, categories) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }

    val isFilterActive = selectedCashierId != null || selectedCategoryId != null || selectedOrderType != null

    fun updatePresetForDates(s: LocalDate, e: LocalDate): Int {
        return when {
            s == todayDate.coerceAtLeast(minDate) && e == todayDate -> 0
            s == todayDate.minusDays(1).coerceAtLeast(minDate) && e == todayDate.minusDays(1).coerceAtLeast(minDate) -> 1
            s == todayDate.minusDays(6).coerceAtLeast(minDate) && e == todayDate -> 2
            s == todayDate.withDayOfMonth(1).coerceAtLeast(minDate) && e == todayDate -> 3
            else -> -1
        }
    }

    val isSingleDay = startDate == endDate
    val isHourly = isSingleDay

    val fromEpoch = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val toEpoch = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    val loadedReport by produceState<LoadedReport?>(null, fromEpoch, toEpoch, isHourly,
        selectedCashierId, selectedOrderType, matchingCategoryIds) {
        value = null
        value = withContext(Dispatchers.IO) {
            LoadedReport(
                summaryForRange(fromEpoch, toEpoch, selectedCashierId, selectedOrderType, matchingCategoryIds),
                analyticsForRange(fromEpoch, toEpoch, selectedCashierId, selectedOrderType, matchingCategoryIds),
                evolutionForRange(fromEpoch, toEpoch, isHourly, selectedCashierId, selectedOrderType, matchingCategoryIds)
            )
        }
    }
    if (loadedReport == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ManagementPageHeader(strings.reportsTitle, strings, onBack)
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator()
        }
        return
    }
    val summary = loadedReport!!.summary
    val analytics = loadedReport!!.analytics
    val evolutionPoints = loadedReport!!.evolution

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val totalSales = summary.salesCentimes
    val totalOrders = summary.completedOrders
    val averageBasketCentimes = if (totalOrders > 0) totalSales / totalOrders else 0L
    val netSalesCentimes = maxOf(0L, totalSales - summary.taxCentimes)
    val cashPercentage = if (totalSales > 0) ((summary.cashCentimes.toDouble() / totalSales) * 100).toInt() else 0
    val cardPercentage = if (totalSales > 0) ((summary.cardCentimes.toDouble() / totalSales) * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(strings.reportsTitle, strings, onBack)

        val reportScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .touchDragScroll(reportScrollState)
                .verticalScroll(reportScrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Period Presets & Date Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, PosColors.Border),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 260.dp,
                            horizontalSpacing = 12.dp,
                            verticalSpacing = 8.dp
                        ) {
                            val filterScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .touchHorizontalDragScroll(filterScrollState)
                                    .horizontalScroll(filterScrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    strings.today,
                                    strings.yesterday,
                                    strings.last7Days,
                                    strings.thisMonth
                                ).forEachIndexed { index, label ->
                                    val isSelected = selectedPreset == index
                                    Surface(
                                        onClick = {
                                            selectedPreset = index
                                            when (index) {
                                                0 -> {
                                                    startDate = todayDate.coerceAtLeast(minDate)
                                                    endDate = todayDate
                                                }
                                                1 -> {
                                                    val yesterday = todayDate.minusDays(1).coerceAtLeast(minDate)
                                                    startDate = yesterday
                                                    endDate = yesterday
                                                }
                                                2 -> {
                                                    startDate = todayDate.minusDays(6).coerceAtLeast(minDate)
                                                    endDate = todayDate
                                                }
                                                3 -> {
                                                    startDate = todayDate.withDayOfMonth(1).coerceAtLeast(minDate)
                                                    endDate = todayDate
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                        border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSelected) Color.White else PosColors.TextHigh,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Date Début Touch Card
                                Surface(
                                    onClick = { showStartDatePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.Center) {
                                            Text(
                                                strings.startDate,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = PosColors.TextMedium
                                            )
                                            Text(
                                                startDate.format(dateFormatter),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.BakeryBrown
                                            )
                                        }
                                        Text("📅", fontSize = 14.sp)
                                    }
                                }

                                Text("→", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosColors.TextLow)

                                // Date Fin Touch Card
                                Surface(
                                    onClick = { showEndDatePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.Center) {
                                            Text(
                                                strings.endDate,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = PosColors.TextMedium
                                            )
                                            Text(
                                                endDate.format(dateFormatter),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.BakeryBrown
                                            )
                                        }
                                        Text("📅", fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // Secondary Filter Row: Caissier, Catégorie, Type de vente
                        HorizontalDivider(color = PosColors.Border.copy(alpha = 0.6f))

                        val filterRowScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .touchHorizontalDragScroll(filterRowScrollState)
                                .horizontalScroll(filterRowScrollState),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Cashier Filter
                            Box {
                                FilterChipButton(
                                    icon = "👤",
                                    label = selectedCashier?.name ?: strings.allCashiers,
                                    isActive = selectedCashierId != null,
                                    onClick = { cashierDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = cashierDropdownExpanded,
                                    onDismissRequest = { cashierDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                strings.allCashiers,
                                                fontWeight = if (selectedCashierId == null) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedCashierId == null) PosColors.Primary else PosColors.TextHigh
                                            )
                                        },
                                        onClick = {
                                            selectedCashierId = null
                                            cashierDropdownExpanded = false
                                        }
                                    )
                                    cashiers.forEach { user ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    user.name,
                                                    fontWeight = if (selectedCashierId == user.id) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedCashierId == user.id) PosColors.Primary else PosColors.TextHigh
                                                )
                                            },
                                            onClick = {
                                                selectedCashierId = user.id
                                                cashierDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 2. Hierarchical Category Filter
                            Box {
                                FilterChipButton(
                                    icon = "📁",
                                    label = selectedCategory?.name ?: strings.allCategories,
                                    isActive = selectedCategoryId != null,
                                    onClick = { categoryDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = categoryDropdownExpanded,
                                    onDismissRequest = { categoryDropdownExpanded = false },
                                    modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                strings.allCategories,
                                                fontWeight = if (selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedCategoryId == null) PosColors.Primary else PosColors.TextHigh
                                            )
                                        },
                                        onClick = {
                                            selectedCategoryId = null
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                    flatCategories.forEach { item ->
                                        val indent = "    ".repeat(item.depth)
                                        val prefix = if (item.depth == 0) "• " else "↳ "
                                        val isSelected = selectedCategoryId == item.category.id
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "$indent$prefix${item.category.name}",
                                                    fontWeight = if (isSelected) FontWeight.Bold else if (item.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) PosColors.Primary else PosColors.TextHigh,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedCategoryId = item.category.id
                                                categoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. Order Type Filter
                            Box {
                                FilterChipButton(
                                    icon = "🏷️",
                                    label = orderTypeDisplayName(selectedOrderType, strings),
                                    isActive = selectedOrderType != null,
                                    onClick = { orderTypeDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = orderTypeDropdownExpanded,
                                    onDismissRequest = { orderTypeDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                strings.allOrderTypes,
                                                fontWeight = if (selectedOrderType == null) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedOrderType == null) PosColors.Primary else PosColors.TextHigh
                                            )
                                        },
                                        onClick = {
                                            selectedOrderType = null
                                            orderTypeDropdownExpanded = false
                                        }
                                    )
                                    listOf(OrderType.COUNTER, OrderType.TAKEAWAY, OrderType.DINE_IN, OrderType.PREORDER).forEach { type ->
                                        val isSelected = selectedOrderType == type
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    orderTypeDisplayName(type, strings),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) PosColors.Primary else PosColors.TextHigh
                                                )
                                            },
                                            onClick = {
                                                selectedOrderType = type
                                                orderTypeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Reset button when any secondary filter is set
                            if (isFilterActive) {
                                Surface(
                                    onClick = {
                                        selectedCashierId = null
                                        selectedCategoryId = null
                                        selectedOrderType = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = PosColors.DangerLight,
                                    border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("✕", fontSize = 11.sp, color = PosColors.Danger, fontWeight = FontWeight.Bold)
                                        Text(
                                            strings.resetFilters,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PosColors.Danger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Main KPI Cards (8 metrics, perfectly balanced in 4x2 grid)
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 160.dp,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp
                ) {
                    // KPI 1: Chiffre d'Affaires Total (Featured)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PosColors.BakeryBrown),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    strings.totalRevenue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text("📈", fontSize = 16.sp)
                            }
                            Text(
                                "${MoneyRules.formatFixed(totalSales)} ${strings.currency}",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                strings.text("Toutes taxes comprises (TTC)", "Taxes included (TTC)", "شامل الضريبة"),
                                fontSize = 10.sp,
                                color = PosColors.GoldenHoney
                            )
                        }
                    }

                    // KPI 2: Total Orders
                    ReportKpiCard(
                        title = strings.totalOrders,
                        value = "$totalOrders",
                        subtitle = strings.text("Commandes clôturées", "Completed orders", "الطلبات المكتملة"),
                        icon = "🧾",
                        accentColor = PosColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 3: Panier Moyen
                    ReportKpiCard(
                        title = strings.averageTicket,
                        value = "${MoneyRules.formatFixed(averageBasketCentimes)} ${strings.currency}",
                        subtitle = strings.text("Moyenne / commande", "Avg / completed order", "متوسط الطلب"),
                        icon = "🛒",
                        accentColor = PosColors.SecondaryDark,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 4: CA HT
                    ReportKpiCard(
                        title = strings.netSales,
                        value = "${MoneyRules.formatFixed(netSalesCentimes)} ${strings.currency}",
                        subtitle = strings.text("Hors taxes (CA net)", "Net sales (excl. tax)", "دون ضريبة"),
                        icon = "📊",
                        accentColor = PosColors.BakeryBrown,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 5: Ventes Espèces
                    ReportKpiCard(
                        title = strings.cashSales,
                        value = "${MoneyRules.formatFixed(summary.cashCentimes)} ${strings.currency}",
                        subtitle = "$cashPercentage% des ventes",
                        icon = "💵",
                        accentColor = PosColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 6: Ventes Carte / TPE
                    ReportKpiCard(
                        title = strings.cardSales,
                        value = "${MoneyRules.formatFixed(summary.cardCentimes)} ${strings.currency}",
                        subtitle = "$cardPercentage% des ventes",
                        icon = "💳",
                        accentColor = PosColors.SecondaryDark,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 7: TVA Collectée
                    ReportKpiCard(
                        title = strings.tax,
                        value = "${MoneyRules.formatFixed(summary.taxCentimes)} ${strings.currency}",
                        subtitle = strings.text("TVA collectée", "VAT collected", "الضريبة المحصلة"),
                        icon = "🏛️",
                        accentColor = PosColors.Alert,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // KPI 8: Ventes Annulées
                    ReportKpiCard(
                        title = strings.cancelledSalesLabel,
                        value = analytics.cancelledSales.toString(),
                        subtitle = strings.text("Sur la période", "In selected period", "خلال الفترة"),
                        icon = "↩️",
                        accentColor = PosColors.Danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. CA Evolution Chart (Hourly for 1 day, Daily for multi-day)
                SalesEvolutionChart(
                    points = evolutionPoints,
                    isHourly = isHourly,
                    totalSalesCentimes = totalSales,
                    strings = strings
                )

                // 4. Analytics Breakdown: Row A (Top Products + Top Categories)
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 320.dp,
                    horizontalSpacing = 14.dp,
                    verticalSpacing = 14.dp
                ) {
                    TopProductsCard(
                        products = analytics.byProduct.take(10),
                        totalSalesCentimes = totalSales,
                        strings = strings
                    )

                    TopCategoriesCard(
                        categories = analytics.byCategory.take(10),
                        totalSalesCentimes = totalSales,
                        strings = strings
                    )
                }

                // 5. Analytics Breakdown: Row B (Payment Split + Cashier Performance)
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 320.dp,
                    horizontalSpacing = 14.dp,
                    verticalSpacing = 14.dp
                ) {
                    PaymentBreakdownCard(
                        summary = summary,
                        cashPercentage = cashPercentage,
                        cardPercentage = cardPercentage,
                        strings = strings
                    )

                    CashierPerformanceCard(
                        cashiers = analytics.byCashier,
                        strings = strings
                    )
                }
            }
        }
    }

    if (showStartDatePicker) {
        TouchDatePickerModal(
            title = strings.text("Sélectionner la Date début", "Select Start Date", "تحديد تاريخ البداية"),
            initialDate = startDate,
            minDate = minDate,
            maxDate = endDate,
            onDateSelected = { newStart ->
                val clamped = newStart.coerceIn(minDate, todayDate)
                startDate = clamped
                if (endDate < clamped) {
                    endDate = clamped
                }
                selectedPreset = updatePresetForDates(clamped, endDate)
            },
            onDismiss = { showStartDatePicker = false },
            strings = strings
        )
    }

    if (showEndDatePicker) {
        TouchDatePickerModal(
            title = strings.text("Sélectionner la Date fin", "Select End Date", "تحديد تاريخ النهاية"),
            initialDate = endDate,
            minDate = startDate,
            maxDate = todayDate,
            onDateSelected = { newEnd ->
                val clamped = newEnd.coerceIn(minDate, todayDate)
                endDate = clamped
                if (startDate > clamped) {
                    startDate = clamped
                }
                selectedPreset = updatePresetForDates(startDate, clamped)
            },
            onDismiss = { showEndDatePicker = false },
            strings = strings
        )
    }
}

@Composable
private fun FilterChipButton(
    icon: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) PosColors.PrimaryLight else PosColors.Workspace,
        border = BorderStroke(1.dp, if (isActive) PosColors.Primary else PosColors.Border),
        modifier = Modifier
            .height(36.dp)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 12.sp)
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) PosColors.PrimaryDark else PosColors.TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("▼", fontSize = 8.sp, color = if (isActive) PosColors.Primary else PosColors.TextLow)
        }
    }
}

@Composable
private fun SalesEvolutionChart(
    points: List<SalesEvolutionPoint>,
    isHourly: Boolean,
    totalSalesCentimes: Long,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chart Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        strings.revenueEvolution,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.BakeryBrown
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PosColors.PrimaryLight
                    ) {
                        Text(
                            if (isHourly) strings.hourlyEvolution else strings.dailyEvolution,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.PrimaryDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    "${MoneyRules.formatFixed(totalSalesCentimes)} ${strings.currency}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
            }

            if (points.isEmpty() || points.all { it.amountCentimes == 0L }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(PosColors.Workspace, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.text("Aucune vente enregistrée sur cette période", "No sales recorded in this period", "لا توجد مبيعات مسجلة في هذه الفترة"),
                        color = PosColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                val maxAmount = points.maxOf { it.amountCentimes }.coerceAtLeast(1L)
                val scrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .touchHorizontalDragScroll(scrollState)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEach { pt ->
                        val ratio = (pt.amountCentimes.toFloat() / maxAmount).coerceIn(0f, 1f)
                        val hasSales = pt.amountCentimes > 0L

                        Column(
                            modifier = Modifier
                                .width(if (points.size <= 10) 64.dp else 42.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Amount label above bar
                            Text(
                                if (hasSales) "${MoneyRules.formatFixed(pt.amountCentimes)}" else "",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasSales) PosColors.BakeryBrown else Color.Transparent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(4.dp))

                            // Bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .fillMaxHeight(ratio.coerceAtLeast(0.04f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (hasSales) PosColors.Primary else PosColors.SurfaceContainer)
                                )
                            }

                            HorizontalDivider(color = PosColors.Border, thickness = 1.dp)

                            Spacer(Modifier.height(4.dp))

                            // Time label
                            Text(
                                pt.label,
                                fontSize = 10.sp,
                                fontWeight = if (hasSales) FontWeight.Bold else FontWeight.Medium,
                                color = if (hasSales) PosColors.TextHigh else PosColors.TextMuted
                            )

                            // Order count
                            Text(
                                if (pt.orderCount > 0) "${pt.orderCount} vte" else "",
                                fontSize = 8.sp,
                                color = PosColors.TextLow
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProductsCard(
    products: List<SalesBreakdown>,
    totalSalesCentimes: Long,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.topProducts,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
                Text(
                    "${products.size} affichés",
                    fontSize = 11.sp,
                    color = PosColors.TextMedium
                )
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.text("Aucune vente de produit", "No product sales", "لا توجد مبيعات منتجات"),
                        color = PosColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                val maxProductAmount = products.maxOfOrNull { it.amountCentimes }?.coerceAtLeast(1L) ?: 1L

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    products.forEachIndexed { index, item ->
                        val shareRatio = (item.amountCentimes.toFloat() / maxProductAmount).coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Rank Badge
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (index) {
                                                    0 -> PosColors.GoldenHoney
                                                    1 -> Color(0xFFC0C0C0)
                                                    2 -> Color(0xFFCD7F32)
                                                    else -> PosColors.Workspace
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index < 3) Color.White else PosColors.TextMedium
                                        )
                                    }

                                    Text(
                                        item.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = PosColors.Workspace
                                    ) {
                                        Text(
                                            "${item.quantity} vendus",
                                            fontSize = 10.sp,
                                            color = PosColors.TextMedium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    "${MoneyRules.formatFixed(item.amountCentimes)} ${strings.currency}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.BakeryBrown
                                )
                            }

                            // Share progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PosColors.SurfaceContainer)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(shareRatio)
                                        .fillMaxHeight()
                                        .background(PosColors.Primary)
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
private fun TopCategoriesCard(
    categories: List<SalesBreakdown>,
    totalSalesCentimes: Long,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                strings.topCategories,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.BakeryBrown
            )

            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.text("Aucune catégorie enregistrée", "No categories recorded", "لا توجد فئات مسجلة"),
                        color = PosColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        val percent = if (totalSalesCentimes > 0) {
                            ((cat.amountCentimes.toDouble() / totalSalesCentimes) * 100).toInt()
                        } else 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📁", fontSize = 13.sp)
                                Column {
                                    Text(
                                        cat.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${cat.quantity} articles · $percent% du CA",
                                        fontSize = 10.sp,
                                        color = PosColors.TextMuted
                                    )
                                }
                            }

                            Text(
                                "${MoneyRules.formatFixed(cat.amountCentimes)} ${strings.currency}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentBreakdownCard(
    summary: SalesSummary,
    cashPercentage: Int,
    cardPercentage: Int,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.text("Répartition des Encaissements", "Payment Methods Breakdown", "توزيع المدفوعات"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.BakeryBrown
            )

            // Split Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PosColors.Workspace)
                ) {
                    if (cashPercentage > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(cashPercentage.toFloat())
                                .background(PosColors.Success)
                        )
                    }
                    if (cardPercentage > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(cardPercentage.toFloat())
                                .background(PosColors.SecondaryDark)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Espèces: $cashPercentage%", fontSize = 11.sp, color = PosColors.Success, fontWeight = FontWeight.Bold)
                    Text("Carte / TPE: $cardPercentage%", fontSize = 11.sp, color = PosColors.SecondaryDark, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = PosColors.Border)

            BreakdownRow(
                icon = "💵",
                label = strings.cashSales,
                amount = "${MoneyRules.formatFixed(summary.cashCentimes)} ${strings.currency}",
                share = "$cashPercentage%",
                color = PosColors.Success
            )

            BreakdownRow(
                icon = "💳",
                label = strings.cardSales,
                amount = "${MoneyRules.formatFixed(summary.cardCentimes)} ${strings.currency}",
                share = "$cardPercentage%",
                color = PosColors.SecondaryDark
            )
        }
    }
}

@Composable
private fun CashierPerformanceCard(
    cashiers: List<SalesBreakdown>,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                strings.cashierStats,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.BakeryBrown
            )

            if (cashiers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.text("Aucune vente par caissier", "No cashier sales", "لا توجد مبيعات لأمناء الصندوق"),
                        color = PosColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cashiers.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("👤", fontSize = 13.sp)
                                Column {
                                    Text(
                                        c.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${c.quantity} vente(s)",
                                        fontSize = 10.sp,
                                        color = PosColors.TextMuted
                                    )
                                }
                            }

                            Text(
                                "${MoneyRules.formatFixed(c.amountCentimes)} ${strings.currency}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(115.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PosColors.TextMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(icon, fontSize = 15.sp)
            }
            Text(
                value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                color = PosColors.TextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    icon: String,
    label: String,
    amount: String,
    share: String,
    color: Color
) {
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 14.sp)
            }
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PosColors.BakeryBrown)
                Text(share, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(amount, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PosColors.BakeryBrown)
    }
}
