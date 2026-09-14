package ma.elaroui.pos.presentation.register.close

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseRegisterScreen(
    viewModel: CloseRegisterViewModel,
    onRegisterClosed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clôture de la caisse partagée", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading && uiState.session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(16.dp))
                }
            }

            Text(
                text = "La clôture termine la session pour tous les utilisateurs. Les ventes et opérations restent enregistrées avec leur auteur.",
                color = Color(0xFF64748B),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Récapitulatif de Fin de Session", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fond de caisse initial:")
                        Text(MonetaryUtils.formatDh(uiState.openingCashCentimes), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Entrées (Cash In):", color = Color(0xFF1E8E3E))
                        Text("+ ${MonetaryUtils.formatDh(uiState.totalCashInCentimes)}", fontWeight = FontWeight.Bold, color = Color(0xFF1E8E3E))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Sorties (Cash Out):", color = Color(0xFFE63946))
                        Text("- ${MonetaryUtils.formatDh(uiState.totalCashOutCentimes)}", fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Ventes espèces:", color = Color(0xFF457B9D))
                        Text(
                            "+ ${MonetaryUtils.formatDh(uiState.cashSalesCentimes)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF457B9D)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Montant Attendu:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                        Text(MonetaryUtils.formatDh(uiState.expectedCashCentimes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.openOrderCount > 0) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 800.dp)
                        .testTag("close_register_open_orders_blocker")
                ) {
                    Text(
                        "Clôture bloquée : ${uiState.openOrderCount} commande(s) encore ouverte(s). Terminez ou annulez-les avant de clôturer.",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = uiState.countedCashInput,
                onValueChange = { viewModel.updateCountedCashInput(it) },
                label = { Text("Montant Compté dans le tiroir (DH) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Maximum 2 décimales") },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .testTag("counted_cash_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Difference Banner
            val diff = uiState.differenceCentimes
            val diffColor = when {
                diff == 0L -> Color(0xFF1E8E3E) // Green (Balanced)
                diff > 0 -> Color(0xFF1D3557) // Blue (Surplus)
                else -> Color(0xFFE63946) // Red (Shortage)
            }
            val diffText = when {
                uiState.countedCashInput.isBlank() -> "Saisissez le montant réellement compté"
                diff == 0L -> "Caisse Équilibrée (Écart: 0.00 DH)"
                diff > 0 -> "Surplus de Caisse: + ${MonetaryUtils.formatDh(diff)}"
                else -> "Manquant de Caisse: ${MonetaryUtils.formatDh(diff)}"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = diffColor.copy(alpha = 0.1f))
            ) {
                Text(
                    text = diffText,
                    color = diffColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (uiState.requiresOwnerApproval) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F1))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Attention: L'écart dépasse le seuil de 50.00 DH. Une note de clôture et la confirmation par PIN du Propriétaire sont obligatoires.",
                            color = Color(0xFFE63946),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.closingNoteInput,
                            onValueChange = { viewModel.updateClosingNoteInput(it) },
                            label = { Text("Note de clôture / Justification *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.ownerPinInput,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) viewModel.updateOwnerPinInput(it) },
                            label = { Text("Confirmation PIN Propriétaire *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.closingNoteInput,
                    onValueChange = { viewModel.updateClosingNoteInput(it) },
                    label = { Text("Note de clôture (Optionnelle)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.submitCloseRegister(onRegisterClosed) },
                enabled = uiState.countedCashInput.isNotBlank() &&
                    uiState.openOrderCount == 0 &&
                    (!uiState.requiresOwnerApproval ||
                        (uiState.closingNoteInput.isNotBlank() &&
                            uiState.ownerPinInput.isNotBlank())) &&
                    !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .height(52.dp)
                    .testTag("close_register_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Clôturer la caisse partagée", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
