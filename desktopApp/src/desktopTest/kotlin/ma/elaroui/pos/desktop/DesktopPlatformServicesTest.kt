package ma.elaroui.pos.desktop

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.shared.PlatformPath
import ma.elaroui.pos.shared.PosPlatform
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.LogEntry
import ma.elaroui.pos.shared.LogLevel

class DesktopPlatformServicesTest {
    @Test
    fun packagedLoggerWritesToConfiguredUserLogPath() {
        val root = Files.createTempDirectory("pos-logger-")
        try {
            val file = root.resolve("logs/application.log")
            JvmPlatformLogger(file).log(LogEntry(LogLevel.INFO, "started", category = "Startup"))
            val text = Files.readString(file)
            assertTrue(text.contains("[INFO] [Startup] started"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun fileSystemUsesOnlyProvidedTemporaryDirectory() {
        val root = Files.createTempDirectory("pos-phase3-")
        try {
            val service = JvmFileSystemAccess()
            val folder = PlatformPath.parse(root.resolve("nested").toString())
            val file = PlatformPath.parse(root.resolve("nested").resolve("test.bin").toString())
            service.createDirectories(folder)
            service.writeBytes(file, byteArrayOf(1, 2, 3))

            assertContentEquals(byteArrayOf(1, 2, 3), service.readBytes(file))
            val metadata = requireNotNull(service.metadata(file))
            assertEquals("test.bin", metadata.name)
            assertEquals(3, metadata.sizeBytes)
            assertFalse(metadata.isDirectory)
            assertTrue(service.delete(file))
            assertEquals(null, service.metadata(file))
        } finally {
            root.resolve("nested").deleteIfExists()
            root.deleteIfExists()
        }
    }

    @Test
    fun windowsDirectoriesPreserveLocalAppDataLayout() {
        val root = Files.createTempDirectory("pos-phase3-dirs-")
        try {
            val paths = DesktopApplicationPathsProvider(
                platform = DesktopPlatform.WINDOWS,
                environment = mapOf("LOCALAPPDATA" to root.toString()),
                userHome = root.toString(),
                temporaryDirectory = root.resolve("temp").toString()
            ).paths(createDirectories = false)

            assertEquals(root.resolve("PATISSERIE_POS/data/pos.db"), paths.database)
            assertEquals(root.resolve("PATISSERIE_POS/data/secure/license.dat"), paths.licenseFile)
            assertFalse(Files.exists(paths.data))
        } finally {
            root.deleteIfExists()
        }
    }

    @Test
    fun linuxDirectoriesHonorXdgAndCreateRequiredFolders() {
        val root = Files.createTempDirectory("pos-linux-dirs-")
        try {
            val paths = DesktopApplicationPathsProvider(
                platform = DesktopPlatform.LINUX,
                environment = mapOf(
                    "XDG_DATA_HOME" to root.resolve("xdg-data").toString(),
                    "XDG_CONFIG_HOME" to root.resolve("xdg-config").toString(),
                    "XDG_CACHE_HOME" to root.resolve("xdg-cache").toString()
                ),
                userHome = root.resolve("home").toString(),
                temporaryDirectory = root.resolve("tmp").toString()
            ).paths()

            assertEquals(root.resolve("xdg-data/patisserie-pos/pos.db"), paths.database)
            assertEquals(root.resolve("xdg-config/patisserie-pos/secure/license.dat"), paths.licenseFile)
            assertTrue(Files.isDirectory(paths.backups))
            assertTrue(Files.isDirectory(paths.images))
            assertTrue(Files.isDirectory(paths.logs))
            assertTrue(Files.isDirectory(paths.configuration))
            assertTrue(Files.isDirectory(paths.cache))
            assertTrue(Files.isDirectory(paths.temporary))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun unsupportedPlatformUsesSafeUserLocalFallback() {
        val root = Files.createTempDirectory("pos-unsupported-dirs-")
        try {
            val paths = DesktopApplicationPathsProvider(
                platform = DesktopPlatform.UNSUPPORTED,
                environment = emptyMap(),
                userHome = root.toString(),
                temporaryDirectory = root.resolve("tmp").toString()
            ).paths(createDirectories = false)

            assertTrue(paths.data.startsWith(root))
            assertEquals(root.resolve(".patisserie-pos/data"), paths.data)
        } finally {
            root.deleteIfExists()
        }
    }

    @Test
    fun detectsSupportedAndUnsupportedDesktopPlatforms() {
        assertEquals(DesktopPlatform.WINDOWS, DesktopPlatform.detect("Windows 11"))
        assertEquals(DesktopPlatform.LINUX, DesktopPlatform.detect("Linux"))
        assertEquals(DesktopPlatform.LINUX, DesktopPlatform.detect("GNU/Linux"))
        assertEquals(DesktopPlatform.UNSUPPORTED, DesktopPlatform.detect("Mac OS X"))
        assertEquals(DesktopPlatform.UNSUPPORTED, DesktopPlatform.detect(""))
    }

    @Test
    fun platformInformationMapsLinuxAndUnknownWithoutHardCodingWindows() {
        val linux = JvmPlatformInformationProvider({ "Linux" }, { "6.8" }, { "amd64" }).current()
        val unknown = JvmPlatformInformationProvider({ "FreeBSD" }, { "14" }, { "amd64" }).current()

        assertEquals(PosPlatform.LINUX, linux.platform)
        assertEquals(PosPlatform.UNKNOWN, unknown.platform)
    }

    @Test
    fun jvmLocaleReportsRtlAndLanguageTag() {
        val locale = JvmLocaleInformationProvider { java.util.Locale.forLanguageTag("ar-MA") }.current()
        assertEquals("ar-MA", locale.languageTag)
        assertEquals("MA", locale.country)
        assertTrue(locale.isRightToLeft)
    }
}
