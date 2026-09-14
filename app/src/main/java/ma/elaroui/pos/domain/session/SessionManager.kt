package ma.elaroui.pos.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import javax.inject.Inject
import javax.inject.Singleton

data class UserSession(
    val userId: Long,
    val userName: String,
    val role: UserRole,
    val loginTime: Long = System.currentTimeMillis(),
    val currentRegisterSessionId: Long? = null,
    val isLocked: Boolean = false
)

@Singleton
class SessionManager @Inject constructor() {

    private val _sessionState = MutableStateFlow<UserSession?>(null)
    val sessionState: StateFlow<UserSession?> = _sessionState.asStateFlow()

    val currentUser: UserSession? get() = _sessionState.value
    val isAuthenticated: Boolean get() = _sessionState.value != null

    fun login(user: User, registerSessionId: Long? = null) {
        _sessionState.value = UserSession(
            userId = user.id,
            userName = user.name,
            role = user.role,
            loginTime = System.currentTimeMillis(),
            currentRegisterSessionId = registerSessionId,
            isLocked = false
        )
    }

    fun updateRegisterSessionId(sessionId: Long?) {
        val current = _sessionState.value ?: return
        _sessionState.value = current.copy(currentRegisterSessionId = sessionId)
    }

    fun lock() {
        val current = _sessionState.value ?: return
        _sessionState.value = current.copy(isLocked = true)
    }

    fun unlock() {
        val current = _sessionState.value ?: return
        _sessionState.value = current.copy(isLocked = false)
    }

    fun logout() {
        // Clear sensitive state, preserve register session state in DB
        _sessionState.value = null
    }

    fun switchUser() {
        _sessionState.value = null
    }
}
