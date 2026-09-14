package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxDesktopStartupTest {
    @Test
    fun `linux autostart entry launches installed application as user`() {
        val directory = Files.createTempDirectory("pos-autostart-")
        val entry = LinuxAutostartInstaller(directory).ensureInstalled()
        val content = Files.readString(entry)
        assertTrue(entry.fileName.toString() == "ma.elaroui.patisseriepos.desktop")
        assertTrue(content.contains("Name=PATISSERIE_POS"))
        assertTrue(content.contains("Exec=\"/opt/patisserie-pos/bin/PATISSERIE_POS\""))
        assertTrue(content.contains("Icon=/opt/patisserie-pos/lib/PATISSERIE_POS.png"))
        assertTrue(content.contains("X-GNOME-Autostart-enabled=true"))
        assertFalse(content.contains("sudo"))
    }
}
