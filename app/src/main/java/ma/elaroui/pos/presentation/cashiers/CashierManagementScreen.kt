package ma.elaroui.pos.presentation.cashiers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierManagementScreen(
    viewModel: CashierViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestion des Caissiers", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.openCreateDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        Text("+ Nouveau Caissier", color = Color.White)
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
                .padding(adaptive.screenContentPadding)
        ) {
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(16.dp))
                }
            }
            uiState.successMessage?.let { success ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4EA)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(success, color = Color(0xFF1E8E3E), modifier = Modifier.padding(16.dp))
                }
            }

            if (uiState.cashiers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun caissier enregistré.", fontSize = 18.sp, color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.cashiers) { cashier ->
                        CashierRow(
                            cashier = cashier,
                            onResetPin = { viewModel.openResetPinDialog(cashier) },
                            onToggleActive = { viewModel.toggleCashierActive(cashier) }
                        )
                    }
                }
            }

            if (uiState.isCreateDialogOpen) {
                CreateCashierDialog(
                    name = uiState.newCashierName,
                    pin = uiState.newCashierPin,
                    isLoading = uiState.isLoading,
                    onFormChange = { n, p -> viewModel.updateCreateForm(n, p) },
                    onDismiss = { viewModel.closeCreateDialog() },
                    onConfirm = { viewModel.createCashier() }
                )
            }

            if (uiState.isResetPinDialogOpen) {
                ResetPinDialog(
                    cashierName = uiState.selectedCashier?.name ?: "",
                    newPin = uiState.resetNewPin,
                    ownerPin = uiState.ownerPinConfirm,
                    isLoading = uiState.isLoading,
                    onFormChange = { np, op -> viewModel.updateResetPinForm(np, op) },
                    onDismiss = { viewModel.closeResetPinDialog() },
                    onConfirm = { viewModel.submitResetPin() }
                )
            }
        }
    }
}

@Composable
private fun CashierRow(
    cashier: User,
    onResetPin: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(cashier.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(
                    if (cashier.active) "Statut: Actif" else "Statut: Inactif",
                    fontSize = 14.sp,
                    color = if (cashier.active) Color(0xFF1E8E3E) else Color(0xFFE63946)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResetPin) {
                    Text("Réinitialiser PIN")
                }
                Button(
                    onClick = onToggleActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (cashier.active) Color(0xFF64748B) else Color(0xFF1E8E3E)
                    )
                ) {
                    Text(if (cashier.active) "Désactiver" else "Activer", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CreateCashierDialog(
    name: String,
    pin: String,
    isLoading: Boolean,
    onFormChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Caissier") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onFormChange(it, pin) },
                    label = { Text("Nom du caissier *") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onFormChange(name, it) },
                    label = { Text("Code PIN (4-6 chiffres) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && pin.length >= 4 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
            ) {
                Text("Créer", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ResetPinDialog(
    cashierName: String,
    newPin: String,
    ownerPin: String,
    isLoading: Boolean,
    onFormChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réinitialiser le PIN de $cashierName") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onFormChange(it, ownerPin) },
                    label = { Text("Nouveau Code PIN Caissier *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ownerPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onFormChange(newPin, it) },
                    label = { Text("Confirmation PIN Propriétaire *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = newPin.length >= 4 && ownerPin.length >= 4 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
            ) {
                Text("Valider la réinitialisation", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
