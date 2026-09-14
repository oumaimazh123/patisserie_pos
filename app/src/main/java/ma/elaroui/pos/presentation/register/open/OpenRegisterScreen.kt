package ma.elaroui.pos.presentation.register.open

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRegisterScreen(
    viewModel: OpenRegisterViewModel,
    isOwner: Boolean = false,
    onBackToDashboard: () -> Unit = {},
    onLock: () -> Unit = {},
    onSessionOpened: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var actionInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            actionInProgress = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ouverture de caisse", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (isOwner) {
                        OutlinedButton(
                            onClick = {
                                if (!actionInProgress) {
                                    actionInProgress = true
                                    onBackToDashboard()
                                }
                            },
                            enabled = !actionInProgress,
                            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .padding(start = 12.dp)
                                .testTag("open_register_back_to_dashboard")
                        ) {
                            Text("← Retour", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (!actionInProgress) {
                                    actionInProgress = true
                                    onLock()
                                }
                            },
                            enabled = !actionInProgress,
                            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .padding(start = 12.dp)
                                .testTag("open_register_lock")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🔒", fontSize = 15.sp)
                                Text("Verrouiller", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                },
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
            if (uiState.isLoading && uiState.activeRegisters.isEmpty()) {
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
                text = "Montant de fond de caisse initial",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D3557)
            )
            Text(
                text = "Une seule session est utilisée par tous les utilisateurs connectés. Chaque opération garde le nom de son auteur.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.activeRegisters.isNotEmpty()) {
                Text("Sélectionner la caisse", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.activeRegisters, key = { it.id }) { register ->
                        FilterChip(
                            selected = (uiState.selectedRegister?.id == register.id),
                            onClick = { viewModel.selectRegister(register) },
                            label = { Text(register.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = uiState.openingCashInput,
                onValueChange = { viewModel.updateOpeningCashInput(it) },
                label = { Text("Fond de caisse (DH) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Maximum 2 décimales") },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .testTag("opening_cash_input")
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Montants rapides", fontSize = 14.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("200", "500", "1000", "2000")) { amount ->
                    OutlinedButton(onClick = { viewModel.updateOpeningCashInput(amount) }) {
                        Text("$amount DH")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (!actionInProgress) {
                        actionInProgress = true
                        viewModel.submitOpenRegister(onSessionOpened)
                    }
                },
                enabled = uiState.selectedRegister != null &&
                    uiState.openingCashInput.isNotBlank() &&
                    !uiState.isLoading &&
                    !actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .height(52.dp)
                    .testTag("open_register_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Ouvrir la caisse", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
