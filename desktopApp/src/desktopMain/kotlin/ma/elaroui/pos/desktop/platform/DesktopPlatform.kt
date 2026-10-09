package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import ma.elaroui.pos.shared.ApplicationDirectories
import ma.elaroui.pos.shared.ApplicationDirectoriesProvider
import ma.elaroui.pos.shared.PlatformPath

enum class DesktopPlatform {
    WINDOWS,
    LINUX,
    UNSUPPORTED;

    companion object {
        fun detect(osName: String = System.getProperty("os.name", "")): DesktopPlatform {
            val normalized = osName.trim().lowercase()
            return when {
                normalized.startsWith("windows") || normalized.contains("windows") -> WINDOWS
                normalized == "linux" || normalized.contains("linux") -> LINUX
                else -> UNSUPPORTED
            }
        }
    }
}

data class DesktopApplicationPaths(
    val root: Path,
    val data: Path,
    val database: Path,
    val backups: Path,
    val images: Path,
    val logs: Path,
    val configuration: Path,
    val cache: Path,
    val temporary: Path,
    val licenseFile: Path
) {
    fun createRequiredDirectories(): DesktopApplicationPaths = apply {
        listOf(data, database.parent, backups, images, logs, configuration, cache, temporary, licenseFile.parent)
            .filterNotNull()
            .forEach(Files::createDirectories)
        migrateLegacyDataIfPresent()
    }

    fun migrateLegacyDataIfPresent() {
        if (Files.exists(database) && Files.size(database) > 0L) {
            return
        }
        val parentDir = root.parent ?: return
        val legacyNames = when (DesktopPlatform.detect()) {
            DesktopPlatform.WINDOWS -> listOf("PatisseriePOS")
            DesktopPlatform.LINUX -> listOf("patisserie-pos")
            DesktopPlatform.UNSUPPORTED -> emptyList()
        }
        for (legacy in legacyNames) {
            val candidateRoot = parentDir.resolve(legacy)
            val candidateDb = candidateRoot.resolve("data").resolve("pos.db")
            if (Files.exists(candidateDb) && Files.size(candidateDb) > 0L) {
                runCatching {
                    Files.createDirectories(database.parent)
                    Files.copy(candidateDb, database, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    val wal = candidateRoot.resolve("data").resolve("pos.db-wal")
                    if (Files.exists(wal)) {
                        Files.copy(wal, database.resolveSibling("pos.db-wal"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    val shm = candidateRoot.resolve("data").resolve("pos.db-shm")
                    if (Files.exists(shm)) {
                        Files.copy(shm, database.resolveSibling("pos.db-shm"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    val candidateLicense = candidateRoot.resolve("data").resolve("secure").resolve("license.dat")
                    if (Files.exists(candidateLicense) && (!Files.exists(licenseFile) || Files.size(licenseFile) == 0L)) {
                        Files.createDirectories(licenseFile.parent)
                        Files.copy(candidateLicense, licenseFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    val candidateImages = candidateRoot.resolve("data").resolve("images")
                    if (Files.exists(candidateImages) && Files.isDirectory(candidateImages)) {
                        copyDirectoryRecursively(candidateImages, images)
                    }
                    val candidateBackups = candidateRoot.resolve("data").resolve("backups")
                    if (Files.exists(candidateBackups) && Files.isDirectory(candidateBackups)) {
                        copyDirectoryRecursively(candidateBackups, backups)
                    }
                }
                break
            }
        }
    }

    private fun copyDirectoryRecursively(source: Path, target: Path) {
        if (!Files.exists(source)) return
        Files.walk(source).use { stream ->
            stream.forEach { sourcePath ->
                val relative = source.relativize(sourcePath)
                val targetPath = target.resolve(relative)
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun asSharedDirectories() = ApplicationDirectories(
        data = PlatformPath.parse(data.toString()),
        cache = PlatformPath.parse(cache.toString()),
        temporary = PlatformPath.parse(temporary.toString())
    )
}

class DesktopApplicationPathsProvider(
    private val platform: DesktopPlatform = DesktopPlatform.detect(),
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: String = System.getProperty("user.home"),
    private val temporaryDirectory: String = System.getProperty("java.io.tmpdir")
) : ApplicationDirectoriesProvider {

    fun paths(createDirectories: Boolean = true): DesktopApplicationPaths {
        val home = Paths.get(userHome)
        val temp = Paths.get(temporaryDirectory, APP_DIRECTORY_NAME)
        val result = when (platform) {
            DesktopPlatform.WINDOWS -> {
                val localAppData = environment["LOCALAPPDATA"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(Paths::get)
                    ?: home.resolve("AppData").resolve("Local")
                val root = localAppData.resolve(APP_DIRECTORY_NAME)
                windowsPaths(root, temp)
            }

            DesktopPlatform.LINUX -> {
                val dataRoot = xdgPath("XDG_DATA_HOME", home.resolve(".local").resolve("share"))
                    .resolve(LINUX_DIRECTORY_NAME)
                val configRoot = xdgPath("XDG_CONFIG_HOME", home.resolve(".config"))
                    .resolve(LINUX_DIRECTORY_NAME)
                val cacheRoot = xdgPath("XDG_CACHE_HOME", home.resolve(".cache"))
                    .resolve(LINUX_DIRECTORY_NAME)
                linuxPaths(dataRoot, configRoot, cacheRoot, temp)
            }

            DesktopPlatform.UNSUPPORTED -> {
                // A safe user-local fallback keeps unsupported Unix-like/JVM desktops away from
                // both the installation directory and privileged system directories.
                val root = home.resolve(".$LINUX_DIRECTORY_NAME")
                linuxPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"), temp)
            }
        }
        return if (createDirectories) result.createRequiredDirectories() else result
    }

    override fun directories(): ApplicationDirectories = paths().asSharedDirectories()

    private fun windowsPaths(root: Path, temp: Path): DesktopApplicationPaths {
        val data = root.resolve("data")
        return DesktopApplicationPaths(
            root = root,
            data = data,
            database = data.resolve("pos.db"),
            backups = data.resolve("backups"),
            images = data.resolve("images"),
            logs = data.resolve("logs"),
            configuration = root.resolve("config"),
            cache = root.resolve("cache"),
            temporary = temp,
            // Preserve the location used by all existing Windows installations.
            licenseFile = data.resolve("secure").resolve("license.dat")
        )
    }

    private fun linuxPaths(data: Path, config: Path, cache: Path, temp: Path) = DesktopApplicationPaths(
        root = data,
        data = data,
        database = data.resolve("pos.db"),
        backups = data.resolve("backups"),
        images = data.resolve("images"),
        logs = data.resolve("logs"),
        configuration = config,
        cache = cache,
        temporary = temp,
        licenseFile = config.resolve("secure").resolve("license.dat")
    )

    private fun xdgPath(variable: String, fallback: Path): Path = environment[variable]
        ?.takeIf(String::isNotBlank)
        ?.let(Paths::get)
        ?: fallback

    companion object {
        private const val APP_DIRECTORY_NAME = "PATISSERIE_POS"
        private const val LINUX_DIRECTORY_NAME = "patisserie-pos"
    }
}

interface DesktopExternalIntegration {
    val platform: DesktopPlatform
}

interface DesktopPrintingService : DesktopExternalIntegration

interface DesktopDeviceIdentityService : DesktopExternalIntegration {
    fun installationId(): String
}
