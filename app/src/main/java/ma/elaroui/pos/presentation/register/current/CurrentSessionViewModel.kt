package ma.elaroui.pos.presentation.register.current

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.CashMovement
import ma.elaroui.pos.domain.model.CashMovementType
import ma.elaroui.pos.domain.model.Register
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.repository.CashMovementRepository
import ma.elaroui.pos.domain.repository.RegisterRepository
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.GetCurrentExpectedCashUseCase
import ma.elaroui.pos.domain.usecase.RecordCashMovementUseCase
import ma.elaroui.pos.presentation.register.moneyInputToCentimesOrNull
import ma.elaroui.pos.presentation.register.sanitizeMoneyInput
import javax.inject.Inject

data class CurrentSessionUiState(
    val session: RegisterSession? = null,
    val register: Register? = null,
    val cashier: User? = null,
    val totalCashInCentimes: Long = 0L,
    val totalCashOutCentimes: Long = 0L,
    val cashSalesCentimes: Long = 0L,
    val currentExpectedCashCentimes: Long = 0L,
    val cashMovements: List<CashMovement> = emptyList(),
    val movementUserNames: Map<Long, String> = emptyMap(),
    val canCloseRegister: Boolean = false,
    val isMovementDialogOpen: Boolean = false,
    val movementType: CashMovementType = CashMovementType.CASH_IN,
    val movementAmountInput: String = "",
    val movementReasonInput: String = "",
    val movementDescriptionInput: String = "",
    val ownerPinInput: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CurrentSessionViewModel @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val userRepository: UserRepository,
    private val getCurrentExpectedCashUseCase: GetCurrentExpectedCashUseCase,
    private val recordCashMovementUseCase: RecordCashMovementUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrentSessionUiState())
    val uiState: StateFlow<CurrentSessionUiState> = _uiState.asStateFlow()
    private var sessionJob: Job? = null
    private var movementJob: Job? = null

    init {
        loadCurrentSession()
    }

    fun loadCurrentSession() {
        sessionJob?.cancel()
        movementJob?.cancel()
        val sessionId = sessionManager.currentUser?.currentRegisterSessionId
        if (sessionId == null) {
            _uiState.value = CurrentSessionUiState(isLoading = false)
            return
        }

        sessionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val session = registerRepository.getSessionById(sessionId)
                    ?: throw IllegalStateException("Session de caisse introuvable.")
                val register = registerRepository.getRegisterById(session.registerId)
                val cashier = userRepository.getUserById(session.cashierId)
                _uiState.update {
                    it.copy(
                        session = session,
                        register = register,
                        cashier = cashier,
                        canCloseRegister = sessionManager.currentUser?.role == UserRole.OWNER,
                        isLoading = false
                    )
                }
                refreshSummary(sessionId)
                observeMovements(sessionId)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Impossible de charger la session."
                    )
                }
            }
        }
    }

    private fun observeMovements(sessionId: Long) {
        movementJob?.cancel()
        movementJob = viewModelScope.launch {
            cashMovementRepository.getCashMovementsForSession(sessionId).collect { list ->
                val names = list.map { it.createdByUserId }
                    .distinct()
                    .mapNotNull { userRepository.getUserById(it) }
                    .associate { it.id to it.name }
                _uiState.update { state ->
                    state.copy(
                        cashMovements = list.sortedByDescending { it.createdAt },
                        movementUserNames = names
                    )
                }
                refreshSummary(sessionId)
            }
        }
    }

    private suspend fun refreshSummary(sessionId: Long) {
        val session = _uiState.value.session ?: return
        val cashIn = cashMovementRepository.getTotalCashInForSession(sessionId)
        val cashOut = cashMovementRepository.getTotalCashOutForSession(sessionId)
        val expected = getCurrentExpectedCashUseCase(sessionId)
        val cashSales = expected - session.openingCashCentimes - cashIn + cashOut
        _uiState.update {
            it.copy(
                totalCashInCentimes = cashIn,
                totalCashOutCentimes = cashOut,
                cashSalesCentimes = cashSales,
                currentExpectedCashCentimes = expected
            )
        }
    }

    fun openMovementDialog(type: CashMovementType) {
        _uiState.update {
            it.copy(
                isMovementDialogOpen = true,
                movementType = type,
                movementAmountInput = "",
                movementReasonInput = "",
                movementDescriptionInput = "",
                ownerPinInput = "",
                errorMessage = null
            )
        }
    }

    fun closeMovementDialog() {
        _uiState.update {
            it.copy(
                isMovementDialogOpen = false,
                errorMessage = null,
                movementAmountInput = "",
                movementReasonInput = "",
                movementDescriptionInput = "",
                ownerPinInput = ""
            )
        }
    }

    fun updateMovementForm(
        amount: String,
        reason: String,
        description: String,
        ownerPin: String
    ) {
        val sanitizedAmount = sanitizeMoneyInput(amount) ?: return
        if (description.length > 200 || ownerPin.length > 6 || !ownerPin.all(Char::isDigit)) return
        _uiState.update {
            it.copy(
                movementAmountInput = sanitizedAmount,
                movementReasonInput = reason,
                movementDescriptionInput = description,
                ownerPinInput = ownerPin,
                errorMessage = null
            )
        }
    }

    fun submitCashMovement() {
        val state = _uiState.value
        val sessionId = state.session?.id ?: return
        val currentUserId = sessionManager.currentUser?.userId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val centimes = moneyInputToCentimesOrNull(state.movementAmountInput)
                    ?: throw IllegalArgumentException("Saisissez un montant valide avec au maximum 2 décimales.")
                if (state.movementReasonInput.isBlank()) {
                    throw IllegalArgumentException("Sélectionnez un motif.")
                }
                if (
                    state.movementReasonInput == "Autre" &&
                    state.movementDescriptionInput.isBlank()
                ) {
                    throw IllegalArgumentException("La description est obligatoire pour le motif Autre.")
                }
                val movementReason = buildString {
                    append(state.movementReasonInput.trim())
                    state.movementDescriptionInput.trim().takeIf { it.isNotBlank() }?.let {
                        append(" — ")
                        append(it)
                    }
                }

                val movement = recordCashMovementUseCase(
                    sessionId = sessionId,
                    type = state.movementType,
                    amountCentimes = centimes,
                    reason = movementReason,
                    currentUser = currentUser,
                    approvalPinForCashOut = state.ownerPinInput
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isMovementDialogOpen = false,
                        movementAmountInput = "",
                        movementReasonInput = "",
                        movementDescriptionInput = "",
                        ownerPinInput = "",
                        cashMovements = (it.cashMovements + movement)
                            .distinctBy { cashMovement -> cashMovement.id }
                            .sortedByDescending { cashMovement -> cashMovement.createdAt },
                        successMessage = "Mouvement d'espèces enregistré.",
                        errorMessage = null
                    )
                }
                refreshSummary(sessionId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Échec d'enregistrement du mouvement."
                    )
                }
            }
        }
    }
}
