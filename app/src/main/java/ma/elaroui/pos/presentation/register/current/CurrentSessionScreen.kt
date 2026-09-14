package ma.elaroui.pos.presentation.register.current

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.CashMovement
import ma.elaroui.pos.domain.model.CashMovementType
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentSessionScreen(
    viewModel: CurrentSessionViewModel,
    onNavigateToCloseRegister: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session
    val locale = java.util.Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caisse partagée actuelle", color = Color.White) },
                actions = {
                    if (uiState.canCloseRegister) {
                        Button(
                            onClick = onNavigateToCloseRegister,
                            enabled = session != null && !uiState.isLoading,
                            modifier = Modifier.testTag("current_session_close"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                        ) {
                            Text("Fermer la caisse", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "Caisse partagée",
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = 12.dp)
                        )
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
                .padding(24.dp)
        ) {
            uiState.errorMessage?.takeIf { !uiState.isMovementDialogOpen }?.let { error ->
                Surface(
                    color = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(error, modifier = Modifier.padding(12.dp))
                }
            }
            uiState.successMessage?.let { message ->
                Surface(
                    color = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF1E8E3E),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        message,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (uiState.isLoading && session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune session active.", fontSize = 18.sp, color = Color(0xFF64748B))
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Caisse: ${uiState.register?.name ?: "Principale"}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                        Text("Ouverte par: ${uiState.cashier?.name ?: "Utilisateur"}", fontSize = 16.sp, color = Color(0xFF64748B))
                        Text(
                            "Ouverte le: ${SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(session.openedAt))}",
                            fontSize = 14.sp, color = Color(0xFF64748B)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fond de caisse initial:")
                            Text(MonetaryUtils.formatDh(session.openingCashCentimes), fontWeight = FontWeight.Bold)
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
                            Text("Ventes encaissées en espèces:", color = Color(0xFF457B9D))
                            Text(
                                "+ ${MonetaryUtils.formatDh(uiState.cashSalesCentimes)}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF457B9D)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Attendu Actuel:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                            Text(MonetaryUtils.formatDh(uiState.currentExpectedCashCentimes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 480.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MovementButton(
                                "+ Entrée espèces",
                                Color(0xFF1E8E3E),
                                Modifier.weight(1f)
                            ) { viewModel.openMovementDialog(CashMovementType.CASH_IN) }
                            MovementButton(
                                "− Sortie espèces",
                                Color(0xFFE63946),
                                Modifier.weight(1f)
                            ) { viewModel.openMovementDialog(CashMovementType.CASH_OUT) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MovementButton(
                                "+ Entrée espèces",
                                Color(0xFF1E8E3E),
                                Modifier.fillMaxWidth()
                            ) { viewModel.openMovementDialog(CashMovementType.CASH_IN) }
                            MovementButton(
                                "− Sortie espèces",
                                Color(0xFFE63946),
                                Modifier.fillMaxWidth()
                            ) { viewModel.openMovementDialog(CashMovementType.CASH_OUT) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Historique des mouvements de caisse", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.cashMovements.isEmpty()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Aucun mouvement enregistré pour cette session.",
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.cashMovements, key = { it.id }) { movement ->
                            CashMovementRow(
                                movement = movement,
                                userName = uiState.movementUserNames[movement.createdByUserId]
                                    ?: "Utilisateur #${movement.createdByUserId}"
                            )
                        }
                    }
                }
            }

            if (uiState.isMovementDialogOpen) {
                CashMovementDialog(
                    type = uiState.movementType,
                    amount = uiState.movementAmountInput,
                    reason = uiState.movementReasonInput,
                    description = uiState.movementDescriptionInput,
                    ownerPin = uiState.ownerPinInput,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onFormChange = { a, r, d, op -> viewModel.updateMovementForm(a, r, d, op) },
                    onDismiss = { viewModel.closeMovementDialog() },
                    onConfirm = { viewModel.submitCashMovement() }
                )
            }
        }
    }
}

@Composable
private fun MovementButton(
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun CashMovementRow(movement: CashMovement, userName: String) {
    val locale = java.util.Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(movement.reason, fontWeight = FontWeight.SemiBold)
                Text(
                    "$userName • ${SimpleDateFormat("HH:mm", locale).format(Date(movement.createdAt))}",
                    fontSize = 12.sp, color = Color(0xFF64748B)
                )
            }
            Text(
                text = "${if (movement.type == CashMovementType.CASH_IN) "+" else "-"} ${MonetaryUtils.formatDh(movement.amountCentimes)}",
                fontWeight = FontWeight.Bold,
                color = if (movement.type == CashMovementType.CASH_IN) Color(0xFF1E8E3E) else Color(0xFFE63946)
            )
        }
    }
}

@Composable
private fun CashMovementDialog(
    type: CashMovementType,
    amount: String,
    reason: String,
    description: String,
    ownerPin: String,
    isLoading: Boolean,
    errorMessage: String?,
    onFormChange: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var reasonMenuExpanded by remember(type) { mutableStateOf(false) }
    val reasons = remember(type) {
        if (type == CashMovementType.CASH_IN) {
            listOf(
                "Ajout de monnaie",
                "Apport du propriétaire",
                "Retour d’une sortie précédente",
                "Remboursement reçu",
                "Correction de caisse",
                "Dépôt exceptionnel",
                "Autre"
            )
        } else {
            listOf(
                "Achat urgent de stock",
                "Paiement fournisseur",
                "Dépense du café",
                "Retrait de sécurité",
                "Retrait du propriétaire",
                "Remboursement client",
                "Correction de caisse",
                "Transfert vers coffre",
                "Autre"
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == CashMovementType.CASH_IN) "Entrée d'espèces" else "Sortie d'espèces") },
        text = {
            Column {
                errorMessage?.let { error ->
                    Text(error, color = Color(0xFFE63946), modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { onFormChange(it, reason, description, ownerPin) },
                    label = { Text("Montant (DH) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { reasonMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reason.ifBlank { "Motif *" },
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼")
                    }
                    DropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.82f)
                    ) {
                        reasons.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    reasonMenuExpanded = false
                                    onFormChange(amount, option, description, ownerPin)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { onFormChange(amount, reason, it, ownerPin) },
                    label = {
                        Text(
                            if (reason == "Autre") {
                                "Description complémentaire *"
                            } else {
                                "Description complémentaire — facultative"
                            }
                        )
                    },
                    supportingText = { Text("${description.length}/200") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == CashMovementType.CASH_OUT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ownerPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                onFormChange(amount, reason, description, it)
                            }
                        },
                        label = { Text("PIN partagé des sorties *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = amount.isNotBlank() &&
                    reason.isNotBlank() &&
                    (reason != "Autre" || description.isNotBlank()) &&
                    (type != CashMovementType.CASH_OUT || ownerPin.isNotBlank()) &&
                    !isLoading,
                modifier = Modifier.testTag("cash_movement_confirm"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == CashMovementType.CASH_IN) Color(0xFF1E8E3E) else Color(0xFFE63946)
                )
            ) {
                Text("Valider", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
