package ma.elaroui.pos.presentation.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.elaroui.pos.core.license.LicenseState
import ma.elaroui.pos.core.license.LicenseStatus
import ma.elaroui.pos.domain.license.LicenseManager
import javax.inject.Inject

data class LicenseUiState(
    val licenseState: LicenseState,
    val requestCode: String = "",
    val licenseKeyInput: String = "",
    val isActivating: Boolean = false,
    val userMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LicenseViewModel @Inject constructor(
    private val licenseManager: LicenseManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LicenseUiState(
            licenseState = licenseManager.licenseState.value,
            requestCode = licenseManager.generateRequestCode()
        )
    )
    val uiState: StateFlow<LicenseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            licenseManager.licenseState.collect { state ->
                _uiState.update { it.copy(licenseState = state) }
            }
        }
    }

    fun onLicenseKeyInputChanged(input: String) {
        _uiState.update { it.copy(licenseKeyInput = input, userMessage = null) }
    }

    fun copyRequestCodeToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("License Request Code", _uiState.value.requestCode)
            clipboard.setPrimaryClip(clip)
            _uiState.update { it.copy(userMessage = "Code de demande copié dans le presse-papiers ✓") }
        } catch (_: Exception) {
            _uiState.update { it.copy(userMessage = "Impossible de copier automatiquement.") }
        }
    }

    fun activateLicense() {
        val key = _uiState.value.licenseKeyInput.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(userMessage = "Veuillez saisir ou coller la clé de licence.") }
            return
        }

        _uiState.update { it.copy(isActivating = true, userMessage = null) }
        val resultState = licenseManager.activateLicense(key)
        _uiState.update {
            it.copy(
                isActivating = false,
                licenseState = resultState,
                isSuccess = resultState.status == LicenseStatus.LICENCE_VALID,
                userMessage = if (resultState.status == LicenseStatus.LICENCE_VALID) {
                    "Licence activée avec succès ! Merci de votre achat."
                } else {
                    resultState.errorMessage ?: "Échec d'activation de la licence."
                }
            )
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
