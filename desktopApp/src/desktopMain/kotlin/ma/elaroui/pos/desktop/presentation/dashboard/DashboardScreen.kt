@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.SalesSummary
import ma.elaroui.pos.desktop.presentation.components.AppHeaderHeight
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosDimens
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun DashboardScreen(
    summary: SalesSummary,
    strings: DesktopStrings,
    onNavigateToPos: () -> Unit,
    onNavigateToSales: () -> Unit,
    onNavigateToDailyReport: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTables: () -> Unit,
    onNavigateToCashiers: () -> Unit,
    onNavigateToRegisterHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEstablishment: () -> Unit = onNavigateToSettings,
    onLockPos: () -> Unit,
    showWindowModeButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            BoxWithConstraints(Modifier.fillMaxWidth().height(AppHeaderHeight)) {
                val compact = maxWidth < 720.dp
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.dashboardTitle,
                            modifier = Modifier.weight(1f),
                            color = PosColors.TextHigh,
                            fontSize = if (compact) 18.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(if (compact) 8.dp else 16.dp))

                        // Primary Action: Open POS
                        Button(
                            onClick = onNavigateToPos,
                            modifier = Modifier
                                .heightIn(min = 46.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PosColors.Primary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (compact) "🛒 POS" else "🛒 " + strings.text("Accéder au POS", "Open POS", "فتح نقطة البيع"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        // Secondary Action: Settings
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("⚙️", fontSize = 18.sp)
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Secondary Action: Profile / Lock
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = onLockPos,
                                modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("👤", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PosColors.Canvas)
        ) {
            val compact = maxWidth < 760.dp
            val contentPadding = if (compact) 16.dp else 24.dp
            val contentSpacing = if (compact) 12.dp else 16.dp
            val showMetricsInRow = maxWidth >= 760.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                // Key Sales Summary Cards Row
                val metrics = listOf(
                    DashboardMetric(
                        title = strings.text("Ventes Aujourd’hui", "Sales Today", "مبيعات اليوم"),
                        value = "${MoneyRules.formatFixed(summary.salesCentimes)} ${strings.currency}",
                        subtitle = strings.text(
                            "${summary.completedOrders} commande(s) réglée(s)",
                            "${summary.completedOrders} paid order(s)",
                            "${summary.completedOrders} طلب مدفوع"
                        ),
                        icon = "📈",
                        color = PosColors.Primary
                    ),
                    DashboardMetric(
                        title = strings.text("Espèces (Cash)", "Cash", "النقد"),
                        value = "${MoneyRules.formatFixed(summary.cashCentimes)} ${strings.currency}",
                        subtitle = strings.cashSales,
                        icon = "💵",
                        color = PosColors.SecondaryDark
                    ),
                    DashboardMetric(
                        title = strings.text("Carte / TPE", "Card / Terminal", "البطاقة / جهاز الدفع"),
                        value = "${MoneyRules.formatFixed(summary.cardCentimes)} ${strings.currency}",
                        subtitle = strings.cardSales,
                        icon = "💳",
                        color = PosColors.BakeryBrown
                    )
                )

                if (showMetricsInRow) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        metrics.forEach { metric ->
                            MetricCard(
                                metric = metric,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
                        metrics.forEach { metric ->
                            MetricCard(
                                metric = metric,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 18.dp else 24.dp))

                Text(
                    strings.text("Gestion & Navigation", "Management & Navigation", "الإدارة والتنقل"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.TextHigh
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Owner Navigation Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        NavTile(
                            title = strings.text("Rapport des Ventes", "Sales Report", "تقرير المبيعات"),
                            icon = "📊",
                            onClick = onNavigateToDailyReport
                        )
                    }
                    item {
                        NavTile(
                            title = strings.salesHistory,
                            icon = "🧾",
                            onClick = onNavigateToSales
                        )
                    }
                    item {
                        NavTile(
                            title = strings.text("Sessions de Caisse", "Register Sessions", "جلسات الصندوق"),
                            icon = "🏦",
                            onClick = onNavigateToRegisterHistory
                        )
                    }
                    item {
                        NavTile(
                            title = strings.text("Gestion des Produits", "Product Management", "إدارة المنتجات"),
                            icon = "📦",
                            onClick = onNavigateToProducts
                        )
                    }
                    item {
                        NavTile(
                            title = strings.text("Gestion des Catégories", "Category Management", "إدارة الفئات"),
                            icon = "📁",
                            onClick = onNavigateToCategories
                        )
                    }
                    item {
                        NavTile(
                            title = strings.text("Comptes Caissiers", "Cashier Accounts", "حسابات أمناء الصندوق"),
                            icon = "👥",
                            onClick = onNavigateToCashiers
                        )
                    }
                    item {
                        NavTile(
                            title = strings.text("Paramètres & Sauvegarde", "Settings & Backup", "الإعدادات والنسخ الاحتياطي"),
                            icon = "⚙️",
                            onClick = onNavigateToSettings
                        )
                    }
                }
            }
        }
    }
}

private data class DashboardMetric(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: String,
    val color: Color
)

@Composable
private fun MetricCard(
    metric: DashboardMetric,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = metric.color),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(metric.icon, fontSize = 18.sp)
            }
            Text(
                metric.value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                metric.subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NavTile(
    title: String,
    icon: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedBg by animateColorAsState(
        when {
            isPressed -> PosColors.Workspace
            isHovered -> PosColors.Workspace
            else -> Color.White
        }
    )

    val animatedBorder by animateColorAsState(
        when {
            isPressed -> PosColors.PrimaryDark
            isHovered -> PosColors.Primary
            else -> PosColors.Border
        }
    )

    val animatedElevation = when {
        isPressed -> 1.dp
        isHovered -> 4.dp
        else -> 1.dp
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = animatedBg),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isHovered) PosColors.PrimaryLight else PosColors.Workspace),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PosColors.TextHigh,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
