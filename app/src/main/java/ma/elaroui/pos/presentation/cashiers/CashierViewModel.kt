package ma.elaroui.pos.presentation.cashiers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ManageCashierUseCase
import javax.inject.Inject

data class CashiersUiState(
    val cashiers: List<User> = emptyList(),
    val isCreateDialogOpen: Boolean = false,
    val isResetPinDialogOpen: Boolean = false,
    val selectedCashier: User? = null,
    val newCashierName: String = "",
    val newCashierPin: String = "",
    val resetNewPin: String = "",
    val ownerPinConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CashierViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val manageCashierUseCase: ManageCashierUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashiersUiState())
    val uiState: StateFlow<CashiersUiState> = _uiState.asStateFlow()

    init {
        loadCashiers()
    }

    fun loadCashiers() {
        viewModelScope.launch {
            userRepository.getAllActiveUsers().collect { list ->
                val cashiersOnly = list.filter { it.role == UserRole.CASHIER }
                _uiState.update { it.copy(cashiers = cashiersOnly) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogOpen = true,
                newCashierName = "",
                newCashierPin = "",
                errorMessage = null
            )
        }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = false) }
    }

    fun updateCreateForm(name: String, pin: String) {
        _uiState.update { it.copy(newCashierName = name, newCashierPin = pin) }
    }

    fun createCashier() {
        val state = _uiState.value
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                manageCashierUseCase.createCashier(state.newCashierName, state.newCashierPin)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCreateDialogOpen = false,
                        successMessage = "Caissier créé avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur création caissier."
                    )
                }
            }
        }
    }

    fun openResetPinDialog(cashier: User) {
        _uiState.update {
            it.copy(
                isResetPinDialogOpen = true,
                selectedCashier = cashier,
                resetNewPin = "",
                ownerPinConfirm = "",
                errorMessage = null
            )
        }
    }

    fun closeResetPinDialog() {
        _uiState.update { it.copy(isResetPinDialogOpen = false, selectedCashier = null) }
    }

    fun updateResetPinForm(newPin: String, ownerPin: String) {
        _uiState.update { it.copy(resetNewPin = newPin, ownerPinConfirm = ownerPin) }
    }

    fun submitResetPin() {
        val state = _uiState.value
        val cashier = state.selectedCashier ?: return
        val currentUserId = sessionManager.currentUser?.userId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUserObj = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Compte Propriétaire introuvable.")

                manageCashierUseCase.resetCashierPin(
                    cashierId = cashier.id,
                    newPin = state.resetNewPin,
                    ownerPin = state.ownerPinConfirm,
                    ownerUser = currentUserObj
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isResetPinDialogOpen = false,
                        selectedCashier = null,
                        successMessage = "Code PIN réinitialisé avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Échec de réinitialisation."
                    )
                }
            }
        }
    }

    fun toggleCashierActive(cashier: User) {
        val currentOwnerId = sessionManager.currentUser?.userId ?: return
        viewModelScope.launch {
            try {
                manageCashierUseCase.toggleCashierActiveState(cashier.id, currentOwnerId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }
}
