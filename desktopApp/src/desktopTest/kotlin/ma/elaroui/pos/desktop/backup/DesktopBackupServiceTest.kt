package ma.elaroui.pos.desktop.backup

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.platform.DesktopApplicationPaths
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

class DesktopBackupServiceTest {
    @Test
    fun rejectsUnrelatedSqliteAndFutureSchemaBeforeStagingRestore() {
        val root = Files.createTempDirectory("audit-backup-schema")
        val unrelated = root.resolve("unrelated.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:$unrelated").use { c ->
            c.createStatement().use { it.execute("CREATE TABLE other(value TEXT)") }
        }
        WindowsPosDatabase.open(root.resolve("live.db")).use { db ->
            assertFalse(db.validateBackup(unrelated))
            val future = root.resolve("future.db")
            db.backupTo(future)
            java.sql.DriverManager.getConnection("jdbc:sqlite:$future").use { c ->
                c.createStatement().use { it.execute("INSERT INTO schema_migrations VALUES(999,1)") }
            }
            assertFalse(db.validateBackup(future))
            kotlin.test.assertFailsWith<IllegalArgumentException> { db.stageRestore(future) }
            assertFalse(Files.exists(root.resolve("live.db.restore-pending")))
        }
    }

    @Test
    fun portableNameUsesSafeCharacters() {
        assertEquals(
            "PATISSERIE_POS_Backup_2026-08-29_213000.zip",
            DesktopBackupService.backupFileName(Instant.parse("2026-08-29T21:30:00Z"))
        )
    }

    @Test
    fun backupRestoreRecoversBusinessSettingsAndManagedImagesAcrossDataRoots() = runBlocking {
        val sourceRoot = Files.createTempDirectory("pos-backup-source-")
        val destinationRoot = Files.createTempDirectory("pos-backup-destination-")
        try {
            val sourcePaths = paths(sourceRoot)
            val image = sourcePaths.images.resolve("products/atlas.png")
            Files.createDirectories(image.parent)
            Files.write(image, byteArrayOf(1, 3, 5, 7))
            val archive = sourceRoot.resolve("exports/portable.zip")
            WindowsPosDatabase.open(sourcePaths.database).use { db ->
                db.settings.put(AppSetting("establishment_name", "Café Atlas"))
                db.settings.put(AppSetting("customer_printer", "Receipt Printer"))
                db.categories.save(Category(90, "Spécialités", true, 0, imagePath = image.toString()))
                db.products.save(Product(90, 90, "Café Atlas", 2500, 2000, true, true, image.toString()))
                DesktopBackupService(db, sourcePaths, DesktopPlatform.LINUX) { Instant.parse("2026-08-29T21:30:00Z") }
                    .createBackup(archive)
            }

            val destinationPaths = paths(destinationRoot)
            WindowsPosDatabase.open(destinationPaths.database).use { db ->
                db.settings.put(AppSetting("establishment_name", "Changed"))
                DesktopBackupService(db, destinationPaths, DesktopPlatform.LINUX).stageRestore(archive)
            }
            WindowsPosDatabase.open(destinationPaths.database).use { restored ->
                assertEquals("Café Atlas", restored.settings.get("establishment_name"))
                assertEquals("Receipt Printer", restored.settings.get("customer_printer"))
                val product = restored.products.findById(90)!!
                assertTrue(product.imagePath!!.startsWith(destinationPaths.images.toString()))
                assertTrue(Files.exists(Path.of(product.imagePath!!)))
                assertTrue(Files.readAllBytes(Path.of(product.imagePath!!)).contentEquals(byteArrayOf(1, 3, 5, 7)))
                val categoryImage = restored.categories.findById(90)!!.imagePath!!
                assertTrue(categoryImage.startsWith(destinationPaths.images.toString()))
                assertTrue(Files.exists(Path.of(categoryImage)))
            }
        } finally {
            sourceRoot.toFile().deleteRecursively()
            destinationRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun validationRejectsCorruptArchiveAndArchiveWithoutDatabase() {
        val root = Files.createTempDirectory("pos-backup-invalid-")
        try {
            val appPaths = paths(root)
            WindowsPosDatabase.open(appPaths.database).use { db ->
                val service = DesktopBackupService(db, appPaths)
                val corrupt = root.resolve("corrupt.zip")
                Files.writeString(corrupt, "not a zip")
                assertFalse(service.validate(corrupt).valid)

                val missingDatabase = root.resolve("missing-db.zip")
                java.util.zip.ZipOutputStream(Files.newOutputStream(missingDatabase)).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry(DesktopBackupService.MANIFEST_ENTRY))
                    zip.write("formatVersion=1\ndatabaseEntry=data/pos.db\n".toByteArray())
                    zip.closeEntry()
                }
                assertFalse(service.validate(missingDatabase).valid)
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun invalidPendingRestoreRollsBackAndKeepsCurrentDatabaseUsable() = runBlocking {
        val root = Files.createTempDirectory("pos-restore-rollback-")
        try {
            val appPaths = paths(root)
            WindowsPosDatabase.open(appPaths.database).use {
                it.settings.put(AppSetting("establishment_name", "Original Café"))
            }
            Files.writeString(appPaths.database.resolveSibling("pos.db.restore-pending"), "corrupted")
            WindowsPosDatabase.open(appPaths.database).use { reopened ->
                assertEquals("Original Café", reopened.settings.get("establishment_name"))
            }
            assertTrue(Files.exists(appPaths.database.resolveSibling("pos.db.restore-failed")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun archiveContainsManifestAndDatabaseButNeverDeviceLicence() {
        val root = Files.createTempDirectory("pos-backup-license-")
        try {
            val appPaths = paths(root)
            Files.createDirectories(appPaths.licenseFile.parent)
            Files.writeString(appPaths.licenseFile, "DEVICE-BOUND-SECRET")
            val archive = root.resolve("backup.zip")
            WindowsPosDatabase.open(appPaths.database).use { db -> DesktopBackupService(db, appPaths).createBackup(archive) }
            ZipFile(archive.toFile()).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(listOf(DesktopBackupService.MANIFEST_ENTRY, DesktopBackupService.DATABASE_ENTRY), names)
                assertTrue(zip.getInputStream(zip.getEntry(DesktopBackupService.MANIFEST_ENTRY)).bufferedReader().readText().contains("licenceIncluded=false"))
            }
        } finally { root.toFile().deleteRecursively() }
    }

    private fun paths(root: Path): DesktopApplicationPaths {
        val data = root.resolve("data")
        val result = DesktopApplicationPaths(
            root, data, data.resolve("pos.db"), data.resolve("backups"), data.resolve("images"),
            data.resolve("logs"), root.resolve("config"), root.resolve("cache"), root.resolve("tmp"),
            root.resolve("config/secure/license.dat")
        )
        return result.createRequiredDirectories()
    }
}
