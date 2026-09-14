package ma.elaroui.pos.desktop.backup

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.DesktopBuildInfo
import ma.elaroui.pos.desktop.platform.DesktopApplicationPaths
import ma.elaroui.pos.desktop.platform.DesktopPlatform

data class BackupValidation(val valid: Boolean, val message: String, val legacyDatabase: Boolean = false)

class DesktopBackupService(
    private val database: WindowsPosDatabase,
    private val paths: DesktopApplicationPaths,
    private val platform: DesktopPlatform = DesktopPlatform.detect(),
    private val now: () -> Instant = Instant::now
) {
    fun defaultBackupPath(): Path = paths.backups.resolve(backupFileName(now()))

    fun createBackup(target: Path) {
        require(target.toAbsolutePath().normalize() != paths.database.toAbsolutePath().normalize()) {
            "Backup destination cannot overwrite the live database"
        }
        target.parent?.let(Files::createDirectories)
        requireUsableSpace(target.parent ?: target.toAbsolutePath().parent, estimatedBackupBytes())
        val work = Files.createTempDirectory(paths.temporary, "backup-")
        val temporaryArchive = target.resolveSibling("${target.fileName}.tmp-${UUID.randomUUID()}")
        try {
            val snapshot = work.resolve(DATABASE_ENTRY.substringAfterLast('/'))
            database.backupTo(snapshot)
            require(database.validateBackup(snapshot)) { "Database snapshot validation failed" }
            ZipOutputStream(Files.newOutputStream(temporaryArchive)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                Files.copy(snapshot, zip)
                zip.closeEntry()
            }
            require(validate(temporaryArchive).valid) { "Created backup archive failed validation" }
            moveReplacing(temporaryArchive, target)
            require(Files.isRegularFile(target) && Files.size(target) > 0L) { "Backup file was not created" }
        } finally {
            Files.deleteIfExists(temporaryArchive)
            deleteTree(work)
        }
    }

    fun validate(source: Path): BackupValidation {
        if (!Files.isRegularFile(source)) return BackupValidation(false, "Backup file does not exist")
        if (runCatching { Files.size(source) }.getOrDefault(0L) <= 0L) return BackupValidation(false, "Backup file is empty")
        if (source.fileName.toString().endsWith(".db", ignoreCase = true)) {
            return BackupValidation(database.validateBackup(source), "Legacy SQLite backup", legacyDatabase = true)
        }
        return runCatching {
            ZipFile(source.toFile()).use { zip ->
                val manifestEntry = requireNotNull(zip.getEntry(MANIFEST_ENTRY)) { "Backup manifest is missing" }
                val properties = Properties().apply { zip.getInputStream(manifestEntry).use(::load) }
                require(properties.getProperty("formatVersion") == FORMAT_VERSION) { "Unsupported backup format" }
                require(properties.getProperty("databaseEntry") == DATABASE_ENTRY) { "Invalid database entry" }
                val databaseEntry = requireNotNull(zip.getEntry(DATABASE_ENTRY)) { "Database is missing from backup" }
                require(!databaseEntry.isDirectory && databaseEntry.size != 0L) { "Database entry is empty" }
                val checkDirectory = Files.createTempDirectory(paths.temporary, "backup-check-")
                try {
                    val candidate = checkDirectory.resolve("pos.db")
                    zip.getInputStream(databaseEntry).use { input -> Files.copy(input, candidate) }
                    require(database.validateBackup(candidate)) { "Backup database is corrupted" }
                } finally { deleteTree(checkDirectory) }
            }
            BackupValidation(true, "Backup is valid")
        }.getOrElse { BackupValidation(false, it.message ?: "Invalid backup archive") }
    }

    fun stageRestore(source: Path) {
        val validation = validate(source)
        require(validation.valid) { validation.message }
        requireUsableSpace(paths.temporary, Files.size(source) * 3L)
        Files.createDirectories(paths.backups)
        val safety = paths.backups.resolve("PATISSERIE_POS_PreRestore_${timestamp(now())}.db")
        database.backupTo(safety)
        require(database.validateBackup(safety)) { "Unable to create the pre-restore safety backup" }
        purgeOldPreRestoreSafetyBackups(maxToKeep = 5)
        if (validation.legacyDatabase) {
            database.stageRestore(source)
            return
        }
        val work = Files.createTempDirectory(paths.temporary, "restore-")
        try {
            val candidate = work.resolve("pos.db")
            ZipFile(source.toFile()).use { zip ->
                zip.getInputStream(requireNotNull(zip.getEntry(DATABASE_ENTRY))).use { Files.copy(it, candidate) }
            }
            require(database.validateBackup(candidate)) { "Extracted database is corrupted" }
            database.stageRestore(candidate)
        } finally { deleteTree(work) }
    }

    private fun purgeOldPreRestoreSafetyBackups(maxToKeep: Int = 5) {
        runCatching {
            if (!Files.isDirectory(paths.backups)) return@runCatching
            Files.list(paths.backups).use { stream ->
                val preRestores = stream
                    .filter {
                        Files.isRegularFile(it) &&
                            (it.fileName.toString().startsWith("PATISSERIE_POS_PreRestore_") || it.fileName.toString().startsWith("GeneralPOS_PreRestore_")) &&
                            it.fileName.toString().endsWith(".db")
                    }
                    .sorted { p1, p2 -> Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1)) }
                    .toList()
                if (preRestores.size > maxToKeep) {
                    preRestores.drop(maxToKeep).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    private fun manifest(): String = buildString {
        appendLine("formatVersion=$FORMAT_VERSION")
        appendLine("applicationId=${DesktopBuildInfo.APPLICATION_ID}")
        appendLine("applicationVersion=${DesktopBuildInfo.version}")
        appendLine("createdAt=${now()}")
        appendLine("databaseSchemaVersion=${database.schemaVersion()}")
        appendLine("platform=${platform.name}")
        appendLine("databaseEntry=$DATABASE_ENTRY")
        appendLine("licenceIncluded=false")
    }

    private fun estimatedBackupBytes(): Long {
        val databaseBytes = runCatching { Files.size(paths.database) }.getOrDefault(0L)
        val imageBytes = if (Files.isDirectory(paths.images)) runCatching {
            Files.walk(paths.images).use { entries -> entries.filter(Files::isRegularFile).mapToLong(Files::size).sum() }
        }.getOrDefault(0L) else 0L
        return (databaseBytes + imageBytes).coerceAtLeast(1_048_576L) * 2L
    }

    private fun requireUsableSpace(directory: Path, requiredBytes: Long) {
        Files.createDirectories(directory)
        val usable = Files.getFileStore(directory).usableSpace
        require(usable >= requiredBytes.coerceAtLeast(1_048_576L)) { "Insufficient disk space" }
    }

    companion object {
        const val FORMAT_VERSION = "1"
        const val MANIFEST_ENTRY = "manifest.properties"
        const val DATABASE_ENTRY = "data/pos.db"
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC)
        fun backupFileName(instant: Instant): String = "PATISSERIE_POS_Backup_${timestamp(instant)}.zip"
        private fun timestamp(instant: Instant) = FORMATTER.format(instant)
        private fun moveReplacing(from: Path, to: Path) {
            try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) }
        }
        private fun deleteTree(root: Path) {
            if (!Files.exists(root)) return
            Files.walk(root).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
