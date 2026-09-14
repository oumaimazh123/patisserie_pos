package ma.elaroui.pos.presentation.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.core.print.PrintResult
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.ReceiptData
import ma.elaroui.pos.domain.usecase.GetCompletedSaleDetailsUseCase
import ma.elaroui.pos.domain.usecase.PrintReceiptUseCase
import javax.inject.Inject

data class SaleDetailUiState(
    val order: Order? = null,
    val receiptData: ReceiptData? = null,
    val cashierName: String = "",
    val registerName: String = "",
    val tableName: String? = null,
    val isLoading: Boolean = true,
    val isPrinting: Boolean = false,
    val printMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCompletedSaleDetailsUseCase: GetCompletedSaleDetailsUseCase,
    private val printReceiptUseCase: PrintReceiptUseCase
) : ViewModel() {

    val orderId: Long = checkNotNull(savedStateHandle["orderId"]) { "orderId is required" }

    private val _uiState = MutableStateFlow(SaleDetailUiState())
    val uiState: StateFlow<SaleDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val details = getCompletedSaleDetailsUseCase(orderId)
            if (details == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Détail introuvable.") }
            } else {
                _uiState.update {
                    it.copy(
                        order = details.order,
                        receiptData = details.receiptData,
                        cashierName = details.cashierName,
                        registerName = details.registerName,
                        tableName = details.tableName,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun reprintReceipt() {
        val receiptData = _uiState.value.receiptData ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, printMessage = null) }
            val copyData = receiptData.copy(isReprint = true)
            val result = printReceiptUseCase(copyData)
            when (result) {
                is PrintResult.Success -> {
                    _uiState.update { it.copy(isPrinting = false, printMessage = "Réimpression lancée avec succès.") }
                }
                is PrintResult.Failure -> {
                    _uiState.update { it.copy(isPrinting = false, printMessage = "Échec d'impression: ${result.reason}") }
                }
            }
        }
    }
}
