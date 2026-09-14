package ma.elaroui.pos.shared

enum class PosPlatform { ANDROID, WINDOWS, LINUX, UNKNOWN }

data class ApplicationIdentity(
    val applicationId: String,
    val productId: String,
    val platform: PosPlatform,
    val versionName: String
)

data class EpochMilliseconds(val value: Long)

interface Clock {
    fun now(): EpochMilliseconds
}

interface ApplicationPreferences {
    fun getString(key: String, default: String = ""): String
    fun getLong(key: String, default: Long = 0L): Long
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

/**
 * Storage for secrets or device-bound values. Implementations, not common
 * code, determine the platform protection mechanism.
 */
interface SecureKeyValueStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

@JvmInline
value class PlatformPath private constructor(val value: String) {
    companion object {
        fun parse(value: String): PlatformPath {
            require(value.isNotBlank()) { "Path cannot be blank." }
            require('\u0000' !in value) { "Path cannot contain NUL." }
            return PlatformPath(value)
        }
    }
}

data class FileMetadata(
    val path: PlatformPath,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: EpochMilliseconds?
)

data class FileSelectionRequest(
    val title: String,
    val allowedExtensions: Set<String> = emptySet(),
    val suggestedFileName: String? = null
)

interface FileSelectionService {
    suspend fun selectFile(request: FileSelectionRequest): PlatformPath?
    suspend fun selectSaveDestination(request: FileSelectionRequest): PlatformPath?
}

interface FileSystemAccess {
    fun metadata(path: PlatformPath): FileMetadata?
    fun readBytes(path: PlatformPath): ByteArray
    fun writeBytes(path: PlatformPath, bytes: ByteArray)
    fun createDirectories(path: PlatformPath)
    fun delete(path: PlatformPath): Boolean
}

data class ApplicationDirectories(
    val data: PlatformPath,
    val cache: PlatformPath,
    val temporary: PlatformPath
)

interface ApplicationDirectoriesProvider {
    fun directories(): ApplicationDirectories
}

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val category: String = "POS",
    val throwableMessage: String? = null
)

interface PlatformLogger {
    fun log(entry: LogEntry)
    fun debug(message: String, category: String = "POS") =
        log(LogEntry(LogLevel.DEBUG, message, category))
    fun info(message: String, category: String = "POS") =
        log(LogEntry(LogLevel.INFO, message, category))
    fun warning(message: String, category: String = "POS") =
        log(LogEntry(LogLevel.WARNING, message, category))
    fun error(message: String, cause: Throwable? = null, category: String = "POS") =
        log(LogEntry(LogLevel.ERROR, message, category, cause?.message))
}

data class PlatformInformation(
    val platform: PosPlatform,
    val platformName: String,
    val operatingSystemVersion: String,
    val architecture: String,
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null
)

interface PlatformInformationProvider {
    fun current(): PlatformInformation
}

interface RandomUuidGenerator {
    fun randomUuid(): String
}

data class LocaleInformation(
    val languageTag: String,
    val language: String,
    val country: String?,
    val isRightToLeft: Boolean
)

interface LocaleInformationProvider {
    fun current(): LocaleInformation
}

interface InstallationIdProvider {
    fun getOrCreateInstallationId(): String
}

/**
 * Aggregation used by diagnostics and future application services. It contains
 * no persistence, licensing, printing, or POS workflow.
 */
data class PlatformServices(
    val clock: Clock,
    val preferences: ApplicationPreferences,
    val secureStorage: SecureKeyValueStorage,
    val fileSelection: FileSelectionService,
    val fileSystem: FileSystemAccess,
    val directories: ApplicationDirectoriesProvider,
    val logger: PlatformLogger,
    val platformInformation: PlatformInformationProvider,
    val uuidGenerator: RandomUuidGenerator,
    val localeInformation: LocaleInformationProvider
)

data class PlatformDiagnostic(
    val platform: PlatformInformation,
    val directories: ApplicationDirectories,
    val locale: LocaleInformation,
    val capturedAt: EpochMilliseconds,
    val correlationId: String
)

class CollectPlatformDiagnostic(
    private val clock: Clock,
    private val directories: ApplicationDirectoriesProvider,
    private val platformInformation: PlatformInformationProvider,
    private val localeInformation: LocaleInformationProvider,
    private val uuidGenerator: RandomUuidGenerator,
    private val logger: PlatformLogger
) {
    fun execute(): PlatformDiagnostic {
        val diagnostic = PlatformDiagnostic(
            platform = platformInformation.current(),
            directories = directories.directories(),
            locale = localeInformation.current(),
            capturedAt = clock.now(),
            correlationId = uuidGenerator.randomUuid()
        )
        logger.info("Platform diagnostic collected", "DesktopDiagnostic")
        return diagnostic
    }
}
