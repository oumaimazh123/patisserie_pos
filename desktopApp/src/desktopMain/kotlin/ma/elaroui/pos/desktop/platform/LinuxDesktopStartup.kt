package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import ma.elaroui.pos.desktop.DesktopBuildInfo
class LinuxAutostartInstaller(
    private val autostartDirectory: Path,
    private val executable: String = "/opt/patisserie-pos/bin/PATISSERIE_POS"
) {
    fun ensureInstalled(): Path {
        Files.createDirectories(autostartDirectory)
        val entry = autostartDirectory.resolve(DesktopBuildInfo.LINUX_DESKTOP_ID)
        val content = """
            [Desktop Entry]
            Type=Application
            Name=${DesktopBuildInfo.APPLICATION_NAME}
            Comment=Point de vente spécialisé pour Pâtisserie
            Exec="$executable"
            Icon=/opt/patisserie-pos/lib/PATISSERIE_POS.png
            Terminal=false
            X-GNOME-Autostart-enabled=true
            StartupNotify=true
            StartupWMClass=${DesktopBuildInfo.APPLICATION_NAME}
        """.trimIndent() + System.lineSeparator()
        if (!Files.isRegularFile(entry) || Files.readString(entry) != content) {
            Files.writeString(
                entry,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        }
        return entry
    }
}
