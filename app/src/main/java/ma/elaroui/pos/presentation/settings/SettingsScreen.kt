package ma.elaroui.pos.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPrinterSettings: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToLicenseManagement: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current
    val context = LocalContext.current
    val logoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.updateBusinessLogo(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(adaptive.screenContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Information du Établissement", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(uiState.restaurantName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                        Text("Adresse: ${uiState.restaurantAddress.ifBlank { "Non renseignée" }}", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("Tél: ${uiState.restaurantPhone.ifBlank { "Non renseigné" }}", fontSize = 12.sp, color = Color(0xFF64748B))
                        uiState.sellerIce.takeIf { it.isNotBlank() }?.let { Text("ICE: $it", fontSize = 12.sp, color = Color(0xFF64748B)) }
                        uiState.sellerTaxId.takeIf { it.isNotBlank() }?.let { Text("IF: $it", fontSize = 12.sp, color = Color(0xFF64748B)) }
                        uiState.sellerCommercialRegister.takeIf { it.isNotBlank() }?.let { Text("RC: $it", fontSize = 12.sp, color = Color(0xFF64748B)) }
                        uiState.sellerPatente.takeIf { it.isNotBlank() }?.let { Text("Patente: $it", fontSize = 12.sp, color = Color(0xFF64748B)) }
                        uiState.wifiName.takeIf { it.isNotBlank() }?.let { Text("Wi-Fi: $it", fontSize = 12.sp, color = Color(0xFF64748B)) }
                        Text("Devise: MAD (Dirham Marocain)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2A9D8F))
                        Text(
                            if (uiState.restaurantLogoUri.isBlank()) "Logo: Non renseigné" else "Logo: Configuré",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::openBusinessDialog,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Modifier") }
                    }
                }
                uiState.successMessage?.let {
                    Text(it, color = Color(0xFF2A9D8F), modifier = Modifier.padding(top = 8.dp))
                }
                uiState.errorMessage?.let {
                    Text(it, color = Color(0xFFE63946), modifier = Modifier.padding(top = 8.dp))
                }
            }

            item {
                Text("Licence & Sécurité", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTile(
                    title = "Gestion de Licence",
                    subtitle = "Clé RSA hors-ligne, période d'essai et identifiant d'installation",
                    icon = "🔑",
                    onClick = onNavigateToLicenseManagement
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTile(
                    title = "Sauvegarde & Restauration Locale",
                    subtitle = "Format .cafeposbackup chiffré AES-GCM",
                    icon = "💾",
                    onClick = onNavigateToBackupRestore
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTile(
                    title = "PIN partagé des sorties",
                    subtitle = if (uiState.isCashOutPinConfigured) {
                        "Configuré — toucher pour le remplacer"
                    } else {
                        "À configurer avant toute sortie d’espèces"
                    },
                    icon = "🔐",
                    onClick = viewModel::openCashOutPinDialog
                )
            }

            item {
                Text("Matériel & Périphériques", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTile(
                    title = "Imprimantes Client & Cuisine",
                    subtitle = "Client: ${
                        if (uiState.printerSettings.enabled) uiState.printerSettings.printerType.name else "Désactivée"
                    } • Cuisine: ${
                        if (uiState.printerSettings.kitchenEnabled) {
                            uiState.printerSettings.kitchenPrinterType.name
                        } else {
                            "Désactivée"
                        }
                    }",
                    icon = "🖨️",
                    onClick = onNavigateToPrinterSettings
                )
            }
        }
    }

    if (uiState.isBusinessDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeBusinessDialog,
            title = { Text("Informations de l'établissement") },
            text = {
                Column(
                    modifier = Modifier
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.errorMessage?.let { Text(it, color = Color(0xFFE63946)) }
                    OutlinedTextField(
                        value = uiState.businessNameInput,
                        onValueChange = {
                            viewModel.updateBusinessForm(
                                it,
                                uiState.businessPhoneInput,
                                uiState.businessAddressInput
                            )
                        },
                        label = { Text("Nom *") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.businessAddressInput,
                        onValueChange = {
                            viewModel.updateBusinessForm(
                                uiState.businessNameInput,
                                uiState.businessPhoneInput,
                                it
                            )
                        },
                        label = { Text("Adresse") }
                    )
                    OutlinedTextField(
                        value = uiState.businessPhoneInput,
                        onValueChange = {
                            viewModel.updateBusinessForm(
                                uiState.businessNameInput,
                                it,
                                uiState.businessAddressInput
                            )
                        },
                        label = { Text("Téléphone") },
                        singleLine = true
                    )
                    Text("Informations légales (optionnelles)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.sellerIceInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                it, uiState.sellerTaxIdInput, uiState.sellerCommercialRegisterInput,
                                uiState.sellerPatenteInput, uiState.wifiNameInput, uiState.wifiCodeInput
                            )
                        },
                        label = { Text("ICE fournisseur (15 chiffres)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.sellerTaxIdInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                uiState.sellerIceInput, it, uiState.sellerCommercialRegisterInput,
                                uiState.sellerPatenteInput, uiState.wifiNameInput, uiState.wifiCodeInput
                            )
                        },
                        label = { Text("Identifiant fiscal — IF") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.sellerCommercialRegisterInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                uiState.sellerIceInput, uiState.sellerTaxIdInput, it,
                                uiState.sellerPatenteInput, uiState.wifiNameInput, uiState.wifiCodeInput
                            )
                        },
                        label = { Text("Registre de commerce — RC") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.sellerPatenteInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                uiState.sellerIceInput, uiState.sellerTaxIdInput,
                                uiState.sellerCommercialRegisterInput, it,
                                uiState.wifiNameInput, uiState.wifiCodeInput
                            )
                        },
                        label = { Text("Patente") },
                        singleLine = true
                    )
                    Text("Wi-Fi client (optionnel)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.wifiNameInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                uiState.sellerIceInput, uiState.sellerTaxIdInput,
                                uiState.sellerCommercialRegisterInput, uiState.sellerPatenteInput,
                                it, uiState.wifiCodeInput
                            )
                        },
                        label = { Text("Nom du réseau Wi-Fi") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.wifiCodeInput,
                        onValueChange = {
                            viewModel.updateLegalAndWifiForm(
                                uiState.sellerIceInput, uiState.sellerTaxIdInput,
                                uiState.sellerCommercialRegisterInput, uiState.sellerPatenteInput,
                                uiState.wifiNameInput, it
                            )
                        },
                        label = { Text("Code Wi-Fi") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = { logoLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (uiState.businessLogoUriInput.isBlank()) {
                                "Sélectionner un logo"
                            } else {
                                "Changer le logo"
                            }
                        )
                    }
                    if (uiState.businessLogoUriInput.isNotBlank()) {
                        TextButton(onClick = { viewModel.updateBusinessLogo(null) }) {
                            Text("Supprimer le logo")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::saveBusinessInformation) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::closeBusinessDialog) {
                    Text("Annuler")
                }
            }
        )
    }

    if (uiState.isCashOutPinDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeCashOutPinDialog,
            title = { Text("PIN partagé des sorties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ce code est indépendant du PIN personnel du propriétaire. Communiquez-le uniquement aux caissiers autorisés à effectuer des sorties.",
                        color = Color(0xFF64748B)
                    )
                    uiState.errorMessage?.let { Text(it, color = Color(0xFFE63946)) }
                    OutlinedTextField(
                        value = uiState.cashOutPinInput,
                        onValueChange = {
                            viewModel.updateCashOutPinForm(
                                it,
                                uiState.cashOutPinConfirmationInput
                            )
                        },
                        label = { Text("Nouveau PIN (4 à 6 chiffres)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.cashOutPinConfirmationInput,
                        onValueChange = {
                            viewModel.updateCashOutPinForm(uiState.cashOutPinInput, it)
                        },
                        label = { Text("Confirmer le PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::saveCashOutApprovalPin,
                    enabled = uiState.cashOutPinInput.length in 4..6 &&
                        uiState.cashOutPinConfirmationInput.length in 4..6
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::closeCashOutPinDialog) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun SettingsTile(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1D3557))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Text("→", fontSize = 20.sp, color = Color(0xFF64748B))
        }
    }
}
