package ma.elaroui.pos.platform

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
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
import ma.elaroui.pos.shared.LogLevel
import ma.elaroui.pos.shared.PlatformInformation
import ma.elaroui.pos.shared.PlatformInformationProvider
import ma.elaroui.pos.shared.PlatformLogger
import ma.elaroui.pos.shared.PlatformPath
import ma.elaroui.pos.shared.PosPlatform
import ma.elaroui.pos.shared.RandomUuidGenerator
import ma.elaroui.pos.shared.SecureKeyValueStorage

class AndroidClock : Clock {
    override fun now() = EpochMilliseconds(System.currentTimeMillis())
}

/**
 * Adapter over an existing SharedPreferences file. It creates no new storage
 * policy and is not installed into Hilt during this phase.
 */
class AndroidPreferencesAdapter(
    private val delegate: SharedPreferences
) : ApplicationPreferences {
    override fun getString(key: String, default: String) = delegate.getString(key, default) ?: default
    override fun getLong(key: String, default: Long) = delegate.getLong(key, default)
    override fun getBoolean(key: String, default: Boolean) = delegate.getBoolean(key, default)
    override fun putString(key: String, value: String) { delegate.edit().putString(key, value).apply() }
    override fun putLong(key: String, value: Long) { delegate.edit().putLong(key, value).apply() }
    override fun putBoolean(key: String, value: Boolean) { delegate.edit().putBoolean(key, value).apply() }
    override fun remove(key: String) { delegate.edit().remove(key).apply() }
    override fun contains(key: String) = delegate.contains(key)
}

/**
 * Delegates to the storage selected by existing Android code. This adapter
 * intentionally does not replace licensing or introduce a new encryption path.
 */
class AndroidSecureStorageAdapter(
    private val delegate: SharedPreferences
) : SecureKeyValueStorage {
    override fun get(key: String) = delegate.getString(key, null)
    override fun put(key: String, value: String) { delegate.edit().putString(key, value).apply() }
    override fun remove(key: String) { delegate.edit().remove(key).apply() }
    override fun contains(key: String) = delegate.contains(key)
}

class AndroidApplicationDirectoriesProvider(
    private val context: Context
) : ApplicationDirectoriesProvider {
    override fun directories() = ApplicationDirectories(
        data = PlatformPath.parse(context.filesDir.absolutePath),
        cache = PlatformPath.parse(context.cacheDir.absolutePath),
        temporary = PlatformPath.parse(context.cacheDir.resolve("tmp").absolutePath)
    )
}

class AndroidFileSystemAccess : FileSystemAccess {
    override fun metadata(path: PlatformPath): FileMetadata? {
        val file = File(path.value)
        if (!file.exists()) return null
        return FileMetadata(
            path = path,
            name = file.name,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified().takeIf { it > 0 }?.let(::EpochMilliseconds)
        )
    }

    override fun readBytes(path: PlatformPath) = File(path.value).readBytes()
    override fun writeBytes(path: PlatformPath, bytes: ByteArray) {
        File(path.value).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }
    override fun createDirectories(path: PlatformPath) {
        check(File(path.value).mkdirs() || File(path.value).isDirectory) {
            "Unable to create directory."
        }
    }
    override fun delete(path: PlatformPath) = File(path.value).delete()
}

/**
 * Existing Activity/Compose launchers remain responsible for Android Uri
 * selection. Callbacks translate their result at the platform boundary.
 */
class AndroidFileSelectionAdapter(
    private val open: suspend (FileSelectionRequest) -> PlatformPath?,
    private val save: suspend (FileSelectionRequest) -> PlatformPath?
) : FileSelectionService {
    override suspend fun selectFile(request: FileSelectionRequest) = open(request)
    override suspend fun selectSaveDestination(request: FileSelectionRequest) = save(request)
}

class AndroidPlatformLogger : PlatformLogger {
    override fun log(entry: LogEntry) {
        val message = entry.throwableMessage?.let { "${entry.message}: $it" } ?: entry.message
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(entry.category, message)
            LogLevel.INFO -> Log.i(entry.category, message)
            LogLevel.WARNING -> Log.w(entry.category, message)
            LogLevel.ERROR -> Log.e(entry.category, message)
        }
    }
}

class AndroidPlatformInformationProvider : PlatformInformationProvider {
    override fun current() = PlatformInformation(
        platform = PosPlatform.ANDROID,
        platformName = "Android",
        operatingSystemVersion = Build.VERSION.RELEASE.orEmpty(),
        architecture = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        deviceManufacturer = Build.MANUFACTURER,
        deviceModel = Build.MODEL
    )
}

class AndroidRandomUuidGenerator : RandomUuidGenerator {
    override fun randomUuid(): String = UUID.randomUUID().toString()
}

class AndroidLocaleInformationProvider(
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
