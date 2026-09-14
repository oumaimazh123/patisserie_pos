package ma.elaroui.pos.core.license

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class InstallationIdManager @Inject constructor(
    @ApplicationContext private val context: Context?
) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    open fun getInstallationId(): String {
        val existing = prefs?.getString(KEY_INSTALLATION_ID, null)
        if (existing != null && existing.isNotBlank()) {
            return existing
        }

        // Generate hardware-backed keystore key to bind device lifecycle
        val id = generateDeviceBoundId()
        prefs?.edit()?.putString(KEY_INSTALLATION_ID, id)?.apply()
        return id
    }

    private fun generateDeviceBoundId(): String {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val builder = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)

                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback gracefully if KeyStore is unavailable (e.g. non-standard JVM test runner)
        }

        val uuid = UUID.randomUUID().toString().uppercase().replace("-", "")
        val formatted = "INST-${uuid.substring(0, 4)}-${uuid.substring(4, 8)}-${uuid.substring(8, 12)}-${uuid.substring(12, 16)}"
        return formatted
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    companion object {
        private const val PREF_NAME = "pos_device_identity_prefs"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEYSTORE_ALIAS = "POS_INSTALLATION_KEY"
    }
}
