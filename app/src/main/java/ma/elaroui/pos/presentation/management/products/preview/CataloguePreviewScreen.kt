package ma.elaroui.pos.presentation.management.products.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.presentation.management.products.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CataloguePreviewScreen(
    viewModel: ProductViewModel,
    onBack: () -> Unit
) {
    val catalogueState by viewModel.observeSellableCatalogueUseCase().collectAsState(initial = emptyList())
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    val currentCategoryWithProducts = if (selectedCategoryId == null) {
        catalogueState.firstOrNull()
    } else {
        catalogueState.find { it.category.id == selectedCategoryId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aperçu Catalogue POS (Caissier)", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .padding(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    "Aperçu temps réel des produits vendables visibles par les caissiers à la caisse.",
                    color = Color(0xFF1D3557),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp
                )
            }

            // Categories Carousel
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalogueState) { catGroup ->
                    FilterChip(
                        selected = (currentCategoryWithProducts?.category?.id == catGroup.category.id),
                        onClick = { selectedCategoryId = catGroup.category.id },
                        label = { Text("${catGroup.category.name} (${catGroup.products.size})") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentCategoryWithProducts == null || currentCategoryWithProducts.products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun produit vendable disponible dans cette catégorie.", fontSize = 16.sp, color = Color(0xFF64748B))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentCategoryWithProducts.products) { product ->
                        Card(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D3557))
                                Text(MonetaryUtils.formatDh(product.priceCentimes), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE63946))
                            }
                        }
                    }
                }
            }
        }
    }
}
