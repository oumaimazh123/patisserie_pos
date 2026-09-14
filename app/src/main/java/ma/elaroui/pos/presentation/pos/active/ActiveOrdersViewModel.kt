package ma.elaroui.pos.presentation.pos.active

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
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.CancelOrderUseCase
import ma.elaroui.pos.domain.usecase.GetTablesUseCase
import ma.elaroui.pos.domain.usecase.MoveDineInOrderUseCase
import ma.elaroui.pos.domain.usecase.ObserveActiveOrdersUseCase
import javax.inject.Inject

data class ActiveOrdersUiState(
    val activeOrders: List<Order> = emptyList(),
    val tables: List<RestaurantTable> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: OrderType? = null,
    val isMoveTableDialogOpen: Boolean = false,
    val selectedOrderForMove: Order? = null,
    val destinationTableIdInput: Long? = null,
    val isCancelDialogOpen: Boolean = false,
    val selectedOrderForCancel: Order? = null,
    val cancellationReasonInput: String = "",
    val ownerPinInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ActiveOrdersViewModel @Inject constructor(
    private val observeActiveOrdersUseCase: ObserveActiveOrdersUseCase,
    private val getTablesUseCase: GetTablesUseCase,
    private val moveDineInOrderUseCase: MoveDineInOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveOrdersUiState())
    val uiState: StateFlow<ActiveOrdersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            observeActiveOrdersUseCase().collect { orderList ->
                _uiState.update { it.copy(activeOrders = orderList) }
            }
        }
        viewModelScope.launch {
            getTablesUseCase(forOwner = false).collect { tableList ->
                _uiState.update { it.copy(tables = tableList) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectTypeFilter(type: OrderType?) {
        _uiState.update { it.copy(selectedTypeFilter = type) }
    }

    // MOVE TABLE DIALOG
    fun openMoveTableDialog(order: Order) {
        _uiState.update {
            it.copy(
                isMoveTableDialogOpen = true,
                selectedOrderForMove = order,
                destinationTableIdInput = null,
                errorMessage = null
            )
        }
    }

    fun closeMoveTableDialog() {
        _uiState.update { it.copy(isMoveTableDialogOpen = false) }
    }

    fun selectDestinationTable(tableId: Long) {
        _uiState.update { it.copy(destinationTableIdInput = tableId) }
    }

    fun submitMoveTable() {
        val state = _uiState.value
        val order = state.selectedOrderForMove ?: return
        val destId = state.destinationTableIdInput ?: return
        val currentUserId = sessionManager.currentUser?.userId ?: return
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                moveDineInOrderUseCase(order.id, destId, currentUser)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isMoveTableDialogOpen = false,
                        successMessage = "Commande déplacée avec succès vers la nouvelle table."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur de déplacement de la table."
                    )
                }
            }
        }
    }

    // CANCEL ORDER DIALOG
    fun openCancelDialog(order: Order) {
        _uiState.update {
            it.copy(
                isCancelDialogOpen = true,
                selectedOrderForCancel = order,
                cancellationReasonInput = "",
                ownerPinInput = "",
                errorMessage = null
            )
        }
    }

    fun closeCancelDialog() {
        _uiState.update { it.copy(isCancelDialogOpen = false) }
    }

    fun updateCancellationReason(reason: String) {
        _uiState.update {
            it.copy(
                cancellationReasonInput = reason,
                errorMessage = null
            )
        }
    }

    fun updateOwnerPin(pin: String) {
        if (pin.length <= 6 && pin.all(Char::isDigit)) {
            _uiState.update { it.copy(ownerPinInput = pin, errorMessage = null) }
        }
    }

    fun submitCancelOrder() {
        val state = _uiState.value
        val order = state.selectedOrderForCancel ?: return
        val currentUserId = sessionManager.currentUser?.userId ?: return
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                cancelOrderUseCase(
                    orderId = order.id,
                    ownerPin = state.ownerPinInput,
                    reason = state.cancellationReasonInput
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCancelDialogOpen = false,
                        successMessage = "Vente ${order.orderNumber} annulée."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'annulation de la commande."
                    )
                }
            }
        }
    }
}
