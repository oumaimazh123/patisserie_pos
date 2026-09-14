package ma.elaroui.pos.desktop.presentation.management.products

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun CataloguePreviewScreen(
    products: List<Product>,
    strings: DesktopStrings,
    onBack: () -> Unit
) {
    val visibleProducts = products.filter { it.active }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader(strings.cataloguePreview, strings, onBack)

        if (visibleProducts.isEmpty()) {
            EmptyStateCard(title = strings.noResults, modifier = Modifier.weight(1f))
        } else {
            val gridState = rememberLazyGridState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .touchDragScroll(gridState)
                ) {
                    items(visibleProducts, key = { it.id }) { product ->
                        Card(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            Column(
                                modifier = Modifier.padding(14.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(
                                    "${MoneyRules.formatFixed(product.priceCentimes)} ${strings.currency}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    style = defaultScrollbarStyle()
                )
            }
        }
    }
}
