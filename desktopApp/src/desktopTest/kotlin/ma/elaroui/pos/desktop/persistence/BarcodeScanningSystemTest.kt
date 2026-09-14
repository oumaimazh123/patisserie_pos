package ma.elaroui.pos.desktop.persistence

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.platform.BarcodeScannerController
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import java.nio.file.Files
import kotlin.test.*

class BarcodeScanningSystemTest {
    private fun createDb(): WindowsPosDatabase {
        val dir = Files.createTempDirectory("pos-barcode-test")
        return WindowsPosDatabase.open(dir.resolve("pos.db"))
    }

    @Test
    fun testBarcodePreservesLeadingZerosAndExactString(): Unit = runBlocking {
        val db = createDb()
        val catId = db.categories.save(Category(0, "Boissons", true, 1))

        val productLeadingZero = Product(
            id = 0,
            categoryId = catId,
            name = "Lait Frais 1L",
            priceCentimes = 1200,
            taxRateBasisPoints = 0,
            available = true,
            active = true,
            imagePath = null,
            sku = "SKU-BAR-001",
            barcode = "0012345678905"
        )
        val prodId = db.products.save(productLeadingZero)
        assertTrue(prodId > 0)

        // Query by exact barcode
        val found = db.findProductByBarcode("0012345678905")
        assertNotNull(found)
        assertEquals("0012345678905", found.barcode)
        assertEquals("Lait Frais 1L", found.name)

        // Ensure leading zero wasn't stripped to 12345678905
        assertNull(db.findProductByBarcode("12345678905"))
    }

    @Test
    fun testBarcodeEan8AndEan13AndUpcLookups(): Unit = runBlocking {
        val db = createDb()
        val catId = db.categories.save(Category(0, "Snacks", true, 1))

        // EAN-8
        db.products.save(Product(0, catId, "EAN8 Biscuit", 500, 2000, true, true, null, "SKU-EAN8", "01234567"))
        // EAN-13
        db.products.save(Product(0, catId, "EAN13 Chocolat", 1500, 2000, true, true, null, "SKU-EAN13", "6111234567890"))
        // UPC
        db.products.save(Product(0, catId, "UPC Boisson", 2000, 2000, true, true, null, "SKU-UPC", "012345678905"))

        assertEquals("EAN8 Biscuit", db.findProductByBarcode("01234567")?.name)
        assertEquals("EAN13 Chocolat", db.findProductByBarcode("6111234567890")?.name)
        assertEquals("UPC Boisson", db.findProductByBarcode("012345678905")?.name)
    }

    @Test
    fun testDuplicateBarcodeRejection(): Unit = runBlocking {
        val db = createDb()
        val catId = db.categories.save(Category(0, "Epicerie", true, 1))

        db.products.save(Product(0, catId, "Produit A", 1000, 2000, true, true, null, null, "6110000000012"))

        // Attempt to insert another product with the same barcode
        assertFailsWith<DesktopValidationException> {
            db.products.save(Product(0, catId, "Produit B", 2000, 2000, true, true, null, null, "6110000000012"))
        }
    }

    @Test
    fun testInactiveProductLookup(): Unit = runBlocking {
        val db = createDb()
        val catId = db.categories.save(Category(0, "Epicerie", true, 1))

        db.products.save(Product(0, catId, "Produit Désactivé", 1000, 2000, available = false, active = false, imagePath = null, sku = null, barcode = "99999999"))

        val found = db.findProductByBarcode("99999999")
        assertNotNull(found)
        assertFalse(found.active)
        assertFalse(found.available)
    }

    @Test
    fun testScannerControllerEnterAndTabTerminators() {
        val scannedList = mutableListOf<String>()
        val controller = BarcodeScannerController { barcode ->
            scannedList.add(barcode)
        }

        // Test scan with ENTER
        controller.feedRaw("6111234567890", '\n')
        assertEquals(listOf("6111234567890"), scannedList)

        // Test scan with TAB
        controller.feedRaw("00123456", '\t')
        assertEquals(listOf("6111234567890", "00123456"), scannedList)
    }

    @Test
    fun testScannerControllerRapidConsecutiveScans() {
        val scannedList = mutableListOf<String>()
        val controller = BarcodeScannerController { barcode ->
            scannedList.add(barcode)
        }

        // Simulate 15 rapid scans in succession
        for (i in 1..15) {
            controller.feedRaw("SCAN-$i", '\n')
        }

        assertEquals(15, scannedList.size)
        for (i in 1..15) {
            assertEquals("SCAN-$i", scannedList[i - 1])
        }
        assertEquals("", controller.currentBufferContent)
    }

    @Test
    fun testScannerControllerTimeoutResetsStrayCharacters() {
        val scannedList = mutableListOf<String>()
        val controller = BarcodeScannerController(timeoutMs = 50L) { barcode ->
            scannedList.add(barcode)
        }

        // Stray key 'x' without enter
        controller.feedChar('x')

        // Wait beyond timeout
        Thread.sleep(70)

        // Now scan real barcode
        controller.feedRaw("REAL-BARCODE", '\n')

        // Should not have 'x' prepended
        assertEquals(listOf("REAL-BARCODE"), scannedList)
    }
}
