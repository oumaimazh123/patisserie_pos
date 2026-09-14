package ma.elaroui.pos.presentation.management.products

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ma.elaroui.pos.core.util.ImageUtils
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.Category
import ma.elaroui.pos.domain.model.Product
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ActivateProductUseCase
import ma.elaroui.pos.domain.usecase.CreateProductUseCase
import ma.elaroui.pos.domain.usecase.DeactivateProductUseCase
import ma.elaroui.pos.domain.usecase.GetCategoriesUseCase
import ma.elaroui.pos.domain.usecase.GetProductsUseCase
import ma.elaroui.pos.domain.usecase.ObserveSellableCatalogueUseCase
import ma.elaroui.pos.domain.usecase.SetProductAvailabilityUseCase
import ma.elaroui.pos.domain.usecase.UpdateProductUseCase
import java.math.BigDecimal
import javax.inject.Inject

data class ProductManagementUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val isAddEditDialogOpen: Boolean = false,
    val editingProduct: Product? = null,
    val productNameInput: String = "",
    val productCategoryIdInput: Long? = null,
    val productPriceInput: String = "",
    val productSkuInput: String = "",
    val productBarcodeInput: String = "",
    val productImagePathInput: String? = null,
    val productImageChanged: Boolean = false,
    val productAvailableInput: Boolean = true,
    val detailProduct: Product? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getProductsUseCase: GetProductsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val setProductAvailabilityUseCase: SetProductAvailabilityUseCase,
    private val activateProductUseCase: ActivateProductUseCase,
    private val deactivateProductUseCase: DeactivateProductUseCase,
    val observeSellableCatalogueUseCase: ObserveSellableCatalogueUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductManagementUiState())
    val uiState: StateFlow<ProductManagementUiState> = _uiState.asStateFlow()
    private var categoriesJob: Job? = null
    private var productsJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        categoriesJob?.cancel()
        productsJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        categoriesJob = viewModelScope.launch {
            getCategoriesUseCase(forOwner = true).collect { catList ->
                _uiState.update { state ->
                    val validFilter = state.selectedCategoryId?.takeIf { id -> catList.any { it.id == id } }
                    state.copy(categories = catList, selectedCategoryId = validFilter)
                }
            }
        }
        productsJob = viewModelScope.launch {
            getProductsUseCase(forOwner = true).collect { prodList ->
                _uiState.update { it.copy(products = prodList, isLoading = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategoryFilter(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun openAddDialog() {
        val firstCat = _uiState.value.categories.firstOrNull { it.active }?.id
        _uiState.update {
            it.copy(
                isAddEditDialogOpen = true,
                editingProduct = null,
                productNameInput = "",
                productCategoryIdInput = firstCat,
                productPriceInput = "",
                productSkuInput = "",
                productBarcodeInput = "",
                productImagePathInput = null,
                productImageChanged = false,
                productAvailableInput = true,
                errorMessage = null
            )
        }
    }

    fun openEditDialog(product: Product) {
        _uiState.update {
            it.copy(
                isAddEditDialogOpen = true,
                editingProduct = product,
                productNameInput = product.name,
                productCategoryIdInput = product.categoryId,
                productPriceInput = MonetaryUtils.toBigDecimal(product.priceCentimes).toPlainString(),
                productSkuInput = product.sku.orEmpty(),
                productBarcodeInput = product.barcode.orEmpty(),
                productImagePathInput = product.imagePath,
                productImageChanged = false,
                productAvailableInput = product.available,
                errorMessage = null
            )
        }
    }

    fun closeDialog() {
        val state = _uiState.value
        if (state.productImageChanged &&
            state.productImagePathInput != state.editingProduct?.imagePath
        ) {
            ImageUtils.deleteProductImage(state.productImagePathInput)
        }
        _uiState.update { it.copy(isAddEditDialogOpen = false) }
    }

    fun openProductDetails(product: Product) {
        _uiState.update { it.copy(detailProduct = product) }
    }

    fun closeProductDetails() {
        _uiState.update { it.copy(detailProduct = null) }
    }

    fun editDetailedProduct() {
        val product = _uiState.value.detailProduct ?: return
        closeProductDetails()
        openEditDialog(product)
    }

    fun updateForm(name: String, catId: Long?, price: String, available: Boolean) {
        _uiState.update {
            it.copy(
                productNameInput = name,
                productCategoryIdInput = catId,
                productPriceInput = price,
                productAvailableInput = available,
                errorMessage = null
            )
        }
    }

    fun updateIdentifiers(sku: String, barcode: String) {
        _uiState.update {
            it.copy(productSkuInput = sku, productBarcodeInput = barcode, errorMessage = null)
        }
    }

    fun selectProductImage(uri: Uri) {
        try {
            val savedPath = ImageUtils.saveProductImage(context, uri)
            val state = _uiState.value
            if (state.productImageChanged &&
                state.productImagePathInput != state.editingProduct?.imagePath
            ) {
                ImageUtils.deleteProductImage(state.productImagePathInput)
            }
            _uiState.update {
                it.copy(
                    productImagePathInput = savedPath,
                    productImageChanged = true,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Échec du chargement de l'image.") }
        }
    }

    fun removeProductImage() {
        val state = _uiState.value
        if (state.productImageChanged &&
            state.productImagePathInput != state.editingProduct?.imagePath
        ) {
            ImageUtils.deleteProductImage(state.productImagePathInput)
        }
        _uiState.update {
            it.copy(
                productImagePathInput = null,
                productImageChanged = true,
                errorMessage = null
            )
        }
    }

    fun submitSaveProduct() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        val catId = state.productCategoryIdInput ?: run {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une catégorie.") }
            return
        }
        if (state.isLoading) return
        if (state.productNameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Le nom du produit est obligatoire.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val normalizedPrice = state.productPriceInput.trim().replace(',', '.')
                val priceDecimal = normalizedPrice.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("Le prix saisi est invalide.")
                if (priceDecimal <= BigDecimal.ZERO) {
                    throw IllegalArgumentException("Le prix doit être supérieur à zéro.")
                }
                if (priceDecimal.scale() > 2) {
                    throw IllegalArgumentException("Le prix accepte au maximum deux décimales.")
                }
                if (priceDecimal > BigDecimal("9999999.99")) {
                    throw IllegalArgumentException("Le prix saisi est trop élevé.")
                }
                val centimes = MonetaryUtils.toCentimes(priceDecimal)

                val saved = if (state.editingProduct == null) {
                    createProductUseCase(
                        categoryId = catId,
                        name = state.productNameInput,
                        priceCentimes = centimes,
                        imagePath = state.productImagePathInput,
                        sku = state.productSkuInput,
                        barcode = state.productBarcodeInput,
                        available = state.productAvailableInput,
                        currentUser = currentUser
                    )
                } else {
                    updateProductUseCase(
                        productId = state.editingProduct.id,
                        categoryId = catId,
                        name = state.productNameInput,
                        priceCentimes = centimes,
                        imagePath = state.productImagePathInput,
                        sku = state.productSkuInput,
                        barcode = state.productBarcodeInput,
                        replaceImage = state.productImageChanged,
                        available = state.productAvailableInput,
                        currentUser = currentUser
                    )
                }
                if (state.productImageChanged &&
                    state.editingProduct?.imagePath != saved.imagePath
                ) {
                    ImageUtils.deleteProductImage(state.editingProduct?.imagePath)
                }

                _uiState.update {
                    it.copy(
                        products = it.products
                            .filterNot { product -> product.id == saved.id }
                            .plus(saved)
                            .sortedWith(compareBy<Product> { product -> product.displayOrder }.thenBy { product -> product.name }),
                        isLoading = false,
                        isAddEditDialogOpen = false,
                        detailProduct = null,
                        successMessage = "Produit enregistré avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'enregistrement du produit."
                    )
                }
            }
        }
    }

    fun toggleProductAvailability(product: Product) {
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                setProductAvailabilityUseCase(product.id, !product.available, currentUser)
                _uiState.update {
                    it.copy(
                        products = it.products.map { item ->
                            if (item.id == product.id) item.copy(available = !product.available) else item
                        },
                        isLoading = false,
                        successMessage = if (product.available) {
                            "Produit retiré de la vente."
                        } else {
                            "Produit disponible à la vente."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun toggleProductActiveState(product: Product) {
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                if (product.active) {
                    deactivateProductUseCase(product.id, currentUser)
                } else {
                    activateProductUseCase(product.id, currentUser)
                }
                _uiState.update {
                    it.copy(
                        products = it.products.map { item ->
                            if (item.id == product.id) {
                                item.copy(
                                    active = !product.active,
                                    available = if (product.active) false else item.available
                                )
                            } else {
                                item
                            }
                        },
                        isLoading = false,
                        successMessage = if (product.active) "Produit supprimé." else "Produit réactivé."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }
}
