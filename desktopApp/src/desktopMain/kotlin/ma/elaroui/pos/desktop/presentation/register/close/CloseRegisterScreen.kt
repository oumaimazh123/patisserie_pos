@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.register.close

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.DiscrepancyBadge
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.rules.DiscrepancyKind
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.RegisterClosingRules

@Composable
fun CloseRegisterScreen(
    openingCashCentimes: Long,
    cashSalesCentimes: Long,
    cardSalesCentimes: Long,
    cashInCentimes: Long,
    cashOutCentimes: Long,
    expectedCashCentimes: Long,
    activeOrdersCount: Int,
    cashierName: String = "",
    strings: DesktopStrings,
    onCloseRegisterSubmitted: (input: RegisterClosingInput) -> Unit,
    onNavigateToActiveOrders: (() -> Unit)? = null,
    onBack: () -> Unit,
    errorMessage: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var countedCashInput by remember { mutableStateOf("") }
    var leftInDrawerInput by remember { mutableStateOf("") }
    var removedAmountInput by remember { mutableStateOf("") }
    var userManuallyEditedRemoved by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var autoEnvelopeReference by remember(cashierName) {
        mutableStateOf(RegisterClosingHelper.generateEnvelopeReference(cashierName))
    }

    var selectedDestination by remember { mutableStateOf("Coffre") }
    var customDestinationInput by remember { mutableStateOf("") }
    var closingNoteInput by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    var countedCashError by remember { mutableStateOf<String?>(null) }
    var customDestinationError by remember { mutableStateOf<String?>(null) }
    val backendError = uiMessage?.takeIf { it.isError }?.text ?: errorMessage.takeIf { it.isNotBlank() }

    val totalSalesCentimes = cashSalesCentimes + cardSalesCentimes

    val parsedCounted = (MoneyRules.parseToCentimes(countedCashInput) as? MoneyParseResult.Success)?.centimes
    val parsedLeft = (MoneyRules.parseToCentimes(leftInDrawerInput) as? MoneyParseResult.Success)?.centimes
    val parsedRemoved = (MoneyRules.parseToCentimes(removedAmountInput) as? MoneyParseResult.Success)?.centimes

    val hasWithdrawal = (parsedRemoved ?: 0L) > 0L

    val sumBreakdown = (parsedLeft ?: 0L) + (parsedRemoved ?: 0L)
    val isBreakdownValid = parsedCounted != null && parsedLeft != null && parsedRemoved != null &&
            parsedLeft >= 0L && parsedRemoved >= 0L && sumBreakdown == parsedCounted

    val diffCentimes = (parsedCounted ?: 0L) - expectedCashCentimes
    val diffKind = if (parsedCounted != null) RegisterClosingRules.classifyDifference(diffCentimes) else DiscrepancyKind.EXACT

    val destinationValue = if (selectedDestination == "Autre") customDestinationInput.trim() else selectedDestination
    val isDestinationValid = !hasWithdrawal || destinationValue.isNotBlank()

    val hasActiveOrders = activeOrdersCount > 0
    val canSubmit = !hasActiveOrders && isBreakdownValid && isDestinationValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.closeRegisterTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← ${strings.back}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PosColors.BakeryBrown)
            )
        }
    ) { padding ->
        val closeScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PosColors.Canvas)
                .touchDragScroll(closeScrollState)
                .verticalScroll(closeScrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiMessage != null && !uiMessage.isError) {
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = onClearMessage
                    )
                }

                // Active orders blocking alert
                if (hasActiveOrders) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PosColors.AlertLight),
                        border = BorderStroke(1.5.dp, PosColors.Alert),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("🛑", fontSize = 24.sp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        strings.text(
                                            "Clôture impossible : $activeOrdersCount commande(s) active(s) en cours",
                                            "Closing blocked: $activeOrdersCount active order(s) pending",
                                            "التحصيل معلق: توجد $activeOrdersCount طلبات نشطة"
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        strings.text(
                                            "Veuillez encaisser ou annuler toutes les commandes de la session avant de pouvoir clôturer la caisse.",
                                            "Please complete or cancel all open orders before closing the register session.",
                                            "يرجى استكمال أو إلغاء جميع الطلبات المفتوحة قبل إغلاق الصندوق."
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.Alert
                                    )
                                }
                            }

                            if (onNavigateToActiveOrders != null) {
                                Button(
                                    onClick = onNavigateToActiveOrders,
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Alert),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.activeOrders, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 1. Session Financial Summary Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            strings.text("Récapitulatif financier de la session", "Session Financial Summary", "ملخص الحساب المالي للجلسة"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PosColors.BakeryBrown
                        )

                        // Highlight Expected Physical Cash Card - Full Width Responsive Hero Banner
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isCompact = maxWidth < 540.dp
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PosColors.BakeryBrown,
                                border = BorderStroke(1.dp, PosColors.PrimaryDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isCompact) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                strings.expectedCash,
                                                fontSize = 15.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Black.copy(alpha = 0.25f)
                                            ) {
                                                Text(
                                                    "${MoneyRules.formatFixed(expectedCashCentimes)} ${strings.currency}",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = PosColors.Honey,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        Text(
                                            strings.text(
                                                "Fond initial + Ventes espèces + Entrées - Sorties (Carte/TPE exclue)",
                                                "Opening cash + Cash sales + Cash In - Cash Out (Card/TPE excluded)",
                                                "رصيد البداية + مبيعات نقداً + إيداعات - سحوبات (البطاقة مستثناة)"
                                            ),
                                            fontSize = 11.sp,
                                            color = PosColors.SecondaryLight,
                                            lineHeight = 15.sp
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                strings.expectedCash,
                                                fontSize = 16.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                strings.text(
                                                    "Fond initial + Ventes espèces + Entrées - Sorties (Carte/TPE exclue)",
                                                    "Opening cash + Cash sales + Cash In - Cash Out (Card/TPE excluded)",
                                                    "رصيد البداية + مبيعات نقداً + إيداعات - سحوبات (البطاقة مستثناة)"
                                                ),
                                                fontSize = 11.sp,
                                                color = PosColors.SecondaryLight,
                                                lineHeight = 15.sp
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                "${MoneyRules.formatFixed(expectedCashCentimes)} ${strings.currency}",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PosColors.Honey,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Operational metrics in balanced grid
                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 135.dp,
                            horizontalSpacing = 10.dp,
                            verticalSpacing = 10.dp
                        ) {
                            SummaryItemBox(strings.openingCash, "${MoneyRules.formatFixed(openingCashCentimes)} ${strings.currency}", PosColors.BakeryBrown, Modifier.fillMaxWidth())
                            SummaryItemBox(strings.cashSales, "${MoneyRules.formatFixed(cashSalesCentimes)} ${strings.currency}", PosColors.Success, Modifier.fillMaxWidth())
                            SummaryItemBox(strings.cardSales, "${MoneyRules.formatFixed(cardSalesCentimes)} ${strings.currency}", PosColors.SecondaryDark, Modifier.fillMaxWidth())
                            SummaryItemBox(strings.totalSales, "${MoneyRules.formatFixed(totalSalesCentimes)} ${strings.currency}", PosColors.Primary, Modifier.fillMaxWidth())
                            SummaryItemBox(strings.cashIn, "+${MoneyRules.formatFixed(cashInCentimes)} ${strings.currency}", PosColors.Success, Modifier.fillMaxWidth())
                            SummaryItemBox(strings.cashOut, "-${MoneyRules.formatFixed(cashOutCentimes)} ${strings.currency}", PosColors.Alert, Modifier.fillMaxWidth())
                        }
                    }
                }

                // 2. Cash Count & Discrepancy Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            strings.text("1. Comptage physique des espèces", "1. Physical Cash Count", "1. العد الفعلي للنقد"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PosColors.BakeryBrown
                        )

                        TouchNumericField(
                            value = countedCashInput,
                            onValueChange = { newCountedStr ->
                                countedCashInput = newCountedStr
                                countedCashError = null
                                if (errorMessage.isNotBlank() || uiMessage != null) onClearMessage()
                                val newCountedParsed = (MoneyRules.parseToCentimes(newCountedStr) as? MoneyParseResult.Success)?.centimes
                                if (!userManuallyEditedRemoved && newCountedParsed != null && parsedLeft != null) {
                                    val autoRemoved = RegisterClosingHelper.calculateAutoRemovedCentimes(newCountedParsed, parsedLeft) ?: 0L
                                    removedAmountInput = MoneyRules.formatFixed(autoRemoved)
                                }
                            },
                            label = strings.countedCash + " *",
                            placeholder = "0.00",
                            currencySymbol = strings.currency,
                            isDecimalAllowed = true,
                            errorMessage = countedCashError ?: backendError,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Live Difference / Discrepancy Badge Row
                        if (parsedCounted != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when (diffKind) {
                                    DiscrepancyKind.EXACT -> PosColors.SuccessLight
                                    DiscrepancyKind.SURPLUS -> PosColors.PrimaryLight
                                    DiscrepancyKind.SHORTAGE -> PosColors.DangerLight
                                },
                                border = BorderStroke(1.dp, when (diffKind) {
                                    DiscrepancyKind.EXACT -> PosColors.Success
                                    DiscrepancyKind.SURPLUS -> PosColors.PrimaryDark
                                    DiscrepancyKind.SHORTAGE -> PosColors.Danger
                                }),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            when (diffKind) {
                                                DiscrepancyKind.EXACT -> "✅ " + strings.exactMatch
                                                DiscrepancyKind.SURPLUS -> "📈 " + strings.surplus
                                                DiscrepancyKind.SHORTAGE -> "📉 " + strings.deficit
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = when (diffKind) {
                                                DiscrepancyKind.EXACT -> PosColors.Success
                                                DiscrepancyKind.SURPLUS -> PosColors.PrimaryDark
                                                DiscrepancyKind.SHORTAGE -> PosColors.Danger
                                            }
                                        )
                                    }

                                    Text(
                                        "${if (diffCentimes > 0) "+" else ""}${MoneyRules.formatFixed(diffCentimes)} ${strings.currency}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (diffKind) {
                                            DiscrepancyKind.EXACT -> PosColors.Success
                                            DiscrepancyKind.SURPLUS -> PosColors.PrimaryDark
                                            DiscrepancyKind.SHORTAGE -> PosColors.Danger
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Breakdown & Remittance Section (Left in drawer + Removed in envelope = Counted)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                strings.text("2. Répartition du fond et remise", "2. Float & Remittance Breakdown", "2. توزيع الرصيد والتحويل"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = PosColors.BakeryBrown
                            )

                            // Quick Fill actions if counted is available
                            if (parsedCounted != null && parsedCounted > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            leftInDrawerInput = countedCashInput
                                            removedAmountInput = "0"
                                            userManuallyEditedRemoved = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(strings.text("Tout laisser en caisse", "Keep all in drawer", "إبقاء الكل بالصندوق"), fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            leftInDrawerInput = "0"
                                            removedAmountInput = countedCashInput
                                            userManuallyEditedRemoved = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(strings.text("Tout retirer", "Withdraw all", "سحب الكل"), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left in drawer input
                            TouchNumericField(
                                value = leftInDrawerInput,
                                onValueChange = { newLeftStr ->
                                    leftInDrawerInput = newLeftStr
                                    val newLeftParsed = (MoneyRules.parseToCentimes(newLeftStr) as? MoneyParseResult.Success)?.centimes
                                    if (!userManuallyEditedRemoved && parsedCounted != null && newLeftParsed != null) {
                                        val autoRemoved = RegisterClosingHelper.calculateAutoRemovedCentimes(parsedCounted, newLeftParsed) ?: 0L
                                        removedAmountInput = MoneyRules.formatFixed(autoRemoved)
                                    } else if (newLeftStr.isBlank()) {
                                        removedAmountInput = ""
                                        userManuallyEditedRemoved = false
                                    }
                                },
                                label = strings.leftInDrawer + " *",
                                placeholder = "0.00",
                                supportingText = { Text(strings.text("Sera le fond suggéré de la prochaine session", "Suggested opening float for next session", "سيكون المبلغ المقترح للجلسة القادمة")) },
                                currencySymbol = strings.currency,
                                isDecimalAllowed = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )

                            // Removed amount input (editable manually)
                            TouchNumericField(
                                value = removedAmountInput,
                                onValueChange = {
                                    removedAmountInput = it
                                    userManuallyEditedRemoved = true
                                },
                                label = strings.removedAmount + " *",
                                placeholder = "0.00",
                                supportingText = { Text(strings.text("Montant prélevé ou mis en enveloppe", "Amount taken or placed in envelope", "المبلغ المودع في الظرف أو الخزنة")) },
                                currencySymbol = strings.currency,
                                isDecimalAllowed = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Real-time Breakdown Status Indicator
                        if (parsedCounted != null) {
                            val isLeftOverCounted = parsedLeft != null && parsedLeft > parsedCounted
                            val isMatch = !isLeftOverCounted && parsedLeft != null && parsedRemoved != null && (parsedLeft + parsedRemoved == parsedCounted)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMatch) PosColors.SuccessLight else PosColors.AlertLight,
                                border = BorderStroke(1.dp, if (isMatch) PosColors.Success else PosColors.Alert),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isMatch) "✅ " else "⚠️ ", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isLeftOverCounted) {
                                            strings.text(
                                                "Le montant laissé en fond de caisse ne peut pas dépasser les espèces comptées (${MoneyRules.formatFixed(parsedCounted)} ${strings.currency})",
                                                "The amount left in drawer cannot exceed counted cash (${MoneyRules.formatFixed(parsedCounted)} ${strings.currency})",
                                                "المبلغ المتبقي في الصندوق لا يمكن أن يتجاوز النقد المحسوب"
                                            )
                                        } else if (isMatch) {
                                            strings.text(
                                                "Équilibre parfait : ${MoneyRules.formatFixed(parsedLeft ?: 0L)} + ${MoneyRules.formatFixed(parsedRemoved ?: 0L)} = ${MoneyRules.formatFixed(parsedCounted)} ${strings.currency}",
                                                "Balanced: ${MoneyRules.formatFixed(parsedLeft ?: 0L)} + ${MoneyRules.formatFixed(parsedRemoved ?: 0L)} = ${MoneyRules.formatFixed(parsedCounted)} ${strings.currency}",
                                                "متطابق تماماً مع النقد المحسوب"
                                            )
                                        } else {
                                            strings.leftPlusRemovedMustEqualCounted +
                                                    " (Actuel : ${MoneyRules.formatFixed(sumBreakdown)} / ${MoneyRules.formatFixed(parsedCounted)} ${strings.currency})"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isMatch) PosColors.Success else PosColors.Alert
                                    )
                                }
                            }
                        }

                        // Destination selection chips (strictly visible only when withdrawal > 0)
                        if (hasWithdrawal) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.remittanceDestination, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Coffre", "Responsable", "Banque", "Autre").forEach { dest ->
                                        val isSelected = selectedDestination == dest
                                        Surface(
                                            onClick = { selectedDestination = dest },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                            border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Text(
                                                when (dest) {
                                                    "Coffre" -> "🔒 " + strings.text("Coffre", "Safe", "الخزنة")
                                                    "Responsable" -> "👤 " + strings.text("Responsable", "Manager", "المسؤول")
                                                    "Banque" -> "🏦 " + strings.text("Banque", "Bank", "البنك")
                                                    else -> "📝 " + strings.text("Autre", "Other", "أخرى")
                                                },
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else PosColors.TextMedium
                                            )
                                        }
                                    }
                                }

                                if (selectedDestination == "Autre") {
                                    TouchTextField(
                                        value = customDestinationInput,
                                        onValueChange = {
                                            customDestinationInput = it
                                            customDestinationError = null
                                            if (errorMessage.isNotBlank() || uiMessage != null) onClearMessage()
                                        },
                                        label = strings.text("Préciser la destination *", "Specify destination *", "حدد الوجهة *"),
                                        singleLine = true,
                                        errorMessage = customDestinationError,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // Envelope / Remittance reference (read-only when withdrawal > 0) & Closing note
                        if (hasWithdrawal) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PosColors.Workspace,
                                    border = BorderStroke(1.dp, PosColors.Border),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            strings.remittanceReference,
                                            fontSize = 11.sp,
                                            color = PosColors.TextLow,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("✉️", fontSize = 14.sp)
                                            Text(
                                                autoEnvelopeReference,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PosColors.BakeryBrown
                                            )
                                        }
                                    }
                                }

                                TouchTextField(
                                    value = closingNoteInput,
                                    onValueChange = { if (it.length <= 500) closingNoteInput = it },
                                    label = strings.closingNote,
                                    placeholder = strings.text("Observations, remarques...", "Remarks, notes...", "ملاحظات..."),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.5f)
                                )
                            }
                        } else {
                            TouchTextField(
                                value = closingNoteInput,
                                onValueChange = { if (it.length <= 500) closingNoteInput = it },
                                label = strings.closingNote,
                                placeholder = strings.text("Observations, remarques...", "Remarks, notes...", "ملاحظات..."),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                    }
                }

                // Confirm Close Action Button
                Button(
                    onClick = {
                        autoEnvelopeReference = RegisterClosingHelper.generateEnvelopeReference(cashierName, System.currentTimeMillis())
                        showConfirmDialog = true
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pointerHoverIcon(if (canSubmit) PointerIcon.Hand else PointerIcon.Default),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosColors.Primary,
                        disabledContainerColor = PosColors.Border
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        "🔒 " + strings.closeRegisterAction,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Final Confirmation Modal
    if (showConfirmDialog && parsedCounted != null && parsedLeft != null && parsedRemoved != null) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                isSubmitting = false
            },
            title = {
                Text(
                    strings.closeRegisterTitle,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        strings.text(
                            "Confirmez-vous la fermeture définitive de cette session de caisse ?",
                            "Do you confirm closing this register session?",
                            "هل تؤكد إغلاق جلسة الصندوق هذه نهائياً؟"
                        ),
                        fontWeight = FontWeight.Medium
                    )

                    HorizontalDivider(color = PosColors.Border)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.countedCash, color = PosColors.TextMedium, fontSize = 13.sp)
                        Text("${MoneyRules.formatFixed(parsedCounted)} ${strings.currency}", fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.leftInDrawer, color = PosColors.TextMedium, fontSize = 13.sp)
                        Text("${MoneyRules.formatFixed(parsedLeft)} ${strings.currency}", fontWeight = FontWeight.SemiBold, color = PosColors.Success)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.removedAmount, color = PosColors.TextMedium, fontSize = 13.sp)
                        Text("${MoneyRules.formatFixed(parsedRemoved)} ${strings.currency}", fontWeight = FontWeight.SemiBold, color = PosColors.Alert)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.discrepancy, color = PosColors.TextMedium, fontSize = 13.sp)
                        DiscrepancyBadge(differenceCentimes = diffCentimes)
                    }

                    if (hasWithdrawal) {
                        if (destinationValue.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(strings.remittanceDestination, color = PosColors.TextMedium, fontSize = 13.sp)
                                Text(destinationValue, fontWeight = FontWeight.Medium)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.remittanceReference, color = PosColors.TextMedium, fontSize = 13.sp)
                            Text(autoEnvelopeReference, fontWeight = FontWeight.Bold, color = PosColors.BakeryBrown)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            val input = RegisterClosingInput(
                                countedCashCentimes = parsedCounted,
                                leftInDrawerCentimes = parsedLeft,
                                removedAmountCentimes = parsedRemoved,
                                remittanceReference = if (hasWithdrawal) autoEnvelopeReference else null,
                                remittanceDestination = if (hasWithdrawal) destinationValue.takeIf { it.isNotBlank() } else null,
                                closingNote = closingNoteInput.trim().takeIf { it.isNotBlank() }
                            )
                            onCloseRegisterSubmitted(input)
                            showConfirmDialog = false
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        isSubmitting = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SummaryItemBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PosColors.Workspace,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 11.sp, color = PosColors.TextMedium, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
