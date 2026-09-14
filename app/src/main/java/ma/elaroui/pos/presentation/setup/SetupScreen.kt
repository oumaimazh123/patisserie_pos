package ma.elaroui.pos.presentation.setup

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration POS - Étape ${uiState.currentStep} / 6", color = Color.White) },
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
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFE63946),
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            when (uiState.currentStep) {
                1 -> StepWelcome()
                2 -> StepLanguage(uiState.selectedLanguage) { viewModel.updateLanguage(it) }
                3 -> StepRestaurantInfo(
                    name = uiState.restaurantName,
                    phone = uiState.restaurantPhone,
                    address = uiState.restaurantAddress,
                    sellerIce = uiState.sellerIce,
                    sellerTaxId = uiState.sellerTaxId,
                    sellerCommercialRegister = uiState.sellerCommercialRegister,
                    sellerPatente = uiState.sellerPatente,
                    wifiName = uiState.wifiName,
                    wifiCode = uiState.wifiCode,
                    onUpdate = { n, p, a -> viewModel.updateRestaurantInfo(n, p, a) },
                    onLegalAndWifiUpdate = viewModel::updateLegalAndWifiInfo
                )
                4 -> StepOwnerAccount(
                    name = uiState.ownerName,
                    pin = uiState.ownerPin,
                    pinConfirm = uiState.ownerPinConfirm,
                    onUpdate = { n, p, pc -> viewModel.updateOwnerInfo(n, p, pc) }
                )
                5 -> StepFirstRegister(
                    name = uiState.registerName,
                    onUpdate = { viewModel.updateRegisterName(it) }
                )
                6 -> StepSummary(uiState)
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.currentStep > 1) {
                    OutlinedButton(
                        onClick = { viewModel.previousStep() },
                        enabled = !uiState.isSubmitting
                    ) {
                        Text("Retour")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (uiState.currentStep < 6) {
                    Button(
                        onClick = { viewModel.nextStep() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        Text("Suivant", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = { viewModel.submitSetup(onSetupComplete) },
                        enabled = !uiState.isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Terminer la configuration", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bienvenue sur POS Café & Restaurant", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Cette assistant va vous guider pour configurer votre établissement et votre compte Propriétaire.",
            fontSize = 16.sp, color = Color(0xFF64748B)
        )
    }
}

@Composable
private fun StepLanguage(selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Choisir la langue d'affichage", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        listOf("fr" to "Français", "ar" to "العربية", "en" to "English").forEach { (code, label) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                onClick = { onSelect(code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == code) Color(0xFFFFF0F1) else Color.White
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (selected == code), onClick = { onSelect(code) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StepRestaurantInfo(
    name: String,
    phone: String,
    address: String,
    sellerIce: String,
    sellerTaxId: String,
    sellerCommercialRegister: String,
    sellerPatente: String,
    wifiName: String,
    wifiCode: String,
    onUpdate: (String, String, String) -> Unit,
    onLegalAndWifiUpdate: (String, String, String, String, String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Informations du Restaurant", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onUpdate(it, phone, address) },
            label = { Text("Nom de l'établissement *") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { onUpdate(name, it, address) },
            label = { Text("Téléphone (Optionnel)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { onUpdate(name, phone, it) },
            label = { Text("Adresse (Optionnelle)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Informations légales (optionnelles)", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = sellerIce,
            onValueChange = { onLegalAndWifiUpdate(it, sellerTaxId, sellerCommercialRegister, sellerPatente, wifiName, wifiCode) },
            label = { Text("ICE fournisseur (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = sellerTaxId,
            onValueChange = { onLegalAndWifiUpdate(sellerIce, it, sellerCommercialRegister, sellerPatente, wifiName, wifiCode) },
            label = { Text("Identifiant fiscal — IF (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = sellerCommercialRegister,
            onValueChange = { onLegalAndWifiUpdate(sellerIce, sellerTaxId, it, sellerPatente, wifiName, wifiCode) },
            label = { Text("Registre de commerce — RC (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = sellerPatente,
            onValueChange = { onLegalAndWifiUpdate(sellerIce, sellerTaxId, sellerCommercialRegister, it, wifiName, wifiCode) },
            label = { Text("Patente (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Wi-Fi client (optionnel)", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiName,
            onValueChange = { onLegalAndWifiUpdate(sellerIce, sellerTaxId, sellerCommercialRegister, sellerPatente, it, wifiCode) },
            label = { Text("Nom du réseau Wi-Fi (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiCode,
            onValueChange = { onLegalAndWifiUpdate(sellerIce, sellerTaxId, sellerCommercialRegister, sellerPatente, wifiName, it) },
            label = { Text("Code Wi-Fi (Optionnel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Devise: MAD (Dirham Marocain)", fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
    }
}

@Composable
private fun StepOwnerAccount(
    name: String,
    pin: String,
    pinConfirm: String,
    onUpdate: (String, String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Compte Propriétaire (Owner)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onUpdate(it, pin, pinConfirm) },
            label = { Text("Nom du Propriétaire *") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onUpdate(name, it, pinConfirm) },
            label = { Text("Code PIN (4 à 6 chiffres) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pinConfirm,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onUpdate(name, pin, it) },
            label = { Text("Confirmer le code PIN *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepFirstRegister(name: String, onUpdate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Premier Caisse Enregistreuse", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onUpdate,
            label = { Text("Nom de la caisse") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepSummary(state: SetupUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Résumé de la Configuration", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Établissement: ${state.restaurantName}", fontWeight = FontWeight.Bold)
                Text("Langue: ${state.selectedLanguage.uppercase()}")
                Text("Propriétaire: ${state.ownerName}")
                Text("Caisse principale: ${state.registerName}")
                Text("Devise: MAD")
            }
        }
    }
}
