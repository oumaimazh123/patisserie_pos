package ma.elaroui.pos.desktop.presentation.sales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.MoneyRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// -----------------------------------------------------------------------------
// FILTER ENUMS
// -----------------------------------------------------------------------------

internal enum class SalesPeriod {
    ALL, TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM
}

internal enum class SalesPaymentFilter {
    ALL, CASH, CARD
}

// -----------------------------------------------------------------------------
// BUSINESS LOGIC HELPERS (TESTABLE)
// -----------------------------------------------------------------------------

internal fun computeSalesDateBounds(
    period: SalesPeriod,
    customStart: String = "",
    customEnd: String = "",
    referenceEpoch: Long = System.currentTimeMillis()
): Pair<Long?, Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = referenceEpoch }
    return when (period) {
        SalesPeriod.ALL -> Pair(null, null)
        SalesPeriod.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            Pair(cal.timeInMillis, null)
        }
        SalesPeriod.YESTERDAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStart = cal.timeInMillis
            Pair(yesterdayStart, todayStart - 1)
        }
        SalesPeriod.LAST_7_DAYS -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            Pair(cal.timeInMillis, null)
        }
        SalesPeriod.LAST_30_DAYS -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -29)
            Pair(cal.timeInMillis, null)
        }
        SalesPeriod.CUSTOM -> {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply { isLenient = false }
            val from = runCatching {
                if (customStart.isNotBlank()) {
                    val d = sdf.parse(customStart.trim())
                    val c = Calendar.getInstance().apply { time = d }
                    c.set(Calendar.HOUR_OF_DAY, 0)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    c.timeInMillis
                } else null
            }.getOrNull()

            val to = runCatching {
                if (customEnd.isNotBlank()) {
                    val d = sdf.parse(customEnd.trim())
                    val c = Calendar.getInstance().apply { time = d }
                    c.set(Calendar.HOUR_OF_DAY, 23)
                    c.set(Calendar.MINUTE, 59)
                    c.set(Calendar.SECOND, 59)
                    c.set(Calendar.MILLISECOND, 999)
                    c.timeInMillis
                } else null
            }.getOrNull()

            Pair(from, to)
        }
    }
}

internal fun calculateVisiblePages(currentPage: Int, totalPages: Int): List<Int?> {
    if (totalPages <= 1) return emptyList()
    if (totalPages <= 7) return (1..totalPages).toList()

    val pages = mutableListOf<Int?>()
    pages.add(1)

    if (currentPage <= 4) {
        for (i in 2..5) pages.add(i)
        pages.add(null) // ellipsis
        pages.add(totalPages)
    } else if (currentPage >= totalPages - 3) {
        pages.add(null) // ellipsis
        for (i in (totalPages - 4) until totalPages) pages.add(i)
        pages.add(totalPages)
    } else {
        pages.add(null) // ellipsis
        pages.add(currentPage - 1)
        pages.add(currentPage)
        pages.add(currentPage + 1)
        pages.add(null) // ellipsis
        pages.add(totalPages)
    }
    return pages
}

internal fun filterAndSortSales(
    sales: List<SalesHistoryRow>,
    query: String,
    status: OrderStatus?,
    paymentFilter: SalesPaymentFilter,
    fromEpoch: Long?,
    toEpoch: Long?,
    selectedCategoryId: Long? = null,
    categories: List<Category> = emptyList(),
    products: List<Product> = emptyList()
): List<SalesHistoryRow> {
    val q = query.trim()

    // Precalculate matching product IDs if category filter is active
    val matchingProductIds: Set<Long>? = if (selectedCategoryId != null) {
        val descendantCategoryIds = setOf(selectedCategoryId) + CategoryHierarchyRules.getAllDescendantIds(selectedCategoryId, categories)
        products.filter { it.categoryId in descendantCategoryIds }.map { it.id }.toSet()
    } else {
        null
    }

    val filtered = sales.filter { row ->
        val timestamp = row.paidAtEpochMillis ?: 0L

        // Status check
        if (status != null && row.order.status != status) return@filter false

        // Payment check
        val paymentMatches = when (paymentFilter) {
            SalesPaymentFilter.ALL -> true
            SalesPaymentFilter.CASH -> row.paymentMethod == PaymentMethod.CASH
            SalesPaymentFilter.CARD -> row.paymentMethod == PaymentMethod.CARD
        }
        if (!paymentMatches) return@filter false

        // Date check
        if (fromEpoch != null && timestamp < fromEpoch) return@filter false
        if (toEpoch != null && timestamp > toEpoch) return@filter false

        // Hierarchical Category check
        if (matchingProductIds != null) {
            val hasMatchingProduct = row.order.lines.any { it.productId in matchingProductIds }
            if (!hasMatchingProduct) return@filter false
        }

        // Text search check
        if (q.isNotEmpty()) {
            val matchesNumber = row.order.number.contains(q, ignoreCase = true)
            val matchesCashier = row.cashierName.contains(q, ignoreCase = true)
            if (!matchesNumber && !matchesCashier) return@filter false
        }

        true
    }

    // Default sorting: Newest first
    return filtered.sortedWith(compareByDescending { it.paidAtEpochMillis ?: it.order.id })
}

// -----------------------------------------------------------------------------
// MAIN COMPOSABLE
// -----------------------------------------------------------------------------

@Composable
fun CompletedSalesScreen(
    sales: List<SalesHistoryRow>,
    strings: DesktopStrings,
    categories: List<Category> = emptyList(),
    products: List<Product> = emptyList(),
    onSelectSale: (SalesHistoryRow) -> Unit,
    onBack: (() -> Unit)? = null
) {
    // Search & Filter state
    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf(SalesPeriod.ALL) }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf(SalesPaymentFilter.ALL) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    // Pagination state
    var pageSize by remember { mutableStateOf(20) }
    var currentPage by remember { mutableStateOf(1) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    // Date filtering bounds
    val now = remember { System.currentTimeMillis() }
    val (fromEpoch, toEpoch) = remember(selectedPeriod, customStartDate, customEndDate, now) {
        computeSalesDateBounds(selectedPeriod, customStartDate, customEndDate, now)
    }

    // Filter & Sort list
    val filteredSales = remember(
        sales,
        searchQuery,
        selectedStatus,
        selectedPaymentMethod,
        selectedCategoryId,
        categories,
        products,
        fromEpoch,
        toEpoch
    ) {
        filterAndSortSales(
            sales = sales,
            query = searchQuery,
            status = selectedStatus,
            paymentFilter = selectedPaymentMethod,
            fromEpoch = fromEpoch,
            toEpoch = toEpoch,
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            products = products
        )
    }

    // Reset pagination to page 1 whenever any filter or page size changes
    LaunchedEffect(
        searchQuery,
        selectedPeriod,
        customStartDate,
        customEndDate,
        selectedStatus,
        selectedPaymentMethod,
        selectedCategoryId,
        pageSize
    ) {
        currentPage = 1
    }

    // Check if any filter is active
    val hasActiveFilters = searchQuery.isNotBlank() ||
            selectedPeriod != SalesPeriod.ALL ||
            customStartDate.isNotBlank() ||
            customEndDate.isNotBlank() ||
            selectedStatus != null ||
            selectedPaymentMethod != SalesPaymentFilter.ALL ||
            selectedCategoryId != null

    fun resetFilters() {
        searchQuery = ""
        selectedPeriod = SalesPeriod.ALL
        customStartDate = ""
        customEndDate = ""
        selectedStatus = null
        selectedPaymentMethod = SalesPaymentFilter.ALL
        selectedCategoryId = null
        currentPage = 1
    }

    // Pagination calculations
    val totalItems = filteredSales.size
    val totalPages = maxOf(1, (totalItems + pageSize - 1) / pageSize)
    val safeCurrentPage = currentPage.coerceIn(1, totalPages)
    val startIndex = if (totalItems == 0) 0 else (safeCurrentPage - 1) * pageSize
    val endIndex = minOf(startIndex + pageSize, totalItems)
    val pagedSales = if (totalItems == 0) emptyList() else filteredSales.subList(startIndex, endIndex)

    val salesListState = rememberLazyListState()

    // Scroll to top of list on page change
    LaunchedEffect(safeCurrentPage) {
        if (pagedSales.isNotEmpty()) {
            salesListState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.salesHistory,
            strings = strings,
            onBackToDashboard = onBack
        )

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // -------------------------------------------------------------
                // SEARCH & FILTER BAR
                // -------------------------------------------------------------
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PosColors.Surface,
                    border = BorderStroke(1.dp, PosColors.Border),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Row: Search Input + Reset Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TouchTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = strings.text(
                                    "Rechercher par N° commande, caissier…",
                                    "Search order #, cashier…",
                                    "بحث برقم الطلب أو الكاشير…"
                                ),
                                leadingIcon = { Text("🔍", fontSize = 16.sp) },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        Text(
                                            "✕",
                                            fontSize = 14.sp,
                                            color = PosColors.TextMedium,
                                            modifier = Modifier
                                                .clickable { searchQuery = "" }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .padding(4.dp)
                                        )
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = PosColors.Canvas,
                                    unfocusedContainerColor = PosColors.Canvas,
                                    focusedBorderColor = PosColors.Primary,
                                    unfocusedBorderColor = PosColors.Border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )

                            // Reset Filters Button
                            ResetFiltersButton(
                                isEnabled = hasActiveFilters,
                                strings = strings,
                                onReset = { resetFilters() }
                            )
                        }

                        // Filter Dropdowns Row: Category, Period, Status, Payment Method
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isCompact = maxWidth < 880.dp

                            if (isCompact) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategoryFilterDropdown(
                                            categories = categories,
                                            selectedCategoryId = selectedCategoryId,
                                            onCategorySelected = { selectedCategoryId = it },
                                            strings = strings,
                                            modifier = Modifier.weight(1.2f)
                                        )

                                        PeriodFilterDropdown(
                                            selected = selectedPeriod,
                                            onSelect = { selectedPeriod = it },
                                            strings = strings,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StatusFilterDropdown(
                                            selected = selectedStatus,
                                            onSelect = { selectedStatus = it },
                                            strings = strings,
                                            modifier = Modifier.weight(1f)
                                        )

                                        PaymentFilterDropdown(
                                            selected = selectedPaymentMethod,
                                            onSelect = { selectedPaymentMethod = it },
                                            strings = strings,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CategoryFilterDropdown(
                                        categories = categories,
                                        selectedCategoryId = selectedCategoryId,
                                        onCategorySelected = { selectedCategoryId = it },
                                        strings = strings,
                                        modifier = Modifier.weight(1.25f)
                                    )

                                    PeriodFilterDropdown(
                                        selected = selectedPeriod,
                                        onSelect = { selectedPeriod = it },
                                        strings = strings,
                                        modifier = Modifier.weight(1.1f)
                                    )

                                    StatusFilterDropdown(
                                        selected = selectedStatus,
                                        onSelect = { selectedStatus = it },
                                        strings = strings,
                                        modifier = Modifier.weight(1f)
                                    )

                                    PaymentFilterDropdown(
                                        selected = selectedPaymentMethod,
                                        onSelect = { selectedPaymentMethod = it },
                                        strings = strings,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Custom Date Range Inputs (visible only if period == CUSTOM)
                        AnimatedVisibility(
                            visible = selectedPeriod == SalesPeriod.CUSTOM,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PosColors.Workspace,
                                border = BorderStroke(1.dp, PosColors.BorderVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "📅 " + strings.text("Plage de dates :", "Date range:", "نطاق التواريخ:"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh
                                    )

                                    TouchTextField(
                                        value = customStartDate,
                                        onValueChange = { customStartDate = it },
                                        placeholder = strings.text("Début (jj/mm/aaaa)", "Start (dd/mm/yyyy)", "البداية (يوم/شهر/سنة)"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = PosColors.Surface,
                                            unfocusedContainerColor = PosColors.Surface,
                                            focusedBorderColor = PosColors.Primary,
                                            unfocusedBorderColor = PosColors.Border
                                        ),
                                        modifier = Modifier.width(220.dp).heightIn(min = 48.dp)
                                    )

                                    Text(
                                        "→",
                                        fontSize = 14.sp,
                                        color = PosColors.TextMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    TouchTextField(
                                        value = customEndDate,
                                        onValueChange = { customEndDate = it },
                                        placeholder = strings.text("Fin (jj/mm/aaaa)", "End (dd/mm/yyyy)", "النهاية (يوم/شهر/سنة)"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = PosColors.Surface,
                                            unfocusedContainerColor = PosColors.Surface,
                                            focusedBorderColor = PosColors.Primary,
                                            unfocusedBorderColor = PosColors.Border
                                        ),
                                        modifier = Modifier.width(220.dp).heightIn(min = 48.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // SALES LIST OR EMPTY STATE
                // -------------------------------------------------------------
                if (totalItems == 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PosColors.Surface,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🧾", fontSize = 42.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                strings.text(
                                    "Aucune commande ne correspond aux filtres sélectionnés",
                                    "No order matches the selected filters",
                                    "لا توجد أي مبيعات تطابق الفلاتر المحددة"
                                ),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                strings.text(
                                    "Modifiez vos critères de recherche ou réinitialisez les filtres pour afficher l'ensemble des ventes.",
                                    "Modify your search criteria or reset filters to display all sales.",
                                    "يرجى تعديل معايير البحث أو إعادة ضبط الفلاتr لعرض كافة المبيعات."
                                ),
                                fontSize = 13.sp,
                                color = PosColors.TextMedium,
                                textAlign = TextAlign.Center
                            )
                            if (hasActiveFilters) {
                                Spacer(Modifier.height(18.dp))
                                Button(
                                    onClick = { resetFilters() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        "↺ " + strings.text("Réinitialiser les filtres", "Reset filters", "إعادة ضبط الفلاتر"),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = salesListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .touchDragScroll(salesListState),
                            contentPadding = PaddingValues(end = 14.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pagedSales, key = { it.order.id }) { row ->
                                SalesRowCard(
                                    row = row,
                                    strings = strings,
                                    dateFormat = dateFormat,
                                    onClick = { onSelectSale(row) }
                                )
                            }
                        }

                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(salesListState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            style = defaultScrollbarStyle().copy(
                                unhoverColor = PosColors.BorderVariant.copy(alpha = 0.6f),
                                hoverColor = PosColors.Primary,
                                thickness = 8.dp,
                                shape = RoundedCornerShape(4.dp)
                            )
                        )
                    }
                }

                // -------------------------------------------------------------
                // BOTTOM PAGINATION BAR
                // -------------------------------------------------------------
                SalesPaginationBar(
                    totalItems = totalItems,
                    startIndex = startIndex,
                    endIndex = endIndex,
                    currentPage = safeCurrentPage,
                    totalPages = totalPages,
                    pageSize = pageSize,
                    onPageSizeChange = { newSize ->
                        pageSize = newSize
                        currentPage = 1
                    },
                    onPageChange = { newPage ->
                        currentPage = newPage
                    },
                    strings = strings
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// DROPDOWNS IMPLEMENTATION
// -----------------------------------------------------------------------------

@Composable
private fun <T> PosFilterDropdown(
    label: String,
    selectedValue: T,
    items: List<Pair<T, String>>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isFilterActive: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = items.firstOrNull { it.first == selectedValue }?.second ?: label

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        expanded -> PosColors.Primary
        isFilterActive -> PosColors.Primary
        isHovered -> PosColors.BorderVariant
        else -> PosColors.Border
    }

    val containerColor = when {
        isFilterActive -> PosColors.PrimaryLight.copy(alpha = 0.25f)
        isHovered -> PosColors.Workspace
        else -> PosColors.Surface
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentLabel,
                    fontSize = 13.sp,
                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFilterActive) PosColors.PrimaryDark else PosColors.TextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
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
            modifier = Modifier
                .background(PosColors.Surface)
                .widthIn(min = 180.dp)
        ) {
            items.forEach { (item, itemText) ->
                val isSelected = item == selectedValue
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PosColors.Primary else PosColors.TextHigh
                        )
                    },
                    onClick = {
                        onItemSelected(item)
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
private fun PeriodFilterDropdown(
    selected: SalesPeriod,
    onSelect: (SalesPeriod) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SalesPeriod.ALL to ("📅 " + strings.text("Toutes les périodes", "All periods", "كل الفترات")),
        SalesPeriod.TODAY to ("☀️ " + strings.today),
        SalesPeriod.YESTERDAY to ("⏮️ " + strings.yesterday),
        SalesPeriod.LAST_7_DAYS to ("📊 " + strings.last7Days),
        SalesPeriod.LAST_30_DAYS to ("🗓️ " + strings.text("30 derniers jours", "Last 30 days", "آخر 30 يوم")),
        SalesPeriod.CUSTOM to ("✏️ " + strings.text("Personnalisée", "Custom", "مخصصة"))
    )

    PosFilterDropdown(
        label = strings.text("Période", "Period", "الفترة"),
        selectedValue = selected,
        items = items,
        onItemSelected = onSelect,
        modifier = modifier,
        isFilterActive = selected != SalesPeriod.ALL
    )
}

@Composable
private fun StatusFilterDropdown(
    selected: OrderStatus?,
    onSelect: (OrderStatus?) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        null to strings.text("Tous les statuts", "All statuses", "جميع الحالات"),
        OrderStatus.COMPLETED to ("● " + strings.text("Clôturées", "Completed", "مكتملة")),
        OrderStatus.OPEN to ("● " + strings.text("Ouvertes", "Open", "مفتوحة")),
        OrderStatus.CANCELLED to ("● " + strings.text("Annulées", "Cancelled", "ملغاة"))
    )

    PosFilterDropdown(
        label = strings.text("Statut", "Status", "الحالة"),
        selectedValue = selected,
        items = items,
        onItemSelected = onSelect,
        modifier = modifier,
        isFilterActive = selected != null
    )
}

@Composable
private fun PaymentFilterDropdown(
    selected: SalesPaymentFilter,
    onSelect: (SalesPaymentFilter) -> Unit,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SalesPaymentFilter.ALL to strings.text("Tous les paiements", "All payments", "جميع طرق الدفع"),
        SalesPaymentFilter.CASH to ("💵 " + strings.cash),
        SalesPaymentFilter.CARD to ("💳 " + strings.card)
    )

    PosFilterDropdown(
        label = strings.text("Mode de paiement", "Payment Method", "طريقة الدفع"),
        selectedValue = selected,
        items = items,
        onItemSelected = onSelect,
        modifier = modifier,
        isFilterActive = selected != SalesPaymentFilter.ALL
    )
}

private data class CategoryFilterItem(
    val category: Category,
    val level: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

@Composable
private fun CategoryFilterDropdown(
    categories: List<Category>,
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

    val categoryTree = remember(categories) {
        CategoryHierarchyRules.buildCategoryTree(categories)
    }

    // Auto-expand ancestors of matching items or selected item
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

    val visibleTreeItems = remember(categoryTree, effectiveExpandedIds, menuSearchQuery, searchMatchingAncestorIds) {
        val result = mutableListOf<CategoryFilterItem>()
        val query = menuSearchQuery.trim()

        fun traverse(nodes: List<ma.elaroui.pos.shared.rules.CategoryTreeNode>) {
            nodes.forEach { node ->
                val matchesDirectly = query.isBlank() || node.category.name.contains(query, ignoreCase = true)
                val isAncestorOfMatch = searchMatchingAncestorIds.contains(node.category.id)
                val isVisible = matchesDirectly || isAncestorOfMatch

                if (isVisible) {
                    val isExpanded = effectiveExpandedIds.contains(node.category.id)
                    val hasChildren = node.children.isNotEmpty()
                    result.add(
                        CategoryFilterItem(
                            category = node.category,
                            level = node.level,
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
            color = if (isFilterActive) PosColors.PrimaryLight.copy(alpha = 0.25f) else PosColors.Surface,
            border = BorderStroke(1.dp, if (isFilterActive) PosColors.Primary else PosColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
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
                        text = selectedCategory?.name ?: strings.allCategories,
                        fontSize = 13.sp,
                        fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFilterActive) PosColors.PrimaryDark else PosColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 420.dp)
        ) {
            // Search Input inside Dropdown
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

            // "Toutes les catégories" Root Option
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
                                text = strings.allCategories,
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

            // Hierarchical Categories Tree
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
                    val isSelected = item.category.id == selectedCategoryId
                    val itemInteraction = remember { MutableInteractionSource() }
                    val isHovered by itemInteraction.collectIsHoveredAsState()

                    val startPadding = when (item.level) {
                        1 -> 8.dp
                        2 -> 24.dp
                        else -> 42.dp
                    }

                    Surface(
                        onClick = {
                            onCategorySelected(item.category.id)
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
                                if (item.hasChildren) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { toggleCategoryExpand(item.category.id) }
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
                                    text = item.category.name,
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
private fun ResetFiltersButton(
    isEnabled: Boolean,
    strings: DesktopStrings,
    onReset: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        !isEnabled -> PosColors.Surface.copy(alpha = 0.5f)
        isHovered -> PosColors.DangerLight
        else -> PosColors.Surface
    }

    val border = when {
        !isEnabled -> PosColors.Border.copy(alpha = 0.5f)
        isHovered -> PosColors.Danger.copy(alpha = 0.5f)
        else -> PosColors.Border
    }

    val textColor = when {
        !isEnabled -> PosColors.TextLow
        isHovered -> PosColors.Danger
        else -> PosColors.TextMedium
    }

    Surface(
        onClick = { if (isEnabled) onReset() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .height(48.dp)
            .pointerHoverIcon(if (isEnabled) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("↺", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
            Text(
                strings.text("Réinitialiser les filtres", "Reset filters", "إعادة ضبط الفلاتر"),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

// -----------------------------------------------------------------------------
// BOTTOM PAGINATION COMPONENT
// -----------------------------------------------------------------------------

@Composable
private fun SalesPaginationBar(
    totalItems: Int,
    startIndex: Int,
    endIndex: Int,
    currentPage: Int,
    totalPages: Int,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    strings: DesktopStrings
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PosColors.Surface,
        border = BorderStroke(1.dp, PosColors.Border),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val isCompact = maxWidth < 760.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Counter and Page Size row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Counter
                        PaginationCounterText(
                            totalItems = totalItems,
                            startIndex = startIndex,
                            endIndex = endIndex,
                            strings = strings
                        )

                        // Page Size Dropdown
                        PageSizeDropdown(
                            pageSize = pageSize,
                            onPageSizeChange = onPageSizeChange,
                            strings = strings
                        )
                    }

                    // Navigation buttons
                    if (totalPages > 1) {
                        PaginationControlsRow(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageChange = onPageChange,
                            strings = strings
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Page Size Selector
                    PageSizeDropdown(
                        pageSize = pageSize,
                        onPageSizeChange = onPageSizeChange,
                        strings = strings
                    )

                    // Center: Counter + Navigation Buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaginationCounterText(
                            totalItems = totalItems,
                            startIndex = startIndex,
                            endIndex = endIndex,
                            strings = strings
                        )

                        if (totalPages > 1) {
                            PaginationControlsRow(
                                currentPage = currentPage,
                                totalPages = totalPages,
                                onPageChange = onPageChange,
                                strings = strings
                            )
                        }
                    }

                    // Right spacer to visually balance the center column
                    Spacer(Modifier.width(160.dp))
                }
            }
        }
    }
}

@Composable
private fun PaginationCounterText(
    totalItems: Int,
    startIndex: Int,
    endIndex: Int,
    strings: DesktopStrings
) {
    val text = if (totalItems == 0) {
        strings.text("0 commande", "0 orders", "0 طلب")
    } else {
        "${startIndex + 1}–$endIndex " + strings.text("sur", "of", "من") + " $totalItems " + strings.text("commandes", "orders", "طلبات")
    }

    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = PosColors.TextHigh
    )
}

@Composable
private fun PageSizeDropdown(
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    strings: DesktopStrings
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(10, 20, 50, 100)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            strings.text("Ventes par page :", "Sales per page:", "المبيعات في الصفحة:"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = PosColors.TextMedium
        )

        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = PosColors.Workspace,
                border = BorderStroke(1.dp, PosColors.Border),
                modifier = Modifier
                    .height(34.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "$pageSize",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.Primary
                    )
                    Text(if (expanded) "▲" else "▼", fontSize = 10.sp, color = PosColors.TextMedium)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PosColors.Surface)
            ) {
                options.forEach { option ->
                    val isSelected = option == pageSize
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$option",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PosColors.Primary else PosColors.TextHigh
                            )
                        },
                        onClick = {
                            onPageSizeChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaginationControlsRow(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    strings: DesktopStrings
) {
    val pageNumbers = remember(currentPage, totalPages) {
        calculateVisiblePages(currentPage, totalPages)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Previous Button
        PaginationNavButton(
            label = "← " + strings.text("Précédent", "Previous", "السابق"),
            isEnabled = currentPage > 1,
            onClick = { onPageChange(currentPage - 1) }
        )

        Spacer(Modifier.width(4.dp))

        // Numeric Page Buttons
        pageNumbers.forEach { page ->
            if (page == null) {
                Text(
                    "…",
                    fontSize = 14.sp,
                    color = PosColors.TextMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                val isSelected = page == currentPage
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                val bg = when {
                    isSelected -> PosColors.Primary
                    isHovered -> PosColors.Workspace
                    else -> Color.Transparent
                }
                val border = when {
                    isSelected -> PosColors.Primary
                    isHovered -> PosColors.BorderVariant
                    else -> PosColors.Border.copy(alpha = 0.6f)
                }

                Surface(
                    onClick = { onPageChange(page) },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(6.dp),
                    color = bg,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$page",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else PosColors.TextHigh
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // Next Button
        PaginationNavButton(
            label = strings.text("Suivant", "Next", "التالي") + " →",
            isEnabled = currentPage < totalPages,
            onClick = { onPageChange(currentPage + 1) }
        )
    }
}

@Composable
private fun PaginationNavButton(
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        !isEnabled -> PosColors.Canvas.copy(alpha = 0.5f)
        isHovered -> PosColors.Workspace
        else -> PosColors.Surface
    }

    val border = when {
        !isEnabled -> PosColors.Border.copy(alpha = 0.4f)
        isHovered -> PosColors.Primary
        else -> PosColors.Border
    }

    val textColor = when {
        !isEnabled -> PosColors.TextLow
        isHovered -> PosColors.Primary
        else -> PosColors.TextHigh
    }

    Surface(
        onClick = { if (isEnabled) onClick() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .height(32.dp)
            .pointerHoverIcon(if (isEnabled) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

// -----------------------------------------------------------------------------
// SALE ROW CARD (UNTOUCHED VISUAL DESIGN & INTERACTION)
// -----------------------------------------------------------------------------

@Composable
private fun SalesRowCard(
    row: SalesHistoryRow,
    strings: DesktopStrings,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val order = row.order
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBorder by animateColorAsState(
        if (isHovered) PosColors.Primary else PosColors.Border
    )

    // User-friendly Order Status Badge
    val (statusText, statusBg, statusBorder, statusColor) = when (order.status) {
        OrderStatus.COMPLETED -> SalesBadgeStyle(
            strings.text("Clôturée", "Completed", "مكتملة"),
            PosColors.SuccessLight,
            PosColors.Success.copy(alpha = 0.3f),
            PosColors.Success
        )
        OrderStatus.OPEN -> SalesBadgeStyle(
            strings.text("Ouverte", "Open", "مفتوحة"),
            PosColors.AlertLight,
            PosColors.Alert.copy(alpha = 0.3f),
            PosColors.Alert
        )
        OrderStatus.CANCELLED -> SalesBadgeStyle(
            strings.text("Annulée", "Cancelled", "ملغاة"),
            PosColors.DangerLight,
            PosColors.Danger.copy(alpha = 0.3f),
            PosColors.Danger
        )
    }

    // User-friendly Order Type Label
    val typeText = when (order.type) {
        OrderType.DINE_IN -> if (order.tableId != null) strings.text("🍽️ Sur place (Table ${order.tableId})", "🍽️ Dine In (Table ${order.tableId})", "🍽️ محلي (طاولة ${order.tableId})")
        else strings.text("🍽️ Sur place", "🍽️ Dine In", "🍽️ محلي")
        OrderType.TAKEAWAY -> strings.text("🛍️ À emporter", "🛍️ Takeaway", "🛍️ سفري")
        OrderType.COUNTER -> strings.text("☕ Comptoir", "☕ Counter", "☕ بالصندوق")
        OrderType.PREORDER -> strings.text("🎂 Précommande", "🎂 Pre-order", "🎂 طلب مسبق")
    }

    // User-friendly Payment Method Badge
    val paymentText = when (row.paymentMethod) {
        PaymentMethod.CASH -> "💵 " + strings.cash
        PaymentMethod.CARD -> "💳 " + strings.card
        PaymentMethod.CARNET_CLIENT -> "📖 " + strings.text("Carnet client", "Customer Credit", "دفتر الزبون")
        PaymentMethod.MOBILE_QR -> "📱 " + strings.text("Mobile / QR", "Mobile / QR", "محمول / QR")
        null -> strings.text("— Non réglé", "— Unpaid", "— غير مسدد")
    }

    val dateFormatted = row.paidAtEpochMillis?.let { dateFormat.format(Date(it)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
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
            val isCompact = maxWidth < 620.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            order.number,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusBg,
                                border = BorderStroke(1.dp, statusBorder)
                            ) {
                                Text(
                                    "● $statusText",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (row.paymentMethod != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border)
                                ) {
                                    Text(
                                        paymentText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            typeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PosColors.TextMedium
                        )

                        Text("•", fontSize = 11.sp, color = PosColors.BorderVariant)

                        Text(
                            "👤 ${row.cashierName}",
                            fontSize = 11.sp,
                            color = PosColors.TextMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (dateFormatted != null) {
                            Text("•", fontSize = 11.sp, color = PosColors.BorderVariant)
                            Text(
                                "🕒 $dateFormatted",
                                fontSize = 11.sp,
                                color = PosColors.TextMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.PrimaryDark
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                strings.text("Détails", "Details", "التفاصيل"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PosColors.Primary
                            )
                            Text("→", fontSize = 14.sp, color = PosColors.Primary)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                order.number,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusBg,
                                border = BorderStroke(1.dp, statusBorder)
                            ) {
                                Text(
                                    "● $statusText",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (row.paymentMethod != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border)
                                ) {
                                    Text(
                                        paymentText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.TextHigh,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                typeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = PosColors.TextMedium
                            )

                            Text("•", fontSize = 12.sp, color = PosColors.BorderVariant)

                            Text(
                                "👤 ${row.cashierName}",
                                fontSize = 12.sp,
                                color = PosColors.TextMedium
                            )

                            if (dateFormatted != null) {
                                Text("•", fontSize = 12.sp, color = PosColors.BorderVariant)
                                Text(
                                    "🕒 $dateFormatted",
                                    fontSize = 12.sp,
                                    color = PosColors.TextMedium
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.PrimaryDark
                            )
                            Text(
                                "${order.lines.sumOf { it.quantity }} ${strings.text("article(s)", "item(s)", "عنصر")}",
                                fontSize = 12.sp,
                                color = PosColors.TextMedium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PosColors.PrimaryLight,
                            border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                strings.details + " →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PosColors.Primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SalesBadgeStyle(val text: String, val bg: Color, val border: Color, val color: Color)
