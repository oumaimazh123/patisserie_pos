package ma.elaroui.pos.desktop.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.display.CustomerDisplayDeviceDetector
import ma.elaroui.pos.desktop.display.DiscoveredCustomerDisplayDevice
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.display.CustomerDisplayConfig
import ma.elaroui.pos.shared.display.CustomerDisplayConnectionStatus
import ma.elaroui.pos.shared.display.CustomerDisplayController
import ma.elaroui.pos.shared.display.VfdBaudRate
import ma.elaroui.pos.shared.display.VfdConnectionMode
import ma.elaroui.pos.shared.display.VfdDataBits
import ma.elaroui.pos.shared.display.VfdParity
import ma.elaroui.pos.shared.display.VfdProtocol
import ma.elaroui.pos.shared.display.VfdStopBits

@Composable
fun CustomerDisplaySettingsScreen(
    controller: CustomerDisplayController,
    strings: DesktopStrings,
    onSaveSettings: suspend (CustomerDisplayConfig) -> Unit,
    onNavigateToEstablishment: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToLicense: () -> Unit,
    onBack: () -> Unit
) {
    val currentConfig by controller.config.collectAsState()
    val connectionStatus by controller.connectionStatus.collectAsState()

    var enabled by remember(currentConfig) { mutableStateOf(currentConfig.enabled) }
    var portName by remember(currentConfig) { mutableStateOf(currentConfig.portName) }
    var protocol by remember(currentConfig) { mutableStateOf(currentConfig.protocol) }
    var baudRate by remember(currentConfig) { mutableStateOf(currentConfig.baudRate) }

    var uiMessage by remember { mutableStateOf<UiMessage?>(null) }
    var isTestingDisplay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var discoveredDevices by remember { mutableStateOf(emptyList<DiscoveredCustomerDisplayDevice>()) }
    LaunchedEffect(Unit) {
        discoveredDevices = CustomerDisplayDeviceDetector.detectDevices()
        if (portName.isBlank() && discoveredDevices.isNotEmpty()) {
            portName = discoveredDevices.first().portName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(strings.settings, strings, onBack)

        SettingsTabBar(
            selectedTab = SettingsTab.CUSTOMER_DISPLAY,
            strings = strings,
            onNavigateToEstablishment = onNavigateToEstablishment,
            onNavigateToPrinters = onNavigateToPrinters,
            onNavigateToCustomerDisplay = {},
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToDataManagement = onNavigateToDataManagement,
            onNavigateToLicense = onNavigateToLicense
        )

        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(scrollState)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                border = BorderStroke(1.dp, PosColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "📟 " + strings.text("Afficheur client (VFD 2×20)", "Customer Display (VFD 2×20)", "شاشة الزبون"),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.BakeryBrown
                        )
                        Text(
                            strings.text(
                                "Affiche les articles, totaux et rendu monnaie en temps réel au client.",
                                "Display cart items, totals, and change in real time to the customer.",
                                "عرض المنتجات والمجموع والمتبقي في الوقت الفعلي للزبون."
                            ),
                            fontSize = 13.sp,
                            color = PosColors.TextMedium
                        )
                    }

                    PosInlineAlert(
                        message = uiMessage,
                        onDismiss = { uiMessage = null }
                    )

                    // 1. Activation Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    strings.text("Activer l'afficheur client", "Enable customer display", "تفعيل شاشة الزبون"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PosColors.TextHigh
                                )
                                Text(
                                    strings.text(
                                        "Transmet automatiquement les totaux au client dès le 1er produit",
                                        "Automatically stream totals to customer from the first product added",
                                        "إرسال المجموع إلى شاشة الزبون تلقائياً بدءاً من المنتج الأول"
                                    ),
                                    fontSize = 12.sp,
                                    color = PosColors.TextMedium
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it }
                            )
                        }
                    }

                    // 2. Statut de connexion & Test
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    strings.text("État :", "Status:", "الحالة:"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                val statusBadgeColor = when (connectionStatus) {
                                    CustomerDisplayConnectionStatus.CONNECTED -> PosColors.Success
                                    CustomerDisplayConnectionStatus.CONNECTING -> PosColors.Alert
                                    CustomerDisplayConnectionStatus.ERROR -> PosColors.Danger
                                    CustomerDisplayConnectionStatus.DISCONNECTED -> PosColors.TextMuted
                                }
                                Surface(
                                    color = statusBadgeColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, statusBadgeColor)
                                ) {
                                    Text(
                                        text = connectionStatus.labelFr,
                                        color = statusBadgeColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isTestingDisplay = true
                                        val ok = controller.testDisplay(durationSeconds = 5)
                                        isTestingDisplay = false
                                        uiMessage = if (ok) {
                                            UiMessage.success(
                                                strings.text(
                                                    "Message de test envoyé à l'afficheur (5 secondes) !",
                                                    "Test message sent to customer display (5 seconds)!",
                                                    "تم إرسال رسالة الاختبار إلى شاشة الزبون (5 ثوانٍ)!"
                                                )
                                            )
                                        } else {
                                            UiMessage.error(
                                                strings.text(
                                                    "Échec du test de l'afficheur. Vérifiez le port et l'activation.",
                                                    "Customer display test failed. Check port and activation.",
                                                    "فشل اختبار شاشة الزبون. تحقق من المنفذ والتفعيل."
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = !isTestingDisplay && enabled,
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    if (isTestingDisplay) strings.text("Test en cours...", "Testing...", "جاري الاختبار...")
                                    else "📟 " + strings.text("Tester l'afficheur", "Test customer display", "اختبار الشاشة"),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 3. Port de communication
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                strings.text("Port de communication", "Communication Port", "منفذ الاتصال"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                            TextButton(
                                onClick = {
                                    discoveredDevices = CustomerDisplayDeviceDetector.detectDevices()
                                }
                            ) {
                                Text("🔄 " + strings.text("Actualiser les ports", "Refresh ports", "تحديث المنافذ"), fontSize = 12.sp)
                            }
                        }

                        // Liste des ports disponibles sous forme de chips
                        if (discoveredDevices.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                discoveredDevices.take(4).forEach { dev ->
                                    val isSelected = portName == dev.portName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { portName = dev.portName },
                                        label = { Text(dev.portName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }

                        TouchTextField(
                            value = portName,
                            onValueChange = { portName = it },
                            label = strings.text("Nom du port (ex: COM1, COM3)", "Port name (e.g. COM1, COM3)", "اسم المنفذ"),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Vitesse (Baud Rate)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            strings.text("Vitesse de transmission (Baud)", "Transmission Speed (Baud)", "سرعة النقل"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(VfdBaudRate.B9600, VfdBaudRate.B19200, VfdBaudRate.B38400, VfdBaudRate.B115200).forEach { b ->
                                FilterChip(
                                    selected = baudRate == b,
                                    onClick = { baudRate = b },
                                    label = {
                                        Text(
                                            if (b == VfdBaudRate.B9600) "${b.rate} (Recommandé)" else b.rate.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (baudRate == b) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // 5. Protocole VFD
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            strings.text("Protocole de commande", "Command Protocol", "بروتوكول الأوامر"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.TextHigh
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(VfdProtocol.ESC_POS, VfdProtocol.CD5220, VfdProtocol.DSP800, VfdProtocol.UTC_STANDARD).forEach { p ->
                                FilterChip(
                                    selected = protocol == p,
                                    onClick = { protocol = p },
                                    label = {
                                        Text(
                                            if (p == VfdProtocol.ESC_POS) "ESC/POS (Standard)" else p.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (protocol == p) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 6. Bouton Enregistrer
                    Button(
                        onClick = {
                            val updatedConfig = currentConfig.copy(
                                enabled = enabled,
                                portName = portName.trim(),
                                protocol = protocol,
                                baudRate = baudRate,
                                connectionMode = if (portName.startsWith("COM", ignoreCase = true)) VfdConnectionMode.SERIAL else VfdConnectionMode.USB,
                                parity = VfdParity.NONE,
                                dataBits = VfdDataBits.EIGHT,
                                stopBits = VfdStopBits.ONE,
                                columns = 20,
                                rows = 2,
                                thankYouDurationSeconds = currentConfig.thankYouDurationSeconds.coerceAtLeast(1)
                            )
                            scope.launch {
                                controller.updateConfig(updatedConfig)
                                onSaveSettings(updatedConfig)
                                uiMessage = UiMessage.success(strings.text("Paramètres enregistrés avec succès !", "Settings saved successfully!", "تم حفظ الإعدادات بنجاح!"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(
                            "💾 " + strings.save,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
