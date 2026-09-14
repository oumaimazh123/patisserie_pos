package ma.elaroui.pos.desktop.license

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ma.elaroui.pos.desktop.platform.DesktopPlatform

class DesktopLicenseStoreFactoryTest {
    @Test
    fun linuxEncryptedStorePersistsValuesWithoutPlaintext() {
        val root = Files.createTempDirectory("pos-linux-license-")
        try {
            val file = root.resolve("config/secure/license.dat")
            val store = DesktopLicenseStoreFactory.create(DesktopPlatform.LINUX, file)
            store.put("installation_id", "LINUX-TEST")

            assertEquals("LINUX-TEST", DesktopLicenseStoreFactory.create(DesktopPlatform.LINUX, file).get("installation_id"))
            assertTrue(Files.isRegularFile(file))
            val persistedBytes = Files.readAllBytes(file)
            val persistedAsSingleByteText = persistedBytes.toString(Charsets.ISO_8859_1)
            assertTrue(!persistedAsSingleByteText.contains("LINUX-TEST"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
