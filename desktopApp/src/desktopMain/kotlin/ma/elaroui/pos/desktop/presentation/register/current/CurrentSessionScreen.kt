package ma.elaroui.pos.desktop.presentation.register.current

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.domain.CashMovement
import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CurrentSessionScreen(
    session: RegisterSession?,
    cashMovements: List<CashMovement>,
    cashSalesCentimes: Long,
    cardSalesCentimes: Long = 0L,
    sessionSales: List<SalesHistoryRow> = emptyList(),
    strings: DesktopStrings,
    canCloseRegister: Boolean = true,
    onNavigateToCloseRegister: () -> Unit = {},
    onCashMovementSubmitted: (CashMovementType, Long, String) -> Unit = { _, _, _ -> },
    onNavigateToReceipt: (Order) -> Unit = {},
    onBack: (() -> Unit)? = null,
    message: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var showMovementDialog by remember { mutableStateOf<CashMovementType?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var localUiMessage by remember { mutableStateOf<UiMessage?>(null) }

    val effectiveUiMessage = localUiMessage ?: uiMessage ?: message.takeIf { it.isNotBlank() }?.let { UiMessage.info(it) }

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PosColors.Canvas)
        ) {
            ManagementPageHeader(
                title = strings.currentSessionTitle,
                strings = strings,
                onBackToDashboard = onBack,
                backLabel = strings.back
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    title = strings.text(
                        "Aucune session de caisse active",
                        "No active register session",
                        "لا توجد جلسة صندوق مفتوحة حالياً"
                    )
                )
            }
        }
        return
    }

    val openingCash = session.openingCashCentimes
    val totalCashIn = cashMovements.filter { it.type == CashMovementType.CASH_IN }.sumOf { it.amountCentimes }
    val totalCashOut = cashMovements.filter { it.type == CashMovementType.CASH_OUT }.sumOf { it.amountCentimes }
    val totalSales = cashSalesCentimes + cardSalesCentimes
    val expectedPhysicalCash = openingCash + cashSalesCentimes + totalCashIn - totalCashOut

    val confirmedSales = remember(sessionSales) { sessionSales.filter { it.order.status == OrderStatus.COMPLETED } }
    val cancelledSales = remember(sessionSales) { sessionSales.filter { it.order.status == OrderStatus.CANCELLED } }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.currentSessionTitle,
            strings = strings,
            onBackToDashboard = onBack,
            backLabel = strings.back
        ) {
            // Action Buttons in Header
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        showMovementDialog = CashMovementType.CASH_IN
                        amountInput = ""
                        reasonInput = ""
                        localUiMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("+ " + strings.cashIn, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        showMovementDialog = CashMovementType.CASH_OUT
                        amountInput = ""
                        reasonInput = ""
                        localUiMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Alert),
                    border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(44.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("- " + strings.cashOut, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (canCloseRegister) {
                    Button(
                        onClick = onNavigateToCloseRegister,
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("🔒 " + strings.closeRegisterAction, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val isDesktopWide = maxWidth >= 960.dp

            if (isDesktopWide) {
                // ==========================================
                // 2-COLUMN DESKTOP LAYOUT (Side-by-Side)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN: Summary KPIs + Cash Movement Journal (52% width)
                    Column(
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Notifications / Alerts
                        if (effectiveUiMessage != null) {
                            PosInlineAlert(
                                message = effectiveUiMessage,
                                onDismiss = { localUiMessage = null; onClearMessage() }
                            )
                        }

                        // Summary Financial KPI Grid (2 columns of cards)
                        SessionMetricsGrid(
                            openingCash = openingCash,
                            totalSales = totalSales,
                            cashSalesCentimes = cashSalesCentimes,
                            cardSalesCentimes = cardSalesCentimes,
                            totalCashIn = totalCashIn,
                            totalCashOut = totalCashOut,
                            expectedPhysicalCash = expectedPhysicalCash,
                            strings = strings
                        )

                        // Cash Movement Journal (takes remaining vertical space)
                        CashMovementsJournalSection(
                            cashMovements = cashMovements,
                            timeFormat = timeFormat,
                            strings = strings,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // RIGHT COLUMN: Session Sales (48% width)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SessionSalesSection(
                            sessionSales = sessionSales,
                            confirmedSales = confirmedSales,
                            cancelledSales = cancelledSales,
                            timeFormat = timeFormat,
                            strings = strings,
                            onNavigateToReceipt = onNavigateToReceipt,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // ==========================================
                // NARROW / TABLET LAYOUT (Single Column Stack)
                // ==========================================
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .touchDragScroll(scrollState)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Notifications
                    if (effectiveUiMessage != null) {
                        PosInlineAlert(
                            message = effectiveUiMessage,
                            onDismiss = { localUiMessage = null; onClearMessage() }
                        )
                    }

                    // Financial KPIs
                    SessionMetricsGrid(
                        openingCash = openingCash,
                        totalSales = totalSales,
                        cashSalesCentimes = cashSalesCentimes,
                        cardSalesCentimes = cardSalesCentimes,
                        totalCashIn = totalCashIn,
                        totalCashOut = totalCashOut,
                        expectedPhysicalCash = expectedPhysicalCash,
                        strings = strings
                    )

                    // Cash Movements Journal
                    CashMovementsJournalSection(
                        cashMovements = cashMovements,
                        timeFormat = timeFormat,
                        strings = strings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 360.dp)
                    )

                    // Session Sales
                    SessionSalesSection(
                        sessionSales = sessionSales,
                        confirmedSales = confirmedSales,
                        cancelledSales = cancelledSales,
                        timeFormat = timeFormat,
                        strings = strings,
                        onNavigateToReceipt = onNavigateToReceipt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 480.dp)
                    )
                }
            }
        }
    }

    // Cash Movement Dialog Modal
    showMovementDialog?.let { type ->
        CashMovementDialogModal(
            type = type,
            amountInput = amountInput,
            onAmountChange = { amountInput = it },
            reasonInput = reasonInput,
            onReasonChange = { reasonInput = it },
            strings = strings,
            onDismiss = { showMovementDialog = null },
            onConfirm = { parsedCentimes, reason ->
                onCashMovementSubmitted(type, parsedCentimes, reason)
                val formattedAmount = MoneyRules.formatFixed(parsedCentimes)
                val isCashIn = type == CashMovementType.CASH_IN
                localUiMessage = UiMessage.success(if (isCashIn) {
                    strings.text("Entrée de $formattedAmount ${strings.currency} enregistrée avec succès", "Cash in of $formattedAmount ${strings.currency} recorded", "تم تسجيل الإيداع بنجاح")
                } else {
                    strings.text("Sortie de $formattedAmount ${strings.currency} enregistrée avec succès", "Cash out of $formattedAmount ${strings.currency} recorded", "تم تسجيل السحب بنجاح")
                })
                showMovementDialog = null
            }
        )
    }
}

/**
 * 2x3 Grid + Full Width Expected Cash Card for Session KPIs
 */
@Composable
private fun SessionMetricsGrid(
    openingCash: Long,
    totalSales: Long,
    cashSalesCentimes: Long,
    cardSalesCentimes: Long,
    totalCashIn: Long,
    totalCashOut: Long,
    expectedPhysicalCash: Long,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Opening cash & Total sales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSessionMetricCard(
                icon = "🪙",
                label = strings.openingCash,
                value = "${MoneyRules.formatFixed(openingCash)} ${strings.currency}",
                accentColor = PosColors.BakeryBrown,
                modifier = Modifier.weight(1f)
            )
            CompactSessionMetricCard(
                icon = "📊",
                label = strings.totalSales,
                value = "${MoneyRules.formatFixed(totalSales)} ${strings.currency}",
                accentColor = PosColors.Primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Cash sales & Card/TPE sales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSessionMetricCard(
                icon = "💵",
                label = strings.cashSales,
                value = "${MoneyRules.formatFixed(cashSalesCentimes)} ${strings.currency}",
                accentColor = PosColors.Success,
                modifier = Modifier.weight(1f)
            )
            CompactSessionMetricCard(
                icon = "💳",
                label = strings.cardSales,
                value = "${MoneyRules.formatFixed(cardSalesCentimes)} ${strings.currency}",
                accentColor = PosColors.SecondaryDark,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Cash in & Cash out
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSessionMetricCard(
                icon = "📥",
                label = strings.cashIn,
                value = "+${MoneyRules.formatFixed(totalCashIn)} ${strings.currency}",
                accentColor = PosColors.Success,
                modifier = Modifier.weight(1f)
            )
            CompactSessionMetricCard(
                icon = "📤",
                label = strings.cashOut,
                value = "-${MoneyRules.formatFixed(totalCashOut)} ${strings.currency}",
                accentColor = PosColors.Alert,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 4: Expected Cash Highlight Card (physical drawer balance)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = PosColors.BakeryBrown),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = strings.expectedCash,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.SecondaryLight
                    )
                    Text(
                        text = strings.text("(Carte/TPE exclue du tiroir)", "(Card/TPE excluded from drawer)", "(مستثنى منها دفعات البطاقة)"),
                        fontSize = 10.sp,
                        color = PosColors.SecondaryLight.copy(alpha = 0.85f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${MoneyRules.formatFixed(expectedPhysicalCash)} ${strings.currency}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text("💵", fontSize = 20.sp)
                }
            }
        }
    }
}

/**
 * Compact Session Metric Card
 */
@Composable
private fun CompactSessionMetricCard(
    icon: String,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = PosColors.TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(icon, fontSize = 18.sp)
        }
    }
}

/**
 * Cash Movements Journal Section (Left column bottom)
 */
@Composable
private fun CashMovementsJournalSection(
    cashMovements: List<CashMovement>,
    timeFormat: SimpleDateFormat,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PosColors.Workspace),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Journal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🪙", fontSize = 16.sp)
                    Text(
                        strings.text("Journal des mouvements", "Cash movements log", "سجل حركات الصندوق"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PosColors.Surface,
                    border = BorderStroke(1.dp, PosColors.Border)
                ) {
                    Text(
                        text = "${cashMovements.size} " + strings.text("mouvement(s)", "movement(s)", "حركة"),
                        fontSize = 11.sp,
                        color = PosColors.TextMuted,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Journal List or Empty State
            if (cashMovements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("🪙", fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.text(
                                "Aucun mouvement de caisse dans cette session",
                                "No cash movements recorded in this session",
                                "لا توجد حركات صندوق في هذه الجلسة"
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.text(
                                "Utilisez les boutons « + Entrée » ou « - Sortie » en haut.",
                                "Use the \"+ Cash In\" or \"- Cash Out\" buttons above.",
                                "استخدم أزرار الإيداع والسحب في الأعلى."
                            ),
                            fontSize = 11.sp,
                            color = PosColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val movementsListState = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = movementsListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 6.dp)
                            .touchDragScroll(movementsListState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(cashMovements, key = { it.id }) { movement ->
                            SessionCashMovementCard(
                                movement = movement,
                                timeFormat = timeFormat,
                                strings = strings
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(movementsListState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Compact Cash Movement Item Card
 */
@Composable
private fun SessionCashMovementCard(
    movement: CashMovement,
    timeFormat: SimpleDateFormat,
    strings: DesktopStrings
) {
    val isCashIn = movement.type == CashMovementType.CASH_IN
    val timeStr = if (movement.createdAtEpochMilliseconds > 0L) {
        timeFormat.format(Date(movement.createdAtEpochMilliseconds))
    } else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCashIn) PosColors.SuccessLight else PosColors.AlertLight,
                    border = BorderStroke(
                        1.dp,
                        if (isCashIn) PosColors.Success.copy(alpha = 0.4f) else PosColors.Alert.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (isCashIn) "📥 " + strings.cashIn else "📤 " + strings.cashOut,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCashIn) PosColors.Success else PosColors.Alert,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = movement.reason,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (timeStr.isNotBlank()) {
                            Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                color = PosColors.TextMuted
                            )
                        }
                        if (movement.createdByUserId > 0) {
                            Text(
                                text = "· " + strings.text("Caissier #${movement.createdByUserId}", "User #${movement.createdByUserId}", "مستخدم #${movement.createdByUserId}"),
                                fontSize = 10.sp,
                                color = PosColors.TextMuted
                            )
                        }
                        if (!movement.description.isNullOrBlank()) {
                            Text(
                                text = "· ${movement.description}",
                                fontSize = 10.sp,
                                color = PosColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "${if (isCashIn) "+" else "-"}${MoneyRules.formatFixed(movement.amountCentimes)} ${strings.currency}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCashIn) PosColors.Success else PosColors.Alert
            )
        }
    }
}

/**
 * Session Sales Section (Right column)
 */
@Composable
private fun SessionSalesSection(
    sessionSales: List<SalesHistoryRow>,
    confirmedSales: List<SalesHistoryRow>,
    cancelledSales: List<SalesHistoryRow>,
    timeFormat: SimpleDateFormat,
    strings: DesktopStrings,
    onNavigateToReceipt: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PosColors.Workspace),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🧾", fontSize = 16.sp)
                    Text(
                        text = strings.text("Ventes de la session", "Session sales", "مبيعات الجلسة"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PosColors.Surface,
                        border = BorderStroke(1.dp, PosColors.Border)
                    ) {
                        Text(
                            text = "${sessionSales.size} " + strings.text("vente(s)", "sale(s)", "عملية"),
                            fontSize = 11.sp,
                            color = PosColors.TextMuted,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (confirmedSales.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PosColors.SuccessLight,
                            border = BorderStroke(1.dp, PosColors.Success.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${confirmedSales.size} " + strings.text("payée(s)", "paid", "مسددة"),
                                fontSize = 11.sp,
                                color = PosColors.Success,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (cancelledSales.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PosColors.DangerLight,
                            border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${cancelledSales.size} " + strings.text("annulée(s)", "cancelled", "ملغاة"),
                                fontSize = 11.sp,
                                color = PosColors.Danger,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Sales Content
            if (sessionSales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("🧾", fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.text(
                                "Aucune vente enregistrée dans cette session",
                                "No sales recorded in this session",
                                "لا توجد مبيعات مسجلة في هذه الجلسة"
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.text(
                                "Les ventes de cette session apparaîtront ici.",
                                "Sales in this session will appear here.",
                                "ستظهر المبيعات الخاصة بهذه الجلسة هنا."
                            ),
                            fontSize = 11.sp,
                            color = PosColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val salesListState = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = salesListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 6.dp)
                            .touchDragScroll(salesListState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Confirmed / Paid Sales
                        items(confirmedSales, key = { it.order.id }) { row ->
                            SessionSaleCard(
                                row = row,
                                isCancelled = false,
                                timeFormat = timeFormat,
                                strings = strings,
                                onViewReceipt = { onNavigateToReceipt(row.order) }
                            )
                        }

                        // Cancelled Sales
                        if (cancelledSales.isNotEmpty()) {
                            if (confirmedSales.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = strings.text("Ventes annulées", "Cancelled sales", "المبيعات الملغاة"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.Danger
                                    )
                                }
                            }
                            items(cancelledSales, key = { it.order.id }) { row ->
                                SessionSaleCard(
                                    row = row,
                                    isCancelled = true,
                                    timeFormat = timeFormat,
                                    strings = strings,
                                    onViewReceipt = null
                                )
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(salesListState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Compact, Information-Dense Session Sale Card
 */
@Composable
private fun SessionSaleCard(
    row: SalesHistoryRow,
    isCancelled: Boolean,
    timeFormat: SimpleDateFormat,
    strings: DesktopStrings,
    onViewReceipt: (() -> Unit)?
) {
    val order = row.order
    val paymentLabel = when (row.paymentMethod) {
        PaymentMethod.CASH -> "💵 " + strings.cash
        PaymentMethod.CARD -> "💳 " + strings.text("Carte", "Card", "بطاقة")
        PaymentMethod.CARNET_CLIENT -> "📖 " + strings.text("Carnet", "Credit", "دفتر")
        PaymentMethod.MOBILE_QR -> "📱 " + strings.text("Mobile/QR", "Mobile/QR", "محمول")
        null -> strings.text("Non réglé", "Unpaid", "غير مسدد")
    }

    val timeMs = row.paidAtEpochMillis ?: order.createdAtEpochMilliseconds
    val timeStr = if (timeMs > 0L) timeFormat.format(Date(timeMs)) else ""

    val totalItems = order.lines.sumOf { it.quantity }
    val productSummary = remember(order.lines) {
        if (order.lines.isEmpty()) {
            ""
        } else {
            val maxToShow = 2
            val summary = order.lines.take(maxToShow).joinToString(", ") { "${it.quantity}x ${it.name}" }
            val extra = order.lines.size - maxToShow
            if (extra > 0) "$summary (+ $extra)" else summary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) PosColors.DangerLight.copy(alpha = 0.25f) else PosColors.Surface
        ),
        border = BorderStroke(
            1.dp,
            if (isCancelled) PosColors.Danger.copy(alpha = 0.35f) else PosColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Line 1: [Badge Payée/Annulée] Order # · Time · Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isCancelled) PosColors.DangerLight else PosColors.SuccessLight,
                        border = BorderStroke(
                            1.dp,
                            if (isCancelled) PosColors.Danger.copy(alpha = 0.4f) else PosColors.Success.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (isCancelled) strings.text("Annulée", "Cancelled", "ملغاة")
                            else strings.text("Payée", "Paid", "مسددة"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCancelled) PosColors.Danger else PosColors.Success,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = order.number,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                }

                Text(
                    text = if (timeStr.isNotBlank()) "$timeStr · $paymentLabel" else paymentLabel,
                    fontSize = 11.sp,
                    color = PosColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            // Line 2: Product summary
            if (productSummary.isNotBlank()) {
                Text(
                    text = productSummary,
                    fontSize = 11.sp,
                    color = PosColors.TextMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Line 3: Items count · Total amount · Receipt button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$totalItems " + strings.text("art.", "items", "عنصر"),
                        fontSize = 11.sp,
                        color = PosColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) PosColors.Danger else PosColors.TextHigh
                    )
                }

                if (onViewReceipt != null) {
                    OutlinedButton(
                        onClick = onViewReceipt,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary),
                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(28.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🧾 " + strings.text("Reçu", "Receipt", "إيصال"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cash Movement Dialog Modal
 */
@Composable
private fun CashMovementDialogModal(
    type: CashMovementType,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    reasonInput: String,
    onReasonChange: (String) -> Unit,
    strings: DesktopStrings,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val isCashIn = type == CashMovementType.CASH_IN
    val title = if (isCashIn) {
        "📥 " + strings.text("Entrée d'espèces (Apport de monnaie)", "Cash In (Add float)", "إيداع نقدي في الصندوق")
    } else {
        "📤 " + strings.text("Sortie d'espèces (Retrait de caisse)", "Cash Out (Withdrawal)", "سحب نقدي من الصندوق")
    }

    val parsedCentimes = (MoneyRules.parseToCentimes(amountInput) as? MoneyParseResult.Success)?.centimes ?: 0L
    var amountError by remember { mutableStateOf<String?>(null) }
    var reasonError by remember { mutableStateOf<String?>(null) }

    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        amountFocusRequester.requestFocus()
    }

    fun submit() {
        var hasError = false
        if (parsedCentimes <= 0L) {
            amountError = strings.text("Le montant doit être supérieur à 0", "Amount must be greater than 0", "يجب أن يكون المبلغ أكبر من 0")
            hasError = true
        }
        if (reasonInput.trim().isBlank()) {
            reasonError = strings.text("Le motif est obligatoire", "Reason is required", "السبب مطلوب")
            hasError = true
        }
        if (!hasError) {
            onConfirm(parsedCentimes, reasonInput.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    Key.Enter -> {
                        submit()
                        true
                    }
                    else -> false
                }
            } else false
        },
        title = {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.TextHigh
            )
        },
        text = {
            val dialogScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .touchDragScroll(dialogScrollState)
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isCashIn) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.AlertLight,
                        border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.text(
                                "⚠️ Ce montant sera déduit du montant théorique attendu en caisse.",
                                "⚠️ This amount will be deducted from the expected cash balance.",
                                "⚠️ سيتم خصم هذا المبلغ من الرصيد المتوقع في الصندوق."
                            ),
                            color = PosColors.Alert,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Montant Field
                TouchNumericField(
                    value = amountInput,
                    onValueChange = {
                        onAmountChange(it)
                        amountError = null
                    },
                    label = strings.text("Montant (${strings.currency}) *", "Amount (${strings.currency}) *", "المبلغ (${strings.currency}) *"),
                    placeholder = "0.00",
                    currencySymbol = strings.currency,
                    isDecimalAllowed = true,
                    errorMessage = amountError,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester)
                )

                // Quick reason presets
                val quickReasons = if (isCashIn) {
                    listOf("Apport monnaie", "Fonds initial", "Autre apport")
                } else {
                    listOf("Achat fournitures", "Facture fournisseur", "Retrait espèce", "Autre dépense")
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.text("Motifs rapides :", "Quick reasons:", "أسباب سريعة:"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickReasons.forEach { quickReason ->
                            OutlinedButton(
                                onClick = {
                                    onReasonChange(quickReason)
                                    reasonError = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(quickReason, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // Motif Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TouchTextField(
                        value = reasonInput,
                        onValueChange = {
                            onReasonChange(it)
                            reasonError = null
                        },
                        label = strings.movementReason + " *",
                        placeholder = if (isCashIn) strings.text("Ex: Apport monnaie d'ouverture...", "Ex: Morning float top-up...", "مثال: إيداع الصرف الصباحي...")
                        else strings.text("Ex: Facture lait, Achat pain...", "Ex: Milk invoice, bread purchase...", "مثال: شراء الحليب، الخبز..."),
                        singleLine = true,
                        errorMessage = reasonError,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = strings.text(
                            "Précisez la raison de ce mouvement de fonds pour le journal de caisse.",
                            "Specify the business reason for the cash register audit log.",
                            "حدد سبب الحركة المالية لسجل المراجعة."
                        ),
                        fontSize = 10.sp,
                        color = PosColors.TextMuted
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCashIn) PosColors.Primary else PosColors.Alert),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(strings.confirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(strings.cancel)
            }
        }
    )
}
