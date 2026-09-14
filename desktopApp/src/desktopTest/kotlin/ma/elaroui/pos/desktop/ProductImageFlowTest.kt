package ma.elaroui.pos.desktop

import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

class ProductImageFlowTest {
    @Test
    fun importedProductImageIsCopiedAndPersistedOnEdit() {
        val root = Files.createTempDirectory("pos-product-image-test")
        val source = root.resolve("source.png")
        Files.write(source, Base64.getDecoder().decode(ONE_PIXEL_PNG))
        WindowsPosDatabase.open(root.resolve("pos.db")).use { db ->
            db.configureInitialSetup("Test café", "Owner", "1234")
            runBlocking {
                val categoryId = db.categories.save(Category(id = 0L, name = "General"))
                db.products.save(Product(id = 0L, categoryId = categoryId, name = "Image test item", priceCentimes = 1_000L, taxRateBasisPoints = 0))
            }
            val state = DesktopNavState(db, root)
            val managedPath = state.importProductImage(source)
            assertTrue(Files.isRegularFile(java.nio.file.Path.of(managedPath)))
            assertNotEquals(source.toAbsolutePath().toString(), managedPath)

            val existing = state.products.first()
            runBlocking { db.products.save(existing.copy(imagePath = managedPath)) }
            assertEquals(managedPath, runBlocking { db.products.findById(existing.id) }?.imagePath)
        }
    }

    private companion object {
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
