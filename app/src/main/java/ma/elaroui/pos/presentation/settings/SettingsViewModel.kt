package ma.elaroui.pos.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.PrinterSettings
import ma.elaroui.pos.domain.usecase.CreateBackupUseCase
import ma.elaroui.pos.domain.usecase.GetPrinterSettingsUseCase
import ma.elaroui.pos.domain.usecase.PrintReceiptUseCase
import ma.elaroui.pos.domain.usecase.RestoreBackupUseCase
import ma.elaroui.pos.domain.usecase.SavePrinterSettingsUseCase
import ma.elaroui.pos.domain.usecase.VerifyOwnerPinUseCase
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val restaurantName: String = "",
    val restaurantPhone: String = "",
    val restaurantAddress: String = "",
    val restaurantLogoUri: String = "",
    val sellerIce: String = "",
    val sellerTaxId: String = "",
    val sellerCommercialRegister: String = "",
    val sellerPatente: String = "",
    val wifiName: String = "",
    val wifiCode: String = "",
    val isBusinessDialogOpen: Boolean = false,
    val businessNameInput: String = "",
    val businessPhoneInput: String = "",
    val businessAddressInput: String = "",
    val businessLogoUriInput: String = "",
    val sellerIceInput: String = "",
    val sellerTaxIdInput: String = "",
    val sellerCommercialRegisterInput: String = "",
    val sellerPatenteInput: String = "",
    val wifiNameInput: String = "",
    val wifiCodeInput: String = "",
    val isCashOutPinConfigured: Boolean = false,
    val isCashOutPinDialogOpen: Boolean = false,
    val cashOutPinInput: String = "",
    val cashOutPinConfirmationInput: String = "",
    val language: String = "fr",
    val printerSettings: PrinterSettings = PrinterSettings(),
    val isLoading: Boolean = true,
    val isBackupPasswordRequired: Boolean = false,
    val isProcessing: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val setupPreferences: SetupPreferences,
    private val getPrinterSettingsUseCase: GetPrinterSettingsUseCase,
    private val savePrinterSettingsUseCase: SavePrinterSettingsUseCase,
    private val printReceiptUseCase: PrintReceiptUseCase,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val verifyOwnerPinUseCase: VerifyOwnerPinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val printer = getPrinterSettingsUseCase()
            _uiState.update {
                it.copy(
                    restaurantName = setupPreferences.restaurantName,
                    restaurantPhone = setupPreferences.restaurantPhone ?: "",
                    restaurantAddress = setupPreferences.restaurantAddress ?: "",
                    restaurantLogoUri = setupPreferences.restaurantLogoUri,
                    sellerIce = setupPreferences.sellerIce,
                    sellerTaxId = setupPreferences.sellerTaxId,
                    sellerCommercialRegister = setupPreferences.sellerCommercialRegister,
                    sellerPatente = setupPreferences.sellerPatente,
                    wifiName = setupPreferences.wifiName,
                    wifiCode = setupPreferences.wifiCode,
                    isCashOutPinConfigured = setupPreferences.cashOutApprovalPinHash.isNotBlank(),
                    language = setupPreferences.selectedLanguage,
                    printerSettings = printer,
                    isLoading = false
                )
            }
        }
    }

    fun openBusinessDialog() {
        _uiState.update {
            it.copy(
                isBusinessDialogOpen = true,
                businessNameInput = it.restaurantName,
                businessPhoneInput = it.restaurantPhone,
                businessAddressInput = it.restaurantAddress,
                businessLogoUriInput = it.restaurantLogoUri,
                sellerIceInput = it.sellerIce,
                sellerTaxIdInput = it.sellerTaxId,
                sellerCommercialRegisterInput = it.sellerCommercialRegister,
                sellerPatenteInput = it.sellerPatente,
                wifiNameInput = it.wifiName,
                wifiCodeInput = it.wifiCode,
                errorMessage = null
            )
        }
    }

    fun closeBusinessDialog() {
        _uiState.update { it.copy(isBusinessDialogOpen = false, errorMessage = null) }
    }

    fun updateBusinessForm(name: String, phone: String, address: String) {
        _uiState.update {
            it.copy(
                businessNameInput = name,
                businessPhoneInput = phone,
                businessAddressInput = address,
                errorMessage = null
            )
        }
    }

    fun updateBusinessLogo(uri: String?) {
        _uiState.update { it.copy(businessLogoUriInput = uri.orEmpty()) }
    }

    fun updateLegalAndWifiForm(
        sellerIce: String,
        sellerTaxId: String,
        sellerCommercialRegister: String,
        sellerPatente: String,
        wifiName: String,
        wifiCode: String
    ) {
        _uiState.update {
            it.copy(
                sellerIceInput = sellerIce,
                sellerTaxIdInput = sellerTaxId,
                sellerCommercialRegisterInput = sellerCommercialRegister,
                sellerPatenteInput = sellerPatente,
                wifiNameInput = wifiName,
                wifiCodeInput = wifiCode,
                errorMessage = null
            )
        }
    }

    fun saveBusinessInformation() {
        val state = _uiState.value
        val name = state.businessNameInput.trim()
        val phone = state.businessPhoneInput.trim()
        val address = state.businessAddressInput.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Le nom de l'établissement est obligatoire.") }
            return
        }
        if (phone.isNotBlank() && !phone.matches(Regex("[+0-9 ()-]{6,20}"))) {
            _uiState.update { it.copy(errorMessage = "Le numéro de téléphone est invalide.") }
            return
        }
        val sellerIce = state.sellerIceInput.trim()
        if (sellerIce.isNotBlank() && !sellerIce.matches(Regex("\\d{15}"))) {
            _uiState.update { it.copy(errorMessage = "L'ICE doit contenir exactement 15 chiffres.") }
            return
        }
        if (state.wifiNameInput.length > 64 || state.wifiCodeInput.length > 128) {
            _uiState.update { it.copy(errorMessage = "Le nom ou le code Wi-Fi est trop long.") }
            return
        }
        setupPreferences.restaurantName = name
        setupPreferences.restaurantPhone = phone
        setupPreferences.restaurantAddress = address
        setupPreferences.restaurantLogoUri = state.businessLogoUriInput
        setupPreferences.sellerIce = sellerIce
        setupPreferences.sellerTaxId = state.sellerTaxIdInput.trim()
        setupPreferences.sellerCommercialRegister = state.sellerCommercialRegisterInput.trim()
        setupPreferences.sellerPatente = state.sellerPatenteInput.trim()
        setupPreferences.wifiName = state.wifiNameInput.trim()
        setupPreferences.wifiCode = state.wifiCodeInput.trim()
        _uiState.update {
            it.copy(
                restaurantName = name,
                restaurantPhone = phone,
                restaurantAddress = address,
                restaurantLogoUri = state.businessLogoUriInput,
                sellerIce = sellerIce,
                sellerTaxId = state.sellerTaxIdInput.trim(),
                sellerCommercialRegister = state.sellerCommercialRegisterInput.trim(),
                sellerPatente = state.sellerPatenteInput.trim(),
                wifiName = state.wifiNameInput.trim(),
                wifiCode = state.wifiCodeInput.trim(),
                isBusinessDialogOpen = false,
                successMessage = "Informations de l'établissement enregistrées.",
                errorMessage = null
            )
        }
    }

    fun openCashOutPinDialog() {
        _uiState.update {
            it.copy(
                isCashOutPinDialogOpen = true,
                cashOutPinInput = "",
                cashOutPinConfirmationInput = "",
                errorMessage = null
            )
        }
    }

    fun closeCashOutPinDialog() {
        _uiState.update {
            it.copy(
                isCashOutPinDialogOpen = false,
                cashOutPinInput = "",
                cashOutPinConfirmationInput = "",
                errorMessage = null
            )
        }
    }

    fun updateCashOutPinForm(pin: String, confirmation: String) {
        if (
            pin.length <= 6 &&
            confirmation.length <= 6 &&
            pin.all(Char::isDigit) &&
            confirmation.all(Char::isDigit)
        ) {
            _uiState.update {
                it.copy(
                    cashOutPinInput = pin,
                    cashOutPinConfirmationInput = confirmation,
                    errorMessage = null
                )
            }
        }
    }

    fun saveCashOutApprovalPin() {
        val state = _uiState.value
        if (state.cashOutPinInput.length !in 4..6) {
            _uiState.update { it.copy(errorMessage = "Le PIN doit contenir entre 4 et 6 chiffres.") }
            return
        }
        if (state.cashOutPinInput != state.cashOutPinConfirmationInput) {
            _uiState.update { it.copy(errorMessage = "Les deux codes PIN ne correspondent pas.") }
            return
        }

        setupPreferences.cashOutApprovalPinHash = SecurityUtils.hashPin(
            state.cashOutPinInput,
            SecurityUtils.generateSalt()
        )
        _uiState.update {
            it.copy(
                isCashOutPinConfigured = true,
                isCashOutPinDialogOpen = false,
                cashOutPinInput = "",
                cashOutPinConfirmationInput = "",
                successMessage = "PIN partagé des sorties enregistré.",
                errorMessage = null
            )
        }
    }

    fun updatePrinterSettings(settings: PrinterSettings) {
        viewModelScope.launch {
            runCatching { savePrinterSettingsUseCase(settings) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            printerSettings = settings,
                            successMessage = "Configuration imprimante enregistrée.",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Configuration invalide.")
                    }
                }
        }
    }

    fun printTestReceipt(kitchen: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, successMessage = null) }
            val result = if (kitchen) {
                printReceiptUseCase.printKitchenTest(_uiState.value.restaurantName)
            } else {
                printReceiptUseCase.printTest(_uiState.value.restaurantName)
            }
            _uiState.update {
                when (result) {
                    is ma.elaroui.pos.core.print.PrintResult.Success -> it.copy(
                        isProcessing = false,
                        successMessage = "Impression de test envoyée !",
                        errorMessage = null
                    )
                    is ma.elaroui.pos.core.print.PrintResult.Failure -> it.copy(
                        isProcessing = false,
                        successMessage = null,
                        errorMessage = result.reason
                    )
                }
            }
        }
    }

    fun performCreateBackup(outputStream: OutputStream, ownerPin: String, password: String?) {
        viewModelScope.launch {
            outputStream.use { stream ->
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
                val owner = verifyOwnerPinUseCase(ownerPin)
                if (owner == null) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "PIN Propriétaire invalide.") }
                    return@launch
                }

                val result = createBackupUseCase(stream, password)
                if (result.isSuccess) {
                    _uiState.update { it.copy(isProcessing = false, successMessage = "Sauvegarde créée avec succès ✓") }
                } else {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Échec: ${result.exceptionOrNull()?.localizedMessage}") }
                }
            }
        }
    }

    fun performRestoreBackup(inputStream: InputStream, ownerPin: String, password: String?) {
        viewModelScope.launch {
            inputStream.use { stream ->
                _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
                val owner = verifyOwnerPinUseCase(ownerPin)
                if (owner == null) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "PIN Propriétaire invalide.") }
                    return@launch
                }

                val result = restoreBackupUseCase(stream, password)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Restauration terminée. Redémarrez l'application pour charger toutes les données."
                        )
                    }
                } else {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Échec de restauration: ${result.exceptionOrNull()?.localizedMessage}") }
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
