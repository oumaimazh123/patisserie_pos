package ma.elaroui.pos.desktop

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.UUID
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.shared.ApplicationDirectories
import ma.elaroui.pos.shared.ApplicationDirectoriesProvider
import ma.elaroui.pos.shared.ApplicationPreferences
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.FileMetadata
import ma.elaroui.pos.shared.FileSelectionRequest
import ma.elaroui.pos.shared.FileSelectionService
import ma.elaroui.pos.shared.FileSystemAccess
import ma.elaroui.pos.shared.LocaleInformation
import ma.elaroui.pos.shared.LocaleInformationProvider
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.PlatformInformation
import ma.elaroui.pos.shared.PlatformInformationProvider
import ma.elaroui.pos.shared.PlatformLogger
import ma.elaroui.pos.shared.PlatformPath
import ma.elaroui.pos.shared.PosPlatform
import ma.elaroui.pos.shared.RandomUuidGenerator
import ma.elaroui.pos.shared.SecureKeyValueStorage

class JvmClock : Clock {
    override fun now() = EpochMilliseconds(System.currentTimeMillis())
}

class JvmApplicationDirectoriesProvider(
    private val platform: DesktopPlatform = DesktopPlatform.detect(),
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: String = System.getProperty("user.home"),
    private val temporaryDirectory: String = System.getProperty("java.io.tmpdir")
) : ApplicationDirectoriesProvider {
    override fun directories(): ApplicationDirectories = DesktopApplicationPathsProvider(
        platform = platform,
        environment = environment,
        userHome = userHome,
        temporaryDirectory = temporaryDirectory
    ).directories()
}

class JvmFileSystemAccess : FileSystemAccess {
    override fun metadata(path: PlatformPath): FileMetadata? {
        val nioPath = Paths.get(path.value)
        if (!Files.exists(nioPath)) return null
        return FileMetadata(
            path = path,
            name = nioPath.fileName?.toString().orEmpty(),
            isDirectory = Files.isDirectory(nioPath),
            sizeBytes = if (Files.isRegularFile(nioPath)) Files.size(nioPath) else 0L,
            lastModified = Files.getLastModifiedTime(nioPath).toMillis().let(::EpochMilliseconds)
        )
    }

    override fun readBytes(path: PlatformPath): ByteArray = Files.readAllBytes(Paths.get(path.value))

    override fun writeBytes(path: PlatformPath, bytes: ByteArray) {
        val nioPath = Paths.get(path.value)
        nioPath.parent?.let(Files::createDirectories)
        Files.write(
            nioPath,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    override fun createDirectories(path: PlatformPath) {
        Files.createDirectories(Paths.get(path.value))
    }

    override fun delete(path: PlatformPath): Boolean = Files.deleteIfExists(Paths.get(path.value))
}

class JvmFileSelectionService : FileSelectionService {
    override suspend fun selectFile(request: FileSelectionRequest): PlatformPath? =
        choose(request, save = false)

    override suspend fun selectSaveDestination(request: FileSelectionRequest): PlatformPath? =
        choose(request, save = true)

    private fun choose(request: FileSelectionRequest, save: Boolean): PlatformPath? {
        val exts = request.allowedExtensions.map { it.removePrefix(".").lowercase() }
        val path = if (exts.any { it in setOf("png", "jpg", "jpeg", "webp") }) {
            NativeFileDialogs.selectImage(request.title)
        } else if (exts.contains("csv")) {
            NativeFileDialogs.selectCsv(request.title)
        } else if (exts.contains("db")) {
            NativeFileDialogs.selectDb(request.title, save = save, defaultName = request.suggestedFileName ?: "pos-backup.db")
        } else {
            NativeFileDialogs.selectImage(request.title)
        }
        return path?.let { PlatformPath.parse(it.toAbsolutePath().toString()) }
    }
}

/**
 * Phase-3 foundation only. These stores are intentionally in-memory and are
 * not desktop production persistence or licensing implementations.
 */
class InMemoryApplicationPreferences : ApplicationPreferences {
    private val values = mutableMapOf<String, Any>()
    override fun getString(key: String, default: String) = values[key] as? String ?: default
    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putString(key: String, value: String) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun contains(key: String) = key in values
}

class InMemorySecureKeyValueStorage : SecureKeyValueStorage {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun contains(key: String) = key in values
}

class JvmPlatformLogger(
    private val logFile: java.nio.file.Path = DesktopApplicationPathsProvider().paths().logs.resolve("application.log"),
    private val maxSizeBytes: Long = 5 * 1024 * 1024L,
    private val maxBackupFiles: Int = 3
) : PlatformLogger {
    private val writeLock = Any()

    private fun rotateIfNeeded() {
        if (!Files.exists(logFile) || Files.size(logFile) < maxSizeBytes) return
        runCatching {
            for (i in maxBackupFiles - 1 downTo 1) {
                val src = logFile.resolveSibling("${logFile.fileName}.$i")
                val dst = logFile.resolveSibling("${logFile.fileName}.${i + 1}")
                if (Files.exists(src)) {
                    Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            val firstBackup = logFile.resolveSibling("${logFile.fileName}.1")
            Files.move(logFile, firstBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun log(entry: LogEntry) {
        val failure = entry.throwableMessage?.let { " | $it" }.orEmpty()
        val line = "${Instant.now()} [${entry.level}] [${entry.category}] ${entry.message}$failure"
        println(line)
        runCatching {
            synchronized(writeLock) {
                logFile.parent?.let(Files::createDirectories)
                rotateIfNeeded()
                Files.writeString(
                    logFile,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            }
        }.onFailure { System.err.println("Unable to write application log: ${it.message}") }
    }
}

class JvmPlatformInformationProvider(
    private val osName: () -> String = { System.getProperty("os.name", "") },
    private val osVersion: () -> String = { System.getProperty("os.version", "Unknown") },
    private val architecture: () -> String = { System.getProperty("os.arch", "Unknown") }
) : PlatformInformationProvider {
    override fun current() = PlatformInformation(
        platform = when (DesktopPlatform.detect(osName())) {
            DesktopPlatform.WINDOWS -> PosPlatform.WINDOWS
            DesktopPlatform.LINUX -> PosPlatform.LINUX
            DesktopPlatform.UNSUPPORTED -> PosPlatform.UNKNOWN
        },
        platformName = osName().ifBlank { "Unknown" },
        operatingSystemVersion = osVersion(),
        architecture = architecture()
    )
}

class JvmRandomUuidGenerator : RandomUuidGenerator {
    override fun randomUuid(): String = UUID.randomUUID().toString()
}

class JvmLocaleInformationProvider(
    private val locale: () -> Locale = { Locale.getDefault() }
) : LocaleInformationProvider {
    override fun current(): LocaleInformation {
        val value = locale()
        val language = value.language.ifBlank { "und" }
        return LocaleInformation(
            languageTag = value.toLanguageTag(),
            language = language,
            country = value.country.takeIf { it.isNotBlank() },
            isRightToLeft = language in setOf("ar", "fa", "he", "ur")
        )
    }
}
