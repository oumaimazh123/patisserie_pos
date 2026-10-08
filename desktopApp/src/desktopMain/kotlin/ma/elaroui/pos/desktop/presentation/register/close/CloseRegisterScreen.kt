@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.register.close

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.ResponsiveFlowGrid
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.rules.MoneyRules
import ma.elaroui.pos.shared.rules.RegisterClosingInput

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
    var closingNoteInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val totalSalesCentimes = cashSalesCentimes + cardSalesCentimes
    val hasActiveOrders = activeOrdersCount > 0
    val canSubmit = !hasActiveOrders && !isSubmitting

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
                    .widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiMessage != null && !uiMessage.isError) {
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = onClearMessage
                    )
                }

                val currentError = uiMessage?.takeIf { it.isError }?.text ?: errorMessage.takeIf { it.isNotBlank() }
                if (!currentError.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = PosColors.DangerLight),
                        border = BorderStroke(1.dp, PosColors.Danger)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("⚠️", fontSize = 20.sp)
                            Text(currentError, color = PosColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
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

                // Session Financial Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PosColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            strings.text("Récapitulatif financier de la session", "Session Financial Summary", "ملخص الحساب المالي للجلسة"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PosColors.BakeryBrown
                        )

                        // Hero Banner: Large Expected Cash Card
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
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = PosColors.Honey,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        Text(
                                            strings.text(
                                                "Ventes espèces + Entrées d'espèces - Sorties d'espèces",
                                                "Cash sales + Cash In - Cash Out",
                                                "مبيعات نقداً + إيداعات نقداً - سحوبات نقداً"
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
                                            .padding(horizontal = 22.dp, vertical = 18.dp),
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
                                                fontSize = 17.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                strings.text(
                                                    "Ventes espèces + Entrées d'espèces - Sorties d'espèces",
                                                    "Cash sales + Cash In - Cash Out",
                                                    "مبيعات نقداً + إيداعات نقداً - سحوبات نقداً"
                                                ),
                                                fontSize = 11.sp,
                                                color = PosColors.SecondaryLight,
                                                lineHeight = 15.sp
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Black.copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                "${MoneyRules.formatFixed(expectedCashCentimes)} ${strings.currency}",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PosColors.Honey,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Financial Cards Grid
                        ResponsiveFlowGrid(
                            modifier = Modifier.fillMaxWidth(),
                            minItemWidth = 140.dp,
                            horizontalSpacing = 12.dp,
                            verticalSpacing = 12.dp
                        ) {
                            SummaryItemBox(
                                label = strings.cashSales,
                                value = "${MoneyRules.formatFixed(cashSalesCentimes)} ${strings.currency}",
                                color = PosColors.Success,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SummaryItemBox(
                                label = strings.cardSales,
                                value = "${MoneyRules.formatFixed(cardSalesCentimes)} ${strings.currency}",
                                color = PosColors.SecondaryDark,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SummaryItemBox(
                                label = strings.totalSales,
                                value = "${MoneyRules.formatFixed(totalSalesCentimes)} ${strings.currency}",
                                color = PosColors.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SummaryItemBox(
                                label = strings.cashIn,
                                value = "+${MoneyRules.formatFixed(cashInCentimes)} ${strings.currency}",
                                color = PosColors.Success,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SummaryItemBox(
                                label = strings.cashOut,
                                value = "-${MoneyRules.formatFixed(cashOutCentimes)} ${strings.currency}",
                                color = PosColors.Alert,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Optional compact closing note field
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

                // Primary Action Button: Fermer la caisse
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
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

    // Confirmation Modal
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) {
                    showConfirmDialog = false
                }
            },
            title = {
                Text(
                    strings.closeRegisterTitle,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.expectedCash, color = PosColors.TextMedium, fontSize = 13.sp)
                        Text(
                            "${MoneyRules.formatFixed(expectedCashCentimes)} ${strings.currency}",
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown,
                            fontSize = 15.sp
                        )
                    }

                    if (closingNoteInput.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.text("Note", "Note", "ملاحظة"), color = PosColors.TextMedium, fontSize = 13.sp)
                            Text(
                                closingNoteInput.trim(),
                                fontWeight = FontWeight.Normal,
                                color = PosColors.TextHigh,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            showConfirmDialog = false
                            val input = RegisterClosingInput(
                                closingNote = closingNoteInput.trim().takeIf { it.isNotBlank() }
                            )
                            onCloseRegisterSubmitted(input)
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontSize = 11.sp, color = PosColors.TextMedium, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
