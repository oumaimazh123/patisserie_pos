package ma.elaroui.pos.presentation.register.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.RegisterSessionOperation
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.usecase.GetRegisterSessionHistoryUseCase
import ma.elaroui.pos.domain.usecase.GetRegisterSessionOperationsUseCase
import javax.inject.Inject

data class RegisterHistoryUiState(
    val sessions: List<RegisterSession> = emptyList(),
    val usersMap: Map<Long, User> = emptyMap(),
    val searchQuery: String = "",
    val cashierFilterId: Long? = null,
    val selectedSession: RegisterSession? = null,
    val selectedSessionOperations: List<RegisterSessionOperation> = emptyList(),
    val isLoading: Boolean = true,
    val isDetailLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RegisterHistoryViewModel @Inject constructor(
    private val getRegisterSessionHistoryUseCase: GetRegisterSessionHistoryUseCase,
    private val getRegisterSessionOperationsUseCase: GetRegisterSessionOperationsUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterHistoryUiState())
    val uiState: StateFlow<RegisterHistoryUiState> = _uiState.asStateFlow()
    private var allSessions: List<RegisterSession> = emptyList()
    private var historyJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadHistory()
    }

    fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                getRegisterSessionHistoryUseCase().collect { sessions ->
                    allSessions = sessions.sortedByDescending { it.openedAt }
                    val users = allSessions
                        .map { it.cashierId }
                        .distinct()
                        .mapNotNull { userRepository.getUserById(it) }
                        .associateBy { it.id }
                    _uiState.update {
                        it.copy(
                            usersMap = users,
                            isLoading = false
                        )
                    }
                    publishFilters()
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Impossible de charger l'historique des sessions."
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        publishFilters()
    }

    fun selectCashier(cashierId: Long?) {
        _uiState.update { it.copy(cashierFilterId = cashierId) }
        publishFilters()
    }

    fun selectSession(session: RegisterSession) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSession = session,
                    selectedSessionOperations = emptyList(),
                    isDetailLoading = true,
                    errorMessage = null
                )
            }
            try {
                val operations = getRegisterSessionOperationsUseCase(session.id)
                _uiState.update {
                    it.copy(
                        selectedSessionOperations = operations,
                        isDetailLoading = false
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Impossible de charger les mouvements."
                    )
                }
            }
        }
    }

    fun clearSelectedSession() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSession = null,
                selectedSessionOperations = emptyList(),
                isDetailLoading = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun publishFilters() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val filtered = allSessions.asSequence()
            .filter { state.cashierFilterId == null || it.cashierId == state.cashierFilterId }
            .filter { session ->
                query.isBlank() ||
                    session.id.toString().contains(query) ||
                    state.usersMap[session.cashierId]?.name
                        ?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.openedAt }
            .toList()

        _uiState.update {
            it.copy(
                sessions = filtered
            )
        }
    }
}
