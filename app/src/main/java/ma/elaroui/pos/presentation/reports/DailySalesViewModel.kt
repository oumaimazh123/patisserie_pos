package ma.elaroui.pos.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.DailySalesReport
import ma.elaroui.pos.domain.usecase.ExportSalesCsvUseCase
import ma.elaroui.pos.domain.usecase.GetDailySalesReportUseCase
import java.io.OutputStream
import java.util.Calendar
import javax.inject.Inject

data class DailySalesUiState(
    val selectedDateMs: Long = System.currentTimeMillis(),
    val report: DailySalesReport? = null,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccessMessage: String? = null
)

@HiltViewModel
class DailySalesViewModel @Inject constructor(
    private val getDailySalesReportUseCase: GetDailySalesReportUseCase,
    private val exportSalesCsvUseCase: ExportSalesCsvUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailySalesUiState())
    val uiState: StateFlow<DailySalesUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadReport()
    }

    fun loadReport() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val report = getDailySalesReportUseCase(_uiState.value.selectedDateMs)
                _uiState.update { it.copy(report = report, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        report = null,
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur de chargement du rapport."
                    )
                }
            }
        }
    }

    fun selectDate(dateMs: Long) {
        val normalizedDate = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        _uiState.update {
            it.copy(
                selectedDateMs = normalizedDate,
                exportSuccessMessage = null,
                errorMessage = null
            )
        }
        loadReport()
    }

    fun selectPreviousDay() = moveSelectedDateBy(-1)

    fun selectNextDay() {
        val nextDay = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.selectedDateMs
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        if (startOfDay(nextDay) <= startOfDay(System.currentTimeMillis())) {
            selectDate(nextDay)
        }
    }

    fun selectToday() = selectDate(System.currentTimeMillis())

    /**
     * Takes ownership of [outputStream] and closes it after the background export.
     * The ActivityResult callback must not close the stream before this coroutine runs.
     */
    fun exportCsv(outputStream: OutputStream) {
        val selectedDate = _uiState.value.selectedDateMs
        _uiState.update {
            it.copy(
                isExporting = true,
                errorMessage = null,
                exportSuccessMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selectedDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startMs = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val endMs = calendar.timeInMillis - 1

                outputStream.use { stream ->
                    exportSalesCsvUseCase(stream, startMs, endMs)
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "Rapport CSV enregistré avec succès."
                    )
                }
            } catch (e: Exception) {
                runCatching { outputStream.close() }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Échec de l'exportation CSV : ${e.localizedMessage ?: "erreur inconnue"}"
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

    fun clearExportMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun moveSelectedDateBy(days: Int) {
        val date = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.selectedDateMs
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
        selectDate(date)
    }

    private fun startOfDay(dateMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dateMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
