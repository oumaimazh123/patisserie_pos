package ma.elaroui.pos.desktop.presentation.register.current

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.shared.domain.CashMovement
import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSession
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage

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
                        .height(48.dp)
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
                        .height(48.dp)
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
                            .height(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("🔒 " + strings.closeRegisterAction, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Notifications / Status Banners (Typed Alert)
                PosInlineAlert(
                    message = effectiveUiMessage,
                    onDismiss = { localUiMessage = null; onClearMessage() }
                )

                // Summary Financial KPI Cards Row 1: Sales & Opening
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 220.dp
                ) {
                    SessionMetricCard(
                        icon = "🪙",
                        label = strings.openingCash,
                        value = "${MoneyRules.formatFixed(openingCash)} ${strings.currency}",
                        accentColor = PosColors.BakeryBrown,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SessionMetricCard(
                        icon = "💵",
                        label = strings.cashSales,
                        value = "${MoneyRules.formatFixed(cashSalesCentimes)} ${strings.currency}",
                        accentColor = PosColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SessionMetricCard(
                        icon = "💳",
                        label = strings.cardSales,
                        value = "${MoneyRules.formatFixed(cardSalesCentimes)} ${strings.currency}",
                        accentColor = PosColors.SecondaryDark,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SessionMetricCard(
                        icon = "📊",
                        label = strings.totalSales,
                        value = "${MoneyRules.formatFixed(totalSales)} ${strings.currency}",
                        accentColor = PosColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Summary Financial KPI Cards Row 2: Movements & Expected Physical Cash
                ResponsiveFlowGrid(
                    modifier = Modifier.fillMaxWidth(),
                    minItemWidth = 260.dp
                ) {
                    SessionMetricCard(
                        icon = "📥",
                        label = strings.cashIn,
                        value = "+${MoneyRules.formatFixed(totalCashIn)} ${strings.currency}",
                        accentColor = PosColors.Success,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SessionMetricCard(
                        icon = "📤",
                        label = strings.cashOut,
                        value = "-${MoneyRules.formatFixed(totalCashOut)} ${strings.currency}",
                        accentColor = PosColors.Alert,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Expected Cash Highlight Card (Explicit physical drawer balance, card excluded)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
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
                                Column {
                                    Text(
                                        strings.expectedCash,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosColors.SecondaryLight
                                    )
                                    Text(
                                        strings.text("(Carte/TPE exclue du tiroir)", "(Card/TPE excluded from drawer)", "(مستثنى منها دفعات البطاقة)"),
                                        fontSize = 10.sp,
                                        color = PosColors.SecondaryLight.copy(alpha = 0.8f)
                                    )
                                }
                                Text("💵", fontSize = 22.sp)
                            }
                            Text(
                                "${MoneyRules.formatFixed(expectedPhysicalCash)} ${strings.currency}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Section: Ventes de la session
                val confirmedSales = remember(sessionSales) { sessionSales.filter { it.order.status == OrderStatus.COMPLETED } }
                val cancelledSales = remember(sessionSales) { sessionSales.filter { it.order.status == OrderStatus.CANCELLED } }
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.text("Ventes de la session", "Session sales", "مبيعات الجلسة"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )

                    Text(
                        "${sessionSales.size} " + strings.text("vente(s)", "sale(s)", "عملية"),
                        fontSize = 13.sp,
                        color = PosColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (sessionSales.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PosColors.Surface,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🧾", fontSize = 28.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                strings.text(
                                    "Aucune vente enregistrée dans cette session",
                                    "No sales recorded in this session",
                                    "لا توجد مبيعات مسجلة في هذه الجلسة"
                                ),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                        }
                    }
                } else {
                    val salesScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(salesScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Confirmed / Paid Sales
                        if (confirmedSales.isNotEmpty()) {
                            confirmedSales.forEach { row ->
                                SessionSaleCard(
                                    row = row,
                                    isCancelled = false,
                                    timeFormat = timeFormat,
                                    strings = strings,
                                    onViewReceipt = { onNavigateToReceipt(row.order) }
                                )
                            }
                        }

                        // Cancelled Sales
                        if (cancelledSales.isNotEmpty()) {
                            if (confirmedSales.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    strings.text("Ventes annulées", "Cancelled sales", "المبيعات الملغاة"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.Danger
                                )
                            }
                            cancelledSales.forEach { row ->
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
                }

                Spacer(Modifier.height(8.dp))

                // Cash Movements History Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.text("Journal des mouvements de caisse", "Cash movements log", "سجل حركات الصندوق"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )

                    Text(
                        "${cashMovements.size} " + strings.text("mouvement(s)", "movement(s)", "حركة"),
                        fontSize = 13.sp,
                        color = PosColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Movements List or Polished Empty State
                if (cashMovements.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PosColors.Surface,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🪙", fontSize = 36.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                strings.text(
                                    "Aucun mouvement de caisse enregistré pour cette session",
                                    "No cash movements recorded for this session",
                                    "لا توجد أي حركات صندوق مسجلة لهذه الجلسة"
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.text(
                                    "Utilisez les boutons « + Entrée d'espèces » ou « - Sortie d'espèces » ci-dessus pour enregistrer un apport de monnaie ou un retrait de fonds.",
                                    "Use the \"+ Cash In\" or \"- Cash Out\" buttons above to record float top-up or cash withdrawals.",
                                    "استخدم الأزرار أعلاه لتسجيل إيداع سيولة أو سحب مالي."
                                ),
                                fontSize = 13.sp,
                                color = PosColors.TextMuted
                            )
                        }
                    }
                } else {
                    val movementsListState = remember { androidx.compose.foundation.lazy.LazyListState() }
                    LazyColumn(
                        state = movementsListState,
                        modifier = Modifier
                            .weight(1f)
                            .touchDragScroll(movementsListState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cashMovements) { movement ->
                            val isCashIn = movement.type == CashMovementType.CASH_IN

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                                border = BorderStroke(1.dp, PosColors.Border),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCashIn) PosColors.SuccessLight else PosColors.AlertLight,
                                            border = BorderStroke(1.dp, if (isCashIn) PosColors.Success.copy(alpha = 0.4f) else PosColors.Alert.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                if (isCashIn) "● " + strings.cashIn else "○ " + strings.cashOut,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCashIn) PosColors.Success else PosColors.Alert,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                movement.reason,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PosColors.TextHigh
                                            )
                                            if (!movement.description.isNullOrBlank()) {
                                                Text(
                                                    movement.description!!,
                                                    fontSize = 12.sp,
                                                    color = PosColors.TextMuted
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        "${if (isCashIn) "+" else "-"}${MoneyRules.formatFixed(movement.amountCentimes)} ${strings.currency}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCashIn) PosColors.Success else PosColors.Danger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cash Movement Dialog Modal
    showMovementDialog?.let { type ->
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

        fun confirmMovement() {
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
                onCashMovementSubmitted(type, parsedCentimes, reasonInput.trim())
                val formattedAmount = MoneyRules.formatFixed(parsedCentimes)
                localUiMessage = UiMessage.success(if (isCashIn) {
                    strings.text("Entrée de $formattedAmount ${strings.currency} enregistrée avec succès", "Cash in of $formattedAmount ${strings.currency} recorded", "تم تسجيل الإيداع بنجاح")
                } else {
                    strings.text("Sortie de $formattedAmount ${strings.currency} enregistrée avec succès", "Cash out of $formattedAmount ${strings.currency} recorded", "تم تسجيل السحب بنجاح")
                })
                showMovementDialog = null
            }
        }

        AlertDialog(
            onDismissRequest = { showMovementDialog = null },
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape -> {
                            showMovementDialog = null
                            true
                        }
                        Key.Enter -> {
                            confirmMovement()
                            true
                        }
                        else -> false
                    }
                } else false
            },
            title = {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.TextHigh
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .touchDragScroll(dialogScrollState)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!isCashIn) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PosColors.AlertLight,
                            border = BorderStroke(1.dp, PosColors.Alert.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                strings.text(
                                    "⚠️ Ce montant sera déduit du montant théorique attendu en caisse.",
                                    "⚠️ This amount will be deducted from the expected cash balance.",
                                    "⚠️ سيتم خصم هذا المبلغ من الرصيد المتوقع في الصندوق."
                                ),
                                color = PosColors.Alert,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Montant (DH) Field
                    TouchNumericField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
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
                            strings.text("Motifs rapides :", "Quick reasons:", "أسباب سريعة:"),
                            fontSize = 12.sp,
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
                                        reasonInput = quickReason
                                        reasonError = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(quickReason, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Motif Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TouchTextField(
                            value = reasonInput,
                            onValueChange = {
                                reasonInput = it
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
                            strings.text(
                                "Précisez la raison de ce mouvement de fonds pour le journal de caisse.",
                                "Specify the business reason for the cash register audit log.",
                                "حدد سبب الحركة المالية لسجل المراجعة."
                            ),
                            fontSize = 11.sp,
                            color = PosColors.TextMuted
                        )
                    }

                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmMovement() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isCashIn) PosColors.Primary else PosColors.Alert),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showMovementDialog = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SessionMetricCard(
    icon: String,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosColors.TextMuted
                )
                Text(icon, fontSize = 18.sp)
            }
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

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
        PaymentMethod.CASH -> strings.cash
        PaymentMethod.CARD -> strings.text("Carte bancaire", "Bank Card", "بطاقة بنكية")
        PaymentMethod.CARNET_CLIENT -> strings.text("Carnet client", "Customer Credit", "دفتر الزبون")
        PaymentMethod.MOBILE_QR -> strings.text("Mobile / QR", "Mobile / QR", "محمول / QR")
        null -> strings.text("Non réglé", "Unpaid", "غير مسدد")
    }

    val timeMs = row.paidAtEpochMillis ?: order.createdAtEpochMilliseconds
    val timeStr = timeFormat.format(Date(timeMs))

    val totalItems = order.lines.sumOf { it.quantity }
    val maxPreviewLines = 3
    val previewLines = order.lines.take(maxPreviewLines)
    val remainingCount = order.lines.size - maxPreviewLines

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) PosColors.DangerLight.copy(alpha = 0.35f) else PosColors.Surface
        ),
        border = BorderStroke(
            1.dp,
            if (isCancelled) PosColors.Danger.copy(alpha = 0.3f) else PosColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: [Badge] Ref + Time · Payment
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
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCancelled) PosColors.DangerLight else PosColors.SuccessLight,
                        border = BorderStroke(1.dp, if (isCancelled) PosColors.Danger.copy(alpha = 0.4f) else PosColors.Success.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isCancelled) strings.text("Annulée", "Cancelled", "ملغاة")
                            else strings.text("Payée", "Paid", "مسددة"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCancelled) PosColors.Danger else PosColors.Success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = order.number,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                }

                Text(
                    text = "$timeStr · $paymentLabel",
                    fontSize = 12.sp,
                    color = PosColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            // Products lines preview
            if (order.lines.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    previewLines.forEach { line ->
                        Text(
                            text = "${line.quantity} x ${line.name}",
                            fontSize = 12.sp,
                            color = PosColors.TextHigh,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    if (remainingCount > 0) {
                        Text(
                            text = "+ $remainingCount " + strings.text("autre(s) article(s)", "other article(s)", "عنصر آخر"),
                            fontSize = 11.sp,
                            color = PosColors.TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Footer Row: Count of articles + Total Amount + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "$totalItems " + strings.text("article(s)", "article(s)", "عنصر"),
                        fontSize = 12.sp,
                        color = PosColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) PosColors.Danger else PosColors.TextHigh
                    )
                }

                if (onViewReceipt != null) {
                    OutlinedButton(
                        onClick = onViewReceipt,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary),
                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(34.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = strings.text("Voir le reçu", "View receipt", "عرض الإيصال"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

