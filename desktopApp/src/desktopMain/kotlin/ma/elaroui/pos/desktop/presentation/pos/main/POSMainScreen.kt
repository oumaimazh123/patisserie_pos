@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.pos.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.ResponsiveSplitPane
import ma.elaroui.pos.desktop.presentation.components.SafeProductImage
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import androidx.compose.ui.graphics.graphicsLayer
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosDimens
import ma.elaroui.pos.desktop.presentation.components.PosUi
import ma.elaroui.pos.desktop.presentation.components.mouseWheelScroll
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosSnackbar
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.domain.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import ma.elaroui.pos.desktop.platform.BarcodeScannerController
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategoryPopularityRules
import ma.elaroui.pos.shared.rules.MoneyRules
import ma.elaroui.pos.shared.rules.ProductPopularityRules

internal fun buildCartItems(
    cart: Map<Long, Int>,
    productsById: Map<Long, Product>
): List<Pair<Product, Int>> = cart.mapNotNull { (productId, quantity) ->
    productsById[productId]?.let { product -> product to quantity }
}

@Composable
fun POSMainScreen(
    categories: List<Category>,
    popularCategoryIds: List<Long> = emptyList(),
    categoryScores: Map<Long, Long> = emptyMap(),
    productScores: Map<Long, Long> = emptyMap(),
    products: List<Product>,
    cart: Map<Long, Int>,
    discountBasisPoints: Int,
    itemDiscountsBasisPoints: Map<Long, Int> = emptyMap(),
    strings: DesktopStrings,
    autoOpenCategoryPicker: Boolean = true,
    onProductClicked: (Product) -> Unit,
    onQuantityChanged: (productId: Long, quantity: Int) -> Unit,
    onDiscountChanged: (basisPoints: Int) -> Unit,
    onItemDiscountChanged: (productId: Long, basisPoints: Int) -> Unit = { _, _ -> },
    onHoldOrder: () -> Unit,
    onProceedToPayment: () -> Unit,
    onClearCart: (() -> Unit)? = null,
    onBarcodeScanned: (barcode: String) -> Unit = {},
    message: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showCategoryPicker by remember { mutableStateOf(autoOpenCategoryPicker) }
    var pickerInitialParentId by remember { mutableStateOf<Long?>(null) }
    var showClearCartConfirmDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val scannerController = remember(onBarcodeScanned) {
        BarcodeScannerController(
            onBarcodeScanned = { barcode ->
                searchQuery = ""
                onBarcodeScanned(barcode)
            }
        )
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    // Typed uiMessage has top priority; fallback to info for any legacy unclassified string
    val effectiveUiMessage = uiMessage ?: message.takeIf { it.isNotBlank() }?.let { UiMessage.info(it) }

    val activeCategories = remember(categories) { categories.filter { it.active } }
    val activeCategoryIds = remember(activeCategories) { activeCategories.mapTo(mutableSetOf()) { it.id } }

    val selectedCategory = remember(activeCategories, selectedCategoryId) {
        activeCategories.firstOrNull { it.id == selectedCategoryId }
    }
    val breadcrumb: List<Category> = remember(selectedCategoryId, activeCategories) {
        val id = selectedCategoryId
        if (id != null) CategoryHierarchyRules.getBreadcrumbPath(id, activeCategories)
        else emptyList()
    }
    val childCategories: List<Category> = remember(selectedCategoryId, activeCategories) {
        val id = selectedCategoryId
        if (id != null) {
            activeCategories.filter { it.parentId == id }.sortedBy { it.displayOrder }
        } else emptyList()
    }
    val allowedCategoryIds = remember(selectedCategoryId, activeCategories) {
        val id = selectedCategoryId
        if (id != null) {
            setOf(id) + CategoryHierarchyRules.getAllDescendantIds(id, activeCategories)
        } else null
    }

    val filteredProducts = remember(products, activeCategoryIds, selectedCategoryId, allowedCategoryIds, searchQuery, productScores) {
        filterDesktopPosProducts(products, activeCategoryIds, selectedCategoryId, searchQuery, productScores, allowedCategoryIds)
    }

    val productsById = remember(products) { products.associateBy { it.id } }

    val cartItems by remember(cart, productsById) {
        derivedStateOf {
            buildCartItems(cart, productsById)
        }
    }
    val totalItemsCount = remember(cartItems) { cartItems.sumOf { it.second } }
    val grossSubtotal = remember(cartItems) { cartItems.sumOf { (prod, qty) -> prod.priceCentimes * qty } }
    val totalItemDiscounts = remember(cartItems, itemDiscountsBasisPoints) {
        cartItems.sumOf { (prod, qty) ->
            val bps = (itemDiscountsBasisPoints[prod.id] ?: 0).coerceIn(0, 10_000)
            val lineTotal = prod.priceCentimes * qty
            (lineTotal * bps + 5_000L) / 10_000L
        }
    }
    val subtotalAfterItemDiscounts = remember(grossSubtotal, totalItemDiscounts) { grossSubtotal - totalItemDiscounts }
    val globalDiscount = remember(subtotalAfterItemDiscounts, discountBasisPoints) {
        (subtotalAfterItemDiscounts * discountBasisPoints + 5_000L) / 10_000L
    }
    val totalDiscount = remember(totalItemDiscounts, globalDiscount) { totalItemDiscounts + globalDiscount }
    val total = remember(grossSubtotal, totalDiscount) { grossSubtotal - totalDiscount }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                scannerController.onKeyEvent(event)
            }
    ) {
        ResponsiveSplitPane(
            modifier = Modifier.fillMaxSize().background(PosUi.Canvas),
            horizontalBreakpoint = 700.dp,
            primaryFraction = 0.70f
        ) {
        // Left Column (70%): Product Catalog & Categories
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(PosUi.CompactPadding)
        ) {
            val selectedCategoryName = selectedCategory?.name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TouchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = strings.text("Rechercher un produit (nom, code-barres)…", "Search product (name, barcode)…", "البحث عن منتج…"),
                    leadingIcon = { Text("🔍", fontSize = 16.sp) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕", color = PosColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PosColors.Primary,
                        unfocusedBorderColor = PosColors.Border
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = PosDimens.SearchHeight)
                )
                Button(
                    onClick = {
                        pickerInitialParentId = null
                        showCategoryPicker = true
                    },
                    modifier = Modifier
                        .heightIn(min = PosDimens.SearchHeight)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosColors.BakeryBrown,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        selectedCategoryName?.let {
                            strings.text("📂 $it", "📂 $it", "📂 $it")
                        } ?: strings.text("📂 Catégories", "📂 Categories", "📂 الفئات"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Interactive Tactile Breadcrumb Bar (when a category is selected)
            if (selectedCategory != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // Back button
                            FilledTonalButton(
                                onClick = {
                                    selectedCategoryId = selectedCategory.parentId
                                    onClearMessage()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    "← " + strings.text("Retour", "Back", "رجوع"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // "Tous" link
                            Text(
                                text = strings.text("Tous", "All", "الكل"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = PosColors.Primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedCategoryId = null
                                        onClearMessage()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            )

                            for ((index, cat) in breadcrumb.withIndex()) {
                                Text("›", fontSize = 12.sp, color = PosColors.TextMuted)
                                val isCurrent = (index == breadcrumb.lastIndex)
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) PosColors.TextHigh else PosColors.Primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(enabled = !isCurrent) {
                                            selectedCategoryId = cat.id
                                            onClearMessage()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .pointerHoverIcon(if (!isCurrent) PointerIcon.Hand else PointerIcon.Default)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedCategoryId = null
                                onClearMessage()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("✕", fontSize = 13.sp, color = PosColors.TextMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }


            if (showCategoryPicker) {
                DesktopCategoryPickerDialog(
                    categories = activeCategories,
                    initialParentId = pickerInitialParentId,
                    selectedCategoryId = selectedCategoryId,
                    categoryScores = categoryScores,
                    popularCategoryIds = popularCategoryIds,
                    strings = strings,
                    onSelect = { categoryId ->
                        onClearMessage()
                        selectedCategoryId = categoryId
                        showCategoryPicker = false
                    },
                    onDismiss = { showCategoryPicker = false }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Product Cards Grid with Touch Scrolling & Vertical Scrollbar
            val productGridState = rememberLazyGridState()
            LaunchedEffect(selectedCategoryId, searchQuery) {
                productGridState.scrollToItem(0)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyVerticalGrid(
                    state = productGridState,
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(PosUi.GridGap),
                    verticalArrangement = Arrangement.spacedBy(PosUi.GridGap),
                    contentPadding = PaddingValues(end = 14.dp, bottom = 28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .touchDragScroll(productGridState)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        POSProductCard(
                            product = product,
                            strings = strings,
                            onClick = { onProductClicked(product) }
                        )
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(productGridState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    style = defaultScrollbarStyle()
                )
            }
        }

        // Right Column (30%): Order Cart / Live Ticket
        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = PosColors.Workspace,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, PosColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Cart Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.cartTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.TextHigh
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalItemsCount > 0) {
                            Surface(
                                color = PosColors.PrimaryLight,
                                shape = RoundedCornerShape(PosDimens.RadiusPill)
                            ) {
                                Text(
                                    text = "$totalItemsCount ${strings.text("article(s)", "item(s)", "عنصر")}",
                                    color = PosColors.PrimaryDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { showClearCartConfirmDialog = true },
                                modifier = Modifier
                                    .size(30.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    text = "🗑️",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Confirmation dialog for clearing the cart
                if (showClearCartConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearCartConfirmDialog = false },
                        title = {
                            Text(
                                text = strings.text("Vider le panier ?", "Clear cart?", "إفراغ السلة؟"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh
                            )
                        },
                        text = {
                            Text(
                                text = strings.text(
                                    "Êtes-vous sûr de vouloir supprimer tous les articles du panier en cours ?",
                                    "Are you sure you want to remove all items from the current cart?",
                                    "هل أنت متأكد من رغبتك في إزالة جميع العناصر من السلة الحالية؟"
                                ),
                                fontSize = 13.sp,
                                color = PosColors.TextMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (onClearCart != null) {
                                        onClearCart()
                                    } else {
                                        cartItems.forEach { (prod, _) -> onQuantityChanged(prod.id, 0) }
                                    }
                                    showClearCartConfirmDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger)
                            ) {
                                Text(
                                    strings.text("Vider", "Clear", "إفراغ"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearCartConfirmDialog = false }) {
                                Text(strings.cancel)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Cart Line Items List (Scrollable with Scrollbar & Smooth Mouse Wheel)
                val cartListState = rememberLazyListState()

                // Auto-scroll only when a new product reference is added to the cart
                var previousProductIds by remember { mutableStateOf(cart.keys.toSet()) }
                LaunchedEffect(cart.keys.toSet()) {
                    val currentKeys = cart.keys.toSet()
                    val newKeys = currentKeys - previousProductIds
                    if (newKeys.isNotEmpty() && cartItems.isNotEmpty()) {
                        val targetIndex = cartItems.indexOfFirst { (p, _) -> p.id in newKeys }
                        if (targetIndex >= 0) {
                            cartListState.animateScrollToItem(targetIndex)
                        } else {
                            cartListState.animateScrollToItem(cartItems.lastIndex)
                        }
                    }
                    previousProductIds = currentKeys
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = cartListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp)
                            .mouseWheelScroll(cartListState, multiplier = 0.75f)
                            .touchDragScroll(cartListState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (cartItems.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🛒", fontSize = 22.sp)
                                        }
                                        Text(
                                            strings.emptyCart,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = PosColors.TextHigh
                                        )
                                        Text(
                                            strings.text(
                                                "Cliquez sur un produit pour l'ajouter",
                                                "Click a product to add it",
                                                "انقر على منتج لإضافته"
                                            ),
                                            fontSize = 11.sp,
                                            color = PosColors.TextMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(cartItems, key = { it.first.id }) { (prod, qty) ->
                                POSCartLineItem(
                                    name = prod.name,
                                    imagePath = prod.imagePath,
                                    quantity = qty,
                                    unitPriceCentimes = prod.priceCentimes,
                                    discountBasisPoints = (itemDiscountsBasisPoints[prod.id] ?: 0).coerceIn(0, 10_000),
                                    strings = strings,
                                    onIncrease = { onQuantityChanged(prod.id, qty + 1) },
                                    onDecrease = { onQuantityChanged(prod.id, qty - 1) },
                                    onRemove = { onQuantityChanged(prod.id, 0) },
                                    onDiscountChanged = { bps -> onItemDiscountChanged(prod.id, bps) }
                                )
                            }
                        }
                    }

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(cartListState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        style = defaultScrollbarStyle()
                    )
                }

                // Total Summary Card (Fixed)
                Surface(
                    shape = RoundedCornerShape(PosDimens.RadiusCard),
                    color = Color.White,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                strings.text("Remise globale", "Global discount", "الخصم الإجمالي"),
                                fontSize = 11.sp,
                                color = PosColors.TextMedium
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf(0, 500, 1000, 2000)) { basisPoints ->
                                    FilterChip(
                                        selected = discountBasisPoints == basisPoints,
                                        onClick = { onDiscountChanged(basisPoints) },
                                        label = { Text("${basisPoints / 100}%", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PosColors.Primary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                        if (totalDiscount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(strings.text("Sous-total brut", "Gross subtotal", "المجموع الإجمالي"), fontSize = 11.sp, color = PosColors.TextMedium)
                                Text("${MoneyRules.formatFixed(grossSubtotal)} ${strings.currency}", fontSize = 12.sp, color = PosColors.TextHigh)
                            }
                            if (totalItemDiscounts > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(strings.text("Remises articles", "Item discounts", "خصومات المنتجات"), fontSize = 11.sp, color = PosColors.TextMedium)
                                    Text("-${MoneyRules.formatFixed(totalItemDiscounts)} ${strings.currency}", fontSize = 12.sp, color = PosColors.Danger, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (globalDiscount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${strings.text("Remise globale", "Global discount", "خصم إجمالي")} (${discountBasisPoints / 100}%)", fontSize = 11.sp, color = PosColors.TextMedium)
                                    Text("-${MoneyRules.formatFixed(globalDiscount)} ${strings.currency}", fontSize = 12.sp, color = PosColors.Danger, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        HorizontalDivider(color = PosColors.Border)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    strings.total,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.TextHigh
                                )
                                Text(
                                    "TTC",
                                    fontSize = 10.sp,
                                    color = PosColors.TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "${MoneyRules.formatFixed(total)} ${strings.currency}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PosColors.Primary
                            )
                        }
                    }
                }

                // Primary CTA Actions: 56-64dp touch target, ergonomic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onHoldOrder,
                        enabled = cartItems.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(PosDimens.TouchStandard)
                            .pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, if (cartItems.isNotEmpty()) PosColors.BakeryBrown else PosColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PosColors.BakeryBrown
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            strings.holdOrder,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onProceedToPayment,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(PosDimens.CashOutButtonHeight)
                            .pointerHoverIcon(PointerIcon.Hand),
                        enabled = cartItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosColors.Primary,
                            disabledContainerColor = PosColors.Primary.copy(alpha = 0.38f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            strings.proceedToPayment,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                }
            }
        }
        // Floating snackbar overlay for temporary POS messages
        if (effectiveUiMessage != null) {
            PosSnackbar(
                message = effectiveUiMessage,
                onDismiss = onClearMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }

}

internal fun filterDesktopPosProducts(
    products: List<Product>,
    activeCategoryIds: Set<Long>,
    selectedCategoryId: Long?,
    searchQuery: String,
    productScores: Map<Long, Long> = emptyMap(),
    allowedCategoryIds: Set<Long>? = null
): List<Product> {
    val normalizedQuery = searchQuery.trim()
    val filtered = products.filter { product ->
        val matchesCategory = if (allowedCategoryIds != null) {
            product.categoryId != null && product.categoryId in allowedCategoryIds
        } else {
            selectedCategoryId == null || product.categoryId == selectedCategoryId
        }
        val isCategoryValid = product.categoryId == null || product.categoryId in activeCategoryIds || selectedCategoryId == null
        product.active && product.available && isCategoryValid &&
            (normalizedQuery.isNotEmpty() || matchesCategory) &&
            (
                normalizedQuery.isEmpty() ||
                    product.name.contains(normalizedQuery, ignoreCase = true) ||
                    product.sku?.contains(normalizedQuery, ignoreCase = true) == true ||
                    product.barcode?.contains(normalizedQuery, ignoreCase = true) == true
            )
    }
    return ProductPopularityRules.sortProducts(filtered, productScores)
}

@Composable
private fun DesktopCategoryPickerDialog(
    categories: List<Category>,
    initialParentId: Long?,
    selectedCategoryId: Long?,
    categoryScores: Map<Long, Long> = emptyMap(),
    popularCategoryIds: List<Long> = emptyList(),
    strings: DesktopStrings,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var currentParentId by remember(initialParentId) { mutableStateOf(initialParentId) }
    val currentParent = remember(currentParentId, categories) {
        categories.firstOrNull { it.id == currentParentId }
    }
    val breadcrumb = remember(currentParentId, categories) {
        if (currentParentId != null) CategoryHierarchyRules.getBreadcrumbPath(currentParentId!!, categories)
        else emptyList()
    }

    val currentLevelCategories = remember(currentParentId, categories, categoryScores, popularCategoryIds) {
        val filtered = if (currentParentId == null) {
            categories.filter { it.isRoot && it.active }
        } else {
            categories.filter { it.parentId == currentParentId && it.active }
        }
        val effectiveScores = if (categoryScores.isNotEmpty()) {
            categoryScores
        } else {
            popularCategoryIds.mapIndexed { index, id -> id to (popularCategoryIds.size - index).toLong() }.toMap()
        }
        CategoryPopularityRules.sortCategories(filtered, effectiveScores)
    }
    val dialogScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 600.dp, max = 860.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentParentId != null) {
                            IconButton(
                                onClick = {
                                    currentParentId = currentParent?.parentId
                                },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                            }
                        }
                        Text(
                            text = currentParent?.name ?: strings.text("Choisir une catégorie", "Choose a category", "اختيار فئة"),
                            modifier = Modifier.weight(1f, fill = false),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) { Text("✕", fontSize = 18.sp, color = PosColors.TextMuted) }
                }

                if (breadcrumb.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.text("Toutes les catégories", "All categories", "كل الفئات"),
                            fontSize = 12.sp,
                            color = PosColors.Primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { currentParentId = null }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        )
                        for ((index, step) in breadcrumb.withIndex()) {
                            Text("›", fontSize = 12.sp, color = PosColors.TextMuted)
                            val isLast = (index == breadcrumb.lastIndex)
                            Text(
                                text = step.name,
                                fontSize = 12.sp,
                                color = if (isLast) PosColors.TextHigh else PosColors.Primary,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(enabled = !isLast) { currentParentId = step.id }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .pointerHoverIcon(if (!isLast) PointerIcon.Hand else PointerIcon.Default)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(dialogScrollState)
                    .touchDragScroll(dialogScrollState)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentParentId == null) {
                        DesktopCategoryPickerButton(
                            label = strings.text("Tous les produits", "All products", "كل المنتجات"),
                            isAllProducts = true,
                            selected = selectedCategoryId == null,
                            strings = strings
                        ) { onSelect(null) }
                    } else {
                        DesktopCategoryPickerButton(
                            label = strings.text(
                                "Tous les produits (${currentParent?.name})",
                                "All products in (${currentParent?.name})",
                                "كل المنتجات في (${currentParent?.name})"
                            ),
                            imagePath = currentParent?.imagePath,
                            selected = selectedCategoryId == currentParentId,
                            strings = strings
                        ) { onSelect(currentParentId) }
                    }

                    currentLevelCategories.forEach { category ->
                        val hasChildren = categories.any { it.parentId == category.id && it.active }
                        DesktopCategoryPickerButton(
                            label = category.name,
                            imagePath = category.imagePath,
                            selected = category.id == selectedCategoryId,
                            hasChildren = hasChildren,
                            strings = strings
                        ) {
                            if (hasChildren) {
                                currentParentId = category.id
                            } else {
                                onSelect(category.id)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(12.dp)
            ) { Text(strings.text("Fermer", "Close", "إغلاق")) }
        }
    )
}

/**
 * Lucide LayoutGrid icon: 2x2 grid of 4 rounded squares.
 * Conforms to Lucide layout-grid vector specification (viewBox 0 0 24 24, stroke 2, rx 1).
 */
@Composable
internal fun LucideLayoutGridIcon(
    modifier: Modifier = Modifier,
    color: Color = PosColors.Primary,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val scale = w / 24f
        val strokePx = (strokeWidth.toPx() * (w / 24.dp.toPx())).coerceAtLeast(1.5f)
        val cornerRadius = CornerRadius(1f * scale, 1f * scale)
        val rectSize = Size(7f * scale, 7f * scale)
        val strokeStyle = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // 1. Top-left
        drawRoundRect(
            color = color,
            topLeft = Offset(3f * scale, 3f * scale),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = strokeStyle
        )
        // 2. Top-right
        drawRoundRect(
            color = color,
            topLeft = Offset(14f * scale, 3f * scale),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = strokeStyle
        )
        // 3. Bottom-left
        drawRoundRect(
            color = color,
            topLeft = Offset(3f * scale, 14f * scale),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = strokeStyle
        )
        // 4. Bottom-right
        drawRoundRect(
            color = color,
            topLeft = Offset(14f * scale, 14f * scale),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = strokeStyle
        )
    }
}

@Composable
internal fun DesktopCategoryPickerButton(
    label: String,
    imagePath: String? = null,
    isAllProducts: Boolean = false,
    selected: Boolean,
    hasChildren: Boolean = false,
    strings: DesktopStrings,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedBorder by animateColorAsState(
        when {
            selected -> PosColors.Primary
            isPressed -> PosColors.PrimaryDark
            isHovered -> PosColors.Primary
            else -> PosColors.Border
        }
    )

    val animatedElevation = when {
        selected -> 3.dp
        isHovered -> 3.dp
        isPressed -> 1.dp
        else -> 1.dp
    }

    Card(
        modifier = Modifier
            .width(155.dp)
            .height(175.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .graphicsLayer {
                val scale = if (isPressed) 0.98f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) PosColors.PrimaryLight.copy(alpha = 0.45f) else Color.White
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAllProducts) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(topStart = PosDimens.RadiusCard, topEnd = PosDimens.RadiusCard))
                        .background(PosColors.Workspace),
                    contentAlignment = Alignment.Center
                ) {
                    LucideLayoutGridIcon(
                        modifier = Modifier.size(38.dp),
                        color = PosColors.Primary
                    )
                }
            } else {
                SafeProductImage(
                    imagePath = imagePath,
                    contentDescription = label,
                    placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(topStart = PosDimens.RadiusCard, topEnd = PosDimens.RadiusCard))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) PosColors.PrimaryDark else PosColors.TextHigh,
                    maxLines = 2,
                    minLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasChildren) {
                        Text(
                            text = strings.text("Sous-catégories", "Sub-categories", "فئات فرعية"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.Primary
                        )
                        Text(
                            text = "›",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosColors.Primary
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                        if (selected) {
                            Text(
                                text = "✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun POSProductCard(
    product: Product,
    strings: DesktopStrings,
    onClick: () -> Unit
) {
    val isAvailable = product.available
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

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
            .height(210.dp)
            .pointerHoverIcon(if (isAvailable) PointerIcon.Hand else PointerIcon.Default)
            .graphicsLayer {
                val scale = if (isPressed) 0.98f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isAvailable,
                onClick = onClick
            ),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = if (isPressed) PosColors.Workspace else Color.White),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SafeProductImage(
                imagePath = product.imagePath,
                contentDescription = product.name,
                placeholderText = strings.text("Aucune image", "No image", "لا توجد صورة"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = PosDimens.RadiusCard, topEnd = PosDimens.RadiusCard))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.TextHigh,
                    maxLines = 2,
                    minLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${MoneyRules.formatFixed(product.priceCentimes)} ${strings.currency}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.Primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isHovered) PosColors.Primary else PosColors.Workspace,
                        border = BorderStroke(1.dp, if (isHovered) PosColors.Primary else PosColors.Border),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "＋",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHovered) Color.White else PosColors.TextHigh
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun POSCartLineItem(
    name: String,
    imagePath: String?,
    quantity: Int,
    unitPriceCentimes: Long,
    discountBasisPoints: Int,
    strings: DesktopStrings,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onDiscountChanged: (basisPoints: Int) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val lineGross = unitPriceCentimes * quantity
    val lineDiscount = if (discountBasisPoints > 0) {
        (lineGross * discountBasisPoints + 5_000L) / 10_000L
    } else 0L
    val lineNet = lineGross - lineDiscount

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Product Image Thumbnail (Real image from product, or standard photo placeholder)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PosColors.Border),
                    color = PosColors.Workspace,
                    modifier = Modifier.size(48.dp)
                ) {
                    SafeProductImage(
                        imagePath = imagePath,
                        contentDescription = name,
                        placeholderText = "",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                // Product Name & Price
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PosColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (discountBasisPoints > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${MoneyRules.formatFixed(lineGross)} ${strings.currency}",
                                textDecoration = TextDecoration.LineThrough,
                                fontSize = 11.sp,
                                color = PosColors.TextMuted
                            )
                            Text(
                                text = "${MoneyRules.formatFixed(lineNet)} ${strings.currency}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.Success
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PosColors.SuccessLight
                            ) {
                                Text(
                                    text = "-${discountBasisPoints / 100}%",
                                    color = PosColors.Success,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${MoneyRules.formatFixed(lineGross)} ${strings.currency}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextMedium
                        )
                    }
                }

                // Quantity controls & Remove
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onDecrease,
                            modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("−", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PosColors.TextHigh)
                        }
                    }

                    Text(
                        text = "$quantity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PosColors.TextHigh,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.Workspace,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onIncrease,
                            modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("＋", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PosColors.TextHigh)
                        }
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("✕", color = PosColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Item-level discount selector row
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.text("Remise article", "Item discount", "خصم المنتج"),
                    fontSize = 10.sp,
                    color = PosColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = listOf(0, 500, 1000, 2000)
                    presets.forEach { bps ->
                        val selected = discountBasisPoints == bps
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) PosColors.Primary else PosColors.Workspace,
                            border = BorderStroke(1.dp, if (selected) PosColors.Primary else PosColors.Border),
                            modifier = Modifier
                                .clickable { onDiscountChanged(bps) }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                text = "${bps / 100}%",
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else PosColors.TextHigh,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (discountBasisPoints !in presets) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PosColors.Primary,
                            border = BorderStroke(1.dp, PosColors.Primary),
                            modifier = Modifier
                                .clickable { showCustomDialog = true }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                text = "${discountBasisPoints / 100}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PosColors.Workspace,
                            border = BorderStroke(1.dp, PosColors.Border),
                            modifier = Modifier
                                .clickable { showCustomDialog = true }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                text = strings.text("+ %", "+ %", "+ %"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = PosColors.TextMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        var customPercentText by remember {
            mutableStateOf(if (discountBasisPoints > 0) (discountBasisPoints / 100).toString() else "")
        }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = {
                Text(
                    text = strings.text("Remise sur $name", "Discount on $name", "خصم على $name"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        strings.text("Pourcentage de remise (0 à 100%) :", "Discount percentage (0 to 100%):", "نسبة الخصم (0 إلى 100٪):"),
                        fontSize = 13.sp,
                        color = PosColors.TextMedium
                    )
                    OutlinedTextField(
                        value = customPercentText,
                        onValueChange = { customPercentText = it.filter { ch -> ch.isDigit() }.take(3) },
                        suffix = { Text("%", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pct = customPercentText.toIntOrNull() ?: 0
                        onDiscountChanged((pct * 100).coerceIn(0, 10_000))
                        showCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary)
                ) {
                    Text(strings.text("Appliquer", "Apply", "تطبيق"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
