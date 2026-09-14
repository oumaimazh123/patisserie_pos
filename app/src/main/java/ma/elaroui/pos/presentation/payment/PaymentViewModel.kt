package ma.elaroui.pos.presentation.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.PaymentResult
import ma.elaroui.pos.domain.repository.OrderRepository
import ma.elaroui.pos.domain.usecase.CompleteOrderWithPaymentUseCase
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class PaymentUiState(
    val order: Order? = null,
    val isLoadingOrder: Boolean = true,
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
    val receivedCashInput: String = "",
    val cardReference: String = "",
    val isCardTerminalApproved: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val paymentResult: PaymentResult? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val completeOrderWithPaymentUseCase: CompleteOrderWithPaymentUseCase
) : ViewModel() {

    val orderId: Long = checkNotNull(savedStateHandle["orderId"]) { "orderId is required" }

    // Submission token stored in SavedStateHandle to persist across rotation
    private val submissionToken: String = savedStateHandle["submissionToken"] ?: UUID.randomUUID().toString().also {
        savedStateHandle["submissionToken"] = it
    }

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>("receivedCashInput")?.let { saved ->
            _uiState.update { it.copy(receivedCashInput = saved) }
        }
        loadOrder()
    }

    fun loadOrder() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOrder = true, errorMessage = null) }
            val order = orderRepository.getOrderById(orderId)
            if (order == null) {
                _uiState.update { it.copy(isLoadingOrder = false, errorMessage = "Commande introuvable.") }
            } else {
                _uiState.update {
                    val initialInput = it.receivedCashInput.ifBlank {
                        MonetaryUtils.toBigDecimal(order.totalCentimes).toPlainString()
                    }
                    savedStateHandle["receivedCashInput"] = initialInput
                    it.copy(
                        order = order,
                        isLoadingOrder = false,
                        receivedCashInput = initialInput
                    )
                }
            }
        }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(selectedMethod = method, errorMessage = null) }
    }

    fun updateReceivedCashInput(input: String) {
        if (!isValidMoneyInput(input)) return
        savedStateHandle["receivedCashInput"] = input
        _uiState.update { it.copy(receivedCashInput = input, errorMessage = null) }
    }

    fun setQuickCashAmount(amountDh: Long) {
        savedStateHandle["receivedCashInput"] = amountDh.toString()
        _uiState.update { it.copy(receivedCashInput = amountDh.toString(), errorMessage = null) }
    }

    fun setExactCashAmount() {
        val exact = MonetaryUtils.toBigDecimal(_uiState.value.order?.totalCentimes ?: 0L).toPlainString()
        savedStateHandle["receivedCashInput"] = exact
        _uiState.update { it.copy(receivedCashInput = exact, errorMessage = null) }
    }

    fun updateCardReference(ref: String) {
        _uiState.update { it.copy(cardReference = ref, errorMessage = null) }
    }

    fun toggleCardTerminalApproval(approved: Boolean) {
        _uiState.update { it.copy(isCardTerminalApproved = approved, errorMessage = null) }
    }

    fun processPayment() {
        val state = _uiState.value
        val order = state.order ?: return

        if (state.isProcessing) return
        val orderTotal = order.totalCentimes
        var receivedCentimes = 0L

        if (state.selectedMethod == PaymentMethod.CASH) {
            receivedCentimes = parseMoneyToCentimes(state.receivedCashInput) ?: run {
                _uiState.update { it.copy(errorMessage = "Le montant reçu est vide ou invalide.") }
                return
            }
            if (receivedCentimes < orderTotal) {
                _uiState.update {
                    it.copy(errorMessage = "Le montant reçu (${state.receivedCashInput} DH) est inférieur au total de la commande.")
                }
                return
            }
        } else if (state.selectedMethod == PaymentMethod.CARD) {
            if (!state.isCardTerminalApproved) {
                _uiState.update {
                    it.copy(errorMessage = "Veuillez confirmer que le paiement a été validé sur le TPE / Terminal Carte.")
                }
                return
            }
            receivedCentimes = orderTotal
        }

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = completeOrderWithPaymentUseCase(
                    orderId = orderId,
                    paymentMethod = state.selectedMethod,
                    amountCentimes = orderTotal,
                    receivedAmountCentimes = receivedCentimes,
                    submissionToken = submissionToken,
                    externalReference = if (state.selectedMethod == PaymentMethod.CARD) state.cardReference else null,
                    buyerCompanyName = order.buyerCompanyName.orEmpty(),
                    buyerAddress = order.buyerAddress.orEmpty(),
                    buyerIce = order.buyerIce.orEmpty()
                )
                _uiState.update { it.copy(isProcessing = false, paymentResult = result) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.localizedMessage ?: "Erreur de règlement.")
                }
            }
        }
    }

    fun clearPaymentResultEvent() {
        _uiState.update { it.copy(paymentResult = null) }
    }
}

internal fun isValidMoneyInput(input: String): Boolean =
    input.isEmpty() || Regex("^\\d+([.,]\\d{0,2})?$").matches(input)

internal fun parseMoneyToCentimes(input: String): Long? = runCatching {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.endsWith(".")) return null
    when (val parsed = MoneyRules.parseToCentimes(normalized)) {
        is MoneyParseResult.Success -> parsed.centimes
        is MoneyParseResult.Failure -> null
    }
}.getOrNull()
