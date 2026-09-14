package ma.elaroui.pos.desktop.print

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.Charset
import javax.imageio.ImageIO
import kotlin.test.*
import ma.elaroui.pos.shared.domain.*

class CustomerReceiptLogoPrintingTest {

    private lateinit var tempLogoFile: File
    private val sampleOrder = Order(
        id = 101L,
        number = "CMD-2026-001",
        type = OrderType.DINE_IN,
        status = OrderStatus.COMPLETED,
        lines = listOf(
            OrderLine(1L, "Café Crème", 1500L, 2, 1000),
            OrderLine(2L, "Croissant Pur Beurre", 1000L, 1, 1000)
        ),
        subtotalCentimes = 4000L,
        discountCentimes = 0L,
        taxCentimes = 364L,
        totalCentimes = 4000L,
        tableId = 4L,
        registerSessionId = 1L,
        cashierId = 2L
    )

    @BeforeTest
    fun setUp() {
        // Create a temporary 200x100 RGB image with high-contrast text/shapes
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 200, 100)
        g.color = Color.BLACK
        g.fillRect(20, 20, 160, 60)
        g.color = Color.WHITE
        g.drawString("LOGO TEST", 50, 55)
        g.dispose()

        tempLogoFile = File.createTempFile("pos_test_logo_", ".png")
        ImageIO.write(img, "PNG", tempLogoFile)
    }

    @AfterTest
    fun tearDown() {
        if (::tempLogoFile.isInitialized && tempLogoFile.exists()) {
            tempLogoFile.delete()
        }
    }

    @Test
    fun testLoadLogoBufferedImageReadsValidFile() {
        val loaded = ThermalTicketRenderer.loadLogoBufferedImage(tempLogoFile.absolutePath)
        assertNotNull(loaded, "Logo image must load successfully from valid disk path")
        assertEquals(200, loaded.width)
        assertEquals(100, loaded.height)
    }

    @Test
    fun testLoadLogoBufferedImageHandlesNullAndMissingFilesGracefully() {
        assertNull(ThermalTicketRenderer.loadLogoBufferedImage(null))
        assertNull(ThermalTicketRenderer.loadLogoBufferedImage(""))
        assertNull(ThermalTicketRenderer.loadLogoBufferedImage("   "))
        assertNull(ThermalTicketRenderer.loadLogoBufferedImage("C:/path/that/does/not/exist/logo.png"))

        val emptyFile = File.createTempFile("pos_empty_logo_", ".png")
        try {
            assertNull(ThermalTicketRenderer.loadLogoBufferedImage(emptyFile.absolutePath))
        } finally {
            emptyFile.delete()
        }
    }

    @Test
    fun testRenderLogoEscPosGeneratesValidRasterBitImagePayload() {
        val logoBytes80 = ThermalTicketRenderer.renderLogoEscPos(tempLogoFile.absolutePath, 80)
        assertTrue(logoBytes80.isNotEmpty(), "ESC/POS bytes for 80mm logo must not be empty")

        // 1. Must contain Center Alignment prefix: 0x1B, 0x61, 0x01
        assertContentEquals(byteArrayOf(0x1B, 0x61, 0x01), logoBytes80.take(3).toByteArray())

        // 2. Must contain ESC/POS GS v 0 raster header: 0x1D, 0x76, 0x30, 0x00
        val gsV0Header = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        val headerIdx = logoBytes80.indexOfSubArray(gsV0Header)
        assertTrue(headerIdx >= 3, "GS v 0 0 raster header must be present after alignment command")

        // 3. Must end with LF and Alignment Reset: 0x0A, 0x1B, 0x61, 0x00
        val resetAlign = byteArrayOf(0x0A, 0x1B, 0x61, 0x00)
        assertContentEquals(resetAlign, logoBytes80.takeLast(4).toByteArray())

        // Also test 58mm
        val logoBytes58 = ThermalTicketRenderer.renderLogoEscPos(tempLogoFile.absolutePath, 58)
        assertTrue(logoBytes58.isNotEmpty(), "ESC/POS bytes for 58mm logo must not be empty")
        assertTrue(logoBytes58.indexOfSubArray(gsV0Header) >= 0)
    }

    @Test
    fun testCustomerReceiptPrintsLogoOnWindows() {
        val companyWithLogo = ReceiptCompany(
            name = "Le Gourmet Parisien",
            specialty = "Salon de Thé & Restaurant",
            address = "12 Rue de la Paix",
            phone = "0140203040",
            logoPath = tempLogoFile.absolutePath
        )

        val bytes80 = ThermalTicketRenderer.render(
            order = sampleOrder,
            company = companyWithLogo,
            kind = TicketKind.CUSTOMER,
            paperWidth = 80
        )

        // Verify GS v 0 raster header is in output
        val gsV0Header = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        val logoIdx = bytes80.indexOfSubArray(gsV0Header)
        assertTrue(logoIdx > 0, "Windows customer receipt must contain ESC/POS logo raster stream")

        // Verify establishment name appears AFTER logo
        val nameBytes = "Le Gourmet Parisien".toByteArray(Charsets.UTF_8)
        val nameIdx = bytes80.indexOfSubArray(nameBytes)
        assertTrue(nameIdx > logoIdx, "Store name text must appear after the logo")
    }

    @Test
    fun testCustomerReceiptPrintsLogoOnLinuxCups() {
        val companyWithLogo = ReceiptCompany(
            name = "Le Gourmet Parisien",
            specialty = "Salon de Thé & Restaurant",
            address = "12 Rue de la Paix",
            phone = "0140203040",
            logoPath = tempLogoFile.absolutePath
        )

        val request = EscPosPrintRequest(
            order = sampleOrder,
            company = companyWithLogo,
            kind = TicketKind.CUSTOMER,
            paperWidth = 80,
            paymentMethod = PaymentMethod.CASH,
            receivedCentimes = 5000L,
            changeCentimes = 1000L,
            cashierName = "Yassine"
        )

        val formatResult = LinuxEscPosFormatter.format(request)
        assertIs<EscPosFormatResult.Success>(formatResult)

        val outputBytes = formatResult.bytes
        val gsV0Header = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        val logoIdx = outputBytes.indexOfSubArray(gsV0Header)
        assertTrue(logoIdx > 0, "Linux CP858 customer receipt must contain ESC/POS logo raster stream")

        val nameBytes = "Le Gourmet Parisien".toByteArray(Charset.forName("IBM00858"))
        val nameIdx = outputBytes.indexOfSubArray(nameBytes)
        assertTrue(nameIdx > logoIdx, "Store name text must follow the logo on Linux CUPS stream")
    }

    @Test
    fun testPreparationAndKitchenTicketsDoNotPrintLogoEvenIfConfigured() {
        val companyWithLogo = ReceiptCompany(
            name = "Le Gourmet Parisien",
            address = "12 Rue de la Paix",
            phone = "0140203040",
            logoPath = tempLogoFile.absolutePath
        )

        val prepBytes = ThermalTicketRenderer.render(
            order = sampleOrder,
            company = companyWithLogo,
            kind = TicketKind.PREPARATION,
            paperWidth = 80
        )
        val kitchenBytes = ThermalTicketRenderer.render(
            order = sampleOrder,
            company = companyWithLogo,
            kind = TicketKind.KITCHEN,
            paperWidth = 80
        )

        val gsV0Header = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
        assertEquals(-1, prepBytes.indexOfSubArray(gsV0Header), "Preparation ticket must NOT contain logo")
        assertEquals(-1, kitchenBytes.indexOfSubArray(gsV0Header), "Kitchen ticket must NOT contain logo")
    }

    private fun ByteArray.indexOfSubArray(target: ByteArray): Int {
        if (target.isEmpty() || target.size > size) return -1
        for (i in 0..size - target.size) {
            var found = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
