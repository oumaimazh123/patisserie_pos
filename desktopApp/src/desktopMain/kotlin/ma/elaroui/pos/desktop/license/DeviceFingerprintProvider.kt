package ma.elaroui.pos.desktop.license

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.UUID
import ma.elaroui.pos.desktop.platform.DesktopPlatform

fun interface DeviceFingerprintProvider {
    fun getFingerprint(): String
}

class WindowsDeviceFingerprintProvider(private val storage: DesktopLicenseStore) : DeviceFingerprintProvider {
    override fun getFingerprint(): String = storage.get("installation_id")
        ?: "WIN-${UUID.randomUUID().toString().uppercase()}".also { storage.put("installation_id", it) }
}

interface LinuxMachineIdentifierReader {
    fun machineId(): String?
    fun productUuid(): String?
}

class LinuxSystemIdentifierReader : LinuxMachineIdentifierReader {
    override fun machineId(): String? = readFirstReadable(
        Path.of("/etc/machine-id"),
        Path.of("/var/lib/dbus/machine-id")
    )

    override fun productUuid(): String? = readFirstReadable(Path.of("/sys/class/dmi/id/product_uuid"))

    private fun readFirstReadable(vararg candidates: Path): String? = candidates.firstNotNullOfOrNull { path ->
        runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull()?.takeIf(String::isNotBlank)
    }
}

object DeviceFingerprintDerivation {
    fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), "")
        ?.takeIf { it.length >= 8 && it.all { character -> character.isLetterOrDigit() || character == '-' || character == '_' } }

    fun linux(machineId: String?, productUuid: String?, fallbackSeed: String): String {
        val components = buildList {
            normalize(machineId)?.let { add("machine-id=$it") }
            normalize(productUuid)?.let { add("product-uuid=$it") }
            if (isEmpty()) add("fallback=${requireNotNull(normalize(fallbackSeed)) { "Invalid fallback device seed" }}")
        }.sorted().joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(components.toByteArray(StandardCharsets.UTF_8))
        return "LINUX-${digest.joinToString("") { "%02X".format(it) }}"
    }
}

class LinuxDeviceFingerprintProvider(
    private val identifiers: LinuxMachineIdentifierReader = LinuxSystemIdentifierReader(),
    private val fallbackSeedFile: Path,
    private val randomUuid: () -> String = { UUID.randomUUID().toString() }
) : DeviceFingerprintProvider {
    override fun getFingerprint(): String {
        val machineId = identifiers.machineId()
        val productUuid = identifiers.productUuid()
        val fallback = if (DeviceFingerprintDerivation.normalize(machineId) == null &&
            DeviceFingerprintDerivation.normalize(productUuid) == null) loadOrCreateFallbackSeed() else "unused-fallback"
        return DeviceFingerprintDerivation.linux(machineId, productUuid, fallback)
    }

    private fun loadOrCreateFallbackSeed(): String {
        runCatching { Files.readString(fallbackSeedFile, StandardCharsets.UTF_8) }.getOrNull()
            ?.let(DeviceFingerprintDerivation::normalize)
            ?.let { return it }
        val seed = randomUuid()
        fallbackSeedFile.parent?.let(Files::createDirectories)
        Files.writeString(fallbackSeedFile, seed, StandardCharsets.UTF_8)
        runCatching {
            Files.setPosixFilePermissions(
                fallbackSeedFile,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }
        return seed
    }
}

object DesktopDeviceFingerprintProviderFactory {
    fun create(
        platform: DesktopPlatform,
        configurationDirectory: Path,
        windowsStorage: DesktopLicenseStore? = null
    ): DeviceFingerprintProvider = when (platform) {
        DesktopPlatform.WINDOWS -> WindowsDeviceFingerprintProvider(requireNotNull(windowsStorage))
        DesktopPlatform.LINUX -> LinuxDeviceFingerprintProvider(
            fallbackSeedFile = configurationDirectory.resolve("secure/device-id.seed")
        )
        DesktopPlatform.UNSUPPORTED -> DeviceFingerprintProvider {
            throw UnsupportedOperationException("Device fingerprint is unavailable on platform $platform")
        }
    }
}
