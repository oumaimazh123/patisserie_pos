@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.pos.active

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosUi
import ma.elaroui.pos.desktop.presentation.components.TouchPinField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.touchHorizontalDragScroll
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.RestaurantTable
import ma.elaroui.pos.shared.domain.TableStatus
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun ActiveOrdersScreen(
    openOrders: List<Order>,
    tables: List<RestaurantTable>,
    strings: DesktopStrings,
    onResumeOrder: (Order) -> Unit,
    onPaymentOrder: (Order) -> Unit,
    onCancelOrder: (orderId: Long, reason: String, cancellationPin: String?) -> Unit,
    onMoveOrderTable: (orderId: Long, newTableId: Long?) -> Unit,
    isCancellationPinRequired: Boolean = false,
    onVerifyCancellationPin: (String) -> Boolean = { true },
    onBack: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var orderToCancel by remember { mutableStateOf<Order?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    var orderToMoveTable by remember { mutableStateOf<Order?>(null) }

    var orderPendingPinAuth by remember { mutableStateOf<Order?>(null) }
    var enteredCancellationPin by remember { mutableStateOf("") }
    var pinAuthError by remember { mutableStateOf<String?>(null) }

    val filteredOrders = openOrders.filter { order ->
        searchQuery.isBlank() || order.number.contains(searchQuery, ignoreCase = true)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PosUi.Canvas)
    ) {
        val screenWidth = maxWidth
        val isWideScreen = screenWidth >= 1080.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Responsive Top Search Bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PosColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TouchTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = strings.search + "…",
                        leadingIcon = { Text("🔍", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )

                    // Orders Count Badge
                    Surface(
                        color = PosColors.PrimaryLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${filteredOrders.size} ${strings.text("commande(s)", "order(s)", "طلب")}",
                            color = PosColors.BakeryBrown,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Orders Content (Adaptive Grid on wide screens, List on standard/compact screens)
            if (filteredOrders.isEmpty()) {
                EmptyStateCard(title = strings.noActiveOrders, modifier = Modifier.weight(1f))
            } else if (isWideScreen) {
                val gridState = rememberLazyGridState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 480.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .touchDragScroll(gridState),
                        contentPadding = PaddingValues(end = 14.dp, bottom = 32.dp)
                    ) {
                        items(filteredOrders, key = { it.id }) { order ->
                            val tableName = order.tableId?.let { tid -> tables.firstOrNull { it.id == tid }?.name }
                            ActiveOrderCard(
                                order = order,
                                tableName = tableName,
                                strings = strings,
                                onMoveTable = { orderToMoveTable = order },
                                onCancel = {
                                    if (isCancellationPinRequired) {
                                        orderPendingPinAuth = order
                                        enteredCancellationPin = ""
                                        pinAuthError = null
                                    } else {
                                        orderToCancel = order
                                        cancelReason = ""
                                        enteredCancellationPin = ""
                                    }
                                },
                                onResume = { onResumeOrder(order) },
                                onPayment = { onPaymentOrder(order) }
                            )
                        }
                    }

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(gridState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        style = defaultScrollbarStyle().copy(
                            unhoverColor = PosColors.Border,
                            hoverColor = PosColors.Primary,
                            thickness = 8.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .touchDragScroll(listState),
                        contentPadding = PaddingValues(end = 14.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredOrders, key = { it.id }) { order ->
                            val tableName = order.tableId?.let { tid -> tables.firstOrNull { it.id == tid }?.name }
                            ActiveOrderCard(
                                order = order,
                                tableName = tableName,
                                strings = strings,
                                onMoveTable = { orderToMoveTable = order },
                                onCancel = {
                                    if (isCancellationPinRequired) {
                                        orderPendingPinAuth = order
                                        enteredCancellationPin = ""
                                        pinAuthError = null
                                    } else {
                                        orderToCancel = order
                                        cancelReason = ""
                                        enteredCancellationPin = ""
                                    }
                                },
                                onResume = { onResumeOrder(order) },
                                onPayment = { onPaymentOrder(order) }
                            )
                        }
                    }

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        style = defaultScrollbarStyle().copy(
                            unhoverColor = PosColors.Border,
                            hoverColor = PosColors.Primary,
                            thickness = 8.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                    )
                }
            }
        }
    }

    // Modal 1: PIN d'autorisation d'annulation (si exigé pour le caissier)
    orderPendingPinAuth?.let { order ->
        AlertDialog(
            onDismissRequest = { orderPendingPinAuth = null; enteredCancellationPin = ""; pinAuthError = null },
            title = {
                Text(
                    strings.text("Code PIN d’annulation — ${order.number}", "Cancellation PIN — ${order.number}", "رمز PIN للإلغاء — ${order.number}"),
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        strings.text(
                            "Cette action requiert l'autorisation par code PIN (4 à 6 chiffres).",
                            "This action requires authorization via PIN (4 to 6 digits).",
                            "يتطلب هذا الإجراء تصريحًا برمز PIN (من 4 إلى 6 أرقام)."
                        ),
                        fontSize = 13.sp,
                        color = PosColors.TextMedium
                    )

                    TouchPinField(
                        pin = enteredCancellationPin,
                        onPinChange = { enteredCancellationPin = it; pinAuthError = null },
                        label = strings.text("Code PIN (4 à 6 chiffres)", "PIN Code (4-6 digits)", "رمز PIN (4 إلى 6 أرقام)"),
                        maxDigits = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!pinAuthError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PosColors.DangerLight,
                            border = BorderStroke(1.dp, PosColors.Danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pinAuthError!!,
                                color = PosColors.Danger,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validationErr = ma.elaroui.pos.shared.rules.PinValidationRules.validatePin(enteredCancellationPin)
                        if (validationErr != null) {
                            pinAuthError = when (validationErr) {
                                ma.elaroui.pos.shared.rules.PinValidationError.BLANK -> strings.required
                                ma.elaroui.pos.shared.rules.PinValidationError.INVALID_LENGTH -> "Le PIN doit comporter 4 à 6 chiffres."
                                ma.elaroui.pos.shared.rules.PinValidationError.NOT_DIGITS -> "Le PIN ne doit contenir que des chiffres."
                                ma.elaroui.pos.shared.rules.PinValidationError.MISMATCH -> strings.pinMismatch
                            }
                            return@Button
                        }
                        if (onVerifyCancellationPin(enteredCancellationPin)) {
                            orderToCancel = order
                            cancelReason = ""
                            orderPendingPinAuth = null
                            pinAuthError = null
                        } else {
                            pinAuthError = strings.text("Code PIN incorrect.", "Incorrect PIN.", "رمز PIN غير صحيح.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { orderPendingPinAuth = null; enteredCancellationPin = ""; pinAuthError = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Modal 2: Cancel Order Reason Dialog
    orderToCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { orderToCancel = null; enteredCancellationPin = "" },
            title = {
                Text(
                    "${strings.cancelOrder} — ${order.number}",
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val cancelScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .touchDragScroll(cancelScrollState)
                        .verticalScroll(cancelScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val quickCancelReasons = listOf(
                        "Erreur de saisie",
                        "Client parti",
                        "Article indisponible",
                        "Autre raison"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            strings.text("Motifs rapides :", "Quick reasons:", "أسباب سريعة:"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextMedium
                        )
                        val reasonsListState = rememberLazyListState()
                        LazyRow(
                            state = reasonsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .touchHorizontalDragScroll(reasonsListState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(quickCancelReasons) { reason ->
                                val isSelected = cancelReason == reason
                                Surface(
                                    onClick = { cancelReason = reason },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PosColors.Primary else PosColors.Workspace,
                                    border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
                                    modifier = Modifier.height(36.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                        Text(
                                            reason,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else PosColors.TextHigh
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TouchTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = strings.cancelReason,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cancelReason.isNotBlank()) {
                            onCancelOrder(order.id, cancelReason.trim(), enteredCancellationPin.ifBlank { null })
                            orderToCancel = null
                            enteredCancellationPin = ""
                        }
                    },
                    enabled = cancelReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { orderToCancel = null; enteredCancellationPin = "" },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Move Table Dialog Modal
    orderToMoveTable?.let { order ->
        AlertDialog(
            onDismissRequest = { orderToMoveTable = null },
            title = {
                Text(
                    "${strings.moveTable} — ${order.number}",
                    fontWeight = FontWeight.Bold,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val availableTables = tables.filter { it.active && (it.status == TableStatus.AVAILABLE || it.id == order.tableId) }
                val tableGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = tableGridState,
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .heightIn(max = 280.dp)
                        .touchDragScroll(tableGridState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableTables) { table ->
                        val isCurrent = table.id == order.tableId
                        Surface(
                            onClick = {
                                onMoveOrderTable(order.id, table.id)
                                orderToMoveTable = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) PosColors.Primary else PosColors.Workspace,
                            border = BorderStroke(1.dp, if (isCurrent) PosColors.Primary else PosColors.Border),
                            modifier = Modifier
                                .height(56.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text(
                                    table.name + if (isCurrent) " (actuelle)" else "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else PosColors.TextHigh,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = { orderToMoveTable = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveOrderCard(
    order: Order,
    tableName: String?,
    strings: DesktopStrings,
    onMoveTable: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onPayment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==========================================
            // TOP / MAIN ROW
            // Left: Order number (large & bold) + Creation Time Badge (MM/JJ HH:mm)
            // Right: Total amount (strongly visible)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Order number + Creation Date/Time Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = order.number,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PosColors.BakeryBrown
                    )

                    val formattedDate = formatOrderCreationDateTime(order.createdAtEpochMilliseconds)
                    if (formattedDate.isNotBlank() && formattedDate != "—") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PosColors.PrimaryLight,
                            border = BorderStroke(1.dp, PosColors.Primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("🕒", fontSize = 12.sp)
                                Text(
                                    text = formattedDate,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.PrimaryDark
                                )
                            }
                        }
                    }
                }

                // Right: Total amount (strongly visible)
                Text(
                    text = "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PosColors.Primary
                )
            }

            // ==========================================
            // SECOND ROW
            // Left: Number of articles, Table number when applicable, Status
            // Right: Main action: "Procéder au paiement"
            // ==========================================
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val isNarrow = availableWidth < 540.dp

                if (isNarrow) {
                    // Narrow: Stack metadata and Payment CTA
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metadata items
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Number of articles
                            Surface(
                                color = PosColors.Workspace,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "📦 " + strings.text(
                                        "${order.lines.sumOf { it.quantity }} article(s)",
                                        "${order.lines.sumOf { it.quantity }} item(s)",
                                        "${order.lines.sumOf { it.quantity }} عنصر"
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PosColors.TextMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Current status
                            Surface(
                                color = PosColors.SuccessLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "⏳ " + strings.text("En attente", "On hold", "معلق"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PosColors.Success,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Main Action: Procéder au paiement (Full width on narrow)
                        Button(
                            onClick = onPayment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Success),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)
                        ) {
                            Text(
                                text = "💳 " + strings.proceedToPayment,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Normal / Wide: Row with Left (Metadata) and Right (Main Payment Action)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Metadata items
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Number of articles
                            Surface(
                                color = PosColors.Workspace,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "📦 " + strings.text(
                                        "${order.lines.sumOf { it.quantity }} article(s)",
                                        "${order.lines.sumOf { it.quantity }} item(s)",
                                        "${order.lines.sumOf { it.quantity }} عنصر"
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PosColors.TextMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }

                            // Current status
                            Surface(
                                color = PosColors.SuccessLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "⏳ " + strings.text("En attente", "On hold", "معلق"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PosColors.Success,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Right: Main Action: Procéder au paiement
                        Button(
                            onClick = onPayment,
                            modifier = Modifier
                                .height(52.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PosColors.Success),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)
                        ) {
                            Text(
                                text = "💳 " + strings.proceedToPayment,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Compact preview of items if available (keeps cashier informed)
            if (order.lines.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val previewLines = order.lines.take(3)
                        previewLines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${line.quantity}× ${line.name}",
                                    fontSize = 12.sp,
                                    color = PosColors.TextHigh,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = "${MoneyRules.formatFixed(line.unitPriceCentimes * line.quantity)} ${strings.currency}",
                                    fontSize = 11.sp,
                                    color = PosColors.TextMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (order.lines.size > 3) {
                            Text(
                                text = strings.text(
                                    "+ ${order.lines.size - 3} autre(s) article(s)...",
                                    "+ ${order.lines.size - 3} more item(s)...",
                                    "+ ${order.lines.size - 3} عناصر أخرى..."
                                ),
                                fontSize = 11.sp,
                                color = PosColors.TextLow,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = PosColors.Border)

            // ==========================================
            // THIRD / ACTION ROW (Secondary actions wrapping with FlowRow)
            // - Reprendre
            // - Annuler la vente
            // ==========================================
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reprendre (Resume order in cart directly without PIN)
                OutlinedButton(
                    onClick = onResume,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    border = BorderStroke(1.5.dp, PosColors.Primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary)
                ) {
                    Text(
                        text = "✏️ " + strings.resumeOrder,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Annuler la vente (Cancel order with reason prompt, no PIN)
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    border = BorderStroke(1.dp, PosColors.Danger),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger)
                ) {
                    Text(
                        text = "🗑️ " + strings.cancelOrder,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PosColors.Danger
                    )
                }
            }
        }
    }
}

fun formatOrderCreationDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
}

