package ma.elaroui.pos.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.core.result.Resource
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.usecase.CompleteFirstRunSetupUseCase
import javax.inject.Inject

data class SetupUiState(
    val currentStep: Int = 1,
    val selectedLanguage: String = "fr",
    val restaurantName: String = "",
    val restaurantPhone: String = "",
    val restaurantAddress: String = "",
    val sellerIce: String = "",
    val sellerTaxId: String = "",
    val sellerCommercialRegister: String = "",
    val sellerPatente: String = "",
    val wifiName: String = "",
    val wifiCode: String = "",
    val ownerName: String = "",
    val ownerPin: String = "",
    val ownerPinConfirm: String = "",
    val registerName: String = "Main Register",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val setupResult: Resource<User>? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val completeFirstRunSetupUseCase: CompleteFirstRunSetupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateLanguage(language: String) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun updateRestaurantInfo(name: String, phone: String, address: String) {
        _uiState.update {
            it.copy(
                restaurantName = name,
                restaurantPhone = phone,
                restaurantAddress = address,
                errorMessage = null
            )
        }
    }

    fun updateLegalAndWifiInfo(
        sellerIce: String,
        sellerTaxId: String,
        sellerCommercialRegister: String,
        sellerPatente: String,
        wifiName: String,
        wifiCode: String
    ) {
        _uiState.update {
            it.copy(
                sellerIce = sellerIce,
                sellerTaxId = sellerTaxId,
                sellerCommercialRegister = sellerCommercialRegister,
                sellerPatente = sellerPatente,
                wifiName = wifiName,
                wifiCode = wifiCode,
                errorMessage = null
            )
        }
    }

    fun updateOwnerInfo(name: String, pin: String, pinConfirm: String) {
        _uiState.update {
            it.copy(
                ownerName = name,
                ownerPin = pin,
                ownerPinConfirm = pinConfirm,
                errorMessage = null
            )
        }
    }

    fun updateRegisterName(name: String) {
        _uiState.update { it.copy(registerName = name) }
    }

    fun nextStep() {
        val state = _uiState.value
        when (state.currentStep) {
            2 -> {
                // Validate language
            }
            3 -> {
                if (state.restaurantName.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Le nom du restaurant est obligatoire.") }
                    return
                }
            }
            4 -> {
                if (state.ownerName.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Le nom du propriétaire est obligatoire.") }
                    return
                }
                if (state.ownerPin.length !in 4..6 || !state.ownerPin.all { it.isDigit() }) {
                    _uiState.update { it.copy(errorMessage = "Le code PIN doit comporter 4 à 6 chiffres.") }
                    return
                }
                if (state.ownerPin != state.ownerPinConfirm) {
                    _uiState.update { it.copy(errorMessage = "Les codes PIN ne correspondent pas.") }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(
                currentStep = (it.currentStep + 1).coerceAtMost(6),
                errorMessage = null
            )
        }
    }

    fun previousStep() {
        _uiState.update {
            it.copy(
                currentStep = (it.currentStep - 1).coerceAtLeast(1),
                errorMessage = null
            )
        }
    }

    fun submitSetup(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val owner = completeFirstRunSetupUseCase(
                    language = state.selectedLanguage,
                    restaurantName = state.restaurantName,
                    phone = state.restaurantPhone,
                    address = state.restaurantAddress,
                    ownerName = state.ownerName,
                    ownerPin = state.ownerPin,
                    registerName = state.registerName,
                    sellerIce = state.sellerIce,
                    sellerTaxId = state.sellerTaxId,
                    sellerCommercialRegister = state.sellerCommercialRegister,
                    sellerPatente = state.sellerPatente,
                    wifiName = state.wifiName,
                    wifiCode = state.wifiCode
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        setupResult = Resource.Success(owner)
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.localizedMessage ?: "Erreur lors de la configuration."
                    )
                }
            }
        }
    }
}
