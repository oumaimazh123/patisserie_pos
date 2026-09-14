package ma.elaroui.pos.desktop.security

import ma.elaroui.pos.shared.rules.PinValidationRules
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure PIN credential manager for order cancellation and short PIN codes.
 * Uses PBKDF2WithHmacSHA256 with per-installation salt.
 * Enforces 4 to 6 numeric digits rule and prevents plain text exposure.
 */
object PinSecurity {
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        require(PinValidationRules.isValid(pin)) {
            "Le code PIN doit comporter 4 à 6 chiffres."
        }
        require(saltHex.isNotBlank() && saltHex.length % 2 == 0) {
            "Sel cryptographique invalide."
        }
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, storedHash: String?, saltHex: String?): Boolean {
        if (!PinValidationRules.isValid(pin) || storedHash.isNullOrBlank() || saltHex.isNullOrBlank()) {
            return false
        }
        return try {
            val computed = hashPin(pin, saltHex)
            MessageDigest.isEqual(
                computed.toByteArray(StandardCharsets.UTF_8),
                storedHash.toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: Throwable) {
            false
        }
    }
}
