package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

class WindowsUpdatePersistenceTest {
    @Test
    fun installUpdateAndUninstallKeepCustomerDatabaseAndFiles() = runBlocking {
        val localAppData = Files.createTempDirectory("windows-update-persistence-")
        try {
            val installDirectory = localAppData.resolve("PATISSERIE_POS_APP")
            val paths = DesktopApplicationPathsProvider(
                DesktopPlatform.WINDOWS,
                mapOf("LOCALAPPDATA" to localAppData.toString()),
                localAppData.toString()
            ).paths()
            assertFalse(paths.root.startsWith(installDirectory))
            assertFalse(installDirectory.startsWith(paths.root))

            Files.createDirectories(installDirectory)
            Files.writeString(installDirectory.resolve("app.exe"), "first version")
            Files.write(paths.licenseFile, byteArrayOf(1, 3, 5))
            Files.write(paths.images.resolve("cake.png"), byteArrayOf(2, 4, 6))
            Files.writeString(paths.configuration.resolve("device-id.seed"), "stable-device")
            Files.writeString(paths.backups.resolve("previous-backup.txt"), "retained")

            WindowsPosDatabase.open(paths.database).use { db ->
                db.configureInitialSetup("Customer bakery", "Owner", "1234")
                val categoryId = db.categories.save(Category(0, "Cakes", true, 0))
                db.products.save(Product(0, categoryId, "Chocolate cake", 1_500, 1_000, true, true, paths.images.resolve("cake.png").toString()))
                db.settings.put(AppSetting("upgrade_test_marker", "keep me"))
            }

            // An update replaces installer-owned files only. Reopening must use the same DB.
            deleteTree(installDirectory)
            Files.createDirectories(installDirectory)
            Files.writeString(installDirectory.resolve("app.exe"), "updated version")
            assertCustomerData(paths)

            // Uninstall removes the application directory; a reinstall must not seed duplicates.
            deleteTree(installDirectory)
            assertFalse(Files.exists(installDirectory))
            assertCustomerData(paths)
            Files.createDirectories(installDirectory)
            Files.writeString(installDirectory.resolve("app.exe"), "reinstalled version")
            assertCustomerData(paths)
        } finally {
            deleteTree(localAppData)
        }
    }

    private suspend fun assertCustomerData(paths: DesktopApplicationPaths) {
        WindowsPosDatabase.open(paths.database).use { db ->
            assertEquals("Customer bakery", db.settings.get("establishment_name"))
            assertEquals("keep me", db.settings.get("upgrade_test_marker"))
            assertEquals(1, db.categories.observeAll().first().size)
            assertEquals(1, db.products.observeAll().first().size)
            assertEquals("Chocolate cake", db.products.observeAll().first().single().name)
            assertEquals(1, db.users.observeActive().first().size)
            assertEquals(12, db.schemaVersion())
        }
        assertContentEquals(byteArrayOf(1, 3, 5), Files.readAllBytes(paths.licenseFile))
        assertContentEquals(byteArrayOf(2, 4, 6), Files.readAllBytes(paths.images.resolve("cake.png")))
        assertEquals("stable-device", Files.readString(paths.configuration.resolve("device-id.seed")))
        assertEquals("retained", Files.readString(paths.backups.resolve("previous-backup.txt")))
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
