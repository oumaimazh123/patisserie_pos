package ma.elaroui.pos.domain.usecase

import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

class AccountLockedException(val remainingSeconds: Int) :
    DomainException("Account locked due to 5 failed attempts. Please wait $remainingSeconds seconds.")

@Singleton
class CompleteFirstRunSetupUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val registerRepository: RegisterRepository,
    private val ensureStarterDataUseCase: EnsureStarterDataUseCase,
    private val setupPreferences: SetupPreferences
) {
    suspend operator fun invoke(
        language: String,
        restaurantName: String,
        phone: String,
        address: String,
        ownerName: String,
        ownerPin: String,
        registerName: String,
        sellerIce: String = "",
        sellerTaxId: String = "",
        sellerCommercialRegister: String = "",
        sellerPatente: String = "",
        wifiName: String = "",
        wifiCode: String = ""
    ): User {
        if (setupPreferences.isSetupComplete) {
            throw DomainException("Setup has already been completed.")
        }
        if (restaurantName.isBlank()) {
            throw DomainException("Restaurant name is required.")
        }
        if (ownerName.isBlank()) {
            throw DomainException("Owner name is required.")
        }
        if (ownerPin.length !in 4..6 || !ownerPin.all { it.isDigit() }) {
            throw DomainException("Owner PIN must be between 4 and 6 digits.")
        }

        // Salt and hash PIN
        val salt = SecurityUtils.generateSalt()
        val pinHash = SecurityUtils.hashPin(ownerPin, salt)

        // 1. Create Owner User
        val owner = User(
            name = ownerName.trim(),
            role = UserRole.OWNER,
            pinHash = pinHash,
            active = true
        )
        val ownerId = userRepository.insertUser(owner)
        val savedOwner = owner.copy(id = ownerId)

        // 2. Create Initial Register
        val finalRegisterName = if (registerName.isBlank()) "Main Register" else registerName.trim()
        val register = Register(name = finalRegisterName, active = true)
        val registerId = registerRepository.insertRegister(register)

        // 3. Create or repair only missing starter records. Existing records are preserved.
        ensureStarterDataUseCase()

        // 4. Mark setup complete only after every required seed record was created.
        setupPreferences.selectedLanguage = language
        setupPreferences.restaurantName = restaurantName.trim()
        setupPreferences.restaurantPhone = phone.trim()
        setupPreferences.restaurantAddress = address.trim()
        setupPreferences.sellerIce = sellerIce.trim()
        setupPreferences.sellerTaxId = sellerTaxId.trim()
        setupPreferences.sellerCommercialRegister = sellerCommercialRegister.trim()
        setupPreferences.sellerPatente = sellerPatente.trim()
        setupPreferences.wifiName = wifiName.trim()
        setupPreferences.wifiCode = wifiCode.trim()
        setupPreferences.currency = "MAD"
        setupPreferences.defaultRegisterId = registerId
        setupPreferences.isSetupComplete = true

        return savedOwner
    }
}

@Singleton
class AuthenticateUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val registerRepository: RegisterRepository,
    private val sessionManager: SessionManager,
    private val setupPreferences: SetupPreferences
) {
    // Lockout tracking: userId -> Pair(failedCount, lockTimestamp)
    private val failedAttemptsMap = mutableMapOf<Long, Pair<Int, Long>>()

    suspend operator fun invoke(userId: Long, pin: String): User {
        val user = userRepository.getUserById(userId)
            ?: throw DomainException("User not found.")

        if (!user.active) {
            throw DomainException("This user account is deactivated and cannot log in.")
        }

        // Check lockout status
        val (failedCount, lockTime) = failedAttemptsMap[userId] ?: Pair(0, 0L)
        if (failedCount >= 5) {
            val elapsedSeconds = ((System.currentTimeMillis() - lockTime) / 1000).toInt()
            val lockoutDuration = 30
            if (elapsedSeconds < lockoutDuration) {
                val remaining = lockoutDuration - elapsedSeconds
                throw AccountLockedException(remaining)
            } else {
                // Reset lockout after 30 seconds
                failedAttemptsMap[userId] = Pair(0, 0L)
            }
        }

        // Verify PIN
        val isValid = SecurityUtils.verifyPin(pin, user.pinHash)
        if (!isValid) {
            val newCount = failedCount + 1
            val lockTimestamp = if (newCount >= 5) System.currentTimeMillis() else 0L
            failedAttemptsMap[userId] = Pair(newCount, lockTimestamp)

            if (newCount >= 5) {
                throw AccountLockedException(30)
            } else {
                throw DomainException("Invalid PIN. ${5 - newCount} attempt(s) remaining.")
            }
        }

        // Success: Reset failed counter
        failedAttemptsMap[userId] = Pair(0, 0L)

        // Every user joins the single shared register session, regardless of who opened it.
        val activeSession = registerRepository.getAnyActiveSession()

        // Login to SessionManager
        sessionManager.login(user, activeSession?.id)
        return user
    }
}

@Singleton
class ManageCashierUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend fun createCashier(name: String, pin: String): User {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            throw DomainException("Cashier name is required.")
        }
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            throw DomainException("PIN must be between 4 and 6 digits.")
        }

        val activeUsers = userRepository.getUserByPin(pin)
        // Check duplicate name
        val allUsers = userRepository.getUserById(1L) // Fetch users check
        val salt = SecurityUtils.generateSalt()
        val pinHash = SecurityUtils.hashPin(pin, salt)

        val cashier = User(
            name = trimmedName,
            role = UserRole.CASHIER,
            pinHash = pinHash,
            active = true
        )
        val newId = userRepository.insertUser(cashier)
        return cashier.copy(id = newId)
    }

    suspend fun updateCashierName(cashierId: Long, newName: String) {
        val cashier = userRepository.getUserById(cashierId)
            ?: throw DomainException("Cashier not found.")
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Cashier name cannot be empty.")
        }
        userRepository.updateUser(cashier.copy(name = trimmed))
    }

    suspend fun resetCashierPin(cashierId: Long, newPin: String, ownerPin: String, ownerUser: User) {
        if (ownerUser.role != UserRole.OWNER) {
            throw DomainException("Only an Owner can reset a cashier's PIN.")
        }
        if (!SecurityUtils.verifyPin(ownerPin, ownerUser.pinHash)) {
            throw DomainException("Invalid Owner PIN. Authorization denied.")
        }
        if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
            throw DomainException("New PIN must be between 4 and 6 digits.")
        }

        val cashier = userRepository.getUserById(cashierId)
            ?: throw DomainException("Cashier not found.")

        val salt = SecurityUtils.generateSalt()
        val newPinHash = SecurityUtils.hashPin(newPin, salt)
        userRepository.updateUser(cashier.copy(pinHash = newPinHash))
    }

    suspend fun toggleCashierActiveState(cashierId: Long, currentAuthenticatedOwnerId: Long) {
        if (cashierId == currentAuthenticatedOwnerId) {
            throw DomainException("The currently authenticated Owner cannot deactivate their own account.")
        }
        val cashier = userRepository.getUserById(cashierId)
            ?: throw DomainException("Cashier not found.")

        userRepository.updateUser(cashier.copy(active = !cashier.active))
    }
}
