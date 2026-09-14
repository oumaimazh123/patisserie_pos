package ma.elaroui.pos.desktop

import androidx.compose.runtime.Composable
import java.nio.file.Path
import ma.elaroui.pos.desktop.license.WindowsLicenseManager
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavGraph

@Composable
fun DesktopPosApp(
    database: WindowsPosDatabase,
    dataDir: Path,
    licenseManager: WindowsLicenseManager,
    showWindowModeButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit = {},
    onExitApp: (() -> Unit)? = null,
    onErrorOccurred: (String) -> Unit = {}
) {
    DesktopNavGraph(
        database = database,
        dataDir = dataDir,
        licenseManager = licenseManager,
        showWindowModeButton = showWindowModeButton,
        isFullscreen = isFullscreen,
        onToggleWindowMode = onToggleWindowMode,
        onExitApp = onExitApp
    )
}
