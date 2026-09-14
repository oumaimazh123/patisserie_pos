package ma.elaroui.pos.presentation.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onPaymentCompletedSuccess: (Long) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current

    val order = uiState.order
    val orderTotalCentimes = order?.totalCentimes ?: 0L
    val receivedCentimes = parseMoneyToCentimes(uiState.receivedCashInput)
    val changeCentimes = if ((receivedCentimes ?: 0L) > orderTotalCentimes) {
        receivedCentimes!! - orderTotalCentimes
    } else 0L
    val remainingCentimes = if (receivedCentimes == null || receivedCentimes < orderTotalCentimes) {
        orderTotalCentimes - (receivedCentimes ?: 0L)
    } else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Règlement - Commande ${order?.orderNumber ?: ""}") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoadingOrder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (order == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.errorMessage ?: "Commande introuvable", color = Color(0xFFE63946))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(adaptive.screenContentPadding)
                    .imePadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Order Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D3557))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total À Payer", color = Color(0xFFA8DADC), fontSize = 14.sp)
                                Text(
                                    MonetaryUtils.formatDh(orderTotalCentimes),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Type: ${order.type.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${order.items.sumOf { it.quantity }} article(s)", color = Color(0xFFA8DADC), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment Method Selector
                    Text("Mode de Règlement", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PaymentMethodButton(
                            title = "Espèces (Cash)",
                            subtitle = "Paiement cash & rendu",
                            isSelected = uiState.selectedMethod == PaymentMethod.CASH,
                            onClick = { viewModel.selectPaymentMethod(PaymentMethod.CASH) },
                            modifier = Modifier.weight(1f)
                        )
                        PaymentMethodButton(
                            title = "Carte / TPE",
                            subtitle = "Terminal bancaire",
                            isSelected = uiState.selectedMethod == PaymentMethod.CARD,
                            onClick = { viewModel.selectPaymentMethod(PaymentMethod.CARD) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cash Panel
                    if (uiState.selectedMethod == PaymentMethod.CASH) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Montant Encaissé (DH)", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = uiState.receivedCashInput,
                                    onValueChange = { viewModel.updateReceivedCashInput(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF1D3557),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Quick Cash Shortcuts
                                Text("Raccourcis Billets", fontSize = 12.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.setExactCashAmount() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Exact", fontSize = 12.sp)
                                    }
                                    listOf(50L, 100L, 200L).forEach { dh ->
                                        OutlinedButton(
                                            onClick = { viewModel.setQuickCashAmount(dh) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("$dh DH", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (remainingCentimes > 0) "Montant restant:" else "Monnaie à Rendre:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1D3557)
                                    )
                                    Text(
                                        MonetaryUtils.formatDh(if (remainingCentimes > 0) remainingCentimes else changeCentimes),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = if (changeCentimes > 0) Color(0xFF2A9D8F) else Color(0xFF1D3557)
                                    )
                                }
                            }
                        }
                    }

                    // Card Panel
                    if (uiState.selectedMethod == PaymentMethod.CARD) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Règlement par Carte Bancaire", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = uiState.isCardTerminalApproved,
                                        onCheckedChange = { viewModel.toggleCardTerminalApproval(it) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Confirmer que le paiement a été validé sur le TPE",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1D3557)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = uiState.cardReference,
                                    onValueChange = { viewModel.updateCardReference(it) },
                                    label = { Text("Référence TPE / Autorisation (Optionnel)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                }

                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFE63946),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Sticky Confirm Payment Button
                Button(
                    onClick = { viewModel.processPayment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !uiState.isProcessing && (
                        uiState.selectedMethod != PaymentMethod.CASH ||
                            (receivedCentimes != null && receivedCentimes >= orderTotalCentimes)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Valider le Règlement", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Payment Success Event Dialog
        uiState.paymentResult?.let { result ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.clearPaymentResultEvent()
                    onPaymentCompletedSuccess(result.completedOrder.id)
                },
                title = { Text("Règlement Effectué ✓", color = Color(0xFF2A9D8F), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Paiement de la commande ${result.completedOrder.orderNumber} enregistré avec succès.")
                        if (result.payment.method == PaymentMethod.CASH && result.changeAmountCentimes > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Rendu monnaie: ${MonetaryUtils.formatDh(result.changeAmountCentimes)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF2A9D8F)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearPaymentResultEvent()
                            onPaymentCompletedSuccess(result.completedOrder.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3557))
                    ) {
                        Text("Terminer & Reçu")
                    }
                }
            )
        }
    }
}

@Composable
private fun PaymentMethodButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1D3557) else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF1D3557),
                fontSize = 15.sp
            )
            Text(
                subtitle,
                color = if (isSelected) Color(0xFFA8DADC) else Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}
