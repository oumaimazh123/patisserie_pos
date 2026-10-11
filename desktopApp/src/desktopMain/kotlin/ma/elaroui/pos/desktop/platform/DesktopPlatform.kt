package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
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
        val migrationLock = DesktopSingleInstanceLock.acquire(configuration.resolve("migration.lock"))
            ?: error("Another process is migrating customer data")
        migrationLock.use { migrateLegacyDataUnderLock() }
    }

    private fun migrateLegacyDataUnderLock() {
        if (Files.exists(database)) {
            require(Files.size(database) > 0L) { "Existing database is empty; refusing legacy migration" }
            return
        }
        val parentDir = root.parent ?: return
        val legacyNames = listOf("PatisseriePOS", "patisserie-pos")
        for (legacy in legacyNames) {
            val candidateRoot = parentDir.resolve(legacy)
            val candidateDb = candidateRoot.resolve("data").resolve("pos.db")
            if (Files.exists(candidateDb) && Files.size(candidateDb) > 0L) {
                val legacyLock = DesktopSingleInstanceLock.acquire(candidateRoot.resolve("config/application.lock"))
                    ?: error("Legacy POS is still running; close it before migrating customer data")
                legacyLock.use {
                val sourceImages = candidateRoot.resolve("data").resolve("images")
                val staging = database.resolveSibling("pos.db.legacy-${java.util.UUID.randomUUID()}.tmp")
                try {
                    Files.createDirectories(database.parent)
                    val escaped = staging.toAbsolutePath().toString().replace("'", "''")
                    DriverManager.getConnection("jdbc:sqlite:${candidateDb.toAbsolutePath()}").use { source ->
                        // A SQLite snapshot includes committed WAL transactions; a raw file copy does not.
                        source.createStatement().use { it.execute("VACUUM INTO '$escaped'") }
                    }
                    DriverManager.getConnection("jdbc:sqlite:${staging.toAbsolutePath()}").use { snapshot ->
                        val intact = snapshot.createStatement().use { statement ->
                            statement.executeQuery("PRAGMA integrity_check").use { it.next() && it.getString(1) == "ok" }
                        }
                        check(intact) { "Legacy database snapshot failed integrity verification" }
                        rewriteLegacyImagePaths(snapshot, sourceImages, images)
                    }
                    val candidateLicense = candidateRoot.resolve("data").resolve("secure").resolve("license.dat")
                    if (Files.exists(candidateLicense)) {
                        Files.createDirectories(licenseFile.parent)
                        copyMissingOrIdentical(candidateLicense, licenseFile)
                    }
                    if (Files.isDirectory(sourceImages)) {
                        copyDirectoryRecursively(sourceImages, images)
                    }
                    val candidateBackups = candidateRoot.resolve("data").resolve("backups")
                    if (Files.exists(candidateBackups) && Files.isDirectory(candidateBackups)) {
                        copyDirectoryRecursively(candidateBackups, backups)
                    }
                    try { Files.move(staging, database, StandardCopyOption.ATOMIC_MOVE) }
                    catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(staging, database) }
                } finally {
                    Files.deleteIfExists(staging)
                }
                }
                return
            }
        }
    }

    private fun rewriteLegacyImagePaths(
        snapshot: java.sql.Connection,
        sourceImages: Path,
        targetImages: Path
    ) {
        val sourcePrefix = sourceImages.toAbsolutePath().normalize().toString().replace('\\', '/') + "/"
        for (table in listOf("products", "categories")) {
            val hasTable = snapshot.createStatement().use { statement ->
                statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'")
                    .use { it.next() }
            }
            if (!hasTable) continue
            val hasImagePath = snapshot.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                    var found = false
                    while (rows.next()) if (rows.getString("name") == "image_path") found = true
                    found
                }
            }
            if (!hasImagePath) continue
            val updates = mutableListOf<Pair<Long, String>>()
            snapshot.createStatement().use { statement ->
                statement.executeQuery("SELECT id,image_path FROM $table WHERE image_path IS NOT NULL AND image_path<>''")
                    .use { rows ->
                        while (rows.next()) {
                            val old = rows.getString(2).replace('\\', '/')
                            if (old.startsWith(sourcePrefix, ignoreCase = true)) {
                                val relative = old.substring(sourcePrefix.length)
                                val target = targetImages.resolve(relative).normalize()
                                check(target.startsWith(targetImages.normalize())) { "Unsafe legacy image path" }
                                updates += rows.getLong(1) to target.toString()
                            }
                        }
                    }
            }
            snapshot.prepareStatement("UPDATE $table SET image_path=? WHERE id=?").use { update ->
                updates.forEach { (id, path) ->
                    update.setString(1, path)
                    update.setLong(2, id)
                    update.addBatch()
                }
                update.executeBatch()
            }
        }
    }

    private fun copyMissingOrIdentical(source: Path, target: Path) {
        if (Files.exists(target)) {
            check(Files.isRegularFile(target) && Files.mismatch(source, target) == -1L) {
                "Existing customer file differs from legacy source: $target"
            }
        } else {
            Files.copy(source, target)
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
                    copyMissingOrIdentical(sourcePath, targetPath)
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
