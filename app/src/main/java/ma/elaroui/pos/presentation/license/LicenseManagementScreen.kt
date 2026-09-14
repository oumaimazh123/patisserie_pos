package ma.elaroui.pos.presentation.license

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.license.LicenseState
import ma.elaroui.pos.core.license.LicenseStatus
import ma.elaroui.pos.core.license.LicenseType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class StatusConfig(
    val bgColor: Color,
    val contentColor: Color,
    val iconSymbol: String,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseManagementScreen(
    viewModel: LicenseViewModel,
    onBack: () -> Unit,
    canNavigateBack: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val licenseState = uiState.licenseState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestion de Licence", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canNavigateBack) {
                        TextButton(onClick = onBack) {
                            Text("← Retour", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1D3557),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status Banner Card
            LicenseStatusCard(licenseState = licenseState)

            // User Notification Snackbar/Message
            uiState.userMessage?.let { msg ->
                Surface(
                    color = if (uiState.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = if (uiState.isSuccess) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (uiState.isSuccess) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Continuer vers le POS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // 2. Installation Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Identifiant d'Installation Appareil", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1D3557))
                    Text(
                        text = licenseState.installationId,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF457B9D)
                    )
                    Text("Cet identifiant est lié de façon unique à cet appareil et cette installation.", fontSize = 12.sp, color = Color.Gray)
                }
            }

            // 3. License Request Code Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔑", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Code de Demande de Licence", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1D3557))
                    }
                    Text("Envoyez ce code au fournisseur pour obtenir votre clé de licence signée.", fontSize = 13.sp, color = Color.DarkGray)

                    Surface(
                        color = Color(0xFFF1F3F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCED4DA), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = uiState.requestCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF343A40)
                        )
                    }

                    Button(
                        onClick = { viewModel.copyRequestCodeToClipboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF457B9D)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📋", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copier le Code de Demande")
                    }
                }
            }

            // 4. Activation Input Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Activation de la Licence", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1D3557))
                    Text("Collez ici la clé de licence reçue de votre fournisseur :", fontSize = 13.sp, color = Color.DarkGray)

                    OutlinedTextField(
                        value = uiState.licenseKeyInput,
                        onValueChange = { viewModel.onLicenseKeyInputChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        placeholder = { Text("Collez votre clé de licence ici...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = { viewModel.activateLicense() },
                        enabled = !uiState.isActivating && uiState.licenseKeyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isActivating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Activer la Licence", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseStatusCard(licenseState: LicenseState) {
    val config = when (licenseState.status) {
        LicenseStatus.LICENCE_VALID -> StatusConfig(
            bgColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF2E7D32),
            iconSymbol = "✓",
            title = "Licence Valide ✓",
            description = "Titulaire: ${licenseState.customerName ?: "Client POS"}\nType: ${formatType(licenseState.licenseType)}${formatExpiry(licenseState.expirationDateMs)}"
        )
        LicenseStatus.TRIAL_ACTIVE -> StatusConfig(
            bgColor = Color(0xFFE3F2FD),
            contentColor = Color(0xFF1565C0),
            iconSymbol = "⏳",
            title = "Mode démo — Essai gratuit de 7 jours",
            description = "Temps restant : ${formatHours(licenseState.remainingTrialTimeMs)}. Vous pouvez activer la licence à tout moment."
        )
        LicenseStatus.TRIAL_EXPIRING_SOON -> StatusConfig(
            bgColor = Color(0xFFFFF8E1),
            contentColor = Color(0xFFF57F17),
            iconSymbol = "⚠️",
            title = "Période d'Essai Expire Bientôt !",
            description = "Il vous reste ${formatHours(licenseState.remainingTrialTimeMs)}. Veuillez activer votre licence pour éviter toute interruption."
        )
        LicenseStatus.TRIAL_EXPIRED -> StatusConfig(
            bgColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFC62828),
            iconSymbol = "🔒",
            title = "Période d'Essai Expirée",
            description = "Votre essai gratuit de 7 jours est terminé. L'application est bloquée, mais toutes vos données sont conservées. Activez une licence pour reprendre votre travail."
        )
        LicenseStatus.LICENCE_EXPIRED -> StatusConfig(
            bgColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFC62828),
            iconSymbol = "🔒",
            title = "Licence Expirée",
            description = "Votre licence d'abonnement a expiré. Veuillez contacter le fournisseur."
        )
        LicenseStatus.LICENCE_WRONG_DEVICE -> StatusConfig(
            bgColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFC62828),
            iconSymbol = "⚠️",
            title = "Licence Incompatible",
            description = "La licence fournie est liée à un autre appareil."
        )
        LicenseStatus.CLOCK_ROLLBACK_DETECTED -> StatusConfig(
            bgColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFC62828),
            iconSymbol = "⚠️",
            title = "Erreur d'Horloge Système",
            description = "Recul de l'horloge système détecté. Corrigez l'heure de votre appareil."
        )
        else -> StatusConfig(
            bgColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFC62828),
            iconSymbol = "⚠️",
            title = "Licence Invalide",
            description = "La clé de licence saisie n'est pas valide ou est corrompue."
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = config.bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(config.iconSymbol, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(config.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = config.contentColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(config.description, fontSize = 14.sp, color = config.contentColor.copy(alpha = 0.9f))
            }
        }
    }
}

private fun formatHours(ms: Long): String {
    val totalHours = ms / (1000 * 60 * 60)
    val days = totalHours / 24
    val hours = totalHours % 24
    return if (days > 0) "$days jour(s) et $hours heure(s)" else "$hours heure(s)"
}

private fun formatType(type: LicenseType?): String {
    return when (type) {
        LicenseType.FULL_LIFETIME -> "Licence Définitive (Achat unique)"
        LicenseType.SUBSCRIPTION -> "Licence Abonnement"
        LicenseType.TRIAL_EXTENSION -> "Extension d'Essai"
        else -> "Licence POS"
    }
}

private fun formatExpiry(ms: Long?): String {
    if (ms == null) return " (Illimitée)"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    return " (Expire le ${sdf.format(Date(ms))})"
}
