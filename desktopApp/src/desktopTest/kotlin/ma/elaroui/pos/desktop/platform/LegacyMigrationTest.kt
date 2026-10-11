package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.Product

class LegacyMigrationTest {

    @Test
    fun testLegacyDataMigrationCopiesDatabaseAndFiles() = runBlocking {
        val tempDir = Files.createTempDirectory("legacy-migration-test")
        val legacyDir = tempDir.resolve("PatisseriePOS")
        val legacyDataDir = legacyDir.resolve("data")
        Files.createDirectories(legacyDataDir)

        val legacyDb = legacyDataDir.resolve("pos.db")
        val legacyImage = legacyDataDir.resolve("images/products/cake.png")
        Files.createDirectories(legacyImage.parent)
        Files.write(legacyImage, byteArrayOf(4, 2, 1))
        val legacyLicense = legacyDataDir.resolve("secure/license.dat")
        Files.createDirectories(legacyLicense.parent)
        Files.write(legacyLicense, byteArrayOf(8, 6, 3))
        WindowsPosDatabase.open(legacyDb).use { db ->
            db.configureInitialSetup("Legacy Bakery", "Owner 1", "1234")
            val categoryId = db.categories.save(Category(0, "Cakes", true, 0))
            db.products.save(Product(0, categoryId, "Cake", 1_500, 1_000, true, true, legacyImage.toString()))
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
            assertEquals(1, db.categories.observeAll().first().size)
            val products = db.products.observeAll().first()
            assertEquals(1, products.size)
            assertEquals(paths.images.resolve("products/cake.png").toString(), products.single().imagePath)
        }
        assertTrue(Files.readAllBytes(paths.images.resolve("products/cake.png")).contentEquals(byteArrayOf(4, 2, 1)))
        assertTrue(Files.readAllBytes(paths.licenseFile).contentEquals(byteArrayOf(8, 6, 3)))

        provider.paths(createDirectories = true)
        WindowsPosDatabase.open(paths.database).use { db ->
            assertEquals(1, db.categories.observeAll().first().size)
            assertEquals(1, db.products.observeAll().first().size)
        }
    }

    @Test
    fun conflictingCustomerFileStopsLegacyMigrationWithoutCreatingNewDatabase() = runBlocking {
        val root = Files.createTempDirectory("legacy-migration-conflict")
        val legacyData = root.resolve("PatisseriePOS/data")
        val legacyLicense = legacyData.resolve("secure/license.dat")
        Files.createDirectories(legacyLicense.parent)
        Files.write(legacyLicense, byteArrayOf(1, 2, 3))
        val legacyDb = legacyData.resolve("pos.db")
        WindowsPosDatabase.open(legacyDb).use { it.configureInitialSetup("Original", "Owner", "1234") }

        val provider = DesktopApplicationPathsProvider(
            DesktopPlatform.WINDOWS,
            mapOf("LOCALAPPDATA" to root.toString()),
            root.toString()
        )
        val target = provider.paths(createDirectories = false)
        Files.createDirectories(target.licenseFile.parent)
        Files.write(target.licenseFile, byteArrayOf(9, 9, 9))

        assertFailsWith<IllegalStateException> { provider.paths(createDirectories = true) }
        assertFalse(Files.exists(target.database))
        assertTrue(Files.readAllBytes(target.licenseFile).contentEquals(byteArrayOf(9, 9, 9)))
        WindowsPosDatabase.open(legacyDb).use { db ->
            assertEquals("Original", db.settings.get("establishment_name"))
        }
    }

    @Test
    fun legacyMigrationIncludesCommittedWalChanges() = runBlocking {
        val root = Files.createTempDirectory("legacy-wal-upgrade")
        val legacyDb = root.resolve("PatisseriePOS/data/pos.db")
        val provider = DesktopApplicationPathsProvider(
            DesktopPlatform.WINDOWS,
            mapOf("LOCALAPPDATA" to root.toString()),
            root.toString()
        )
        WindowsPosDatabase.open(legacyDb).use { source ->
            source.configureInitialSetup("WAL Bakery", "Owner", "1234")
            source.settings.put(AppSetting("upgrade_marker", "committed while source is open"))
            val paths = provider.paths(createDirectories = true)
            WindowsPosDatabase.open(paths.database).use { migrated ->
                assertEquals("WAL Bakery", migrated.settings.get("establishment_name"))
                assertEquals("committed while source is open", migrated.settings.get("upgrade_marker"))
            }
        }
    }

    @Test
    fun runningLegacyPosPreventsPartialMigration() = runBlocking {
        val root = Files.createTempDirectory("legacy-running-upgrade")
        val legacyRoot = root.resolve("PatisseriePOS")
        WindowsPosDatabase.open(legacyRoot.resolve("data/pos.db")).use {
            it.configureInitialSetup("Open Store", "Owner", "1234")
        }
        val provider = DesktopApplicationPathsProvider(
            DesktopPlatform.WINDOWS,
            mapOf("LOCALAPPDATA" to root.toString()),
            root.toString()
        )
        val lock = DesktopSingleInstanceLock.acquire(legacyRoot.resolve("config/application.lock"))!!
        try {
            assertFailsWith<IllegalStateException> { provider.paths(createDirectories = true) }
            assertFalse(Files.exists(provider.paths(createDirectories = false).database))
        } finally {
            lock.close()
        }
        val migrated = provider.paths(createDirectories = true)
        WindowsPosDatabase.open(migrated.database).use {
            assertEquals("Open Store", it.settings.get("establishment_name"))
        }
    }

    @Test
    fun preMigrationBackupIsVerifiedBeforeSchemaChanges() = runBlocking {
        val tempDir = Files.createTempDirectory("backup-preupdate-test")
        val dbPath = tempDir.resolve("data").resolve("pos.db")
        Files.createDirectories(dbPath.parent)

        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("Test Store", "Owner", "1111")
        }

        // Reopening the current schema is not a migration.
        WindowsPosDatabase.open(dbPath).use { db ->
            assertEquals("Test Store", db.settings.get("establishment_name"))
        }
        assertFalse(Files.exists(dbPath.parent.resolve("backups")))

        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM schema_migrations WHERE version=12") }
        }

        WindowsPosDatabase.open(dbPath).use { db ->
            assertEquals(12, db.schemaVersion())
            assertEquals("Test Store", db.settings.get("establishment_name"))
        }

        val backupsDir = dbPath.parent.resolve("backups")
        assertTrue(Files.exists(backupsDir), "Backups dir should exist")
        val backups = Files.list(backupsDir).use { stream ->
            stream.filter { it.fileName.toString().startsWith("auto_pre_update_") }.toList()
        }
        assertTrue(backups.isNotEmpty(), "At least one auto_pre_update backup should be present")
        DriverManager.getConnection("jdbc:sqlite:${backups.single().toAbsolutePath()}").use { snapshot ->
            snapshot.createStatement().use { statement ->
                statement.executeQuery("SELECT MAX(version) FROM schema_migrations").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(11, rows.getInt(1))
                }
                statement.executeQuery("SELECT value FROM settings WHERE key='establishment_name'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Test Store", rows.getString(1))
                }
            }
        }
    }

    @Test
    fun failedPreMigrationBackupPreventsDatabaseMigration() = runBlocking {
        val tempDir = Files.createTempDirectory("backup-must-succeed-test")
        val dbPath = tempDir.resolve("data/pos.db")
        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("Protected Store", "Owner", "1111")
        }
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM schema_migrations WHERE version=12") }
        }
        val backupsPath = dbPath.parent.resolve("backups")
        Files.writeString(backupsPath, "not a directory")

        assertFailsWith<Exception> { WindowsPosDatabase.open(dbPath).close() }
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT MAX(version) FROM schema_migrations").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(11, rows.getInt(1))
                }
                statement.executeQuery("SELECT value FROM settings WHERE key='establishment_name'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Protected Store", rows.getString(1))
                }
            }
        }
    }

    @Test
    fun preMigrationBackupIncludesCommittedWalChanges() = runBlocking {
        val root = Files.createTempDirectory("pre-migration-wal-")
        val path = root.resolve("data/pos.db")
        WindowsPosDatabase.open(path).use { it.configureInitialSetup("WAL customer", "Owner", "1111") }

        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { writer ->
            writer.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("INSERT INTO settings(key,value) VALUES('wal_upgrade_marker','committed value')")
                statement.execute("DELETE FROM schema_migrations WHERE version=12")
            }
            // Keep the writer connection open so its committed pages may still be in WAL.
            WindowsPosDatabase.open(path).use { migrated ->
                assertEquals("committed value", migrated.settings.get("wal_upgrade_marker"))
            }
            val backups = Files.list(path.parent.resolve("backups")).use { files ->
                files.filter { it.fileName.toString().startsWith("auto_pre_update_") }.toList()
            }
            assertEquals(1, backups.size)
            DriverManager.getConnection("jdbc:sqlite:${backups.single().toAbsolutePath()}").use { snapshot ->
                snapshot.createStatement().use { statement ->
                    statement.executeQuery("SELECT value FROM settings WHERE key='wal_upgrade_marker'").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("committed value", rows.getString(1))
                    }
                    statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version=12").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(0, rows.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun missingIntermediateMigrationMarkerAlsoRequiresSafetyBackup() = runBlocking {
        val root = Files.createTempDirectory("backup-middle-migration-test")
        val path = root.resolve("data/pos.db")
        WindowsPosDatabase.open(path).use { it.configureInitialSetup("Marker Store", "Owner", "1111") }
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM schema_migrations WHERE version=11") }
        }
        Files.writeString(path.parent.resolve("backups"), "blocked")

        assertFailsWith<Exception> { WindowsPosDatabase.open(path).close() }
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version=11").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
                statement.executeQuery("SELECT value FROM settings WHERE key='establishment_name'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Marker Store", rows.getString(1))
                }
            }
        }
    }
}
