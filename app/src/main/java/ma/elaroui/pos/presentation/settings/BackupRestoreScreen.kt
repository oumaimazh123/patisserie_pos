package ma.elaroui.pos.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var pinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf<String?>(null) } // "BACKUP" or "RESTORE"

    // SAF Create Document Launcher for Backup
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.let { stream ->
                viewModel.performCreateBackup(stream, pinInput, passwordInput.ifBlank { null })
            }
        }
    }

    // SAF Open Document Launcher for Restore
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let { stream ->
                viewModel.performRestoreBackup(stream, pinInput, passwordInput.ifBlank { null })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde & Restauration") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Sécurité et Authentification Propriétaire", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.take(6) },
                        label = { Text("Code PIN Propriétaire *") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Mot de passe de Chiffrement (Optionnel)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Text("Recommandé: Ajoute un chiffrement fort AES-GCM 256 bits.", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. Créer une Sauvegarde", fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                    Text("Exporte l'intégralité des données (produits, tables, ventes, caisses) au format .cafeposbackup.", fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { createBackupLauncher.launch("POS_Backup_${System.currentTimeMillis()}.cafeposbackup") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pinInput.length >= 4 && !uiState.isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F))
                    ) {
                        Text("Créer & Sauvegarder 💾", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("2. Restaurer une Sauvegarde", fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                    Text("⚠️ Attention : La restauration remplace les données actuelles de l'application.", fontSize = 12.sp, color = Color(0xFFE63946))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pinInput.length >= 4 && !uiState.isProcessing
                    ) {
                        Text("Sélectionner un fichier de restauration", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(error, color = Color(0xFFE63946), fontWeight = FontWeight.Bold)
            }

            uiState.successMessage?.let { msg ->
                Text(msg, color = Color(0xFF2A9D8F), fontWeight = FontWeight.Bold)
            }
        }
    }
}
