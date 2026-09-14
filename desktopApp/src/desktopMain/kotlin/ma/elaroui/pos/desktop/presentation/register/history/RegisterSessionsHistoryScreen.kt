package ma.elaroui.pos.desktop.presentation.register.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SessionHistoryRow
import ma.elaroui.pos.desktop.presentation.components.DiscrepancyBadge
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun RegisterSessionsHistoryScreen(
    sessions: List<SessionHistoryRow>,
    strings: DesktopStrings,
    onReprintClosingReport: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var openOnly by remember { mutableStateOf(false) }

    val filtered = sessions.filter { row ->
        (!openOnly || row.session.status == RegisterSessionStatus.OPEN) &&
        (searchQuery.isBlank() || row.session.id.toString().contains(searchQuery) || row.cashierName.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(strings.registerHistory, strings, onBack)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Search & Filter Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TouchTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = strings.text("Rechercher par N° de session ou caissier…", "Search by session # or cashier…", "البحث برقم الجلسة أو الكاشير…"),
                        leadingIcon = { Text("🔍", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PosColors.Primary,
                            unfocusedBorderColor = PosColors.Border
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        onClick = { openOnly = false },
                        shape = RoundedCornerShape(10.dp),
                        color = if (!openOnly) PosColors.Primary else Color.White,
                        border = BorderStroke(1.dp, if (!openOnly) PosColors.Primary else PosColors.Border),
                        shadowElevation = if (!openOnly) 2.dp else 1.dp,
                        modifier = Modifier
                            .height(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                strings.text("Toutes", "All", "الكل"),
                                color = if (!openOnly) Color.White else PosColors.TextHigh,
                                fontWeight = if (!openOnly) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        onClick = { openOnly = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (openOnly) PosColors.Primary else Color.White,
                        border = BorderStroke(1.dp, if (openOnly) PosColors.Primary else PosColors.Border),
                        shadowElevation = if (openOnly) 2.dp else 1.dp,
                        modifier = Modifier
                            .height(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                strings.text("Ouvertes uniquement", "Open only", "المفتوحة فقط"),
                                color = if (openOnly) Color.White else PosColors.TextHigh,
                                fontWeight = if (openOnly) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    EmptyStateCard(
                        title = if (searchQuery.isNotBlank() || openOnly) strings.noResults else strings.text("Aucune session enregistrée", "No session recorded", "لا توجد جلسات مسجلة"),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val sessionsListState = remember { androidx.compose.foundation.lazy.LazyListState() }
                    LazyColumn(
                        state = sessionsListState,
                        modifier = Modifier
                            .weight(1f)
                            .touchDragScroll(sessionsListState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered) { row ->
                            SessionRowCard(
                                row = row,
                                strings = strings,
                                onReprintClosingReport = onReprintClosingReport
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRowCard(
    row: SessionHistoryRow,
    strings: DesktopStrings,
    onReprintClosingReport: (Long) -> Unit
) {
    val sess = row.session
    val isOpen = sess.status == RegisterSessionStatus.OPEN
    var isExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBorder by animateColorAsState(
        if (isExpanded) PosColors.Primary else if (isHovered) PosColors.SecondaryDark else PosColors.Border
    )

    val openedDateStr = formatDateTime(sess.openedAtEpochMilliseconds)
    val closedDateStr = sess.closedAtEpochMilliseconds?.let { formatDateTime(it) }
    val dateText = if (closedDateStr != null) {
        strings.text("Ouverte : ", "Opened: ", "فُتحت: ") + openedDateStr + " · " + strings.text("Clôturée : ", "Closed: ", "أُغلقت: ") + closedDateStr
    } else {
        strings.text("Ouverte : ", "Opened: ", "فُتحت: ") + openedDateStr
    }

    Card(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHovered || isExpanded) PosColors.Workspace else Color.White),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else if (isHovered) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Session # and Cashier + Status & Discrepancy
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
                            .background(if (isOpen) PosColors.SuccessLight else PosColors.Workspace),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isOpen) "🟢" else "🔒", fontSize = 18.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${strings.sessionsTab} #${sess.id} — ${row.cashierName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = PosColors.BakeryBrown,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (row.closingUserName != null) {
                                Text(
                                    "(${strings.text("Fermé par", "Closed by", "أُغلق بواسطة")} ${row.closingUserName})",
                                    fontSize = 12.sp,
                                    color = PosColors.TextMedium
                                )
                            }
                        }
                        Text(
                            dateText,
                            fontSize = 12.sp,
                            color = PosColors.TextMedium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOpen) PosColors.SuccessLight else PosColors.Workspace,
                        border = BorderStroke(1.dp, if (isOpen) PosColors.Success else PosColors.Border)
                    ) {
                        Text(
                            if (isOpen) "● " + strings.text("En cours", "In progress", "قيد التشغيل") else "🔒 " + strings.text("Clôturée", "Closed", "مغلقة"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOpen) PosColors.Success else PosColors.TextMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!isOpen) {
                        DiscrepancyBadge(differenceCentimes = sess.differenceCentimes)
                    }

                    Text(if (isExpanded) "▲" else "▼", fontSize = 12.sp, color = PosColors.TextMedium)
                }
            }

            HorizontalDivider(color = PosColors.Border.copy(alpha = 0.7f))

            // Primary Financial Summary Flow
            ResponsiveFlowGrid(
                modifier = Modifier.fillMaxWidth(),
                minItemWidth = 140.dp,
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                FinancialMetricPill(
                    label = strings.openingCash,
                    value = "${MoneyRules.formatFixed(sess.openingCashCentimes)} ${strings.currency}",
                    color = PosColors.BakeryBrown,
                    modifier = Modifier.fillMaxWidth()
                )

                FinancialMetricPill(
                    label = strings.totalSales,
                    value = "${MoneyRules.formatFixed(row.totalSalesCentimes)} ${strings.currency}",
                    color = PosColors.Primary,
                    modifier = Modifier.fillMaxWidth()
                )

                FinancialMetricPill(
                    label = strings.expectedCash,
                    value = sess.expectedCashCentimes?.let { "${MoneyRules.formatFixed(it)} ${strings.currency}" } ?: "—",
                    color = PosColors.SecondaryDark,
                    modifier = Modifier.fillMaxWidth()
                )

                FinancialMetricPill(
                    label = strings.countedCash,
                    value = sess.countedCashCentimes?.let { "${MoneyRules.formatFixed(it)} ${strings.currency}" } ?: "—",
                    color = PosColors.Success,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Expanded Detailed Audit Section
            if (isExpanded) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            strings.text("Détails d'audit et remise de la session", "Audit and Remittance Details", "تفاصيل المراجعة والتحويل"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )

                        // Sales & Movements breakdown
                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 140.dp,
                            horizontalSpacing = 8.dp,
                            verticalSpacing = 8.dp
                        ) {
                            DetailItem(strings.cashSales, "${MoneyRules.formatFixed(row.cashSalesCentimes)} ${strings.currency}", Modifier.fillMaxWidth())
                            DetailItem(strings.cardSales, "${MoneyRules.formatFixed(row.cardSalesCentimes)} ${strings.currency}", Modifier.fillMaxWidth())
                            DetailItem(strings.cashIn, "+${MoneyRules.formatFixed(row.cashInCentimes)} ${strings.currency}", Modifier.fillMaxWidth())
                            DetailItem(strings.cashOut, "-${MoneyRules.formatFixed(row.cashOutCentimes)} ${strings.currency}", Modifier.fillMaxWidth())
                        }

                        if (!isOpen) {
                            HorizontalDivider(color = PosColors.Border.copy(alpha = 0.5f))

                            // Cashier closing breakdown
                            ResponsiveFlowGrid(
                                modifier = Modifier.fillMaxWidth(),
                                minItemWidth = 140.dp,
                                horizontalSpacing = 8.dp,
                                verticalSpacing = 8.dp
                            ) {
                                DetailItem(
                                    strings.leftInDrawer,
                                    sess.leftInDrawerCentimes?.let { "${MoneyRules.formatFixed(it)} ${strings.currency}" } ?: "—",
                                    Modifier.fillMaxWidth()
                                )
                                DetailItem(
                                    strings.removedAmount,
                                    sess.removedAmountCentimes?.let { "${MoneyRules.formatFixed(it)} ${strings.currency}" } ?: "—",
                                    Modifier.fillMaxWidth()
                                )
                                if ((sess.removedAmountCentimes ?: 0L) > 0L) {
                                    sess.remittanceDestination?.takeIf { it.isNotBlank() }?.let { dest ->
                                        DetailItem(
                                            strings.remittanceDestination,
                                            dest,
                                            Modifier.fillMaxWidth()
                                        )
                                    }
                                    sess.remittanceReference?.takeIf { it.isNotBlank() }?.let { ref ->
                                        DetailItem(
                                            strings.remittanceReference,
                                            ref,
                                            Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (!sess.closingNote.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("📝", fontSize = 14.sp)
                                        Text(
                                            "${strings.closingNote}: ${sess.closingNote}",
                                            fontSize = 12.sp,
                                            color = PosColors.TextHigh,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { onReprintClosingReport(sess.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    strings.text(
                                        "🧾 Réimprimer le rapport de clôture",
                                        "🧾 Reprint closing report",
                                        "🧾 إعادة طباعة تقرير الإغلاق"
                                    ),
                                    fontWeight = FontWeight.Bold
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
private fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = PosColors.TextMedium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh)
    }
}

@Composable
private fun FinancialMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PosColors.Workspace,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 10.sp, color = PosColors.TextMedium, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun formatDateTime(epochMs: Long): String {
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.ofEpochMilli(epochMs))
    }.getOrElse { "—" }
}
