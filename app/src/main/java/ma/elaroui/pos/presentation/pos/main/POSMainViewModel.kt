package ma.elaroui.pos.presentation.pos.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.repository.CategorySalesRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.CreateOrderUseCase
import ma.elaroui.pos.domain.usecase.GetCategoriesUseCase
import ma.elaroui.pos.domain.usecase.GetOrderDetailsUseCase
import ma.elaroui.pos.domain.usecase.ObserveSellableCatalogueUseCase
import ma.elaroui.pos.domain.usecase.UpdateOpenOrderUseCase
import javax.inject.Inject
import ma.elaroui.pos.shared.rules.CategoryPopularityRules

data class POSMainUiState(
    val cartState: CartState = CartState(),
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val tables: List<RestaurantTable> = emptyList(),
    val selectedCategoryId: Long? = null,
    val popularCategoryIds: List<Long> = emptyList(),
    val categoryScores: Map<Long, Long> = emptyMap(),
    val productScores: Map<Long, Long> = emptyMap(),
    val searchQuery: String = "",
    val editingOrderId: Long? = null,
    val editingOrderNumber: String? = null,
    val isTablePickerOpen: Boolean = false,
    val isNoteDialogOpen: Boolean = false,
    val selectedCartItemForNote: CartItem? = null,
    val itemNoteInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val orderCreatedSuccess: Order? = null
)

@HiltViewModel
class POSMainViewModel @Inject constructor(
    private val observeSellableCatalogueUseCase: ObserveSellableCatalogueUseCase,
    private val createOrderUseCase: CreateOrderUseCase,
    private val updateOpenOrderUseCase: UpdateOpenOrderUseCase,
    private val getOrderDetailsUseCase: GetOrderDetailsUseCase,
    private val userRepository: UserRepository,
    private val categorySalesRepository: CategorySalesRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(POSMainUiState())
    val uiState: StateFlow<POSMainUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadPopularCategories(activeCategoryIds: Set<Long>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val samples = runCatching {
                categorySalesRepository.getRecentCategorySales(CategoryPopularityRules.lookbackStart(now))
            }.getOrDefault(emptyList())
            val scores = CategoryPopularityRules.scores(
                samples = samples,
                nowEpochMilliseconds = now,
                activeCategoryIds = activeCategoryIds
            )
            val ranked = CategoryPopularityRules.rank(
                samples = samples,
                nowEpochMilliseconds = now,
                activeCategoryIds = activeCategoryIds
            )
            _uiState.update { current ->
                current.copy(
                    popularCategoryIds = ranked,
                    categoryScores = scores
                )
            }
        }
    }

    fun refreshPopularCategories() {
        loadPopularCategories(_uiState.value.categories.mapTo(mutableSetOf()) { it.id })
    }

    fun loadData() {
        viewModelScope.launch {
            observeSellableCatalogueUseCase().collect { catalogueGroups ->
                val allCats = catalogueGroups.map { it.category }
                val allProds = catalogueGroups.flatMap { it.products }
                _uiState.update { current ->
                    val validSelection = current.selectedCategoryId
                        ?.takeIf { id -> allCats.any { it.id == id } }
                    current.copy(
                        categories = allCats,
                        products = allProds,
                        selectedCategoryId = validSelection
                    )
                }
                loadPopularCategories(allCats.mapTo(mutableSetOf()) { it.id })
            }
        }
    }

    fun loadOrderForEditing(orderId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val order = getOrderDetailsUseCase(orderId)
            if (order != null && order.status == OrderStatus.OPEN) {
                val table = order.tableId?.let { tid -> _uiState.value.tables.find { it.id == tid } }
                val cartItems = order.items.map { item ->
                    val prod = _uiState.value.products.find { it.id == item.productId }
                        ?: Product(id = item.productId, categoryId = 0L, name = item.productNameSnapshot, priceCentimes = item.unitPriceSnapshotCentimes)
                    CartItem(
                        product = prod,
                        quantity = item.quantity,
                        note = item.note,
                        unitPriceCentimes = item.unitPriceSnapshotCentimes
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        editingOrderId = order.id,
                        editingOrderNumber = order.orderNumber,
                        cartState = CartState(
                            items = cartItems,
                            discountBasisPoints = if (order.subtotalCentimes > 0) {
                                ((order.discountCentimes * 10_000L) / order.subtotalCentimes).toInt()
                            } else 0,
                            orderType = order.type,
                            selectedTable = table
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Impossible de charger cette commande.") }
            }
        }
    }

    fun selectCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setOrderType(type: OrderType) {
        _uiState.update { current ->
            current.copy(
                cartState = current.cartState.copy(orderType = OrderType.COUNTER, selectedTable = null),
                errorMessage = null
            )
        }
    }

    fun selectDineInTable(table: RestaurantTable) {
        _uiState.update { current ->
            current.copy(
                cartState = current.cartState.copy(orderType = OrderType.DINE_IN, selectedTable = table),
                isTablePickerOpen = false,
                errorMessage = null
            )
        }
    }

    fun clearDineInTable() {
        _uiState.update { current ->
            current.copy(
                cartState = current.cartState.copy(
                    orderType = OrderType.DINE_IN,
                    selectedTable = null
                ),
                isTablePickerOpen = false,
                errorMessage = null
            )
        }
    }

    fun openTablePicker() {
        _uiState.update { it.copy(isTablePickerOpen = true) }
    }

    fun closeTablePicker() {
        _uiState.update { it.copy(isTablePickerOpen = false) }
    }

    fun addProductToCart(product: Product) {
        if (!product.active || !product.available) {
            _uiState.update {
                it.copy(errorMessage = "Ce produit n'est pas disponible à la vente.")
            }
            return
        }
        _uiState.update { current ->
            val existingIndex = current.cartState.items.indexOfFirst { it.product.id == product.id && it.note.isNull_or_blank() }
            val updatedItems = current.cartState.items.toMutableList()

            if (existingIndex >= 0) {
                val existing = updatedItems[existingIndex]
                updatedItems[existingIndex] = existing.copy(quantity = existing.quantity + 1)
            } else {
                updatedItems.add(CartItem(product = product, quantity = 1, unitPriceCentimes = product.priceCentimes))
            }

            current.copy(
                cartState = current.cartState.copy(items = updatedItems),
                errorMessage = null
            )
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    fun updateCartItemQuantity(index: Int, delta: Int) {
        _uiState.update { current ->
            val updatedItems = current.cartState.items.toMutableList()
            if (index in updatedItems.indices) {
                val item = updatedItems[index]
                val newQty = item.quantity + delta
                if (newQty <= 0) {
                    updatedItems.removeAt(index)
                } else {
                    updatedItems[index] = item.copy(quantity = newQty)
                }
            }
            current.copy(cartState = current.cartState.copy(items = updatedItems))
        }
    }

    fun removeCartItem(index: Int) {
        _uiState.update { current ->
            val updatedItems = current.cartState.items.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems.removeAt(index)
            }
            current.copy(cartState = current.cartState.copy(items = updatedItems))
        }
    }

    fun openNoteDialog(item: CartItem) {
        _uiState.update {
            it.copy(
                isNoteDialogOpen = true,
                selectedCartItemForNote = item,
                itemNoteInput = item.note ?: ""
            )
        }
    }

    fun closeNoteDialog() {
        _uiState.update { it.copy(isNoteDialogOpen = false, selectedCartItemForNote = null) }
    }

    fun saveCartItemNote(note: String) {
        val target = _uiState.value.selectedCartItemForNote ?: return
        _uiState.update { current ->
            val updatedItems = current.cartState.items.map { item ->
                if (item == target) item.copy(note = note.trim().takeIf { it.isNotBlank() }) else item
            }
            current.copy(
                cartState = current.cartState.copy(items = updatedItems),
                isNoteDialogOpen = false,
                selectedCartItemForNote = null
            )
        }
    }

    fun clearCart() {
        _uiState.update { current ->
            current.copy(
                cartState = current.cartState.copy(items = emptyList()),
                editingOrderId = null,
                editingOrderNumber = null,
                errorMessage = null
            )
        }
    }

    fun setDiscountBasisPoints(value: Int) {
        if (value !in 0..10_000) return
        _uiState.update { current ->
            current.copy(cartState = current.cartState.copy(discountBasisPoints = value), errorMessage = null)
        }
    }

    fun submitSaveOrder() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: return
        if (state.cartState.items.isEmpty() || state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val savedOrder = if (state.editingOrderId == null) {
                    createOrderUseCase(
                        type = OrderType.COUNTER,
                        tableId = null,
                        cartItems = state.cartState.items,
                        discountBasisPoints = state.cartState.discountBasisPoints,
                        currentUser = currentUser
                    )
                } else {
                    updateOpenOrderUseCase(
                        orderId = state.editingOrderId,
                        updatedCartItems = state.cartState.items,
                        discountBasisPoints = state.cartState.discountBasisPoints,
                        currentUser = currentUser
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        orderCreatedSuccess = savedOrder,
                        cartState = CartState(),
                        editingOrderId = null,
                        editingOrderNumber = null,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'enregistrement de la commande."
                    )
                }
            }
        }
    }

    fun clearOrderSuccessEvent() {
        _uiState.update { it.copy(orderCreatedSuccess = null) }
    }
}

internal fun filterPosProducts(
    products: List<Product>,
    searchQuery: String,
    selectedCategoryId: Long?,
    productScores: Map<Long, Long> = emptyMap()
): List<Product> {
    val normalizedQuery = searchQuery.trim()
    val filtered = products.filter { product ->
        product.active && product.available &&
            (normalizedQuery.isNotEmpty() || selectedCategoryId == null || product.categoryId == selectedCategoryId) &&
            (
                normalizedQuery.isEmpty() ||
                    product.name.contains(normalizedQuery, ignoreCase = true) ||
                    product.sku?.contains(normalizedQuery, ignoreCase = true) == true ||
                    product.barcode?.contains(normalizedQuery, ignoreCase = true) == true
            )
    }
    return filtered.sortedWith(
        compareByDescending<Product> { productScores[it.id] ?: 0L }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id }
    )
}
