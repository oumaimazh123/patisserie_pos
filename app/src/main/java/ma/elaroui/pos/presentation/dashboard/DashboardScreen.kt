package ma.elaroui.pos.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToPos: () -> Unit,
    onNavigateToSales: () -> Unit,
    onNavigateToDailyReport: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTables: () -> Unit,
    onNavigateToCashiers: () -> Unit,
    onNavigateToRegisterHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLockPos: () -> Unit,
    onSwitchUser: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val stats = uiState.stats
    val adaptive = LocalAdaptiveDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tableau de Bord Propriétaire") },
                actions = {
                    Button(
                        onClick = onNavigateToPos,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        Text("Accéder au POS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                    IconButton(onClick = onSwitchUser) {
                        Text("👤", fontSize = 20.sp)
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(adaptive.screenContentPadding)
            ) {
                // Key Sales Summary Cards
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Ventes Aujourd'hui",
                        value = MonetaryUtils.formatDh(stats?.todayTotalSalesCentimes ?: 0L),
                        subtitle = "${stats?.completedOrderCount ?: 0} commande(s) réglée(s)",
                        containerColor = Color(0xFF1D3557),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Espèces (Cash)",
                        value = MonetaryUtils.formatDh(stats?.todayCashSalesCentimes ?: 0L),
                        subtitle = "Total cash encaissé",
                        containerColor = Color(0xFF2A9D8F),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Carte / TPE",
                        value = MonetaryUtils.formatDh(stats?.todayCardSalesCentimes ?: 0L),
                        subtitle = "Total carte bancaire",
                        containerColor = Color(0xFF457B9D),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Gestion & Navigation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                Spacer(modifier = Modifier.height(12.dp))

                // Owner Navigation Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = adaptive.generalCardMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    item { NavTile(title = "Rapport des Ventes", icon = "📊", onClick = onNavigateToDailyReport) }
                    item { NavTile(title = "Historique des Ventes", icon = "🧾", onClick = onNavigateToSales) }
                    item { NavTile(title = "Sessions de Caisse", icon = "🏦", onClick = onNavigateToRegisterHistory) }
                    item { NavTile(title = "Gestion Produits", icon = "📦", onClick = onNavigateToProducts) }
                    item { NavTile(title = "Gestion Catégories", icon = "📁", onClick = onNavigateToCategories) }
                    item { NavTile(title = "Comptes Caissiers", icon = "👥", onClick = onNavigateToCashiers) }
                    item { NavTile(title = "Paramètres & Sauvegarde", icon = "⚙️", onClick = onNavigateToSettings) }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 13.sp, color = contentColor.copy(alpha = 0.8f))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Text(subtitle, fontSize = 11.sp, color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun NavTile(title: String, icon: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
        }
    }
}
