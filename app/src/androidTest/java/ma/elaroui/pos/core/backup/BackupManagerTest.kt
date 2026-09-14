package ma.elaroui.pos.core.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.data.local.entity.CategoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: POSDatabase

    @Before
    fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "backup-manager-test").apply {
            deleteRecursively()
            mkdirs()
        }
        context = IsolatedBackupContext(target, root)
        database = openDatabase()
    }

    @After
    fun cleanup() {
        runCatching { database.close() }
        root.deleteRecursively()
    }

    @Test
    fun encryptedBackup_restoresRoomAndPreferences_andRejectsCorruption() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(name = "Original"))
        val preferencesFile = File(root, "shared_prefs/pos_setup_prefs.xml").apply {
            parentFile!!.mkdirs()
            writeText("<map><string name=\"restaurant_name\">Café Original</string></map>")
        }
        val licensePreferencesFile = File(root, "shared_prefs/pos_license_prefs.xml").apply {
            writeText("<map><string name=\"activated_license\">device-a-license</string></map>")
        }
        val output = ByteArrayOutputStream()
        val areaImage = File(context.filesDir, "app_images/areas/area_test.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        assertTrue(
            BackupManager.createBackup(context, database, output, "secret").isSuccess
        )
        database.categoryDao().insertCategory(CategoryEntity(name = "Temporary"))
        preferencesFile.writeText("<map/>")
        licensePreferencesFile.writeText(
            "<map><string name=\"activated_license\">device-b-license</string></map>"
        )
        areaImage.delete()

        assertTrue(
            BackupManager.restoreBackup(
                context,
                database,
                ByteArrayInputStream(output.toByteArray()),
                "secret"
            ).isSuccess
        )
        database = openDatabase()
        assertEquals(
            listOf("Original"),
            database.categoryDao().getAllCategoriesForOwner().first().map { it.name }
        )
        assertTrue(preferencesFile.readText().contains("Café Original"))
        assertEquals(listOf<Byte>(1, 2, 3, 4), areaImage.readBytes().toList())
        assertTrue(
            "A backup must not clone device-bound licence preferences.",
            licensePreferencesFile.readText().contains("device-b-license")
        )

        val corrupted = output.toByteArray().copyOfRange(0, 24)
        val result = BackupManager.restoreBackup(
            context,
            database,
            ByteArrayInputStream(corrupted),
            "secret"
        )
        assertTrue(result.isFailure)
    }

    private fun openDatabase(): POSDatabase = Room.databaseBuilder(
        context,
        POSDatabase::class.java,
        "pos_restaurant_db"
    ).allowMainThreadQueries().build()
}

private class IsolatedBackupContext(
    base: Context,
    private val root: File
) : ContextWrapper(base) {
    override fun getDatabasePath(name: String): File =
        File(root, "databases/$name").also { it.parentFile?.mkdirs() }

    override fun getApplicationInfo(): ApplicationInfo =
        ApplicationInfo(super.getApplicationInfo()).apply { dataDir = root.absolutePath }

    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
}
