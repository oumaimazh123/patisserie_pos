package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase

class LegacyMigrationTest {

    @Test
    fun testLegacyDataMigrationCopiesDatabaseAndFiles() = runBlocking {
        val tempDir = Files.createTempDirectory("legacy-migration-test")
        val legacyDir = tempDir.resolve("PatisseriePOS")
        val legacyDataDir = legacyDir.resolve("data")
        Files.createDirectories(legacyDataDir)

        val legacyDb = legacyDataDir.resolve("pos.db")
        WindowsPosDatabase.open(legacyDb).use { db ->
            db.configureInitialSetup("Legacy Bakery", "Owner 1", "1234")
        }
        assertTrue(Files.exists(legacyDb))
        assertTrue(Files.size(legacyDb) > 0L)

        // Target new app dir
        val provider = DesktopApplicationPathsProvider(
            platform = DesktopPlatform.WINDOWS,
            environment = mapOf("LOCALAPPDATA" to tempDir.toString()),
            userHome = tempDir.toString()
        )
        val paths = provider.paths(createDirectories = true)

        assertTrue(Files.exists(paths.database), "Target database should have been migrated")
        assertTrue(Files.size(paths.database) > 0L, "Migrated database should be non-empty")

        WindowsPosDatabase.open(paths.database).use { db ->
            val setupComplete = db.settings.get("setup_complete")
            val businessName = db.settings.get("establishment_name")
            assertEquals("true", setupComplete)
            assertEquals("Legacy Bakery", businessName)
        }
    }

    @Test
    fun testPreUpdateAutoBackupIsCreatedOnOpen() = runBlocking {
        val tempDir = Files.createTempDirectory("backup-preupdate-test")
        val dbPath = tempDir.resolve("data").resolve("pos.db")
        Files.createDirectories(dbPath.parent)

        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("Test Store", "Owner", "1111")
        }

        // Reopen database - should trigger pre-update auto backup
        WindowsPosDatabase.open(dbPath).use { db ->
            assertEquals("Test Store", db.settings.get("establishment_name"))
        }

        val backupsDir = dbPath.parent.resolve("backups")
        assertTrue(Files.exists(backupsDir), "Backups dir should exist")
        val backups = Files.list(backupsDir).use { stream ->
            stream.filter { it.fileName.toString().startsWith("auto_pre_update_") }.toList()
        }
        assertTrue(backups.isNotEmpty(), "At least one auto_pre_update backup should be present")
    }
}
