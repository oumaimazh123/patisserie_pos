package ma.elaroui.pos.desktop.print

import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class ThermalPrintingHardSuiteTest {

    private val sampleCompany = ReceiptCompany(
        name = "Café Restaurant Atlas",
        address = "123 Boulevard Mohammed V, Quartier Hassan, Rabat",
        phone = "0537001122",
        ice = "001234567890012",
        taxId = "40201099",
        commercialRegister = "76543",
        patente = "889900",
        wifiName = "Atlas_Guest_WiFi",
        wifiCode = "AtlasCafe2026"
    )

    private val baseOrder = Order(
        id = 101,
        number = "ORD-2026-00101",
        type = OrderType.DINE_IN,
        status = OrderStatus.COMPLETED,
        lines = listOf(
            OrderLine(1, "Café Crème", 1_600, 2, 1_000),
            OrderLine(2, "Croissant aux Amandes", 1_800, 1, 1_000)
        ),
        subtotalCentimes = 5_000,
        discountCentimes = 0,
        taxCentimes = 455,
        totalCentimes = 5_000,
        tableId = 4,
        registerSessionId = 1,
        cashierId = 1
    )

    @Test
    fun test01_normalPaidOrderRendersCustomerAndKitchenTickets(): Unit {
        val customer80 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        val customer58 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.CUSTOMER, 58)
        val kitchen80 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.KITCHEN, 80)
        val kitchen58 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.KITCHEN, 58)

        // Verify company and tax details on customer receipt
        assertTrue(customer80.contains("Café Restaurant Atlas"))
        assertTrue(customer80.contains("001234567890012")) // ICE
        assertTrue(customer80.contains("40201099")) // IF
        assertTrue(customer80.contains("ORD-2026-00101"))
        assertTrue(customer80.contains("50.00 DH"))

        // Kitchen ticket must omit financial details and Wi-Fi code
        assertTrue(kitchen80.contains("*** TICKET CUISINE / BAR ***"))
        assertTrue(kitchen80.contains("ORD-2026-00101"))
        assertFalse(kitchen80.contains("50.00 DH"))
        assertFalse(kitchen80.contains("AtlasCafe2026"))

        // Verify line widths
        assertTrue(customer58.lines().all { it.length <= 32 }, "58mm customer receipt exceeded 32 chars")
        assertTrue(customer80.lines().all { it.length <= 48 }, "80mm customer receipt exceeded 48 chars")
        assertTrue(kitchen58.lines().all { it.length <= 32 }, "58mm kitchen ticket exceeded 32 chars")
        assertTrue(kitchen80.lines().all { it.length <= 48 }, "80mm kitchen ticket exceeded 48 chars")
    }

    @Test
    fun test02_cashPaymentReceiptWithReceivedAndChange(): Unit {
        val receipt = ThermalTicketRenderer.previewText(
            order = baseOrder,
            company = sampleCompany,
            kind = TicketKind.CUSTOMER,
            paperWidth = 80,
            isReprint = false,
            paymentMethod = PaymentMethod.CASH,
            receivedCentimes = 10_000, // 100.00 DH
            changeCentimes = 5_000     // 50.00 DH
        )
        assertTrue(receipt.contains("Mode paiement:") && receipt.contains("ESPECES"))
        assertTrue(receipt.contains("Espèces reçues:") && receipt.contains("100.00 DH"))
        assertTrue(receipt.contains("Monnaie rendue:") && receipt.contains("50.00 DH"))
        assertTrue(receipt.contains("TOTAL") && receipt.contains("50.00 DH"))
    }

    @Test
    fun test03_cardPaymentReceipt(): Unit {
        val receipt = ThermalTicketRenderer.previewText(
            order = baseOrder,
            company = sampleCompany,
            kind = TicketKind.CUSTOMER,
            paperWidth = 80,
            isReprint = false,
            paymentMethod = PaymentMethod.CARD,
            receivedCentimes = null,
            changeCentimes = null
        )
        assertTrue(receipt.contains("Mode paiement:") && receipt.contains("CARTE / TPE"))
        assertFalse(receipt.contains("Espèces reçues"))
        assertFalse(receipt.contains("Monnaie rendue"))
    }

    @Test
    fun test04_orderTypesDineInTakeawayAndCounter(): Unit {
        val dineInWithTable = baseOrder.copy(type = OrderType.DINE_IN, tableId = 7)
        val dineInNoTable = baseOrder.copy(type = OrderType.DINE_IN, tableId = null)
        val takeaway = baseOrder.copy(type = OrderType.TAKEAWAY, tableId = null)
        val counter = baseOrder.copy(type = OrderType.COUNTER, tableId = null)

        val textDineIn = ThermalTicketRenderer.previewText(dineInWithTable, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(textDineIn.contains("Sur place (Table 7)"))

        val textDineInNoTable = ThermalTicketRenderer.previewText(dineInNoTable, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(textDineInNoTable.contains("Sur place"))

        val textTakeaway = ThermalTicketRenderer.previewText(takeaway, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(textTakeaway.contains("A emporter"))

        val textCounter = ThermalTicketRenderer.previewText(counter, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(textCounter.contains("Au comptoir"))
    }

    @Test
    fun test05_ordersWithManyItemsAndLongProductNames(): Unit {
        val longName1 = "Tajine de Poulet Fermier au Citron Confit et Olives Rouges de Marrakech"
        val longName2 = "Cocktail Signature Fruits Exotiques Mangue Passion Ananas Sans Sucre Ajouté"
        val longLines = (1..25).map { index ->
            OrderLine(
                productId = index.toLong(),
                name = if (index % 2 == 0) "$longName1 #$index" else "$longName2 #$index",
                unitPriceCentimes = 4_550,
                quantity = index,
                taxRateBasisPoints = 1_000
            )
        }
        val largeOrder = baseOrder.copy(
            lines = longLines,
            subtotalCentimes = longLines.sumOf { it.unitPriceCentimes * it.quantity },
            totalCentimes = longLines.sumOf { it.unitPriceCentimes * it.quantity }
        )

        val preview58 = ThermalTicketRenderer.previewText(largeOrder, sampleCompany, TicketKind.CUSTOMER, 58)
        val preview80 = ThermalTicketRenderer.previewText(largeOrder, sampleCompany, TicketKind.CUSTOMER, 80)

        assertTrue(preview58.lines().all { it.length <= 32 }, "58mm ticket wrapping violated width constraint")
        assertTrue(preview80.lines().all { it.length <= 48 }, "80mm ticket wrapping violated width constraint")

        // Byte array output rendering
        val bytes58 = ThermalTicketRenderer.render(largeOrder, sampleCompany, TicketKind.CUSTOMER, 58)
        val bytes80 = ThermalTicketRenderer.render(largeOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(bytes58.isNotEmpty())
        assertTrue(bytes80.isNotEmpty())
    }

    @Test
    fun test06_discountsAndTaxBreakdownCalculations(): Unit {
        val orderWithDiscountAndTax = baseOrder.copy(
            subtotalCentimes = 10_000,  // 100.00 DH
            discountCentimes = 1_500,   // 15.00 DH discount
            taxCentimes = 773,          // VAT on 85.00 DH (10% VAT -> HT = 77.27, TVA = 7.73)
            totalCentimes = 8_500       // 85.00 DH Total TTC
        )

        val receipt = ThermalTicketRenderer.previewText(orderWithDiscountAndTax, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(receipt.contains("SOUS-TOTAL") && receipt.contains("100.00 DH"))
        assertTrue(receipt.contains("REMISE") && receipt.contains("-15.00 DH"))
        assertTrue(receipt.contains("TOTAL HT") && receipt.contains("77.27 DH"))
        assertTrue(receipt.contains("TVA") && receipt.contains("7.73 DH"))
        assertTrue(receipt.contains("TOTAL") && receipt.contains("85.00 DH"))
    }

    @Test
    fun test07_largeTotalsAndFormatting(): Unit {
        val hugeOrder = baseOrder.copy(
            subtotalCentimes = 999_999_50L, // 999,999.50 DH
            totalCentimes = 999_999_50L
        )
        val receipt80 = ThermalTicketRenderer.previewText(hugeOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        val receipt58 = ThermalTicketRenderer.previewText(hugeOrder, sampleCompany, TicketKind.CUSTOMER, 58)

        assertTrue(receipt80.contains("999999.50 DH"))
        assertTrue(receipt58.lines().all { it.length <= 32 })
    }

    @Test
    fun test08_accentsSpecialCharactersAndSafeEncoding(): Unit {
        val specialOrder = baseOrder.copy(
            lines = listOf(
                OrderLine(1, "Café glacé & Crème brûlée (Grande Taille)", 2_500, 1, 1_000),
                OrderLine(2, "Thé à la menthe & Pâtisserie", 1_500, 2, 1_000)
            )
        )
        val receipt = ThermalTicketRenderer.previewText(specialOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(receipt.contains("Café glacé & Crème brûlée"))
        assertTrue(receipt.contains("Thé à la menthe & Pâtisserie"))

        // Render to raw bytes using centralized French CP858 must succeed without CharacterCodingException
        val bytes = ThermalTicketRenderer.render(specialOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(bytes.isNotEmpty())
        val decoded = String(bytes, FrenchEscPosEncoder.CHARSET)
        assertTrue(decoded.contains("Café glacé & Crème brûlée"))
        assertTrue(decoded.contains("Thé à la menthe & Pâtisserie"))
    }

    @Test
    fun test09_reprintDuplicataBanner(): Unit {
        val original = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.CUSTOMER, 80, isReprint = false)
        val reprint = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.CUSTOMER, 80, isReprint = true)

        assertFalse(original.contains("*** DUPLICATA ***"), "Original receipt must not have duplicata banner")
        assertTrue(reprint.contains("*** DUPLICATA ***"), "Reprinted receipt must contain DUPLICATA banner")
    }

    @Test
    fun test10_printerFailureHandlingAndResilience(): Unit {
        val printer = WindowsThermalPrinter()

        // Empty/blank printer name
        val blankResult = printer.print(DesktopPrinterConfig("", 80), "test".toByteArray())
        assertTrue(blankResult.isFailure)
        assertTrue(blankResult.exceptionOrNull()!!.message!!.contains("No printer name"))

        // Disabled printer
        val disabledResult = printer.print(DesktopPrinterConfig("POS-Printer", 80, enabled = false), "test".toByteArray())
        assertTrue(disabledResult.isFailure)
        assertTrue(disabledResult.exceptionOrNull()!!.message!!.contains("disabled"))

        // Non-existent / disconnected printer
        val nonExistentResult = printer.print(DesktopPrinterConfig("Printer-Disconnected-12345", 80), "test".toByteArray())
        assertTrue(nonExistentResult.isFailure)
        assertTrue(nonExistentResult.exceptionOrNull()!!.message!!.contains("unavailable, disconnected, or powered off"))

        // Available printers lookup returns non-null list without throwing
        val printers = printer.availablePrinters()
        assertNotNull(printers)
    }

    @Test
    fun test11_printerFailureDoesNotCorruptDatabaseOrPayments(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, object : ma.elaroui.pos.shared.Clock {
                override fun now() = ma.elaroui.pos.shared.EpochMilliseconds(1_000L)
            }).execute(1, 1, 1, 10_000)

            val orderResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(1, "ORD-CRASH-SAFE", OrderType.COUNTER, 1, listOf(1L to 1), null, 1)
            val order = assertIs<UseCaseResult.Success<Order>>(orderResult).value

            // 1. Complete payment in DB
            val payResult = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(order.id, PaymentMethod.CASH, 2_000, "token-safety-test")
            val payment = assertIs<UseCaseResult.Success<Payment>>(payResult).value

            // 2. Simulate printer crash/disconnection
            val printer = WindowsThermalPrinter()
            val printResult = printer.print(DesktopPrinterConfig("MissingPrinter", 80), "sample bytes".toByteArray())
            assertTrue(printResult.isFailure)

            // 3. Verify payment, order, and database state remain completely intact and valid
            assertEquals(OrderStatus.COMPLETED, db.orders.findById(order.id)?.status)
            val savedPayment = db.payments.findBySubmissionToken(order.id, "token-safety-test")
            assertNotNull(savedPayment)
            assertEquals(payment.amountCentimes, savedPayment.amountCentimes)
            assertEquals(PaymentStatus.COMPLETED, savedPayment.status)

            // 4. Verify order can be previewed/rendered again for reprint
            val reprintBytes = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.CUSTOMER, 80, isReprint = true)
            assertTrue(reprintBytes.isNotEmpty())
        }
    }

    @Test
    fun test12_stressTestRapidConsecutiveTicketRendering(): Unit {
        val iterations = 50
        val startTime = System.currentTimeMillis()
        for (i in 1..iterations) {
            val dynamicOrder = baseOrder.copy(
                id = i.toLong(),
                number = "ORD-STRESS-$i",
                totalCentimes = (i * 1_250L)
            )
            val bytes58 = ThermalTicketRenderer.render(dynamicOrder, sampleCompany, TicketKind.CUSTOMER, 58)
            val bytes80 = ThermalTicketRenderer.render(dynamicOrder, sampleCompany, TicketKind.CUSTOMER, 80)
            val kitchenBytes = ThermalTicketRenderer.render(dynamicOrder, sampleCompany, TicketKind.KITCHEN, 80)

            assertTrue(bytes58.isNotEmpty())
            assertTrue(bytes80.isNotEmpty())
            assertTrue(kitchenBytes.isNotEmpty())
        }
        val elapsed = System.currentTimeMillis() - startTime
        assertTrue(elapsed < 2_000, "Stress rendering of 150 tickets took too long: ${elapsed}ms")
    }

    @Test
    fun test13_virtualPrinterClassification(): Unit {
        // Virtual printers must be identified
        assertTrue(WindowsThermalPrinter.isVirtual("Microsoft Print to PDF"))
        assertTrue(WindowsThermalPrinter.isVirtual("OneNote (Desktop)"))
        assertTrue(WindowsThermalPrinter.isVirtual("Send to OneNote 16"))
        assertTrue(WindowsThermalPrinter.isVirtual("Microsoft XPS Document Writer"))
        assertTrue(WindowsThermalPrinter.isVirtual("Fax"))
        assertTrue(WindowsThermalPrinter.isVirtual("Adobe PDF"))
        assertTrue(WindowsThermalPrinter.isVirtual("Sage PDF Converter"))
        assertTrue(WindowsThermalPrinter.isVirtual("CutePDF Writer"))
        assertTrue(WindowsThermalPrinter.isVirtual("Foxit PhantomPDF Printer"))
        assertTrue(WindowsThermalPrinter.isVirtual("Bullzip PDF Printer"))
        assertTrue(WindowsThermalPrinter.isVirtual("PDFCreator"))

        // Physical / Thermal printers must NOT be classified as virtual
        assertFalse(WindowsThermalPrinter.isVirtual("EPSON TM-T20III Receipt"))
        assertFalse(WindowsThermalPrinter.isVirtual("POS-80"))
        assertFalse(WindowsThermalPrinter.isVirtual("XP-80C"))
        assertFalse(WindowsThermalPrinter.isVirtual("Star TSP100"))
        assertFalse(WindowsThermalPrinter.isVirtual("Xprinter XP-N160I"))
        assertFalse(WindowsThermalPrinter.isVirtual("Kitchen-80"))
        assertFalse(WindowsThermalPrinter.isVirtual("Bar-Printer"))
        assertFalse(WindowsThermalPrinter.isVirtual("Generic / Text Only"))

        // Ignored printers (OneNote, Fax, XPS, PDF) must be filtered out
        assertTrue(WindowsThermalPrinter.isIgnored("OneNote for Windows 10"))
        assertTrue(WindowsThermalPrinter.isIgnored("Envoyer à OneNote 16"))
        assertTrue(WindowsThermalPrinter.isIgnored("Fax"))
        assertTrue(WindowsThermalPrinter.isIgnored("Microsoft XPS Document Writer"))
        assertTrue(WindowsThermalPrinter.isIgnored("Microsoft Print to PDF"))
        assertTrue(WindowsThermalPrinter.isIgnored("Adobe PDF"))
        assertFalse(WindowsThermalPrinter.isIgnored("EPSON TM-T20III Receipt"))
        assertFalse(WindowsThermalPrinter.isIgnored("POS-80"))
    }

    @Test
    fun test14_testPrintCrashResilienceAndEdgeCases(): Unit {
        val printer = WindowsThermalPrinter()

        // 1. No printer configured / Blank name -> Returns Failure, No Crash
        val blankResult = printer.print(DesktopPrinterConfig("", 80), "test".toByteArray())
        assertTrue(blankResult.isFailure)
        assertNotNull(blankResult.exceptionOrNull())

        val spacesResult = printer.print(DesktopPrinterConfig("   ", 80), "test".toByteArray())
        assertTrue(spacesResult.isFailure)

        // 2. Invalid printer name -> Returns Failure, No Crash
        val invalidResult = printer.print(DesktopPrinterConfig("NON_EXISTENT_PRINTER_XYZ", 80), "test".toByteArray())
        assertTrue(invalidResult.isFailure)
        assertFalse(printer.isPrinterAvailable("NON_EXISTENT_PRINTER_XYZ"))

        // 3. Handling of paper width validation
        assertFails { ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.CUSTOMER, 0) }
        assertFails { ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.CUSTOMER, 999) }

        val renderWidth58 = ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.CUSTOMER, 58)
        assertTrue(renderWidth58.isNotEmpty())

        val renderWidth80 = ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        assertTrue(renderWidth80.isNotEmpty())

        // 4. Repeated rapid test print calls with missing/invalid printers -> No Crash
        for (i in 1..25) {
            val repeatResult = printer.print(DesktopPrinterConfig("MISSING-PRINTER-$i", 80), renderWidth80)
            assertTrue(repeatResult.isFailure)
        }
    }

    @Test
    fun test15_LogoRendering_PngJpgWebp_58mm80mm_CorruptAndMissingSafety(): Unit {
        val tempDir = Files.createTempDirectory("thermal_logo_test")

        // 1. Create a valid PNG test logo
        val validPngFile = tempDir.resolve("valid_logo.png").toFile()
        val img = BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.BLACK
        g.fillRect(10, 10, 100, 40)
        g.dispose()
        ImageIO.write(img, "png", validPngFile)

        // 2. Create a very large image (e.g. 2000x2000)
        val largeImageFile = tempDir.resolve("large_logo.png").toFile()
        val largeImg = BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(largeImg, "png", largeImageFile)

        // 3. Create a corrupt/invalid image file (garbage bytes with .png extension)
        val corruptFile = tempDir.resolve("corrupt_logo.png").toFile()
        corruptFile.writeBytes("NOT_A_REAL_IMAGE_DATA_CORRUPT_BYTES_XYZ".toByteArray())

        // Non-existent path
        val missingPath = tempDir.resolve("non_existent_logo.png").toString()

        // --- TEST SCENARIOS ---

        // A. Valid Logo on 80mm Customer Ticket
        val companyWithValidLogo = sampleCompany.copy(logoPath = validPngFile.absolutePath)
        val renderWithLogo80 = ThermalTicketRenderer.render(baseOrder, companyWithValidLogo, TicketKind.CUSTOMER, 80)
        assertTrue(renderWithLogo80.isNotEmpty())
        // Raster header 0x1D 0x76 ('GS v') must be present when logo is rendered
        val hasRasterCommand80 = renderWithLogo80.indices.any { i ->
            i + 3 < renderWithLogo80.size &&
                renderWithLogo80[i] == 0x1D.toByte() &&
                renderWithLogo80[i + 1] == 0x76.toByte() &&
                renderWithLogo80[i + 2] == 0x30.toByte()
        }
        assertTrue(hasRasterCommand80, "80mm render should include ESC/POS raster image sequence for valid logo")

        // B. Valid Logo on 58mm Customer Ticket
        val renderWithLogo58 = ThermalTicketRenderer.render(baseOrder, companyWithValidLogo, TicketKind.CUSTOMER, 58)
        assertTrue(renderWithLogo58.isNotEmpty())
        val hasRasterCommand58 = renderWithLogo58.indices.any { i ->
            i + 3 < renderWithLogo58.size &&
                renderWithLogo58[i] == 0x1D.toByte() &&
                renderWithLogo58[i + 1] == 0x76.toByte() &&
                renderWithLogo58[i + 2] == 0x30.toByte()
        }
        assertTrue(hasRasterCommand58, "58mm render should include ESC/POS raster image sequence for valid logo")

        // C. Kitchen ticket should NOT render logo
        val renderKitchenWithLogo = ThermalTicketRenderer.render(baseOrder, companyWithValidLogo, TicketKind.KITCHEN, 80)
        val hasRasterInKitchen = renderKitchenWithLogo.indices.any { i ->
            i + 3 < renderKitchenWithLogo.size &&
                renderKitchenWithLogo[i] == 0x1D.toByte() &&
                renderKitchenWithLogo[i + 1] == 0x76.toByte() &&
                renderKitchenWithLogo[i + 2] == 0x30.toByte()
        }
        assertFalse(hasRasterInKitchen, "Kitchen tickets should not include customer logo")

        // D. No Logo -> Clean text ticket without raster header
        val companyNoLogo = sampleCompany.copy(logoPath = "")
        val renderNoLogo = ThermalTicketRenderer.render(baseOrder, companyNoLogo, TicketKind.CUSTOMER, 80)
        assertTrue(renderNoLogo.isNotEmpty())
        val hasRasterNoLogo = renderNoLogo.indices.any { i ->
            i + 3 < renderNoLogo.size &&
                renderNoLogo[i] == 0x1D.toByte() &&
                renderNoLogo[i + 1] == 0x76.toByte() &&
                renderNoLogo[i + 2] == 0x30.toByte()
        }
        assertFalse(hasRasterNoLogo, "Ticket with empty logo should not include raster header")

        // E. Missing Logo File -> No crash, falls back to text receipt
        val companyMissingLogo = sampleCompany.copy(logoPath = missingPath)
        val renderMissingLogo = ThermalTicketRenderer.render(baseOrder, companyMissingLogo, TicketKind.CUSTOMER, 80)
        assertTrue(renderMissingLogo.isNotEmpty(), "Missing logo file must not crash rendering")
        val previewMissing = ThermalTicketRenderer.previewText(baseOrder, companyMissingLogo, TicketKind.CUSTOMER, 80)
        assertTrue(previewMissing.contains("Café Restaurant Atlas"))

        // F. Corrupt Logo File -> No crash, falls back safely
        val companyCorruptLogo = sampleCompany.copy(logoPath = corruptFile.absolutePath)
        val renderCorrupt = ThermalTicketRenderer.render(baseOrder, companyCorruptLogo, TicketKind.CUSTOMER, 80)
        assertTrue(renderCorrupt.isNotEmpty(), "Corrupted image file must not crash rendering")

        // G. Very Large Logo -> Proportionally scaled without crash or memory overflow
        val companyLargeLogo = sampleCompany.copy(logoPath = largeImageFile.absolutePath)
        val renderLarge80 = ThermalTicketRenderer.render(baseOrder, companyLargeLogo, TicketKind.CUSTOMER, 80)
        assertTrue(renderLarge80.isNotEmpty(), "Large image must be constrained and scaled proportionally")
        val renderLarge58 = ThermalTicketRenderer.render(baseOrder, companyLargeLogo, TicketKind.CUSTOMER, 58)
        assertTrue(renderLarge58.isNotEmpty(), "Large image must fit 58mm without crash")
    }

    @Test
    fun test16_operationalPreparationTicketAndPrinterRouting(): Unit {
        // Operational preparation ticket must have order details and total, but omit all legal/store blocks
        val prep80 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.PREPARATION, 80)
        val prep58 = ThermalTicketRenderer.previewText(baseOrder, sampleCompany, TicketKind.PREPARATION, 58)

        assertTrue(prep80.contains("*** TICKET DE PREPARATION ***"))
        assertTrue(prep80.contains("ORD-2026-00101"))
        assertTrue(prep80.contains("Sur place (Table 4)"))
        assertTrue(prep80.contains("Café Crème"))
        assertTrue(prep80.contains("50.00 DH")) // Includes total for cashier/prep verification

        // MUST NOT contain fiscal/legal metadata
        assertFalse(prep80.contains("001234567890012")) // ICE
        assertFalse(prep80.contains("40201099")) // IF
        assertFalse(prep80.contains("76543")) // RC
        assertFalse(prep80.contains("889900")) // Patente
        assertFalse(prep80.contains("0537001122")) // Phone
        assertFalse(prep80.contains("123 Boulevard Mohammed V")) // Address
        assertFalse(prep80.contains("AtlasCafe2026")) // Wi-Fi code
        assertFalse(prep80.contains("TVA")) // Tax breakdown

        assertTrue(prep58.lines().all { it.length <= 32 })
        assertTrue(prep80.lines().all { it.length <= 48 })

        val prepBytes80 = ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.PREPARATION, 80)
        val prepBytes58 = ThermalTicketRenderer.render(baseOrder, sampleCompany, TicketKind.PREPARATION, 58)
        assertTrue(prepBytes80.isNotEmpty())
        assertTrue(prepBytes58.isNotEmpty())
    }

    @Test
    fun test17_dineInDraftPrintingDoesNotForcePayment(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, object : ma.elaroui.pos.shared.Clock {
                override fun now() = ma.elaroui.pos.shared.EpochMilliseconds(1_000L)
            }).execute(1, 1, 1, 10_000)

            // Create open Dine-in order without payment
            val orderResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(10, "ORD-DINEIN-DRAFT", OrderType.DINE_IN, 1, listOf(1L to 2), 4L, 1)
            val order = assertIs<UseCaseResult.Success<Order>>(orderResult).value

            // Order status is OPEN / active
            assertEquals(OrderStatus.OPEN, order.status)
            assertEquals(4L, order.tableId)

            // Render operational tickets for kitchen & prep
            val kitchenBytes = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.KITCHEN, 80)
            val prepBytes = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.PREPARATION, 80)
            assertTrue(kitchenBytes.isNotEmpty())
            assertTrue(prepBytes.isNotEmpty())

            // Database has NO payment recorded for this session yet
            val totalCash = db.payments.totalCashForSession(1)
            assertEquals(0L, totalCash, "No payment should exist for open dine-in order")

            // Order is still open in database
            val dbOrder = db.orders.findById(order.id)
            assertNotNull(dbOrder)
            assertEquals(OrderStatus.OPEN, dbOrder.status)
        }
    }

    @Test
    fun test15_windowsPrinterServiceDiscoveryAndHealth(): Unit {
        val service = WindowsPrinterService(logger = ma.elaroui.pos.desktop.JvmPlatformLogger())
        val discovery = service.discoverPrinters()
        assertTrue(discovery.isSuccess)

        val availableNames = WindowsThermalPrinter().availablePrinters()
        assertEquals(availableNames.size, discovery.printers.size)

        val healthBlank = service.checkPrinterHealth("")
        assertEquals(PrinterConnectionState.NOT_CONFIGURED, healthBlank.state)
        assertFalse(healthBlank.message.contains("CUPS"))

        val healthUnknown = service.checkPrinterHealth("NON_EXISTENT_PRINTER_ABC")
        assertEquals(PrinterConnectionState.NOT_CONFIGURED, healthUnknown.state)
        assertTrue(healthUnknown.message.contains("Windows"))
        assertFalse(healthUnknown.message.contains("CUPS"))

        val healthOneNote = service.checkPrinterHealth("OneNote for Windows 10")
        assertEquals(PrinterConnectionState.NOT_CONFIGURED, healthOneNote.state)
        assertTrue(healthOneNote.message.contains("non prise en charge"))

        val healthPdf = service.checkPrinterHealth("Microsoft Print to PDF")
        assertEquals(PrinterConnectionState.NOT_CONFIGURED, healthPdf.state)
        assertTrue(healthPdf.message.contains("non prise en charge"))
    }
}
