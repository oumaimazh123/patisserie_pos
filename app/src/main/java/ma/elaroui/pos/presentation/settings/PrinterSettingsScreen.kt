package ma.elaroui.pos.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.domain.model.PrinterSettings
import ma.elaroui.pos.domain.model.PrinterType
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current
    var draft by remember { mutableStateOf(uiState.printerSettings) }
    LaunchedEffect(uiState.printerSettings) { draft = uiState.printerSettings }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration des Imprimantes") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Navy, fontWeight = FontWeight.Bold)
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(adaptive.screenContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrinterEndpointCard(
                title = "Imprimante Ticket Client",
                description = "Factures et reçus remis au client",
                enabled = draft.enabled,
                type = draft.printerType,
                name = draft.printerName.orEmpty(),
                address = draft.printerAddress.orEmpty(),
                paperWidth = draft.paperWidth,
                allowAndroidSystem = true,
                onEnabled = { draft = draft.copy(enabled = it) },
                onType = {
                    draft = draft.copy(printerType = it)
                    if (it == PrinterType.BLUETOOTH_ESCPOS && Build.VERSION.SDK_INT >= 31) {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                onName = { draft = draft.copy(printerName = it) },
                onAddress = { draft = draft.copy(printerAddress = it) },
                onWidth = { draft = draft.copy(paperWidth = it) },
                onTest = { viewModel.printTestReceipt(false) }
            )
            PrinterEndpointCard(
                title = "Imprimante Cuisine",
                description = "Tickets de préparation envoyés en cuisine",
                enabled = draft.kitchenEnabled,
                type = draft.kitchenPrinterType,
                name = draft.kitchenPrinterName.orEmpty(),
                address = draft.kitchenPrinterAddress.orEmpty(),
                paperWidth = draft.kitchenPaperWidth,
                allowAndroidSystem = false,
                onEnabled = { draft = draft.copy(kitchenEnabled = it) },
                onType = {
                    draft = draft.copy(kitchenPrinterType = it)
                    if (it == PrinterType.BLUETOOTH_ESCPOS && Build.VERSION.SDK_INT >= 31) {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                onName = { draft = draft.copy(kitchenPrinterName = it) },
                onAddress = { draft = draft.copy(kitchenPrinterAddress = it) },
                onWidth = { draft = draft.copy(kitchenPaperWidth = it) },
                onTest = { viewModel.printTestReceipt(true) }
            )
            uiState.errorMessage?.let {
                Text(it, color = Color(0xFFE63946), fontWeight = FontWeight.Bold)
            }
            uiState.successMessage?.let {
                Text(it, color = Color(0xFF2A9D8F), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.updatePrinterSettings(draft) },
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enregistrer les deux imprimantes")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrinterEndpointCard(
    title: String,
    description: String,
    enabled: Boolean,
    type: PrinterType,
    name: String,
    address: String,
    paperWidth: Int,
    allowAndroidSystem: Boolean,
    onEnabled: (Boolean) -> Unit,
    onType: (PrinterType) -> Unit,
    onName: (String) -> Unit,
    onAddress: (String) -> Unit,
    onWidth: (Int) -> Unit,
    onTest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, color = Navy)
                    Text(description, color = Color(0xFF64748B))
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allowAndroidSystem) {
                    FilterChip(
                        selected = type == PrinterType.ANDROID_SYSTEM,
                        onClick = { onType(PrinterType.ANDROID_SYSTEM) },
                        label = { Text("Android/PDF") }
                    )
                }
                FilterChip(
                    selected = type == PrinterType.ETHERNET_ESCPOS,
                    onClick = { onType(PrinterType.ETHERNET_ESCPOS) },
                    label = { Text("Ethernet") }
                )
                FilterChip(
                    selected = type == PrinterType.BLUETOOTH_ESCPOS,
                    onClick = { onType(PrinterType.BLUETOOTH_ESCPOS) },
                    label = { Text("Bluetooth") }
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Nom de l'imprimante") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (type != PrinterType.ANDROID_SYSTEM) {
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddress,
                    label = {
                        Text(
                            if (type == PrinterType.ETHERNET_ESCPOS) {
                                "Adresse IP:port (port 9100 par défaut)"
                            } else {
                                "Adresse Bluetooth MAC"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = paperWidth == 80,
                    onClick = { onWidth(80) },
                    label = { Text("80 mm") }
                )
                FilterChip(
                    selected = paperWidth == 58,
                    onClick = { onWidth(58) },
                    label = { Text("58 mm") }
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onTest, enabled = enabled) {
                    Text("Tester")
                }
            }
        }
    }
}

private val Navy = Color(0xFF1D3557)
