package ma.elaroui.pos.presentation.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.PaymentStatus
import ma.elaroui.pos.domain.model.SalesHistoryEntry
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ExportSalesHistoryCsvUseCase
import ma.elaroui.pos.domain.usecase.GetSalesHistoryUseCase
import java.io.OutputStream
import java.util.Calendar
import javax.inject.Inject

data class SalesHistoryOption(
    val id: Long,
    val label: String
)

data class SalesHistorySummary(
    val orderCount: Int = 0,
    val totalCentimes: Long = 0L,
    val cashCentimes: Long = 0L,
    val cardCentimes: Long = 0L,
    val averageCentimes: Long = 0L
)

data class CompletedSalesUiState(
    val entries: List<SalesHistoryEntry> = emptyList(),
    val filteredCount: Int = 0,
    val hasMore: Boolean = false,
    val searchQuery: String = "",
    val status: OrderStatus = OrderStatus.COMPLETED,
    val startDateMs: Long = currentMonthStart(),
    val endDateMs: Long = endOfDay(System.currentTimeMillis()),
    val selectedCashierId: Long? = null,
    val selectedRegisterId: Long? = null,
    val selectedPaymentMethod: PaymentMethod? = null,
    val selectedOrderType: OrderType? = null,
    val cashierOptions: List<SalesHistoryOption> = emptyList(),
    val registerOptions: List<SalesHistoryOption> = emptyList(),
    val summary: SalesHistorySummary = SalesHistorySummary(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isOwner: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccessMessage: String? = null
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            selectedCashierId,
            selectedRegisterId,
            selectedPaymentMethod,
            selectedOrderType
        ).size
}

@HiltViewModel
class CompletedSalesViewModel @Inject constructor(
    private val getSalesHistoryUseCase: GetSalesHistoryUseCase,
    private val exportSalesHistoryCsvUseCase: ExportSalesHistoryCsvUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompletedSalesUiState())
    val uiState: StateFlow<CompletedSalesUiState> = _uiState.asStateFlow()

    private var allEntries: List<SalesHistoryEntry> = emptyList()
    private var filteredEntries: List<SalesHistoryEntry> = emptyList()
    private var visibleLimit = PAGE_SIZE
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.sessionState
                .map { session ->
                    Triple(
                        session?.role == UserRole.OWNER,
                        session?.currentRegisterSessionId,
                        session?.userId
                    )
                }
                .distinctUntilChanged()
                .collect {
                    loadHistory()
                }
        }
    }

    fun loadHistory() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val session = sessionManager.currentUser
            val isOwner = session?.role == UserRole.OWNER
            val sessionId = if (isOwner) null else session?.currentRegisterSessionId

            if (!isOwner && sessionId == null) {
                allEntries = emptyList()
                filteredEntries = emptyList()
                _uiState.update {
                    it.copy(
                        entries = emptyList(),
                        filteredCount = 0,
                        hasMore = false,
                        isOwner = false,
                        isLoading = false,
                        errorMessage = "Ouvrez une session de caisse pour consulter vos ventes."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    isOwner = isOwner,
                    errorMessage = null,
                    exportSuccessMessage = null
                )
            }
            try {
                val state = _uiState.value
                allEntries = getSalesHistoryUseCase(
                    status = state.status,
                    startMs = startOfDay(state.startDateMs),
                    endMs = endOfDay(state.endDateMs),
                    sessionId = sessionId
                )
                visibleLimit = PAGE_SIZE
                publishFilteredEntries()
                _uiState.update { it.copy(isLoading = false) }
            } catch (error: Exception) {
                allEntries = emptyList()
                filteredEntries = emptyList()
                _uiState.update {
                    it.copy(
                        entries = emptyList(),
                        filteredCount = 0,
                        hasMore = false,
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Erreur de chargement de l'historique."
                    )
                }
            }
        }
    }

    fun selectStatus(status: OrderStatus) {
        if (status == OrderStatus.OPEN || status == _uiState.value.status) return
        _uiState.update {
            it.copy(
                status = status,
                selectedPaymentMethod = null,
                selectedOrderType = null,
                selectedCashierId = null,
                selectedRegisterId = null
            )
        }
        loadHistory()
    }

    fun selectStartDate(dateMs: Long) {
        val normalized = startOfDay(dateMs)
        _uiState.update {
            it.copy(
                startDateMs = normalized,
                endDateMs = if (normalized > it.endDateMs) endOfDay(normalized) else it.endDateMs
            )
        }
        loadHistory()
    }

    fun selectEndDate(dateMs: Long) {
        val normalized = endOfDay(dateMs)
        _uiState.update {
            it.copy(
                startDateMs = if (normalized < it.startDateMs) startOfDay(normalized) else it.startDateMs,
                endDateMs = normalized
            )
        }
        loadHistory()
    }

    fun selectCurrentMonth() {
        _uiState.update {
            it.copy(
                startDateMs = currentMonthStart(),
                endDateMs = endOfDay(System.currentTimeMillis())
            )
        }
        loadHistory()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        resetPageAndFilter()
    }

    fun selectCashier(cashierId: Long?) {
        _uiState.update { it.copy(selectedCashierId = cashierId) }
        resetPageAndFilter()
    }

    fun selectRegister(registerId: Long?) {
        _uiState.update { it.copy(selectedRegisterId = registerId) }
        resetPageAndFilter()
    }

    fun selectPaymentMethod(method: PaymentMethod?) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
        resetPageAndFilter()
    }

    fun selectOrderType(type: OrderType?) {
        _uiState.update { it.copy(selectedOrderType = type) }
        resetPageAndFilter()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedCashierId = null,
                selectedRegisterId = null,
                selectedPaymentMethod = null,
                selectedOrderType = null
            )
        }
        resetPageAndFilter()
    }

    fun loadMore() {
        if (_uiState.value.hasMore) {
            visibleLimit += PAGE_SIZE
            publishFilteredEntries()
        }
    }

    /**
     * Takes ownership of [outputStream] and closes it after writing.
     */
    fun exportCsv(outputStream: OutputStream) {
        val entriesSnapshot = filteredEntries
        _uiState.update {
            it.copy(
                isExporting = true,
                errorMessage = null,
                exportSuccessMessage = null
            )
        }
        viewModelScope.launch {
            try {
                outputStream.use { exportSalesHistoryCsvUseCase(it, entriesSnapshot) }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "${entriesSnapshot.size} vente(s) exportée(s)."
                    )
                }
            } catch (error: Exception) {
                runCatching { outputStream.close() }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Échec de l'export CSV : ${
                            error.localizedMessage ?: "erreur inconnue"
                        }"
                    )
                }
            }
        }
    }

    fun reportExportError(message: String) {
        _uiState.update {
            it.copy(
                isExporting = false,
                errorMessage = message,
                exportSuccessMessage = null
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
    }

    private fun resetPageAndFilter() {
        visibleLimit = PAGE_SIZE
        publishFilteredEntries()
    }

    private fun publishFilteredEntries() {
        val state = _uiState.value
        val query = state.searchQuery.trim()

        filteredEntries = allEntries.asSequence()
            .filter { entry ->
                query.isBlank() ||
                    entry.order.orderNumber.contains(query, ignoreCase = true) ||
                    entry.cashierName.contains(query, ignoreCase = true) ||
                    entry.registerName.contains(query, ignoreCase = true)
            }
            .filter { state.selectedCashierId == null || it.order.cashierId == state.selectedCashierId }
            .filter { state.selectedRegisterId == null || it.registerId == state.selectedRegisterId }
            .filter { state.selectedOrderType == null || it.order.type == state.selectedOrderType }
            .filter { entry ->
                state.selectedPaymentMethod == null ||
                    entry.order.payments.any { payment ->
                        payment.status == PaymentStatus.COMPLETED &&
                            payment.method == state.selectedPaymentMethod
                    }
            }
            .sortedByDescending { entry ->
                if (entry.order.status == OrderStatus.CANCELLED) {
                    entry.order.cancelledAt ?: entry.order.updatedAt
                } else {
                    entry.order.completedAt ?: entry.order.updatedAt
                }
            }
            .toList()

        val completedEntries = filteredEntries.filter {
            it.order.status == OrderStatus.COMPLETED
        }
        val total = completedEntries.sumOf { it.order.totalCentimes }
        val cash = completedEntries.sumOf { entry ->
            entry.order.payments
                .filter {
                    it.status == PaymentStatus.COMPLETED &&
                        it.method == PaymentMethod.CASH
                }
                .sumOf { it.amountCentimes }
        }
        val card = completedEntries.sumOf { entry ->
            entry.order.payments
                .filter {
                    it.status == PaymentStatus.COMPLETED &&
                        it.method == PaymentMethod.CARD
                }
                .sumOf { it.amountCentimes }
        }
        val summary = SalesHistorySummary(
            orderCount = filteredEntries.size,
            totalCentimes = if (state.status == OrderStatus.COMPLETED) {
                total
            } else {
                filteredEntries.sumOf { it.order.totalCentimes }
            },
            cashCentimes = cash,
            cardCentimes = card,
            averageCentimes = if (completedEntries.isEmpty()) 0L else total / completedEntries.size
        )
        val cashierOptions = allEntries
            .distinctBy { it.order.cashierId }
            .map { SalesHistoryOption(it.order.cashierId, it.cashierName) }
            .sortedBy { it.label.lowercase() }
        val registerOptions = allEntries
            .mapNotNull { entry ->
                entry.registerId?.let { SalesHistoryOption(it, entry.registerName) }
            }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }

        _uiState.update {
            it.copy(
                entries = filteredEntries.take(visibleLimit),
                filteredCount = filteredEntries.size,
                hasMore = visibleLimit < filteredEntries.size,
                cashierOptions = cashierOptions,
                registerOptions = registerOptions,
                summary = summary
            )
        }
    }

    companion object {
        private const val PAGE_SIZE = 40
    }
}

private fun currentMonthStart(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDay(dateMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dateMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(dateMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dateMs
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis
