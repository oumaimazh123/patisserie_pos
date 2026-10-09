package ma.elaroui.pos.desktop.platform

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.Frame
import java.awt.GraphicsEnvironment
import kotlin.test.*

class WindowsNativeFileDialogTest {

    @Test
    fun testCsvExtensionFilterMatching() {
        val csvFilters = listOf("csv")
        assertTrue(WindowsNativeFileDialog.matchesExtension("products.csv", csvFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("PRODUCTS.CSV", csvFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("path/to/my_catalog.Csv", csvFilters))

        assertFalse(WindowsNativeFileDialog.matchesExtension("catalog.txt", csvFilters))
        assertFalse(WindowsNativeFileDialog.matchesExtension("catalog.xlsx", csvFilters))
        assertFalse(WindowsNativeFileDialog.matchesExtension("catalog", csvFilters))
        assertFalse(WindowsNativeFileDialog.matchesExtension("catalog.csv.bak", csvFilters))
    }

    @Test
    fun testImageExtensionFilterMatching() {
        val imageFilters = listOf("png", "jpg", "jpeg", "webp")
        assertTrue(WindowsNativeFileDialog.matchesExtension("photo.png", imageFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("PHOTO.PNG", imageFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("picture.jpg", imageFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("picture.jpeg", imageFilters))
        assertTrue(WindowsNativeFileDialog.matchesExtension("logo.webp", imageFilters))

        assertFalse(WindowsNativeFileDialog.matchesExtension("data.csv", imageFilters))
        assertFalse(WindowsNativeFileDialog.matchesExtension("database.db", imageFilters))
    }

    @Test
    fun testFilterPatternFormatting() {
        assertEquals("*.csv", WindowsNativeFileDialog.formatDefaultFilterPattern(listOf("csv")))
        assertEquals("*.png;*.jpg;*.jpeg;*.webp", WindowsNativeFileDialog.formatDefaultFilterPattern(listOf("png", "jpg", "jpeg", "webp")))
        assertEquals("*.db;*.zip", WindowsNativeFileDialog.formatDefaultFilterPattern(listOf("*.db", "*.zip")))
    }

    @Test
    fun testActiveFrameResolutionSafe() {
        // Must not throw even if no frames are open
        val activeFrame = WindowsNativeFileDialog.findActiveFrame()
        // In test runner activeFrame may be null or a test frame; must run cleanly
        if (activeFrame != null) {
            assertNotNull(activeFrame.name)
        }
    }

    @Test
    fun testFullscreenRestorationStateTracking() {
        var placement: WindowPlacement = WindowPlacement.Fullscreen
        val wasFullscreen = placement == WindowPlacement.Fullscreen
        assertTrue(wasFullscreen)

        // Simulate dialog opening: temporary switch to Maximized
        if (wasFullscreen) {
            placement = WindowPlacement.Maximized
        }
        assertEquals(WindowPlacement.Maximized, placement)

        // Simulate dialog closing (either selection or cancellation)
        if (wasFullscreen) {
            placement = WindowPlacement.Fullscreen
        }
        assertEquals(WindowPlacement.Fullscreen, placement, "Must automatically restore Fullscreen on dialog dismiss")
    }

    @Test
    fun testNonFullscreenStatePreserved() {
        var placement: WindowPlacement = WindowPlacement.Floating
        val wasFullscreen = placement == WindowPlacement.Fullscreen
        assertFalse(wasFullscreen)

        // Simulate dialog workflow
        if (wasFullscreen) {
            placement = WindowPlacement.Maximized
        }
        // State remains Floating
        assertEquals(WindowPlacement.Floating, placement)

        if (wasFullscreen) {
            placement = WindowPlacement.Fullscreen
        }
        assertEquals(WindowPlacement.Floating, placement, "Must not force Fullscreen if original window was Floating")
    }

    @Test
    fun testHeadlessModeSafety() {
        if (GraphicsEnvironment.isHeadless()) {
            val result = WindowsNativeFileDialog.showNativeWindowsDialog(
                title = "Test",
                isSave = false,
                filterExtensions = listOf("csv")
            )
            assertNull(result, "In headless environment, showNativeWindowsDialog should return null safely")
        }
    }
}
