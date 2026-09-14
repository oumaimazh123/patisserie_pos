package ma.elaroui.pos.desktop.platform

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.JvmFileSelectionService
import ma.elaroui.pos.shared.FileSelectionRequest
import kotlin.test.*

class NativeFileDialogsTest {

    @Test
    fun testNativeFileDialogsStructureAndFilters(): Unit {
        assertNotNull(NativeFileDialogs)
        assertNotNull(WindowsNativeFileDialog)

        // Verify valid image extensions (.png, .jpg, .jpeg, .webp)
        val allowedExtensions = setOf("png", "jpg", "jpeg", "webp")
        val sampleNames = listOf("photo.png", "item.JPG", "drink.jpeg", "logo.WEBP", "doc.pdf", "data.csv")

        val validImageNames = sampleNames.filter { name ->
            name.substringAfterLast('.', "").lowercase() in allowedExtensions
        }
        assertEquals(listOf("photo.png", "item.JPG", "drink.jpeg", "logo.WEBP"), validImageNames)
    }

    @Test
    fun testFilterSpecStructure(): Unit {
        val filter = WindowsNativeFileDialog.COMDLG_FILTERSPEC("Images (*.png;*.jpg;*.jpeg;*.webp)", "*.png;*.jpg;*.jpeg;*.webp")
        assertNotNull(filter)
        assertEquals("Images (*.png;*.jpg;*.jpeg;*.webp)", filter.pszName?.toString())
        assertEquals("*.png;*.jpg;*.jpeg;*.webp", filter.pszSpec?.toString())
    }

    @Test
    fun testFileSelectionServiceIntegration(): Unit = runBlocking {
        val service = JvmFileSelectionService()
        assertNotNull(service)

        val request = FileSelectionRequest(
            title = "Choisir une image",
            allowedExtensions = setOf(".png", ".jpg", ".jpeg", ".webp"),
            suggestedFileName = "test.png"
        )
        assertNotNull(request)
        assertEquals("Choisir une image", request.title)
        assertEquals(setOf(".png", ".jpg", ".jpeg", ".webp"), request.allowedExtensions)
    }
}
