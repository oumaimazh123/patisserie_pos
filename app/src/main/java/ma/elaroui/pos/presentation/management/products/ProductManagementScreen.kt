package ma.elaroui.pos.presentation.management.products

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.elaroui.pos.R
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.Product
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF1D3557)
private val Red = Color(0xFFE63946)
private val SecondaryText = Color(0xFF64748B)
private val PageBackground = Color(0xFFF8F9FA)
private val ProductImageShape = RoundedCornerShape(
    topStart = 12.dp,
    topEnd = 12.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(
    viewModel: ProductViewModel,
    onNavigateToPreview: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage, uiState.successMessage, uiState.isAddEditDialogOpen) {
        val message = uiState.errorMessage.takeIf { !uiState.isAddEditDialogOpen }
            ?: uiState.successMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val filteredProducts = uiState.products.filter { product ->
        (product.name.contains(uiState.searchQuery, ignoreCase = true) ||
            product.sku?.contains(uiState.searchQuery, ignoreCase = true) == true ||
            product.barcode?.contains(uiState.searchQuery, ignoreCase = true) == true) &&
            (uiState.selectedCategoryId == null || product.categoryId == uiState.selectedCategoryId)
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::selectProductImage) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_catalogue), color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            "← ${stringResource(R.string.return_action)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToPreview) {
                        Text(
                            stringResource(R.string.catalogue_preview),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddDialog,
                containerColor = Red
            ) {
                Text("+", fontSize = 28.sp, color = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PageBackground)
                .padding(adaptive.screenContentPadding)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_product_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.selectCategoryFilter(null) },
                        label = { Text(stringResource(R.string.all_categories)) }
                    )
                }
                items(uiState.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategoryFilter(category.id) },
                        label = {
                            Text(
                                category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (filteredProducts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_products_found),
                        fontSize = 18.sp,
                        color = SecondaryText
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = adaptive.productCardMinWidth),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val categoryName = uiState.categories
                            .firstOrNull { it.id == product.categoryId }
                            ?.name
                            ?: "—"
                        ProductCard(
                            product = product,
                            categoryName = categoryName,
                            onDetails = { viewModel.openProductDetails(product) },
                            onEdit = { viewModel.openEditDialog(product) },
                            onToggleAvailability = {
                                viewModel.toggleProductAvailability(product)
                            }
                        )
                    }
                }
            }
        }
    }

    uiState.detailProduct?.let { product ->
        val categoryName = uiState.categories.firstOrNull { it.id == product.categoryId }?.name ?: "—"
        ProductDetailsDialog(
            product = product,
            categoryName = categoryName,
            onDismiss = viewModel::closeProductDetails,
            onEdit = viewModel::editDetailedProduct,
            onToggleActive = {
                viewModel.closeProductDetails()
                viewModel.toggleProductActiveState(product)
            }
        )
    }

    if (uiState.isAddEditDialogOpen) {
        ProductFormDialog(
            uiState = uiState,
            onDismiss = viewModel::closeDialog,
            onSave = viewModel::submitSaveProduct,
            onNameChange = {
                viewModel.updateForm(
                    it,
                    uiState.productCategoryIdInput,
                    uiState.productPriceInput,
                    uiState.productAvailableInput
                )
            },
            onCategoryChange = {
                viewModel.updateForm(
                    uiState.productNameInput,
                    it,
                    uiState.productPriceInput,
                    uiState.productAvailableInput
                )
            },
            onPriceChange = {
                viewModel.updateForm(
                    uiState.productNameInput,
                    uiState.productCategoryIdInput,
                    it,
                    uiState.productAvailableInput
                )
            },
            onSkuChange = { viewModel.updateIdentifiers(it, uiState.productBarcodeInput) },
            onBarcodeChange = { viewModel.updateIdentifiers(uiState.productSkuInput, it) },
            onAvailabilityChange = {
                viewModel.updateForm(
                    uiState.productNameInput,
                    uiState.productCategoryIdInput,
                    uiState.productPriceInput,
                    it
                )
            },
            onSelectImage = { imagePickerLauncher.launch("image/*") },
            onRemoveImage = viewModel::removeProductImage
        )
    }
}

@Composable
internal fun ProductCard(
    product: Product,
    categoryName: String,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onToggleAvailability: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(370.dp)
            .testTag("product_card_${product.id}")
            .clickable(onClick = onDetails),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            SafeProductImage(
                imagePath = product.imagePath,
                contentDescription = "${stringResource(R.string.product_image)}: ${product.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(ProductImageShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Text(
                    product.name,
                    color = Navy,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    categoryName,
                    color = SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    productPrice(product.priceCentimes),
                    color = Red,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.product_available),
                        color = if (product.available) Color(0xFF1E8E3E) else SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = product.available,
                        onCheckedChange = { onToggleAvailability() },
                        enabled = product.active
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(stringResource(R.string.edit_action), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProductDetailsDialog(
    product: Product,
    categoryName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SafeProductImage(
                    imagePath = product.imagePath,
                    contentDescription = "${stringResource(R.string.product_image)}: ${product.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                DetailRow(stringResource(R.string.category_label), categoryName)
                DetailRow(stringResource(R.string.price_label), productPrice(product.priceCentimes))
                DetailRow(
                    stringResource(R.string.product_available),
                    if (product.available) stringResource(R.string.available)
                    else stringResource(R.string.product_unavailable)
                )
                DetailRow(
                    stringResource(R.string.status_label),
                    if (product.active) stringResource(R.string.active_status)
                    else stringResource(R.string.inactive_status)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) {
                Text(stringResource(R.string.edit_action))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onToggleActive) {
                    Text(
                        if (product.active) stringResource(R.string.deactivate_action)
                        else stringResource(R.string.reactivate_action)
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close_action))
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SecondaryText, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Navy,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun ProductFormDialog(
    uiState: ProductManagementUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onPriceChange: (String) -> Unit,
    onSkuChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onAvailabilityChange: (Boolean) -> Unit,
    onSelectImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (uiState.editingProduct == null) stringResource(R.string.add_product_title)
                else stringResource(R.string.edit_product_title)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.errorMessage?.let { error ->
                    item { Text(error, color = Red) }
                }
                item {
                    Text(
                        stringResource(R.string.optional_image),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    SafeProductImage(
                        imagePath = uiState.productImagePathInput,
                        contentDescription = stringResource(R.string.product_image),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSelectImage,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (uiState.productImagePathInput.isNullOrBlank()) {
                                    stringResource(R.string.select_image)
                                } else {
                                    stringResource(R.string.replace_image)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!uiState.productImagePathInput.isNullOrBlank()) {
                            TextButton(onClick = onRemoveImage) {
                                Text(stringResource(R.string.remove_image), color = Red)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = uiState.productNameInput,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.product_name_required)) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        stringResource(R.string.category_required),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.categories.filter { it.active }, key = { it.id }) { category ->
                            FilterChip(
                                selected = uiState.productCategoryIdInput == category.id,
                                onClick = { onCategoryChange(category.id) },
                                label = {
                                    Text(
                                        category.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = uiState.productPriceInput,
                        onValueChange = onPriceChange,
                        label = { Text(stringResource(R.string.sale_price_required)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = uiState.productSkuInput,
                        onValueChange = onSkuChange,
                        label = { Text("SKU / référence (facultatif)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = uiState.productBarcodeInput,
                        onValueChange = onBarcodeChange,
                        label = { Text("Code-barres (facultatif)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.available_for_sale),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.productAvailableInput,
                            onCheckedChange = onAvailabilityChange
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = uiState.productNameInput.isNotBlank() && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(stringResource(R.string.save), color = Color.White)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private sealed interface ProductImageState {
    data object Loading : ProductImageState
    data object Empty : ProductImageState
    data class Loaded(val bitmap: Bitmap) : ProductImageState
}

@Composable
internal fun SafeProductImage(
    imagePath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by produceState<ProductImageState>(
        initialValue = if (imagePath.isNullOrBlank()) ProductImageState.Empty
        else ProductImageState.Loading,
        key1 = imagePath
    ) {
        value = if (imagePath.isNullOrBlank()) {
            ProductImageState.Empty
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = when {
                        imagePath.startsWith("http://") || imagePath.startsWith("https://") -> {
                            val connection = URL(imagePath).openConnection() as HttpURLConnection
                            connection.connectTimeout = 5_000
                            connection.readTimeout = 5_000
                            connection.instanceFollowRedirects = true
                            try {
                                connection.inputStream.use(BitmapFactory::decodeStream)
                            } finally {
                                connection.disconnect()
                            }
                        }
                        imagePath.startsWith("content://") ||
                            imagePath.startsWith("file://") -> {
                            context.contentResolver.openInputStream(Uri.parse(imagePath))
                                ?.use(BitmapFactory::decodeStream)
                        }
                        else -> File(imagePath).takeIf { it.isFile }
                            ?.inputStream()
                            ?.use(BitmapFactory::decodeStream)
                    }
                    bitmap?.let(ProductImageState::Loaded) ?: ProductImageState.Empty
                }.getOrDefault(ProductImageState.Empty)
            }
        }
    }

    Box(
        modifier = modifier
            .testTag("product_image")
            .background(Color(0xFFE9EEF3)),
        contentAlignment = Alignment.Center
    ) {
        when (val imageState = state) {
            ProductImageState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = Navy
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.image_loading),
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
            ProductImageState.Empty -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ImagePlaceholderIcon()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.no_image),
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
            is ProductImageState.Loaded -> {
                Image(
                    bitmap = imageState.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ImagePlaceholderIcon() {
    Canvas(Modifier.size(36.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = SecondaryText,
            style = Stroke(width = stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawCircle(
            color = SecondaryText,
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.72f,
                y = size.height * 0.28f
            )
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.46f)
            lineTo(size.width * 0.58f, size.height * 0.62f)
            lineTo(size.width * 0.72f, size.height * 0.50f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
        }
        drawPath(path, color = SecondaryText, style = Stroke(width = stroke))
    }
}

private fun productPrice(priceCentimes: Long): String =
    MonetaryUtils.formatDh(priceCentimes).replace('.', ',')
