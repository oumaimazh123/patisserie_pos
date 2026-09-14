package ma.elaroui.pos.desktop.presentation.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.rules.MoneyRules

private data class DetailBadge(val text: String, val bg: Color, val border: Color, val color: Color)

@Composable
fun SaleDetailScreen(
    row: SalesHistoryRow,
    strings: DesktopStrings,
    onNavigateToReceiptPreview: (kind: TicketKind) -> Unit,
    onBack: () -> Unit
) {
    val order = row.order

    // User-friendly Order Status Badge
    val statusBadge = when (order.status) {
        OrderStatus.COMPLETED -> DetailBadge(strings.text("Clôturée", "Completed", "مكتملة"), PosColors.SuccessLight, PosColors.Success.copy(alpha = 0.3f), PosColors.Success)
        OrderStatus.OPEN -> DetailBadge(strings.text("Ouverte", "Open", "مفتوحة"), PosColors.AlertLight, PosColors.Alert.copy(alpha = 0.3f), PosColors.Alert)
        OrderStatus.CANCELLED -> DetailBadge(strings.text("Annulée", "Cancelled", "ملغاة"), PosColors.DangerLight, PosColors.Danger.copy(alpha = 0.3f), PosColors.Danger)
    }

    // User-friendly Order Type
    val typeText = when (order.type) {
        OrderType.DINE_IN -> if (order.tableId != null) strings.text("🍽️ Sur place (Table ${order.tableId})", "🍽️ Dine In (Table ${order.tableId})", "🍽️ محلي (طاولة ${order.tableId})")
                             else strings.text("🍽️ Sur place", "🍽️ Dine In", "🍽️ محلي")
        OrderType.TAKEAWAY -> strings.text("🛍️ À emporter", "🛍️ Takeaway", "🛍️ سفري")
        OrderType.COUNTER -> strings.text("☕ Comptoir", "☕ Counter", "☕ بالصندوق")
        OrderType.PREORDER -> strings.text("🎂 Précommande", "🎂 Pre-order", "🎂 طلب مسبق")
    }

    // User-friendly Payment Method
    val paymentText = when (row.paymentMethod) {
        PaymentMethod.CASH -> "💵 " + strings.cash
        PaymentMethod.CARD -> "💳 " + strings.card
        PaymentMethod.CARNET_CLIENT -> "📖 " + strings.text("Carnet client", "Customer Credit", "دفتر الزبون")
        PaymentMethod.MOBILE_QR -> "📱 " + strings.text("Mobile / QR", "Mobile / QR", "محمول / QR")
        null -> strings.text("— Non réglé", "— Unpaid", "— غير مسدد")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = "${strings.details} — ${order.number}",
            strings = strings,
            onBackToDashboard = onBack
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onNavigateToReceiptPreview(TicketKind.CUSTOMER) },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("🖨️ " + strings.customerReceipt, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

            }
        }

        val detailScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .touchDragScroll(detailScrollState)
                .verticalScroll(detailScrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 850.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
                border = BorderStroke(1.dp, PosColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Meta Information Header Section
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 560.dp

                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        order.number,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PosColors.TextHigh
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusBadge.bg,
                                            border = BorderStroke(1.dp, statusBadge.border)
                                        ) {
                                            Text(
                                                "● ${statusBadge.text}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusBadge.color,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PosColors.Workspace,
                                            border = BorderStroke(1.dp, PosColors.Border)
                                        ) {
                                            Text(
                                                paymentText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PosColors.TextHigh,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    "👤 ${strings.cashierRole} : ${row.cashierName} • $typeText",
                                    fontSize = 12.sp,
                                    color = PosColors.TextMedium
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        order.number,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PosColors.TextHigh
                                    )
                                    Text(
                                        "👤 ${strings.cashierRole} : ${row.cashierName} • $typeText",
                                        fontSize = 13.sp,
                                        color = PosColors.TextMedium
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusBadge.bg,
                                        border = BorderStroke(1.dp, statusBadge.border)
                                    ) {
                                        Text(
                                            "● ${statusBadge.text}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusBadge.color,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PosColors.Workspace,
                                        border = BorderStroke(1.dp, PosColors.Border)
                                    ) {
                                        Text(
                                            paymentText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PosColors.TextHigh,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Line Items List
                    Text(
                        strings.text("Articles commandés", "Ordered items", "المنتجات المطلوبة"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.BakeryBrown
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        order.lines.forEach { line ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PosColors.Workspace,
                                border = BorderStroke(1.dp, PosColors.Border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(line.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh)
                                        Text(
                                            "${line.quantity} × ${MoneyRules.formatFixed(line.unitPriceCentimes)} ${strings.currency}" +
                                                    if (line.taxRateBasisPoints > 0) " (TVA ${line.taxRateBasisPoints / 100}%)" else "",
                                            fontSize = 12.sp,
                                            color = PosColors.TextMedium
                                        )
                                    }

                                    Text(
                                        "${MoneyRules.formatFixed(line.unitPriceCentimes * line.quantity)} ${strings.currency}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PosColors.TextHigh
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PosColors.Border)

                    // Financial Summary Breakdown
                    val htCentimes = order.totalCentimes - order.taxCentimes
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(strings.text("Sous-total HT", "Subtotal excl. tax", "المجموع دون ضريبة"), fontSize = 13.sp, color = PosColors.TextMedium)
                            Text("${MoneyRules.formatFixed(htCentimes)} ${strings.currency}", fontSize = 13.sp, color = PosColors.TextHigh)
                        }

                        if (order.taxCentimes > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(strings.tax, fontSize = 13.sp, color = PosColors.TextMedium)
                                Text("${MoneyRules.formatFixed(order.taxCentimes)} ${strings.currency}", fontSize = 13.sp, color = PosColors.TextHigh)
                            }
                        }

                        if (order.discountCentimes > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(strings.discount, fontSize = 13.sp, color = PosColors.Danger)
                                Text("-${MoneyRules.formatFixed(order.discountCentimes)} ${strings.currency}", fontSize = 13.sp, color = PosColors.Danger, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.total + " TTC", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosColors.PrimaryDark)
                                Text(
                                    "${MoneyRules.formatFixed(order.totalCentimes)} ${strings.currency}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PosColors.PrimaryDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
