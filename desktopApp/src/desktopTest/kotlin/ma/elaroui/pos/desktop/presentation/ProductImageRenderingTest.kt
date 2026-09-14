package ma.elaroui.pos.desktop.presentation

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.test.*

class ProductImageRenderingTest {

    private fun createTestImageFile(width: Int, height: Int, color: Color): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()

        val tempFile = Files.createTempFile("product-img-test-", ".png").toFile()
        tempFile.deleteOnExit()
        ImageIO.write(image, "PNG", tempFile)
        return tempFile
    }

    @Test
    fun testPortraitLandscapeAndSquareImageDecoding() {
        // 1. Portrait image (300 x 600)
        val portraitFile = createTestImageFile(300, 600, Color.RED)
        val portraitBytes = portraitFile.readBytes()
        val portraitSkia = SkiaImage.makeFromEncoded(portraitBytes)
        assertNotNull(portraitSkia)
        assertEquals(300, portraitSkia.width)
        assertEquals(600, portraitSkia.height)
        val portraitBitmap = portraitSkia.toComposeImageBitmap()
        assertEquals(300, portraitBitmap.width)
        assertEquals(600, portraitBitmap.height)

        // 2. Landscape image (800 x 400)
        val landscapeFile = createTestImageFile(800, 400, Color.BLUE)
        val landscapeBytes = landscapeFile.readBytes()
        val landscapeSkia = SkiaImage.makeFromEncoded(landscapeBytes)
        assertNotNull(landscapeSkia)
        assertEquals(800, landscapeSkia.width)
        assertEquals(400, landscapeSkia.height)
        val landscapeBitmap = landscapeSkia.toComposeImageBitmap()
        assertEquals(800, landscapeBitmap.width)
        assertEquals(400, landscapeBitmap.height)

        // 3. Square image (500 x 500)
        val squareFile = createTestImageFile(500, 500, Color.GREEN)
        val squareBytes = squareFile.readBytes()
        val squareSkia = SkiaImage.makeFromEncoded(squareBytes)
        assertNotNull(squareSkia)
        assertEquals(500, squareSkia.width)
        assertEquals(500, squareSkia.height)
        val squareBitmap = squareSkia.toComposeImageBitmap()
        assertEquals(500, squareBitmap.width)
        assertEquals(500, squareBitmap.height)
    }

    @Test
    fun testNonExistentOrCorruptImagePathDoesNotThrow() {
        val nonExistentPath = "/non/existent/path/product.png"
        val bitmap1 = runCatching {
            val file = File(nonExistentPath)
            if (file.exists() && file.isFile) {
                SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            } else null
        }.getOrNull()
        assertNull(bitmap1)

        val corruptFile = Files.createTempFile("corrupt-test", ".png").toFile()
        corruptFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        corruptFile.deleteOnExit()

        val bitmap2 = runCatching {
            val file = corruptFile
            if (file.exists() && file.isFile) {
                SkiaImage.makeFromEncoded(file.readBytes())?.toComposeImageBitmap()
            } else null
        }.getOrNull()
        assertNull(bitmap2)
    }
}
