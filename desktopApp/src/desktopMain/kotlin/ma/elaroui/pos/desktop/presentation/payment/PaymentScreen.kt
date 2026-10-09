@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.FieldErrorMessage
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosDimens
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun PaymentScreen(
    order: Order,
    strings: DesktopStrings,
    onPaymentSubmitted: (method: PaymentMethod, receivedCentimes: Long?) -> Unit,
    onBack: () -> Unit,
    message: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {},
    onCashAmountChanged: ((receivedCentimes: Long?, changeCentimes: Long) -> Unit)? = null,
    onPaymentMethodChanged: ((PaymentMethod) -> Unit)? = null
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var receivedCashInput by remember { mutableStateOf("") }

    val orderTotalCentimes = order.totalCentimes
    val parsedCash = (MoneyRules.parseToCentimes(receivedCashInput) as? MoneyParseResult.Success)?.centimes
    val changeCentimes = if (parsedCash != null && parsedCash > orderTotalCentimes) parsedCash - orderTotalCentimes else 0L
    val remainingCentimes = if (parsedCash == null || parsedCash < orderTotalCentimes) orderTotalCentimes - (parsedCash ?: 0L) else 0L

    LaunchedEffect(parsedCash, changeCentimes) {
        onCashAmountChanged?.invoke(parsedCash, changeCentimes)
    }

    LaunchedEffect(selectedMethod) {
        onPaymentMethodChanged?.invoke(selectedMethod)
    }

    val isPaymentValid = selectedMethod != PaymentMethod.CASH || (parsedCash != null && parsedCash >= orderTotalCentimes)

    fun onKeypadPress(key: String) {
        if (message.isNotBlank() || uiMessage != null) {
            onClearMessage()
        }
        receivedCashInput = when (key) {
            "⌫" -> if (receivedCashInput.isNotEmpty()) receivedCashInput.dropLast(1) else ""
            "." -> {
                if (receivedCashInput.isEmpty()) "0."
                else if (!receivedCashInput.contains(".")) "$receivedCashInput."
                else receivedCashInput
            }
            else -> { // Digits 0-9
                if (receivedCashInput == "0" && key == "0") receivedCashInput
                else if (receivedCashInput == "0" && key != ".") key
                else {
                    val dotIdx = receivedCashInput.indexOf('.')
                    if (dotIdx != -1 && receivedCashInput.length - dotIdx > 2) {
                        receivedCashInput // Max 2 decimal digits
                    } else if (receivedCashInput.length >= 8) {
                        receivedCashInput
                    } else {
                        receivedCashInput + key
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.paymentTitle,
                            color = PosColors.TextHigh,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = order.number,
                            color = PosColors.TextMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = PosColors.TextHigh),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(start = 8.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("← ${strings.back}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PosColors.Canvas)
        ) {
            val isWideLayout = maxWidth >= 760.dp
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .touchDragScroll(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isWideLayout) {
                    // Two-column balanced layout for standard POS landscape screens (1024x768, 1280x800, etc.)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 980.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Column: Order Summary, Payment Method, Remaining/Change, Validation Button
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryCard(
                                order = order,
                                orderTotalCentimes = orderTotalCentimes,
                                strings = strings
                            )

                            PaymentMethodSelector(
                                selectedMethod = selectedMethod,
                                onSelectMethod = { selectedMethod = it },
                                strings = strings
                            )

                            PaymentStatusCard(
                                selectedMethod = selectedMethod,
                                parsedCash = parsedCash,
                                orderTotalCentimes = orderTotalCentimes,
                                remainingCentimes = remainingCentimes,
                                changeCentimes = changeCentimes,
                                strings = strings
                            )

                            val effectiveMessage = uiMessage ?: message.takeIf { it.isNotBlank() }?.let { UiMessage.error(it) }
                            val cashError = if (selectedMethod == PaymentMethod.CASH && effectiveMessage?.isError == true) effectiveMessage.text else null
                            if (effectiveMessage != null && effectiveMessage.text != cashError) {
                                PosInlineAlert(
                                    message = effectiveMessage,
                                    onDismiss = onClearMessage
                                )
                            }

                            ValidationButton(
                                enabled = isPaymentValid,
                                onClick = { onPaymentSubmitted(selectedMethod, parsedCash) },
                                strings = strings
                            )
                        }

                        // Right Column: Cash Amount Input or Manual TPE Guide
                        if (selectedMethod == PaymentMethod.CASH) {
                            val effectiveMessage = uiMessage ?: message.takeIf { it.isNotBlank() }?.let { UiMessage.error(it) }
                            val cashError = if (effectiveMessage?.isError == true) effectiveMessage.text else null
                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CashInputCard(
                                    receivedCashInput = receivedCashInput,
                                    onClear = {
                                        receivedCashInput = ""
                                        onClearMessage()
                                    },
                                    orderTotalCentimes = orderTotalCentimes,
                                    onQuickAmount = {
                                        receivedCashInput = it
                                        onClearMessage()
                                    },
                                    onKeyPress = { onKeypadPress(it) },
                                    errorMessage = cashError,
                                    strings = strings
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ManualTpeGuideCard(
                                    orderTotalCentimes = orderTotalCentimes,
                                    strings = strings
                                )
                            }
                        }
                    }
                } else {
                    // Single Column stacked layout for narrow / mobile screens
                    val effectiveMessage = uiMessage ?: message.takeIf { it.isNotBlank() }?.let { UiMessage.error(it) }
                    val cashError = if (selectedMethod == PaymentMethod.CASH && effectiveMessage?.isError == true) effectiveMessage.text else null
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            order = order,
                            orderTotalCentimes = orderTotalCentimes,
                            strings = strings
                        )

                        PaymentMethodSelector(
                            selectedMethod = selectedMethod,
                            onSelectMethod = { selectedMethod = it },
                            strings = strings
                        )

                        if (selectedMethod == PaymentMethod.CASH) {
                            CashInputCard(
                                receivedCashInput = receivedCashInput,
                                onClear = {
                                    receivedCashInput = ""
                                    onClearMessage()
                                },
                                orderTotalCentimes = orderTotalCentimes,
                                onQuickAmount = {
                                    receivedCashInput = it
                                    onClearMessage()
                                },
                                onKeyPress = { onKeypadPress(it) },
                                errorMessage = cashError,
                                strings = strings
                            )
                        } else {
                            ManualTpeGuideCard(
                                orderTotalCentimes = orderTotalCentimes,
                                strings = strings
                            )
                        }

                        PaymentStatusCard(
                            selectedMethod = selectedMethod,
                            parsedCash = parsedCash,
                            orderTotalCentimes = orderTotalCentimes,
                            remainingCentimes = remainingCentimes,
                            changeCentimes = changeCentimes,
                            strings = strings
                        )

                        if (effectiveMessage != null && effectiveMessage.text != cashError) {
                            PosInlineAlert(
                                message = effectiveMessage,
                                onDismiss = onClearMessage
                            )
                        }

                        ValidationButton(
                            enabled = isPaymentValid,
                            onClick = { onPaymentSubmitted(selectedMethod, parsedCash) },
                            strings = strings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    order: Order,
    orderTotalCentimes: Long,
    strings: DesktopStrings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.text("Total à payer", "Total Due", "المجموع الواجب دفعه"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosColors.TextMedium
                )
                Text(
                    text = "${order.lines.sumOf { it.quantity }} ${strings.text("article(s)", "item(s)", "عنصر")}",
                    fontSize = 12.sp,
                    color = PosColors.TextMuted
                )
            }
            Text(
                text = "${MoneyRules.formatFixed(orderTotalCentimes)} ${strings.currency}",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PosColors.Primary
            )
        }
    }
}

@Composable
private fun PaymentMethodSelector(
    selectedMethod: PaymentMethod,
    onSelectMethod: (PaymentMethod) -> Unit,
    strings: DesktopStrings
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = strings.paymentMethod,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = PosColors.TextHigh
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaymentMethodTabButton(
                title = strings.cash,
                icon = "💵",
                isSelected = selectedMethod == PaymentMethod.CASH,
                onClick = { onSelectMethod(PaymentMethod.CASH) },
                modifier = Modifier.weight(1f)
            )
            PaymentMethodTabButton(
                title = strings.card,
                icon = "💳",
                isSelected = selectedMethod == PaymentMethod.CARD,
                onClick = { onSelectMethod(PaymentMethod.CARD) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PaymentMethodTabButton(
    title: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(PosDimens.TouchStandard)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PosColors.Primary else Color.White,
        border = BorderStroke(
            1.5.dp,
            if (isSelected) PosColors.Primary else PosColors.Border
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else PosColors.TextHigh
            )
        }
    }
}

@Composable
private fun CashInputCard(
    receivedCashInput: String,
    onClear: () -> Unit,
    orderTotalCentimes: Long,
    onQuickAmount: (String) -> Unit,
    onKeyPress: (String) -> Unit,
    errorMessage: String? = null,
    strings: DesktopStrings
) {
    val isError = !errorMessage.isNullOrBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Amount Received Display Box
            Text(
                text = "${strings.amountReceived} (${strings.currency})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.TextHigh
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = PosColors.Workspace,
                border = BorderStroke(1.5.dp, if (isError) PosColors.Danger else PosColors.Primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (receivedCashInput.isBlank()) "0.00" else receivedCashInput,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (receivedCashInput.isBlank()) PosColors.TextMuted else PosColors.TextHigh
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.currency,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextMedium
                        )
                        if (receivedCashInput.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = onClear,
                                shape = RoundedCornerShape(6.dp),
                                color = PosColors.Border,
                                modifier = Modifier
                                    .size(28.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                                }
                            }
                        }
                    }
                }
            }

            FieldErrorMessage(errorMessage = errorMessage)

            // Quick Banknote Touch Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickAmountButton(
                    label = strings.exactAmount,
                    onClick = { onQuickAmount(MoneyRules.formatFixed(orderTotalCentimes)) },
                    modifier = Modifier.weight(1.3f)
                )
                listOf(50L, 100L, 200L).forEach { dh ->
                    QuickAmountButton(
                        label = "$dh ${strings.currency}",
                        onClick = { onQuickAmount(dh.toString()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Keypad strictly: 1 2 3 / 4 5 6 / 7 8 9 / . 0 ⌫
            KeypadGrid(
                onKeyPress = onKeyPress
            )
        }
    }
}

@Composable
private fun QuickAmountButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(10.dp),
        color = PosColors.Workspace,
        border = BorderStroke(1.dp, PosColors.Border)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.TextHigh,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KeypadGrid(
    onKeyPress: (String) -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isActionKey = key == "⌫"
                    Surface(
                        onClick = { onKeyPress(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(PosDimens.KeypadKeyHeight)
                            .pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActionKey) PosColors.Workspace else Color.White,
                        border = BorderStroke(1.dp, PosColors.Border),
                        shadowElevation = 1.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = if (isActionKey) 20.sp else 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentStatusCard(
    selectedMethod: PaymentMethod,
    parsedCash: Long?,
    orderTotalCentimes: Long,
    remainingCentimes: Long,
    changeCentimes: Long,
    strings: DesktopStrings
) {
    if (selectedMethod == PaymentMethod.CARD) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PosColors.Workspace,
            border = BorderStroke(1.dp, PosColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💳", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.text("Paiement par carte", "Card Payment", "الدفع بالبطاقة"),
                        fontWeight = FontWeight.Bold,
                        color = PosColors.BakeryBrown,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "${MoneyRules.formatFixed(orderTotalCentimes)} ${strings.currency}",
                    fontWeight = FontWeight.Bold,
                    color = PosColors.Primary,
                    fontSize = 17.sp
                )
            }
        }
    } else {
        val isComplete = parsedCash != null && parsedCash >= orderTotalCentimes
        val containerColor = if (isComplete) PosColors.SuccessLight else PosColors.AlertLight
        val borderColor = if (isComplete) PosColors.Success else PosColors.Alert
        val textColor = if (isComplete) PosColors.Success else PosColors.Alert

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isComplete) "${strings.changeDue} :" else strings.text("Montant restant :", "Remaining:", "المتبقي:"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor
                )
                Text(
                    text = "${MoneyRules.formatFixed(if (isComplete) changeCentimes else remainingCentimes)} ${strings.currency}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ValidationButton(
    enabled: Boolean,
    onClick: () -> Unit,
    strings: DesktopStrings
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(PosDimens.CashOutButtonHeight)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        colors = ButtonDefaults.buttonColors(
            containerColor = PosColors.Primary,
            disabledContainerColor = PosColors.Primary.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = strings.text("Valider le paiement", "Validate Payment", "تأكيد الدفع"),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ManualTpeGuideCard(
    orderTotalCentimes: Long,
    strings: DesktopStrings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💳", fontSize = 24.sp)
                Column {
                    Text(
                        text = strings.text("Guide d'encaissement TPE", "Manual POS Terminal Guide", "دليل الدفع عبر جهاز TPE"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PosColors.BakeryBrown
                    )
                    Text(
                        text = strings.text("Terminal autonome (non relié au POS)", "Standalone terminal (not connected to POS)", "جهاز مستقل (غير متصل بالصندوق)"),
                        fontSize = 12.sp,
                        color = PosColors.TextMuted
                    )
                }
            }

            HorizontalDivider(color = PosColors.Border.copy(alpha = 0.6f))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TpeStepRow(
                    stepNumber = "1",
                    text = strings.text(
                        "Saisir le montant ${MoneyRules.formatFixed(orderTotalCentimes)} ${strings.currency} sur le TPE",
                        "Enter amount ${MoneyRules.formatFixed(orderTotalCentimes)} ${strings.currency} on the terminal",
                        "أدخل المبلغ ${MoneyRules.formatFixed(orderTotalCentimes)} ${strings.currency} على جهاز TPE"
                    )
                )
                TpeStepRow(
                    stepNumber = "2",
                    text = strings.text(
                        "Faire payer le client (carte bancaire ou sans contact)",
                        "Customer presents card or contactless payment",
                        "تقديم الزبون للبطاقة أو الدفع بدون تلامس"
                    )
                )
                TpeStepRow(
                    stepNumber = "3",
                    text = strings.text(
                        "Attendre la validation et le ticket d'acceptation du TPE",
                        "Wait for approval and printed receipt from terminal",
                        "انتظار الموافقة وطباعة وصل المعاملة من الجهاز"
                    )
                )
                TpeStepRow(
                    stepNumber = "4",
                    text = strings.text(
                        "Cliquer sur « Valider le paiement » ci-dessous",
                        "Click 'Validate payment' below",
                        "اضغط على «تأكيد الدفع» أدناه"
                    )
                )
            }
        }
    }
}

@Composable
private fun TpeStepRow(stepNumber: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PosColors.PrimaryLight,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PosColors.PrimaryDark
                )
            }
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = PosColors.TextHigh
        )
    }
}

