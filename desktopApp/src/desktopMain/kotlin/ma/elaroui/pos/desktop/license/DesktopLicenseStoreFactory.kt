package ma.elaroui.pos.desktop.license

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import ma.elaroui.pos.desktop.platform.DesktopApplicationPaths
import ma.elaroui.pos.desktop.platform.DesktopPlatform

data class DesktopLicensingComponents(
    val storage: DesktopLicenseStore,
    val fingerprintProvider: DeviceFingerprintProvider
) {
    fun manager(
        now: () -> Long = System::currentTimeMillis,
        publicKeyPem: String = WindowsLicenseManager.PUBLIC_KEY
    ) = WindowsLicenseManager(storage, now, publicKeyPem, fingerprintProvider)
}

object DesktopLicensingFactory {
    fun create(platform: DesktopPlatform, paths: DesktopApplicationPaths): DesktopLicensingComponents = when (platform) {
        DesktopPlatform.WINDOWS -> {
            val store = WindowsSecureStore(paths.licenseFile)
            DesktopLicensingComponents(store, WindowsDeviceFingerprintProvider(store))
        }
        DesktopPlatform.LINUX -> {
            val fingerprint = LinuxDeviceFingerprintProvider(
                fallbackSeedFile = paths.configuration.resolve("secure/device-id.seed")
            )
            val store = LinuxEncryptedLicenseStore(paths.licenseFile, fingerprint)
            DesktopLicensingComponents(store, fingerprint)
        }
        DesktopPlatform.UNSUPPORTED -> {
            val store = UnsupportedPlatformLicenseStore(platform)
            DesktopLicensingComponents(store, DesktopDeviceFingerprintProviderFactory.create(platform, paths.configuration))
        }
    }
}

/** Compatibility factory retained for tests and small call sites. */
object DesktopLicenseStoreFactory {
    fun create(platform: DesktopPlatform, file: Path): DesktopLicenseStore = when (platform) {
        DesktopPlatform.WINDOWS -> WindowsSecureStore(file)
        DesktopPlatform.LINUX -> {
            val configuration = file.parent?.parent ?: file.parent ?: Path.of(System.getProperty("user.home"))
            val fingerprint = LinuxDeviceFingerprintProvider(fallbackSeedFile = configuration.resolve("device-id.seed"))
            LinuxEncryptedLicenseStore(file, fingerprint)
        }
        DesktopPlatform.UNSUPPORTED -> UnsupportedPlatformLicenseStore(platform)
    }
}

class LicenseStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class LinuxEncryptedLicenseStore(
    private val file: Path,
    private val fingerprintProvider: DeviceFingerprintProvider,
    private val secureRandom: SecureRandom = SecureRandom()
) : DesktopLicenseStore {
    init { file.parent?.let(Files::createDirectories) }

    @Synchronized override fun get(key: String): String? = load()[key]

    @Synchronized override fun put(key: String, value: String) {
        val values = load().apply { this[key] = value }
        writeEncrypted(values)
    }

    private fun load(): MutableMap<String, String> {
        if (!Files.exists(file)) return mutableMapOf()
        val bytes = runCatching { Files.readAllBytes(file) }
            .getOrElse { throw LicenseStorageException("Unable to read Linux licence state", it) }
        if (bytes.isEmpty()) throw LicenseStorageException("Linux licence state is empty or corrupted")
        return if (bytes.startsWith(MAGIC)) decrypt(bytes) else parseLegacy(bytes)
    }

    private fun decrypt(bytes: ByteArray): MutableMap<String, String> = runCatching {
        require(bytes.size > MAGIC.size + NONCE_SIZE) { "Encrypted licence state is truncated" }
        val nonce = bytes.copyOfRange(MAGIC.size, MAGIC.size + NONCE_SIZE)
        val ciphertext = bytes.copyOfRange(MAGIC.size + NONCE_SIZE, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        parseText(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
    }.getOrElse { throw LicenseStorageException("Linux licence state is corrupted or belongs to another device", it) }

    /** Reads the Phase-1 0600/base64 format once; the next write upgrades it to AES-GCM. */
    private fun parseLegacy(bytes: ByteArray): MutableMap<String, String> = runCatching {
        parseText(String(bytes, StandardCharsets.UTF_8))
    }.getOrElse { throw LicenseStorageException("Legacy Linux licence state is corrupted", it) }

    private fun parseText(text: String): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        text.lineSequence().filter(String::isNotBlank).forEach { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed licence state entry" }
            val key = line.substring(0, separator)
            val value = String(Base64.getDecoder().decode(line.substring(separator + 1)), StandardCharsets.UTF_8)
            result[key] = value
        }
        require(result.isNotEmpty()) { "Licence state contains no values" }
        return result
    }

    private fun writeEncrypted(values: Map<String, String>) {
        val plain = values.entries.sortedBy { it.key }.joinToString("\n") {
            "${it.key}=${Base64.getEncoder().encodeToString(it.value.toByteArray(StandardCharsets.UTF_8))}"
        }.toByteArray(StandardCharsets.UTF_8)
        val nonce = ByteArray(NONCE_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        val output = MAGIC + nonce + cipher.doFinal(plain)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.write(temporary, output)
        restrictToCurrentUser(temporary)
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToCurrentUser(file)
    }

    private fun encryptionKey(): SecretKeySpec {
        val material = "$KEY_DOMAIN|${fingerprintProvider.getFingerprint()}"
        return SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    private fun restrictToCurrentUser(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size &&
        copyOfRange(0, prefix.size).contentEquals(prefix)

    companion object {
        private val MAGIC = "POSLIC2".toByteArray(StandardCharsets.US_ASCII)
        private const val NONCE_SIZE = 12
        private const val KEY_DOMAIN = "GeneralPOS/LinuxLicenseState/v1"
    }
}

private class UnsupportedPlatformLicenseStore(private val platform: DesktopPlatform) : DesktopLicenseStore {
    override fun get(key: String): String? = null
    override fun put(key: String, value: String): Unit = throw UnsupportedOperationException(
        "Persistent licence storage is not available for desktop platform $platform"
    )
}
