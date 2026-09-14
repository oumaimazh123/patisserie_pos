package ma.elaroui.pos.presentation.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.PaymentMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    viewModel: SaleDetailViewModel,
    onNavigateToReceiptPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    SaleDetailContent(
        uiState = uiState,
        onBack = onBack,
        onNavigateToReceiptPreview = onNavigateToReceiptPreview,
        onReprint = viewModel::reprintReceipt,
        onRetry = viewModel::loadDetails
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailContent(
    uiState: SaleDetailUiState,
    onBack: () -> Unit,
    onNavigateToReceiptPreview: (Long) -> Unit,
    onReprint: () -> Unit,
    onRetry: () -> Unit
) {
    val order = uiState.order
    Scaffold(
        modifier = Modifier.testTag("sale_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Détail ${order?.orderNumber.orEmpty()}",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = DetailNavy, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            order == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.errorMessage ?: "Information de vente introuvable.",
                            color = DetailRed
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRetry) {
                            Text("Réessayer")
                        }
                    }
                }
            }

            else -> {
                val cancelled = order.status == OrderStatus.CANCELLED
                val receipt = uiState.receiptData
                val eventAt = if (cancelled) {
                    order.cancelledAt ?: order.updatedAt
                } else {
                    receipt?.paymentAt ?: order.completedAt ?: order.updatedAt
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag("sale_detail_content"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SaleHeader(
                            uiState = uiState,
                            eventAt = eventAt,
                            cancelled = cancelled
                        )
                    }

                    if (cancelled) {
                        item {
                            CancellationCard(
                                reason = order.cancellationReason,
                                cancelledAt = eventAt
                            )
                        }
                    }

                    item {
                        Text(
                            "Articles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DetailNavy
                        )
                    }

                    if (order.items.isEmpty()) {
                        item {
                            Surface(
                                color = DetailLight,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Aucun article enregistré.",
                                    color = DetailSlate,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(order.items, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = DetailLight)
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    if (maxWidth >= 480.dp) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    item.productNameSnapshot,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DetailNavy
                                                )
                                                Text(
                                                    "${item.quantity} × ${
                                                        MonetaryUtils.formatDh(
                                                            item.unitPriceSnapshotCentimes
                                                        )
                                                    }",
                                                    fontSize = 12.sp,
                                                    color = DetailSlate
                                                )
                                            }
                                            Text(
                                                MonetaryUtils.formatDh(item.lineTotalCentimes),
                                                fontWeight = FontWeight.Bold,
                                                color = DetailRed
                                            )
                                        }
                                    } else {
                                        Column {
                                            Text(
                                                item.productNameSnapshot,
                                                fontWeight = FontWeight.Bold,
                                                color = DetailNavy
                                            )
                                            Text(
                                                "${item.quantity} × ${
                                                    MonetaryUtils.formatDh(
                                                        item.unitPriceSnapshotCentimes
                                                    )
                                                }",
                                                fontSize = 12.sp,
                                                color = DetailSlate
                                            )
                                            Text(
                                                MonetaryUtils.formatDh(item.lineTotalCentimes),
                                                fontWeight = FontWeight.Bold,
                                                color = DetailRed,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        TotalsCard(uiState)
                    }

                    uiState.printMessage?.let { message ->
                        item {
                            Text(
                                message,
                                color = DetailTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!cancelled && receipt != null) {
                        item {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                if (maxWidth >= 500.dp) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ReceiptPreviewButton(
                                            orderId = order.id,
                                            onNavigate = onNavigateToReceiptPreview,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ReprintButton(
                                            isPrinting = uiState.isPrinting,
                                            onReprint = onReprint,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ReceiptPreviewButton(
                                            orderId = order.id,
                                            onNavigate = onNavigateToReceiptPreview,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        ReprintButton(
                                            isPrinting = uiState.isPrinting,
                                            onReprint = onReprint,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleHeader(
    uiState: SaleDetailUiState,
    eventAt: Long,
    cancelled: Boolean
) {
    val order = checkNotNull(uiState.order)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cancelled) DetailRed else DetailNavy
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Commande ${order.orderNumber}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${orderTypeLabel(order.type)}${
                    uiState.tableName?.let { " • Table $it" }.orEmpty()
                }",
                color = Color.White.copy(alpha = 0.82f)
            )
            Text(
                "${if (cancelled) "Annulée" else "Réglée"} le ${displayDetailDate(eventAt)}",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
            Text(
                "Caisse : ${uiState.registerName} • Caissier : ${uiState.cashierName}",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CancellationCard(reason: String?, cancelledAt: Long) {
    Surface(
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFFC62828),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cancelled_sale_notice")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Commande annulée", fontWeight = FontWeight.Bold)
            Text(reason?.takeIf { it.isNotBlank() } ?: "Motif non renseigné")
            Text(displayDetailDate(cancelledAt), fontSize = 12.sp)
            Text(
                "Cette commande est conservée dans l'historique et exclue du chiffre d'affaires.",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TotalsCard(uiState: SaleDetailUiState) {
    val order = checkNotNull(uiState.order)
    val cancelled = order.status == OrderStatus.CANCELLED
    val receipt = uiState.receiptData

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (cancelled) "VALEUR ANNULÉE" else "TOTAL RÉGLÉ",
                    fontWeight = FontWeight.Bold,
                    color = DetailNavy
                )
                Text(
                    MonetaryUtils.formatDh(order.totalCentimes),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (cancelled) DetailRed else DetailTeal
                )
            }
            if (!cancelled && receipt != null) {
                HorizontalDivider()
                Text(
                    "Mode de paiement : ${paymentMethodLabel(receipt.paymentMethod)}",
                    fontWeight = FontWeight.SemiBold,
                    color = DetailSlate
                )
                receipt.receivedAmountCentimes?.let { received ->
                    Text(
                        "Montant reçu : ${MonetaryUtils.formatDh(received)} • Monnaie : ${
                            MonetaryUtils.formatDh(receipt.changeAmountCentimes ?: 0L)
                        }",
                        fontSize = 12.sp,
                        color = DetailSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptPreviewButton(
    orderId: Long,
    onNavigate: (Long) -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = { onNavigate(orderId) },
        modifier = modifier.testTag("sale_detail_receipt_preview")
    ) {
        Text("Aperçu reçu")
    }
}

@Composable
private fun ReprintButton(
    isPrinting: Boolean,
    onReprint: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onReprint,
        modifier = modifier.testTag("sale_detail_reprint"),
        enabled = !isPrinting,
        colors = ButtonDefaults.buttonColors(containerColor = DetailNavy)
    ) {
        Text(if (isPrinting) "Impression…" else "Réimprimer")
    }
}

private fun orderTypeLabel(type: OrderType): String = when (type) {
    OrderType.DINE_IN -> "Sur place"
    OrderType.TAKEAWAY -> "À emporter"
    OrderType.COUNTER -> "Comptoir"
}

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Espèces"
    PaymentMethod.CARD -> "Carte / TPE"
    PaymentMethod.CARNET_CLIENT -> "Carnet client"
    PaymentMethod.MOBILE_QR -> "Paiement mobile"
}

private fun displayDetailDate(dateMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH).format(Date(dateMs))

private val DetailNavy = Color(0xFF1D3557)
private val DetailTeal = Color(0xFF2A9D8F)
private val DetailRed = Color(0xFFE63946)
private val DetailSlate = Color(0xFF64748B)
private val DetailLight = Color(0xFFF8F9FA)
