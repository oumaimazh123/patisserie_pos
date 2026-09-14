package ma.elaroui.pos.presentation.register.open

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.Register
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.GetActiveRegistersUseCase
import ma.elaroui.pos.domain.usecase.OpenRegisterSessionUseCase
import ma.elaroui.pos.presentation.register.moneyInputToCentimesOrNull
import ma.elaroui.pos.presentation.register.sanitizeMoneyInput
import javax.inject.Inject

data class OpenRegisterUiState(
    val activeRegisters: List<Register> = emptyList(),
    val selectedRegister: Register? = null,
    val openingCashInput: String = "0",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val openedSession: RegisterSession? = null
)

@HiltViewModel
class OpenRegisterViewModel @Inject constructor(
    private val getActiveRegistersUseCase: GetActiveRegistersUseCase,
    private val openRegisterSessionUseCase: OpenRegisterSessionUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpenRegisterUiState())
    val uiState: StateFlow<OpenRegisterUiState> = _uiState.asStateFlow()

    init {
        loadRegisters()
    }

    fun loadRegisters() {
        viewModelScope.launch {
            try {
                getActiveRegistersUseCase().collect { list ->
                    _uiState.update {
                        it.copy(
                            activeRegisters = list,
                            selectedRegister = it.selectedRegister
                                ?.takeIf { selected -> list.any { register -> register.id == selected.id } }
                                ?: list.firstOrNull(),
                            isLoading = false,
                            errorMessage = if (list.isEmpty()) {
                                "Aucune caisse active n'est disponible."
                            } else {
                                null
                            }
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: "Impossible de charger les caisses."
                    )
                }
            }
        }
    }

    fun selectRegister(register: Register) {
        _uiState.update { it.copy(selectedRegister = register) }
    }

    fun updateOpeningCashInput(input: String) {
        val sanitized = sanitizeMoneyInput(input) ?: return
        _uiState.update { it.copy(openingCashInput = sanitized, errorMessage = null) }
    }

    fun submitOpenRegister(onSuccess: () -> Unit) {
        val state = _uiState.value
        val register = state.selectedRegister ?: run {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une caisse.") }
            return
        }
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur non authentifiée.") }
            return
        }
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val centimes = moneyInputToCentimesOrNull(state.openingCashInput)
                    ?: throw IllegalArgumentException("Saisissez un montant valide avec au maximum 2 décimales.")

                val session = openRegisterSessionUseCase(
                    registerId = register.id,
                    openingCashCentimes = centimes,
                    authenticatedUser = currentUser
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        openedSession = session
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Échec d'ouverture de caisse."
                    )
                }
            }
        }
    }
}
