package ma.elaroui.pos.desktop.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * High-security administrative deletion credential manager.
 * Uses PBKDF2WithHmacSHA256 with 100,000 iterations and per-installation cryptographic salt.
 * Ensures the raw password is never logged, stored in plain text, or exposed in backups.
 */
object AdminDeletionSecurity {
    const val MIN_PASSWORD_LENGTH = 8
    const val DEFAULT_PASSWORD = "admin1234"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, saltHex: String): String {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Le mot de passe d'administration doit comporter au moins $MIN_PASSWORD_LENGTH caractères."
        }
        require(saltHex.isNotBlank() && saltHex.length % 2 == 0) {
            "Sel cryptographique invalide."
        }
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, storedHash: String?, saltHex: String?): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH || storedHash.isNullOrBlank() || saltHex.isNullOrBlank()) {
            return false
        }
        return try {
            val computed = hashPassword(password, saltHex)
            MessageDigest.isEqual(
                computed.toByteArray(StandardCharsets.UTF_8),
                storedHash.toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: Throwable) {
            false
        }
    }
}
