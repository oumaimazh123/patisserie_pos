package ma.elaroui.pos.domain.license

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ma.elaroui.pos.core.license.*
import ma.elaroui.pos.data.local.preferences.LicensePreferences
import org.json.JSONObject
import android.util.Base64 as AndroidBase64
import java.util.Base64 as JavaBase64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LicenseManager @Inject constructor(
    private val installationIdManager: InstallationIdManager,
    private val licensePreferences: LicensePreferences,
    private val licenseVerifier: LicenseVerifier
) {
    private val _licenseState = MutableStateFlow(computeCurrentState())
    open val licenseState: StateFlow<LicenseState> = _licenseState.asStateFlow()

    fun getInstallationId(): String = installationIdManager.getInstallationId()

    open fun refreshLicenseState(): LicenseState {
        val newState = computeCurrentState()
        _licenseState.value = newState
        return newState
    }

    open fun generateRequestCode(): String {
        val instId = getInstallationId()
        val jsonStr = "{\"appId\":\"${LicenseVerifier.EXPECTED_APP_ID}\",\"productId\":\"${LicenseVerifier.EXPECTED_PRODUCT_ID}\",\"version\":\"1.0\",\"installationId\":\"$instId\"}"
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        return encodeBase64(bytes)
    }

    open fun activateLicense(rawLicenseKeyString: String): LicenseState {
        val instId = getInstallationId()
        val now = System.currentTimeMillis()

        val (status, payload) = licenseVerifier.verifyLicenseString(
            rawLicenseString = rawLicenseKeyString,
            currentInstallationId = instId,
            currentTimeMs = now
        )

        if (status == LicenseStatus.LICENCE_VALID) {
            licensePreferences.activatedLicenseString = rawLicenseKeyString.trim()
            licensePreferences.lastTrustedTimestamp = now
            return refreshLicenseState()
        }

        return LicenseState(
            status = status,
            installationId = instId,
            customerName = payload?.customerName,
            issueDateMs = payload?.issueDateMs,
            expirationDateMs = payload?.expirationDateMs,
            licenseType = payload?.licenseType,
            errorMessage = when (status) {
                LicenseStatus.LICENCE_EXPIRED ->
                    "Cette licence a expiré."
                LicenseStatus.LICENCE_WRONG_DEVICE ->
                    "Cette licence appartient à une autre installation."
                else ->
                    "Clé de licence invalide ou signature incorrecte."
            }
        )
    }

    open fun clearLicense() {
        licensePreferences.clearLicense()
        refreshLicenseState()
    }

    private fun computeCurrentState(): LicenseState {
        val instId = getInstallationId()
        val now = System.currentTimeMillis()

        // 1. Clock Rollback Check
        val lastTrusted = licensePreferences.lastTrustedTimestamp
        if (lastTrusted > 0 && now < (lastTrusted - LicenseVerifier.ROLLBACK_TOLERANCE_MS)) {
            return LicenseState(
                status = LicenseStatus.CLOCK_ROLLBACK_DETECTED,
                installationId = instId,
                errorMessage = "Détection d'un recul de l'horloge système. Veuillez corriger l'heure et la date de l'appareil."
            )
        }

        // Update trusted timestamp if moving forward
        if (now > lastTrusted) {
            licensePreferences.lastTrustedTimestamp = now
        }

        // 2. Check Activated License (takes priority over trial)
        val rawLicense = licensePreferences.activatedLicenseString
        if (!rawLicense.isNullOrBlank()) {
            val (status, payload) = licenseVerifier.verifyLicenseString(
                rawLicenseString = rawLicense,
                currentInstallationId = instId,
                currentTimeMs = now
            )

            if (payload != null) {
                return LicenseState(
                    status = status,
                    installationId = instId,
                    customerName = payload.customerName,
                    remainingTrialTimeMs = 0L,
                    issueDateMs = payload.issueDateMs,
                    expirationDateMs = payload.expirationDateMs,
                    licenseType = payload.licenseType,
                    errorMessage = when (status) {
                        LicenseStatus.LICENCE_EXPIRED -> "Votre licence d'abonnement a expiré."
                        LicenseStatus.LICENCE_INVALID -> "Clé de licence invalide ou corrompue."
                        LicenseStatus.LICENCE_WRONG_DEVICE -> "Cette licence appartient à une autre installation."
                        else -> null
                    }
                )
            }
            return LicenseState(
                status = LicenseStatus.LICENCE_INVALID,
                installationId = instId,
                remainingTrialTimeMs = 0L,
                errorMessage = "La licence enregistrée est invalide ou corrompue."
            )
        }

        // 3. Fall back to the seven-day demo. Only license preferences are read or
        // written here; expiry never clears the Room database or other app data.
        var firstLaunch = licensePreferences.firstLaunchTimestamp
        if (firstLaunch == 0L) {
            firstLaunch = now
            licensePreferences.firstLaunchTimestamp = now
        }

        val trialEndMs = firstLaunch + LicenseVerifier.TRIAL_DURATION_MS
        val remainingTrialMs = trialEndMs - now

        return if (remainingTrialMs <= 0) {
            LicenseState(
                status = LicenseStatus.TRIAL_EXPIRED,
                installationId = instId,
                remainingTrialTimeMs = 0L,
                errorMessage = "Votre période d'essai gratuite de 7 jours a expiré. Vos données sont conservées. Activez une licence pour continuer."
            )
        } else if (remainingTrialMs <= (48 * 60 * 60 * 1000L)) { // <= 48 hours remaining
            LicenseState(
                status = LicenseStatus.TRIAL_EXPIRING_SOON,
                installationId = instId,
                remainingTrialTimeMs = remainingTrialMs
            )
        } else {
            LicenseState(
                status = LicenseStatus.TRIAL_ACTIVE,
                installationId = instId,
                remainingTrialTimeMs = remainingTrialMs
            )
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            AndroidBase64.encodeToString(bytes, AndroidBase64.NO_WRAP or AndroidBase64.DEFAULT)
        } catch (_: Throwable) {
            JavaBase64.getEncoder().encodeToString(bytes)
        }
    }
}
