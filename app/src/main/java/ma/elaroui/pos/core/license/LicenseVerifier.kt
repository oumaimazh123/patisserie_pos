package ma.elaroui.pos.core.license

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import android.util.Base64 as AndroidBase64
import java.util.Base64 as JavaBase64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LicenseVerifier @Inject constructor(
    @ApplicationContext private val context: Context?
) {
    var customPublicKey: PublicKey? = null

    open fun verifyLicenseString(
        rawLicenseString: String,
        currentInstallationId: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Pair<LicenseStatus, LicensePayload?> {
        val trimmed = rawLicenseString.trim()
        if (trimmed.isEmpty()) {
            return Pair(LicenseStatus.LICENCE_INVALID, null)
        }

        try {
            val (payloadJson, signatureBytes) = parseLicenseParts(trimmed)
                ?: return Pair(LicenseStatus.LICENCE_INVALID, null)

            val publicKey = customPublicKey ?: loadPublicKeyFromAssets()
                ?: return Pair(LicenseStatus.LICENCE_INVALID, null)

            val sig = Signature.getInstance(signatureAlgorithmForKey(publicKey))
            sig.initVerify(publicKey)
            sig.update(payloadJson.toByteArray(Charsets.UTF_8))

            val isSignatureValid = sig.verify(signatureBytes)
            if (!isSignatureValid) {
                return Pair(LicenseStatus.LICENCE_INVALID, null)
            }

            // Parse Payload JSON
            val payload = parsePayloadJson(payloadJson)
                ?: return Pair(LicenseStatus.LICENCE_INVALID, null)

            // Validate standard bounds
            if (payload.appId != EXPECTED_APP_ID || payload.productId != EXPECTED_PRODUCT_ID) {
                return Pair(LicenseStatus.LICENCE_INVALID, payload)
            }

            // Device Binding Check
            if (!payload.installationId.equals(currentInstallationId, ignoreCase = true)) {
                return Pair(LicenseStatus.LICENCE_WRONG_DEVICE, payload)
            }

            // Expiration Date Check
            if (payload.expirationDateMs != null && currentTimeMs > payload.expirationDateMs) {
                return Pair(LicenseStatus.LICENCE_EXPIRED, payload)
            }

            return Pair(LicenseStatus.LICENCE_VALID, payload)
        } catch (_: Throwable) {
            return Pair(LicenseStatus.LICENCE_INVALID, null)
        }
    }

    private fun parseLicenseParts(licenseStr: String): Pair<String, ByteArray>? {
        return try {
            if (licenseStr.startsWith("{")) {
                val payloadJson = extractJsonField(licenseStr, "payload")
                val sigB64 = extractJsonField(licenseStr, "signature")
                if (payloadJson != null && sigB64 != null) {
                    Pair(payloadJson, decodeBase64(sigB64))
                } else null
            } else if (licenseStr.contains(".")) {
                val parts = licenseStr.split(".")
                if (parts.size != 2) return null
                val payloadJson = String(decodeBase64(parts[0]), Charsets.UTF_8)
                val sigBytes = decodeBase64(parts[1])
                Pair(payloadJson, sigBytes)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parsePayloadJson(jsonStr: String): LicensePayload? {
        return try {
            val licenseId = extractJsonField(jsonStr, "licenseId") ?: return null
            val productId = extractJsonField(jsonStr, "productId") ?: EXPECTED_PRODUCT_ID
            val appId = extractJsonField(jsonStr, "appId") ?: EXPECTED_APP_ID
            val installationId = extractJsonField(jsonStr, "installationId") ?: return null
            val customerName = extractJsonField(jsonStr, "customerName") ?: "Client POS"
            val issueDateMs = extractJsonNumberField(jsonStr, "issueDateMs") ?: System.currentTimeMillis()
            val expirationDateMs = extractJsonNumberField(jsonStr, "expirationDateMs")
            val typeStr = extractJsonField(jsonStr, "licenseType") ?: "FULL_LIFETIME"
            val schemaVersion = extractJsonField(jsonStr, "schemaVersion") ?: "1.0"

            val licenseType = try { LicenseType.valueOf(typeStr) } catch (_: Throwable) { LicenseType.FULL_LIFETIME }

            LicensePayload(
                licenseId = licenseId,
                productId = productId,
                appId = appId,
                installationId = installationId,
                customerName = customerName,
                issueDateMs = issueDateMs,
                expirationDateMs = expirationDateMs,
                licenseType = licenseType,
                schemaVersion = schemaVersion
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractJsonField(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonNumberField(json: String, key: String): Long? {
        val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    open fun loadPublicKeyFromAssets(): PublicKey? {
        return try {
            val pemString = if (context != null) {
                val inputStream: InputStream = context.assets.open("public_key.pem")
                inputStream.bufferedReader().use { it.readText() }
            } else {
                FALLBACK_PUBLIC_KEY_PEM
            }
            parsePublicKeyFromPem(pemString)
        } catch (_: Throwable) {
            try { parsePublicKeyFromPem(FALLBACK_PUBLIC_KEY_PEM) } catch (_: Throwable) { null }
        }
    }

    fun parsePublicKeyFromPem(pem: String): PublicKey {
        val cleaned = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = decodeBase64(cleaned)
        val spec = X509EncodedKeySpec(keyBytes)
        return listOf("EC", "RSA").firstNotNullOfOrNull { algorithm ->
            runCatching {
                KeyFactory.getInstance(algorithm).generatePublic(spec)
            }.getOrNull()
        } ?: throw IllegalArgumentException("Unsupported or invalid public key.")
    }

    private fun signatureAlgorithmForKey(publicKey: PublicKey): String {
        return when (publicKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "SHA256withECDSA"
            "RSA" -> "SHA256withRSA"
            else -> throw IllegalArgumentException(
                "Unsupported license public-key algorithm: ${publicKey.algorithm}"
            )
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            AndroidBase64.decode(str, AndroidBase64.NO_WRAP or AndroidBase64.DEFAULT)
        } catch (_: Throwable) {
            JavaBase64.getDecoder().decode(str.trim())
        }
    }

    companion object {
        const val EXPECTED_APP_ID = "ma.elaroui.generalpos"
        const val EXPECTED_PRODUCT_ID = "GENERAL_POS_V1"
        const val TRIAL_DURATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days = 604,800,000 ms
        const val ROLLBACK_TOLERANCE_MS = 5 * 60 * 1000L // 5 minutes = 300,000 ms

        const val FALLBACK_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECVmpTz2FAi0yd0F8O4A7CkO/10qD
5HyZx/xT53ZaIIqBDnGv4FJlWZcDpBGPVVIJPD8q/neVHP5WoTFB1pCAmw==
-----END PUBLIC KEY-----"""
    }
}
