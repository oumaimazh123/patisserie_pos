package ma.elaroui.pos.presentation.register.close

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.repository.CashMovementRepository
import ma.elaroui.pos.domain.repository.OrderRepository
import ma.elaroui.pos.domain.repository.RegisterRepository
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.CloseRegisterSessionUseCase
import ma.elaroui.pos.domain.usecase.GetCurrentExpectedCashUseCase
import ma.elaroui.pos.presentation.register.moneyInputToCentimesOrNull
import ma.elaroui.pos.presentation.register.sanitizeMoneyInput
import javax.inject.Inject
import kotlin.math.abs

data class CloseRegisterUiState(
    val session: RegisterSession? = null,
    val openingCashCentimes: Long = 0L,
    val totalCashInCentimes: Long = 0L,
    val totalCashOutCentimes: Long = 0L,
    val cashSalesCentimes: Long = 0L,
    val expectedCashCentimes: Long = 0L,
    val openOrderCount: Int = 0,
    val countedCashInput: String = "",
    val countedCashCentimes: Long = 0L,
    val differenceCentimes: Long = 0L,
    val closingNoteInput: String = "",
    val ownerPinInput: String = "",
    val requiresOwnerApproval: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val closedSession: RegisterSession? = null
)

@HiltViewModel
class CloseRegisterViewModel @Inject constructor(
    private val registerRepository: RegisterRepository,
    private val cashMovementRepository: CashMovementRepository,
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val getCurrentExpectedCashUseCase: GetCurrentExpectedCashUseCase,
    private val closeRegisterSessionUseCase: CloseRegisterSessionUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloseRegisterUiState())
    val uiState: StateFlow<CloseRegisterUiState> = _uiState.asStateFlow()

    init {
        loadSessionSummary()
    }

    fun loadSessionSummary() {
        val sessionId = sessionManager.currentUser?.currentRegisterSessionId
        if (sessionId == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Aucune session de caisse active."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val session = registerRepository.getSessionById(sessionId)
                    ?: throw IllegalStateException("Session de caisse introuvable.")
                val cashIn = cashMovementRepository.getTotalCashInForSession(sessionId)
                val cashOut = cashMovementRepository.getTotalCashOutForSession(sessionId)
                val expected = getCurrentExpectedCashUseCase(sessionId)
                val cashSales = expected - session.openingCashCentimes - cashIn + cashOut
                val openOrders = orderRepository.getOpenOrderCountForSession(sessionId)

                _uiState.update {
                    it.copy(
                        session = session,
                        openingCashCentimes = session.openingCashCentimes,
                        totalCashInCentimes = cashIn,
                        totalCashOutCentimes = cashOut,
                        cashSalesCentimes = cashSales,
                        expectedCashCentimes = expected,
                        openOrderCount = openOrders,
                        isLoading = false
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Impossible de charger le récapitulatif."
                    )
                }
            }
        }
    }

    fun updateCountedCashInput(input: String) {
        val sanitized = sanitizeMoneyInput(input) ?: return
        val countedCentimes = moneyInputToCentimesOrNull(sanitized) ?: 0L
        val expectedCentimes = _uiState.value.expectedCashCentimes
        val diff = countedCentimes - expectedCentimes
        val reqApproval = abs(diff) > closeRegisterSessionUseCase.varianceThresholdCentimes

        _uiState.update {
            it.copy(
                countedCashInput = sanitized,
                countedCashCentimes = countedCentimes,
                differenceCentimes = diff,
                requiresOwnerApproval = reqApproval,
                errorMessage = null
            )
        }
    }

    fun updateClosingNoteInput(note: String) {
        if (note.length <= 500) {
            _uiState.update { it.copy(closingNoteInput = note, errorMessage = null) }
        }
    }

    fun updateOwnerPinInput(pin: String) {
        if (pin.length <= 6 && pin.all(Char::isDigit)) {
            _uiState.update { it.copy(ownerPinInput = pin, errorMessage = null) }
        }
    }

    fun submitCloseRegister(onSuccess: () -> Unit) {
        val state = _uiState.value
        val sessionId = state.session?.id ?: return
        val currentUserId = sessionManager.currentUser?.userId ?: return

        if (state.isLoading) return
        if (state.openOrderCount > 0) {
            _uiState.update {
                it.copy(
                    errorMessage = "Clôture impossible : ${state.openOrderCount} commande(s) encore ouverte(s)."
                )
            }
            return
        }
        if (moneyInputToCentimesOrNull(state.countedCashInput) == null) {
            _uiState.update {
                it.copy(errorMessage = "Saisissez un montant compté valide.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val closed = closeRegisterSessionUseCase(
                    sessionId = sessionId,
                    countedCashCentimes = state.countedCashCentimes,
                    closingNote = state.closingNoteInput,
                    currentUser = currentUser,
                    ownerPinForLargeDifference = state.ownerPinInput
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        closedSession = closed
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Échec de clôture de caisse."
                    )
                }
            }
        }
    }
}
