package ma.elaroui.pos.desktop.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesSummary
import ma.elaroui.pos.desktop.persistence.RetailSalesAnalytics
import ma.elaroui.pos.desktop.persistence.SalesBreakdown
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchDatePickerModal
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.touchHorizontalDragScroll
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun DailySalesReportScreen(
    summaryForRange: (fromEpoch: Long, toEpoch: Long) -> SalesSummary,
    analyticsForRange: (fromEpoch: Long, toEpoch: Long) -> RetailSalesAnalytics,
    earliestSaleEpoch: (() -> Long?)? = null,
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

    fun updatePresetForDates(s: LocalDate, e: LocalDate): Int {
        return when {
            s == todayDate.coerceAtLeast(minDate) && e == todayDate -> 0
            s == todayDate.minusDays(1).coerceAtLeast(minDate) && e == todayDate.minusDays(1).coerceAtLeast(minDate) -> 1
            s == todayDate.minusDays(6).coerceAtLeast(minDate) && e == todayDate -> 2
            s == todayDate.withDayOfMonth(1).coerceAtLeast(minDate) && e == todayDate -> 3
            else -> -1
        }
    }

    val fromEpoch = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val toEpoch = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val summary = remember(startDate, endDate) { summaryForRange(fromEpoch, toEpoch) }
    val analytics = remember(startDate, endDate) { analyticsForRange(fromEpoch, toEpoch) }

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
                .padding(20.dp)
                .touchDragScroll(reportScrollState)
                .verticalScroll(reportScrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1050.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Filter & Preset Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, PosColors.Border),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResponsiveFlowGrid(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
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
                                        .height(48.dp)
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
                                    .height(48.dp)
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
                                            fontSize = 13.sp,
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
                                    .height(48.dp)
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PosColors.BakeryBrown
                                        )
                                    }
                                    Text("📅", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // Top KPI Cards Row
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 170.dp,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp
                ) {
                    // Revenue Card (Primary Focus)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PosColors.BakeryBrown),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text("📈", fontSize = 18.sp)
                            }
                            Text(
                                "${MoneyRules.formatFixed(totalSales)} ${strings.currency}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                strings.text("Toutes taxes comprises", "Taxes included", "شامل الضريبة"),
                                fontSize = 11.sp,
                                color = PosColors.Honey
                            )
                        }
                    }

                    // Total Orders Card
                    ReportKpiCard(
                        title = strings.totalOrders,
                        value = "$totalOrders",
                        subtitle = strings.text("Commandes clôturées", "Completed orders", "الطلبات المكتملة"),
                        icon = "🧾",
                        accentColor = PosColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ReportKpiCard(
                        title = strings.text("Ventes annulées", "Cancelled sales", "المبيعات الملغاة"),
                        value = analytics.cancelledSales.toString(),
                        subtitle = strings.text("Sur la période", "In selected period", "خلال الفترة"),
                        icon = "↩️",
                        accentColor = PosColors.Danger,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Cash Sales Card
                    ReportKpiCard(
                        title = strings.cashSales,
                        value = "${MoneyRules.formatFixed(summary.cashCentimes)} ${strings.currency}",
                        subtitle = "$cashPercentage% des ventes",
                        icon = "💵",
                        accentColor = PosColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Card / Terminal Sales Card
                    ReportKpiCard(
                        title = strings.cardSales,
                        value = "${MoneyRules.formatFixed(summary.cardCentimes)} ${strings.currency}",
                        subtitle = "$cardPercentage% des ventes",
                        icon = "💳",
                        accentColor = PosColors.SecondaryDark,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tax Card
                    ReportKpiCard(
                        title = strings.tax,
                        value = "${MoneyRules.formatFixed(summary.taxCentimes)} ${strings.currency}",
                        subtitle = strings.text("TVA collectée", "VAT collected", "الضريبة المحصلة"),
                        icon = "🏛️",
                        accentColor = PosColors.Alert,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Detailed Analytics & Breakdown Section
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 280.dp,
                    horizontalSpacing = 14.dp,
                    verticalSpacing = 14.dp
                ) {
                    // Payment Method Breakdown Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, PosColors.Border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                strings.text("Répartition des Encaissements", "Payment Methods Breakdown", "توزيع المدفوعات"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )

                            // Visual Share Progress Bar
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
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
                                    Text("Carte/TPE: $cardPercentage%", fontSize = 11.sp, color = PosColors.SecondaryDark, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(color = PosColors.Border)

                            // Detailed Line Metrics
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

                    // Key Financial Indicators Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, PosColors.Border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                strings.text("Indicateurs de Performance", "Performance Indicators", "مؤشرات الأداء"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )

                            PerformanceMetricItem(
                                label = strings.text("Panier Moyen par Commande", "Average Ticket", "متوسط قيمة الطلب"),
                                value = "${MoneyRules.formatFixed(averageBasketCentimes)} ${strings.currency}",
                                subtitle = strings.text("Sur $totalOrders commande(s) réalisée(s)", "Based on $totalOrders completed orders", "بناءً على $totalOrders طلب")
                            )

                            HorizontalDivider(color = PosColors.Border)

                            PerformanceMetricItem(
                                label = strings.text("Chiffre d'Affaires Net (Hors Taxes)", "Net Sales (Excl. Tax)", "صافي المبيعات (دون ضريبة)"),
                                value = "${MoneyRules.formatFixed(netSalesCentimes)} ${strings.currency}",
                                subtitle = strings.text("Total après déduction de la TVA", "Total after VAT deduction", "المجموع بعد خصم الضريبة")
                            )
                        }
                    }
                }

                Text(
                    strings.text("Analyse des ventes", "Sales analysis", "تحليل المبيعات"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 280.dp,
                    horizontalSpacing = 14.dp,
                    verticalSpacing = 14.dp
                ) {
                    SalesBreakdownCard(strings.text("Par produit", "By product", "حسب المنتج"), analytics.byProduct, strings)
                    SalesBreakdownCard(strings.text("Par catégorie", "By category", "حسب الفئة"), analytics.byCategory, strings)
                    SalesBreakdownCard(strings.text("Par caissier", "By cashier", "حسب أمين الصندوق"), analytics.byCashier, strings)
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
private fun SalesBreakdownCard(title: String, rows: List<SalesBreakdown>, strings: DesktopStrings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = PosColors.BakeryBrown)
            if (rows.isEmpty()) {
                Text(strings.text("Aucune vente", "No sales", "لا توجد مبيعات"), color = PosColors.TextMedium)
            } else {
                rows.take(10).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${row.label} · ${row.quantity}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${MoneyRules.formatFixed(row.amountCentimes)} ${strings.currency}", fontWeight = FontWeight.SemiBold)
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
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PosColors.TextMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(icon, fontSize = 16.sp)
            }
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 11.sp,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Column {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PosColors.BakeryBrown)
                Text(share, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(amount, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PosColors.BakeryBrown)
    }
}

@Composable
private fun PerformanceMetricItem(
    label: String,
    value: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = PosColors.TextMedium, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosColors.BakeryBrown)
        Text(subtitle, fontSize = 11.sp, color = PosColors.TextLow)
    }
}
