package ma.elaroui.pos.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.license.WindowsLicenseManager
import ma.elaroui.pos.desktop.license.DesktopLicensingFactory
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.platform.LinuxTouchInputCompatibility
import ma.elaroui.pos.desktop.platform.DesktopSingleInstanceLock
import ma.elaroui.pos.desktop.platform.LinuxAutostartInstaller
import ma.elaroui.pos.desktop.presentation.components.PosTheme
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.DesktopTitleBar
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.WindowPosition
import ma.elaroui.pos.desktop.presentation.diagnostics.TouchDiagnosticScreen

fun main() {
    // Install global uncaught exception handler that logs without terminating the JVM
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        System.err.println("Handled uncaught exception on thread ${thread.name}: ${throwable.message}")
        throwable.printStackTrace()
    }

    val detectedPlatform = DesktopPlatform.detect()
    val resolvedPaths = DesktopApplicationPathsProvider(detectedPlatform).paths()
    val instanceLock = DesktopSingleInstanceLock.acquire(resolvedPaths.configuration.resolve("application.lock"))
    if (instanceLock == null) {
        System.err.println("${DesktopBuildInfo.APPLICATION_NAME} is already running; the second launch was ignored.")
        return
    }
    if (detectedPlatform == DesktopPlatform.LINUX) {
        // GNOME reads per-user startup entries from ~/.config/autostart, not the application
        // configuration subdirectory.
        runCatching {
            LinuxAutostartInstaller(
                java.nio.file.Paths.get(System.getProperty("user.home"), ".config", "autostart")
            ).ensureInstalled()
        }
    }

    try {
        application {
        val platform = remember { detectedPlatform }
        val paths = remember { resolvedPaths }
        val applicationLogger = remember { JvmPlatformLogger(paths.logs.resolve("application.log")) }
        val touchDiagnosticsEnabled = remember {
            platform == DesktopPlatform.LINUX && (
                System.getenv("POS_TOUCH_DIAGNOSTICS") == "1" ||
                    System.getProperty("pos.touch.diagnostics", "false").toBoolean()
                )
        }
        val linuxTouchCompatibility = remember(platform, applicationLogger) {
            if (platform == DesktopPlatform.LINUX) {
                LinuxTouchInputCompatibility(applicationLogger, touchDiagnosticsEnabled)
            } else null
        }
        val database = remember {
            WindowsPosDatabase.open(paths.database).also { db ->
                Runtime.getRuntime().addShutdownHook(Thread {
                    runCatching { db.close() }
                })
            }
        }
        val licenseManager = remember { DesktopLicensingFactory.create(platform, paths).manager() }

        var fatalError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            applicationLogger.info(
                "Application started on ${DesktopBuildInfo.operatingSystem}/${DesktopBuildInfo.architecture}, version ${DesktopBuildInfo.version}",
                "Startup"
            )
        }

        val windowState = rememberWindowState(
            placement = WindowPlacement.Fullscreen,
            position = WindowPosition(Alignment.Center),
            width = 1280.dp,
            height = 800.dp
        )
        val toggleWindowMode = {
            val fullscreen = windowState.placement == WindowPlacement.Fullscreen
            windowState.placement = if (fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
        }
        Window(
            onCloseRequest = {
                runCatching { database.close() }
                exitApplication()
            },
            title = DesktopBuildInfo.APPLICATION_NAME,
            state = windowState,
            undecorated = true,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == Key.F11 -> {
                            toggleWindowMode()
                            true
                        }
                        event.key == Key.Escape && windowState.placement == WindowPlacement.Fullscreen -> {
                            windowState.placement = WindowPlacement.Floating
                            true
                        }
                        event.isAltPressed && event.key == Key.F4 -> {
                            runCatching { database.close() }
                            exitApplication()
                            true
                        }
                        else -> false
                    }
                } else false
            }
        ) {
            LaunchedEffect(window) { window.minimumSize = Dimension(900, 600) }
            DisposableEffect(window, linuxTouchCompatibility) {
                linuxTouchCompatibility?.install(window)
                onDispose { linuxTouchCompatibility?.uninstall() }
            }
            MaterialTheme(colorScheme = PosTheme.colorScheme(), typography = PosTheme.typography()) {
                val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
                Column(Modifier.fillMaxSize()) {
                    if (!isFullscreen) {
                        WindowDraggableArea {
                            DesktopTitleBar(
                                title = DesktopBuildInfo.APPLICATION_NAME,
                                onMinimize = { windowState.isMinimized = true },
                                onMaximize = toggleWindowMode,
                                onClose = {
                                    runCatching { database.close() }
                                    exitApplication()
                                }
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (touchDiagnosticsEnabled) {
                            TouchDiagnosticScreen(applicationLogger)
                        } else {
                            DesktopPosApp(
                                database = database,
                                dataDir = paths.data,
                                licenseManager = licenseManager,
                                showWindowModeButton = false,
                                isFullscreen = isFullscreen,
                                onToggleWindowMode = toggleWindowMode,
                                onExitApp = {
                                    runCatching { database.close() }
                                    exitApplication()
                                },
                                onErrorOccurred = { error ->
                                    System.err.println("Captured UI Error: $error")
                                    applicationLogger.error("UI error: $error", category = "UI")
                                    fatalError = error
                                }
                            )
                        }

                        // Top-level In-App Error Dialog that NEVER closes the window or application on OK
                        if (!fatalError.isNullOrBlank()) {
                        AlertDialog(
                            onDismissRequest = { fatalError = null },
                            title = {
                                Text(
                                    "Information système",
                                    fontWeight = FontWeight.Bold,
                                    color = PosColors.Primary,
                                    fontSize = 16.sp
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Une notification système est survenue. Vos données et votre session sont parfaitement en sécurité.",
                                        fontSize = 13.sp,
                                        color = PosColors.TextMedium
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        // Dismiss ONLY, keep the window open and interactive!
                                        fatalError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("OK", fontWeight = FontWeight.Bold)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = Color.White
                        )
                    }
                }
            }
        }
    }
    }
} finally {
        instanceLock?.close()
    }
}
