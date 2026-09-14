package ma.elaroui.pos.presentation.sales

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ma.elaroui.pos.core.util.MonetaryUtils
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    viewModel: SaleDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val receiptData = uiState.receiptData
    val locale = java.util.Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aperçu du Reçu") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Fermer", color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (receiptData == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Aperçu du reçu introuvable.", color = Color(0xFFE63946))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Receipt Paper View Simulation
                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .weight(1f)
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
                    color = Color(0xFFFFFFF0), // Off-white thermal paper simulation
                    shape = RoundedCornerShape(4.dp),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (receiptData.isReprint) {
                            Text("*** REIMPRESSION / DUPLICATA ***", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        receiptData.restaurantLogoUri?.let { logoUri ->
                            val logo = remember(logoUri) {
                                runCatching {
                                    context.contentResolver.openInputStream(Uri.parse(logoUri))?.use {
                                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                                    }
                                }.getOrNull()
                            }
                            logo?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = "Logo de l'établissement",
                                    modifier = Modifier
                                        .widthIn(max = 120.dp)
                                        .heightIn(max = 80.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        Text(receiptData.restaurantName, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        receiptData.restaurantAddress?.let { Text(it, fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.restaurantPhone?.let { Text("Tél: $it", fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.sellerIce?.let { Text("ICE: $it", fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.sellerTaxId?.let { Text("IF: $it", fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.sellerCommercialRegister?.let { Text("RC: $it", fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.sellerPatente?.let { Text("Patente: $it", fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("----------------------------------------", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("N° Commande: ${receiptData.orderNumber}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("Type: ${receiptData.orderType.name} ${receiptData.tableName?.let { "- Table $it" } ?: ""}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("Caisse: ${receiptData.registerName} | Serveur: ${receiptData.cashierName}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("Date: ${dateFormat.format(Date(receiptData.paymentAt))}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            receiptData.buyerCompanyName?.let { Text("Client: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            receiptData.buyerAddress?.let { Text("Adresse client: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            receiptData.buyerIce?.let { Text("ICE client: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("----------------------------------------", fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(receiptData.items) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${item.name} x${item.quantity}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    Text(MonetaryUtils.formatDh(item.lineTotalCentimes), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Text("----------------------------------------", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL TTC:", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            Text(MonetaryUtils.formatDh(receiptData.totalCentimes), fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mode de paiement:", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(receiptData.paymentMethod.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        receiptData.receivedAmountCentimes?.let {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reçu:", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(MonetaryUtils.formatDh(it), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        receiptData.changeAmountCentimes?.let {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rendu:", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(MonetaryUtils.formatDh(it), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        receiptData.wifiName?.let { Text("Wi-Fi: $it", fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        receiptData.wifiCode?.let { Text("Code Wi-Fi: $it", fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace) }
                        if (receiptData.wifiName != null || receiptData.wifiCode != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(receiptData.thankYouMessage, fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.reprintReceipt() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3557))
                ) {
                    Text("Imprimer le Reçu")
                }
            }
        }
    }
}
