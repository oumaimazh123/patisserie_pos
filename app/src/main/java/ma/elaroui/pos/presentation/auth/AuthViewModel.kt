package ma.elaroui.pos.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.usecase.AccountLockedException
import ma.elaroui.pos.domain.usecase.AuthenticateUserUseCase
import javax.inject.Inject

data class AuthUiState(
    val activeUsers: List<User> = emptyList(),
    val selectedUser: User? = null,
    val enteredPin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lockRemainingSeconds: Int = 0,
    val authenticatedUser: User? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authenticateUserUseCase: AuthenticateUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadActiveUsers()
    }

    fun loadActiveUsers() {
        viewModelScope.launch {
            userRepository.getAllActiveUsers().collect { list ->
                _uiState.update { it.copy(activeUsers = list) }
            }
        }
    }

    fun selectUser(user: User) {
        _uiState.update {
            it.copy(
                selectedUser = user,
                enteredPin = "",
                errorMessage = null,
                lockRemainingSeconds = 0
            )
        }
    }

    fun deselectUser() {
        _uiState.update {
            it.copy(
                selectedUser = null,
                enteredPin = "",
                errorMessage = null,
                lockRemainingSeconds = 0
            )
        }
    }

    fun appendDigit(digit: String) {
        val state = _uiState.value
        if (state.enteredPin.length < 6 && state.lockRemainingSeconds == 0) {
            val newPin = state.enteredPin + digit
            _uiState.update { it.copy(enteredPin = newPin, errorMessage = null) }
            // Auto submit when length is 4 or 6
            if (newPin.length >= 4 && state.selectedUser != null) {
                // If max length or 4 digit attempt
            }
        }
    }

    fun deleteDigit() {
        _uiState.update {
            if (it.enteredPin.isNotEmpty()) {
                it.copy(enteredPin = it.enteredPin.dropLast(1), errorMessage = null)
            } else it
        }
    }

    fun clearPin() {
        _uiState.update { it.copy(enteredPin = "", errorMessage = null) }
    }

    fun submitPin(onSuccess: (User) -> Unit) {
        val state = _uiState.value
        val user = state.selectedUser ?: return
        if (state.enteredPin.isBlank() || state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val authenticated = authenticateUserUseCase(user.id, state.enteredPin)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedUser = authenticated,
                        enteredPin = ""
                    )
                }
                onSuccess(authenticated)
            } catch (e: AccountLockedException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        enteredPin = "",
                        lockRemainingSeconds = e.remainingSeconds,
                        errorMessage = e.message
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        enteredPin = "",
                        errorMessage = e.message ?: "Code PIN incorrect."
                    )
                }
            }
        }
    }
}
