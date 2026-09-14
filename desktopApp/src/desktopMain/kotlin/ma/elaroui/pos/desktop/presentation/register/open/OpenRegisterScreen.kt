@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.register.open

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import ma.elaroui.pos.desktop.presentation.components.NumericKeypad
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.TouchNumericField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun OpenRegisterScreen(
    strings: DesktopStrings,
    isOwner: Boolean = false,
    suggestedOpeningCashCentimes: Long? = null,
    onOpenRegisterSubmitted: (openingCashCentimes: Long) -> Unit,
    onBackToDashboard: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    errorMessage: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var openingCashInput by remember {
        mutableStateOf(suggestedOpeningCashCentimes?.let { MoneyRules.formatFixed(it) } ?: "")
    }
    var actionInProgress by remember { mutableStateOf(false) }

    val parsedCentimes = (MoneyRules.parseToCentimes(openingCashInput) as? MoneyParseResult.Success)?.centimes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.openRegisterTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBackToDashboard != null) {
                        TextButton(onClick = onBackToDashboard) {
                            Text("← ${strings.back}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        if (isOwner && onBackToDashboard != null) {
                            OutlinedButton(
                                onClick = onBackToDashboard,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    strings.text("Dashboard", "Dashboard", "لوحة التحكم"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (onLock != null) {
                            OutlinedButton(
                                onClick = onLock,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    strings.text("Verrouiller", "Lock", "قفل"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PosColors.BakeryBrown)
            )
        }
    ) { padding ->
        val openScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PosColors.Canvas)
                .touchDragScroll(openScrollState)
                .verticalScroll(openScrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val hasInputError = errorMessage.isNotBlank() || (uiMessage?.isError == true)
                val inputErrorText = errorMessage.takeIf { it.isNotBlank() } ?: uiMessage?.takeIf { it.isError }?.text

                if (uiMessage != null && !uiMessage.isError) {
                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = onClearMessage
                    )
                }

                if (suggestedOpeningCashCentimes != null && suggestedOpeningCashCentimes > 0) {
                    val formattedSuggested = MoneyRules.formatFixed(suggestedOpeningCashCentimes)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PosColors.PrimaryLight),
                        border = BorderStroke(1.dp, PosColors.Primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("💡", fontSize = 20.sp)
                                Column {
                                    Text(
                                        strings.text("Fond de caisse suggéré", "Suggested Opening Float", "المبلغ الافتتاحي المقترح"),
                                        fontSize = 13.sp,
                                        color = PosColors.PrimaryDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        strings.text(
                                            "Montant laissé dans le tiroir lors de la dernière clôture : $formattedSuggested ${strings.currency}",
                                            "Amount left in drawer at last register close: $formattedSuggested ${strings.currency}",
                                            "المبلغ المتروك في الصندوق عند آخر إغلاق: $formattedSuggested ${strings.currency}"
                                        ),
                                        fontSize = 12.sp,
                                        color = PosColors.TextMedium
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    openingCashInput = formattedSuggested
                                    if (hasInputError) onClearMessage()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(strings.text("Appliquer", "Apply", "تطبيق"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                TouchNumericField(
                    value = openingCashInput,
                    onValueChange = {
                        openingCashInput = it
                        if (hasInputError) onClearMessage()
                    },
                    label = "${strings.openingCash} (${strings.currency}) *",
                    supportingText = { Text(strings.text("Confirmez le montant d'espèces initial dans le tiroir", "Confirm initial physical cash in drawer", "أكّد مبلغ النقد الأولي في الصندوق")) },
                    currencySymbol = strings.currency,
                    isDecimalAllowed = true,
                    isError = hasInputError,
                    errorMessage = inputErrorText,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.text("Montants rapides", "Quick amounts", "مبالغ سريعة"), fontSize = 13.sp, color = PosColors.TextMedium, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("0", "200", "500", "1000", "2000")) { amount ->
                            OutlinedButton(
                                onClick = {
                                    openingCashInput = amount
                                    if (hasInputError) onClearMessage()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("$amount ${strings.currency}")
                            }
                        }
                    }
                }

                // Embedded Touch Numeric Keypad for fast single-touch operation
                NumericKeypad(
                    value = openingCashInput,
                    onValueChange = {
                        openingCashInput = it
                        if (hasInputError) onClearMessage()
                    },
                    isDecimalAllowed = true,
                    clearLabel = "C",
                    keyHeight = 48.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (!actionInProgress && parsedCentimes != null && parsedCentimes >= 0) {
                            actionInProgress = true
                            onOpenRegisterSubmitted(parsedCentimes)
                        }
                    },
                    enabled = !actionInProgress && parsedCentimes != null && parsedCentimes >= 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary)
                ) {
                    Text(strings.openRegisterAction, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
