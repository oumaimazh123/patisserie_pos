package ma.elaroui.pos.core.license

import androidx.annotation.Keep

enum class LicenseType {
    FULL_LIFETIME,
    SUBSCRIPTION,
    TRIAL_EXTENSION
}

enum class LicenseStatus {
    TRIAL_ACTIVE,
    TRIAL_EXPIRING_SOON,
    TRIAL_EXPIRED,
    LICENCE_VALID,
    LICENCE_EXPIRED,
    LICENCE_INVALID,
    LICENCE_WRONG_DEVICE,
    CLOCK_ROLLBACK_DETECTED
}

@Keep
data class LicenseRequestPayload(
    val appId: String = "ma.elaroui.generalpos",
    val productId: String = "GENERAL_POS_V1",
    val version: String = "1.0",
    val installationId: String
)

@Keep
data class LicensePayload(
    val licenseId: String,
    val productId: String = "GENERAL_POS_V1",
    val appId: String = "ma.elaroui.generalpos",
    val installationId: String,
    val customerName: String,
    val issueDateMs: Long,
    val expirationDateMs: Long? = null,
    val licenseType: LicenseType = LicenseType.FULL_LIFETIME,
    val schemaVersion: String = "1.0"
)

data class LicenseState(
    val status: LicenseStatus,
    val installationId: String,
    val customerName: String? = null,
    val remainingTrialTimeMs: Long = 0L,
    val issueDateMs: Long? = null,
    val expirationDateMs: Long? = null,
    val licenseType: LicenseType? = null,
    val errorMessage: String? = null
)
